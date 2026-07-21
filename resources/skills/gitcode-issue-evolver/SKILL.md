---
name: gitcode-issue-evolver
description: Set up, validate, start, inspect, or stop the Windows GitCode Issue Evolver demo and its Cloudflare Quick Tunnel. Use only when the user explicitly asks an installation Agent to prepare or run examples/gitcode_issue_evolver, check its local file-based configuration, or report the temporary GitCode webhook URL. Requires readFile and executeCmd; never read secret or model configuration contents.
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
   - Tell the user to enter Token, Webhook Secret, and model credentials locally. Never ask the user
     to paste them into the conversation.
   - Wait for the user to confirm that the local files are ready, then rerun Check. Do not bypass it.
   Before returning, treat the configuration help as incomplete unless it includes all of:
   - Meanings for `bindHost`, `port`, `dataDir`, `worktreeRoot`, `localRepository`,
     `codingStandardSkill`, `issueWorkerSkill`, `targetRepository`, `publishRepository`,
     `baseBranch`, `assignees`, `workerConcurrency`, `gitUserName`, and `gitUserEmail`.
   - Meanings for `gitCodeToken` and `webhookSecret`, without requesting their values.
   - Both sanitized JSON skeletons from the reference.
   Do not return only the first Check error or a summary of selected fields.
6. Before starting the service, run only the Maven `test` lifecycle phase from the repository root:

```powershell
mvn.cmd -B -ntp test
```

   Do not invoke `clean`, `package`, `verify`, or `install`, and do not use `-DskipTests`,
   `-Dmaven.test.skip`, or a global `-Dtest` selector. Preserve the POM-defined discovery and tag
   policy.

   Track each test file from Surefire `Running <fully-qualified-class>` output and timestamp every
   Maven/Surefire console line. After an exact active top-level test file has been identified, if the
   Maven gate produces no console output for more than 120 consecutive seconds, follow the
   timeout-skip procedure in `references/agent-contract.md`: stop only the current Maven gate process
   tree, add that exact test file to a new run-scoped Surefire exclusions file, and rerun the Maven
   `test` phase with that file. Any new console output resets the 120-second silence timer, even when
   the test's total runtime exceeds 120 seconds. Do not infer a test-file timeout from compilation,
   other non-test work, or an ambiguous active class.

   Never prepopulate or reuse exclusions. If Surefire reports a test failure or error, add that exact
   test file to the same run-scoped exclusions file and rerun the Maven `test` phase. Record the
   failing method, exception or assertion type, message, and the most relevant project stack frame.
   Build, compilation, dependency, or configuration failures are not skippable.

   Report every skipped file as either `TIMEOUT` or `TEST_ERROR`. Include the observed console-silence
   duration for a timeout and the specific sanitized error reason for a test error. A successful retry
   is a Maven test gate with skips, not a complete repository test-suite pass. The POM continues to
   exclude `@Tag("system-test")` by default.
7. When the temporary Maven gate succeeds, run the same management command with `-Action Start`. The Example start script performs its own compilation with Maven tests skipped; do not treat that compilation as another test result.
8. Report only the structured status, local and public health URLs, webhook URL, manual GitCode steps,
   and the Maven test gate result, including every skipped file, its classification, and its reason.

## Other actions

Use `-Action Status` to inspect the non-secret process state. Use `-Action Stop` only when the user explicitly asks to stop or restart the demo. Except for the pre-start Maven gate defined above, do not replace these actions with handwritten process, Maven, Java, or cloudflared commands.

## Hard boundaries

- Never pass a Token, API Key, or Webhook Secret as a command-line argument.
- Never enumerate environment variables or inspect process environments.
- Never configure GitCode Webhooks, labels, branches, permissions, or repository settings.
- Never push, create a PR, comment on an Issue, or merge while setting up the service.
- Never grant setup Shell tools to the Issue worker Agent.
- Treat a temporary Quick Tunnel URL as runtime output, not repository configuration.

Read `references/configuration.md` only when Check reports a missing prerequisite or invalid non-secret runtime configuration.