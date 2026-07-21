# Installation Agent contract

## Required capabilities

The installation Agent must be configured before Skill registration and must have only the setup capabilities it needs:

- `readFile`, so the Agent can load this Skill and its references.
- `executeCmd`, so the Agent can invoke the deterministic PowerShell management script, the test-only
  Maven gate, and the bounded test-file skip handling below.

The Skill does not attach tools. In agent-core-java, register the relevant local SysOperation cards with the installation Agent before calling `registerSkill(...)`.

Do not grant this installation Agent GitCode API, merge, browser automation, or unrestricted credential tools. `writeFile` is not required because runtime files are created by trusted scripts.

## Separation from the worker

The installation Agent and the Issue worker are different trust domains:

- The installation Agent may launch the temporary Maven gate directly. It launches Java and
  cloudflared only through `manage.ps1`.
- The Issue worker receives only Worktree-scoped read, search, and write tools.
- The Issue worker never receives GitCode Token, Shell, HTTP, push, PR, or merge capabilities.

The Java service runs as a background process after the installation Agent returns.

## Credential boundary

The user must prepare these local files before Start:

- `examples/gitcode_issue_evolver/config/evolver-secrets.local.json`
- `examples/apiconfig.json`

The Agent may read the committed `.example.json` templates, `references/configuration.md`, and the
non-secret `evolver-config.local.json` when it needs to diagnose a validation error. The Agent must
not read the local secrets file or model configuration. It may pass their paths to `manage.ps1`; the
Java service loads them inside the service process. Never put their values in prompts, terminal
arguments, logs, repository files, or reports.

## Maven test skip policy

The gate may execute only the Maven `test` lifecycle phase. The first attempt is:

```powershell
mvn.cmd -B -ntp test
```

Do not add `clean`, `package`, `verify`, or `install`. Do not skip all tests or replace POM-defined
normal test discovery with `-Dtest`.

Apply the following procedure when an exact active test file is associated with more than 120
consecutive seconds of Maven/Surefire console silence, or when Surefire reports a test failure or
error:

1. Track each Surefire line of the form `Running <fully-qualified-class>`, its matching class summary,
   and the timestamp of every Maven/Surefire console line. Treat only a matching summary or report
   created after this gate began as completion. Ignore stale reports from an earlier run.
2. When exactly one active top-level test class can be identified and the Maven gate produces no
   console output for more than 120 consecutive seconds, classify only that source test file as
   `TIMEOUT`. Any new console output resets the silence timer; total test runtime greater than 120
   seconds is not sufficient by itself. Silence during Maven compilation or other non-test work is not
   a test-file timeout. If no exact active test class can be identified, or multiple active classes
   make attribution ambiguous, do not guess an exclusion.
3. When Surefire reports a failure or error for an exact top-level test class, classify that source
   test file as `TEST_ERROR`. Capture every failing method in that file, the
   exception or assertion type, its message, and the most relevant project stack frame. Read only the
   Maven console output or the Surefire `<failure>` and `<error>` elements needed for that reason;
   never copy complete reports, `system-out`, `system-err`, environment values, or configuration
   values. Redact any credential-like value from the reported reason.
4. For a timeout, terminate only the verified Maven gate process and its Surefire descendants. Do
   not terminate unrelated Maven or Java processes. For reported test errors, allow an otherwise
   progressing Maven attempt to finish so all failing test files can be collected. If a timeout
   interrupts that attempt, retain already reported `TEST_ERROR` files for the retry. Do not inspect
   process environments or unrelated command lines.
5. Create a new, run-scoped exclusions file under the repository `target` directory. Convert each
   fully qualified top-level test class to its exact slash-separated `.java` pattern and append only
   newly timed-out or erroring test files. Do not seed the file with known failures or reuse exclusions
   from an older gate.
6. Rerun only the Maven `test` phase:

   ```powershell
   mvn.cmd -B -ntp test "-Dsurefire.excludesFile=<absolute-run-scoped-exclusions-file>"
   ```

   Repeat when another exact active test file is associated with more than 120 consecutive seconds of
   console silence or reports a Surefire failure/error. Tests interrupted while a peer is removed must
   run again unless they independently qualify for `TIMEOUT` or `TEST_ERROR`.
7. Build, compilation, dependency, configuration, plugin, or non-test failures fail the gate and must
   never be excluded. A failure without an exact Surefire test class must not be guessed into the list.
8. Gate success requires a retry to reach Maven `BUILD SUCCESS`. Report the POM default tag exclusions
   and every dynamically skipped file. For `TIMEOUT`, report the observed console-silence duration.
   For `TEST_ERROR`, report the failing method(s), exception/assertion type, sanitized message, and
   relevant project stack frame. Never describe this result as a complete test-suite pass.

During a long-running gate, send the user a concise progress update at least once per minute, without
repeating unchanged test logs.
