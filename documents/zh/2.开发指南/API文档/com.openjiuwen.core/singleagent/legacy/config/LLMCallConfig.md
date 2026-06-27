# com.openjiuwen.core.single_agent.legacy.config.LLMCallConfig

## 类 LLMCallConfig

```java
public class LLMCallConfig
```

直接调用模型时使用的请求配置与客户端配置。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `model` | `ModelRequestConfig` | `-` | 模型请求参数。 |
| `modelClient` | `ModelClientConfig` | `-` | 模型客户端连接配置。 |
| `systemPrompt` | `List<Map<String, String>>` | `new ArrayList<>()` | 系统提示词片段。 |
| `userPrompt` | `List<Map<String, String>>` | `new ArrayList<>()` | 用户提示词片段。 |
| `freezeSystemPrompt` | `boolean` | `false` | 是否冻结系统提示词。 |
| `freezeUserPrompt` | `boolean` | `true` | 是否冻结用户提示词。 |

## 说明

- 源码使用 Lombok `@Data`、`@Builder`、`@NoArgsConstructor` 和 `@AllArgsConstructor` 生成常规访问器与构建器。
