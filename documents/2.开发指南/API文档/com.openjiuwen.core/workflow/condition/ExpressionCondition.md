# com.openjiuwen.core.workflow.condition.ExpressionCondition

## class ExpressionCondition

```java
public class ExpressionCondition extends Condition
```

Condition that evaluates string expressions with variable substitution. Supports operators: `&&` (and), `||` (or), `!` (not), comparisons (`==, !=, , >=, in, not_in`), and functions: `length()`, `is_empty()`, `is_not_empty()`. Variables are referenced via `${variable_path`} syntax and resolved from session state.

## Fields

| Signature | Description |
| --- | --- |
| `private final String expression` | Expression. |
| `private final List<String> matches` | Matches. |

## Constructors

| Signature | Description |
| --- | --- |
| `public ExpressionCondition(String expression)` | Create a new `ExpressionCondition` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `private static final Pattern VAR_PATTERN = Pattern.compile()` | Compile the workflow graph into an executable graph. |
| `public Object traceInfo(BaseSession session)` | Execute `traceInfo`. |
| `private Map<String, Object> getInputs(BaseSession session)` | Return the inputs. |
| `public Object doInvoke(Object inputs, BaseSession session)` | Execute `doInvoke`. |
| `public boolean evaluate(BaseSession session)` | Execute `evaluate`. |
| `private boolean evaluateExpression(String expr, Map<String, Object> inputs)` | Evaluate the expression with the given variable bindings. |
| `public static String convertCondition(String expr)` | Execute `convertCondition`. |
| `private static Object parseOrExpression(ExpressionParser parser)` | Execute `parseOrExpression`. |
| `private static Object parseAndExpression(ExpressionParser parser)` | Execute `parseAndExpression`. |
| `private static Object parseNotExpression(ExpressionParser parser)` | Execute `parseNotExpression`. |
| `private static Object parseComparison(ExpressionParser parser)` | Execute `parseComparison`. |
| `private static Object parseAddSub(ExpressionParser parser)` | Execute `parseAddSub`. |
| `private static Object parseMulDiv(ExpressionParser parser)` | Execute `parseMulDiv`. |
| `private static Object parseUnary(ExpressionParser parser)` | Execute `parseUnary`. |
| `private static Object parsePrimary(ExpressionParser parser)` | Execute `parsePrimary`. |
| `private static String toLiteral(Object value)` | Execute `toLiteral`. |
| `private static boolean toBoolean(Object value)` | Execute `toBoolean`. |
| `private static boolean objectEquals(Object left, Object right)` | Execute `objectEquals`. |
| `private static int objectCompare(Object left, Object right)` | Execute `objectCompare`. |
| `private static boolean objectIn(Object left, Object right)` | Execute `objectIn`. |
| `private static Object numericOp(Object left, Object right, char op)` | Execute `numericOp`. |
| `private static int safeLength(Object value)` | Execute `safeLength`. |
| `private static boolean safeIsEmpty(Object value)` | Execute `safeIsEmpty`. |
| `private static String typeName(Object value)` | Execute `typeName`. |

## Nested Types

| Type | Kind | Description |
| --- | --- | --- |
| `ExpressionParser` | `class` | Nested public type `ExpressionParser`. |
