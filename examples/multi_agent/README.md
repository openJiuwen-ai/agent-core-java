# multi_agent 示例

本目录对应 Python `examples/multi_agent` 的第一层 Java 对齐结果。

当前 Java 侧已补齐：

- `TeamCard`
- `EventDrivenTeamCard`
- `TeamConfig`
- `BaseTeam`
- `TeamRuntime`
- `CommunicableAgent`
- 订阅管理与本地点对点 / 发布订阅运行语义

建议先通过测试验证：

```bash
mvn -Dtest=TeamRuntimeCompatibilityTest,SubscriptionManagerCompatibilityTest test
```

当前阶段仍是本地进程内 runtime，对齐的是 Python `core.multi_agent` 的公共 API 与最小运行面；
更完整的内建 team（如 handoff / hierarchical）将继续在后续模块迁移中补齐。
