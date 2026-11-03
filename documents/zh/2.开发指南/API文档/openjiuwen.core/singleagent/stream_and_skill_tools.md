# openjiuwen.core.singleagent 单代理流式事件与 Skill 工具

Java 对应包：`com.openjiuwen.core.singleagent`

0.1.14 中单代理相关 Java 包名统一为 `com.openjiuwen.core.singleagent`。旧包名 `com.openjiuwen.core.single_agent` 不再作为主 API 包使用，直接引用旧包名的 Java 调用方需要同步调整 import。

## 包名迁移

旧写法：

```java
import com.openjiuwen.core.single_agent.agents.ReActAgent;
import com.openjiuwen.core.single_agent.agents.ReActAgentConfig;
import com.openjiuwen.core.single_agent.schema.AgentCard;
```

新写法：

```java
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
```

受影响的主要子包包括：

- `com.openjiuwen.core.singleagent.agents`
- `com.openjiuwen.core.singleagent.rail`
- `com.openjiuwen.core.singleagent.schema`
- `com.openjiuwen.core.singleagent.interrupt`
- `com.openjiuwen.core.singleagent.skills`
- `com.openjiuwen.core.singleagent.legacy`

## ReActAgent 流式输出事件

`ReActAgent.stream(...)` 通过 `OutputSchema` 输出流式事件。工具调用相关事件新增如下：

### tool_call

当模型请求调用工具且工具即将执行时，输出 `tool_call`。

```json
{
  "type": "tool_call",
  "index": 0,
  "payload": {
    "tool_call_id": "call_abc",
    "tool_name": "read_file",
    "arguments": "{\"path\":\"README.md\"}"
  }
}
```

字段说明：

- `tool_call_id`：工具调用 ID。优先使用模型返回的 `ToolCall.id`；如果为空，运行时会补齐一个 ID。
- `tool_name`：工具名称。
- `arguments`：工具调用参数字符串。

### tool_result

工具执行完成后，输出 `tool_result`。

成功示例：

```json
{
  "type": "tool_result",
  "index": 1,
  "payload": {
    "tool_call_id": "call_abc",
    "tool_name": "read_file",
    "status": "completed",
    "result": "file content"
  }
}
```

失败示例：

```json
{
  "type": "tool_result",
  "index": 1,
  "payload": {
    "tool_call_id": "call_abc",
    "tool_name": "read_file",
    "status": "error",
    "error": "Ability execution error: file not found"
  }
}
```

消费方应使用 `tool_call_id` 配对 `tool_call` 和 `tool_result`。多工具场景下，不建议依赖不同工具之间的完成顺序。

### 其他流式事件

模型内容流式输出仍使用既有事件，例如：

- `llm_output`
- `llm_reasoning`
- `llm_usage`
- `answer`

严格校验事件类型的调用方需要允许新增的 `tool_call` 和 `tool_result`。

## Skill 级工具

`BaseAgent` 新增 Skill 级工具注册和激活能力。工具可以绑定到具体 Skill，只有当前 session 激活该 Skill 后，相关工具才会出现在有效工具列表中。

核心 API：

- `registerSkillTools(SkillToolBinding binding)`
- `registerSkillTools(List<SkillToolBinding> bindings)`
- `activateSkill(String skillName, AgentSessionApi session)`
- `deactivateSkill(String skillName, AgentSessionApi session)`
- `getActiveSkillNames(AgentSessionApi session)`
- `listEffectiveToolInfo(AgentSessionApi session)`

示例：

```java
SkillToolBinding binding = SkillToolBinding.builder()
        .skillName("repo-reader")
        .tools(List.of(readFileTool))
        .build();

agent.registerSkillTools(binding).toCompletableFuture().join();
agent.activateSkill("repo-reader", session);
```

激活 Skill 后，`ReActAgent` 会把全局工具和当前 Skill 工具合并后传给模型。如果工具名称重复，会抛出异常，调用方需要保证全局工具与 Skill 工具名称不冲突。
