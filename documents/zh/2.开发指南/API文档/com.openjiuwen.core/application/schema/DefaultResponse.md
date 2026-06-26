# com.openjiuwen.core.application.schema.DefaultResponse

## class DefaultResponse

```java
public class DefaultResponse
```

`DefaultResponse` 描述工作流意图未命中时的兜底回复。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `type` | `String` | `"text"` | 响应类型；源码默认是文本回复。 |
| `text` | `String` | `null` | 兜底回复正文。 |

## 说明

- 该类使用 `@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor` 生成常规访问器与构造器。
- `WorkflowAgentConfig` 默认会构造一个空的 `DefaultResponse`，而 `ApplicationTranslationRegressionTest` 验证了 JSON `default_response` 结构可以正确映射到本类。
