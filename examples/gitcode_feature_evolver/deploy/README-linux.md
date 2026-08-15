# GitCode Feature Evolver on Linux

This deployment runs the feature service independently from
`gitcode-issue-evolver`. It uses its own service account, systemd unit, port,
configuration, SQLite database, Worktrees, Maven cache, bot token, and logs.
Do not point both services at the same writable repository checkout or state
directory.

The checkout's `origin` may remain the service release source. Runtime Feature
Worktrees fetch the configured target repository directly into a dedicated
`feature-target` ref; changing `targetRepository` therefore does not require a
Git remote rewrite in this checkout.
The first post-merge system-test stage freezes that target-base commit in a
detached job-owned Worktree; retries and restarts reuse its recorded revision.
When post-merge testing is enabled, the same seed object database fetches the
configured test base into an isolated `feature-system-test` ref; the generated
branch is pushed to the configured test publication fork and the generated PR
targets the main test repository.

`polling` needs outbound HTTPS only. `webhook` and `both` additionally need a
trusted HTTPS reverse proxy to the loopback listener; no tunnel is started by
this deployment.

## Security boundary and prerequisites

Install JDK 17, Maven, Git, Bash, jq, curl, and rootless Podman using the host's
normal package-management process. The service image must be public and pinned
to an immutable SHA-256 digest. The deployment helper deliberately uses an
empty registry auth file and cannot pull a private image.

The fixed layout is:

```text
/opt/gitcode-feature-evolver/repo
/etc/gitcode-feature-evolver/feature-config.json
/etc/gitcode-feature-evolver/feature-secrets.json
/etc/gitcode-feature-evolver/apiconfig.json
/var/lib/gitcode-feature-evolver/data
/var/lib/gitcode-feature-evolver/worktrees
/var/lib/gitcode-feature-evolver/m2
/var/log/gitcode-feature-evolver/transcripts
```

The root helpers are designed for this layout and reject arbitrary paths. The
unit and helpers must be installed and owned by root before granting
passwordless execution:

```bash
sudo install -o root -g root -m 0644 \
  examples/gitcode_feature_evolver/deploy/systemd/gitcode-feature-evolver.service \
  /etc/systemd/system/gitcode-feature-evolver.service
sudo install -d -o root -g root -m 0755 \
  /usr/local/share/gitcode-feature-evolver
sudo install -o root -g root -m 0644 \
  examples/gitcode_feature_evolver/deploy/logging/logback-safe.xml \
  /usr/local/share/gitcode-feature-evolver/logback-safe.xml
sudo install -o root -g root -m 0644 \
  examples/gitcode_feature_evolver/deploy/logging/logback-transcript.xml.template \
  /usr/local/share/gitcode-feature-evolver/logback-transcript.xml.template
sudo install -o root -g root -m 0755 \
  examples/gitcode_feature_evolver/deploy/sbin/manage-feature-evolver-transcript \
  /usr/local/sbin/manage-feature-evolver-transcript
sudo install -o root -g root -m 0644 \
  examples/gitcode_feature_evolver/deploy/tmpfiles/gitcode-feature-evolver.conf \
  /etc/tmpfiles.d/gitcode-feature-evolver.conf
sudo install -d -o root -g root -m 0755 \
  /usr/local/libexec/gitcode-feature-evolver
sudo install -o root -g root -m 0755 \
  examples/gitcode_feature_evolver/deploy/libexec/podman \
  /usr/local/libexec/gitcode-feature-evolver/podman
sudo install -o root -g root -m 0755 \
  examples/gitcode_feature_evolver/deploy/sbin/provision-gitcode-feature-evolver \
  /usr/local/sbin/provision-gitcode-feature-evolver
sudo install -o root -g root -m 0755 \
  examples/gitcode_feature_evolver/deploy/sbin/run-feature-evolver-test \
  /usr/local/sbin/run-feature-evolver-test
```

The optional least-privilege sudoers delegation is exactly:

```sudoers
jiayang ALL=(root) NOPASSWD: /usr/local/sbin/provision-gitcode-feature-evolver
jiayang ALL=(root) NOPASSWD: /usr/local/sbin/run-feature-evolver-test
jiayang ALL=(root) NOPASSWD: /usr/local/sbin/manage-feature-evolver-transcript *
```

These are sudoers policy entries, not commands to execute in a shell. Install
them with `visudo`. The first helper accepts only the optional literal
`--activate`; the second accepts no arguments; the transcript helper validates
its own fixed commands and duration bounds. Never grant passwordless
`podman`, `bash`, `runuser`, `systemctl`, or a writable helper script.
The provision helper verifies the root-owned unit, Podman launcher, and logging
artifacts against compiled-in digests; after changing any of them, reinstall it
together with the matching helpers as root. The launcher does not elevate
privileges. It moves
each fixed Podman command into the dedicated account's systemd user manager so
the requested CPU, memory, and PID controllers are genuinely delegated.
The system unit exposes `/run/user` read-only so the launcher can connect to
that manager's Unix socket; per-user runtime directories remain protected by
their normal `0700` ownership and the launcher verifies its effective UID.

## Phase 1: rootless account and repository

Run the helper once to create the dedicated account, subordinate UID/GID
ranges, fixed directories, lingering systemd user manager, instance-scoped
cgroup-controller delegation, and rootless runtime boundary:

```bash
sudo -n /usr/local/sbin/provision-gitcode-feature-evolver
```

Deploy a clean primary Git checkout to `/opt/gitcode-feature-evolver/repo`.
The helper prints the service UID:GID. Run it again after the checkout exists;
it makes only that fixed checkout writable by the service and validates the
preinstalled systemd unit.

## Phase 2: external credentials and configuration

Start from `config/feature-config.linux.example.json`. Replace every
placeholder, including `containerUser` with the printed service UID:GID and
`containerImage` with the exact public digest. Keep the fixed paths unless the
root helpers and unit are deliberately reviewed and changed together.
The example sets `fullAgentTranscriptEnabled` to `true` for end-to-end process
discovery. Change it to `false` before a formal deployment that must use only
content-free operational logging.

Create `feature-secrets.json` from the sanitized example. `gitCodeUsername` is
the login that owns the dedicated Feature Evolver bot PAT in `gitCodeToken`;
when omitted it defaults to the feature publication-fork owner. It is not the
assignee or Git commit author. The Feature Bot PAT reads Issues/comments,
pushes feature branches, creates/updates feature PRs, and comments on the
source Issue.

With `systemTestEnabled: true`, `systemTestRepository` is the PR target and
baseline repository, `systemTestBaseBranch` is its base branch, and
`systemTestPublishRepository` is the fork that receives generated test
branches. The supplied values are respectively `openJiuwen/jiuwen-test`,
`agent_core_java`, and `antonjli/jiuwen-test-bot`.

Set `systemTestSmokeSelectors` to one to three trusted, network-independent
smoke classes that already exist on that base branch. These exact classes are
always run together with the Java test classes added for the current feature.
Do not configure wildcards, methods, Maven arguments, or the broad `smoke` tag;
the example uses the deterministic workflow smoke needed by the demonstration.

An independent test credential may be set as `systemTestGitCodeToken`, with
its owning login in `systemTestGitCodeUsername`. The username defaults to the
test publication-fork owner when that token is present. This credential needs
only authenticated read of the target test repository, push access to the
publication fork, and create/read/update access for PRs against the target. If
the test token is omitted, the service reuses both the Feature Bot PAT and its
configured/default login for backward compatibility.
The test credential is never used to poll the original Issue, publish the
feature PR, or comment on the original Issue.

Neither PAT is the personal token used by a human to submit Issues or the bug
service bot token. Give both bots no merge, protected-branch bypass,
deployment, repository-administration, or webhook-administration permission.
`webhookSecret` is required only for `webhook` or `both`.

The account names in `approverLogins` are authenticated command identities;
they are not tokens. REST repository visibility alone is insufficient:
validate authenticated Git fetch from the target and generated-branch push to
the publication fork before enabling post-merge delivery.

Install all external files without putting secrets in the unit, repository, or
shell arguments:

```bash
sudo install -o root -g gitcode-feature-evolver -m 0640 \
  <completed-feature-config.json> \
  /etc/gitcode-feature-evolver/feature-config.json
sudo install -o root -g gitcode-feature-evolver -m 0640 \
  <completed-feature-secrets.json> \
  /etc/gitcode-feature-evolver/feature-secrets.json
sudo install -o root -g gitcode-feature-evolver -m 0640 \
  <completed-apiconfig.json> \
  /etc/gitcode-feature-evolver/apiconfig.json
sudo -n /usr/local/sbin/provision-gitcode-feature-evolver
```

The final provision call first checks the service account's rootless Podman
storage. It reuses an already imported image without contacting the registry;
only a missing image is pulled using the empty auth file. No GitCode, model,
SSH, Git credential-helper, or personal PAT is copied into that storage.

## Phase 3: mandatory deployment gate

The service must be stopped while this gate runs. The fixed helper:

1. creates a clean detached Worktree, builds the Example, and runs deterministic
   controller tests inside the credential-free rootless container. Each Gate
   container runs in a transient service under the dedicated account's systemd
   user manager so rootless CPU, memory, and PID limits remain enforceable. The
   helper then copies only checked, non-symlink runtime outputs to the clean
   deployment checkout;
2. validates external config and rootless-image readiness;
3. populates only the dedicated Maven cache from a clean detached Worktree,
   resolving the no-test lifecycle first and then running the fixed,
   deterministic `ConstrainConfigValidationTest` cache probe. This executes one
   lightweight JUnit class—not the main-project test suite—so Maven also resolves
   dynamically selected Surefire provider and JUnit launcher artifacts;
4. creates a second clean Worktree and repeats both the no-test lifecycle and
   the focused cache probe offline, networkless, non-root, resource-limited, and
   credential-free, with the shared cache mounted read-only;
5. records a root-owned stamp bound to the Git commit, runtime config, bot/Webhook
   secrets, model configuration, and image digest. Only hashes—not secret values—
   are written to the stamp.

The cache-preparation stage needs outbound HTTPS to the configured Maven
repositories. `dependency:go-offline` alone is not treated as sufficient because
Surefire can choose its test provider and launcher only when a test actually
starts. The subsequent read-only-cache validation has networking disabled, so a
missing dynamically selected artifact fails deployment before any Feature Job is
admitted.
The general `/tmp` mount remains `noexec`. A separate 64 MiB `nosuid,nodev`
tmpfs is executable only because Jansi and SQLite JDBC must extract native
libraries before loading them; fixed JVM properties direct those extractions to
that mount. It contains no repository, Maven cache, or credentials. The
root-owned launcher supplies these fixed arguments for an older runtime and
rejects a conflicting native-library mount or `JAVA_TOOL_OPTIONS`; current
runtime code also supplies them explicitly.
The same fixed environment sets `user.home` and `MAVEN_CONFIG` to the general
temporary filesystem, preventing PDFBox and Maven image startup helpers from
writing cache or configuration files into the feature Worktree.

```bash
sudo -n /usr/local/sbin/run-feature-evolver-test
```

Changing the commit, either credential file, runtime configuration, or image
invalidates the stamp.
The runtime uses the same fixed light baseline before RED, then runs only exact
Java test classes from the R2-approved plan for RED/GREEN/REFACTOR/SHIP. It does
not run or claim the main repository's complete Maven suite. The System Test
stage runs only configured smoke classes plus newly added test classes.
The Controller also mounts the test tree read-only and constrains Maven test
compilation to those exact classes through an immutable generated POM overlay
and a clean ephemeral test build directory; test execution, stale output, and
unrelated test sources are not a hidden baseline gate.

Runtime dependency misses use the configured
`dependencyPrefetchCacheRoot` (`/var/lib/gitcode-feature-evolver/prefetch` in
the supplied config). A Job-owned copy of the shared cache is populated by a
credential-free networked container, then the original Gate is repeated with
networking disabled. Prefetch resolves dependencies but does not run
`test-compile`; it resolves both the dynamically selected JUnit Platform
provider and the launcher aligned to the trusted POM's JUnit release without
running an online test. The Controller verifies both runtime JARs were actually
persisted before resuming an offline Gate. `pom.xml` and `.mvn` changes are
rejected before prefetch.
Terminal Job caches are retained for `dependencyPrefetchRetentionHours`.
Only after the gate passes can the provision helper activate the service:

```bash
sudo -n /usr/local/sbin/provision-gitcode-feature-evolver --activate
```

## Health, logs, and control

```bash
systemctl status gitcode-feature-evolver.service --no-pager -l
journalctl -u gitcode-feature-evolver.service -f -o cat
curl --fail http://127.0.0.1:8082/health/live
curl --fail http://127.0.0.1:8082/health/ready
curl --fail http://127.0.0.1:8082/api/monitor | jq .
curl --fail-with-body -X POST \
  -H 'X-Feature-Evolver-Admin: poll' \
  http://127.0.0.1:8082/admin/poll
```

Set `manualPollingEnabled` to `true` in `feature-config.json` to register the
last endpoint. The setting is accepted only with `bindHost` exactly
`127.0.0.1` and `triggerMode` set to `polling` or `both`. A successful request
returns HTTP `202`; HTTP `409` means another polling iteration is already
queued or running. The endpoint is protected by a required non-simple request
header as a browser cross-origin safeguard, but remains an operator-only local
control and must not be exposed by a reverse proxy.

Open `http://127.0.0.1:8082/monitor` from the host, or forward the loopback
port over an authenticated SSH session for a demonstration recording:

```bash
ssh -L 8082:127.0.0.1:8082 <operator>@<service-host>
```

The dashboard refreshes every two seconds and shows polling intake, durable
Job creation, controller stages, test-gate milestones, short commit SHAs, and
the feature/system-test PR links. Its JSON is repository-scoped and intentionally omits
credentials, Issue bodies/comments, prompts, model replies, tool inputs and
outputs, and raw errors. Do not publish `/monitor`, `/api/monitor`, or
`/admin/poll` through Nginx; the supplied proxy example exposes none of these
routes.

Journald is intentionally content-free. It records service lifecycle, polling
summaries, state-independent warnings, and errors; it does not receive Agent
prompts, model replies, tool arguments, tool results, or a duplicate rotating
`run.log`. Durable lifecycle decisions remain in the SQLite audit tables at
`/var/lib/gitcode-feature-evolver/data/feature-evolving.db`. Stage artifacts,
review evidence, test evidence, commits, and PR text remain in the Feature
Worktree and GitCode rather than the service log.

`fullAgentTranscriptEnabled` controls the startup policy. The exploration
configuration sets it to `true`, so every service start creates an isolated
transcript for the complete Agent interaction: runtime prompt, model replies,
tool inputs, and tool outputs. It can contain Issue text and repository source
code and never goes to journald. Set the field to `false` and restart the unit
before formal operation. A missing field currently resolves to `true` so an
exploration deployment cannot silently lose evidence.

The transcript uses size-and-day rolling: 100 MB segments, seven-day history,
and a 2 GB retained-size cap. Its directory is mode `0700`; files are created
under the dedicated account with the unit's `0077` umask. Stale files are also
covered by the seven-day tmpfiles policy. The service uses one worker, so a
transcript can contain multiple sequential Job stages and is not a per-job
tracing API.

An operator can still override the live policy. `disable` takes effect within
five seconds but does not rewrite `feature-config.json`; a later service restart
reapplies the configured startup value. `enable` is a bounded diagnostic
override when the configured startup policy is disabled.

```bash
sudo -n /usr/local/sbin/manage-feature-evolver-transcript enable       # 15 minutes
sudo -n /usr/local/sbin/manage-feature-evolver-transcript enable 30    # maximum 60
sudo -n /usr/local/sbin/manage-feature-evolver-transcript status
sudo -n /usr/local/sbin/manage-feature-evolver-transcript disable
```

The helper requires root, records an `authpriv.notice` policy-change event, and
uses a transient systemd timer for bounded `enable` overrides. Logback reloads
the policy within five seconds without restarting the Java process. Copy an
approved demonstration artifact before retention expires if it must be kept.
Never publish a raw transcript to an Issue or PR without a deliberate secret
and personal-data review.

The unit also rate-limits journald output and treats the JVM's normal SIGTERM
exit code as a successful service stop.

For `webhook` or `both`, expose only the HTTPS reverse-proxy route corresponding
to `http://127.0.0.1:8082/webhooks/gitcode`. Configure GitCode Issue, Note, and
pull-request events with the exact external Webhook secret. Polling remains the
fallback in `both` mode and atomically deduplicates the same Issue.

After replacing the hostname and certificate paths, the bounded Nginx example
can be installed separately:

```bash
sudo install -o root -g root -m 0644 \
  examples/gitcode_feature_evolver/deploy/nginx/gitcode-feature-evolver.conf \
  /etc/nginx/conf.d/gitcode-feature-evolver.conf
sudo nginx -t
sudo systemctl reload nginx
```

The service never merges a PR, grants itself permissions, deploys artifacts, or
turns an Issue comment into a shell command. Human review/merge and production
release remain outside this service.
