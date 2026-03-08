# Java `mvn test` 修复与 Milvus 接入工作报告

- 日期: 2026-03-08
- 工作目录: `F:\oepnjiuwen\agent-core-java\agent-core-java`

## 1. 工作目标

本次工作的目标有 3 项:

1. 修复 Java 版本当前 `mvn test` 无法通过的问题。
2. 对照 Python 版 retrieval 代码，为 Java retrieval 模块补齐 Milvus 向量数据库实现。
3. 完成修复后跑通全量测试，并输出本次工作的书面报告。

## 2. `mvn test` 失败原因与修复

### 2.1 测试代码本身存在编译错误

修复了以下 3 处会直接导致 `testCompile` 失败的问题:

1. `MemoryCallOperatorTest`
   - 问题: mock 的 `invoke()` 声明了受检异常，但测试方法未声明 `throws Exception`。
   - 修复: 补上测试方法签名中的 `throws Exception`。

2. `TextFileParserTest`
   - 问题 1: 断言使用了不存在的 `Document.getContent()`，实际 Java 版文档对象使用 `getText()`。
   - 问题 2: UTF-8 测试字符串存在编码损坏。
   - 修复: 重写该测试文件，改为断言 `getText()`，并恢复正常 UTF-8 文本样例。

3. `ConfigTest`
   - 问题: `EmbeddingConfig` 使用的是 `getBaseUrl()`，测试却调用了不存在的 `getApiBase()`。
   - 修复: 调整为正确的 getter。

### 2.2 全量测试中的并发状态缺陷

在补完 Milvus 后重新跑全量 `mvn test`，发现 `WorkflowTest.testWorkflowWithWaitForAll` 会抛出:

- `ConcurrentModificationException`

根因是:

- `InMemoryCommitState` 的 `updates` 使用普通 `HashMap + ArrayList`
- `InMemoryStateLike` 的 `state` 也没有并发保护
- workflow 的 `wait_for_all` 场景会并发提交节点输出，导致状态容器在迭代/写入时并发修改

修复方式:

1. 为 `InMemoryCommitState.update/updateById/commit/rollback/getUpdates/setUpdates` 增加同步保护。
2. 为 `InMemoryStateLike.get/getByPrefix/getByTransformer/update/getState/setState` 增加同步保护。

这样没有改变上层状态语义，只修复了 in-memory 实现的线程安全缺陷。

## 3. Milvus 实现内容

### 3.1 依赖

在 `pom.xml` 中新增:

- `io.milvus:milvus-sdk-java:2.6.13`

### 3.2 新增类

新增了 2 个 retrieval 侧核心类:

1. `com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore`
2. `com.openjiuwen.core.retrieval.indexing.indexer.MilvusIndexer`

### 3.3 `MilvusVectorStore` 实现范围

已实现以下能力:

1. Milvus 客户端创建与数据库存在性保证
   - 自动连接 Milvus
   - 当 `databaseName` 非默认库时，自动创建并切换数据库

2. 向量写入
   - 支持分批 `insert`
   - 写入后 `flush`

3. Dense 检索
   - 支持 `search`
   - 支持 `cosine` / `dot(IP)` / `euclidean(L2)` 映射
   - 返回 `raw_score` 与向量归一化后的 `raw_score_scaled`

4. Sparse 检索
   - 使用 Milvus 原生 BM25
   - 通过 `EmbeddedText` + `sparse_vector` 字段执行全文检索

5. Hybrid 检索
   - 使用原生 `HybridSearchReq`
   - 支持 native `WeightedRanker` / `RRFRanker`
   - native hybrid 失败时，回退为 Java 侧 dense + sparse 再融合

6. 过滤表达式
   - `Map<String, Object>` filter 自动转成 Milvus 表达式
   - `Collection` 值转成 `IN` 子句
   - 单值转成 `==`

7. 逻辑删除与逻辑 ID 语义
   - 删除不是按 Milvus 主键，而是按 retrieval 层逻辑 `chunk_id`
   - 这与 Java retrieval 上层的文档/切片语义一致

8. 元信息查询
   - `queryByFilters`
   - `tableExists`
   - `deleteTable`
   - `count`

### 3.4 `MilvusIndexer` 实现范围

已实现以下能力:

1. 自动建表
   - 主键 `pk` (`Int64`, auto id)
   - `doc_id`
   - `chunk_id`
   - `text`
   - `vector`
   - `sparse_vector`
   - `metadata`

2. BM25 schema/function 建立
   - 文本字段启用 analyzer/match
   - 建立 `BM25` function，将文本映射到稀疏向量字段
   - 为 `sparse_vector` 创建 `SPARSE_INVERTED_INDEX`

3. Dense 向量 schema/index 建立
   - 自动解析 embedding 维度
   - 对 dense 向量字段创建 `AUTOINDEX`

4. 索引构建
   - 对 `vector` / `hybrid` 类型批量生成 embedding
   - 写入 Milvus 时只写 retrieval schema 中已定义的字段
   - 不再写无 schema 的 `id` 字段

5. 重复文档校验
   - 在 `buildIndex()` 时，按 `doc_id` 检查已有数据
   - 避免同一 `doc_id` 被重复 `add`

6. 更新/删除/索引信息
   - `updateIndex`
   - `deleteIndex`
   - `indexExists`
   - `getIndexInfo`

## 4. 与 Python 版对齐时的设计取舍

本次不是简单照搬 Python 字段名，而是做了“Python 行为对齐 + Java 现有抽象兼容”:

1. Java retrieval 现有字段约定是:
   - `text`
   - `vector`
   - `sparse_vector`
   - `metadata`
   - `doc_id`
   - `chunk_id`

2. Python retrieval/milvus 实现默认字段更偏向:
   - `content`
   - `embedding`
   - `document_id`

3. 最终选择:
   - 保持 Java retrieval 当前字段约定不变
   - 用 Milvus 实现去适配 Java 上层抽象

原因是:

- 这样可以直接与 Java 现有 `Retriever` / `KnowledgeBase` / `Indexer` 语义兼容
- 不会引入新的 store/index 配置不一致问题
- `KnowledgeBase.validateIndex()` 也能保持通过

## 5. 新增/更新测试

### 5.1 新增测试

新增了以下 Milvus 单元测试:

1. `MilvusVectorStoreTest`
   - 向量分数归一化
   - hybrid fallback
   - filter `IN` 表达式
   - 逻辑删除表达式

2. `MilvusIndexerTest`
   - 自动建表
   - 逻辑字段写入
   - 重复 `doc_id` 拒绝
   - 删除按 `doc_id` 过滤

### 5.2 回归结果

已通过以下回归:

1. 定向 Milvus 回归:

```powershell
mvn -Dtest="MilvusVectorStoreTest,MilvusIndexerTest" test
```

2. 全量回归:

```powershell
mvn test
```

最终结果:

- `mvn test` 全量通过

## 6. 本次变更涉及的关键文件

### 6.1 主要新增

- `src/main/java/com/openjiuwen/core/retrieval/vector_store/MilvusVectorStore.java`
- `src/main/java/com/openjiuwen/core/retrieval/indexing/indexer/MilvusIndexer.java`
- `src/test/java/com/openjiuwen/core/retrieval/vector_store/MilvusVectorStoreTest.java`
- `src/test/java/com/openjiuwen/core/retrieval/indexing/indexer/MilvusIndexerTest.java`

### 6.2 主要修复

- `pom.xml`
- `src/test/java/com/openjiuwen/core/operator/memory_call/MemoryCallOperatorTest.java`
- `src/test/java/com/openjiuwen/core/retrieval/common/ConfigTest.java`
- `src/test/java/com/openjiuwen/core/retrieval/indexing/processor/parser/TextFileParserTest.java`
- `src/main/java/com/openjiuwen/core/session/state/InMemoryCommitState.java`
- `src/main/java/com/openjiuwen/core/session/state/InMemoryStateLike.java`

## 7. 当前已知边界

本次已经把 Java retrieval 的 Milvus 主链路补齐，但仍有几个边界需要说明:

1. 当前新增的是 retrieval 层 Milvus 适配，不是 foundation/store 层的完整通用 Milvus 封装。
2. Chroma / PGVector 仍未实现。
3. 当前测试以 mock Milvus SDK 为主，没有接真实 Milvus 服务做集成测试。
4. Java retrieval 目前没有统一的 store factory / provider 自动装配入口，Milvus 需要显式实例化使用。

## 8. 结论

本次工作完成后:

1. Java 项目的 `mvn test` 已恢复为全量通过。
2. retrieval 模块已具备可用的 Milvus dense / sparse / hybrid 检索与索引构建能力。
3. 额外修复了 workflow 并发状态提交的线程安全问题，消除了全量测试中的隐性不稳定项。

