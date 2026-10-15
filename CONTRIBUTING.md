# Contributing

Work from the [phase roadmap](docs/05-delivery/roadmap.md). A stage is ready when its prerequisite decisions are recorded and its evidence can be collected. Claim completion only after the stated exit gate passes.

For implementation work, include the affected product, stage IDs, observable behavior, failure behavior, and validation in the change description. Update a proposed ADR when the experiment selects a different approach. Preserve the rejected alternative and its reason when that reason will help future maintainers.

For research, use an official guide for workflow context and an API reference for exact signatures and availability. Record inaccessible pages as inaccessible, not verified. Refresh a volatile source when implementing against it. A link checker establishes reachability, not accuracy or permission to use an API.

Run `python3 tools/validate_project.py` before committing. Review the design specimen in both appearances and at small and large window sizes when changing design assets. At P1, add real Android lint, unit, instrumentation, accessibility, and build checks using the selected toolchain.

Keep commits focused. Avoid changing the supplied source document. Record third-party licenses and distribution conditions before embedding upstream code; project licensing is still an explicit decision in [dependency review](docs/03-platform/dependency-licensing.md).
