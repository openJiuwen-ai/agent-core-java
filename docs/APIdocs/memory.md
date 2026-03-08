# Memory 模块 API 文档

> 包路径：`com.openjiuwen.core.memory`

Memory 模块提供长期记忆能力，负责消息持久化、变量记忆、片段记忆、摘要记忆、语义检索、记忆抽取以及多种存储后端的迁移与维护。模块主入口是单例 `LongTermMemory`，其下通过配置对象、管理器、存储模型和迁移工具组成完整记忆引擎。

---

## 目录

- [1. 核心入口](#1-核心入口)
- [2. 配置与并发控制](#2-配置与并发控制)
- [3. 索引、写入与检索管理](#3-索引写入与检索管理)
- [4. 存储模型与底层存储](#4-存储模型与底层存储)
- [5. 更新检测与记忆提取](#5-更新检测与记忆提取)
- [6. 迁移工具](#6-迁移工具)

---

## 1. 核心入口

### 1.1 LongTermMemory

长期记忆引擎主入口，使用单例模式管理 KV/向量/数据库三类存储、LLM、Embedding 与各类记忆管理器。

**包路径**：`com.openjiuwen.core.memory`

**常量**

| 常量名 | 值 | 说明 |
|--------|----|------|
| `DEFAULT_VALUE` | `"__default__"` | 默认占位值 |
| `SCOPE_CONFIG_KEY` | `"memory_scope_config"` | scope 配置在 KV 中的键前缀 |

**静态方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getInstance()` | `LongTermMemory` | 获取全局单例 |
| `resetInstance()` | `void` | 重置单例，主要用于测试 |

**存储与配置方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `registerStore(BaseKVStore kvStore, VectorStore vectorStore, BaseDbStore<?> dbStore, Embedding embedding)` | `void` | 注册 KV、向量、SQL 存储以及默认 embedding，并触发迁移 |
| `setConfig(MemoryEngineConfig config)` | `void` | 设置引擎级配置，并初始化 message/fragment/summary/variable/search/write 管理器 |
| `setScopeConfig(String scopeId, MemoryScopeConfig config)` | `boolean` | 设置 scope 级模型和 embedding 配置，API key 会加密存入 KV |
| `getScopeConfig(String scopeId)` | `MemoryScopeConfig` | 获取并解密 scope 配置 |
| `deleteScopeConfig(String scopeId)` | `boolean` | 删除 scope 配置 |
| `deleteMemByScope(String scopeId)` | `boolean` | 删除某 scope 下所有用户记忆和关联映射 |

**消息写入与读取**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addMessages(List<BaseMessage> messages, AgentMemoryConfig agentConfig, String userId, String scopeId, String sessionId, OffsetDateTime timestamp, boolean genMem, int genMemWithHistoryMsgNum)` | `void` | 写入消息，并可选择触发记忆抽取与生成 |
| `addMessages(List<BaseMessage> messages, AgentMemoryConfig agentConfig, String userId, String scopeId, String sessionId)` | `void` | 简化写入接口，默认开启记忆生成 |
| `getRecentMessages(String userId, String scopeId, String sessionId, int num)` | `List<BaseMessage>` | 读取最近 `num` 条消息 |
| `getMessageById(String msgId)` | `MessageManager.MessageRecord` | 按消息 ID 获取消息及时间戳 |
| `deleteMessagesByUserAndScope(String userId, String scopeId)` | `void` | 删除指定用户和 scope 下的消息 |

**记忆 CRUD**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `deleteMemById(String memId, String userId, String scopeId)` | `void` | 按记忆 ID 删除一条记忆 |
| `deleteMemByUserId(String userId, String scopeId)` | `void` | 删除某用户在 scope 下的全部记忆 |
| `updateMemById(String memId, String memory, String userId, String scopeId)` | `void` | 更新指定记忆内容 |

**变量操作**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getVariables(Object names, String userId, String scopeId)` | `Map<String, String>` | 查询变量；`names` 支持 `null`、`String`、`List<String>` |
| `updateVariables(Map<String, String> variables, String userId, String scopeId)` | `void` | 批量更新变量 |
| `deleteVariables(List<String> names, String userId, String scopeId)` | `boolean` | 删除指定变量 |

**检索与分页**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `searchUserMem(String query, int num, String userId, String scopeId, double threshold)` | `List<MemResult>` | 语义检索用户片段记忆 |
| `searchUserHistorySummary(String query, int num, String userId, String scopeId, double threshold)` | `List<MemResult>` | 语义检索历史摘要记忆 |
| `userMemTotalNum(String userId, String scopeId)` | `int` | 统计用户片段记忆数量 |
| `getUserMemByPage(String userId, String scopeId, int pageSize, int pageIdx, MemoryType memoryType)` | `List<MemInfo>` | 按页读取记忆；当 `memoryType == UNKNOWN` 时表示不限类型 |

### 1.2 MemInfo

记忆信息视图对象。

**包路径**：`com.openjiuwen.core.memory`

**字段**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `memId` | `String` | `""` | 记忆 ID |
| `content` | `String` | `""` | 记忆内容 |
| `type` | `MemoryType` | `FRAGMENT_MEMORY` | 记忆类型 |

### 1.3 MemResult

记忆检索结果对象。

**包路径**：`com.openjiuwen.core.memory`

**字段**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `memInfo` | `MemInfo` | - | 命中的记忆信息 |
| `score` | `double` | `0.0` | 相似度分数 |

---

## 2. 配置与并发控制

### 2.1 AgentMemoryConfig

Agent 侧记忆功能开关与变量定义配置。

**包路径**：`com.openjiuwen.core.memory.config`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `memVariables` | `List<Param>` | `[]` | 需要从对话中抽取的变量定义 |
| `enableLongTermMem` | `boolean` | `true` | 是否启用长期记忆 |
| `enableFragmentMemory` | `boolean` | `true` | 是否启用片段记忆 |
| `enableSummaryMemory` | `boolean` | `true` | 是否启用摘要记忆 |

### 2.2 MemoryEngineConfig

引擎级默认模型与安全配置。

**包路径**：`com.openjiuwen.core.memory.config`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `defaultModelCfg` | `ModelRequestConfig` | `null` | 默认大模型请求配置 |
| `defaultModelClientCfg` | `ModelClientConfig` | `null` | 默认模型客户端配置 |
| `inputMsgMaxLen` | `int` | `8192` | 参与分析的最大输入长度 |
| `cryptoKey` | `byte[]` | `new byte[0]` | 记忆内容加密密钥，允许为空 |
| `singleTurnHistorySummaryMaxToken` | `int` | `128` | 单轮摘要最大 token |

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `validateCryptoKey()` | `void` | 校验 `cryptoKey` 长度必须为空或 32 字节 |

### 2.3 MemoryScopeConfig

scope 级模型与 embedding 配置。

**包路径**：`com.openjiuwen.core.memory.config`

| 字段 | 类型 | 说明 |
|------|------|------|
| `modelCfg` | `ModelRequestConfig` | scope 专属模型请求配置 |
| `modelClientCfg` | `ModelClientConfig` | scope 专属模型客户端配置 |
| `embeddingCfg` | `EmbeddingConfig` | scope 专属 embedding 配置 |

### 2.4 DistributedLock

基于 KV 的同步分布式锁，依赖 `exclusiveSet`。

**包路径**：`com.openjiuwen.core.memory.common`

**构造方法**
```java
DistributedLock(BaseKVStore store, String lockName)
```

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `acquire()` | `void` | 轮询获取锁 |
| `release()` | `void` | 释放当前锁 |
| `close()` | `void` | `AutoCloseable` 实现，等价于 `release()` |

---

## 3. 索引、写入与检索管理

### 3.1 BaseMemoryManager

所有记忆管理器的抽象基类。

**包路径**：`com.openjiuwen.core.memory.manage.index`

**抽象方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addMemories(String userId, String scopeId, List<? extends BaseMemoryUnit> memories, Map.Entry<String, Model> llm, Map<String, Object> kwargs)` | `void` | 批量新增记忆 |
| `update(String userId, String scopeId, String memId, String newMemory, Map<String, Object> kwargs)` | `void` | 更新指定记忆 |
| `delete(String userId, String scopeId, String memId, Map<String, Object> kwargs)` | `boolean` | 删除指定记忆 |
| `deleteByUserId(String userId, String scopeId, Map<String, Object> kwargs)` | `boolean` | 删除用户在 scope 下的全部该类记忆 |
| `get(String userId, String scopeId, String memId)` | `Map<String, Object>` | 获取指定记忆 |
| `search(String userId, String scopeId, String query, int topK, Map<String, Object> kwargs)` | `List<Map<String, Object>>` | 检索记忆 |

**静态工具方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `encryptMemoryIfNeeded(byte[] key, String plaintext)` | `String` | 当配置密钥时加密记忆内容 |
| `decryptMemoryIfNeeded(byte[] key, String ciphertext)` | `String` | 对加密内容解密 |

### 3.2 FragmentMemoryManager

片段记忆管理器，负责用户 profile/事实类记忆的增删改查与向量索引更新。

**包路径**：`com.openjiuwen.core.memory.manage.index`

**常量**

| 常量名 | 值 | 说明 |
|--------|----|------|
| `UPDATE_CHECK_OLD_MEMORY_NUM` | `5` | 冲突检查时回查旧记忆数量 |
| `UPDATE_CHECK_OLD_MEMORY_RELEVANCE_THRESHOLD` | `0.75` | 冲突检查相似度阈值 |

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addMemories(...)` | `void` | 批量新增片段记忆，并通过 `MemUpdateChecker` 去重/冲突检测 |
| `update(...)` | `void` | 更新指定片段记忆并同步向量索引 |
| `search(...)` | `List<Map<String, Object>>` | 从向量索引检索片段记忆 |
| `get(...)` | `Map<String, Object>` | 按 ID 读取片段记忆 |
| `delete(...)` | `boolean` | 删除单条片段记忆 |
| `deleteByUserId(...)` | `boolean` | 删除用户的全部片段记忆 |
| `listFragmentMemories(String userId, String scopeId, String profileType)` | `List<Map<String, Object>>` | 列出片段记忆，可按 `profileType` 过滤 |

### 3.3 SummaryManager

摘要记忆管理器，负责会话/历史摘要的存储与向量检索。

**包路径**：`com.openjiuwen.core.memory.manage.index`

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addMemories(...)` | `void` | 新增摘要记忆并写入向量库 |
| `update(...)` | `void` | 更新摘要记忆 |
| `delete(...)` | `boolean` | 删除单条摘要记忆 |
| `deleteByUserId(...)` | `boolean` | 删除用户全部摘要记忆 |
| `get(...)` | `Map<String, Object>` | 按 ID 获取摘要记忆 |
| `search(...)` | `List<Map<String, Object>>` | 检索摘要记忆 |

### 3.4 VariableManager

变量记忆管理器，负责用户变量的读写与查询。

**包路径**：`com.openjiuwen.core.memory.manage.index`

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addMemories(...)` | `void` | 批量新增变量记忆 |
| `update(...)` | `void` | 更新变量记忆 |
| `updateUserVariable(String userId, String scopeId, String name, String value)` | `void` | 更新单个变量 |
| `delete(...)` | `boolean` | 删除变量记忆 |
| `deleteByUserId(...)` | `boolean` | 删除用户全部变量 |
| `deleteUserVariable(String userId, String scopeId, String name)` | `void` | 删除单个变量 |
| `get(...)` | `Map<String, Object>` | 获取变量记忆 |
| `search(...)` | `List<Map<String, Object>>` | 检索变量记忆 |
| `queryVariable(String userId, String scopeId, String varName, String defaultValue)` | `Map<String, String>` | 查询变量，支持单个或全部 |

### 3.5 WriteManager

写入协调器，根据记忆类型把新增、更新、删除请求分发给不同管理器。

**包路径**：`com.openjiuwen.core.memory.manage.index`

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addMemories(String userId, String scopeId, Map<String, ? extends List<? extends BaseMemoryUnit>> memories, Map.Entry<String, Model> llm, SemanticStore semanticStore)` | `void` | 按记忆类型批量写入 |
| `updateMemById(String userId, String scopeId, String memId, String memory, SemanticStore semanticStore)` | `void` | 根据存储中的 `mem_type` 自动更新记忆 |
| `deleteMemById(String userId, String scopeId, String memId, SemanticStore semanticStore)` | `void` | 根据 `mem_type` 删除单条记忆 |
| `deleteMemByUserId(String userId, String scopeId, SemanticStore semanticStore)` | `void` | 删除用户全部记忆 |

### 3.6 SearchManager

统一检索协调器，封装片段记忆、摘要记忆、变量和分页读取逻辑。

**包路径**：`com.openjiuwen.core.memory.manage.search`

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `search(SearchParams params, SemanticStore semanticStore)` | `List<Map<String, Object>>` | 按查询文本、类型、阈值执行统一检索 |
| `listUserMem(String userId, String scopeId, int nums, int pages, String memType)` | `List<Map<String, Object>>` | 按页列出记忆 |
| `listUserProfile(String userId, String scopeId, String profileType)` | `List<Map<String, Object>>` | 列出用户片段记忆 |
| `listUserProfile(String userId, String scopeId)` | `List<Map<String, Object>>` | 列出全部片段记忆 |
| `getUserVariable(String userId, String scopeId, String varName)` | `String` | 获取单个变量 |
| `getAllUserVariable(String userId, String scopeId)` | `Map<String, String>` | 获取全部变量 |

### 3.7 SearchParams

统一检索参数对象。

**包路径**：`com.openjiuwen.core.memory.manage.search`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `userId` | `String` | - | 用户 ID |
| `scopeId` | `String` | - | 作用域 ID |
| `query` | `String` | - | 检索文本 |
| `topK` | `int` | `5` | 返回结果数量 |
| `threshold` | `double` | `0.3` | 相似度阈值 |
| `searchType` | `String` | `null` | 指定记忆类型，如 `fragment`、`summary`、`variable` |

---

## 4. 存储模型与底层存储

### 4.1 BaseMemoryUnit 及子类

记忆单元模型定义。

**包路径**：`com.openjiuwen.core.memory.manage.mem_model`

#### BaseMemoryUnit

| 字段 | 类型 | 说明 |
|------|------|------|
| `memType` | `MemoryType` | 记忆类型 |
| `memId` | `String` | 记忆 ID |

#### FragmentMemoryUnit

| 字段 | 类型 | 说明 |
|------|------|------|
| `fragmentType` | `String` | 片段记忆主题/类型 |
| `content` | `String` | 片段内容 |
| `messageMemId` | `String` | 来源消息 ID |
| `timestamp` | `String` | 生成时间 |

#### SummaryUnit

| 字段 | 类型 | 说明 |
|------|------|------|
| `summary` | `String` | 摘要内容 |
| `messageMemId` | `String` | 来源消息 ID |
| `timestamp` | `String` | 生成时间 |

#### VariableUnit

| 字段 | 类型 | 说明 |
|------|------|------|
| `variableName` | `String` | 变量名 |
| `variableMem` | `String` | 变量值 |

**说明**：`VariableUnit#getMemId()` 固定返回空字符串，变量通过名字访问而不是独立 ID 访问。

### 4.2 MemoryType 与 SupportMemoryType

记忆类型枚举。

**包路径**：`com.openjiuwen.core.memory.manage.mem_model`

#### MemoryType

| 枚举值 | 值 | 说明 |
|--------|----|------|
| `FRAGMENT_MEMORY` | `"fragment"` | 片段记忆 |
| `VARIABLE` | `"variable"` | 变量记忆 |
| `SUMMARY` | `"summary"` | 摘要记忆 |
| `UNKNOWN` | `"unknown"` | 未知类型 |

#### SupportMemoryType

| 枚举值 | 值 | 说明 |
|--------|----|------|
| `USER_PROFILE` | `"user_profile"` | 用户画像类向量记忆 |
| `SUMMARY` | `"summary"` | 摘要类向量记忆 |

### 4.3 MessageAddRequest

消息写入请求对象。

**包路径**：`com.openjiuwen.core.memory.manage.mem_model`

| 字段 | 类型 | 说明 |
|------|------|------|
| `userId` | `String` | 用户 ID |
| `scopeId` | `String` | scope ID |
| `content` | `String` | 消息内容 |
| `role` | `String` | 消息角色 |
| `sessionId` | `String` | 会话 ID |
| `timestamp` | `OffsetDateTime` | 消息时间 |

### 4.4 DataIdManager

ID 生成器。

**包路径**：`com.openjiuwen.core.memory.manage.mem_model`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `generateNextId(String userId)` | `String` | 为指定用户生成下一个记忆/消息 ID |

### 4.5 MessageManager

基于 SQL 的消息持久化管理器。

**包路径**：`com.openjiuwen.core.memory.manage.mem_model`

**内部记录**
```java
public record MessageRecord(BaseMessage message, OffsetDateTime timestamp) {}
```

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `add(MessageAddRequest req)` | `String` | 写入消息并返回消息 ID |
| `get(String userId, String scopeId, String sessionId, int messageLen)` | `List<MessageRecord>` | 按时间倒序读取最近消息，再在返回前恢复为正序 |
| `getById(String msgId)` | `MessageRecord` | 按消息 ID 读取消息 |
| `deleteByUserAndScope(String userId, String scopeId)` | `boolean` | 删除指定用户和 scope 的消息 |

### 4.6 ScopeUserMappingManager

scope 与 user 映射表管理器。

**包路径**：`com.openjiuwen.core.memory.manage.mem_model`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `add(String userId, String scopeId)` | `void` | 添加 scope-user 映射 |
| `deleteByScopeId(String scopeId)` | `boolean` | 删除某 scope 的全部映射 |
| `getByScopeId(String scopeId)` | `List<Map<String, Object>>` | 查询某 scope 关联的用户 |

### 4.7 SemanticStore

记忆模块对 `VectorStore` 的封装，内部自动完成文本 embedding。

**包路径**：`com.openjiuwen.core.memory.manage.mem_model`

**构造方法**
```java
SemanticStore(VectorStore vectorStore)
SemanticStore(VectorStore vectorStore, Embedding embedding)
```

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `initializeEmbeddingModel(Embedding embedding)` | `void` | 注入 embedding 模型 |
| `collectionExist(String collectionName)` | `boolean` | 判断集合是否存在 |
| `createCollection(String collectionName, int dimension, Map<String, Object> schema)` | `void` | 创建集合 |
| `addDocs(List<Map.Entry<String, String>> docs, String tableName)` | `boolean` | 批量写入文档并自动向量化 |
| `search(String query, String tableName, int topK)` | `List<Map.Entry<String, Double>>` | 语义检索 |
| `deleteDocs(List<String> ids, String tableName)` | `void` | 删除指定文档 |
| `deleteTable(String tableName)` | `void` | 删除向量集合 |
| `listCollectionNames()` | `List<String>` | 当前 Java 版 `VectorStore` 不支持，返回空列表 |
| `updateSchema(String collectionName, List<Map<String, Object>> newFields)` | `boolean` | Java 版不支持，返回 `false` |
| `getCollectionMetadata(String collectionName)` | `Map<String, Object>` | Java 版不支持，返回空 Map |
| `updateCollectionMetadata(String collectionName, Map<String, Object> metadata)` | `void` | Java 版不支持，仅记录 warning |

### 4.8 SqlDbStore

基于 JDBC 的 SQL CRUD 封装。

**包路径**：`com.openjiuwen.core.memory.manage.mem_model`

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getDbStore()` | `BaseDbStore<?>` | 获取底层数据库存储对象 |
| `getEngine()` | `Object` | 获取底层连接引擎 |
| `write(String table, Map<String, Object> data)` | `boolean` | 插入记录 |
| `get(String table, String recordId, List<String> columns)` | `Map<String, Object>` | 读取单条记录 |
| `getWithSort(String table, Map<String, Object> filters, String sortBy, String order, int limit)` | `List<Map<String, Object>>` | 条件 + 排序 + 分页查询 |
| `exist(String table, Map<String, Object> conditions)` | `boolean` | 判断记录是否存在 |
| `conditionGet(String table, Map<String, List<Object>> conditions, List<String> columns)` | `List<Map<String, Object>>` | 使用 `IN` 条件批量查询 |
| `update(String table, Map<String, Object> conditions, Map<String, Object> data)` | `boolean` | 条件更新 |
| `delete(String table, Map<String, Object> conditions)` | `boolean` | 条件删除 |

### 4.9 UserMemStore

基于 KV 的记忆主存储，负责维护记忆正文和各类 ID 索引。

**包路径**：`com.openjiuwen.core.memory.manage.mem_model`

**关键常量**

| 常量名 | 值 | 说明 |
|--------|----|------|
| `BYTE_NUM_PER_ID` | `24` | 单个 ID 的序列化字节数 |
| `IDS_STR` | `"ids"` | ID 列表后缀 |
| `USER_PROFILE_TOPIC_STR` | `"UPT"` | 片段记忆 topic 前缀 |
| `KEY_PREFIX_STR` | `"UMD"` | 用户记忆 KV 前缀 |

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `write(String userId, String scopeId, String memId, Map<String, Object> data)` | `boolean` | 写入记忆及其索引 |
| `update(String userId, String scopeId, String memId, Map<String, Object> data)` | `boolean` | 更新记忆内容 |
| `delete(String userId, String scopeId, String memId)` | `void` | 删除单条记忆 |
| `batchDelete(String userId, String scopeId, List<String> memIds)` | `void` | 批量删除记忆 |
| `get(String userId, String scopeId, String memId)` | `Map<String, Object>` | 读取单条记忆 |
| `batchGet(String userId, String scopeId, List<String> memIds)` | `List<Map<String, Object>>` | 批量读取记忆 |
| `getAll(String userId, String scopeId, String memType)` | `List<Map<String, Object>>` | 读取某类全部记忆 |
| `getByTopic(String userId, String scopeId, String topic)` | `List<Map<String, Object>>` | 按片段主题读取记忆 |
| `getInRange(String userId, String scopeId, int startIdx, int endIdx, String memType)` | `List<Map<String, Object>>` | 按范围分页读取记忆 |

---

## 5. 更新检测与记忆提取

### 5.1 CheckResult 与 MemoryStatus

更新检查结果枚举与记忆动作枚举。

**包路径**：`com.openjiuwen.core.memory.manage.update`

#### CheckResult

| 枚举值 | 值 | 说明 |
|--------|----|------|
| `REDUNDANT` | `"redundant"` | 与旧记忆重复 |
| `CONFLICTING` | `"conflicting"` | 与旧记忆冲突 |
| `NONE` | `"none"` | 无冲突，可直接保留 |

#### MemoryStatus

| 枚举值 | 值 | 说明 |
|--------|----|------|
| `ADD` | `"add"` | 新增记忆 |
| `DELETE` | `"delete"` | 删除旧记忆 |

### 5.2 MemCheckItem 与 MemoryActionItem

更新检查结果对象。

**包路径**：`com.openjiuwen.core.memory.manage.update`

#### MemCheckItem

| 字段 | 类型 | 说明 |
|------|------|------|
| `infoId` | `String` | 被检查的新记忆 ID |
| `infoText` | `String` | 被检查的新记忆内容 |
| `result` | `CheckResult` | 检查结果 |
| `relatedInfos` | `Map<String, String>` | 与之相关的旧记忆 |

#### MemoryActionItem

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | 记忆 ID |
| `content` | `String` | 记忆内容 |
| `status` | `MemoryStatus` | 后续动作：新增或删除 |

### 5.3 MemUpdateChecker

利用提示词和 LLM 对新旧记忆进行冗余/冲突分析。

**包路径**：`com.openjiuwen.core.memory.manage.update`

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `check(Map<String, String> newMemories, Map<String, String> oldMemories, Map.Entry<String, Model> baseChatModel)` | `List<MemoryActionItem>` | 使用默认重试次数进行检查 |
| `check(Map<String, String> newMemories, Map<String, String> oldMemories, Map.Entry<String, Model> baseChatModel, int retries)` | `List<MemoryActionItem>` | 执行带重试的冲突检测 |

### 5.4 ExtractMemoryParams

记忆抽取请求对象。

**包路径**：`com.openjiuwen.core.memory.process.extract`

| 字段 | 类型 | 说明 |
|------|------|------|
| `userId` | `String` | 用户 ID |
| `scopeId` | `String` | scope ID |
| `messages` | `List<BaseMessage>` | 当前轮消息 |
| `historyMessages` | `List<BaseMessage>` | 历史消息 |
| `baseChatModel` | `Map.Entry<String, Model>` | `(modelName, modelClient)` 对 |

### 5.5 Generator

记忆生成协调器，把分析结果转为 `BaseMemoryUnit` 列表。

**包路径**：`com.openjiuwen.core.memory.process.extract`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `genAllMemory(Map<String, Object> params)` | `Map<String, List<BaseMemoryUnit>>` | 统一生成变量、摘要和片段记忆单元 |

### 5.6 LongTermMemoryExtractor

从对话中抽取长期片段记忆，输出“主题 -> 记忆内容列表”。

**包路径**：`com.openjiuwen.core.memory.process.extract`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `extractLongTermMemory(ExtractMemoryParams params, String timestamp, int retries)` | `Map<String, List<String>>` | 使用指定重试次数抽取片段记忆 |
| `extractLongTermMemory(ExtractMemoryParams params, String timestamp)` | `Map<String, List<String>>` | 默认重试 3 次抽取片段记忆 |

### 5.7 MemoryAnalyzer

从消息中分析是否包含关键记忆、需要提取哪些变量，以及生成的摘要。

**包路径**：`com.openjiuwen.core.memory.process.extract`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `analyze(List<BaseMessage> messages, List<BaseMessage> historyMessages, Map.Entry<String, Model> baseChatModel, AgentMemoryConfig memoryConfig, int summaryMaxToken, int retries)` | `MemoryAnalyzerResult` | 完整分析接口 |
| `analyze(List<BaseMessage> messages, List<BaseMessage> historyMessages, Map.Entry<String, Model> baseChatModel, AgentMemoryConfig memoryConfig, int summaryMaxToken)` | `MemoryAnalyzerResult` | 默认重试 3 次 |

### 5.8 MemoryAnalyzerResult 与 VariableResult

分析结果对象。

**包路径**：`com.openjiuwen.core.memory.process.extract`

#### MemoryAnalyzerResult

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `hasKeyInformation` | `boolean` | `false` | 是否包含值得写入长期记忆的关键信息 |
| `variables` | `List<VariableResult>` | `[]` | 抽取出的变量 |
| `summary` | `String` | `""` | 生成的摘要 |

#### VariableResult

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `variableKey` | `String` | `""` | 变量名 |
| `variableValue` | `String` | `""` | 变量值 |

### 5.9 PromptApplier

记忆模块提示词加载与变量替换器，从类路径 `memory/prompt/*.md` 读取模板并缓存。

**包路径**：`com.openjiuwen.core.memory.prompt`

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getInstance()` | `PromptApplier` | 获取单例 |
| `apply(String filePrefix, Map<String, Object> variables)` | `String` | 渲染指定模板 |
| `clearCache(String filePrefix)` | `void` | 清除单个模板缓存 |
| `clearCache()` | `void` | 清空全部缓存 |
| `getTemplate(String filePrefix)` | `PromptTemplate` | 获取缓存中的模板对象 |

---

## 6. 迁移工具

### 6.1 KvMigrator

KV 存储迁移器，支持备份、回滚和版本更新。

**包路径**：`com.openjiuwen.core.memory.migration.migrator`

**常量**

| 常量名 | 值 | 说明 |
|--------|----|------|
| `KV_SCHEMA_VERSION` | `"MEMORY_MIGRATION_KV_SCHEMA_VERSION"` | KV schema 版本键 |
| `KV_ENTITY_KEY` | `"kv_global"` | KV 迁移实体键 |

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `tryMigrate(String entityKey, List<BaseOperation> operations)` | `boolean` | 执行 KV 迁移 |

### 6.2 MemoryMetaManager

管理 `memory_meta` 表中的 schema 版本记录。

**包路径**：`com.openjiuwen.core.memory.migration.migrator`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `add(String tableName, String schemaVersion)` | `void` | 记录某表当前 schema 版本 |
| `deleteByTableName(String tableName)` | `boolean` | 删除某表元数据 |
| `getByTableName(String tableName)` | `List<Map<String, Object>>` | 查询某表 schema 版本 |

### 6.3 SqlMigrator

基于 JDBC 的 SQL 表结构迁移器。

**包路径**：`com.openjiuwen.core.memory.migration.migrator`

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `tryMigrate(String entityKey, List<BaseOperation> operations)` | `boolean` | 执行 SQL 迁移，支持加列、改列名、改列类型 |

### 6.4 VectorMigrator

向量库迁移器。由于 Java 版 `VectorStore` API 能力有限，该类当前主要记录日志并执行 no-op。

**包路径**：`com.openjiuwen.core.memory.migration.migrator`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `tryMigrate(String entityKey, List<BaseOperation> operations)` | `boolean` | 尝试迁移向量集合，当前返回成功但不执行真实 schema 变更 |

### 6.5 BaseOperation 与 OperationMetadata

迁移操作抽象模型。

**包路径**：`com.openjiuwen.core.memory.migration.operation`

#### BaseOperation

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getSchemaVersion()` | `int` | 获取操作版本号 |
| `getDescription()` | `String` | 获取操作描述 |
| `getMetadata()` | `OperationMetadata` | 获取元数据 |

#### OperationMetadata

| 字段 | 类型 | 说明 |
|------|------|------|
| `schemaVersion` | `int` | schema 版本号 |
| `description` | `String` | 描述信息 |

### 6.6 OperationRegistry

迁移操作注册表，按 `entityKey` 维护一条递增版本的操作链。

**包路径**：`com.openjiuwen.core.memory.migration.operation`

**公共方法**

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `register(String entityKey, BaseOperation op)` | `void` | 注册操作，要求版本号严格递增 |
| `getOperations(String entityKey, int fromVersion, int toVersion)` | `List<BaseOperation>` | 按版本区间读取操作 |
| `getOperations(String entityKey)` | `List<BaseOperation>` | 读取某实体全部操作 |
| `getCurrentVersion(String entityKey)` | `int` | 获取实体当前最高版本 |
| `getAllEntities()` | `List<String>` | 获取全部实体键 |
| `getAllOperations()` | `Map<String, List<BaseOperation>>` | 获取全部注册的操作链 |
| `clear()` | `void` | 清空注册表 |
| `setOperations(Map<String, List<BaseOperation>> operations)` | `void` | 直接覆盖操作映射 |
