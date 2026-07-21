# Configuration and manual prerequisites

## Assistance contract

When Check reports a missing or invalid file, explain both local JSON files in the user's language
so they can be prepared in one pass. Read only the committed templates and this reference. The
runtime configuration is non-secret and may be inspected for validation support. Never open the
local secrets file or `examples/apiconfig.json`, and never ask the user to paste their values into a
prompt.

## Local prerequisites

Install these tools before invoking the Skill:

- Windows with `powershell.exe`
- JDK 17 with `java` and `javac` on `PATH`
- Maven with `mvn.cmd` on `PATH`
- Git
- cloudflared

The Skill checks these tools but does not install or upgrade them.

## Local files

Create the non-secret runtime file from:

`examples/gitcode_issue_evolver/config/evolver-config.example.json`

Save it as:

`examples/gitcode_issue_evolver/config/evolver-config.local.json`

Create the ignored local secrets file from:

`examples/gitcode_issue_evolver/config/evolver-secrets.example.json`

Do not ask the Agent to fill it. Configure the model in `examples/apiconfig.json` outside the Agent interaction.

The target repository defaults to `openJiuwen/agent-core-java` and the base branch defaults to `730`. Set `publishRepository` to the user's Fork and provide at least one GitCode Assignee. Runtime and Worktree directories must remain outside the target repository.

## Runtime configuration fields

Create `examples/gitcode_issue_evolver/config/evolver-config.local.json` from the committed runtime
template. It contains no credentials.

- `bindHost`: local HTTP bind address. Keep `127.0.0.1` for the Quick Tunnel demo.
- `port`: free local TCP port in the range 1-65535. The example uses `8081`.
- `dataDir`: writable directory for SQLite job state and trusted runtime data. It must be outside
  `localRepository`. Relative paths resolve from the repository root.
- `worktreeRoot`: short, writable directory for per-Issue Git Worktrees. It must be outside
  `localRepository` and both trusted Skill directories. Prefer a short absolute Windows path.
- `localRepository`: local Git repository used as the baseline for Issue work. `.` means the
  repository passed to `manage.ps1`; it must contain `.git`.
- `codingStandardSkill`: trusted directory containing the coding-standard `SKILL.md`.
- `issueWorkerSkill`: trusted directory containing the restricted Issue worker `SKILL.md`.
- `targetRepository`: GitCode `owner/name` repository that owns Issues, receives PRs, and emits
  Webhooks.
- `publishRepository`: GitCode `owner/name` repository that receives `auto-evolving/*` branches.
  Use the target repository for same-repository testing or the user's Fork for cross-repository
  publishing.
- `baseBranch`: existing target branch used to prepare Worktrees and as the PR base, such as `730`.
- `assignees`: one or more GitCode usernames assigned to created PRs for human review.
- `workerConcurrency`: must remain `1` for the SQLite demo.
- `gitUserName`: non-secret Git author name used by the controlled committer.
- `gitUserEmail`: non-secret Git author email used by the controlled committer.

Use this sanitized structure and replace only the non-secret placeholders:

```json
{
  "bindHost": "127.0.0.1",
  "port": 8081,
  "dataDir": "../gitcode-issue-evolver-data",
  "worktreeRoot": "C:/short/path/gitcode-issue-evolver-worktrees",
  "localRepository": ".",
  "codingStandardSkill": "resources/skills/coding-standard",
  "issueWorkerSkill": "examples/gitcode_issue_evolver/skills/gitcode-issue-evolver-worker",
  "targetRepository": "organization/target-repository",
  "publishRepository": "your-account/fork-repository",
  "baseBranch": "730",
  "assignees": ["gitcode-reviewer"],
  "workerConcurrency": 1,
  "gitUserName": "gitcode-issue-evolver",
  "gitUserEmail": "gitcode-issue-evolver@localhost"
}
```

## Local secrets fields

Create `examples/gitcode_issue_evolver/config/evolver-secrets.local.json` from the committed secrets
template and edit it manually outside the Agent interaction.

- `gitCodeToken`: GitCode personal access token used only by the non-Agent API client and Publisher.
  Grant the minimum permissions needed to read Issues and PRs, comment and create PRs in the target
  repository, and push branches to the publish repository. Do not grant merge or repository
  administration capability.
- `webhookSecret`: random shared secret used to verify GitCode Webhook HMAC signatures. It must
  contain at least 32 UTF-8 bytes and exactly match the Secret entered manually in GitCode.

Show only this placeholder structure. Do not generate, request, validate, or echo the real values:

```json
{
  "gitCodeToken": "REPLACE_WITH_MINIMUM_PERMISSION_GITCODE_TOKEN",
  "webhookSecret": "REPLACE_WITH_RANDOM_SECRET_OF_AT_LEAST_32_UTF8_BYTES"
}
```

Both `.local.json` files and `examples/apiconfig.json` must remain untracked. Before any commit,
verify their paths are ignored without printing their contents.

## Manual GitCode work

After Start succeeds, manually configure the returned `/webhooks/gitcode` URL for Issue and Pull Request events. Use the same local Webhook Secret without pasting it into the Agent conversation.

Create the `bug` label if needed. The demo accepts only an Issue update whose label change explicitly adds `bug`. Review and merge remain manual; the service never exposes a merge operation.

Quick Tunnel URLs are temporary. Repeat Start or Status after a restart and update GitCode manually when the URL changes.