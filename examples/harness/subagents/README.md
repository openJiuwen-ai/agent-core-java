# Harness Subagents Baseline

The Java harness subagents baseline currently exposes:

- `SubAgentConfig`
- `buildExploreAgentConfig`
- `buildPlanAgentConfig`
- `buildCodeAgentConfig`
- `buildResearchAgentConfig`
- `buildVerificationAgentConfig`
- matching `create*Agent` helpers

These map the Python public factory layer into Java while the deeper subagent runtime behavior is still being ported.
