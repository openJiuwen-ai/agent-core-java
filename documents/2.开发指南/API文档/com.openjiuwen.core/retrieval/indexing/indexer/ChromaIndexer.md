# com.openjiuwen.core.retrieval.indexing.indexer.ChromaIndexer

## 类 ChromaIndexer

```java
public class ChromaIndexer extends InMemoryIndexer
```

`ChromaIndexer` 没有增加新的索引逻辑，只是用更明确的命名表达“以 Chroma 兼容向量库作为后端”的场景。

## 构造方法

### `public ChromaIndexer(VectorStore vectorStore)`

把传入的 `VectorStore` 直接交给父类 `InMemoryIndexer` 管理。

## 继承行为

- 继承 `buildIndex(...)`、`updateIndex(...)`、`deleteIndex(...)`、`getIndexInfo(...)` 等全部公开能力。
- 运行时行为与 `InMemoryIndexer` 一致，是否真正兼容 Chroma 取决于传入的 `VectorStore` 实现。
