# com.openjiuwen.core.common.constants.Constant

## class Constant

```java
public final class Constant
```

`Constant` is the central holder for shared workflow keys, IR field names, and safe-execution limits.

## IR Fields

| Field | Type | Value | Description |
| --- | --- | --- | --- |
| `USER_FIELDS` | `String` | `"userFields"` | IR key for user-supplied fields. |
| `QUERY` | `String` | `"query"` | IR key for the active query string. |
| `SYSTEM_FIELDS` | `String` | `"systemFields"` | IR key for framework-managed system fields. |

## Workflow Keys

| Field | Type | Value | Description |
| --- | --- | --- | --- |
| `INTERACTION` | `String` | `"__interaction__"` | Workflow interaction marker. |
| `INTERACTIVE_INPUT` | `String` | `"__interactive_input__"` | Dynamic input placeholder emitted by interactive nodes. |
| `INPUTS_KEY` | `String` | `"inputs"` | Generic inputs-map key. |
| `CONFIG_KEY` | `String` | `"config"` | Generic config-map key. |
| `END_FRAME` | `String` | `"all streaming outputs finish"` | End-of-stream frame marker. |
| `END_NODE_STREAM` | `String` | `"end node stream"` | Per-node stream completion marker. |
| `LOOP_ID` | `String` | `"__sys_loop_id"` | System loop identifier key. |
| `INDEX` | `String` | `"index"` | General index key. |
| `FINISH_INDEX` | `String` | `"finish_index"` | Stream-finish index key. |

## Safety Limits

| Field | Type | Value | Description |
| --- | --- | --- | --- |
| `MAX_COLLECTION_SIZE` | `int` | `100000` | Maximum allowed collection size. |
| `MAX_EXPRESSION_LENGTH` | `int` | `5000` | Maximum allowed expression length. |
| `MAX_AST_DEPTH` | `int` | `50` | Maximum AST nesting depth. |
| `NESTED_LOOP_DEPTH` | `int` | `1` | Maximum nested workflow-loop depth. |

## Constructors

| Signature | Description |
| --- | --- |
| `private Constant()` | Utility-class constructor; the type is not instantiable. |
