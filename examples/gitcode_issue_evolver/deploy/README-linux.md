# GitCode Issue Evolver on Linux

This deployment keeps the Java service on the loopback interface, runs it as
a foreground systemd service, and exposes only the GitCode webhook and
readiness endpoint through an HTTPS reverse proxy.

The Quick Tunnel script remains available for an interactive demo. It is not
part of the long-running deployment.

## Prerequisites

- A Linux host with a stable public address
- A DNS name pointing to the host
- JDK 17, Maven, Git, Bash, curl, and Nginx
- Outbound access to GitCode, the configured model endpoint, and Maven
  repositories
- An HTTPS certificate trusted by GitCode

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
```

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

Logback also writes rotating files below
`/var/log/gitcode-issue-evolver`. These logs can contain Issue text, source
code, tool output, and model messages; restrict access to the service
operator.

## Nginx and HTTPS

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
