# com.openjiuwen.core.controller.modules.IntentRecognizer

## class IntentRecognizer

```java
public class IntentRecognizer
```

`IntentRecognizer` 负责把 `InputEvent` 中的用户输入转换为 `Intent` 列表。它依赖会话上下文、当前任务清单和模型工具调用结果，为控制器提供后续的任务创建、暂停、补充和修改指令。

## 依赖

| 成员 | 类型 | 说明 |
|---|---|---|
| `config` | `ControllerConfig` | 提供 `intentLlmId`、`intentConfidenceThreshold` 与允许的意图类型列表。 |
| `taskManager` | `TaskManager` | 用于读取当前任务列表并拼接到用户提示中。 |
| `contextEngine` | `ContextEngine` | 负责获取当前会话对应的 `ModelContext`。 |
| `abilityManager` | `Object` | 构造时注入的能力管理器；当前公开方法不直接使用它。 |
| `modelProvider` | `ModelProvider` | 按模型 ID 和会话延迟获取 `Model` 实例。 |
| `systemMessage` | `SystemMessage` | 固定系统提示消息。 |

## 相关接口

### `public interface ModelProvider`

`ModelProvider` 是一个函数式接口，只暴露一个 `getModel(String modelId, AgentSessionApi session)` 方法，用来为意图识别阶段提供模型实例，避免 `IntentRecognizer` 直接依赖更上层的运行器实现。

## 主要方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `recognize(Event event, AgentSessionApi session)` | `List<Intent>` | 校验事件类型、读取 `ModelContext`、调用模型和工具链，最终返回识别出的意图列表。 |

## 行为说明

1. `recognize(...)` 只接受 `InputEvent`；如果传入其他 `Event` 实现，会直接抛出 `IllegalArgumentException`。
2. 输入数据只允许包含一条 `DataFrame.TextDataFrame`。如果出现文件输入、JSON 输入或多段文本输入，会抛出 `AGENT_CONTROLLER_RUNTIME_ERROR`。
3. 方法会把当前任务列表整理成文本提示，再与用户输入一起封装为 `UserMessage` 写入 `ModelContext`。
4. 首轮模型调用使用 `IntentToolkits.getOpenaiToolSchemas(config.getIntentTypeList())` 限定可用工具；后续补轮调用则放开为全部工具定义。
5. 每个工具调用的参数都会先解析为 `Map<String, Object>`，再交给 `IntentToolkits.dispatch(...)` 生成 `Intent` 与工具消息；工具消息同样会回写到 `ModelContext`。

## 约束与异常

- 读取上下文时固定最多取最近 `50` 条消息。
- 如果 `ContextEngine` 中不存在当前会话的 `ModelContext`，方法会抛出运行时错误并要求调用方先创建上下文。
- 模型调用失败、工具参数解析失败和输入格式违规都会被包装成 `StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR`。
