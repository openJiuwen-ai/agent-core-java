---
name: gitcode-issue-evolver
description: Set up, validate, start, inspect, or stop the Windows GitCode Issue Evolver demo and its Cloudflare Quick Tunnel. Use only when the user explicitly asks an installation Agent to prepare or run examples/gitcode_issue_evolver, check its local file-based configuration, or report the temporary GitCode webhook URL. Requires readFile and executeCmd; never read secret or model configuration contents.
---

# GitCode Issue Evolver Setup

Use this Skill only from a privileged installation Agent. Keep that Agent separate from the restricted Issue worker Agent.

## Start workflow

1. Read `references/agent-contract.md` and enforce its tool and credential boundary.
2. Confirm the repository root containing `pom.xml` and `examples/gitcode_issue_evolver`.
3. Do not open, print, summarize, copy, or edit `evolver-secrets.local.json` or `examples/apiconfig.json`.
4. Run the deterministic check command:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  resources/skills/gitcode-issue-evolver/scripts/manage.ps1 `
  -Action Check `
  -RepositoryRoot "<repository-root>"
```

5. If Check reports missing local files, stop. Tell the user which template to prepare without reading either resulting secret file.
6. When Check succeeds, run the same command with `-Action Start`.
7. Report only the structured status, local and public health URLs, webhook URL, and manual GitCode steps returned by the script.

## Other actions

Use `-Action Status` to inspect the non-secret process state. Use `-Action Stop` only when the user explicitly asks to stop or restart the demo. Do not replace these actions with handwritten process, Maven, Java, or cloudflared commands.

## Hard boundaries

- Never pass a Token, API Key, or Webhook Secret as a command-line argument.
- Never enumerate environment variables or inspect process environments.
- Never configure GitCode Webhooks, labels, branches, permissions, or repository settings.
- Never push, create a PR, comment on an Issue, or merge while setting up the service.
- Never grant setup Shell tools to the Issue worker Agent.
- Treat a temporary Quick Tunnel URL as runtime output, not repository configuration.

Read `references/configuration.md` only when Check reports a missing prerequisite or invalid non-secret runtime configuration.