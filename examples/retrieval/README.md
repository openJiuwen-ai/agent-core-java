# Retrieval Java Examples

这个目录提供了一组基于 Java retrieval 框架的独立示例入口。

## 文件说明

- `TextEmbeddingExample.java`: 文本 embedding，对比跨语言同主题和相关主题之间的向量差异。
- `MultimodalEmbeddingExample.java`: 多模态 embedding，比较同图不同格式、不同图片、同图不同文本的相似度。
- `StandardRerankerExample.java`: 标准 reranker，对比默认查询和带 instruction 的打分结果。
- `ChatRerankerExample.java`: chat reranker，增加兼容性探测，并演示 custom instruction 对分数的影响。
- `QueryRewriterExample.java`: Query Rewriter，多轮对话重写、压缩和 `standalone_query` 输出。
- `ChromaFilterExample.java`: Chroma-compatible 本地检索过滤示例，不依赖外部向量库。
- `MilvusFilterExample.java`: Milvus 检索过滤示例，演示 `doc_id` / `chunk_id` 过滤和删除。
- `RetrievalExampleSupport.java`: 共享配置加载、`BaseModelClient` 创建、Milvus 配置和图片路径解析。
- `ExampleOutput.java`: 控制台输出辅助方法。
- `VectorSimilarityUtils.java`: 余弦相似度和欧氏距离工具方法。

## 配置

所有示例都会默认读取 `examples/apiconfig.json` 中的基础模型配置，并允许用环境变量或 JVM 参数覆盖。

常用覆盖项：

- `EMBEDDING_MODEL`, `EMBEDDING_API_BASE`, `EMBEDDING_API_KEY`
- `MULTIMODAL_EMBEDDING_MODEL`, `MULTIMODAL_EMBEDDING_API_BASE`, `MULTIMODAL_EMBEDDING_API_KEY`
- `RERANKER_MODEL`, `RERANKER_API_BASE`, `RERANKER_API_KEY`
- `CHAT_RERANKER_MODEL`, `CHAT_RERANKER_API_BASE`, `CHAT_RERANKER_API_KEY`
- `CHAT_RERANKER_YES_NO_IDS`
- `QUERY_REWRITER_PROVIDER`, `QUERY_REWRITER_MODEL`, `QUERY_REWRITER_API_BASE`, `QUERY_REWRITER_API_KEY`
- `MILVUS_URI`, `MILVUS_TOKEN`, `MILVUS_DATABASE_NAME`

补充说明：

- `CHAT_RERANKER_YES_NO_IDS` 必填，格式为两个整数 token id，例如 `9454,2753`。
- `MilvusFilterExample` 需要一个可用的 Milvus 实例；其它示例不依赖外部向量库。
- `MultimodalEmbeddingExample` 会复用仓库内的 `images/sample.png`，并在 `examples/retrieval/output/multimodal_assets` 下生成派生图片。

## 运行方式

以下命令假设当前目录是 Java 仓库根目录，也就是包含 `pom.xml`、`examples` 和 `src` 的目录。建议先执行一次编译：

```powershell
mvn -DskipTests compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/retrieval_examples.classpath"
$retrievalSources = Get-ChildItem examples/retrieval/*.java | ForEach-Object { $_.FullName }
javac -cp "target/classes;examples;$(Get-Content target/retrieval_examples.classpath -Raw)" examples/SharedExampleApiConfigLoader.java $retrievalSources
```

运行单个示例：

```powershell
$classpath = Get-Content target/retrieval_examples.classpath -Raw
java -cp "target/classes;examples;examples/retrieval;$classpath" TextEmbeddingExample
java -cp "target/classes;examples;examples/retrieval;$classpath" MultimodalEmbeddingExample
java -cp "target/classes;examples;examples/retrieval;$classpath" StandardRerankerExample
java -cp "target/classes;examples;examples/retrieval;$classpath" ChatRerankerExample
java -cp "target/classes;examples;examples/retrieval;$classpath" QueryRewriterExample
java -cp "target/classes;examples;examples/retrieval;$classpath" ChromaFilterExample
java -cp "target/classes;examples;examples/retrieval;$classpath" MilvusFilterExample
```

如果某个示例需要单独覆盖配置，可以直接追加 JVM 参数，例如：

```powershell
java -DCHAT_RERANKER_YES_NO_IDS=9454,2753 -cp "target/classes;examples;examples/retrieval;$classpath" ChatRerankerExample
java -DMILVUS_URI=http://localhost:19530 -cp "target/classes;examples;examples/retrieval;$classpath" MilvusFilterExample
```

## 当前实现说明

- Java retrieval 层当前的 `VectorStore` 过滤接口接收 `Map<String, Object>`，因此本目录的 filter 示例按 Java 现有语义演示。
- `ChromaFilterExample` 侧重本地 store 的等值和 in-list 过滤；`MilvusFilterExample` 侧重 `doc_id` / `chunk_id` 这类已知 collection 字段。
- 范围和逻辑表达式 DSL 还没有直接接到 Java retrieval `VectorStore` 公共接口上，因此这里没有额外封装一层一比一 API。

## 输出预期

- embedding 示例会打印向量维度、相似度、距离和简单分析结论。
- reranker 示例会打印每个候选文档的分数；chat 版本会先做兼容性探测。
- query rewriter 示例会打印 `before`、`standalone_query`、`intention` 和压缩触发信息。
- filter 示例会打印命中的 `doc_id`、`chunk_id`、分数和文本内容。