# com.openjiuwen.core.context.schema.ContextEngineConfig

## class ContextEngineConfig

```java
public class ContextEngineConfig
```

`ContextEngineConfig` 描述上下文缓冲、窗口截断、KV Cache 释放和重载提示能力的配置项，是 `ContextEngine` 与 `SessionModelContext` 的核心输入对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `maxContextMessageNum` | `Integer` | `null` | 上下文缓冲最多保留的消息数；为空表示不限制。 |
| `defaultWindowMessageNum` | `Integer` | `null` | 构造上下文窗口时默认保留的消息条数；为空表示不启用默认条数限制。 |
| `defaultWindowRoundNum` | `Integer` | `null` | 构造上下文窗口时默认保留的最近对话轮次数；为空表示默认不按轮次截断。 |
| `enableKvCacheRelease` | `boolean` | `false` | 是否在生成窗口后启用 KV Cache 释放管理。 |
| `enableReload` | `boolean` | `false` | 是否在窗口系统消息中追加重载提示，并允许模型借助重载工具取回卸载内容。 |

## 显式方法

### `public void validate()`

校验 `maxContextMessageNum`、`defaultWindowMessageNum`、`defaultWindowRoundNum` 这三个整数配置是否大于 `0`。

**说明**

- 任一配置小于等于 `0` 时会抛出 `IllegalArgumentException`，错误文本采用 `Input should be greater than 0 [type=greater_than, ...]` 这一结构。

## 说明

- 该类使用 `@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`。
- `ContextEngineConfigTest` 覆盖了默认值和 builder 赋值行为。
