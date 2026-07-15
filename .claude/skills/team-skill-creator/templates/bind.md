# Bind: <Team Name> Constraints and Failure Handling

## Resource Constraints

| Constraint | Limit | Description |
|---|---|---|
| `max_parallel_teammates` | <N> | Maximum <N> teammates in parallel |
| `total_wall_clock_budget` | <N> minutes | Maximum execution time for the entire process |
| `total_token_budget` | <N> tokens | Maximum token consumption for the entire process |
| `per_role_token_limit` | <N> tokens | Maximum token consumption per role |
| `max_retry_per_step` | 2 | Maximum 2 retries per step |

### Per-role asymmetric limits

| Role | Token Limit | Time Limit | Special Constraint |
|---|---|---|---|
| <role-1> | <N> | <N> minutes | <special requirement> |
| <role-2> | <N> | <N> minutes | <special requirement> |

## Behavioral Constraints

### Team-level rules

1. **Leader does not generate content**: Leader is only responsible for task distribution, quality gating, and report consolidation
2. **<Visibility rule 1>**: <description>
3. **<Visibility rule 2>**: <description>

### Phase-scoped visibility rules

| Phase | Visibility | Description |
|---|---|---|
| Step <N> | <Fully isolated / Directly visible / Leader visible> | <description> |

## Failure Handling

| Failure Scenario | Handling Strategy |
|---|---|
| Teammate execution fails | Retry 2; if still fails, degrade or skip |
| Input overload | Truncate + prompt, continue execution |
| Quality gate fails | Loop back to upstream role for redo |
| Debate fails | Fallback: each outputs independent conclusion |
| Complete failure | Output error report, marking failure points |
