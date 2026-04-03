# com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig

## 类 KnowledgeBaseConfig

```java
public class KnowledgeBaseConfig
```

知识库级配置对象，定义知识库标识、索引模式、图检索开关以及分块参数。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `kbId` | `String` | 知识库标识，不能为空白。 |
| `indexType` | `String` | 索引模式，默认值为 `"hybrid"`。 |
| `useGraph` | `boolean` | 是否启用图检索，默认值为 `false`。 |
| `chunkSize` | `int` | 分块大小，默认值为 `512`。 |
| `chunkOverlap` | `int` | 分块重叠，默认值为 `50`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public KnowledgeBaseConfig()` | 创建空配置对象。 |
| `public KnowledgeBaseConfig(String kbId)` | 使用默认参数创建配置。 |
| `public KnowledgeBaseConfig(String kbId, String indexType, boolean useGraph, int chunkSize, int chunkOverlap)` | 完整指定知识库配置。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void validate()` | 校验当前配置是否合法。 |
| `public String getKbId()` | 返回知识库标识。 |
| `public void setKbId(String kbId)` | 更新知识库标识并重新校验。 |
| `public String getIndexType()` | 返回索引模式。 |
| `public void setIndexType(String indexType)` | 更新索引模式并重新校验。 |
| `public boolean isUseGraph()` | 返回图检索开关。 |
| `public void setUseGraph(boolean useGraph)` | 更新图检索开关。 |
| `public int getChunkSize()` | 返回分块大小。 |
| `public void setChunkSize(int chunkSize)` | 更新分块大小并重新校验。 |
| `public int getChunkOverlap()` | 返回分块重叠。 |
| `public void setChunkOverlap(int chunkOverlap)` | 更新分块重叠并重新校验。 |

## 说明

- `indexType` 仅允许 `hybrid`、`bm25`、`vector`。
- `chunkSize` 必须大于 `0`，`chunkOverlap` 不能小于 `0`。
- 测试确认：默认值为 `indexType = "hybrid"`、`useGraph = false`、`chunkSize = 512`、`chunkOverlap = 50`；非法 `indexType` 或缺失 `kbId` 会抛出异常。
