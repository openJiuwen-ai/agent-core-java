# Bind: Dev-Test Parallel Team Constraints and Failure Handling

## Resource Constraints

| Constraint | Limit | Description |
|---|---|---|
| `max_parallel_teammates` | 2 | Two people in parallel |
| `total_wall_clock_budget` | 10 minutes | Maximum execution time for the entire process |
| `total_token_budget` | 16,000 tokens | Maximum token consumption for the entire process |
| `per_role_token_limit` | 8,000 tokens | Maximum token consumption per role |
| `max_retry_per_step` | 2 | Maximum 2 retries per step |

### Per-role asymmetric limits

| Role | Token Limit | Time Limit | Special Constraint |
|---|---|---|---|
| developer | 8,000 | 5 minutes | Must output complete runnable code |
| tester | 8,000 | 5 minutes | Must include boundary cases and exception paths |

## Behavioral Constraints

### Team-level rules

1. **Leader does not generate content**: Leader is only responsible for task distribution and report consolidation; does not write code or tests
2. **developer and tester are invisible to each other**: Cannot see each other's output during parallel work
3. **No cross-modification**: tester cannot modify developer's code, developer cannot modify tests

### Phase-scoped visibility rules

| Phase | Visibility | Description |
|---|---|---|
| Step 1 (Parallel Execution) | Fully isolated | developer and tester cannot see each other's output |
| Final Report | Leader visible | leader sees both outputs and consolidates them |

## Failure Handling

| Failure Scenario | Handling Strategy |
|---|---|
| developer fails | Retry 2; if still fails, leader reports the failure point; tester can still write tests based on the failure information |
| tester fails | Retry 2; if still fails, leader only consolidates developer output, noting test absence |
| python3 unavailable | Terminate (required: true) |
| Timeout | Force-end current step; leader reports completed portions |
