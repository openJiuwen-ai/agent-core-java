# com.openjiuwen.core.memory.manage.mem_model.SemanticStore

## 类 SemanticStore

```java
public class SemanticStore
```

`SemanticStore` 是 `com.openjiuwen.core.memory.manage.mem_model` 包下的公开类型，文档按 Java 源码列出其公开成员与签名。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | 记忆模块日志记录器。 |
| `VECTOR_FIELD` | `String` | 字段 `VECTOR_FIELD`。 |
| `TEXT_FIELD` | `String` | 字段 `TEXT_FIELD`。 |
| `ID_FIELD` | `String` | 字段 `ID_FIELD`。 |
| `vectorStore` | `VectorStore` | 向量存储。 |
| `embeddingModel` | `Embedding` | 字段 `embeddingModel`。 |
| `knownCollections` | `Set<String>` | 字段 `knownCollections`。 |
| `collectionMetadata` | `Map<String, Map<String, Object>>` | 字段 `collectionMetadata`。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SemanticStore(VectorStore vectorStore)` | 创建 `SemanticStore` 实例。 |
| `public SemanticStore(VectorStore vectorStore, Embedding embedding)` | 创建 `SemanticStore` 实例。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public void initializeEmbeddingModel(Embedding embeddingModel)` | 执行 `initializeEmbeddingModel`。 |
| `public boolean collectionExist(String collectionName)` | 执行 `collectionExist`。 |
| `public void createCollection(String collectionName, int dimension, Map<String, Object> schema)` | 执行 `createCollection`。 |
| `public boolean addDocs(List<Map.Entry<String, String>> docs, String tableName)` | 执行 `addDocs` 写入流程。 |
| `public List<Map.Entry<String, Double>> search(String query, String tableName, int topK)` | 执行 `search` 查询流程。 |
| `public void deleteDocs(List<String> ids, String tableName)` | 执行 `deleteDocs` 删除流程。 |
| `public void deleteTable(String tableName)` | 执行 `deleteTable` 删除流程。 |
| `public List<String> listCollectionNames()` | 执行 `listCollectionNames` 查询流程。 |
| `public boolean updateSchema(String collectionName, List<?> operations)` | 执行 `updateSchema` 更新流程。 |
| `public Map<String, Object> getCollectionMetadata(String collectionName)` | 返回 `getCollectionMetadata` 的执行结果。 |
| `public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata)` | 执行 `updateCollectionMetadata` 更新流程。 |

## 使用说明

- 相关测试：`SemanticStoreMilvusTest.java`、`SemanticStorePGVectorTest.java`
