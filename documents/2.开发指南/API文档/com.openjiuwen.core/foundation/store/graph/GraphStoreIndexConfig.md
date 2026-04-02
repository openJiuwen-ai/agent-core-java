# com.openjiuwen.core.foundation.store.graph.GraphStoreIndexConfig

## class GraphStoreIndexConfig

```java
public class GraphStoreIndexConfig
```

图索引配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `indexType` | `String` | `null` | 索引类型。 |
| `extraConfigs` | `Map<String, Object>` | `{}` | 附加配置。 |
| `bm25Config` | `BM25Config` | `new BM25Config()` | BM25 参数配置。 |
| `bm25AnalyzerSettings` | `Map<String, Object>` | `null` | BM25 分析器设置。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public GraphStoreIndexConfig(String indexType, Map<String, Object> extraConfigs, BM25Config bm25Config, Map<String, Object> bm25AnalyzerSettings)` | 完整指定图索引配置。 |
| `public GraphStoreIndexConfig()` | 使用默认图索引配置。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getIndexType()` | 返回索引类型。 |
| `public Map<String, Object> getExtraConfigs()` | 返回附加配置。 |
| `public BM25Config getBm25Config()` | 返回 BM25 配置。 |
| `public Map<String, Object> getBm25AnalyzerSettings()` | 返回 BM25 分析器设置。 |
