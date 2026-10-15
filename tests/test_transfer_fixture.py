"""Synthetic host tests using real loopback HTTPS; no device qualification."""

import contextlib
from datetime import datetime
import hashlib
import http.client
import importlib.util
import json
from pathlib import Path
import socket
import ssl
import tempfile
import threading
import time
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("transfer_fixture", ROOT / "tools/transfer_fixture.py")
FIXTURE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(FIXTURE)


def unused_loopback_port():
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


@contextlib.contextmanager
def running_fixture(directory):
    events = []
    with FIXTURE.FixtureServer(directory, event_sink=events.append) as server:
        thread = threading.Thread(target=server.serve_forever, kwargs={"poll_interval": 0.01})
        thread.start()
        try:
            yield server, events
        finally:
            server.stop_event.set()
            server.shutdown()
            thread.join(timeout=2)
            if thread.is_alive():
                raise AssertionError("test fixture accept thread did not stop")


class TransferFixtureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="cloudy-host-test-")
        cls.addClassCleanup(cls.temporary.cleanup)
        cls.directory = Path(cls.temporary.name) / "private-fixture"
        cls.port = unused_loopback_port()
        cls.config = FIXTURE.prepare(cls.directory, "127.0.0.1", cls.port, 65573)

    def context(self):
        # Strict default client validation is retained, including IP SAN checks.
        return ssl.create_default_context(cadata=self.config["caCertificatePem"])

    def request(self, method="GET", target="/fixture.bin", headers=None):
        connection = http.client.HTTPSConnection("127.0.0.1", self.port, context=self.context(), timeout=3)
        try:
            connection.request(method, target, headers=headers or {})
            response = connection.getresponse()
            return response.status, dict(response.getheaders()), response.read()
        finally:
            connection.close()

    def test_known_payload_spans_block_boundary_and_matches_download_hash(self):
        # Independent byte construction pins the documented generator algorithm,
        # including a partial second block; it does not call payload_chunks().
        prefix = b"cloudy-uidt-sha256-blocks-v1\0"
        first = hashlib.sha256(prefix + (0).to_bytes(8, "big")).digest()
        second = hashlib.sha256(prefix + (1).to_bytes(8, "big")).digest()
        expected = first * 2048 + (second * 2)[:37]
        with running_fixture(self.directory) as (_, events):
            status, headers, received = self.request()
        self.assertEqual(status, 200)
        self.assertEqual(received, expected)
        self.assertEqual(int(headers["Content-Length"]), len(expected))
        self.assertEqual(hashlib.sha256(received).hexdigest(), self.config["expectedSha256"])
        self.assertEqual(headers["X-Fixture-SHA256"], self.config["expectedSha256"])
        self.assertEqual(headers["Cache-Control"], "no-store")
        self.assertEqual([event["event"] for event in events], ["request_started", "stream_written"])
        self.assertEqual(events[0]["requestId"], events[1]["requestId"])
        self.assertEqual(events[1]["bytesWritten"], len(expected))
        self.assertLessEqual(datetime.fromisoformat(events[0]["atUtc"].replace("Z", "+00:00")),
                             datetime.fromisoformat(events[1]["atUtc"].replace("Z", "+00:00")))
        self.assertNotIn("127.0.0.1", json.dumps(events))
        self.assertNotIn("/fixture.bin", json.dumps(events))

    def test_only_exact_path_is_served(self):
        with running_fixture(self.directory):
            for target in ("/", "/ca.key", "/../ca.key", "/fixture.bin?x=1", "/%66ixture.bin", "//fixture.bin"):
                with self.subTest(target=target):
                    status, _, body = self.request(target=target)
                    self.assertEqual((status, body), (404, b""))

    def test_methods_and_partial_or_body_requests_are_rejected(self):
        with running_fixture(self.directory):
            for method in ("HEAD", "POST", "PUT", "DELETE", "OPTIONS", "CONNECT"):
                with self.subTest(method=method):
                    status, headers, body = self.request(method=method)
                    self.assertEqual((status, body), (405, b""))
                    self.assertEqual(headers["Allow"], "GET")
            for headers in ({"Range": "bytes=0-10"}, {"Content-Length": "2"}, {"Transfer-Encoding": "chunked"}):
                with self.subTest(headers=headers):
                    self.assertEqual(self.request(headers=headers)[0], 400)

    def test_untrusted_ca_is_rejected(self):
        with running_fixture(self.directory):
            connection = http.client.HTTPSConnection("127.0.0.1", self.port, timeout=3,
                                                     context=ssl.create_default_context())
            try:
                with self.assertRaises(ssl.SSLCertVerificationError):
                    connection.request("GET", "/fixture.bin")
            finally:
                connection.close()

    def test_wrong_certificate_identity_is_rejected(self):
        with running_fixture(self.directory):
            with socket.create_connection(("127.0.0.1", self.port), timeout=3) as raw:
                with self.assertRaises(ssl.SSLCertVerificationError):
                    self.context().wrap_socket(raw, server_hostname="wrong.invalid")

    def test_stalled_tls_peer_does_not_block_another_client(self):
        with running_fixture(self.directory):
            with socket.create_connection(("127.0.0.1", self.port), timeout=3):
                self.assertEqual(self.request()[0], 200)

    def test_shutdown_interrupts_stalled_tls_peer_and_joins_workers(self):
        started = time.monotonic()
        raw = None
        try:
            with running_fixture(self.directory) as (server, _):
                raw = socket.create_connection(("127.0.0.1", self.port), timeout=3)
                # Complete another request to ensure the accept loop has already
                # accepted the deliberately stalled connection before shutdown.
                self.assertEqual(self.request()[0], 200)
            self.assertEqual(server.connections, set())
            self.assertFalse(any(worker.is_alive() for worker in server._threads))
            self.assertLess(time.monotonic() - started, 2)
        finally:
            if raw is not None:
                raw.close()

    def test_interrupt_after_worker_start_keeps_teardown_ownership(self):
        original_start = FIXTURE.ThreadingHTTPServer.process_request
        worker_errors = []

        def start_then_interrupt(server, request, address):
            original_start(server, request, address)
            raise KeyboardInterrupt

        # Inject only the interruption timing. The accepted TLS socket and
        # worker are real; normal teardown must still own both after Ctrl-C.
        with patch.object(threading, "excepthook", worker_errors.append):
            with FIXTURE.FixtureServer(self.directory, event_sink=lambda event: None) as server:
                with socket.create_connection(("127.0.0.1", self.port), timeout=3):
                    request, address = server.get_request()
                    request.settimeout(0.5)
                    with patch.object(FIXTURE.ThreadingHTTPServer, "process_request", start_then_interrupt):
                        with self.assertRaises(KeyboardInterrupt):
                            server.process_request(request, address)
                    self.assertIn(request, server.connections)
            self.assertEqual(server.connections, set())
            self.assertFalse(any(worker.is_alive() for worker in server._threads))
        self.assertEqual(worker_errors, [])

    def test_generated_files_are_private_and_android_config_contains_no_key(self):
        self.assertEqual(self.directory.stat().st_mode & 0o777, 0o700)
        for path in self.directory.iterdir():
            self.assertEqual(path.stat().st_mode & 0o777, 0o600, path.name)
        loaded = json.loads((self.directory / "android-config.json").read_text())
        self.assertEqual(set(loaded), {
            "schemaVersion", "fixtureId", "url", "expectedLengthBytes",
            "expectedSha256", "caCertificatePem",
        })
        self.assertNotIn("PRIVATE KEY", json.dumps(loaded))
        self.assertEqual(loaded, self.config)

    def test_preparation_refuses_overwrite_and_wildcard_or_unbounded_inputs(self):
        original = (self.directory / "ca.key").read_bytes()
        with self.assertRaises(FileExistsError):
            FIXTURE.prepare(self.directory, "127.0.0.1", self.port, 65573)
        self.assertEqual((self.directory / "ca.key").read_bytes(), original)
        for args in (("0.0.0.0", 8443, 1, 0), ("224.0.0.1", 8443, 1, 0),
                     ("::1", 8443, 1, 0), ("127.0.0.1", 443, 1, 0),
                     ("127.0.0.1", 8443, 0, 0), ("127.0.0.1", 8443, 2**40, 0),
                     ("127.0.0.1", 8443, 1, -1), ("127.0.0.1", 8443, True, 0)):
            with self.subTest(args=args), self.assertRaises(ValueError):
                FIXTURE.validate_parameters(*args)
        with self.assertRaises(ValueError):
            FIXTURE.private_path(ROOT / "qualification/public-fixture")
        with self.assertRaises(ValueError):
            list(FIXTURE.payload_chunks(-1))

    def test_config_drift_is_rejected_before_binding(self):
        config_path = self.directory / "android-config.json"
        original = config_path.read_text()
        changed = json.loads(original)
        changed["expectedLengthBytes"] += 1
        try:
            config_path.write_text(json.dumps(changed))
            with self.assertRaisesRegex(ValueError, "does not match"):
                FIXTURE.FixtureServer(self.directory)
        finally:
            config_path.write_text(original)

    def test_private_file_permission_drift_is_rejected(self):
        key_path = self.directory / "server.key"
        try:
            key_path.chmod(0o644)
            with self.assertRaisesRegex(ValueError, "owner-only"):
                FIXTURE.FixtureServer(self.directory)
        finally:
            key_path.chmod(0o600)

    def test_throttle_and_server_stop_leave_an_incomplete_transfer(self):
        host_path = self.directory / "host-config.json"
        original = host_path.read_text()
        android_path = self.directory / "android-config.json"
        original_android = android_path.read_text()
        throttled = json.loads(original)
        throttled["bytesPerSecond"] = 65536
        throttled["lengthBytes"] = 131072
        android = json.loads(original_android)
        android["expectedLengthBytes"] = 131072
        android["expectedSha256"] = FIXTURE.payload_sha256(131072)
        try:
            host_path.write_text(json.dumps(throttled))
            android_path.write_text(json.dumps(android))
            with running_fixture(self.directory) as (server, events):
                connection = http.client.HTTPSConnection("127.0.0.1", self.port,
                                                         context=self.context(), timeout=3)
                try:
                    started = time.monotonic()
                    connection.request("GET", "/fixture.bin")
                    response = connection.getresponse()
                    self.assertEqual(response.status, 200)
                    self.assertEqual(len(response.read(1)), 1)
                    self.assertGreaterEqual(time.monotonic() - started, 0.85)
                    server.stop_event.set()
                    # read() without a size enforces the remaining Content-Length.
                    with self.assertRaises(http.client.IncompleteRead):
                        response.read()
                finally:
                    connection.close()
            self.assertTrue(any(event["event"] == "interrupted" for event in events), events)
            self.assertFalse(any(event["event"] == "stream_written" for event in events), events)
        finally:
            host_path.write_text(original)
            android_path.write_text(original_android)


if __name__ == "__main__":
    unittest.main()
