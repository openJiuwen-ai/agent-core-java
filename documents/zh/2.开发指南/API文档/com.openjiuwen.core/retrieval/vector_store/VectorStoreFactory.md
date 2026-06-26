# com.openjiuwen.core.retrieval.vector_store.VectorStoreFactory

## 类 VectorStoreFactory

```java
public final class VectorStoreFactory
```

`VectorStoreFactory` 根据 `VectorStoreConfig` 与 options 选择并创建向量库实现。

## 构造说明

- 构造方法为私有，外部只能通过静态工厂方法使用。

## 公开静态方法

### `public static VectorStore createVectorStore(VectorStoreConfig config)`

等价于 `createVectorStore(config, Map.of())`。

### `public static VectorStore createVectorStore(VectorStoreConfig config, Map<String, Object> options)`

根据 `config.getStoreType()` 创建具体实现：

- `MILVUS` -> `MilvusVectorStore`
- `CHROMA` -> `ChromaVectorStore`
- `PGVECTOR` -> `PGVectorStore`

**参数：**

- `config`：向量库配置，不能为空。
- `options`：附加构造参数，可包含 `indexType` / `index_type`、Milvus 连接参数或 PGVector 连接参数。

**异常：**

- `config == null` 时抛出 `validation("VectorStoreConfig is required")`。
- 创建 `MilvusVectorStore` 时缺少 `milvus_uri` / `milvusClient` 会抛异常。
- 创建 `PGVectorStore` 时缺少 `jdbcUrl` / `dataSource` 会抛异常。

## 选项约定

- Milvus：支持 `milvus_client`、`milvusClient`、`client`、`milvus_uri`、`milvusUri`、`uri`、`milvus_token`、`milvusToken`、`token`。
- PGVector：支持 `dataSource`、`data_source`、`jdbcUrl`、`jdbc_url`、`pgUri`、`pg_uri`、`url`、`username`、`user`、`password`。
- `indexType` / `index_type` 若不在 `RetrievalValidation.INDEX_TYPES` 中，会回退到 `"hybrid"`。

## 相关测试

- `VectorStoreFactoryTest` 验证三类后端均可按预期创建，并覆盖了 `milvus_client`、`jdbc_url`、`pg_uri`、`dataSource` 等常用入参。
