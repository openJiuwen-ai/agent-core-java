# Configuration and manual prerequisites

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

## Manual GitCode work

After Start succeeds, manually configure the returned `/webhooks/gitcode` URL for Issue and Pull Request events. Use the same local Webhook Secret without pasting it into the Agent conversation.

Create the `bug` label if needed. The demo accepts only an Issue update whose label change explicitly adds `bug`. Review and merge remain manual; the service never exposes a merge operation.

Quick Tunnel URLs are temporary. Repeat Start or Status after a restart and update GitCode manually when the URL changes.