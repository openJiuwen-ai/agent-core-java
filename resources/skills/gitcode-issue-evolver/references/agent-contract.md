# Installation Agent contract

## Required capabilities

The installation Agent must be configured before Skill registration and must have only the setup capabilities it needs:

- `readFile`, so the Agent can load this Skill and its references.
- `executeCmd`, so the Agent can invoke the deterministic PowerShell management script.

The Skill does not attach tools. In agent-core-java, register the relevant local SysOperation cards with the installation Agent before calling `registerSkill(...)`.

Do not grant this installation Agent GitCode API, merge, browser automation, or unrestricted credential tools. `writeFile` is not required because runtime files are created by trusted scripts.

## Separation from the worker

The installation Agent and the Issue worker are different trust domains:

- The installation Agent may launch Maven, Java, and cloudflared through `manage.ps1`.
- The Issue worker receives only Worktree-scoped read, search, and write tools.
- The Issue worker never receives GitCode Token, Shell, HTTP, push, PR, or merge capabilities.

The Java service runs as a background process after the installation Agent returns.

## Credential boundary

The user must prepare these local files before Start:

- `examples/gitcode_issue_evolver/config/evolver-secrets.local.json`
- `examples/apiconfig.json`

The Agent must not read either file. It may pass their paths to `manage.ps1`; the Java service loads them inside the service process. Never put their values in prompts, terminal arguments, logs, repository files, or reports.