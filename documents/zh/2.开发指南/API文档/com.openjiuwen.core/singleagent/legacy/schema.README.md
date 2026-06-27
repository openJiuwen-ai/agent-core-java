# schema

`com.openjiuwen.core.single_agent.legacy.schema` 保存旧版插件与工作流的描述对象，主要用于在配置里记录 `id`、`version`、`description` 和输入参数。

## 类型

| 类型 | 说明 |
|---|---|
| [`PluginSchema`](./schema/PluginSchema.md) | 旧版插件声明对象。 |
| [`WorkflowSchema`](./schema/WorkflowSchema.md) | 旧版工作流声明对象。 |

## 说明

- 两个类型都使用 `LinkedHashMap<String, Object>` 保存输入参数，便于保留字段顺序并兼容动态结构。
