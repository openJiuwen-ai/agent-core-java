# Retrieval 模块 API 文档

> 包路径：`com.openjiuwen.core.retrieval`

嵌入、索引、解析、检索器、重排器与知识库能力。基于 `retrieval` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `99` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.retrieval` | 3 |
| `com.openjiuwen.core.retrieval.common` | 26 |
| `com.openjiuwen.core.retrieval.embedding` | 6 |
| `com.openjiuwen.core.retrieval.indexing.indexer` | 6 |
| `com.openjiuwen.core.retrieval.indexing.processor` | 1 |
| `com.openjiuwen.core.retrieval.indexing.processor.chunker` | 11 |
| `com.openjiuwen.core.retrieval.indexing.processor.extractor` | 3 |
| `com.openjiuwen.core.retrieval.indexing.processor.parser` | 15 |
| `com.openjiuwen.core.retrieval.indexing.processor.splitter` | 2 |
| `com.openjiuwen.core.retrieval.query_rewriter` | 1 |
| `com.openjiuwen.core.retrieval.reranker` | 5 |
| `com.openjiuwen.core.retrieval.retriever` | 9 |
| `com.openjiuwen.core.retrieval.utils` | 4 |
| `com.openjiuwen.core.retrieval.vector_store` | 7 |

## `com.openjiuwen.core.retrieval`

公开类型：`3`

### `GraphKnowledgeBase`

- 类型：`class`
- 声明：`public class GraphKnowledgeBase extends KnowledgeBase`
- 说明：Knowledge base with optional graph index.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public GraphKnowledgeBase(KnowledgeBaseConfig config)` | - |
| `public GraphKnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Embedding embedModel, Parser parser, Chunker chunker, Extractor extractor, Indexer indexManager, BaseModelClient llmClient, Retriever chunkRetriever, Retriever tripleRetriever)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<String> addDocuments(List<Document> documents)` | `List<String>` | - |
| `public List<RetrievalResult> retrieve(String query, RetrievalConfig retrievalConfig)` | `List<RetrievalResult>` | - |
| `public boolean deleteDocuments(List<String> docIds)` | `boolean` | - |
| `public List<String> updateDocuments(List<Document> documents)` | `List<String>` | - |
| `public Map<String, Object> getStatistics()` | `Map<String, Object>` | - |
| `public void close()` | `void` | - |

### `KnowledgeBase`

- 类型：`class`
- 声明：`public abstract class KnowledgeBase implements AutoCloseable`
- 说明：Abstract knowledge base.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `config` | `KnowledgeBaseConfig` | `protected final` | `-` | - |
| `vectorStore` | `VectorStore` | `protected` | `-` | - |
| `embedModel` | `Embedding` | `protected` | `-` | - |
| `parser` | `Parser` | `protected` | `-` | - |
| `chunker` | `Chunker` | `protected` | `-` | - |
| `extractor` | `Extractor` | `protected` | `-` | - |
| `indexManager` | `Indexer` | `protected` | `-` | - |
| `llmClient` | `BaseModelClient` | `protected` | `-` | - |
| `retriever` | `Retriever` | `protected` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected KnowledgeBase(KnowledgeBaseConfig config)` | - |
| `protected KnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Embedding embedModel, Parser parser, Chunker chunker, Extractor extractor, Indexer indexManager, BaseModelClient llmClient, Retriever retriever)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public KnowledgeBaseConfig getConfig()` | `KnowledgeBaseConfig` | - |
| `public VectorStore getVectorStore()` | `VectorStore` | - |
| `public void setVectorStore(VectorStore vectorStore)` | `void` | - |
| `public Embedding getEmbedModel()` | `Embedding` | - |
| `public void setEmbedModel(Embedding embedModel)` | `void` | - |
| `public Parser getParser()` | `Parser` | - |
| `public void setParser(Parser parser)` | `void` | - |
| `public Chunker getChunker()` | `Chunker` | - |
| `public void setChunker(Chunker chunker)` | `void` | - |
| `public Extractor getExtractor()` | `Extractor` | - |
| `public void setExtractor(Extractor extractor)` | `void` | - |
| `public Indexer getIndexManager()` | `Indexer` | - |
| `public void setIndexManager(Indexer indexManager)` | `void` | - |
| `public BaseModelClient getLlmClient()` | `BaseModelClient` | - |
| `public void setLlmClient(BaseModelClient llmClient)` | `void` | - |
| `public Retriever getRetriever()` | `Retriever` | - |
| `public void setRetriever(Retriever retriever)` | `void` | - |
| `public List<Document> parseFiles(List<String> filePaths)` | `List<Document>` | - |
| `public List<Document> parseFiles(List<String> filePaths, Map<String, Object> options)` | `List<Document>` | - |
| `public List<Document> parseUrls(List<String> urls)` | `List<Document>` | - |
| `public List<Document> parseUrls(List<String> urls, Map<String, Object> options)` | `List<Document>` | - |
| `public abstract List<String> addDocuments(List<Document> documents)` | `List<String>` | - |
| `public abstract List<RetrievalResult> retrieve(String query, RetrievalConfig config)` | `List<RetrievalResult>` | - |
| `public abstract boolean deleteDocuments(List<String> docIds)` | `boolean` | - |
| `public abstract List<String> updateDocuments(List<Document> documents)` | `List<String>` | - |
| `public abstract Map<String, Object> getStatistics()` | `Map<String, Object>` | - |
| `public void close()` | `void` | - |
| `protected void validateIndex()` | `void` | - |
| `protected static void compareConfig(String field, Object left, Object right, IndexBackendConfig leftOwner, IndexBackendConfig rightOwner)` | `void` | - |
| `protected Indexer resolveIndexManager()` | `Indexer` | - |
| `protected Indexer requireIndexManager()` | `Indexer` | - |
| `protected static void closeQuietly(AutoCloseable closeable)` | `void` | - |

### `SimpleKnowledgeBase`

- 类型：`class`
- 声明：`public class SimpleKnowledgeBase extends KnowledgeBase`
- 说明：Standard chunk-based knowledge base.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SimpleKnowledgeBase(KnowledgeBaseConfig config)` | - |
| `public SimpleKnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Embedding embedModel, Parser parser, Chunker chunker, Indexer indexManager, BaseModelClient llmClient, Retriever retriever)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<String> addDocuments(List<Document> documents)` | `List<String>` | - |
| `public List<RetrievalResult> retrieve(String query, RetrievalConfig retrievalConfig)` | `List<RetrievalResult>` | - |
| `public boolean deleteDocuments(List<String> docIds)` | `boolean` | - |
| `public List<String> updateDocuments(List<Document> documents)` | `List<String>` | - |
| `public Map<String, Object> getStatistics()` | `Map<String, Object>` | - |
| `protected String chunkIndexName()` | `String` | - |
| `protected Map<String, Object> optionsFrom(RetrievalConfig config)` | `Map<String, Object>` | - |
| `protected Retriever resolveRetriever(RetrievalConfig retrievalConfig)` | `Retriever` | - |
| `public static List<RetrievalResult> retrieveMultiKb(List<? extends KnowledgeBase> knowledgeBases, String query, int topK)` | `List<RetrievalResult>` | - |
| `public static List<MultiKBRetrievalResult> retrieveMultiKbWithSource(List<? extends KnowledgeBase> knowledgeBases, String query, int topK)` | `List<MultiKBRetrievalResult>` | - |

## `com.openjiuwen.core.retrieval.common`

公开类型：`26`

### `BaseCallback`

- 类型：`class`
- 声明：`public class BaseCallback`
- 说明：Base callback for indexing and embedding progress.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public BaseCallback()` | - |
| `public BaseCallback(Collection<?> sequence)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void onBatch(int startIdx, int endIdx, List<String> batch)` | `void` | - |
| `public int getCallCounter()` | `int` | - |
| `public int getTotal()` | `int` | - |

### `BaseRankConfig`

- 类型：`class`
- 声明：`public abstract class BaseRankConfig`
- 说明：Base type for result-ranker configuration.
- 嵌套公开类型：`BaseRankConfig.RankerArguments`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | `String` | `private final` | `-` | - |
| `higherIsBetter` | `boolean` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseRankConfig(String name, boolean higherIsBetter)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getName()` | `String` | - |
| `public boolean isHigherIsBetter()` | `boolean` | - |
| `public abstract RankerArguments getArgs()` | `RankerArguments` | - |
| `public List<Integer> isActive()` | `List<Integer>` | - |
| `public Class<?> getRankerClass(String database)` | `Class<?>` | - |

### `BaseRankConfig.RankerArguments`

- 类型：`record`
- 声明：`public record RankerArguments(List<Object> positional, Map<String, Object> keyword)`
- 说明：Ranker constructor arguments.
- 宿主类型：`BaseRankConfig`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `positional` | `List<Object>` | `private final` | `-` | - |
| `keyword` | `Map<String, Object>` | `private final` | `-` | - |

### `Document`

- 类型：`class`
- 声明：`@Getter @Setter public class Document`
- 说明：Document model.
- 注解：`@Getter`、`@Setter`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `UUID.randomUUID().toString()` | - |
| `text` | `String` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Document()` | - |
| `public Document(String text)` | - |
| `public Document(String id, String text)` | - |
| `public Document(String id, String text, Map<String, Object> metadata)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setText(String text)` | `void` | - |
| `public void setMetadata(Map<String, Object> metadata)` | `void` | - |

### `EmbeddingConfig`

- 类型：`class`
- 声明：`public class EmbeddingConfig`
- 说明：Embedding model configuration.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `modelName` | `String` | `private` | `-` | - |
| `baseUrl` | `String` | `private` | `-` | - |
| `apiKey` | `String` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public EmbeddingConfig()` | - |
| `public EmbeddingConfig(String modelName, String baseUrl)` | - |
| `public EmbeddingConfig(String modelName, String baseUrl, String apiKey)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getModelName()` | `String` | - |
| `public void setModelName(String modelName)` | `void` | - |
| `public String getBaseUrl()` | `String` | - |
| `public void setBaseUrl(String baseUrl)` | `void` | - |
| `public String getApiKey()` | `String` | - |
| `public void setApiKey(String apiKey)` | `void` | - |

### `IndexConfig`

- 类型：`class`
- 声明：`public class IndexConfig`
- 说明：Index configuration.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `indexName` | `String` | `private` | `-` | - |
| `indexType` | `String` | `private` | `"hybrid"` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public IndexConfig()` | - |
| `public IndexConfig(String indexName)` | - |
| `public IndexConfig(String indexName, String indexType)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void validate()` | `void` | - |
| `public String getIndexName()` | `String` | - |
| `public void setIndexName(String indexName)` | `void` | - |
| `public String getIndexType()` | `String` | - |
| `public void setIndexType(String indexType)` | `void` | - |

### `KnowledgeBaseConfig`

- 类型：`class`
- 声明：`public class KnowledgeBaseConfig`
- 说明：Knowledge base configuration.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `kbId` | `String` | `private` | `-` | - |
| `indexType` | `String` | `private` | `"hybrid"` | - |
| `useGraph` | `boolean` | `private` | `false` | - |
| `chunkSize` | `int` | `private` | `512` | - |
| `chunkOverlap` | `int` | `private` | `50` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public KnowledgeBaseConfig()` | - |
| `public KnowledgeBaseConfig(String kbId)` | - |
| `public KnowledgeBaseConfig(String kbId, String indexType, boolean useGraph, int chunkSize, int chunkOverlap)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void validate()` | `void` | - |
| `public String getKbId()` | `String` | - |
| `public void setKbId(String kbId)` | `void` | - |
| `public String getIndexType()` | `String` | - |
| `public void setIndexType(String indexType)` | `void` | - |
| `public boolean isUseGraph()` | `boolean` | - |
| `public void setUseGraph(boolean useGraph)` | `void` | - |
| `public int getChunkSize()` | `int` | - |
| `public void setChunkSize(int chunkSize)` | `void` | - |
| `public int getChunkOverlap()` | `int` | - |
| `public void setChunkOverlap(int chunkOverlap)` | `void` | - |

### `LoggingCallback`

- 类型：`class`
- 声明：`public class LoggingCallback extends BaseCallback`
- 说明：Simple SLF4J-backed callback for batch progress.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LoggingCallback(int total, String desc)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void onBatch(int startIdx, int endIdx, List<String> batch)` | `void` | - |

### `MultiKBRetrievalResult`

- 类型：`class`
- 声明：`@Getter @Setter public class MultiKBRetrievalResult extends RetrievalResult`
- 说明：Retrieval result aggregated across multiple knowledge bases.
- 注解：`@Getter`、`@Setter`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `rawScore` | `double` | `private` | `-` | - |
| `rawScoreScaled` | `double` | `private` | `-` | - |
| `kbIds` | `List<String>` | `private` | `new ArrayList<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MultiKBRetrievalResult(String text, double score, double rawScore, double rawScoreScaled, List<String> kbIds, Map<String, Object> metadata)` | - |

### `MultimodalDocument`

- 类型：`class`
- 声明：`public class MultimodalDocument extends Document`
- 说明：Multimodal document model.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MultimodalDocument()` | - |
| `public MultimodalDocument(String id, String text, Map<String, Object> metadata)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Map<String, Object>> getContent()` | `List<Map<String, Object>>` | - |
| `public MultimodalDocument addField(String kind, String data)` | `MultimodalDocument` | - |
| `public MultimodalDocument addField(String kind, Object data, Object filePath, Object dataId)` | `MultimodalDocument` | - |
| `public MultimodalDocument addField(String kind, Path filePath)` | `MultimodalDocument` | - |

### `RRFRankConfig`

- 类型：`class`
- 声明：`public class RRFRankConfig extends BaseRankConfig`
- 说明：RRF ranker configuration.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `k` | `int` | `private` | `40` | - |
| `denseName` | `boolean` | `private` | `true` | - |
| `denseContent` | `boolean` | `private` | `true` | - |
| `sparseContent` | `boolean` | `private` | `true` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RRFRankConfig()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public RankerArguments getArgs()` | `RankerArguments` | - |
| `public List<Integer> isActive()` | `List<Integer>` | - |
| `public int getK()` | `int` | - |
| `public void setK(int k)` | `void` | - |
| `public boolean isDenseName()` | `boolean` | - |
| `public void setDenseName(boolean denseName)` | `void` | - |
| `public boolean isDenseContent()` | `boolean` | - |
| `public void setDenseContent(boolean denseContent)` | `void` | - |
| `public boolean isSparseContent()` | `boolean` | - |
| `public void setSparseContent(boolean sparseContent)` | `void` | - |

### `RerankerConfig`

- 类型：`class`
- 声明：`public class RerankerConfig`
- 说明：Reranker model configuration aligned with the Python implementation.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `apiKey` | `String` | `private` | `""` | - |
| `apiBase` | `String` | `private` | `-` | - |
| `modelName` | `String` | `private` | `""` | - |
| `timeout` | `double` | `private` | `10.0` | - |
| `temperature` | `double` | `private` | `0.95` | - |
| `topP` | `double` | `private` | `0.1` | - |
| `yesNoIds` | `List<Integer>` | `private` | `-` | - |
| `extraBody` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RerankerConfig()` | - |
| `public RerankerConfig(String apiBase)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getApiKey()` | `String` | - |
| `public void setApiKey(String apiKey)` | `void` | - |
| `public String getApiBase()` | `String` | - |
| `public void setApiBase(String apiBase)` | `void` | - |
| `public String getModelName()` | `String` | - |
| `public void setModelName(String modelName)` | `void` | - |
| `public double getTimeout()` | `double` | - |
| `public void setTimeout(double timeout)` | `void` | - |
| `public double getTemperature()` | `double` | - |
| `public void setTemperature(double temperature)` | `void` | - |
| `public double getTopP()` | `double` | - |
| `public void setTopP(double topP)` | `void` | - |
| `public List<Integer> getYesNoIds()` | `List<Integer>` | - |
| `public void setYesNoIds(List<Integer> yesNoIds)` | `void` | - |
| `public Map<String, Object> getExtraBody()` | `Map<String, Object>` | - |
| `public void setExtraBody(Map<String, Object> extraBody)` | `void` | - |

### `ResultRankRegistry`

- 类型：`class`
- 声明：`public final class ResultRankRegistry`
- 说明：Registry for database-native ranker implementations.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void registerResultRankerClass(String database, Class<?> weightedClass, Class<?> rrfClass, Map<String, Class<?>> extras)` | `void` | - |
| `public static Class<?> getRankerClass(String database, String name)` | `Class<?>` | - |
| `public static Map<String, Class<?>> getRankerClasses(String database)` | `Map<String, Class<?>>` | - |

### `RetrievalConfig`

- 类型：`class`
- 声明：`public class RetrievalConfig`
- 说明：Retrieval-time options.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `topK` | `int` | `private` | `5` | - |
| `scoreThreshold` | `Double` | `private` | `-` | - |
| `useGraph` | `Boolean` | `private` | `-` | - |
| `agentic` | `boolean` | `private` | `false` | - |
| `graphExpansion` | `boolean` | `private` | `false` | - |
| `filters` | `Map<String, Object>` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RetrievalConfig()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public int getTopK()` | `int` | - |
| `public void setTopK(int topK)` | `void` | - |
| `public Double getScoreThreshold()` | `Double` | - |
| `public void setScoreThreshold(Double scoreThreshold)` | `void` | - |
| `public Boolean getUseGraph()` | `Boolean` | - |
| `public void setUseGraph(Boolean useGraph)` | `void` | - |
| `public boolean isAgentic()` | `boolean` | - |
| `public void setAgentic(boolean agentic)` | `void` | - |
| `public boolean isGraphExpansion()` | `boolean` | - |
| `public void setGraphExpansion(boolean graphExpansion)` | `void` | - |
| `public Map<String, Object> getFilters()` | `Map<String, Object>` | - |
| `public void setFilters(Map<String, Object> filters)` | `void` | - |

### `RetrievalExceptions`

- 类型：`class`
- 声明：`public final class RetrievalExceptions`
- 说明：Helpers for building retrieval-related exceptions with concise call sites.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static BaseError error(StatusCode status, String message)` | `BaseError` | - |
| `public static ValidationError validation(String message)` | `ValidationError` | - |

### `RetrievalResult`

- 类型：`class`
- 声明：`@Getter @Setter public class RetrievalResult`
- 说明：User-facing retrieval result.
- 注解：`@Getter`、`@Setter`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `text` | `String` | `private` | `-` | - |
| `score` | `double` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |
| `docId` | `String` | `private` | `-` | - |
| `chunkId` | `String` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public RetrievalResult()` | - |
| `public RetrievalResult(String text, double score)` | - |
| `public RetrievalResult(String text, double score, Map<String, Object> metadata, String docId, String chunkId)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setText(String text)` | `void` | - |
| `public void setMetadata(Map<String, Object> metadata)` | `void` | - |

### `RetrievalValidation`

- 类型：`class`
- 声明：`public final class RetrievalValidation`
- 说明：Shared retrieval validation helpers.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `INDEX_TYPES` | `Set<String>` | `public static final` | `Set.of("hybrid", "bm25", "vector")` | - |
| `DISTANCE_METRICS` | `Set<String>` | `public static final` | `Set.of("cosine", "euclidean", "dot")` | - |
| `STORE_TYPES` | `Set<String>` | `public static final` | `Set.of("milvus", "chroma", "pgvector")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void requireNonBlank(String value, String field)` | `void` | - |
| `public static void requireNonNull(Object value, String field)` | `void` | - |
| `public static void requirePositive(int value, String field, StatusCode status)` | `void` | - |
| `public static void requireNonNegative(int value, String field, StatusCode status)` | `void` | - |
| `public static String validateIndexType(String value, String field)` | `String` | - |
| `public static String validateDistanceMetric(String value, String field)` | `String` | - |
| `public static String validateStoreType(String value, String field)` | `String` | - |
| `public static void validateDatabaseName(String value, String field)` | `void` | - |

### `SearchResult`

- 类型：`class`
- 声明：`@Getter @Setter public class SearchResult`
- 说明：Raw search result.
- 注解：`@Getter`、`@Setter`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `-` | - |
| `text` | `String` | `private` | `-` | - |
| `score` | `double` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SearchResult()` | - |
| `public SearchResult(String id, String text, double score)` | - |
| `public SearchResult(String id, String text, double score, Map<String, Object> metadata)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setId(String id)` | `void` | - |
| `public void setText(String text)` | `void` | - |
| `public void setMetadata(Map<String, Object> metadata)` | `void` | - |

### `StoreType`

- 类型：`enum`
- 声明：`public enum StoreType`
- 说明：Supported vector store providers.

**枚举常量**

| 名称 | 初始化值 | 说明 |
|---|---|---|
| `MILVUS` | `new StoreType("milvus")` | - |
| `CHROMA` | `new StoreType("chroma")` | - |
| `PGVECTOR` | `new StoreType("pgvector")` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String value()` | `String` | - |
| `public static StoreType fromValue(String value)` | `StoreType` | - |

### `TextChunk`

- 类型：`class`
- 声明：`@Getter @Setter public class TextChunk`
- 说明：Text chunk model.
- 注解：`@Getter`、`@Setter`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `private` | `-` | - |
| `text` | `String` | `private` | `-` | - |
| `docId` | `String` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |
| `embedding` | `List<Float>` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TextChunk()` | - |
| `public TextChunk(String id, String text, String docId)` | - |
| `public TextChunk(String id, String text, String docId, Map<String, Object> metadata, List<Float> embedding)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static TextChunk fromDocument(Document document, String chunkText)` | `TextChunk` | - |
| `public static TextChunk fromDocument(Document document, String chunkText, String id)` | `TextChunk` | - |
| `public void setId(String id)` | `void` | - |
| `public void setText(String text)` | `void` | - |
| `public void setDocId(String docId)` | `void` | - |
| `public void setMetadata(Map<String, Object> metadata)` | `void` | - |
| `public void setEmbedding(List<Float> embedding)` | `void` | - |

### `TqdmCallback`

- 类型：`class`
- 声明：`public class TqdmCallback extends BaseCallback`
- 说明：Lightweight progress callback aligned with Python's TqdmCallback.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TqdmCallback(Collection<?> sequence)` | - |
| `public TqdmCallback(Collection<?> sequence, String desc)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void onBatch(int startIdx, int endIdx, List<String> batch)` | `void` | - |
| `public int length()` | `int` | - |
| `public String getDesc()` | `String` | - |

### `Triple`

- 类型：`class`
- 声明：`@Getter @Setter public class Triple`
- 说明：Knowledge triple.
- 注解：`@Getter`、`@Setter`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `subject` | `String` | `private` | `-` | - |
| `predicate` | `String` | `private` | `-` | - |
| `object` | `String` | `private` | `-` | - |
| `confidence` | `Double` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `new LinkedHashMap<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public Triple()` | - |
| `public Triple(String subject, String predicate, String object)` | - |
| `public Triple(String subject, String predicate, String object, Double confidence, Map<String, Object> metadata)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setSubject(String subject)` | `void` | - |
| `public void setPredicate(String predicate)` | `void` | - |
| `public void setObject(String object)` | `void` | - |
| `public void setMetadata(Map<String, Object> metadata)` | `void` | - |

### `TripleBeam`

- 类型：`class`
- 声明：`public class TripleBeam implements Iterable<RetrievalResult>`
- 说明：Beam of retrieval triples.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TripleBeam(List<RetrievalResult> triples, double score)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public RetrievalResult get(int index)` | `RetrievalResult` | - |
| `public int size()` | `int` | - |
| `public boolean contains(RetrievalResult triple)` | `boolean` | - |
| `public List<RetrievalResult> getTriples()` | `List<RetrievalResult>` | - |
| `public double getScore()` | `double` | - |
| `public Iterator<RetrievalResult> iterator()` | `Iterator<RetrievalResult>` | - |

### `TripleMemory`

- 类型：`class`
- 声明：`public class TripleMemory`
- 说明：Deduplicated triple memory.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public int size()` | `int` | - |
| `public List<List<String>> getMemory()` | `List<List<String>>` | - |
| `public String getTriplesStr()` | `String` | - |
| `public void extendMemory(List<String> triple)` | `void` | - |
| `public void batchExtendMemory(List<List<String>> triples)` | `void` | - |

### `VectorStoreConfig`

- 类型：`class`
- 声明：`public class VectorStoreConfig`
- 说明：Vector store configuration.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `storeProvider` | `String` | `private` | `-` | - |
| `databaseName` | `String` | `private` | `""` | - |
| `collectionName` | `String` | `private` | `-` | - |
| `distanceMetric` | `String` | `private` | `"cosine"` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public VectorStoreConfig()` | - |
| `public VectorStoreConfig(String storeProvider, String collectionName)` | - |
| `public VectorStoreConfig(StoreType storeProvider, String collectionName)` | - |
| `public VectorStoreConfig(String storeProvider, String databaseName, String collectionName, String distanceMetric)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void validate()` | `void` | - |
| `public String getStoreProvider()` | `String` | - |
| `public StoreType getStoreType()` | `StoreType` | - |
| `public void setStoreProvider(String storeProvider)` | `void` | - |
| `public String getDatabaseName()` | `String` | - |
| `public void setDatabaseName(String databaseName)` | `void` | - |
| `public String getCollectionName()` | `String` | - |
| `public void setCollectionName(String collectionName)` | `void` | - |
| `public String getDistanceMetric()` | `String` | - |
| `public void setDistanceMetric(String distanceMetric)` | `void` | - |

### `WeightedRankConfig`

- 类型：`class`
- 声明：`public class WeightedRankConfig extends BaseRankConfig`
- 说明：Weighted ranker configuration for dense/sparse fusion.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `denseName` | `double` | `private` | `0.15` | - |
| `denseContent` | `double` | `private` | `0.6` | - |
| `sparseContent` | `double` | `private` | `0.25` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WeightedRankConfig()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public RankerArguments getArgs()` | `RankerArguments` | - |
| `public double getDenseName()` | `double` | - |
| `public void setDenseName(double denseName)` | `void` | - |
| `public double getDenseContent()` | `double` | - |
| `public void setDenseContent(double denseContent)` | `void` | - |
| `public double getSparseContent()` | `double` | - |
| `public void setSparseContent(double sparseContent)` | `void` | - |

## `com.openjiuwen.core.retrieval.embedding`

公开类型：`6`

### `APIEmbedding`

- 类型：`class`
- 声明：`public class APIEmbedding implements Embedding, AutoCloseable`
- 说明：Universal HTTP embedding client aligned with the Python APIEmbedding implementation.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `config` | `EmbeddingConfig` | `protected final` | `-` | - |
| `modelName` | `String` | `protected final` | `-` | - |
| `apiKey` | `String` | `protected final` | `-` | - |
| `apiUrl` | `String` | `protected final` | `-` | - |
| `timeout` | `int` | `protected final` | `-` | - |
| `maxRetries` | `int` | `protected final` | `-` | - |
| `maxBatchSize` | `int` | `protected final` | `-` | - |
| `maxConcurrent` | `int` | `protected final` | `-` | - |
| `headers` | `Map<String, String>` | `protected final` | `-` | - |
| `httpClient` | `HttpClient` | `protected final` | `-` | - |
| `executor` | `ExecutorService` | `protected final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public APIEmbedding(EmbeddingConfig config)` | - |
| `public APIEmbedding(EmbeddingConfig config, int timeout, int maxRetries, Map<String, String> extraHeaders, int maxBatchSize, int maxConcurrent)` | - |
| `public APIEmbedding(EmbeddingConfig config, int timeout, int maxRetries, Map<String, String> extraHeaders, int maxBatchSize, int maxConcurrent, HttpClient httpClient)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Float> embedQuery(String text)` | `List<Float>` | - |
| `public List<Float> embedQuery(String text, Map<String, Object> options)` | `List<Float>` | - |
| `public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize)` | `List<List<Float>>` | - |
| `public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize, Map<String, Object> options)` | `List<List<Float>>` | - |
| `public int getDimension()` | `int` | - |
| `public int getMaxBatchSize()` | `int` | - |
| `protected List<List<Float>> getEmbeddings(Object input, Map<String, Object> options)` | `List<List<Float>>` | - |
| `protected List<List<Float>> parseEmbeddings(JsonNode root)` | `List<List<Float>>` | - |
| `protected List<Float> parseSingleEmbedding(JsonNode embeddingNode)` | `List<Float>` | - |
| `protected static List<String> validateTexts(List<String> texts)` | `List<String>` | - |
| `protected static BaseCallback resolveCallback(Map<String, Object> options, Collection<?> sequence)` | `BaseCallback` | - |
| `protected static Map<String, Object> cleanPayloadOptions(Map<String, Object> options)` | `Map<String, Object>` | - |
| `public void close()` | `void` | - |

### `Embedding`

- 类型：`interface`
- 声明：`public interface Embedding`
- 说明：Embedding model abstraction.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `List<Float> embedQuery(String text)` | `List<Float>` | - |
| `default List<Float> embedQuery(String text, Map<String, Object> options)` | `List<Float>` | - |
| `List<List<Float>> embedDocuments(List<String> texts, Integer batchSize)` | `List<List<Float>>` | - |
| `default List<List<Float>> embedDocuments(List<String> texts, Integer batchSize, Map<String, Object> options)` | `List<List<Float>>` | - |
| `int getDimension()` | `int` | - |
| `default int getMaxBatchSize()` | `int` | - |

### `EmbeddingUtils`

- 类型：`class`
- 声明：`public final class EmbeddingUtils`
- 说明：Helpers for embedding model implementations.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static List<Float> parseBase64Embedding(String base64Embedding)` | `List<Float>` | - |

### `HashEmbedding`

- 类型：`class`
- 声明：`public class HashEmbedding implements Embedding`
- 说明：Deterministic local embedding based on SHA-256 hashing.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public HashEmbedding()` | - |
| `public HashEmbedding(int dimension, int maxBatchSize)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Float> embedQuery(String text)` | `List<Float>` | - |
| `public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize)` | `List<List<Float>>` | - |
| `public int getDimension()` | `int` | - |
| `public int getMaxBatchSize()` | `int` | - |

### `OpenAIEmbedding`

- 类型：`class`
- 声明：`public class OpenAIEmbedding extends APIEmbedding`
- 说明：OpenAI-compatible embedding client with base64 embedding support.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public OpenAIEmbedding(EmbeddingConfig config)` | - |
| `public OpenAIEmbedding(EmbeddingConfig config, int timeout, int maxRetries, Map<String, String> extraHeaders, int maxBatchSize, int maxConcurrent, Integer dimension, HttpClient httpClient)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public int getDimension()` | `int` | - |
| `public List<Float> embedQuery(String text, Map<String, Object> options)` | `List<Float>` | - |
| `public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize, Map<String, Object> options)` | `List<List<Float>>` | - |
| `protected List<List<Float>> parseEmbeddings(JsonNode root)` | `List<List<Float>>` | - |

### `VLLMEmbedding`

- 类型：`class`
- 声明：`public class VLLMEmbedding extends OpenAIEmbedding`
- 说明：vLLM-compatible multimodal embedding client.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public VLLMEmbedding(EmbeddingConfig config)` | - |
| `public VLLMEmbedding(EmbeddingConfig config, int timeout, int maxRetries, Map<String, String> extraHeaders, int maxBatchSize, int maxConcurrent, Integer dimension, HttpClient httpClient)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static Map<String, Object> parseMultimodalInput(MultimodalDocument document, Map<String, Object> options)` | `Map<String, Object>` | - |
| `public List<Float> embedMultimodal(MultimodalDocument document)` | `List<Float>` | - |
| `public List<Float> embedMultimodal(Object input, Map<String, Object> options)` | `List<Float>` | - |
| `public List<Float> embedMultimodal(MultimodalDocument document, Map<String, Object> options)` | `List<Float>` | - |
| `public List<Float> embedMultimodalSync(MultimodalDocument document)` | `List<Float>` | - |
| `public List<Float> embedMultimodalSync(Object input, Map<String, Object> options)` | `List<Float>` | - |
| `public List<Float> embedMultimodalSync(MultimodalDocument document, Map<String, Object> options)` | `List<Float>` | - |

## `com.openjiuwen.core.retrieval.indexing.indexer`

公开类型：`6`

### `ChromaIndexer`

- 类型：`class`
- 声明：`public class ChromaIndexer extends InMemoryIndexer`
- 说明：Chroma-compatible indexer backed by the in-memory implementation.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ChromaIndexer(VectorStore vectorStore)` | - |

### `InMemoryIndexer`

- 类型：`class`
- 声明：`public class InMemoryIndexer implements Indexer`
- 说明：In-memory index manager backed by VectorStore.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public InMemoryIndexer(VectorStore vectorStore)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel, Map<String, Object> options)` | `boolean` | - |
| `public boolean updateIndex(List<TextChunk> chunks, String docId, IndexConfig config, Embedding embedModel, Map<String, Object> options)` | `boolean` | - |
| `public boolean deleteIndex(String docId, String indexName, Map<String, Object> options)` | `boolean` | - |
| `public boolean indexExists(String indexName)` | `boolean` | - |
| `public Map<String, Object> getIndexInfo(String indexName)` | `Map<String, Object>` | - |
| `public String getDatabaseName()` | `String` | - |
| `public String getDistanceMetric()` | `String` | - |
| `public String getIndexType()` | `String` | - |
| `public String getTextField()` | `String` | - |
| `public String getVectorField()` | `String` | - |
| `public String getSparseVectorField()` | `String` | - |
| `public String getMetadataField()` | `String` | - |
| `public String getDocIdField()` | `String` | - |

### `IndexBackendConfig`

- 类型：`interface`
- 声明：`public interface IndexBackendConfig`
- 说明：Shared config surface that must match between vector store and index manager.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `String getDatabaseName()` | `String` | - |
| `String getDistanceMetric()` | `String` | - |
| `String getIndexType()` | `String` | - |
| `String getTextField()` | `String` | - |
| `String getVectorField()` | `String` | - |
| `String getSparseVectorField()` | `String` | - |
| `String getMetadataField()` | `String` | - |
| `String getDocIdField()` | `String` | - |

### `Indexer`

- 类型：`interface`
- 声明：`public interface Indexer extends IndexBackendConfig, AutoCloseable`
- 说明：Index manager abstraction.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel, Map<String, Object> options)` | `boolean` | - |
| `boolean updateIndex(List<TextChunk> chunks, String docId, IndexConfig config, Embedding embedModel, Map<String, Object> options)` | `boolean` | - |
| `boolean deleteIndex(String docId, String indexName, Map<String, Object> options)` | `boolean` | - |
| `boolean indexExists(String indexName)` | `boolean` | - |
| `Map<String, Object> getIndexInfo(String indexName)` | `Map<String, Object>` | - |
| `default void close()` | `void` | - |

### `IndexerFactory`

- 类型：`class`
- 声明：`public final class IndexerFactory`
- 说明：Factory for pairing a vector store with its index manager implementation.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static Indexer createIndexer(VectorStore vectorStore)` | `Indexer` | - |

### `MilvusIndexer`

- 类型：`class`
- 声明：`public class MilvusIndexer implements Indexer`
- 说明：Milvus-backed index manager for retrieval.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MilvusIndexer(MilvusVectorStore vectorStore)` | - |
| `public MilvusIndexer(VectorStoreConfig config, String milvusUri, String indexType)` | - |
| `public MilvusIndexer(VectorStoreConfig config, String milvusUri, String milvusToken, String indexType)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel, Map<String, Object> options)` | `boolean` | - |
| `public boolean updateIndex(List<TextChunk> chunks, String docId, IndexConfig config, Embedding embedModel, Map<String, Object> options)` | `boolean` | - |
| `public boolean deleteIndex(String docId, String indexName, Map<String, Object> options)` | `boolean` | - |
| `public boolean indexExists(String indexName)` | `boolean` | - |
| `public Map<String, Object> getIndexInfo(String indexName)` | `Map<String, Object>` | - |
| `public void close()` | `void` | - |
| `public String getDatabaseName()` | `String` | - |
| `public String getDistanceMetric()` | `String` | - |
| `public String getIndexType()` | `String` | - |
| `public String getTextField()` | `String` | - |
| `public String getVectorField()` | `String` | - |
| `public String getSparseVectorField()` | `String` | - |
| `public String getMetadataField()` | `String` | - |
| `public String getDocIdField()` | `String` | - |

## `com.openjiuwen.core.retrieval.indexing.processor`

公开类型：`1`

### `Processor`

- 类型：`interface`
- 声明：`public interface Processor<I, O>`
- 说明：Generic retrieval processor abstraction.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `O process(I input, Map<String, Object> options)` | `O` | - |

## `com.openjiuwen.core.retrieval.indexing.processor.chunker`

公开类型：`11`

### `CharChunker`

- 类型：`class`
- 声明：`public class CharChunker extends Chunker`
- 说明：Character window chunker.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public CharChunker(int chunkSize, int chunkOverlap)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<String> chunkText(String text)` | `List<String>` | - |

### `Chunker`

- 类型：`class`
- 声明：`public abstract class Chunker implements Processor<List<Document>, List<TextChunk>>`
- 说明：Chunker abstraction for documents.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `chunkSize` | `int` | `protected final` | `-` | - |
| `chunkOverlap` | `int` | `protected final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected Chunker(int chunkSize, int chunkOverlap)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract List<String> chunkText(String text)` | `List<String>` | - |
| `public List<TextChunk> chunkDocuments(List<Document> documents)` | `List<TextChunk>` | - |
| `public List<TextChunk> process(List<Document> input, Map<String, Object> options)` | `List<TextChunk>` | - |

### `ChunkerRegistry`

- 类型：`class`
- 声明：`public final class ChunkerRegistry`
- 说明：Registry for named chunkers.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void registerChunker(String name, Supplier<Chunker> factory)` | `void` | - |
| `public static Chunker getChunker(String name)` | `Chunker` | - |

### `HybridChunker`

- 类型：`class`
- 声明：`public class HybridChunker extends Chunker`
- 说明：Chunker that skips splitting for specific document types.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `innerChunker` | `Chunker` | `private final` | `-` | - |
| `noSplitWhen` | `Predicate<Document>` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public HybridChunker(Chunker innerChunker)` | - |
| `public HybridChunker(Chunker innerChunker, Predicate<Document> noSplitWhen)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<String> chunkText(String text)` | `List<String>` | - |
| `public List<TextChunk> chunkDocuments(List<Document> documents)` | `List<TextChunk>` | - |

### `PreprocessingPipeline`

- 类型：`class`
- 声明：`public class PreprocessingPipeline implements TextPreprocessor`
- 说明：Sequential text preprocessing pipeline.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `preprocessors` | `List<TextPreprocessor>` | `private final` | `new ArrayList<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public PreprocessingPipeline(List<TextPreprocessor> preprocessors)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String process(String text)` | `String` | - |

### `SpecialCharacterNormalizer`

- 类型：`class`
- 声明：`public class SpecialCharacterNormalizer implements TextPreprocessor`
- 说明：Replaces control characters with spaces.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String process(String text)` | `String` | - |

### `TextChunker`

- 类型：`class`
- 声明：`public class TextChunker extends Chunker`
- 说明：Composite chunker with preprocessing.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `innerChunker` | `Chunker` | `private final` | `-` | - |
| `pipeline` | `PreprocessingPipeline` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TextChunker(int chunkSize, int chunkOverlap, String chunkUnit)` | - |
| `public TextChunker(int chunkSize, int chunkOverlap, String chunkUnit, Function<String, List<String>> tokenizer, String language)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<String> chunkText(String text)` | `List<String>` | - |
| `public List<TextChunk> chunkDocuments(List<Document> documents)` | `List<TextChunk>` | - |

### `TextPreprocessor`

- 类型：`interface`
- 声明：`public interface TextPreprocessor`
- 说明：Text preprocessor abstraction.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `String process(String text)` | `String` | - |

### `TokenizerChunker`

- 类型：`class`
- 声明：`public class TokenizerChunker extends Chunker`
- 说明：Token-aware chunker backed by SentenceSplitter.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `splitter` | `SentenceSplitter` | `private final` | `-` | - |
| `tokenizer` | `Function<String, List<String>>` | `private final` | `-` | - |
| `language` | `String` | `private final` | `-` | - |
| `splitterConfig` | `Map<String, Object>` | `private final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TokenizerChunker(int chunkSize, int chunkOverlap)` | - |
| `public TokenizerChunker(int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer)` | - |
| `public TokenizerChunker(int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer, String language, Map<String, Object> splitterConfig)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<String> chunkText(String text)` | `List<String>` | - |
| `public Function<String, List<String>> getTokenizer()` | `Function<String, List<String>>` | - |
| `public String getLanguage()` | `String` | - |
| `public Map<String, Object> getSplitterConfig()` | `Map<String, Object>` | - |

### `URLEmailRemover`

- 类型：`class`
- 声明：`public class URLEmailRemover implements TextPreprocessor`
- 说明：Removes URLs and email addresses from text.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String process(String text)` | `String` | - |

### `WhitespaceNormalizer`

- 类型：`class`
- 声明：`public class WhitespaceNormalizer implements TextPreprocessor`
- 说明：Normalizes repeated whitespace.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String process(String text)` | `String` | - |

## `com.openjiuwen.core.retrieval.indexing.processor.extractor`

公开类型：`3`

### `Extractor`

- 类型：`class`
- 声明：`public abstract class Extractor implements Processor<List<TextChunk>, List<Triple>>`
- 说明：Triple extractor abstraction.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options)` | `List<Triple>` | - |
| `public List<Triple> process(List<TextChunk> input, Map<String, Object> options)` | `List<Triple>` | - |

### `LLMTripleExtractor`

- 类型：`class`
- 声明：`public class LLMTripleExtractor extends Extractor`
- 说明：LLM-backed triple extractor aligned with the Python implementation.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public LLMTripleExtractor(BaseModelClient llmClient, String modelName)` | - |
| `public LLMTripleExtractor(BaseModelClient llmClient, String modelName, float temperature, int maxConcurrent)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options)` | `List<Triple>` | - |

### `SimpleTripleExtractor`

- 类型：`class`
- 声明：`public class SimpleTripleExtractor extends Extractor`
- 说明：Lightweight local triple extractor based on sentence tokenization.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Triple> extract(List<TextChunk> chunks, Map<String, Object> options)` | `List<Triple>` | - |

## `com.openjiuwen.core.retrieval.indexing.processor.parser`

公开类型：`15`

### `AutoFileParser`

- 类型：`class`
- 声明：`public class AutoFileParser extends Parser`
- 说明：File parser router based on file extension.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static void registerNewParser(String extension, Supplier<? extends Parser> supplier)` | `void` | - |
| `public static List<String> getSupportedFormats()` | `List<String>` | - |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | `List<Document>` | - |
| `protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options)` | `String` | - |
| `public boolean supports(String doc)` | `boolean` | - |

### `AutoLinkParser`

- 类型：`class`
- 声明：`public class AutoLinkParser extends Parser`
- 说明：URL parser router.
- 嵌套公开类型：`AutoLinkParser.Route`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `HTTP_URL_PATTERN` | `Pattern` | `public static final` | `Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE)` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AutoLinkParser()` | - |
| `public AutoLinkParser(List<Route> routes)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | `List<Document>` | - |
| `protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options)` | `String` | - |
| `public boolean supports(String doc)` | `boolean` | - |

### `AutoLinkParser.Route`

- 类型：`record`
- 声明：`public record Route(Predicate<String> matcher, Parser parser)`
- 说明：URL 路由规则，将匹配谓词绑定到具体解析器。
- 宿主类型：`AutoLinkParser`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `matcher` | `Predicate<String>` | `private final` | `-` | - |
| `parser` | `Parser` | `private final` | `-` | - |

### `AutoParser`

- 类型：`class`
- 声明：`public class AutoParser extends Parser`
- 说明：Top-level parser that routes between file and URL parsers.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AutoParser()` | - |
| `public AutoParser(Parser linkParser, Parser fileParser)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | `List<Document>` | - |
| `protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options)` | `String` | - |
| `public boolean supports(String doc)` | `boolean` | - |

### `ExcelParser`

- 类型：`class`
- 声明：`public class ExcelParser extends Parser`
- 说明：Parser for xlsx/csv/tsv tabular files that emits row and column documents.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static String cellStr(Object value)` | `String` | - |
| `public static List<Document> rowsToDocuments(List<? extends List<?>> rows, String sheetName, String baseId, int sheetIndex, boolean includeHeader)` | `List<Document>` | - |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | `List<Document>` | - |
| `protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options)` | `String` | - |
| `public boolean supports(String doc)` | `boolean` | - |

### `ImageCaptioner`

- 类型：`class`
- 声明：`public class ImageCaptioner`
- 说明：Lightweight image caption helper aligned with the Python retrieval parser stack.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `IMAGE_CAPTION_PROMPT` | `String` | `public static final` | `"Write a short caption describing the provided image."` | - |
| `SAVED_IMAGE_DIR` | `String` | `public static final` | `"images"` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ImageCaptioner(BaseModelClient llmClient)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static String cpImage(String imageLoc)` | `String` | - |
| `public static String cpImage(String imageLoc, String targetDir)` | `String` | - |
| `public List<String> captionImages(List<String> imageLocs)` | `List<String>` | - |
| `protected String llmCall(String imageLoc)` | `String` | - |

### `ImageParser`

- 类型：`class`
- 声明：`public class ImageParser extends Parser`
- 说明：Parser for image files using LLM captions.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | `List<Document>` | - |
| `protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options)` | `String` | - |
| `public boolean supports(String doc)` | `boolean` | - |

### `JsonParser`

- 类型：`class`
- 声明：`public class JsonParser extends Parser`
- 说明：JSON file parser that returns formatted JSON text when possible.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | `List<Document>` | - |
| `protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options)` | `String` | - |
| `public boolean supports(String doc)` | `boolean` | - |

### `PDFParser`

- 类型：`class`
- 声明：`public class PDFParser extends Parser`
- 说明：PDF parser with optional image caption extraction.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | `List<Document>` | - |
| `protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options)` | `String` | - |
| `public boolean supports(String doc)` | `boolean` | - |

### `Parser`

- 类型：`class`
- 声明：`public abstract class Parser implements Processor<String, List<Document>>`
- 说明：Document parser abstraction.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | `List<Document>` | - |
| `protected abstract String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options)` | `String` | - |
| `public boolean supports(String doc)` | `boolean` | - |
| `public List<Document> process(String input, Map<String, Object> options)` | `List<Document>` | - |

### `TextFileParser`

- 类型：`class`
- 声明：`public class TextFileParser extends Parser`
- 说明：Simple UTF-8 text file parser.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options)` | `String` | - |
| `public boolean supports(String doc)` | `boolean` | - |

### `TxtMdParser`

- 类型：`class`
- 声明：`public class TxtMdParser extends Parser`
- 说明：TXT/MD file parser aligned with the Python TxtMdParser behavior.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | `List<Document>` | - |
| `protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options)` | `String` | - |
| `public boolean supports(String doc)` | `boolean` | - |

### `WeChatArticleParser`

- 类型：`class`
- 声明：`public class WeChatArticleParser extends WebPageParser`
- 说明：WeChat article parser.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WeChatArticleParser()` | - |
| `public WeChatArticleParser(HttpClient httpClient)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static boolean isWechatArticleUrl(String url)` | `boolean` | - |
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | `List<Document>` | - |
| `public boolean supports(String doc)` | `boolean` | - |

### `WebPageParser`

- 类型：`class`
- 声明：`public class WebPageParser extends Parser`
- 说明：Generic web page parser.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `TITLE_META_PATTERN` | `Pattern` | `protected static final` | `Pattern.compile("<meta[^>]+property=[\"\']og:title[\"\'][^>]+content=[\"\']([^\"\']+)[\"\'][^>]*>", Pattern.CASE_INSENSITIVE \| Pattern.DOTALL)` | - |
| `TITLE_PATTERN` | `Pattern` | `protected static final` | `Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE \| Pattern.DOTALL)` | - |
| `httpClient` | `HttpClient` | `protected final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public WebPageParser()` | - |
| `public WebPageParser(HttpClient httpClient)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | `List<Document>` | - |
| `protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options)` | `String` | - |
| `public boolean supports(String doc)` | `boolean` | - |
| `protected String fetchHtml(String url)` | `String` | - |
| `protected static String extractReadableText(String html, Pattern preferredPattern)` | `String` | - |
| `protected static String extractFirst(String html, Pattern pattern, String fallback)` | `String` | - |

### `WordParser`

- 类型：`class`
- 声明：`public class WordParser extends Parser`
- 说明：DOCX parser with optional image caption support.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options)` | `List<Document>` | - |
| `protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options)` | `String` | - |
| `public boolean supports(String doc)` | `boolean` | - |

## `com.openjiuwen.core.retrieval.indexing.processor.splitter`

公开类型：`2`

### `SentenceSplitter`

- 类型：`class`
- 声明：`public class SentenceSplitter extends Splitter`
- 说明：Sentence-aware splitter with lightweight language detection and tokenizer-aware windows.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SentenceSplitter(int chunkSize, int chunkOverlap)` | - |
| `public SentenceSplitter(int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer, String language)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<String> splitText(String text)` | `List<String>` | - |

### `Splitter`

- 类型：`class`
- 声明：`public abstract class Splitter implements Processor<List<Document>, List<TextChunk>>`
- 说明：Text splitter abstraction.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `chunkSize` | `int` | `protected final` | `-` | - |
| `chunkOverlap` | `int` | `protected final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected Splitter(int chunkSize, int chunkOverlap)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public abstract List<String> splitText(String text)` | `List<String>` | - |
| `public List<TextChunk> getNodesFromDocuments(List<Document> documents)` | `List<TextChunk>` | - |
| `public List<TextChunk> process(List<Document> input, Map<String, Object> options)` | `List<TextChunk>` | - |

## `com.openjiuwen.core.retrieval.query_rewriter`

公开类型：`1`

### `QueryRewriter`

- 类型：`class`
- 声明：`public class QueryRewriter`
- 说明：Query rewriter with template loading, JSON parsing, and optional context-aware compression.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `llmClient` | `BaseModelClient` | `private final` | `-` | - |
| `context` | `ModelContext` | `private final` | `-` | - |
| `compressRange` | `int` | `private final` | `-` | - |
| `promptLang` | `String` | `private final` | `-` | - |
| `templateCache` | `Map<String, String>` | `private final` | `new ConcurrentHashMap<>()` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public QueryRewriter(BaseModelClient llmClient)` | - |
| `public QueryRewriter(BaseModelClient llmClient, ModelContext context, int compressRange, String promptLang)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String rewrite(String query, List<RetrievalResult> results)` | `String` | - |
| `public Map<String, Object> compress(List<BaseMessage> messages)` | `Map<String, Object>` | - |
| `public Map<String, Object> rewrite(String query)` | `Map<String, Object>` | - |
| `public String loadTemplate(String promptBase)` | `String` | - |
| `public String msgToText(List<BaseMessage> messages)` | `String` | - |

## `com.openjiuwen.core.retrieval.reranker`

公开类型：`5`

### `ChatReranker`

- 类型：`class`
- 声明：`public class ChatReranker extends StandardReranker`
- 说明：Chat-completion-based reranker aligned with Python's ChatReranker behavior.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ChatReranker(RerankerConfig config)` | - |
| `public ChatReranker(RerankerConfig config, int maxRetries, Map<String, String> extraHeaders, HttpClient httpClient)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected List<Double> rerankOrderedScores(String query, List<String> documents, Object instruct, Map<String, Object> options)` | `List<Double>` | - |

### `LexicalReranker`

- 类型：`class`
- 声明：`public class LexicalReranker implements Reranker`
- 说明：Local lexical reranker based on token overlap.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK)` | `List<RetrievalResult>` | - |

### `Reranker`

- 类型：`interface`
- 声明：`public interface Reranker`
- 说明：Reranker abstraction.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK)` | `List<RetrievalResult>` | - |

### `StandardReranker`

- 类型：`class`
- 声明：`public class StandardReranker implements Reranker`
- 说明：Remote reranker implementation aligned with Python's StandardReranker behavior.
- 嵌套公开类型：`StandardReranker.CandidateBatch`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `ENDPOINT` | `String` | `protected static final` | `"/rerank"` | - |
| `QUERY_TEMPLATE` | `String` | `protected static final` | `"<Instruct>: %s\n<Query>: %s\n"` | - |
| `DEFAULT_INSTRUCT` | `String` | `protected static final` | `"Given a search query, retrieve relevant candidates that answer the query."` | - |
| `config` | `RerankerConfig` | `protected final` | `-` | - |
| `modelName` | `String` | `protected final` | `-` | - |
| `apiKey` | `String` | `protected final` | `-` | - |
| `apiUrl` | `String` | `protected final` | `-` | - |
| `maxRetries` | `int` | `protected final` | `-` | - |
| `headers` | `Map<String, String>` | `protected final` | `-` | - |
| `httpClient` | `HttpClient` | `protected final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public StandardReranker(RerankerConfig config)` | - |
| `public StandardReranker(RerankerConfig config, int maxRetries, Map<String, String> extraHeaders, HttpClient httpClient)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Map<String, Double> rerankScores(String query, List<?> documents)` | `Map<String, Double>` | - |
| `public Map<String, Double> rerankScores(String query, List<?> documents, Object instruct, Map<String, Object> options)` | `Map<String, Double>` | - |
| `public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK)` | `List<RetrievalResult>` | - |
| `protected List<Double> rerankOrderedScores(String query, List<String> documents, Object instruct, Map<String, Object> options)` | `List<Double>` | - |
| `protected Map<String, Object> buildRequestPayload(String query, List<String> documents, Object instruct, Map<String, Object> options)` | `Map<String, Object>` | - |
| `protected List<Double> parseOrderedScores(JsonNode response, int documentCount)` | `List<Double>` | - |
| `protected static String buildQuery(String query, Object instruct)` | `String` | - |
| `protected static CandidateBatch prepareCandidates(List<?> documents)` | `CandidateBatch` | - |
| `protected static String candidateId(RetrievalResult result)` | `String` | - |
| `protected static String normalizeBaseUrl(String baseUrl, String endpoint)` | `String` | - |

### `StandardReranker.CandidateBatch`

- 类型：`record`
- 声明：`protected record CandidateBatch(List<String> ids, List<String> texts)`
- 说明：重排前的候选批次，保存归一化后的候选 id 与文本。
- 宿主类型：`StandardReranker`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `ids` | `List<String>` | `private final` | `-` | - |
| `texts` | `List<String>` | `private final` | `-` | - |

## `com.openjiuwen.core.retrieval.retriever`

公开类型：`9`

### `AbstractRetriever`

- 类型：`class`
- 声明：`public abstract class AbstractRetriever implements Retriever`
- 说明：Common retriever defaults.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK, String mode, Map<String, Object> options)` | `List<List<RetrievalResult>>` | - |

### `AbstractStoreBackedRetriever`

- 类型：`class`
- 声明：`public abstract class AbstractStoreBackedRetriever extends AbstractRetriever`
- 说明：Base class for retrievers backed by a vector store.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `vectorStore` | `VectorStore` | `protected final` | `-` | - |
| `embedModel` | `Embedding` | `protected final` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected AbstractStoreBackedRetriever(VectorStore vectorStore, Embedding embedModel)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public VectorStore getVectorStore()` | `VectorStore` | - |
| `public Embedding getEmbedModel()` | `Embedding` | - |
| `public String getIndexType()` | `String` | - |

### `AgenticRetriever`

- 类型：`class`
- 声明：`public class AgenticRetriever extends AbstractRetriever`
- 说明：Retriever that adds iterative query rewriting and triple reading on top of a base retriever.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AgenticRetriever(Retriever retriever, BaseModelClient llmClient)` | - |
| `public AgenticRetriever(Retriever retriever, BaseModelClient llmClient, int maxIter)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public boolean isGraphRetriever()` | `boolean` | - |
| `public String getDefaultMode()` | `String` | - |
| `public String getIndexType()` | `String` | - |
| `public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)` | `List<RetrievalResult>` | - |
| `public List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK, String mode, Map<String, Object> options)` | `List<List<RetrievalResult>>` | - |
| `public void close()` | `void` | - |

### `GraphRetriever`

- 类型：`class`
- 声明：`public class GraphRetriever extends AbstractRetriever`
- 说明：Graph-aware retriever that expands retrieved chunks through linked triples.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public GraphRetriever(Retriever chunkRetriever, Retriever tripleRetriever)` | - |
| `public GraphRetriever(VectorStore vectorStore, Embedding embedModel, String chunkCollection, String tripleCollection)` | - |
| `public GraphRetriever(Retriever chunkRetriever, Retriever tripleRetriever, VectorStore vectorStore, Embedding embedModel, String chunkCollection, String tripleCollection)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setIndexType(String indexType)` | `void` | - |
| `public String getIndexType()` | `String` | - |
| `public boolean supportsMode(String mode)` | `boolean` | - |
| `public Retriever getRetrieverForMode(String mode, boolean isChunk)` | `Retriever` | - |
| `public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)` | `List<RetrievalResult>` | - |
| `public List<RetrievalResult> graphExpansion(String query, List<RetrievalResult> chunks, List<RetrievalResult> triples, Integer topK, String mode, Map<String, Object> options)` | `List<RetrievalResult>` | - |
| `public void close()` | `void` | - |

### `HybridRetriever`

- 类型：`class`
- 声明：`public class HybridRetriever extends AbstractStoreBackedRetriever`
- 说明：Hybrid retriever combining sparse and dense retrieval.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public HybridRetriever(VectorStore vectorStore, Embedding embedModel)` | - |
| `public HybridRetriever(VectorStore vectorStore, Embedding embedModel, double alpha)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)` | `List<RetrievalResult>` | - |
| `public List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options)` | `List<SearchResult>` | - |
| `public boolean supportsMode(String mode)` | `boolean` | - |

### `Retriever`

- 类型：`interface`
- 声明：`public interface Retriever extends AutoCloseable`
- 说明：Unified retriever abstraction.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)` | `List<RetrievalResult>` | - |
| `List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK, String mode, Map<String, Object> options)` | `List<List<RetrievalResult>>` | - |
| `default List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options)` | `List<SearchResult>` | - |
| `default List<RetrievalResult> retrieve(String query)` | `List<RetrievalResult>` | - |
| `default List<RetrievalResult> retrieve(String query, int topK)` | `List<RetrievalResult>` | - |
| `default boolean supportsMode(String mode)` | `boolean` | - |
| `default String getIndexType()` | `String` | - |
| `default void close()` | `void` | - |

### `SparseRetriever`

- 类型：`class`
- 声明：`public class SparseRetriever extends AbstractStoreBackedRetriever`
- 说明：Sparse / BM25-like retriever.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public SparseRetriever(VectorStore vectorStore)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)` | `List<RetrievalResult>` | - |
| `public List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options)` | `List<SearchResult>` | - |
| `public boolean supportsMode(String mode)` | `boolean` | - |

### `TripleBeamSearch`

- 类型：`class`
- 声明：`public class TripleBeamSearch`
- 说明：Triple beam search used by graph retrieval.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public TripleBeamSearch(Retriever retriever)` | - |
| `public TripleBeamSearch(Retriever retriever, int numBeams, int numCandidatesPerBeam, int maxLength)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<TripleBeam> beamSearch(String query, List<RetrievalResult> triples)` | `List<TripleBeam>` | - |

### `VectorRetriever`

- 类型：`class`
- 声明：`public class VectorRetriever extends AbstractStoreBackedRetriever`
- 说明：Pure vector retriever.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public VectorRetriever(VectorStore vectorStore, Embedding embedModel)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)` | `List<RetrievalResult>` | - |
| `public List<SearchResult> retrieveSearchResults(String query, int topK, String mode, Map<String, Object> options)` | `List<SearchResult>` | - |
| `public boolean supportsMode(String mode)` | `boolean` | - |

## `com.openjiuwen.core.retrieval.utils`

公开类型：`4`

### `ApiRequestUtils`

- 类型：`class`
- 声明：`public final class ApiRequestUtils`
- 说明：Shared HTTP request helper for retrieval services with retry support.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static JsonNode postJsonWithRetry(HttpClient httpClient, String url, Map<String, Object> payload, Map<String, String> headers, Duration timeout, int maxRetries, StatusCode failureCode, String taskName)` | `JsonNode` | - |

### `CommonUtils`

- 类型：`class`
- 声明：`public final class CommonUtils`
- 说明：Common retrieval utilities.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static <T, K>List<T> deduplicate(Iterable<T> data, Function<T, K> keyFn)` | `List<T>` | - |

### `ConfigManager`

- 类型：`class`
- 声明：`public class ConfigManager`
- 说明：Unified configuration manager for retrieval module.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ConfigManager()` | - |
| `public ConfigManager(String configPath)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void loadFromFile(String path)` | `void` | - |
| `public void saveToFile(String path)` | `void` | - |
| `public <T>T getConfig(Class<T> configType)` | `T` | - |
| `public KnowledgeBaseConfig getKnowledgeBaseConfig()` | `KnowledgeBaseConfig` | - |
| `public void updateConfig(Object config)` | `void` | - |

### `FusionUtils`

- 类型：`class`
- 声明：`public final class FusionUtils`
- 说明：Fusion algorithms such as reciprocal rank fusion.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static List<RetrievalResult> rrfFusionRetrieval(List<List<RetrievalResult>> resultsList, int k)` | `List<RetrievalResult>` | - |
| `public static List<SearchResult> rrfFusionSearch(List<List<SearchResult>> resultsList, int k)` | `List<SearchResult>` | - |
| `public static List<RetrievalResult> rrfFusionRetrieval(List<List<RetrievalResult>> resultsList, RRFRankConfig config)` | `List<RetrievalResult>` | - |
| `public static List<SearchResult> rrfFusionSearch(List<List<SearchResult>> resultsList, RRFRankConfig config)` | `List<SearchResult>` | - |
| `public static List<RetrievalResult> weightedFusionRetrieval(List<List<RetrievalResult>> resultsList, WeightedRankConfig config)` | `List<RetrievalResult>` | - |
| `public static List<SearchResult> weightedFusionSearch(List<List<SearchResult>> resultsList, WeightedRankConfig config)` | `List<SearchResult>` | - |

## `com.openjiuwen.core.retrieval.vector_store`

公开类型：`7`

### `ChromaVectorStore`

- 类型：`class`
- 声明：`public class ChromaVectorStore extends InMemoryVectorStore`
- 说明：Local Chroma-compatible vector store backed by the in-memory implementation.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ChromaVectorStore(VectorStoreConfig config)` | - |
| `public ChromaVectorStore(VectorStoreConfig config, String indexType)` | - |

### `InMemoryVectorStore`

- 类型：`class`
- 声明：`public class InMemoryVectorStore implements VectorStore, SchemaMutableVectorStore`
- 说明：Local in-memory vector store used for translated retrieval regression tests.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public InMemoryVectorStore(String collectionName)` | - |
| `public InMemoryVectorStore(VectorStoreConfig config, String indexType)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getCollectionName()` | `String` | - |
| `public void setCollectionName(String collectionName)` | `void` | - |
| `public VectorStore withCollection(String collectionName)` | `VectorStore` | - |
| `public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options)` | `void` | - |
| `public List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options)` | `List<SearchResult>` | - |
| `public List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options)` | `List<SearchResult>` | - |
| `public List<SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha, Map<String, Object> filters, Map<String, Object> options)` | `List<SearchResult>` | - |
| `public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options)` | `boolean` | - |
| `public boolean tableExists(String tableName)` | `boolean` | - |
| `public void deleteTable(String tableName)` | `void` | - |
| `public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit)` | `List<SearchResult>` | - |
| `public long count(String tableName)` | `long` | - |
| `public List<String> listCollectionNames()` | `List<String>` | - |
| `public Map<String, Object> getCollectionMetadata(String collectionName)` | `Map<String, Object>` | - |
| `public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata)` | `void` | - |
| `public void updateSchema(String collectionName, List<?> operations)` | `void` | - |
| `public String getDatabaseName()` | `String` | - |
| `public String getDistanceMetric()` | `String` | - |
| `public String getIndexType()` | `String` | - |
| `public String getTextField()` | `String` | - |
| `public String getVectorField()` | `String` | - |
| `public String getSparseVectorField()` | `String` | - |
| `public String getMetadataField()` | `String` | - |
| `public String getDocIdField()` | `String` | - |

### `MilvusVectorStore`

- 类型：`class`
- 声明：`public class MilvusVectorStore implements VectorStore`
- 说明：Milvus-backed vector store for retrieval.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public MilvusVectorStore(VectorStoreConfig config, String milvusUri)` | - |
| `public MilvusVectorStore(VectorStoreConfig config, String milvusUri, String indexType)` | - |
| `public MilvusVectorStore(VectorStoreConfig config, String milvusUri, String milvusToken, String indexType)` | - |
| `public MilvusVectorStore(MilvusClientV2 client, VectorStoreConfig config, String indexType)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static MilvusClientV2 createClient(String databaseName, String milvusUri, String milvusToken)` | `MilvusClientV2` | - |
| `public MilvusClientV2 getClient()` | `MilvusClientV2` | - |
| `public String getMilvusUri()` | `String` | - |
| `public String getMilvusToken()` | `String` | - |
| `public String getCollectionName()` | `String` | - |
| `public void setCollectionName(String collectionName)` | `void` | - |
| `public VectorStore withCollection(String collectionName)` | `VectorStore` | - |
| `public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options)` | `void` | - |
| `public void ensureCollection(String targetCollection, String requestedIndexType, Integer dimension)` | `void` | - |
| `public List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options)` | `List<SearchResult>` | - |
| `public List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options)` | `List<SearchResult>` | - |
| `public List<SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha, Map<String, Object> filters, Map<String, Object> options)` | `List<SearchResult>` | - |
| `public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options)` | `boolean` | - |
| `public boolean tableExists(String tableName)` | `boolean` | - |
| `public void deleteTable(String tableName)` | `void` | - |
| `public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit)` | `List<SearchResult>` | - |
| `public long count(String tableName)` | `long` | - |
| `public void close()` | `void` | - |
| `public String getDatabaseName()` | `String` | - |
| `public String getDistanceMetric()` | `String` | - |
| `public String getIndexType()` | `String` | - |
| `public String getTextField()` | `String` | - |
| `public String getVectorField()` | `String` | - |
| `public String getSparseVectorField()` | `String` | - |
| `public String getMetadataField()` | `String` | - |
| `public String getDocIdField()` | `String` | - |

### `PGVectorStore`

- 类型：`class`
- 声明：`public class PGVectorStore extends InMemoryVectorStore`
- 说明：Local PGVector-compatible vector store backed by the in-memory implementation.

**构造方法**

| 签名 | 说明 |
|---|---|
| `public PGVectorStore(VectorStoreConfig config)` | - |
| `public PGVectorStore(VectorStoreConfig config, String indexType)` | - |

### `SchemaMutableVectorStore`

- 类型：`interface`
- 声明：`public interface SchemaMutableVectorStore extends VectorStore`
- 说明：Optional extension for vector stores that support schema and collection metadata updates.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `List<String> listCollectionNames()` | `List<String>` | - |
| `Map<String, Object> getCollectionMetadata(String collectionName)` | `Map<String, Object>` | - |
| `void updateCollectionMetadata(String collectionName, Map<String, Object> metadata)` | `void` | - |
| `void updateSchema(String collectionName, List<?> operations)` | `void` | - |

### `VectorStore`

- 类型：`interface`
- 声明：`public interface VectorStore extends IndexBackendConfig, AutoCloseable`
- 说明：Unified vector store abstraction.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `String getCollectionName()` | `String` | - |
| `void setCollectionName(String collectionName)` | `void` | - |
| `VectorStore withCollection(String collectionName)` | `VectorStore` | - |
| `void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options)` | `void` | - |
| `List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options)` | `List<SearchResult>` | - |
| `List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options)` | `List<SearchResult>` | - |
| `List<SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha, Map<String, Object> filters, Map<String, Object> options)` | `List<SearchResult>` | - |
| `boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options)` | `boolean` | - |
| `boolean tableExists(String tableName)` | `boolean` | - |
| `void deleteTable(String tableName)` | `void` | - |
| `List<SearchResult> queryByFilters(Map<String, Object> filters, int limit)` | `List<SearchResult>` | - |
| `long count(String tableName)` | `long` | - |
| `default void close()` | `void` | - |

### `VectorStoreFactory`

- 类型：`class`
- 声明：`public final class VectorStoreFactory`
- 说明：Factory for creating vector stores from configuration.

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static VectorStore createVectorStore(VectorStoreConfig config)` | `VectorStore` | - |
| `public static VectorStore createVectorStore(VectorStoreConfig config, Map<String, Object> options)` | `VectorStore` | - |

