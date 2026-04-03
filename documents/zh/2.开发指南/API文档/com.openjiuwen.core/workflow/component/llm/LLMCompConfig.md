# com.openjiuwen.core.workflow.component.llm.LLMCompConfig

LLM 组件的配置对象。

## class LLMCompConfig

```java
public class LLMCompConfig extends ComponentConfig
```

## Lombok

- 该类型使用 `@Data` 和 `@EqualsAndHashCode(callSuper = true)` 生成访问器、`equals` / `hashCode` 等样板代码。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `modelId` | `String` | - | 已注册模型 ID。 |
| `modelClientConfig` | `ModelClientConfig` | - | 模型客户端配置。 |
| `modelConfig` | `ModelRequestConfig` | - | 模型请求配置。 |
| `templateContent` | `List<Map<String, Object>>` | `new ArrayList<>()` | 提示词模板消息列表。 |
| `systemPromptTemplate` | `SystemMessage` | - | 系统提示词模板。 |
| `userPromptTemplate` | `UserMessage` | - | 用户提示词模板。 |
| `responseFormat` | `Map<String, Object>` | `new LinkedHashMap<>()` | 响应格式配置。 |
| `outputConfig` | `Map<String, Object>` | `new LinkedHashMap<>()` | 输出字段配置。 |
| `enableHistory` | `boolean` | `false` | 是否拼接历史消息。 |
| `cacheStream` | `boolean` | `false` | 是否缓存流式输出。 |

## Notes

- 该配置用于驱动 [`LLMExecutable`](./LLMExecutable.md) 的模型初始化、提示词组装和输出格式化行为。
