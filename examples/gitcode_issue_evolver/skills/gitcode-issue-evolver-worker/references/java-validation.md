# Java validation

The Agent must explicitly load the complete staged `coding-standard-full` Skill copied from
`.claude/skills/coding-standard-full`. The shorter `resources/skills/coding-standard` Skill is only a compatibility
router. The demo service, not the Agent, first runs this trusted argument-list command in the Worktree:

```text
mvn -B -ntp -DskipTests test-compile
```

This compiles main and test sources but does not execute source-repository tests. When smoke is enabled, the Controller then installs the current Worktree version with source tests skipped and runs only the 1–3 exact JiuwenTestJava smoke classes from service configuration. The Agent cannot change the test repository, selectors, Maven arguments, or timeout.

A deterministic compiler or smoke assertion failure may be returned to the Agent for a bounded repair. Dependency resolution, process-start, and Maven-launch failures are infrastructure errors and remain retryable. Both stages must pass for the current combined fingerprint before commit and publication.
