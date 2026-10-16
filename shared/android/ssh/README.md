# SSH and SFTP boundary

**State:** finite credential-free SSH host-key observation is implemented with the retained SSHJ0.41.2 candidate. Authentication, sessions, PTYs, SFTP and forwarding remain future work. **Delivery stages:** P0-S3, P1-S3/S4, P2-S1/S2/S4.

## Responsibility

Engine adapters for trusted connections, authentication, jump channels, PTYs, resize, SFTP, and local forwarding.

## Boundary

Own crypto through an established maintained engine; keep I/O off the UI thread and use bounded backpressure.

## Implemented observation boundary

`SshHostKeyObserver` accepts a validated logical peer and an independently resolved numeric address. It verifies the server's key-exchange signature through SSHJ, records the key in an always-rejecting verifier, and returns an observation only after retiring its actual socket, Reader and helper threads. It never authenticates or opens a session. Cancellation and deadlines close owned transport; uncertain cleanup quarantines the observer instance. The selected algorithm policy is deliberately narrow and does not imply support for every key format accepted by the domain model.

Android DNS, foreground presentation, permission prompts and app-private trust persistence belong to `:platform`; credential access does not belong to this observer. A saved pin requires a fresh, independently verified future connection before authentication. Full engine adoption, rekey/revocation consequences, jump chains, PTY/SFTP/forwarding and broader algorithm interoperability retain their planned gates.

`:ssh:test` exercises domain integration, canonical fingerprint serialization and real bounded TCP cancellation/deadline boundaries. The OpenSSH success case is opt-in with an owned listener and independent public fingerprint; default CI skips that fixture-dependent case. Device transport and product UI results must be recorded separately.

Read the [module map](../../../docs/02-architecture/module-boundaries.md), [phase plan](../../../docs/05-delivery/README.md), and [validation strategy](../../../docs/06-validation/strategy.md). Record exact implementation versions and evidence before promoting a capability.
