# Proposed protocol artifacts

These self-contained JSON Schemas and synthetic examples support design review and future compatibility fixtures. They do not implement an endpoint, authenticate a caller, or establish an official platform API.

| Contract | Shape | Example |
| --- | --- | --- |
| Resource handoff | [handoff.schema.json](schemas/handoff.schema.json) | [handoff.example.json](examples/handoff.example.json) |
| Host capabilities | [capabilities.schema.json](schemas/capabilities.schema.json) | [capabilities.example.json](examples/capabilities.example.json) |
| Event envelope | [event.schema.json](schemas/event.schema.json) | [event.example.json](examples/event.example.json) |

Use `python3 tools/validate_project.py --schemas` with the optional development dependency installed to validate schema structure/examples and negative cases. Runtime validation must additionally enforce identity, signature/scope, resource roots, timestamps/expiry, message-size bounds, monotonic cursor semantics, and idempotency. See the [contract specification](../docs/02-architecture/contracts.md).

The schemas intentionally reject unknown top-level fields for this initial major version. Future optional additions require an explicit negotiated compatibility policy; do not assume this draft is a production-frozen wire format. All example hosts/IDs/events and dates are synthetic.
