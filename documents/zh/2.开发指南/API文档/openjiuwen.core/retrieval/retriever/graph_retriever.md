# com.openjiuwen.core.retrieval.retriever.GraphRetriever

## class GraphRetriever

图检索器实现，结合文档块检索和图检索，支持基于三元组关系的图扩展和多跳检索。

```java
GraphRetriever(
    Retriever chunkRetriever,
    Retriever tripleRetriever,
    VectorStore vectorStore,
    Embedding embedModel,
    String chunkCollection,
    String tripleCollection
)
```

初始化图检索器。

**参数**：

* **chunkRetriever**：文档块检索器；未提供时根据 `mode` 和 `vectorStore` 动态创建。
* **tripleRetriever**：三元组检索器；未提供时根据 `mode` 和 `vectorStore` 动态创建。
* **vectorStore**：向量存储实例，用于动态创建检索器。
* **embedModel**：嵌入模型实例，用于向量检索和 beam search。
* **chunkCollection**：文档块集合名称。
* **tripleCollection**：三元组集合名称。

### getRetrieverForMode

```java
Retriever getRetrieverForMode(String mode, boolean isChunk)
```

返回指定模式对应的文档块检索器或三元组检索器。`mode` 支持 `vector`、`sparse` 和 `hybrid`，并会校验与 `indexType` 的兼容性。

### retrieve

```java
List<RetrievalResult> retrieve(
    String query,
    int topK,
    Double scoreThreshold,
    String mode,
    Map<String, Object> options
)
```

执行图检索。该方法先进行文档块检索，再通过 `graphExpansion` 使用三元组关系扩展结果。

**参数**：

* **query**：查询字符串。
* **topK**：返回结果数量。
* **scoreThreshold**：得分阈值，仅支持 `vector` 模式。
* **mode**：检索模式，默认语义为 `hybrid`。
* **options**：额外参数，可包含 `graph_hops`、`num_beams`、`num_candidates_per_beam` 等。

**返回**：

`List<RetrievalResult>`，返回图扩展后的检索结果列表。

### graphExpansion

```java
List<RetrievalResult> graphExpansion(
    String query,
    List<RetrievalResult> chunks,
    List<RetrievalResult> triples,
    Integer topk,
    String mode,
    Map<String, Object> options
)
```

基于初始文档块检索结果，通过三元组进行扩展检索。若没有可用三元组，返回原始文档块结果；若 beam search 失败，也回退到原始文档块结果。

### batchRetrieve

```java
List<List<RetrievalResult>> batchRetrieve(
    List<String> queries,
    int topK,
    String mode,
    Map<String, Object> options
)
```

批量执行多个查询，并返回每个查询对应的图检索结果。

### close

```java
void close()
```

关闭已注入的文档块检索器和三元组检索器。
