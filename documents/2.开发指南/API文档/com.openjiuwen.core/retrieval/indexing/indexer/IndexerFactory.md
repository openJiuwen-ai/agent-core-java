# com.openjiuwen.core.retrieval.indexing.indexer.IndexerFactory

## 类 IndexerFactory

```java
public final class IndexerFactory
```

`IndexerFactory` 根据传入的 `VectorStore` 实例类型选择最合适的索引器实现。

## 构造说明

- 构造方法为私有，只能使用静态工厂方法。

## 公开静态方法

### `public static Indexer createIndexer(VectorStore vectorStore)`

- 当 `vectorStore == null` 时，抛出 `validation("VectorStore is required")`。
- 当 `vectorStore` 是 `MilvusVectorStore` 时，返回 `MilvusIndexer`。
- 其他实现统一返回 `InMemoryIndexer`。

## 相关测试

- `IndexerFactoryTest` 覆盖了 `MilvusVectorStore -> MilvusIndexer` 和 `InMemoryVectorStore -> InMemoryIndexer` 两条路径。
