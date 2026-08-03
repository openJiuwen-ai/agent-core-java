# com.openjiuwen.core.foundation.store.vector.ChromaVectorStore

## 类 ChromaVectorStore

```java
public class ChromaVectorStore extends InMemoryVectorStore
```

`ChromaVectorStore` 复用 `InMemoryVectorStore` 的全部行为，用于提供与 `StoreType.CHROMA` 对齐的本地兼容实现。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ChromaVectorStore(VectorStoreConfig config)` | 使用默认 `indexType = "hybrid"`。 |
| `public ChromaVectorStore(VectorStoreConfig config, String indexType)` | 显式指定索引类型。 |

## 说明

- 该类没有新增公开方法，所有存储、查询、删除与 schema 变更能力都继承自 `InMemoryVectorStore`。
- `VectorStoreFactory.createVectorStore(...)` 在 `StoreType.CHROMA` 分支返回该实现。
