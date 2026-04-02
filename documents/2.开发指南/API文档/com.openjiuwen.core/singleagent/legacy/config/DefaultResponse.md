# com.openjiuwen.core.singleagent.legacy.config.DefaultResponse

## 类 DefaultResponse

```java
public class DefaultResponse
```

工作流型旧版 agent 的默认回复配置。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `type` | `String` | `"text"` | 默认回复类型。 |
| `text` | `String` | `-` | 默认回复内容。 |

## 说明

- 源码使用 Lombok `@Data` 和 `@Builder` 生成访问器与 builder，适合直接作为配置片段嵌入 `WorkflowAgentConfig`。
