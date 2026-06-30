# JiuwenRL Online Baseline

The Python repository ships a full online RL loop launcher. The current Java baseline does not port that launcher yet.

What Java does expose today:

- dataset / evaluator / trainer building blocks
- example support helpers via `AgentEvolvingExampleSupport`

Suggested verification:

```bash
mvn -Dtest=AgentEvolvingExampleSupportTest test
```
