# com.openjiuwen.core.retrieval.vector_store.SchemaMutableVectorStore

## 接口 SchemaMutableVectorStore

```java
public interface SchemaMutableVectorStore extends VectorStore
```

`SchemaMutableVectorStore` 为支持集合元数据与 schema 变更的向量库实现提供可选扩展接口。

## 抽象方法

### `List<String> listCollectionNames()`

返回当前后端可见的 collection 名称列表。

### `Map<String, Object> getCollectionMetadata(String collectionName)`

读取指定 collection 的元数据。

### `void updateCollectionMetadata(String collectionName, Map<String, Object> metadata)`

增量更新 collection 元数据。

### `void updateSchema(String collectionName, List<?> operations)`

对指定 collection 应用 schema 变更操作。

## 当前实现

- 当前任务范围内只有 `InMemoryVectorStore` 实现了该接口。
