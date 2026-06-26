# openjiuwen.agent_teams.models.pool

Java outputs:

- `com.openjiuwen.agent_teams.models.ModelPoolEntry`
- `com.openjiuwen.agent_teams.models.ModelRouterConfig`
- `com.openjiuwen.agent_teams.models.ModelPoolSupport`

Mirrors Python's `openjiuwen/agent_teams/models/pool.py`.

The Java translation preserves model-pool entries, router expansion, metadata
merging into client/request model configuration, generated runtime `model_id`
values, and bit-exact `model_id` inheritance across pool refreshes.
