# schema

`com.openjiuwen.core.context.schema` 定义上下文引擎配置、卸载消息类型以及描述卸载消息通用字段的接口。

## Types

| 类型 | 说明 |
|---|---|
| [`ContextEngineConfig`](./schema/ContextEngineConfig.md) | 控制上下文缓冲、窗口轮次、KV Cache 释放与重载提示能力的配置对象。 |
| [`OffloadMessages`](./schema/OffloadMessages.md) | 生成带卸载句柄的用户、助手、系统和工具消息类型。 |
| [`OffloadMixin`](./schema/OffloadMixin.md) | 暴露 `offloadType`、`offloadHandle` 与 `metadata` 的 marker 接口。 |

## Notes

- `ContextEngineConfig` 使用 Lombok builder，默认关闭 `enableKvCacheRelease` 与 `enableReload`。
- `OffloadMessages` 的工厂方法会按角色选择具体子类型，并尽量保留原消息上的额外字段。
