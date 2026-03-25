# operator模块单元测试报告

## 1. 转译后的Java测试文件

本次按 Python operator UT 的结构转译为以下 Java 测试：

- `src/test/java/com/openjiuwen/core/operator/OperatorBaseTest.java`
- `src/test/java/com/openjiuwen/core/operator/llm_call/LLMCallOperatorTest.java`
- `src/test/java/com/openjiuwen/core/operator/memory_call/MemoryCallOperatorTest.java`
- `src/test/java/com/openjiuwen/core/operator/tool_call/ToolCallOperatorTest.java`
- `src/test/java/com/openjiuwen/core/operator/OperatorTestSupport.java`

## 2. 对应的Python UT来源

对应转译来源如下：

- `tests/unit_tests/core/operator/test_base.py`
- `tests/unit_tests/core/operator/test_llm_call.py`
- `tests/unit_tests/core/operator/test_memory_call.py`
- `tests/unit_tests/core/operator/test_tool_call.py`

## 3. 覆盖的测试主题

### 3.1 OperatorBaseTest

覆盖：

- `TunableSpec` 全参数初始化
- `TunableSpec` 最小参数初始化
- `Operator` 抽象契约
- 默认 `stream not implemented`

### 3.2 LLMCallOperatorTest

覆盖：

- 默认 / 自定义 `operatorId`
- freeze 组合下的 tunable 暴露
- `system_prompt` / `user_prompt` 更新
- 参数更新回调
- `getState` / `loadState`
- `history` 注入
- `tools` 透传
- `messages` passthrough
- `stream` 基本行为
- operator context 清理

### 3.3 MemoryCallOperatorTest

覆盖：

- 默认 / 自定义 `operatorId`
- `enabled` / `max_retries` tunable 与 state
- retries clamp
- `invoke`
- kwargs 透传
- disabled 行为
- 未配置 memory 行为
- callback 优先级
- `stream`
- operator context 清理
- 未知参数容忍

### 3.4 ToolCallOperatorTest

覆盖：

- 默认 / 自定义 `operatorId`
- registry 存在 / 不存在时的 tunable 暴露
- `tool_description` 更新
- 非法参数容忍
- direct mode
- router mode
- `stream`
- operator context 清理
- 不支持 stream 的等价行为

## 4. 执行命令

本次实际执行了两轮命令。

### 4.1 operator模块专项测试

```powershell
mvn -q "-Djacoco.skip=true" "-Dtest=OperatorBaseTest,LLMCallOperatorTest,MemoryCallOperatorTest,ToolCallOperatorTest" test
```

### 4.2 联合回归验证

为确认本次对 `Session` 的扩展没有影响前两轮已转译模块，又执行了：

```powershell
mvn -q "-Djacoco.skip=true" "-Dtest=WorkflowTest,RetrievalCoreTest,KnowledgeBaseTest,OperatorBaseTest,LLMCallOperatorTest,MemoryCallOperatorTest,ToolCallOperatorTest" test
```

## 5. 测试结果

operator 专项测试结果为：

- `tests=20`
- `failures=0`
- `errors=0`
- `skipped=0`

统计来源：

- `OperatorBaseTest`：3
- `LLMCallOperatorTest`：5
- `MemoryCallOperatorTest`：6
- `ToolCallOperatorTest`：6

联合回归也通过，没有引入 workflow / retrieval 回归失败。

## 6. 测试转译过程中遇到的问题与处理

### 6.1 Python的异步测试需要改写为Java同步测试

Python 版使用：

- `pytest.mark.asyncio`
- `await`
- `async for`

Java 版当前 operator 采用同步接口，因此测试转成：

- 直接调用 `invoke`
- 用 `Iterator` 消费 `stream`

这是接口风格差异，不是功能缺失。

### 6.2 Python的hasattr(stream)判断需要改写

Python 测试里可通过删除对象属性或 `hasattr` 判断来验证“是否支持 stream”。

Java 中 `Tool` / `MemoryOperation` 的 `stream` 是类型层面的显式方法，因此本次转成等价断言：

- 默认实现抛 `UnsupportedOperationException`

### 6.3 首轮测试中的List.of(null)问题

首轮 Java UT 运行时出现了测试自身问题：

- 我在断言 operator context 清理历史时写了 `List.of("llm_call", null)` 这类代码
- Java 的 `List.of(...)` 不允许包含 `null`

处理方式：

- 将这些断言改为 `Arrays.asList("xxx", null)`

这不是模块实现 bug，而是 Java 集合字面量语义和 Python 列表语义不同导致的测试转译问题。

### 6.4 duck typing测试需要改写为接口测试

Python 可以直接把 mock 对象当作 memory/tool 使用。

Java 侧为了保持真实实现而不是反射占位，本次测试改成：

- memory 走 `MemoryOperation` / `MemoryInvoker`
- tool 走 `Tool` / `ToolExecutor` / `ToolRegistry`

这样测试验证的仍然是同一套行为，只是承载方式从鸭子类型改成显式接口。

## 7. 结论

当前 Java 版 operator 的核心回归测试已经建立并稳定通过，覆盖了：

- 抽象层
- LLM operator
- memory operator
- tool operator
- session operator context 清理

当前未纳入本轮 UT 的主要剩余风险是：

- `stream` 迭代器被调用方半路放弃时的上下文回收
- 未来若 operator 与更高层 optimizer 真实接线后的联动场景
