# com.openjiuwen.core.foundation.store.vector.ChromaVectorStore

## class ChromaVectorStore

```java
public class ChromaVectorStore extends AbstractRetrievalVectorStoreAdapter
```

foundation 层的 Chroma 向量存储适配器。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ChromaVectorStore(Map<String, Object> options)` | 使用选项构造 Chroma 适配器。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public List<Map<String, Object>> getAllDocuments(String collectionName) throws Exception` | 面向迁移场景拉取指定 collection 的全部文档。 |

## 使用说明

- 常用选项包括 `database_name`、`collection_name`、`distance_metric` 与 `index_type`。
- 标准 collection 生命周期、写入、搜索和删除能力由父类适配器统一提供。
