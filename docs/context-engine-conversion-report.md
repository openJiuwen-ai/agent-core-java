# context_engine 模块 Python → Java 转换报告

## 1. 概述

本报告总结了 `context_engine` 模块从 Python 到 Java 的完整转换工作。该模块负责对话上下文管理，包含上下文窗口构建、Token 统计、消息压缩、消息卸载等核心功能。

### 转换范围

| 指标 | 数值 |
|------|------|
| Python 源文件数 | 22 个 |
| Java 实现文件数 | 23 个（含跨模块依赖 Session.java） |
| Java 测试文件数 | 10 个 |
| 测试用例总数 | 66 个 |
| 编译状态 | ✅ BUILD SUCCESS |
| 测试状态 | ✅ 66/66 全部通过 |

---

## 2. 文件映射关系

### 2.1 Schema 包 (`com.openjiuwen.core.context.schema`)

| Python 源文件 | Java 文件 | 说明 |
|--------------|-----------|------|
| `schema/config.py` | `ContextEngineConfig.java` | 引擎配置，使用 @Data @Builder |
| `schema/offload_mixin.py` | `OffloadMixin.java` | 卸载消息接口 |
| `schema/offload_messages.py` | `OffloadMessages.java` | 卸载消息工厂类，内含 4 个静态内部类 |

### 2.2 Token 包 (`com.openjiuwen.core.context.token`)

| Python 源文件 | Java 文件 | 说明 |
|--------------|-----------|------|
| `token/token_counter.py` | `TokenCounter.java` | 抽象基类 |
| `token/simple_token_counter.py` | `SimpleTokenCounter.java` | 启发式 Token 计数实现 |

### 2.3 上下文基础类 (`com.openjiuwen.core.context`)

| Python 源文件 | Java 文件 | 说明 |
|--------------|-----------|------|
| `schema/context_stats.py` | `ContextStats.java` | 上下文统计信息 |
| `schema/context_window.py` | `ContextWindow.java` | 上下文窗口 |
| `context/model_context.py` | `ModelContext.java` | 抽象基类 |
| `context_engine.py` | `ContextEngine.java` | 主引擎类 |

### 2.4 Context 子包 (`com.openjiuwen.core.context.context`)

| Python 源文件 | Java 文件 | 说明 |
|--------------|-----------|------|
| `context/context_utils.py` | `ContextUtils.java` | 静态工具类 |
| `context/message_buffer.py` | `ContextMessageBuffer.java` | 消息缓冲区 |
| `context/offload_message_buffer.py` | `OffloadMessageBuffer.java` | 卸载消息缓冲区 |
| `context/kv_cache_manager.py` | `KVCacheManager.java` | KV 缓存管理 |
| `context/session_model_context.py` | `SessionModelContext.java` | ModelContext 主实现 |

### 2.5 Processor 包 (`com.openjiuwen.core.context.processor`)

| Python 源文件 | Java 文件 | 说明 |
|--------------|-----------|------|
| `processor/base.py` (ContextEvent) | `ContextEvent.java` | 处理器事件 |
| `processor/base.py` (ContextProcessor) | `ContextProcessor.java` | 处理器抽象基类 |

### 2.6 Compressor 子包 (`com.openjiuwen.core.context.processor.compressor`)

| Python 源文件 | Java 文件 | 说明 |
|--------------|-----------|------|
| `processor/compressor/current_round_compressor.py` | `CurrentRoundCompressorConfig.java` | 配置类 |
| | `CurrentRoundCompressor.java` | 当轮压缩器实现 |
| `processor/compressor/dialogue_compressor.py` | `DialogueCompressorConfig.java` | 配置类 |
| | `DialogueCompressor.java` | 对话压缩器实现 |
| `processor/compressor/round_level_compressor.py` | `RoundLevelCompressorConfig.java` | 配置类 |
| | `RoundLevelCompressor.java` | 层级轮次压缩器实现 |

### 2.7 Offloader 子包 (`com.openjiuwen.core.context.processor.offloader`)

| Python 源文件 | Java 文件 | 说明 |
|--------------|-----------|------|
| `processor/offloader/message_offloader.py` | `MessageOffloaderConfig.java` | 配置类 |
| | `MessageOffloader.java` | 消息卸载器实现 |
| `processor/offloader/message_summary_offloader.py` | `MessageSummaryOffloaderConfig.java` | 配置类 |
| | `MessageSummaryOffloader.java` | 摘要卸载器实现 |

### 2.8 跨模块依赖

| Java 文件 | 包路径 | 说明 |
|-----------|--------|------|
| `Session.java` | `com.openjiuwen.core.session` | 最小接口，供 ContextEngine 状态持久化使用 |

---

## 3. 测试覆盖

| 测试文件 | 测试数 | 状态 |
|----------|--------|------|
| `ContextEngineConfigTest.java` | 2 | ✅ |
| `ContextWindowTest.java` | 3 | ✅ |
| `ContextStatsTest.java` | 2 | ✅ |
| `SimpleTokenCounterTest.java` | 7 | ✅ |
| `ContextUtilsTest.java` | 6 | ✅ |
| `ContextMessageBufferTest.java` | 8 | ✅ |
| `OffloadMessageBufferTest.java` | 4 | ✅ |
| `OffloadMessagesTest.java` | 6 | ✅ |
| `SessionModelContextTest.java` | 16 | ✅ |
| `ContextEngineTest.java` | 12 | ✅ |
| **总计** | **66** | **全部通过** |

---

## 4. 关键设计决策

### 4.1 Python → Java 模式映射

| Python 模式 | Java 实现 | 说明 |
|-------------|-----------|------|
| Pydantic `BaseModel` | Lombok `@Data` + `@Builder` | 自动生成 getter/setter/builder |
| `async def` | 同步方法 | Java 不使用协程，直接同步调用 |
| Python `dict` | `Map<String, Object>` | 动态字典转为泛型 Map |
| Python `Optional[int]` | `Optional<Integer>` / `Integer` | 根据语义选择合适包装 |
| Python `ABCMeta` / `abstractmethod` | Java `abstract class` | 抽象类模式 |
| Python Mixin | Java `interface` | `OffloadMixin` 转为接口 |
| Python `dataclass` 继承 | `@SuperBuilder` 注解 | 支持 Builder 模式的继承链 |
| Python `__init_subclass__` | 静态注册方法 | `ContextEngine.registerProcessor()` |
| Python `isinstance()` | Java `instanceof` 模式匹配 | 如 `msg instanceof OffloadMixin om` |
| Pydantic `model_validator` | 构造后校验逻辑 | 在 Builder 自定义 `build()` 中实现 |

### 4.2 异步转同步

Python 原代码大量使用 `async/await`，Java 转换全部改为同步方法：

```python
# Python
async def process(self, context: ModelContext) -> ProcessResult:
    ...
```

```java
// Java
public ProcessResult process(ModelContext context) {
    ...
}
```

### 4.3 配置类分离

Python 中 Processor 的配置通过 Pydantic 字段直接定义在类中。Java 采用 **配置类 + 实现类** 分离模式：

- `CurrentRoundCompressorConfig` + `CurrentRoundCompressor`
- `DialogueCompressorConfig` + `DialogueCompressor`
- `RoundLevelCompressorConfig` + `RoundLevelCompressor`
- `MessageOffloaderConfig` + `MessageOffloader`
- `MessageSummaryOffloaderConfig` + `MessageSummaryOffloader`

### 4.4 异常处理

Python 原代码使用自定义异常 + 错误码，Java 使用已有的 `ErrorHelper.buildError()` 模式：

```java
throw ErrorHelper.buildError(StatusCodes.CONTEXT_MESSAGE_PROCESS_ERROR, 
    "Processor execution failed", e.getMessage());
```

涉及的状态码：
- `CONTEXT_MESSAGE_PROCESS_ERROR (153000)` — 处理器执行失败
- `CONTEXT_EXECUTION_ERROR (153001)` — 引擎执行错误
- `CONTEXT_MESSAGE_INVALID (153003)` — 消息格式无效

### 4.5 日志体系

使用项目已有的 `Loggers.CONTEXT_ENGINE` 进行日志记录，对应 Python 中的 `logging.getLogger("context_engine")`。

---

## 5. 跨模块依赖处理

### 5.1 已存在的依赖（直接使用）

| 依赖类 | 所在包 | 用途 |
|--------|--------|------|
| `BaseMessage` 系列 | `com.openjiuwen.core.foundation.llm.schema` | 消息基类 |
| `UserMessage` / `AssistantMessage` / `SystemMessage` / `ToolMessage` | 同上 | 具体消息类型 |
| `ToolCall` | 同上 | 工具调用结构 |
| `Model` | `com.openjiuwen.core.foundation.llm` | LLM 模型接口 |
| `BaseOutputParser` / `JsonOutputParser` | `com.openjiuwen.core.foundation.llm.output_parser` | 输出解析 |
| `ToolCard` | `com.openjiuwen.core.foundation.tool.schema` | 工具卡片定义 |
| `ErrorHelper` / `StatusCodes` | `com.openjiuwen.core.common.exception` | 异常体系 |
| `Loggers` | `com.openjiuwen.core.common.logging` | 日志体系 |

### 5.2 新增的跨模块依赖

| 文件 | 说明 |
|------|------|
| `Session.java` | 最小接口定义，包含 `getSessionId()`、`getState(key)`、`updateState(stateMap)` 三个方法。用于 `ContextEngine` 的上下文状态持久化到 Session 存储。当 Session 模块完整实现后，此接口应由该模块的实际实现替换。|

---

## 6. 包结构

```
com.openjiuwen.core.context
├── ContextEngine.java              # 主引擎
├── ContextStats.java               # 统计信息
├── ContextWindow.java              # 上下文窗口
├── ModelContext.java                # 抽象基类
├── context/
│   ├── ContextMessageBuffer.java   # 消息缓冲区
│   ├── ContextUtils.java           # 工具方法
│   ├── KVCacheManager.java         # KV缓存管理
│   ├── OffloadMessageBuffer.java   # 卸载消息缓冲区
│   └── SessionModelContext.java    # ModelContext 实现
├── processor/
│   ├── ContextEvent.java           # 处理器事件
│   ├── ContextProcessor.java       # 处理器基类
│   ├── compressor/
│   │   ├── CurrentRoundCompressor.java
│   │   ├── CurrentRoundCompressorConfig.java
│   │   ├── DialogueCompressor.java
│   │   ├── DialogueCompressorConfig.java
│   │   ├── RoundLevelCompressor.java
│   │   └── RoundLevelCompressorConfig.java
│   └── offloader/
│       ├── MessageOffloader.java
│       ├── MessageOffloaderConfig.java
│       ├── MessageSummaryOffloader.java
│       └── MessageSummaryOffloaderConfig.java
├── schema/
│   ├── ContextEngineConfig.java
│   ├── OffloadMessages.java
│   └── OffloadMixin.java
└── token/
    ├── SimpleTokenCounter.java
    └── TokenCounter.java

com.openjiuwen.core.session
└── Session.java                    # 跨模块依赖接口
```

---

## 7. 已知限制与后续工作

### 7.1 已知限制

1. **KVCacheManager**: `release()` 方法为占位实现，依赖 `InferenceAffinityModel` 接口（当前不存在）。当推理亲和性模型实现后需要补充。

2. **SessionModelContext.loadState() / saveState()**: 状态持久化依赖 `Session` 接口实现。当前 `Session.java` 为最小接口定义，需要 Session 模块完整实现后对接。

3. **压缩器 LLM 调用**: `CurrentRoundCompressor`、`DialogueCompressor`、`MessageSummaryOffloader` 中的 LLM 调用依赖 `Model.invoke()` 的实际实现。单元测试中已通过 Mock 验证逻辑正确性。

4. **Token 计数精度**: `SimpleTokenCounter` 使用启发式方法（≈4字符/token），与 Python 版本一致。生产环境建议接入真实 Tokenizer。

### 7.2 后续工作

- [ ] 实现 `Session` 模块完整功能，替换最小接口
- [ ] 实现 `InferenceAffinityModel` 接口，补充 KV Cache 释放逻辑
- [ ] 接入真实 Token 计数器（如 tiktoken 的 Java 版本）
- [ ] 补充集成测试，验证与 LLM 的端到端交互
- [ ] 性能测试：大上下文窗口下的压缩/卸载效率

---

## 8. 构建与验证

```bash
# 编译
cd agent-core-java/agent-core-java
mvn compile

# 运行 context_engine 模块测试
mvn test -Dtest="com.openjiuwen.core.context.**"

# 预期输出
# Tests run: 66, Failures: 0, Errors: 0, Skipped: 0
```

---

*报告生成时间：2026 年*  
*转换基准：Python context_engine 模块 v0.1.7*
