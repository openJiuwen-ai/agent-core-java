# com.openjiuwen.core.retrieval.retriever.AbstractStoreBackedRetriever

## 类 AbstractStoreBackedRetriever

```java
public abstract class AbstractStoreBackedRetriever extends AbstractRetriever
```

`AbstractStoreBackedRetriever` 是依赖 `VectorStore` 与 `Embedding` 的检索器基类，保存底层存储和 embedding 模型引用。

## 构造说明

- 该类只提供受保护构造方法 `AbstractStoreBackedRetriever(VectorStore vectorStore, Embedding embedModel)`，供子类初始化底层依赖，不属于公开实例化 API。

## 公开方法

### `public VectorStore getVectorStore()`

返回构造时注入的 `VectorStore`。

### `public Embedding getEmbedModel()`

返回构造时注入的 `Embedding`；纯稀疏检索器可返回 `null`。

### `public String getIndexType()`

直接委托给 `vectorStore.getIndexType()`，用于让子类继承底层索引类型。
