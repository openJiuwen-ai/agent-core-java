# condition

`com.openjiuwen.core.workflow.condition` provides reusable workflow conditions over expressions, arrays, functions, session values, and numeric predicates.

## Types

| Type | Kind | Description |
| --- | --- | --- |
| [`AlwaysTrue`](./condition/AlwaysTrue.md) | `class` | Condition that always evaluates to true. |
| [`ArrayCondition`](./condition/ArrayCondition.md) | `class` | Loop condition over array items, resolving arrays from session state via input schema. |
| [`ArrayConditionInSession`](./condition/ArrayConditionInSession.md) | `class` | Loop condition over array items already stored in session (not from schema). |
| [`Condition`](./condition/Condition.md) | `class` | Abstract condition for workflow branching and loop control. |
| [`ExpressionCondition`](./condition/ExpressionCondition.md) | `class` | Condition that evaluates string expressions with variable substitution. Supports operators: `&&` (and), `||` (or), `!` (not), comparisons (`==, !=, , >=, in, not_in`), and functions: `length()`, `is_empty()`, `is_not_empty()`. Variables are referenced via `${variable_path`} syntax and resolved from session state. |
| [`FuncCondition`](./condition/FuncCondition.md) | `class` | Condition that wraps a callable predicate. |
| [`NumberCondition`](./condition/NumberCondition.md) | `class` | Loop condition based on iteration count, resolving limit from input schema. |
| [`NumberConditionInSession`](./condition/NumberConditionInSession.md) | `class` | Loop condition based on iteration count with limit stored directly (not from schema). |

## Notes

- The current page also links the 8 direct public type page(s) defined in this package.
- Representative workflow behavior is covered by `WorkflowTest.java` for the core runtime and major component flows.
