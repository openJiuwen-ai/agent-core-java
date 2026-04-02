# constants

`com.openjiuwen.core.common.constants` groups framework-wide string and integer constants plus the small enum types used to normalize controller and task selection.

## Core Types

| Type | Description |
| --- | --- |
| [`Constant`](./constants/Constant.md) | Static holder for shared workflow keys, IR field names, and safe-execution limits. |
| [`ControllerType`](./constants/ControllerType.md) | Enum for controller-mode identifiers such as `react` and `workflow`. |
| [`TaskType`](./constants/TaskType.md) | Enum for task-routing identifiers such as `plugin`, `workflow`, and `mcp`. |

## Notes

- `Constant` is a pure utility holder with no instance state.
- `ControllerType` and `TaskType` provide stable string round-tripping for configuration and JSON payloads.
