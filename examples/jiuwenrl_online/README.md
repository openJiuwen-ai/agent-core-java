# JiuwenRL Online Baseline

The Python repository ships a full online RL loop launcher. The current Java baseline does not port that launcher yet.

What Java does expose today:

- dataset / evaluator / trainer building blocks under `com.openjiuwen.agentevolving`
- example support helpers via `examples.agent_evolving.AgentEvolvingExampleSupport`

Suggested verification:

```bash
mvn -Dtest=TrainerMissingTest,OfflineConfigSchemaTest,OnlineRLConfigTest test
```