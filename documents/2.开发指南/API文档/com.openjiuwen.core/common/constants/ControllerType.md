# com.openjiuwen.core.common.constants.ControllerType

## enum ControllerType

```java
public enum ControllerType
```

`ControllerType` normalizes the controller identifiers used by orchestration configuration and JSON payloads.

## Enum Values

| Value | Serialized Value | Description |
| --- | --- | --- |
| `REACT_CONTROLLER` | `"react"` | ReAct-style controller. |
| `WORKFLOW_CONTROLLER` | `"workflow"` | Workflow-based controller. |
| `UNDEFINED` | `"undefined"` | Fallback constant returned when the input token is unknown. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getValue()` | Return the serialized controller token used for JSON output. |
| `public static ControllerType fromValue(String value)` | Resolve an exact string token to the matching enum constant, or return `UNDEFINED` when there is no match. |

## Notes

- `getValue()` is annotated with `@JsonValue`, and `fromValue(String)` is annotated with `@JsonCreator`.
