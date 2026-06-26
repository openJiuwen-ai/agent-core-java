# openjiuwen.agent_teams.models

对应 Python 文件：`openjiuwen/agent_teams/models/__init__.py`

该包聚合团队多模型部署相关符号，包括模型池条目、路由配置、分配结果、分配器策略、工厂函数和成员模型解析函数。

Java 对应类型：

- `com.openjiuwen.agent_teams.models.ModelsPackage`

导出符号：

- `Allocation`
- `ByModelNameAllocator`
- `ModelAllocator`
- `ModelPoolEntry`
- `ModelRouterConfig`
- `RoundRobinModelAllocator`
- `RouterAllocator`
- `build_model_allocator`
- `inherit_pool_ids`
- `resolve_member_model`

实现说明：

- 当前文件只对应 Python package facade 和 `__all__` ledger。
- `allocator.py` 与 `pool.py` 的具体实现由后续任务翻译，当前任务不提前扩大翻译范围。
