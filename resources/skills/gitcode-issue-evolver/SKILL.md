---
name: gitcode-issue-evolver
description: Set up, validate, start, inspect, or stop the Windows GitCode Issue Evolver polling or webhook demo. Use only when the user explicitly asks an installation Agent to prepare or run examples/gitcode_issue_evolver, check its local file-based configuration, or report service status and any temporary GitCode webhook URL. Requires readFile and executeCmd; never read secret or model configuration contents.
---

# GitCode Issue Evolver Setup

Use this Skill only from a privileged installation Agent. Keep that Agent separate from the restricted Issue worker Agent.

## Start workflow

1. Read `references/agent-contract.md` and enforce its tool and credential boundary.
2. If the repository root contains `AGENTS.md`, read and follow it. Treat its `pom.xml` as the
   canonical Maven configuration. Then confirm the root contains both `pom.xml` and
   `examples/gitcode_issue_evolver`.
3. Do not open, print, summarize, copy, or edit `evolver-secrets.local.json` or `examples/apiconfig.json`.
4. Run the deterministic check command:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  resources/skills/gitcode-issue-evolver/scripts/manage.ps1 `
  -Action Check `
  -RepositoryRoot "<repository-root>"
```

5. If Check reports a missing or invalid local configuration, stop before Maven or Start. Read
   `references/configuration.md` and the matching committed `.example.json` template, then help the
   user prepare the local file:
   - Identify both required local paths and source templates. If either `.local.json` file is
     missing, explain both files in one response so the user can prepare them in one pass.
   - Explain every field the user must review, in the user's language.
   - Show only the sanitized JSON skeleton from the reference; never show or infer a real value.
   - Help choose non-secret repository, branch, path, port, and Assignee values.
   - Tell the user to enter the Evolver Bot Token and model credentials locally. Require a Webhook
     Secret only for `webhook` or `both`. Never ask the user to paste credentials into the conversation.
   - Wait for the user to confirm that the local files are ready, then rerun Check. Do not bypass it.
   Before returning, treat the configuration help as incomplete unless it includes all of:
   - Meanings for `bindHost`, `port`, `dataDir`, `worktreeRoot`, `localRepository`,
     `codingStandardSkill`, `issueWorkerSkill`, `targetRepository`, `publishRepository`,
     `baseBranch`, `assignees`, `workerConcurrency`, `triggerMode`, `triggerLabel`,
     `issueScanWindowHours`, `pollIntervalMinutes`, `maxIssueScanPages`,
     `manualFullScanEnabled`, `maxPrimaryRepairRounds`, `maxDiagnosticRepairRounds`,
     `maxTransientStageRetries`, `smokeTestEnabled`, `smokeTestRepository`,
     `smokeTestSelectors`, `smokeTestTimeoutMinutes`, `gitUserName`, and `gitUserEmail`.
   - Meanings for `gitCodeToken` and the conditionally required `webhookSecret`, without requesting
     their values or confusing the Bot Token with a user's personal Issue-submission PAT.
   - Both sanitized JSON skeletons from the reference.
   Do not return only the first Check error or a summary of selected fields.
6. Before starting the service, run the Example-owned deterministic suite. Do not run the main
   repository's complete Maven test suite for this independent service:

```powershell
bash examples/gitcode_issue_evolver/scripts/test-demo.sh
```

   The script compiles the Example and runs its deterministic tests. Do not skip a failing Example
   test or reinterpret a main-project Maven failure as an Evolver test result.
7. When the Example gate succeeds, run the same management command with `-Action Start`. The Example
   start script performs compilation with Maven tests skipped; do not treat that compilation as
   another test result.
8. Report only the structured status, trigger mode, local health URL, optional public health and
   webhook URLs, mode-specific manual GitCode steps, and the Example deterministic gate result.

## Other actions

Use `-Action Status` to inspect the non-secret process state. Use `-Action Stop` only when the user explicitly asks to stop or restart the demo. Except for the pre-start Example gate defined above, do not replace these actions with handwritten process, Maven, Java, or cloudflared commands.

## Hard boundaries

- Never pass a Token, API Key, or Webhook Secret as a command-line argument.
- Never enumerate environment variables or inspect process environments.
- Never configure GitCode Webhooks, labels, branches, permissions, or repository settings.
- Never push, create a PR, comment on an Issue, or merge while setting up the service.
- Never grant setup Shell tools to the Issue worker Agent.
- Treat a temporary Quick Tunnel URL as runtime output, not repository configuration. Polling-only
  mode has no Quick Tunnel or Webhook URL.

Read `references/configuration.md` only when Check reports a missing prerequisite or invalid non-secret runtime configuration.
