# com.openjiuwen.core.single_agent.schema.Artifact

## 类 Artifact

```java
public class Artifact
```

表示 `AgentResult` 中单个结果制品的数据模型。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `artifactId` | `String` | `-` | 制品 ID。 |
| `name` | `String` | `-` | 制品名称。 |
| `description` | `String` | `-` | 制品说明。 |
| `parts` | `List<Part>` | `new ArrayList<>()` | 组成该制品的内容分片列表。 |
| `metadata` | `Map<String, Object>` | `new HashMap<>()` | 制品附加元数据。 |

## 说明

- 相关测试：`DataClassCoverageTest`、`SchemaTest`。
- builder 默认会为 `parts` 与 `metadata` 创建空集合。
