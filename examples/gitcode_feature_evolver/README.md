# GitCode Feature Evolver

GitCode Feature Evolver is an independent, file-configured service for feature
delivery. It reuses the proven Issue Evolver infrastructure without sharing its
database, port, process, bot token, label, Worktrees, or lifecycle. The bug
service remains unchanged.

The feature service admits an open Issue with the exact `feature` label, then
runs a durable DevFlow: specification, independent R1 review, one long-lived
Draft PR, design, independent R2 review, bounded RED/GREEN/REFACTOR tasks,
independent R3 review, and feature PR promotion for human review. After GitCode
reports that PR merged, the same Job creates focused end-to-end Java coverage
in `openJiuwen/jiuwen-test:agent_core_java`, performs an independent
system-test review, publishes the test branch through
`antonjli/jiuwen-test-bot`, and opens a second ready-for-review PR against the
target test repository. It never merges or deploys.

## Trigger and workflow defaults

- `triggerMode`: `polling`, `webhook`, or `both`; the sample uses `both`.
- Polling starts immediately and repeats every 15 minutes.
- `manualPollingEnabled` defaults to `false` for upgrade compatibility. The
  supplied examples enable the protected loopback-only `POST /admin/poll`
  control; it requires polling/both mode and an exact `127.0.0.1` bind.
- `fullAgentTranscriptEnabled` defaults to `true` during exploration. Complete
  prompts, replies, tool inputs, and tool outputs go only to the protected,
  rolling transcript channel; set it to `false` for formal operation.
- Admission uses `updated_at` in a frozen 24-hour window, open state, and an
  exact, case-sensitive `feature` label.
- A repository/Issue IID is admitted once for its lifetime across polling,
  Webhook delivery, restart, and terminal state.
- New Jobs use automatic independent R1/R2/R3 reviews. Legacy `attended`
  configuration is accepted, mapped to `unattended`, and logged as a warning.
  The two normal human waits are feature PR review/merge and system-test PR
  review/merge.
- `maxPrimaryRepairRounds` defaults to 5 and keeps structured Gate feedback in
  the same ReAct conversation. `maxDiagnosticRepairRounds` defaults to 3 and
  then uses an independent diagnostic Agent. Exhaustion is terminal
  `FAILED_AUTOMATION`, not a generic human wait.
- `maxTransientStageRetries` defaults to 5 with persisted delays of 30 seconds,
  2 minutes, 10 minutes, 30 minutes, and 2 hours. Dependency prefetch defaults
  to two isolated attempts and retains terminal Job caches for 24 hours.
- `systemTestEnabled` defaults to `false` when absent for upgrade compatibility;
  the supplied examples enable it and restrict test code to `src/test/java/`
  and `src/test/resources/`.
- `systemTestSmokeSelectors` contains one to three operator-approved, exact
  Java test classes from `jiuwen-test-java`. The controller runs this small
  smoke set together with the test classes added for the current feature; it
  never expands the repository-wide `smoke` tag.
- `gitCodeUsername` defaults to the feature publication-fork owner. An explicit
  value is needed only when the Feature Bot PAT belongs to a different login.
- `systemTestGitCodeToken` can hold a separate least-privilege PAT for the test
  workflow. `systemTestGitCodeUsername` defaults to the test publication-fork
  owner when that PAT is configured. If the test PAT is absent, both values
  compatibly fall back to the Feature Bot credentials. Neither login is a PR
  assignee or Git commit author.
- A feature PR merge advances to `SYSTEM_TEST`; only a later system-test PR
  merge advances to terminal `MERGED`. Either unmerged PR closing advances to
  `CLOSED`; every merge remains human.

The durable workflow prompt is
[`resources/skills/gitcode-feature-devflow`](../../resources/skills/gitcode-feature-devflow/SKILL.md).
Implementation, R3, system-test authoring, and system-test review additionally
load the repository's `coding-standard` Skill. During post-merge work the Agent
gets read-only file/search tools for a frozen target-base source snapshot and
write tools only for the configured test-repository scopes. The controller
freezes the exact target-base commit on the first post-merge stage and reuses it
across retries and restarts. Issue text, comments, source, and prior model output
remain untrusted data; only the service controller supplies authority and
writable paths.

## Agent harness safeguards

The stage harness bounds repository data before it reaches the model. File
reads use one-based `offset`/`limit` paging (at most 2,000 lines and 50 KiB per
call), search uses a zero-based match offset (at most 250 matches and 50 KiB),
and either tool returns `hasMore` plus `nextOffset` for continuation. Read
results include exact `totalLines`; search accepts either a file or directory
and returns structured matches, exact `totalMatches`, `scanComplete`, and
categorized `skippedFiles` counts. Non-UTF-8 assets do not abort a repository
search, while an explicit attempt to read one returns the stable
`FILE_NOT_UTF8` error code. Individual rendered lines are capped at 2,000
Unicode code points or 2,000 UTF-8 bytes for search previews, including a
visible truncation marker. The complete serialized result is independently
capped at 50 KiB rather than limiting only the file-content field. A readable
source file may be larger than one page, but a single call never materializes
the whole file in the Agent context.

Before each model call, an older tool result above 8,192 Unicode code points is
copied into a model-only head/tail preview. The current trailing tool results
remain complete for their first consumption, and the retained context object is
not mutated. At roughly 80 percent of the resolved model context window, the
framework's full compactor summarizes older dialogue and preserves the ten most
recent messages. A provider context-overflow response forces one
compaction-and-retry cycle.

Model calls have a five-minute upper bound. Empty responses, timeouts, rate
limits, HTTP 5xx responses, and transient transport failures receive at most
two exponential-backoff retries around the model invocation only. Tool calls
are not replayed. Authentication, validation, and other deterministic failures
are not retried. These model-facing bounds do not disable the separately
configured protected transcript/audit channel.

## Authenticated Issue comments

Only a GitCode login listed in `approverLogins` can issue these administrator commands:

```text
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
- JVM home and Maven configuration fixed to the container's temporary
  filesystem so library caches cannot pollute the feature Worktree;
- only the assigned Worktree is Agent-writable; ordinary feature gates mount
  the credential-free Maven cache read-only, while post-merge source/test gates
  use a disposable overlay over that cache;
- a fixed sandbox-compatible baseline probe plus exact Java test classes parsed
  from the R2-approved plan; Issue content and the model never supply Maven
  arguments;
- a zero-argument `runApprovedGate` Workflow whose Job, stage, paths, profile,
  selectors, and cancellation checks are captured by the Controller; the
  Controller force-runs it after every final Agent response;
- immediate restoration to the committed stage snapshot after any out-of-scope
  Agent change, followed by terminal `FAILED_POLICY`.

GREEN, REFACTOR, and SHIP run only the Controller-approved selector union.
The service neither runs nor claims the main repository's complete Maven suite.

The post-merge profile installs the merged main artifact with
`maven.test.skip=true` and runs one exact selector union: the configured small
smoke set plus the Controller-derived new test classes. It has no separate
COMPILE baseline and does not run the main or test repository's complete suite.
The Controller mounts the test tree read-only with an immutable, generated POM
overlay and a clean ephemeral test build directory that restrict Maven compiler
roots to that same selector union, so test execution, stale output, or unrelated
test sources cannot alter or become an accidental gate on the Worktree.
Review and publish reuse the passing receipt unless test code, selectors,
image digest, or frozen feature revision changes. Tests using external network, credentials,
`@Disabled`, JUnit assumptions, or sleep-based synchronization are rejected;
a genuine SDK gap becomes auditable `BLOCKED_EXTERNAL` rather than a weakened
test or a routine approval wait.

The container also masks the Worktree's `.git` control file. Build lifecycle
files such as `pom.xml` and `.mvn/`, CI/release control, deployment paths,
trusted Skills, Agent instructions, and credential material are permanent
write exclusions even when an Issue asks for them.

An offline dependency miss enters automatic `DEPENDENCY_PREFETCH`. The service
copies the shared Maven cache into a Job-owned cache without hard links, then
runs dependency resolution without `test-compile` in a credential-free
networked container. It also resolves the JUnit Platform provider selected at
runtime by the explicitly version-pinned Surefire plugin and the launcher
aligned to the trusted POM's JUnit release, without executing an online probe
test. A successful process is accepted only after the Controller verifies both
runtime JARs exist in the persistent Job cache. The prefetch uses
only repositories declared by the trusted POM. It never mounts PATs, model
keys, SSH material, host configuration, or another Job. The original Gate then
retries with `network=none`; caches are retained for 24 hours after terminal
state and removed by a path-constrained cleaner.

System-test source trees remain read-only. The Controller overlays their build
output directories with sticky, explicitly mode-`1777` tmpfs mounts so the
configured non-root container identity can create Maven and Surefire outputs.
Failure to create a Controller-owned build directory is classified as
infrastructure failure rather than test-code repair evidence.

## Local build and deterministic tests

```bash
bash examples/gitcode_feature_evolver/scripts/build-demo.sh
bash examples/gitcode_feature_evolver/scripts/test-demo.sh
bash examples/gitcode_feature_evolver/scripts/run-service.sh --help
```

Copy the two sanitized config examples to untracked local files and supply the
shared model config explicitly. Placeholder files intentionally fail `--check`.
Live GitCode or model calls are not part of the deterministic test suite.

## Local delivery monitor

The loopback listener includes a read-only demonstration dashboard at
`http://127.0.0.1:8082/monitor` and its JSON snapshot at
`http://127.0.0.1:8082/api/monitor`. It visualizes the current polling result,
repository-scoped Job queue, durable workflow stages, test-gate milestones,
short commit SHAs, repair tier/rounds, failure category/code, retry time, Gate
profile/fingerprint/cache hits, dependency-prefetch state, and PR links. The browser refreshes the bounded
snapshot every two seconds; no WebSocket, third-party script, or CDN is used.

When `manualPollingEnabled` is true, the polling card also provides an
immediate-scan button. The same action is available to a host operator with:

```bash
curl --fail-with-body -X POST \
  -H 'X-Feature-Evolver-Admin: poll' \
  http://127.0.0.1:8082/admin/poll
```

The endpoint returns HTTP `202` when queued, `409` when another scheduled or
manual scan is queued/running, and `503` when the service is not ready. It is
not registered when the setting is disabled and must never be reverse-proxied.

The monitor deliberately excludes the bot token, Issue body/comments, Agent
prompt, model reply, tool arguments/results, and raw failure text. Use the
isolated transcript channel described in the Linux deployment guide for those
complete Agent interactions. The exploration configuration enables it for the
whole service run; set `fullAgentTranscriptEnabled` to `false` before formal
operation. Keep the monitor loopback-only and do not add it to a public reverse
proxy.

The deployment checkout's Git `origin` is only a source-code update channel for
the service itself. Each new Feature Worktree fetches its base branch directly
from the validated `targetRepository` into an isolated `feature-target` ref, so
temporarily targeting a fork does not require rewriting the deployment remote.
After the feature PR merge, the service also freezes the current target-base
commit in a detached, job-owned source Worktree. Test Worktrees fetch
`systemTestRepository/systemTestBaseBranch` into an isolated ref, publish the
generated branch to `systemTestPublishRepository`, and create the PR against
the target test repository. Target and publication coordinates are therefore
kept distinct for both feature and system-test delivery.

## Production deployment

Use [`deploy/README-linux.md`](deploy/README-linux.md). The production gate is
intentionally two-phase: root provisions a locked-down rootless executor, then
a credential-free container runs the Example-specific deterministic gate and
no-test build before systemd can activate this independent service.
