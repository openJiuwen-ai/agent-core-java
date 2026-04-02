# com.openjiuwen.core.foundation.store.graph.GraphStoreIndexConfig

## class GraphStoreIndexConfig

```java
public class GraphStoreIndexConfig
```

Graph Database Indexing Options.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `indexType` | `final String` | `-` | Index type. |
| `extraConfigs` | `final Map<String, Object>` | `-` | Extra configs. |
| `bm25Config` | `final BM25Config` | `-` | Bm25 config. |
| `bm25AnalyzerSettings` | `final Map<String, Object>` | `-` | Bm25 analyzer settings. |

## Constructors

| Signature | Description |
| --- | --- |
| `public GraphStoreIndexConfig(String indexType, Map<String, Object> extraConfigs, BM25Config bm25Config, Map<String, Object> bm25AnalyzerSettings)` | Create a new `GraphStoreIndexConfig` instance. |
| `public GraphStoreIndexConfig()` | Create a new `GraphStoreIndexConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String getIndexType()` | Return the index type. |
| `public Map<String, Object> getExtraConfigs()` | Return the extra configs. |
| `public BM25Config getBm25Config()` | Return the bm25 config. |
| `public Map<String, Object> getBm25AnalyzerSettings()` | Return the bm25 analyzer settings. |
