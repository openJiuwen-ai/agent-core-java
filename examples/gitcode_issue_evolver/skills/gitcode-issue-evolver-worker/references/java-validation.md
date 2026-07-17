# Java validation

The Agent must follow the staged `coding-standard` Skill. The demo service, not the Agent, runs this trusted argument-list command in the Worktree:

```text
mvn -B -ntp -DskipTests test-compile
```

This compiles main and test sources but does not execute tests. A deterministic compiler failure may be returned to the Agent for a small repair attempt. Tool, Skill, process-start, and Maven-launch failures are infrastructure errors and remain retryable.
