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

    def foundation(self):
        evidence = "qualification/results/P0-S2/synthetic-policy-test.md"
        gate = next(g for g in self.backlog["capabilityGates"] if g["id"] == "CAP-FOUNDATION")
        gate.update(outcome="supported", supportedScope="Synthetic construction primitives only",
                    evidencePaths=[evidence], decisionNote="Policy fixture, not device evidence")
        for ident in gate["decidingStages"]:
            self.stage(ident)["status"] = "done"
        self.stage("P0-S1")["status"] = "done"
        return gate, evidence

    def test_foundation_admits_p1_only_after_p0_s3(self):
        _, evidence = self.foundation()
        self.experiment(evidence)
        self.stage("P0-S3")["status"] = "done"
        self.stage("P1-S1")["status"] = "ready"
        status, report = self.validate(self.backlog, evidence)
        self.assertEqual((status, report["errors"]), (0, []))
        self.stage("P0-S3")["status"] = "planned"
        self.assert_rejected(self.validate(self.backlog, evidence), "P1-S1", "prerequisites")

    def test_supported_foundation_requires_scope_evidence_and_completed_decision(self):
        gate, evidence = self.foundation()
        for field, value, diagnostic in (("supportedScope", None, "scope"),
                                         ("evidencePaths", [], "evidence")):
            original = gate[field]
            gate[field] = value
            self.assert_rejected(self.validate(self.backlog, evidence), "CAP-FOUNDATION", diagnostic)
            gate[field] = original
        self.stage(gate["decidingStages"][0])["status"] = "planned"
        self.assert_rejected(self.validate(self.backlog, evidence), "CAP-FOUNDATION", "completed deciding")

    def test_platform_experiment_still_blocks_prerequisite_complete_acceptance(self):
        _, evidence = self.foundation()
        # All prerequisite stages/capabilities are qualified in this synthetic
        # scenario, isolating the broad platform gate from foundation admission.
        for stage in self.backlog["stages"]:
            stage["status"] = "done"
        for gate in self.backlog["capabilityGates"]:
            gate.update(outcome="supported", supportedScope="Synthetic test scope",
                        evidencePaths=[evidence], decisionNote="Policy fixture only")
        self.experiment(evidence)
        for ident in ("P2-S5", "P3-S5", "P5-S4"):
            self.assert_rejected(self.validate(self.backlog, evidence), ident, "CAP-PLATFORM", "not supported")

    def test_required_gate_cannot_be_removed_or_substituted(self):
        for ident, gate in (("P1-S1", "CAP-FOUNDATION"), ("P1-S2", "CAP-FOUNDATION"),
                            ("P1-S3", "CAP-FOUNDATION"), ("P2-S5", "CAP-PLATFORM"),
                            ("P3-S5", "CAP-PLATFORM"), ("P5-S4", "CAP-PLATFORM")):
            with self.subTest(stage=ident):
                stage = self.stage(ident)
                original = stage["requiresCapabilities"][:]
                stage["requiresCapabilities"] = [g for g in original if g != gate]
                if gate == "CAP-PLATFORM":
                    stage["requiresCapabilities"].append("CAP-FOUNDATION")
                self.assert_rejected(self.validate(self.backlog), ident, "requires " + gate)
                stage["requiresCapabilities"] = original

    def test_desktop_release_cannot_inherit_a_renderer_dependency(self):
        self.stage("P3-S3")["dependsOn"].append("P2-S2")
        self.assert_rejected(self.validate(self.backlog), "P3-S5", "P2-S2")

    def test_native_stream_cannot_wait_for_optional_host_adapters(self):
        self.stage("P5-S2")["dependsOn"].append("P4-S4")
        self.assert_rejected(self.validate(self.backlog), "P5-S2", "P4-S4")

    def test_optional_host_release_retains_both_standalone_acceptance_paths(self):
        self.stage("P4-S4")["dependsOn"].remove("P3-S9")
        for required in ("P2-S5", "P3-S5"):
            self.assert_rejected(self.validate(self.backlog), "P4-S4", "missing required", required)

    def test_final_suite_entry_retains_standalone_and_stream_acceptance_paths(self):
        original = self.stage("P6-S1")["dependsOn"][:]
        for removed, required in (("P5-S4", "P5-S4"), ("P3-S9", "P2-S5"),
                                  ("P3-S9", "P3-S5")):
            with self.subTest(required=required):
                # Removing the direct suite edge alone leaves the alternate
                # P4-S4 route intact; remove both to exercise full ancestry.
                self.stage("P6-S1")["dependsOn"] = [
                    value for value in original
                    if value != removed and not (removed == "P3-S9" and value == "P4-S4")
                ]
                self.assert_rejected(self.validate(self.backlog), "P6-S1", "missing required", required)
        self.stage("P6-S1")["dependsOn"] = original

    def test_final_gate_cannot_bypass_p6_entry_through_intermediate_stage(self):
        # Mutate the intermediate edge, rather than the final stage's direct
        # list, to prove the existing validator follows transitive ancestry.
        self.stage("P6-S2")["dependsOn"] = []
        self.assert_rejected(self.validate(self.backlog), "P6-S4", "missing required", "P6-S1")

    def test_suite_requires_both_independent_app_candidates(self):
        self.stage("P3-S9")["dependsOn"].remove("P2-S5")
        self.assert_rejected(self.validate(self.backlog), "P3-S9", "P2-S5")


if __name__ == "__main__":
    unittest.main()
