# GitCode Feature Evolver on Linux

This deployment runs the feature service independently from
`gitcode-issue-evolver`. It uses its own service account, systemd unit, port,
configuration, SQLite database, Worktrees, Maven cache, bot token, and logs.
Do not point both services at the same writable repository checkout or state
directory.

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
/var/log/gitcode-feature-evolver
```

The root helpers are designed for this layout and reject arbitrary paths. The
unit and helpers must be installed and owned by root before granting
passwordless execution:

```bash
sudo install -o root -g root -m 0644 \
  examples/gitcode_feature_evolver/deploy/systemd/gitcode-feature-evolver.service \
  /etc/systemd/system/gitcode-feature-evolver.service
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
```

These are sudoers policy entries, not commands to execute in a shell. Install
them with `visudo`. The first helper accepts only the optional literal
`--activate`; the second accepts no arguments. Never grant passwordless
`podman`, `bash`, `runuser`, `systemctl`, or a writable helper script.
The provision helper verifies the root-owned unit against its compiled-in
digest; after changing the unit, reinstall the matching helper and unit as root.

## Phase 1: rootless account and repository

Run the helper once to create the dedicated account, subordinate UID/GID
ranges, fixed directories, and rootless runtime boundary:

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

Create `feature-secrets.json` from the sanitized example. `gitCodeToken` is the
dedicated Feature Evolver bot PAT used to read Issues/comments, push its feature
branches, create/update one PR, and comment on the source Issue. It is not the
personal PAT used by a human to submit Issues, and it is not the bug service bot
token. `webhookSecret` is required only for `webhook` or `both`.

The account names in `approverLogins` are authenticated command identities;
they are not tokens. Give the feature bot no merge, protected-branch bypass,
deployment, repository-administration, or webhook-administration permission.

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

The final provision call pulls the pinned public image into the service
account's rootless Podman storage using an empty auth file. No GitCode, model,
SSH, Git credential-helper, or personal PAT is copied into that storage.

## Phase 3: mandatory deployment gate

The service must be stopped while this gate runs. The fixed helper:

1. creates a clean detached Worktree, builds the Example, and runs deterministic
   controller tests inside the credential-free rootless container, then copies
   only checked, non-symlink runtime outputs to the clean deployment checkout;
2. validates external config and rootless-image readiness;
3. populates only the dedicated Maven cache from a clean detached Worktree;
4. creates a second clean Worktree and runs the real full Maven gate offline,
   networkless, non-root, resource-limited, and credential-free, with that
   shared cache mounted read-only;
5. records a root-owned stamp bound to the Git commit, runtime config, bot/Webhook
   secrets, model configuration, and image digest. Only hashes—not secret values—
   are written to the stamp.

```bash
sudo -n /usr/local/sbin/run-feature-evolver-test
```

Changing the commit, either credential file, runtime configuration, or image
invalidates the stamp.
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
```

Logback writes rotating files under `/var/log/gitcode-feature-evolver` when
configured by the shared project logging setup. SQLite state is at
`/var/lib/gitcode-feature-evolver/data/feature-evolving.db`. Logs and durable
artifacts can contain Issue text, model output, code, and test output; restrict
them to operators even though container credentials are excluded.

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
