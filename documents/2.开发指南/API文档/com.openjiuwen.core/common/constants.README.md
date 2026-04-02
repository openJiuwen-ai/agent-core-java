# constants

`com.openjiuwen.core.common.constants` 汇总框架共享常量与少量枚举类型，用于统一工作流键名、IR 字段名以及控制器、任务类型标识。

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`Constant`](./constants/Constant.md) | 定义跨模块复用的字符串键与安全上限常量。 |
| [`ControllerType`](./constants/ControllerType.md) | 控制器类型枚举。 |
| [`TaskType`](./constants/TaskType.md) | 任务类型枚举。 |

## 说明

- `Constant` 不保存实例状态，仅作为静态常量入口。
- `ControllerType` 与 `TaskType` 都提供字符串到枚举的解析入口，未知值会回退为 `UNDEFINED`。
