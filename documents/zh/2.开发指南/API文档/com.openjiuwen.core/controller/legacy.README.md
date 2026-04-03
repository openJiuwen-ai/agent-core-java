# legacy

`com.openjiuwen.core.controller.legacy` 保留了早期 Controller 体系的兼容 API，包括基于内存消息队列的控制器基类、意图检测控制器、旧版 reasoner、事件模型、任务模型和工具函数。

## Modules

| 模块 | 说明 |
|---|---|
| [`config`](./config.README.md) | 旧版意图检测、Planner、Reflector 和组合式 reasoner 配置。 |
| [`constants`](./constants.README.md) | 旧版意图检测使用的字段常量与角色映射。 |
| [`event`](./event.README.md) | 兼容事件模型与工厂方法。 |
| [`reasoner`](./reasoner.README.md) | 旧版意图检测器、Planner 以及组合式 reasoner。 |
| [`task`](./task.README.md) | 兼容任务模型、依赖关系和结果对象。 |
| [`utils`](./utils.README.md) | 旧版消息处理与 reasoner 辅助函数。 |

## Types

| 类型 | 说明 |
|---|---|
| [`BaseController`](./BaseController.md) | 基于内存消息队列的旧版控制器基类。 |
| [`IntentDetectionController`](./IntentDetectionController.md) | 支持实时打断的旧版意图检测控制器。 |

## Notes

- 该子树主要用于兼容旧代码路径，新控制器能力优先参考 `com.openjiuwen.core.controller`、`modules` 和 `schema`。
- 许多 legacy 类型使用 Lombok 生成 getter、setter、builder；文档重点描述显式字段与行为，而不逐一展开生成方法。
