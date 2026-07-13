# Bind: <团队名>约束与失败处理

## Resource Constraints

| 约束项 | 限制值 | 说明 |
|---|---|---|
| `max_parallel_teammates` | <N> | 最多 <N> 个 teammate 并行 |
| `total_wall_clock_budget` | <N>分钟 | 整个流程最长执行时间 |
| `total_token_budget` | <N> tokens | 整个流程最大 token 消耗 |
| `per_role_token_limit` | <N> tokens | 每个角色最大 token 消耗 |
| `max_retry_per_step` | 2 | 每个步骤最多重试 2 次 |

### Per-role asymmetric limits

| 角色 | Token 限制 | 时间限制 | 特殊约束 |
|---|---|---|---|
| <role-1> | <N> | <N>分钟 | <特殊要求> |
| <role-2> | <N> | <N>分钟 | <特殊要求> |

## Behavioral Constraints

### Team-level rules

1. **Leader 不生成内容**: Leader 只负责任务分发、质量门控和报告整合
2. **<可见性规则 1>**: <说明>
3. **<可见性规则 2>**: <说明>

### Phase-scoped visibility rules

| 阶段 | 可见性 | 说明 |
|---|---|---|
| Step <N> | <完全隔离 / 直接可见 / Leader 可见> | <说明> |

## Failure Handling

| 失败场景 | 处理策略 |
|---|---|
| teammate 执行失败 | 重试 2 次，仍失败则降级或跳过 |
| 输入过载 | 截断 + 提示，继续执行 |
| 质量门控失败 | 回流到上游角色重做 |
| 辩论失败 | 兜底：各自输出独立结论 |
| 完全失败 | 输出错误报告，标注失败点 |
