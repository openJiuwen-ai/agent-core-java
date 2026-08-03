# com.openjiuwen.core.single_agent.schema.Part

## class Part

```java
public class Part
```

`Part` 表示工件或内容中的一个片段。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `type` | `String` | `null` | 片段类型标识。 |
| `content` | `String` | `null` | 片段正文。 |
| `metadata` | `Map<String, Object>` | `null` | 片段附加元数据。 |

## 说明

- `@Data`、`@Builder`、`@NoArgsConstructor` 与 `@AllArgsConstructor` 会为该 DTO 生成访问器、构建器和构造器。
