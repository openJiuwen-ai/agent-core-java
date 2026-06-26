# com.openjiuwen.core.foundation.store.vector.MilvusVectorStore

## class MilvusVectorStore

```java
public class MilvusVectorStore extends AbstractRetrievalVectorStoreAdapter
```

foundation 层的 Milvus 向量存储适配器。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public MilvusVectorStore(Map<String, Object> options)` | 使用选项构造 Milvus 适配器。 |

## 使用说明

- 常用选项包括 `database_name`、`collection_name`、`distance_metric`、`milvus_uri`、`milvus_token` 与 `index_type`。
- 标准 collection 生命周期、写入、搜索和删除能力由父类适配器统一提供。
