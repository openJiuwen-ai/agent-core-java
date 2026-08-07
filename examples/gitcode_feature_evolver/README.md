# GitCode Feature Evolver

GitCode Feature Evolver is an independent, file-configured service for feature
delivery. It reuses the proven Issue Evolver infrastructure without sharing its
database, port, process, bot token, label, Worktrees, or lifecycle. The bug
service remains unchanged.

The feature service admits an open Issue with the exact `feature` label, then
runs a durable DevFlow: specification, independent R1 review, one long-lived
Draft PR, design, independent R2 review, bounded RED/GREEN/REFACTOR tasks,
independent R3 review, and PR promotion for human review. It never merges or
deploys.

## Trigger and workflow defaults

- `triggerMode`: `polling`, `webhook`, or `both`; the sample uses `both`.
- Polling starts immediately and repeats every 15 minutes.
- Admission uses `updated_at` in a frozen 24-hour window, open state, and an
  exact, case-sensitive `feature` label.
- A repository/Issue IID is admitted once for its lifetime across polling,
  Webhook delivery, restart, and terminal state.
- `attended` is the default. R1, R2, and R3 wait for an authenticated approver.
  `unattended` still performs every independent review and test gate.
- Pull requests are reconciled to `MERGED` or `CLOSED`; merge remains human.

The durable workflow prompt is
[`resources/skills/gitcode-feature-devflow`](../../resources/skills/gitcode-feature-devflow/SKILL.md).
Implementation and R3 additionally load the repository's `coding-standard`
Skill. Issue text, comments, source, and prior model output remain untrusted
data; only the service controller supplies authority and writable paths.

## Authenticated Issue comments

Only a GitCode login listed in `approverLogins` can issue these exact commands:

```text
/feature approve r1
/feature approve r2
/feature approve r3
/feature reject r1 <reason>
/feature reject r2 <reason>
/feature reject r3 <reason>
/feature pause <reason>
/feature resume
/feature cancel <reason>
/feature status
```

Polling reads comments as well as Issues, so these controls work without a
Webhook. A Note Webhook provides lower latency in `webhook` or `both` mode.
The author is authenticated from GitCode API/Webhook identity; names written
inside comment text grant no authority.

## Mandatory container boundary

The service refuses to become ready unless its dedicated account has rootless
Podman and the configured public image is already present by an immutable
`name@sha256:<digest>` reference. Every Agent-modified tree is tested with:

- no network, no host credential environment, and no GitCode/model secret
  mounts;
- a non-root UID/GID with `keep-id` mapping;
- read-only container root, dropped capabilities, `no-new-privileges`, and
  bounded memory, CPU, PIDs, time, and `/tmp`;
- only the feature Worktree mounted writable; the trusted, credential-free
  Maven cache is mounted read-only so one job cannot poison later jobs;
- fixed Maven RED or full verification arguments selected by the controller,
  never by Issue content or the model.

The container also masks the Worktree's `.git` control file. Build lifecycle
files such as `pom.xml` and `.mvn/`, CI/release control, deployment paths,
trusted Skills, Agent instructions, and credential material are permanent
write exclusions even when an Issue asks for them.

An offline dependency miss pauses the job for trusted operator prefetch. It
does not grant the feature container network access.

## Local build and deterministic tests

```bash
bash examples/gitcode_feature_evolver/scripts/build-demo.sh
bash examples/gitcode_feature_evolver/scripts/test-demo.sh
bash examples/gitcode_feature_evolver/scripts/run-service.sh --help
```

Copy the two sanitized config examples to untracked local files and supply the
shared model config explicitly. Placeholder files intentionally fail `--check`.
Live GitCode or model calls are not part of the deterministic test suite.

## Production deployment

Use [`deploy/README-linux.md`](deploy/README-linux.md). The production gate is
intentionally two-phase: root provisions a locked-down rootless executor, then
a credential-free container runs the full repository suite before systemd can
activate this independent service.
