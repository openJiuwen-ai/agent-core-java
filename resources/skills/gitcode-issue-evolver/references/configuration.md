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
- cloudflared for `webhook` or `both`; polling-only does not need it

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
- `triggerMode`: `polling`, `webhook`, or `both`. Old files without this field default to
  `webhook`; the new template uses `polling`.
- `triggerLabel`: one case-sensitive GitCode label. The default is `bug`.
- `issueScanWindowHours`: polling accepts only Issues created within this frozen lookback window.
  The default is `24`.
- `pollIntervalMinutes`: fixed delay after one polling cycle finishes. The default is `15`.
- `maxIssueScanPages`: maximum 100-item pages processed in one cycle before persisting a resume
  checkpoint. The default is `10`.
- `manualFullScanEnabled`: when `true`, registers a loopback-only administrative endpoint that
  scans all open Issues carrying the exact trigger label without a creation-time window. It
  requires `bindHost` `127.0.0.1` and `polling` or `both`; the default is `false`.
- `codeCheckFeedbackEnabled`: enables trusted robot comment inspection, controlled OpenLibing
  report extraction, same-PR repair, and the CI-success completion gate. It requires `polling` or
  `both`; existing configurations default to `false`.
- `codeCheckStandardOnlyOverride`: when `true`, CodeCheck jobs ignore Issue-authored remediation
  suggestions and comment-authored repair judgments. The complete rule text, repository evidence,
  and fixed Controller Gate remain authoritative. The default is `false`.
- `codeCheckBotLogin`: exact trusted GitCode robot login. The default is `openJiuwen-bot`.
- `codeCheckSuccessLabel`: exact PR label required in addition to merge before the Job becomes
  `MERGED`. The default is `ci-successful`.
- `openLibingBaseUrl`: plain HTTPS origin allowed by the controlled report reader. Do not include a
  path, query, credentials, or fragment.
- `openLibingTimeoutSeconds`: bounded report request timeout, from 5 through 300 seconds.
- `openLibingMaxFindings`: maximum structured findings admitted to repair context, from 1 through
  200.
- `maxPrimaryRepairRounds`: maximum same-conversation Controller repair rounds before independent
  diagnosis. The default is `5`.
- `maxDiagnosticRepairRounds`: maximum independent diagnostic repair rounds. The default is `3`.
- `maxTransientStageRetries`: maximum scheduled retries for classified transient model, GitCode,
  or infrastructure failures. The default is `5`.
- `smokeTestEnabled`: enables the immutable JiuwenTestJava smoke Gate before commit and PR
  publication. Existing configurations default to `false`; the new template enables it.
- `smokeTestRepository`: isolated local Git checkout of JiuwenTestJava. It must contain `pom.xml`
  and `src/test/java`, remain outside the source repository, Worktree root, and Skill directories,
  and be writable only as needed for Maven `target/` output. Stop the service before updating it.
- `smokeTestSelectors`: between one and three exact fully qualified Java test class names. The
  Controller runs only these selectors; the worker Agent cannot modify the list.
- `smokeTestTimeoutMinutes`: total timeout for installing the current repaired source version with
  tests skipped and running the exact smoke selection. The default is `30`.
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
  "triggerMode": "polling",
  "triggerLabel": "bug",
  "issueScanWindowHours": 24,
  "pollIntervalMinutes": 15,
  "maxIssueScanPages": 10,
  "manualFullScanEnabled": false,
  "codeCheckFeedbackEnabled": true,
  "codeCheckStandardOnlyOverride": false,
  "codeCheckBotLogin": "openJiuwen-bot",
  "codeCheckSuccessLabel": "ci-successful",
  "openLibingBaseUrl": "https://www.openlibing.com",
  "openLibingTimeoutSeconds": 60,
  "openLibingMaxFindings": 100,
  "maxPrimaryRepairRounds": 5,
  "maxDiagnosticRepairRounds": 3,
  "maxTransientStageRetries": 5,
  "smokeTestEnabled": true,
  "smokeTestRepository": "../jiuwen-test-java",
  "smokeTestSelectors": ["com.openjiuwen.test.cases.workflow_drawable.WorkflowDraw001Test"],
  "smokeTestTimeoutMinutes": 30,
  "gitUserName": "gitcode-issue-evolver",
  "gitUserEmail": "gitcode-issue-evolver@localhost"
}
```

## Local secrets fields

Create `examples/gitcode_issue_evolver/config/evolver-secrets.local.json` from the committed secrets
template and edit it manually outside the Agent interaction.

- `gitCodeToken`: Evolver Bot GitCode token used only by the non-Agent API client and Publisher.
  Grant the minimum permissions needed to read Issues and PRs, comment and create PRs in the target
  repository, and push branches to the publish repository. Do not grant merge or repository
  administration capability. This is separate from any personal PAT used to submit an Issue.
- `webhookSecret`: for `webhook` or `both`, use a random shared secret with at least 32 UTF-8 bytes
  that exactly matches the Secret entered manually in GitCode. Leave it empty for polling-only.

OpenLibing report reads use the opaque identifiers in the trusted robot's report URL and require no
Cookie, CSRF token, GitCode PAT, or OAuth browser session. Treat the complete report URL as a
sensitive capability link. The adapter uses only bounded anonymous POST requests to the exact
`openLibingBaseUrl` and never probes the HTML page with GET or HEAD.

Show only this placeholder structure. Do not generate, request, validate, or echo the real values:

```json
{
  "gitCodeToken": "REPLACE_WITH_MINIMUM_PERMISSION_GITCODE_TOKEN",
  "webhookSecret": ""
}
```

Both `.local.json` files and `examples/apiconfig.json` must remain untracked. Before any commit,
verify their paths are ignored without printing their contents.

## Manual GitCode work

For polling-only, no public address or GitCode Webhook configuration is required. The service starts
an immediate scan and then waits the configured fixed delay.

For `webhook` or `both`, after Start succeeds, manually configure the returned `/webhooks/gitcode`
URL for Issue and Pull Request events. Use the same local Webhook Secret without pasting it into the
Agent conversation.

Create the configured trigger label if needed. Polling accepts only open Issues created inside the
configured window with an exact case-sensitive label match. Webhook admission requires an update
that explicitly adds the configured label. Review and merge remain manual; the service never exposes
a merge operation.

Quick Tunnel URLs are temporary. Repeat Start or Status after a restart and update GitCode manually when the URL changes.
