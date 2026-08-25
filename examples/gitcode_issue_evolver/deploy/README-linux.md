# GitCode Issue Evolver on Linux

This deployment keeps the Java service on the loopback interface and runs it
as a foreground systemd service. The default polling configuration needs no
public inbound endpoint. Webhook and both modes can additionally expose the
GitCode webhook and readiness endpoint through an HTTPS reverse proxy.

The Quick Tunnel script remains available for an interactive demo. It is not
part of the long-running deployment.

## Prerequisites

- A Linux host; a stable public address and DNS name are needed only for
  `webhook` or `both`
- JDK 17, Maven, Git, Bash, curl, and Nginx
- Outbound access to GitCode, the configured model endpoint, and Maven
  repositories
- An HTTPS certificate trusted by GitCode for `webhook` or `both`

Use a fresh Linux clone. Do not copy a Windows detached Worktree because its
`.git` file can point to a Windows-only path.

## Filesystem layout

The provided service and proxy templates use this layout:

```text
/opt/gitcode-issue-evolver/repo
/etc/gitcode-issue-evolver/evolver-config.json
/etc/gitcode-issue-evolver/evolver-secrets.json
/etc/gitcode-issue-evolver/apiconfig.json
/var/lib/gitcode-issue-evolver/data
/var/lib/gitcode-issue-evolver/worktrees
/var/lib/gitcode-issue-evolver/jiuwen-test-java
/var/log/gitcode-issue-evolver
```

Create a dedicated account and the repository parent directory:

```bash
sudo useradd --system \
  --home-dir /var/lib/gitcode-issue-evolver \
  --create-home \
  --shell /usr/sbin/nologin \
  gitcode-evolver
sudo install -d -o gitcode-evolver -g gitcode-evolver -m 0755 \
  /opt/gitcode-issue-evolver
```

Clone the exact deployment branch or commit as that account:

```bash
sudo -u gitcode-evolver git clone <repository-url> \
  /opt/gitcode-issue-evolver/repo
sudo -u gitcode-evolver git -C /opt/gitcode-issue-evolver/repo \
  checkout <deployment-ref>
```

The repository must retain the remote and base branch required by
`localRepository`; the service fetches and manages Git Worktrees there.
Set `codingStandardSkill` to
`/opt/gitcode-issue-evolver/repo/.claude/skills/coding-standard-full`.
Startup rejects the incomplete compatibility Skill under `resources/skills`.

When the JiuwenTestJava smoke Gate is enabled, install a dedicated checkout
outside the source repository and Job Worktree root. The service account must
be able to write Maven's generated `target` directory, but the checkout is
never exposed to the Bugfix Agent's file tools:

```bash
sudo -u gitcode-evolver git clone --branch agent_core_java \
  https://gitcode.com/openJiuwen/jiuwen-test.git \
  /var/lib/gitcode-issue-evolver/jiuwen-test-java
```

Use the organization-approved mirror or deployment artifact when direct clone
is unavailable. Update this checkout only while the service is stopped, so a
running Job observes one frozen smoke-test revision.

## External configuration

Keep all live configuration outside the Git checkout. Start the non-secret
runtime file from:

```text
examples/gitcode_issue_evolver/config/evolver-config.linux.example.json
```

Set repository names, the base branch, assignees, and Git commit identity for
the deployment. Keep these Linux paths unless the systemd template is also
changed:

```text
localRepository=/opt/gitcode-issue-evolver/repo
dataDir=/var/lib/gitcode-issue-evolver/data
worktreeRoot=/var/lib/gitcode-issue-evolver/worktrees
smokeTestRepository=/var/lib/gitcode-issue-evolver/jiuwen-test-java
```

Set `smokeTestEnabled=true` and configure one to three exact fully qualified
Java test class names in `smokeTestSelectors`. Do not configure a Maven group,
package wildcard, or the complete test repository. The Controller first runs
the fixed source compile Gate, installs the current repaired source version
with tests skipped, and then runs only those exact smoke classes. The model
cannot change the repository, selectors, Maven arguments, or timeout.

Create the two credential-bearing JSON files directly on the host. Do not put
their values in the unit file, shell history, or source control. The service
expects the same fields as the repository's public, sanitized examples.

Install the completed files with permissions that allow only root and the
service account to read them:

```bash
sudo install -d -o root -g gitcode-evolver -m 0750 \
  /etc/gitcode-issue-evolver
sudo install -o root -g gitcode-evolver -m 0640 \
  <completed-evolver-config.json> \
  /etc/gitcode-issue-evolver/evolver-config.json
sudo install -o root -g gitcode-evolver -m 0640 \
  <completed-evolver-secrets.json> \
  /etc/gitcode-issue-evolver/evolver-secrets.json
sudo install -o root -g gitcode-evolver -m 0640 \
  <completed-apiconfig.json> \
  /etc/gitcode-issue-evolver/apiconfig.json
```

Do not replace the tracked `examples/apiconfig.json` with a live model
configuration. Always pass the external file with `--llm-config`.

## Build

Build the SDK and Example without running tests:

```bash
cd /opt/gitcode-issue-evolver/repo
sudo -u gitcode-evolver bash \
  examples/gitcode_issue_evolver/scripts/build-demo.sh
```

The build writes only generated Example classes and the compile classpath
below `examples/gitcode_issue_evolver/.runtime`. A systemd restart reuses
these outputs instead of running Maven again.

Check the external configuration before installing the service:

```bash
sudo -u gitcode-evolver bash \
  examples/gitcode_issue_evolver/scripts/run-service.sh \
  --config /etc/gitcode-issue-evolver/evolver-config.json \
  --secrets /etc/gitcode-issue-evolver/evolver-secrets.json \
  --llm-config /etc/gitcode-issue-evolver/apiconfig.json \
  --check
```

## systemd

Install and start the provided unit:

```bash
sudo install -o root -g root -m 0644 \
  examples/gitcode_issue_evolver/deploy/systemd/gitcode-issue-evolver.service \
  /etc/systemd/system/gitcode-issue-evolver.service
sudo systemctl daemon-reload
sudo systemctl enable --now gitcode-issue-evolver.service
```

The Java process remains in the foreground and is the systemd MainPID.
`systemctl stop` sends SIGTERM, allowing the JVM shutdown hook to close the
HTTP listener, worker threads, and SQLite store. `processes.json` and
cloudflared are not used by this service.

Check service status and follow console output:

```bash
systemctl status gitcode-issue-evolver.service
journalctl -u gitcode-issue-evolver.service -f
curl --fail http://127.0.0.1:8081/health/ready
```

When `manualFullScanEnabled` is true, a local administrator can request a
full scan of every open Issue carrying the exact configured label. This
operation ignores only the rolling creation-time window and keeps durable
lifetime admission deduplication:

```bash
curl --fail-with-body -X POST \
  -H 'X-Issue-Evolver-Admin: full-scan' \
  http://127.0.0.1:8081/admin/poll/full
```

HTTP 202 means the asynchronous scan was accepted. HTTP 409 means a scheduled
scan or another full scan is still running. The endpoint is not registered
unless explicitly enabled and configuration validation requires the listener
to remain on `127.0.0.1`.

When `codeCheckFeedbackEnabled` is true, polling also inspects comments from the
exact `codeCheckBotLogin`, reads supported OpenLibing reports through the
restricted adapter, and updates the same publication branch after a failed
report. Completion requires both a merged PR and the exact
`codeCheckSuccessLabel`. The restricted adapter posts the opaque report TASK
ID, UUID, and Project ID directly to the fixed OpenLibing API origin. It does
not probe the HTML page, issue HEAD requests, or send Cookie, CSRF, GitCode PAT,
or model credentials. Treat the complete report URL as a sensitive capability
link and keep it out of public logs.

The bugfix Agent shares the bounded-context/model reliability harness used by
Feature Evolver. Each primary or diagnostic repair tier retains one
conversation. The Agent may call only the zero-argument `runApprovedGate`;
the Controller owns the verification commands and repeats the Gate after the
final model response. Deterministic Gate results are reused by the current
file fingerprint. Configure `maxPrimaryRepairRounds`,
`maxDiagnosticRepairRounds`, and `maxTransientStageRetries` to bound recovery.
When smoke is enabled, readiness reports the non-secret `TARGETED_SMOKE`
profile and selector count. It does not disclose repository paths or test
output.

Logback also writes rotating files below
`/var/log/gitcode-issue-evolver`. These logs can contain Issue text, source
code, tool output, and model messages; restrict access to the service
operator.

## Nginx and HTTPS (webhook or both only)

Skip this section when `triggerMode` is `polling`. Polling uses outbound
GitCode REST API calls and requires no public inbound route.

Replace the sample hostname and certificate paths before installing the
configuration:

```bash
sudo install -o root -g root -m 0644 \
  examples/gitcode_issue_evolver/deploy/nginx/gitcode-issue-evolver.conf \
  /etc/nginx/conf.d/gitcode-issue-evolver.conf
sudo nginx -t
sudo systemctl reload nginx
```

Allow inbound TCP 443 in the host and provider firewalls. Keep port 8081
closed externally; the Java configuration binds it to `127.0.0.1`.

Verify the public route:

```bash
curl --fail https://evolver.example.com/health/ready
```

Configure the GitCode Issue and Pull Request webhook manually:

```text
https://evolver.example.com/webhooks/gitcode
```

The Webhook Secret entered in GitCode must exactly match the external local
secrets file. Do not create test Issues, tags, comments, pushes, pull
requests, or merges from deployment scripts.

## Updating

Stop the service before replacing code or backing up SQLite:

```bash
sudo systemctl stop gitcode-issue-evolver.service
sudo -u gitcode-evolver git -C /opt/gitcode-issue-evolver/repo fetch
sudo -u gitcode-evolver git -C /opt/gitcode-issue-evolver/repo \
  checkout <deployment-ref>
sudo -u gitcode-evolver bash \
  /opt/gitcode-issue-evolver/repo/examples/gitcode_issue_evolver/scripts/build-demo.sh
sudo systemctl start gitcode-issue-evolver.service
```

Back up `/var/lib/gitcode-issue-evolver/data/auto-evolving.db` only while the
service is stopped. Keep the external configuration and persistent state out
of Git updates.
