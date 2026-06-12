# openjiuwen.agent_teams.models.allocator

Java outputs:

- `com.openjiuwen.agent_teams.models.Allocation`
- `com.openjiuwen.agent_teams.models.ModelAllocator`
- `com.openjiuwen.agent_teams.models.ModelAllocators`
- `com.openjiuwen.agent_teams.models.RoundRobinModelAllocator`
- `com.openjiuwen.agent_teams.models.ByModelNameAllocator`
- `com.openjiuwen.agent_teams.models.RouterAllocator`

Mirrors Python's `openjiuwen/agent_teams/models/allocator.py`.

The Java translation preserves the pool allocation strategies from Python:
round-robin over the whole pool, lookup by `model_name` with per-group
rotation, and router-style lookup with a deterministic first-entry default.
Allocator state is serialized with the same JSON-friendly keys used by Python
so restart recovery can persist and reload rotation counters.
