# L0-L2 层模块 Python vs Java 转译对比报告

> **版本**: v1.0  
> **检查日期**: 2026-03-07  
> **项目代号**: agent-core-2-java  

---

## 目录

- [1. 概述](#1-概述)
- [2. L0 基础层 (common)](#2-l0-基础层-common)
- [3. L1 基础设施层](#3-l1-基础设施层)
- [4. L2 核心引擎层](#4-l2-核心引擎层)
- [5. 问题与缺漏汇总](#5-问题与缺漏汇总)
- [6. 转译质量评估](#6-转译质量评估)

---

## 1. 概述

### 1.1 检查范围

| 层级 | 名称 | 模块 | 职责 |
|:----:|------|------|------|
| L0 | 基础层 | common | 日志、异常、常量、工具类 |
| L1 | 基础设施层 | foundation, sys_operation | LLM客户端、工具、存储、系统操作 |
| L2 | 核心引擎层 | context_engine, graph, session | 上下文、图引擎、会话管理 |

### 1.2 整体转译统计

| 模块 | Python 文件数 | Java 文件数 | 转译状态 |
|------|:------------:|:-----------:|:--------:|
| common | 17 | 29 | ✅ 完成 |
| foundation | ~25 | ~15 | ⚠️ 部分缺失 |
| sys_operation | 12 | 14 | ✅ 完成 |
| context_engine | 12 | 14 | ✅ 完成 |
| graph | 14 | 23 | ✅ 完成 |
| session | 43 | 63 | ✅ 完成 |

---

## 2. L0 基础层 (common)

### 2.1 constants 子模块

#### 文件映射

| Python 文件 | Java 文件 | 状态 |
|-------------|-----------|:----:|
| `constants/constant.py` | `constants/Constant.java` | ✅ |
| `constants/enums.py` — `ControllerType` | `constants/ControllerType.java` | ✅ |
| `constants/enums.py` — `TaskType` | `constants/TaskType.java` | ✅ |

#### API 对比

**Constant 常量类**

| Python API | Java API | 一致性 |
|------------|----------|:------:|
| `USER_FIELDS` | `Constant.USER_FIELDS` | ✅ |
| `QUERY` | `Constant.QUERY` | ✅ |
| `SYSTEM_FIELDS` | `Constant.SYSTEM_FIELDS` | ✅ |
| `INTERACTION` | `Constant.INTERACTION` | ✅ |
| `INTERACTIVE_INPUT` | `Constant.INTERACTIVE_INPUT` | ✅ |
| `INPUTS_KEY` | `Constant.INPUTS_KEY` | ✅ |
| `CONFIG_KEY` | `Constant.CONFIG_KEY` | ✅ |
| `END_FRAME` | `Constant.END_FRAME` | ✅ |
| `END_NODE_STREAM` | `Constant.END_NODE_STREAM` | ✅ |
| `LOOP_ID` | `Constant.LOOP_ID` | ✅ |
| `INDEX` | `Constant.INDEX` | ✅ |
| `FINISH_INDEX` | `Constant.FINISH_INDEX` | ✅ |
| `MAX_COLLECTION_SIZE` | `Constant.MAX_COLLECTION_SIZE` | ✅ |
| `MAX_EXPRESSION_LENGTH` | `Constant.MAX_EXPRESSION_LENGTH` | ✅ |
| `MAX_AST_DEPTH` | `Constant.MAX_AST_DEPTH` | ✅ |
| `NESTED_LOOP_DEPTH` | `Constant.NESTED_LOOP_DEPTH` | ✅ |

**ControllerType 枚举**

| Python | Java | 备注 |
|--------|------|------|
| `ControllerType.ReActController` | `ControllerType.REACT_CONTROLLER` | ⚠️ 命名风格差异 |
| `ControllerType.WorkflowController` | `ControllerType.WORKFLOW_CONTROLLER` | ⚠️ 命名风格差异 |
| `ControllerType.Undefined` | `ControllerType.UNDEFINED` | ⚠️ 命名风格差异 |

> **说明**: Java 版使用 SCREAMING_SNAKE_CASE，Python 版使用 CamelCase。Java 新增 `fromValue()` 方法用于字符串解析。

**TaskType 枚举**

| Python | Java | 状态 |
|--------|------|:----:|
| `TaskType.PLUGIN` | `TaskType.PLUGIN` | ✅ |
| `TaskType.WORKFLOW` | `TaskType.WORKFLOW` | ✅ |
| `TaskType.MCP` | `TaskType.MCP` | ✅ |
| `TaskType.UNDEFINED` | `TaskType.UNDEFINED` | ✅ |

---

### 2.2 exception 子模块

#### 文件映射

| Python 文件 | Java 文件 | 状态 |
|-------------|-----------|:----:|
| `errors.py` — `BaseError` | `BaseError.java` | ✅ |
| `errors.py` — `FrameworkError` | `FrameworkError.java` | ✅ |
| `errors.py` — `ConfigurationError` | `ConfigurationError.java` | ✅ |
| `errors.py` — `ValidationError` | `ValidationError.java` | ✅ |
| - | `AgentError.java` | ✅ Java 新增 |
| - | `ApplicationError.java` | ✅ Java 新增 |
| - | `ComponentError.java` | ✅ Java 新增 |
| - | `ContextError.java` | ✅ Java 新增 |
| - | `ExecutionError.java` | ✅ Java 新增 |
| - | `ExternalDataError.java` | ✅ Java 新增 |
| - | `ExternalServiceError.java` | ✅ Java 新增 |
| - | `GraphError.java` | ✅ Java 新增 |
| - | `GuardrailError.java` | ✅ Java 新增 |
| - | `ModelError.java` | ✅ Java 新增 |
| - | `RunnerError.java` | ✅ Java 新增 |
| - | `SessionError.java` | ✅ Java 新增 |
| - | `SysOperationError.java` | ✅ Java 新增 |
| - | `ToolchainError.java` | ✅ Java 新增 |
| - | `ToolError.java` | ✅ Java 新增 |
| - | `WorkflowError.java` | ✅ Java 新增 |
| `codes.py` — `StatusCode` | `StatusCode.java` | ✅ |
| `code_template.py` | `StatusCodeTemplate.java` | ✅ |
| `status_mapping.py` | `StatusMapping.java` | ✅ |
| - | `ErrorHelper.java` | ✅ Java 新增辅助类 |
| - | `ErrorMessageTemplate.java` | ✅ Java 新增 |
| - | `StatusCodeSpec.java` | ✅ Java 新增 |
| - | `Termination.java` | ✅ Java 新增 |
| - | `RunnerTermination.java` | ✅ Java 新增 |

#### API 对比

**BaseError 核心方法**

| Python API | Java API | 一致性 |
|------------|----------|:------:|
| `__init__(status, msg, details, cause, **kwargs)` | `BaseError(status, msg, details, cause, params)` | ✅ |
| `to_dict()` | `toMap()` | ⚠️ 方法名差异 |
| `to_json()` | `toJson()` | ⚠️ 方法名差异 |
| `_render_message()` | `renderMessage(status, params)` | ✅ |
| `status` 属性 | `getStatus()` | ✅ |
| `code` 属性 | `getCode()` | ✅ |
| `params` 属性 | `getParams()` | ✅ |
| `details` 属性 | `getDetails()` | ✅ |
| `recoverable` 类属性 | `isRecoverable()` | ✅ |
| `fatal` 类属性 | `isFatal()` | ✅ |

---

### 2.3 logging 子模块

#### 文件映射

| Python 文件 | Java 文件 | 状态 |
|-------------|-----------|:----:|
| `protocol.py` — `LoggerProtocol` | `LoggerProtocol.java` | ✅ |
| `manager.py` | `LogManager.java` | ✅ |
| `utils.py` | `LoggingUtils.java` | ✅ |
| `events.py` | `events/` (22个事件类) | ✅ 扩展 |
| - | `LazyLogger.java` | ✅ Java 新增 |
| - | `Loggers.java` | ✅ Java 新增 |
| `default/` | `defaults/` | ✅ |

#### API 对比

**LoggerProtocol 接口**

| Python 方法 | Java 方法 | 一致性 |
|-------------|-----------|:------:|
| `debug(msg, *args, **kwargs)` | `debug(String msg, Object... args)` | ✅ |
| `info(msg, *args, **kwargs)` | `info(String msg, Object... args)` | ✅ |
| `warning(msg, *args, **kwargs)` | `warning(String msg, Object... args)` | ✅ |
| `error(msg, *args, **kwargs)` | `error(String msg, Object... args)` | ✅ |
| `critical(msg, *args, **kwargs)` | `critical(String msg, Object... args)` | ✅ |
| `exception(msg, *args, **kwargs)` | `exception(String msg, Throwable t, Object... args)` | ⚠️ Java 显式传 Throwable |
| `log(level, msg, *args, **kwargs)` | `log(int level, String msg, Object... args)` | ✅ |
| `set_level(level)` | `setLevel(int level)` | ✅ |
| `get_config()` | `getConfig()` | ✅ |
| `reconfigure(config)` | `reconfigure(Map<String, Object> config)` | ✅ |
| `add_handler(handler)` | - | ❌ Java 未实现 |
| `remove_handler(handler)` | - | ❌ Java 未实现 |
| `add_filter(filter)` | - | ❌ Java 未实现 |
| `remove_filter(filter)` | - | ❌ Java 未实现 |

**Loggers 预定义实例 (Java 新增)**

```java
// Java 提供模块级 Logger 实例
Loggers.CONTEXT_ENGINE
Loggers.GRAPH
Loggers.SESSION
Loggers.LLM
Loggers.TOOL
Loggers.SYS_OPERATION
// ... 等
```

---

### 2.4 schema 子模块

#### 文件映射

| Python 文件 | Java 文件 | 状态 |
|-------------|-----------|:----:|
| `card.py` — `BaseCard` | `BaseCard.java` | ✅ |
| `param.py` — `Param` | `Param.java` | ✅ |
| - | `ParamType.java` | ✅ Java 新增 |

---

### 2.5 security 子模块

#### 文件映射

| Python 文件 | Java 文件 | 状态 |
|-------------|-----------|:----:|
| `exception_utils.py` | `ExceptionUtils.java` | ✅ |
| `json_utils.py` | `JsonUtils.java` | ✅ |
| `path_checker.py` | `PathChecker.java` | ✅ |
| `ssl_utils.py` | `SslUtils.java` | ✅ |
| `url_utils.py` | `UrlUtils.java` | ✅ |
| `user_config.py` | `UserConfig.java` | ✅ |

**完整映射，无缺漏**

---

### 2.6 utils 子模块

#### 文件映射

| Python 文件 | Java 文件 | 状态 |
|-------------|-----------|:----:|
| `dict_utils.py` | `DictUtils.java` | ✅ |
| `hash_util.py` | `HashUtil.java` | ✅ |
| `ip_utils.py` | `IpUtils.java` | ✅ |
| `message_utils.py` | - | ❌ 缺失 |
| `schema_utils.py` | `SchemaUtils.java` | ✅ |
| `singleton.py` | `SingletonSupport.java` | ✅ |

> **缺漏**: `message_utils.py` 未转译

---

## 3. L1 基础设施层

### 3.1 foundation/llm 子模块

#### 文件映射

| Python 文件 | Java 文件 | 状态 |
|-------------|-----------|:----:|
| `model.py` — `Model` | `Model.java` | ✅ |
| `inference_affinity_model.py` | - | ❌ 缺失 |
| `model_clients/base_model_client.py` | `model_clients/BaseModelClient.java` | ✅ |
| `model_clients/openai_model_client.py` | - | ❌ 缺失 |
| `model_clients/dashscope_model_client.py` | - | ❌ 缺失 |
| `model_clients/siliconflow_model_client.py` | - | ❌ 缺失 |
| `output_parsers/` | `output_parsers/` | ✅ |
| `schema/` | `schema/` | ✅ |

#### API 对比

**Model 类**

| Python API | Java API | 一致性 |
|------------|----------|:------:|
| `Model(model_client_config, model_config)` | `Model(ModelClientConfig, ModelRequestConfig)` | ✅ |
| `invoke(messages, ...)` | `invoke(messages, ...)` | ✅ |
| `stream(messages, ...)` | `stream(messages, ...)` | ✅ |
| `_CLIENT_TYPE_REGISTRY` 字典 | `FACTORY_REGISTRY` + SPI | ⚠️ 架构差异 |

**客户端注册机制差异**

```python
# Python: 硬编码字典
_CLIENT_TYPE_REGISTRY: Dict[str, Type[BaseModelClient]] = {
    "OpenAI": OpenAIModelClient,
    "DashScope": DashScopeModelClient,
    ...
}
```

```java
// Java: SPI + ServiceLoader
public interface ModelClientFactory {
    String providerName();
    BaseModelClient create(ModelRequestConfig, ModelClientConfig);
}
// 通过 META-INF/services 自动发现
```

> **说明**: Java 版使用 SPI 机制更灵活，但需要为每个客户端实现 Factory 类。

**缺漏汇总**

| 缺失文件 | 说明 | 优先级 |
|----------|------|:------:|
| `model_clients/OpenAIModelClient.java` | OpenAI 客户端实现 | P0 |
| `model_clients/DashScopeModelClient.java` | 阿里云 DashScope 客户端 | P0 |
| `model_clients/SiliconFlowModelClient.java` | SiliconFlow 客户端 | P1 |
| `InferenceAffinityModel.java` | 推理亲和模型 | P2 |

---

### 3.2 foundation/prompt 子模块

#### 文件映射

| Python 文件 | Java 文件 | 状态 |
|-------------|-----------|:----:|
| `template.py` — `PromptTemplate` | `PromptTemplate.java` | ✅ |
| `assemble/` | `assemble/` | ✅ |

**完整映射**

---

### 3.3 foundation/tool 子模块

#### 文件映射

| Python 文件 | Java 文件 | 状态 |
|-------------|-----------|:----:|
| `base.py` | - | ⚠️ 功能合并到 Tool.java |
| `tool.py` — `tool` 装饰器 | `Tool.java` | ⚠️ 设计差异 |
| `function/` | `function/` | ✅ |
| `mcp/` | `mcp/` | ✅ |
| `schema.py` | `schema/` | ✅ |
| `service_api/` | `service_api/` | ✅ |
| `utils/` | - | ❌ 缺失 |

#### API 对比

**Tool 基类**

| Python API | Java API | 一致性 |
|------------|----------|:------:|
| `async def invoke(inputs, **kwargs)` | `Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs)` | ✅ |
| `async def stream(inputs, **kwargs)` | `Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs)` | ⚠️ 异步转同步迭代器 |
| `card` 属性 | `getCard()` | ✅ |

**tool 装饰器**

```python
# Python: 装饰器模式
@tool
def my_function(...): ...

@tool(name="custom", description="...")
def another_function(...): ...
```

```java
// Java: 构建器模式
LocalFunction myFunction = LocalFunction.builder()
    .card(ToolCard.builder()
        .name("custom")
        .description("...")
        .build())
    .function(this::myFunctionImpl)
    .build();
```

> **说明**: 由于 Java 不支持装饰器语法，使用构建器模式替代。功能等价。

---

### 3.4 foundation/store 子模块

> **重要发现**: Java 版将 `store` 模块放置在 `com.openjiuwen.spi.store` 包下，采用 SPI 模式设计，而非 `foundation/store`。

#### 文件映射

| Python 文件 (foundation/store) | Java 文件 (spi/store) | 状态 |
|--------------------------------|----------------------|:----:|
| `base_vector_store.py` | `vector/BaseVectorStore.java` | ✅ |
| `base_kv_store.py` | `BaseKVStore.java` | ✅ |
| `base_db_store.py` | `BaseDbStore.java` | ✅ |
| `base_embedding.py` — `Embedding` | - | ❌ 缺失 |
| `query/base.py` — `QueryExpr` | `query/QueryExpr.java` | ✅ |
| `query/base.py` — `ComparisonExpr` | `query/ComparisonExpr.java` | ✅ |
| `query/base.py` — `RangeExpr` | `query/RangeExpr.java` | ✅ |
| `query/base.py` — `LogicalExpr` | `query/LogicalExpr.java` | ✅ |
| `query/base.py` — `ArithmeticExpr` | `query/ArithmeticExpr.java` | ✅ |
| `query/base.py` — `ArrayExpr` | `query/ArrayExpr.java` | ✅ |
| `query/base.py` — `JSONExpr` | `query/JSONExpr.java` | ✅ |
| `query/base.py` — `MatchExpr` | `query/MatchExpr.java` | ✅ |
| `query/base.py` — `NullExpr` | `query/NullExpr.java` | ✅ |
| `query/base.py` — `CustomExpr` | `query/CustomExpr.java` | ✅ |
| `query/registry.py` | `query/QueryLanguageRegistry.java` | ✅ |
| - | `query/QueryExpressions.java` | ✅ Java 新增 |
| - | `query/QueryLanguageDefinition.java` | ✅ Java 新增 |
| `vector/chroma_vector_store.py` | - | ❌ 具体实现缺失 |
| `vector/milvus_vector_store.py` | - | ❌ 具体实现缺失 |
| `kv/db_based_kv_store.py` | - | ❌ 具体实现缺失 |
| `kv/in_memory_kv_store.py` | - | ❌ 具体实现缺失 |
| `kv/shelve_store.py` | - | ❌ 具体实现缺失 |
| - | `object/BaseObjectStorageClient.java` | ✅ Java 新增 |
| - | `vector/CollectionSchema.java` | ✅ Java 新增 |
| - | `vector/FieldSchema.java` | ✅ Java 新增 |
| - | `vector/VectorDataType.java` | ✅ Java 新增 |
| - | `vector/VectorSearchResult.java` | ✅ Java 新增 |
| - | `KVStorePipeline.java` | ✅ Java 新增 |

#### API 对比

**BaseVectorStore**

| Python API | Java API | 一致性 |
|------------|----------|:------:|
| `async create_collection(name, schema, **kwargs)` | `createCollection(name, schema, kwargs)` | ✅ |
| `async delete_collection(name, **kwargs)` | `deleteCollection(name, kwargs)` | ✅ |
| `async collection_exists(name, **kwargs)` | `collectionExists(name, kwargs)` | ✅ |
| `async get_schema(name, **kwargs)` | `getSchema(name, kwargs)` | ✅ |
| `async add_docs(collection, docs, **kwargs)` | `addDocs(collection, docs, kwargs)` | ✅ |
| `async search(collection, vector, field, top_k, filters, **kwargs)` | `search(collection, vector, field, topK, filters, kwargs)` | ✅ |
| `async delete_docs_by_ids(collection, ids, **kwargs)` | `deleteDocsByIds(collection, ids, kwargs)` | ✅ |
| `async delete_docs_by_filters(collection, filters, **kwargs)` | `deleteDocsByFilters(collection, filters, kwargs)` | ✅ |

**BaseKVStore**

| Python API | Java API | 一致性 |
|------------|----------|:------:|
| `async set(key, value)` | `set(key, value)` | ✅ |
| `async exclusive_set(key, value, expiry)` | `exclusiveSet(key, value, expiry)` | ✅ |
| `async get(key)` | `get(key)` | ✅ |
| `async exists(key)` | `exists(key)` | ✅ |
| `async delete(key)` | `delete(key)` | ✅ |
| `async get_by_prefix(prefix)` | `getByPrefix(prefix)` | ✅ |
| `async delete_by_prefix(prefix, batch_size)` | `deleteByPrefix(prefix, batchSize)` | ✅ |
| `async mget(keys)` | `mget(keys)` | ✅ |
| `async batch_delete(keys, batch_size)` | `batchDelete(keys, batchSize)` | ✅ |
| - | `pipeline()` | ✅ Java 新增 |

**QueryExpr 表达式系统**

| Python | Java | 状态 |
|--------|------|:----:|
| `QueryExpr.__and__()` | `QueryExpr.and()` | ✅ |
| `QueryExpr.__or__()` | `QueryExpr.or()` | ✅ |
| `QueryExpr.__xor__()` | `QueryExpr.xor()` | ✅ |
| `QueryExpr.__invert__()` | `QueryExpr.not()` | ✅ |
| `QueryExpr.sanitize_str()` | `QueryExpr.sanitizeStr()` | ✅ |
| `QueryExpr.to_expr(database)` | `QueryExpr.toExpr(database)` | ✅ |

#### 架构差异

```python
# Python: 直接在 foundation/store 下
openjiuwen/core/foundation/store/
├── base_vector_store.py
├── base_kv_store.py
├── vector/
│   ├── chroma_vector_store.py
│   └── milvus_vector_store.py
└── kv/
    └── in_memory_kv_store.py
```

```java
// Java: SPI 模式，独立模块
com/openjiuwen/spi/store/
├── BaseVectorStore.java (抽象接口)
├── BaseKVStore.java (抽象接口)
├── vector/
│   ├── BaseVectorStore.java
│   ├── CollectionSchema.java
│   └── FieldSchema.java
├── query/
│   └── QueryExpr.java + 14个表达式类
└── object/
    └── BaseObjectStorageClient.java
```

> **说明**: Java 版采用 SPI 模式，将存储抽象为独立模块，更利于扩展和替换具体实现。

#### 缺漏汇总

| 缺失内容 | 说明 | 优先级 |
|----------|------|:------:|
| `Embedding` 接口 | 嵌入模型抽象基类 | P1 |
| `ChromaVectorStore` | Chroma 向量存储实现 | P1 |
| `MilvusVectorStore` | Milvus 向量存储实现 | P1 |
| `InMemoryKVStore` | 内存 KV 存储实现 | P1 |
| `DbBasedKVStore` | 数据库 KV 存储实现 | P2 |

---

### 3.5 sys_operation 子模块

#### 文件映射

| Python 文件 | Java 文件 | 状态 |
|-------------|-----------|:----:|
| `sys_operation.py` — `SysOperationCard` | `SysOperationCard.java` | ✅ |
| `sys_operation.py` — `SysOperation` | `SysOperation.java` | ✅ |
| `sys_operation.py` — `ToolIdProxy` | `ToolIdProxy.java` | ✅ |
| `base.py` — `BaseOperation` | `BaseOperation.java` | ✅ |
| `base.py` — `OperationMode` | `OperationMode.java` | ✅ |
| `fs.py` — `BaseFsOperation` | `BaseFsOperation.java` | ✅ |
| `code.py` — `BaseCodeOperation` | `BaseCodeOperation.java` | ✅ |
| `shell.py` — `BaseShellOperation` | `BaseShellOperation.java` | ✅ |
| `config.py` | `config/` | ✅ |
| `registry.py` | `registry/` | ✅ |
| `result/` | `result/` | ✅ |
| `sandbox/` | `sandbox/` | ✅ |
| `local/` | `local/` | ✅ |

#### API 对比

**SysOperationCard**

| Python API | Java API | 一致性 |
|------------|----------|:------:|
| `mode: OperationMode` | `getMode()` | ✅ |
| `work_config: LocalWorkConfig` | `getWorkConfig()` | ✅ |
| `gateway_config: SandboxGatewayConfig` | `getGatewayConfig()` | ✅ |
| `fs` 属性 → `ToolIdProxy` | `fs()` 方法 → `ToolIdProxy` | ⚠️ 属性变方法 |
| `shell` 属性 | `shell()` 方法 | ⚠️ 属性变方法 |
| `code` 属性 | `code()` 方法 | ⚠️ 属性变方法 |
| `generate_tool_id()` | `generateToolId()` | ✅ |

**BaseOperation**

| Python API | Java API | 一致性 |
|------------|----------|:------:|
| `list_tools()` | `listTools()` | ✅ |
| `_create_sys_operation_event(...)` | `createSysOperationEvent(...)` | ✅ |
| `_generate_tool_cards(method_names)` | `generateToolCards(methodNames)` | ✅ |

**完整映射，质量良好**

---

## 4. L2 核心引擎层

### 4.1 context_engine 子模块

#### 文件映射

| Python 文件 | Java 文件 | 状态 |
|-------------|-----------|:----:|
| `context_engine.py` — `ContextEngine` | `ContextEngine.java` | ✅ |
| `base.py` — `ModelContext` | `ModelContext.java` | ✅ |
| - | `ContextStats.java` | ✅ Java 新增 |
| - | `ContextWindow.java` | ✅ Java 新增 |
| `context/context.py` | `context/SessionModelContext.java` | ✅ |
| `context/context_utils.py` | `context/ContextUtils.java` | ✅ |
| `context/kv_cache_manager.py` | `context/KVCacheManager.java` | ✅ |
| `context/message_buffer.py` | `context/ContextMessageBuffer.java` | ✅ |
| - | `context/OffloadMessageBuffer.java` | ✅ Java 扩展 |
| `token/base.py` | `token/TokenCounter.java` | ✅ |
| `token/tiktoken_counter.py` | `token/SimpleTokenCounter.java` | ⚠️ 简化实现 |
| `processor/base.py` | `processor/ContextProcessor.java` | ✅ |
| `processor/compressor/` | `processor/compressor/` | ✅ |
| `processor/offloader/` | `processor/offloader/` | ✅ |
| `schema/config.py` | `schema/ContextEngineConfig.java` | ✅ |
| `schema/messages.py` | `schema/OffloadMessages.java` | ✅ |

#### API 对比

**ContextEngine 核心方法**

| Python API | Java API | 一致性 |
|------------|----------|:------:|
| `async create_context(context_id, session, ...)` | `createContext(contextId, session, ...)` | ✅ |
| `get_context(context_id, session_id)` | `getContext(contextId, sessionId)` | ✅ |
| `clear_context(context_id, session_id)` | `clearContext(contextId, sessionId)` | ✅ |
| `register_processor(name, cls)` | `registerProcessorFactory(name, factory)` | ⚠️ 工厂模式 |

**Token 计数器**

```python
# Python: 使用 tiktoken 库
class TiktokenCounter(TokenCounter):
    def count(self, text: str, model: str) -> int:
        import tiktoken
        encoding = tiktoken.encoding_for_model(model)
        return len(encoding.encode(text))
```

```java
// Java: 简化实现
public class SimpleTokenCounter implements TokenCounter {
    @Override
    public int count(String text, String model) {
        // 简单估算: ~4 字符 = 1 token
        return text.length() / 4;
    }
}
```

> **问题**: Java 版使用简单估算，精度不足。建议集成 [JTokkit](https://github.com/knuddelsgmbh/jtokkit) 库。

---

### 4.2 graph 子模块

#### 文件映射

| Python 文件 | Java 文件 | 状态 |
|-------------|-----------|:----:|
| `graph.py` — `PregelGraph` | `PregelGraph.java` | ✅ |
| `base.py` — `Graph` | `Graph.java` | ✅ |
| `base.py` — `Router` | `Router.java` | ✅ |
| `atomic_node.py` | `AtomicNode.java` | ✅ |
| `vertex.py` | `Vertex.java` | ✅ |
| `executable.py` | `Executable.java` | ✅ |
| - | `CompiledGraph.java` | ✅ Java 新增 |
| - | `ExecutableGraph.java` | ✅ Java 新增 |
| - | `GraphNodeState.java` | ✅ Java 新增 |
| `pregel/base.py` | `pregel/*.java` (20个文件) | ✅ 扩展 |
| `pregel/builder.py` | `pregel/PregelBuilder.java` | ✅ |
| `pregel/engine.py` | `pregel/Pregel.java` | ✅ |
| `pregel/router.py` | `pregel/*.Router.java` | ✅ |
| `pregel/channels.py` | `pregel/*Channel.java` | ✅ |
| `store/` | `store/` | ✅ |
| `stream_actor/` | `stream_actor/` | ✅ |
| `visualization/` | `visualization/` | ✅ |

#### API 对比

**Graph 基类**

| Python API | Java API | 一致性 |
|------------|----------|:------:|
| `add_node(node_id, node, wait_for_all=False)` | `addNode(nodeId, node, waitForAll)` | ✅ |
| `add_edge(source_node_id, target_node_id)` | `addEdge(sourceNodeId, targetNodeId)` | ✅ |
| `add_conditional_edges(source_node_id, router)` | `addConditionalEdges(sourceNodeId, router)` | ✅ |
| `start_node(node_id)` | `startNode(nodeId)` | ✅ |
| `end_node(node_id)` | `endNode(nodeId)` | ✅ |
| `compile(session)` | `compile(session)` | ✅ |
| `get_nodes()` | `getNodes()` | ✅ |

**Pregel 核心常量**

| Python | Java | 状态 |
|--------|------|:----:|
| `START` | `PregelConstants.START` | ✅ |
| `END` | `PregelConstants.END` | ✅ |
| `MAX_RECURSIVE_LIMIT` | `PregelConstants.MAX_RECURSIVE_LIMIT` | ✅ |

**Java 版扩展**

```java
// Java 新增的路由类型
public interface IRouter { ... }
public class ConditionalRouter implements IRouter { ... }
public class StaticRouter implements IRouter { ... }
public class BarrierRouter implements IRouter { ... }

// Java 新增的消息类型
public class BarrierMessage extends Message { ... }
public class TriggerMessage extends Message { ... }
```

**完整映射，且有良好扩展**

---

### 4.3 session 子模块

#### 文件映射总览

| 子模块 | Python 文件数 | Java 文件数 | 状态 |
|--------|:------------:|:-----------:|:----:|
| state | 3 | 11 | ✅ 扩展 |
| stream | 4 | 9 | ✅ 扩展 |
| callback | 2 | 3 | ✅ |
| checkpointer | 4 | 5 | ✅ |
| interaction | 3 | 6 | ✅ 扩展 |
| tracer | 6 | 11 | ✅ 扩展 |
| config | 4 | 4+ | ✅ |
| store | 3 | 3 | ✅ |

#### state 子模块

| Python | Java | 状态 |
|--------|------|:----:|
| `ReadableStateLike` | `ReadableState.java` | ✅ |
| `RecoverableStateLike` | `RecoverableState.java` | ✅ |
| `StateLike` | `StateLike.java` | ✅ |
| `CommitStateLike` | `CommitStateLike.java` | ✅ |
| `InMemoryStateLike` | `InMemoryStateLike.java` | ✅ |
| `InMemoryCommitState` | `InMemoryCommitState.java` | ✅ |
| `State` | `State.java` | ✅ |
| `WorkflowStateCollection` | `WorkflowStateCollection.java` | ✅ |
| `CommitState` | `WorkflowCommitState.java` | ✅ |
| `InMemoryState` | `InMemoryState.java` | ✅ |
| `StateCollection` | `AgentStateCollection.java` | ✅ |
| - | `InMemoryStateLike.java` | ✅ Java 新增 |

#### stream 子模块

| Python | Java | 状态 |
|--------|------|:----:|
| `BaseStreamMode` | `StreamMode.java` | ✅ |
| `StreamSchema` | `StreamSchema.java` | ✅ |
| `OutputSchema` | `OutputSchema.java` | ✅ |
| `TraceSchema` | `TraceSchema.java` | ✅ |
| `CustomSchema` | `CustomSchema.java` | ✅ |
| `AsyncStreamQueue` (asyncio.Queue) | `AsyncStreamQueue.java` (BlockingQueue) | ⚠️ 异步转同步 |
| `StreamEmitter` | `StreamEmitter.java` | ✅ |
| `StreamWriter` | `StreamWriter.java` | ✅ |
| `StreamWriterManager` | `StreamWriterManager.java` | ✅ |

#### Session API 对比

**BaseSession 抽象类**

| Python API | Java API | 一致性 |
|------------|----------|:------:|
| `config() -> Config` | `config()` | ✅ |
| `state() -> State` | `state()` | ✅ |
| `tracer()` | `tracer()` | ✅ |
| `stream_writer_manager()` | `streamWriterManager()` | ✅ |
| `callback_manager()` | `callbackManager()` | ✅ |
| `session_id()` | `getSessionId()` | ⚠️ 方法风格差异 |
| `checkpointer()` | `checkpointer()` | ✅ |
| `actor_manager()` | - | ❌ 缺失 |
| `async close()` | - | ❌ 缺失 |

**Session 接口**

```python
# Python: 多模块 Session
# - openjiuwen.core.workflow.Session
# - openjiuwen.core.single_agent.Session
# - openjiuwen.core.multi_agent.Session
class Session:
    """DEPRECATED - use module-specific Session"""
```

```java
// Java: 最小化接口
public interface Session {
    String getSessionId();
    Object getState(String key);
    void updateState(Map<String, Object> state);
}
```

> **说明**: Java 版 Session 接口是简化版，仅包含 ContextEngine 需要的方法。完整 Session 实现在各业务模块中。

---

## 5. 问题与缺漏汇总

### 5.1 严重缺漏 (P0)

| 模块 | 缺失内容 | 影响 |
|------|----------|------|
| `foundation/llm/model_clients` | OpenAI、DashScope 等客户端实现 | 无法调用实际的 LLM 服务 |

### 5.2 中等缺漏 (P1)

| 模块 | 缺失内容 | 影响 |
|------|----------|------|
| `spi/store` | `Embedding` 嵌入模型接口 | 无法进行向量化操作 |
| `spi/store` | Chroma/Milvus 具体实现 | 只有抽象接口，无实际存储能力 |
| `spi/store` | InMemoryKVStore/DbBasedKVStore 实现 | KV 存储无具体实现 |
| `foundation/llm` | `InferenceAffinityModel` | 推理亲和功能缺失 |
| `foundation/tool/utils` | 工具辅助类 | Schema 提取功能受限 |
| `common/utils` | `message_utils.py` | 消息处理工具缺失 |
| `session` | `actor_manager()`, `close()` | Actor 管理和资源清理功能缺失 |

### 5.3 轻微问题 (P2)

| 模块 | 问题 | 建议 |
|------|------|------|
| `context/token` | SimpleTokenCounter 估算精度低 | 集成 JTokkit |
| `common/constants` | 枚举命名风格差异 | 保持 Java 惯例即可 |
| `session/Session` | 接口简化 | 业务模块自行扩展 |
| `store` 模块位置 | 从 `foundation/store` 移至 `spi/store` | SPI 设计更灵活，无需调整 |

### 5.4 设计差异 (可接受)

| Python 设计 | Java 设计 | 说明 |
|-------------|-----------|------|
| `@tool` 装饰器 | 构建器模式 | 功能等价 |
| `async/await` | 同步方法 + Virtual Thread | 性能等价 |
| `_CLIENT_TYPE_REGISTRY` 字典 | SPI + ServiceLoader | Java 更灵活 |
| `protocol.py` (Protocol) | interface | 功能等价 |
| `@dataclass` | Java record / Lombok | 功能等价 |

---

## 6. 转译质量评估

### 6.1 总体评分

| 维度 | 评分 | 说明 |
|------|:----:|------|
| **功能完整性** | 75/100 | foundation/store 完全缺失 |
| **API 一致性** | 85/100 | 命名风格符合 Java 惯例 |
| **代码质量** | 90/100 | 结构清晰，注释完善 |
| **架构设计** | 95/100 | SPI、工厂模式运用得当 |
| **测试覆盖** | 待评估 | 需检查测试代码 |

### 6.2 各模块评分

| 模块 | 完整度 | API一致性 | 备注 |
|------|:------:|:---------:|------|
| common/constants | 100% | 95% | 命名风格差异 |
| common/exception | 100% | 90% | Java 新增细化异常类 |
| common/logging | 95% | 90% | handler/filter 未实现 |
| common/schema | 100% | 100% | 完美映射 |
| common/security | 100% | 100% | 完美映射 |
| common/utils | 85% | 95% | message_utils 缺失 |
| foundation/llm | 60% | 85% | 客户端实现缺失 |
| foundation/prompt | 100% | 100% | 完美映射 |
| foundation/tool | 90% | 85% | utils 目录缺失 |
| foundation/store | 0% | - | 完全缺失 |
| sys_operation | 100% | 95% | 质量优秀 |
| context_engine | 95% | 90% | Token 计数简化 |
| graph | 100% | 95% | 良好扩展 |
| session | 95% | 90% | 少量 API 缺失 |

### 6.3 改进建议

1. **优先完成 foundation/store 模块** - 这是 L1 层的关键基础设施
2. **实现 LLM 客户端** - OpenAI、DashScope 等是 Agent 框架的核心
3. **集成 JTokkit** - 提升 Token 计数精度
4. **补充 message_utils** - 完善工具类
5. **添加 session.close()** - 资源管理完整性

---

## 附录

### A. 文件对照表

详见各模块章节的文件映射表。

### B. API 命名对照规则

| Python 风格 | Java 风格 | 示例 |
|-------------|-----------|------|
| `snake_case` 方法 | `camelCase` 方法 | `create_context` → `createContext` |
| `SCREAMING_SNAKE_CASE` 常量 | `SCREAMING_SNAKE_CASE` 常量 | `MAX_SIZE` → `MAX_SIZE` |
| `CamelCase` 类 | `PascalCase` 类 | `BaseError` → `BaseError` |
| `@property` | `getXxx()` 方法 | `session_id` → `getSessionId()` |
| `async def` | 同步 def | Virtual Thread 自动处理 |

### C. 异步代码映射规则

| Python | Java |
|--------|------|
| `async def method()` | `public ReturnType method()` |
| `await coroutine()` | 直接调用 |
| `asyncio.gather(*tasks)` | `CompletableFuture.allOf()` |
| `asyncio.Queue` | `BlockingQueue` |
| `async with` | `try-with-resources` |

---

*报告生成时间: 2026-03-07*  
*检查工具: 手动代码审查*