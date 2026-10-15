"""Focused authored policy/helper tests; no network or Android runtime verdict."""

import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
SPEC = importlib.util.spec_from_file_location("prepare_vnc_adapter", ROOT / "tools/prepare_vnc_adapter.py")
adapter = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(adapter)


POLICY_TEST = r'''
#include "policy.h"
#include <openssl/evp.h>
#include <openssl/x509.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
static unsigned int cases;
static void check(int condition) { cases++; if (!condition) exit(2); }
static void certificate(long before, long after, int expected) {
    EVP_PKEY *key = EVP_PKEY_Q_keygen(NULL, NULL, "EC", "P-256");
    X509 *cert = X509_new();
    if (!key || !cert || !X509_set_version(cert, 2) ||
        !ASN1_INTEGER_set(X509_get_serialNumber(cert), 1) ||
        !X509_gmtime_adj(X509_getm_notBefore(cert), before) ||
        !X509_gmtime_adj(X509_getm_notAfter(cert), after) || !X509_set_pubkey(cert, key)) exit(3);
    X509_NAME *name = X509_get_subject_name(cert);
    if (!X509_NAME_add_entry_by_txt(name, "CN", MBSTRING_ASC,
            (const unsigned char *)"cloudy-owned-test", -1, -1, 0) ||
        !X509_set_issuer_name(cert, name) || !X509_sign(cert, key, EVP_sha256())) exit(3);
    unsigned char *der = NULL;
    int length = i2d_X509(cert, &der);
    unsigned char pin[32]; unsigned int size;
    if (length <= 0 || !X509_digest(cert, EVP_sha256(), pin, &size) || size != 32) exit(3);
    check(cloudy_verify_leaf(der, (size_t)length, pin) == expected);
    pin[0] ^= 1;
    check(!cloudy_verify_leaf(der, (size_t)length, pin));
    pin[0] ^= 1;
    check(!cloudy_verify_leaf(der, (size_t)length - 1, pin));
    unsigned char *trailing = malloc((size_t)length + 1);
    if (!trailing) exit(3);
    memcpy(trailing, der, (size_t)length); trailing[length] = 0;
    check(!cloudy_verify_leaf(trailing, (size_t)length + 1, pin));
    free(trailing); OPENSSL_free(der); X509_free(cert); EVP_PKEY_free(key);
}
int main(void) {
    certificate(-60, 3600, 1);      /* Matching valid leaf. */
    certificate(-7200, -3600, 0); /* Matching expired leaf still rejects. */
    certificate(3600, 7200, 0);   /* Matching not-yet-valid leaf still rejects. */
    unsigned char pin[32] = {0}, bad[3] = {1, 2, 3};
    check(!cloudy_verify_leaf(bad, sizeof(bad), pin));
    check(!cloudy_verify_leaf(NULL, 0, pin));
    check(!cloudy_verify_leaf(bad, 131073, pin));
    size_t bytes = 0;
    check(cloudy_dimensions(4096, 2048, &bytes) && bytes == 33554432);
    check(!cloudy_dimensions(4096, 2049, &bytes));
    check(!cloudy_dimensions(0, 1, &bytes));
    check(!cloudy_dimensions(2147483647, 2147483647, &bytes));
    check(cloudy_numeric_endpoint("127.0.0.1"));
    check(cloudy_numeric_endpoint("::1"));
    check(!cloudy_numeric_endpoint("localhost"));
    check(!cloudy_numeric_endpoint("unix:/tmp/socket"));
    check(!cloudy_numeric_endpoint("::1%lo"));
    check(cloudy_utf8((const unsigned char *)"abc", 3));
    const unsigned char good[] = {0xf0, 0x9f, 0x98, 0x80};
    const unsigned char overlong[] = {0xc0, 0x80}, nul[] = {0}, surrogate[] = {0xed, 0xa0, 0x80};
    const unsigned char excessive[] = {0xf4, 0x90, 0x80, 0x80};
    check(cloudy_utf8(good, sizeof(good)));
    check(!cloudy_utf8(overlong, sizeof(overlong)));
    check(!cloudy_utf8(nul, sizeof(nul)));
    check(!cloudy_utf8(surrogate, sizeof(surrogate)));
    check(!cloudy_utf8(excessive, sizeof(excessive)));
    check(!cloudy_utf8(good, sizeof(good) - 1));
    unsigned char secret[] = {1, 2, 3}; cloudy_wipe(secret, sizeof(secret));
    check(secret[0] == 0 && secret[1] == 0 && secret[2] == 0);
    printf("{\"policyChecks\":%u,\"failures\":0}\n", cases);
    return 0;
}
'''


class PolicyTests(unittest.TestCase):
    def test_actual_c_policy_with_real_openssl_and_sanitizers(self):
        source = ROOT / "qualification/probes/native-vnc/adapter"
        with tempfile.TemporaryDirectory(prefix="cloudy-vnc-policy-") as temporary:
            stage = Path(temporary)
            fixture = stage / "policy_test.c"
            fixture.write_text(POLICY_TEST)
            binary = stage / "policy_test"
            command = ["cc", "-std=c11", "-Wall", "-Wextra", "-Wpedantic", "-Werror",
                       "-fsanitize=address,undefined", "-fno-omit-frame-pointer", "-g",
                       f"-I{source}", str(source / "policy.c"), str(fixture), "-lcrypto", "-o", str(binary)]
            compiled = subprocess.run(command, capture_output=True, text=True, timeout=30, check=True)
            result = subprocess.run([str(binary)], capture_output=True, text=True, timeout=10, check=True)
            self.assertEqual(compiled.stderr, "")
            self.assertEqual(result.stderr, "")
            self.assertEqual(json.loads(result.stdout), {"policyChecks": 32, "failures": 0})


class HelperTests(unittest.TestCase):
    def test_source_change_blocks_publication(self):
        with patch.object(adapter, "snapshot", return_value=({"adapter": "new"}, {})):
            with self.assertRaisesRegex(ValueError, "publication rejected"):
                adapter.unchanged({"adapter": "reviewed"})

    def test_offline_change_rejects_before_native_or_sdk_access(self):
        with tempfile.TemporaryDirectory() as temporary:
            run = Path(temporary)
            prepared = run / "prepared"
            prepared.mkdir()
            (prepared / "notice").write_text("before")
            receipt = {"inputs": {"adapter": "pin"}, "exitCode": 0, "prepared": adapter.base.hashes(prepared)}
            (prepared / "notice").write_text("changed")
            with patch.object(adapter, "snapshot", return_value=({"adapter": "pin"}, {})):
                with self.assertRaisesRegex(ValueError, "prepared outputs changed"):
                    adapter.check(run, receipt)


if __name__ == "__main__":
    unittest.main()
