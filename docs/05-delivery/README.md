# Delivery plan index

The execution-reviewed plan has seven phases and 40 stages under [Rule 0](../00-project/platform-rule-zero.md). P0-S1 setup and P0-S2 investigation are complete; CAP-PLATFORM remains a bounded experiment, and P0-S3 investigation is in progress. Product application implementation remains planned. Read the [engineering workflow](engineering-workflow.md) and [execution state](execution-state.md) before continuing. No phone/tablet workstream is included.

| Phase | Stage count | Detail |
| --- | ---: | --- |
| P0 | 7 | [Qualification and decisions](phases/p0-qualification.md) |
| P1 | 5 | [Shared foundation, design and early stream decision](phases/p1-foundation.md) |
| P2 | 8 | [Shared SSH/SFTP core and Console daily use](phases/p2-console.md) |
| P3 | 9 | [Independent Desktop and separate suite milestone](phases/p3-desktop.md) |
| P4 | 4 | [Host adapters and activity](phases/p4-host-integration.md) |
| P5 | 3 | [Integrated desktop and document access](phases/p5-integrated-desktop.md) |
| P6 | 4 | [Hardening and release](phases/p6-release.md) |

Read [roadmap.md](roadmap.md) for dependencies, estimates, release gates, and the first ten working days. [backlog.json](backlog.json) is the canonical stage data; phase documents expand the same tasks and exit criteria into reviewable work packages. No remote issues or project boards are created by this planning foundation.

The [capability outcome protocol](feasibility-gates.md) controls when implementation becomes ready. [Independent release gates](release-gates.md) distinguish Console, Desktop, the combined journey and full-suite qualification. The former P5-S1 adoption decision is now P1-S5.

The [established-library policy](library-reuse-policy.md) records the user's instruction to reuse solid upstream engines and avoid excessive repeated verification. The [general progress table](execution-state.md#general-progress) separates completed investigations, diagnostic behavior and unfinished product subsystems.
