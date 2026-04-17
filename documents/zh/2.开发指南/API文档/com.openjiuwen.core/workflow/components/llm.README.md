# llm

`com.openjiuwen.core.workflow.components.llm` 是旧版 `workflow.components.llm` 包路径下的兼容层，主要提供对新实现的别名、桥接构造器与测试兼容 API。

## Types

| Type | Kind | 说明 |
| --- | --- | --- |
| [`FieldInfo`](./llm/FieldInfo.md) | `class` | 旧版 `FieldInfo` 兼容包装类，补充位置参数构造器。 |
| [`IntentDetectionCompConfig`](./llm/IntentDetectionCompConfig.md) | `class` | 旧版意图识别配置兼容包装类，补充位置参数与 snake_case 访问器。 |
| [`IntentDetectionComponent`](./llm/IntentDetectionComponent.md) | `class` | 旧版意图识别组件兼容包装类。 |
| [`LLMCompConfig`](./llm/LLMCompConfig.md) | `class` | 旧版 LLM 配置兼容包装类，补充位置参数构造器与构建器。 |
| [`LLMComponent`](./llm/LLMComponent.md) | `class` | 旧版 LLM 组件兼容包装类。 |
| [`QuestionerComponent`](./llm/QuestionerComponent.md) | `class` | 旧版 Questioner 组件兼容包装类。 |
| [`QuestionerConfig`](./llm/QuestionerConfig.md) | `class` | 旧版 Questioner 配置兼容包装类，补充位置参数与 snake_case 访问器。 |

## Notes

- 当前包共覆盖 `7` 个直接公开类型。
- 当前任务包未提供专用 Java 测试，文档依据源码可见行为整理。
