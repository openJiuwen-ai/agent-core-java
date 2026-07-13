# Bind: 开发-测试并行团队约束与失败处理

## Resource Constraints

| 约束项 | 限制值 | 说明 |
|---|---|---|
| `max_parallel_teammates` | 2 | 两人并行 |
| `total_wall_clock_budget` | 10分钟 | 整个流程最长执行时间 |
| `total_token_budget` | 16,000 tokens | 整个流程最大 token 消耗 |
| `per_role_token_limit` | 8,000 tokens | 每个角色最大 token 消耗 |
| `max_retry_per_step` | 2 | 每个步骤最多重试 2 次 |

### Per-role asymmetric limits

| 角色 | Token 限制 | 时间限制 | 特殊约束 |
|---|---|---|---|
| developer | 8,000 | 5分钟 | 必须输出完整可运行代码 |
| tester | 8,000 | 5分钟 | 必须包含边界用例和异常路径 |

## Behavioral Constraints

### Team-level rules

1. **Leader 不生成内容**: Leader 只负责任务分发和报告整合，不写代码也不写测试
2. **developer 和 tester 彼此不可见**: 并行工作时看不到对方输出
3. **不互相修改**: tester 不能改 developer 的代码，developer 不能改测试

### Phase-scoped visibility rules

| 阶段 | 可见性 | 说明 |
|---|---|---|
| Step 1 (并行执行) | 完全隔离 | developer 和 tester 彼此看不到输出 |
| Final Report | Leader 可见 | leader 看到两份输出并整合 |

## Failure Handling

| 失败场景 | 处理策略 |
|---|---|
| developer 失败 | 重试 2 次，仍失败则 leader 报告失败点，tester 仍可基于失败信息写测试 |
| tester 失败 | 重试 2 次，仍失败则 leader 仅整合 developer 输出，标注测试缺失 |
| python3 不可用 | 终止（required: true） |
| 超时 | 强制结束当前 step，leader 报告已完成的部分 |
