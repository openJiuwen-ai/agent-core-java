# SingleAgent NeedFix

## 对照范围

- Java 文档：`docs/single_agent_unit_test_report.md`
- Java 文档：`docs/single_agent_transform.md`
- Python 实现：
  - `openjiuwen/core/single_agent/base.py`
  - `openjiuwen/core/single_agent/ability_manager.py`
  - `openjiuwen/core/single_agent/agent_callback_manager.py`
  - `openjiuwen/core/single_agent/rail/base.py`
  - `openjiuwen/core/single_agent/agents/react_agent.py`
  - `openjiuwen/core/single_agent/agents/react_agent_evolve.py`
  - `openjiuwen/core/single_agent/schema/agent_card.py`

## 发现的问题

### 1. `AgentCallbackManager.unregisterRail()` 只移除工具，不移除 rail 回调

- Python 行为：`unregister_rail()` 会同时注销 rail 注册的 callbacks 和 tools。
- Java 现状：`unregisterRail()` 仅删除 rail 附带工具，回调仍留在 `CallbackFramework` 中。
- 影响：调用 `unregisterRail()` 后，hook 仍会继续触发，行为与 Python 不一致。
- 处理：已修复。新增回调包装映射与 rail 注册记录，支持按 rail 精确注销。

### 2. `AgentRail.buildCallback()` 缺少可访问性处理

- Python 行为：rail 方法可正常被装饰器/运行时机制调用。
- Java 现状：反射调用 rail hook 时未显式 `setAccessible(true)`。
- 影响：私有/匿名/非公开宿主类场景下可能抛出 `IllegalAccessException`。
- 处理：已修复。反射执行前显式开放访问。

### 3. `ReActAgent` / `ReActAgentEvolve` 的 `AFTER_INVOKE` 上下文恢复错误

- Python 行为：`ctx.lifecycle(BEFORE_INVOKE, AFTER_INVOKE)` 会在 `AFTER_INVOKE` 前恢复原始 `InvokeInputs`。
- Java 现状：`invoke()` 内部把 `ctx.inputs` 改成了 `ModelCallInputs`/`ToolCallInputs`，`finally` 中未恢复。
- 影响：`AFTER_INVOKE` rail 读到的不是 `InvokeInputs`，会破坏与 Python 一致的生命周期语义。
- 处理：已修复。`finally` 中恢复 invoke 级别原始输入后再触发 `AFTER_INVOKE`。

### 4. `ControllerAgent` 存在 3 处行为偏差

- `configure(Map)`：
  - Python 行为：dict 配置会 merge 到现有 `ControllerConfig`。
  - Java 现状：仅打印 warning，不实际合并。
  - 处理：已修复，改为基于属性描述符的安全 merge，并复用 setter 校验。

- `releaseSession()`：
  - Python 行为：若存在 `event_queue`，先 `unsubscribe()`，再 `Runner.release()`。
  - Java 现状：只调用 `Runner.release()`。
  - 处理：已修复，先尝试从 `EventQueue` 退订当前 `agentId/sessionId`。

- 错误透传：
  - Python 行为：已有 `BaseError` 原样抛出，只有普通异常才包装成 controller runtime error。
  - Java 现状：所有 `RuntimeException` 都被二次包装。
  - 处理：已修复，优先透传 `BaseError`。

### 5. `AbilityManager` 存在实现缺漏和一处测试基线错误

- `AgentCard -> ToolInfo` 转换：
  - Python `AgentCard.tool_info()` 直接返回 `input_params`。
  - Java 现状：人为包装为 `{type, properties, required}`，与当前 Python 源码不一致。
  - 处理：已修复，统一走 card 自身的 `toolInfo()`。

- `listToolInfo()` 缺少过滤与 MCP 枚举：
  - Python 行为：支持按 name 过滤，并在存在 MCP server 时拉取对应 MCP tool infos。
  - Java 现状：仅返回本地 tool/workflow/agent，缺少过滤与 MCP tool 发现。
  - 处理：已修复，新增 `listToolInfo(List<String>, String)`，并补上 MCP tool info 拉取与缓存。

- MCP server 名称执行路径：
  - Python 行为：命中 MCP server 名称时显式报 `MCP tool execution not yet implemented`。
  - Java 现状：落入 fallback，报错信息不准确。
  - 处理：已修复，补上显式分支。

- 子 workflow / 子 agent 的会话传播：
  - Python 行为：子调用沿用当前 session。
  - Java 现状：`Runner.runWorkflow()` / `Runner.runAgent()` 传了 `null` session。
  - 影响：至少会丢失 session id；在可适配场景下也无法沿用会话链路。
  - 处理：已修复为优先传 `AgentSessionApi`，否则传 `sessionId`。说明：由于 Java 当前 `Session` 抽象比 Python 更窄，无法完全复制 Python 的 rich session 语义，只能做当前框架允许的最优传播。

### 6. `ReActAgent` / `ReActAgentEvolve` 缺少 reload tool 接入

- Python 行为：`enable_reload=true` 时，会把 `context.reloader_tool()` 注册进 `ability_manager`，必要时也注册到 `Runner.resource_mgr`。
- Java 现状：`initContext()` 只创建 context，没有接入 reload tool。
- 影响：开启 reload 配置后，模型上下文虽然生成了 reload 能力，但 agent 无法真正把它暴露给工具调用链。
- 处理：已修复，补上 ability 注册和 `Runner.resourceMgr()` 注册逻辑。

### 7. `RemoteSkillUtil` 是未完成 stub

- Python 行为：可递归列举 GitHub tree、定位 `SKILL.md` 所在目录、下载整个 skill 目录并写入本地。
- Java 现状：`uploadSkillFromGitHub()` 只有日志和空列表返回，没有实际搜索/下载/写入逻辑。
- 影响：远程 skill 注册功能名义存在，但无法工作。
- 处理：已修复，补上 GitHub tree 递归遍历、skill 目录识别、文件下载和本地写入逻辑。

## 单元测试中的错误与缺漏

### 原有测试错误

- `ControllerAgentTest.testConfigureWithMapLogsWarning`
  - 原断言把“仅 warning 不 merge”当成正确行为。
  - 已修正为校验 `Map -> ControllerConfig` merge 结果。

- `AbilityManagerSupplementTest.testListToolInfoAgentWithInputParams`
  - 原断言把 `properties` 包装结构当成正确行为。
  - 已修正为校验与 Python `AgentCard.tool_info()` 一致的原始 `input_params`。

### 原有测试缺漏

- 未验证 `unregisterRail()` 会移除回调。
- 未验证私有/非公开 rail 子类的反射调用。
- 未验证 `AFTER_INVOKE` 回调能看到恢复后的 `InvokeInputs`。
- 未验证 `enable_reload=true` 时 reload tool 会注册进 `AbilityManager`。
- 未验证 `ControllerAgent` 对 `BaseError` 的透传。
- 未验证 MCP server 名称执行路径的显式报错。
- 未验证 `listToolInfo()` 的 names 过滤能力。
- 仍未覆盖 `RemoteSkillUtil` 的 HTTP 路径。原因：现有测试基建没有为 GitHub API 提供本地 mock server；该项目前属于残余测试风险，而不是实现残缺。

## 已完成修复

- `src/main/java/com/openjiuwen/core/singleagent/AgentCallbackManager.java`
- `src/main/java/com/openjiuwen/core/singleagent/ControllerAgent.java`
- `src/main/java/com/openjiuwen/core/singleagent/AbilityManager.java`
- `src/main/java/com/openjiuwen/core/singleagent/rail/AgentRail.java`
- `src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java`
- `src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgentEvolve.java`
- `src/main/java/com/openjiuwen/core/singleagent/skills/RemoteSkillUtil.java`

- `src/test/java/com/openjiuwen/core/singleagent/AgentCallbackManagerTest.java`
- `src/test/java/com/openjiuwen/core/singleagent/BaseAgentTest.java`
- `src/test/java/com/openjiuwen/core/singleagent/ControllerAgentTest.java`
- `src/test/java/com/openjiuwen/core/singleagent/AbilityManagerTest.java`
- `src/test/java/com/openjiuwen/core/singleagent/AbilityManagerSupplementTest.java`
- `src/test/java/com/openjiuwen/core/singleagent/rail/AgentRailTest.java`
- `src/test/java/com/openjiuwen/core/singleagent/agents/ReActAgentTest.java`
- `src/test/java/com/openjiuwen/core/singleagent/agents/ReActAgentEvolveTest.java`

## 回归测试

执行命令：

```bash
mvn -q -Dtest="AbilityExecutionErrorTest,AbilityManagerTest,AbilityManagerSupplementTest,AgentCallbackManagerTest,BaseAgentTest,ControllerAgentTest,DataClassCoverageTest,ReActAgentConfigTest,ReActAgentTest,ReActAgentEvolveTest,AgentRailTest,AgentCallbackContextTest,AgentCallbackEventTest,RailDataClassesTest,RailExecutorTest,SchemaTest,SkillManagerTest,SkillUtilTest" test
```

结果：

- `tests=263`
- `failures=0`
- `errors=0`
- `skipped=0`
