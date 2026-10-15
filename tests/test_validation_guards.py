"""Exercise planning-policy boundaries without changing real qualification state."""

import contextlib
import copy
import importlib.util
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "cloudy_validation_under_test", ROOT / "tools/validate_project.py"
)
VALIDATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VALIDATOR)


class ValidationPolicyTests(unittest.TestCase):
    def setUp(self):
        self.backlog = copy.deepcopy(VALIDATOR.load("docs/05-delivery/backlog.json"))
        # Start policy scenarios from an unqualified plan so later real stage
        # progress cannot turn these synthetic mutations into unrelated failures.
        for stage in self.backlog["stages"]:
            stage["status"] = "planned"
        for gate in self.backlog["capabilityGates"]:
            gate.update(
                outcome="pending", supportedScope=None, evidencePaths=[],
                nextExperiment=None, decisionNote=None,
            )
        temporary = tempfile.TemporaryDirectory(prefix="cloudy-validation-test-")
        self.addCleanup(temporary.cleanup)
        self.evidence = Path(temporary.name) / "synthetic-evidence.md"
        self.evidence.write_text("Test fixture only; no device or product qualification.\n")

    def stage(self, ident):
        return next(stage for stage in self.backlog["stages"] if stage["id"] == ident)

    def experiment(self, evidence, cap=1):
        gate = next(gate for gate in self.backlog["capabilityGates"]
                    if gate["id"] == "CAP-PLATFORM")
        gate.update(
            outcome="experiment", evidencePaths=[evidence],
            decisionNote="Synthetic policy test, not qualification evidence.",
            nextExperiment={
                "hypothesis": "A bounded follow-up can resolve the uncertainty.",
                "ownerRole": "Test owner",
                "stopCondition": "Stop after the stated effort cap.",
                "expectedEvidence": "A recorded pass, failure, or remaining blocker.",
                "effortCapDays": cap,
            },
        )

    def validate(self, backlog=None, evidence=None):
        original_load = VALIDATOR.load
        original_is_file = Path.is_file
        virtual_evidence = (ROOT / evidence).resolve() if evidence else None

        def load(relative):
            if backlog is not None and relative == "docs/05-delivery/backlog.json":
                return backlog
            return original_load(relative)

        def is_file(path):
            # Project one explicit logical evidence name onto a temporary test
            # file. Never create or inspect a real private/public run artifact.
            if virtual_evidence is not None and path == virtual_evidence:
                return original_is_file(self.evidence)
            return original_is_file(path)

        output = io.StringIO()
        with (
            patch.object(VALIDATOR, "load", side_effect=load),
            patch.object(Path, "is_file", is_file),
            patch.object(sys, "argv", ["validate_project.py"]),
            patch.object(sys, "path", [str(ROOT / "tools"), *sys.path]),
            contextlib.redirect_stdout(output),
        ):
            status = VALIDATOR.main()
        return status, json.loads(output.getvalue())

    def assert_rejected(self, result, *diagnostic_parts):
        status, report = result
        self.assertEqual(status, 1, report)
        self.assertEqual(report["status"], "failed", report)
        self.assertTrue(
            any(all(part in error for part in diagnostic_parts)
                for error in report["errors"]),
            report["errors"],
        )

    def test_current_public_plan_is_valid(self):
        status, report = self.validate()
        self.assertEqual((status, report["status"], report["errors"]), (0, "passed", []))

    def test_finite_experiment_with_public_evidence_is_valid(self):
        evidence = "qualification/results/P0-S2/synthetic-policy-test.md"
        self.experiment(evidence)
        status, report = self.validate(self.backlog, evidence)
        self.assertEqual((status, report["errors"]), (0, []))

    def test_private_evidence_cannot_support_public_readiness(self):
        evidence = "qualification/results/private/synthetic-policy-test.md"
        self.experiment(evidence)
        self.assert_rejected(
            self.validate(self.backlog, evidence), "CAP-PLATFORM", "private"
        )

    def test_infinite_experiment_budget_is_rejected(self):
        evidence = "qualification/results/P0-S2/synthetic-policy-test.md"
        self.experiment(evidence, float("inf"))
        self.assert_rejected(
            self.validate(self.backlog, evidence), "CAP-PLATFORM", "effort cap"
        )

    def test_stage_cannot_start_before_its_prerequisite(self):
        self.stage("P0-S2")["status"] = "ready"
        self.assert_rejected(self.validate(self.backlog), "P0-S2", "prerequisites")

    def test_stage_cannot_start_with_an_unsupported_capability(self):
        self.stage("P2-S1")["status"] = "ready"
        self.assert_rejected(self.validate(self.backlog), "P2-S1", "CAP-SSH")

    def test_desktop_release_cannot_inherit_a_renderer_dependency(self):
        self.stage("P3-S3")["dependsOn"].append("P2-S2")
        self.assert_rejected(self.validate(self.backlog), "P3-S5", "P2-S2")

    def test_native_stream_cannot_wait_for_optional_host_adapters(self):
        self.stage("P5-S2")["dependsOn"].append("P4-S4")
        self.assert_rejected(self.validate(self.backlog), "P5-S2", "P4-S4")

    def test_suite_requires_both_independent_app_candidates(self):
        self.stage("P3-S9")["dependsOn"].remove("P2-S5")
        self.assert_rejected(self.validate(self.backlog), "P3-S9", "P2-S5")


if __name__ == "__main__":
    unittest.main()
