# AgentAsTool

在 openJiuwen Java 里，可以把一个 `Agent` 当作另一个 `Agent` 的可调用能力来使用。

它适合这种场景：

- 你已经有一个宿主 Agent，希望它像调用工具一样调用“翻译专家”“摘要专家”“审校专家”这类子 Agent
- 子能力本身更像一个独立 Agent，而不是简单函数或工作流
- 你需要一个轻量的主从协作结构，而不是先搭一个完整 group

如果你要的是明确的团队边界、成员关系和团队级运行入口，优先看 [AgentTeams](AgentTeams.md)。

## 核心思路

Java 版的 `Agent as Tool` 由两步组成：

1. 把子 Agent 实例注册到 `Runner.resourceMgr()`，让运行时能按 ID 找到它
2. 把子 Agent 的 `AgentCard` 加到宿主 Agent 的 `AbilityManager`，让模型把它当成可调用能力

其中：

- `AgentCard.description` 会直接影响模型什么时候选择这个子 Agent
- `AgentCard.inputParams` 会直接影响模型调用时传什么参数

所以对用户来说，最重要的不是先理解底层分发链路，而是把 `AgentCard` 写清楚。

## 接入步骤

### 第一步：定义子 Agent

子 Agent 是普通的 `BaseAgent` 子类。和 Python 版不同，Java 里最小实现通常要补齐这四个方法：

- `configure(Object config)`
- `getConfig()`
- `invoke(Object inputs, Session session)`
- `stream(Object inputs, Session session, List<StreamMode> streamModes)`

如果你的子 Agent 逻辑很简单，`stream(...)` 可以直接把 `invoke(...)` 的结果包成单元素迭代器返回。

### 第二步：定义子 Agent 的 `AgentCard`

`AgentCard` 至少建议写清楚：

- `id`
- `name`
- `description`
- `inputParams`

其中 `description` 和 `inputParams` 会被转换成模型可见的工具描述。写得越清晰，宿主 Agent 越容易在正确时机调用它。

### 第三步：注册子 Agent 到 `Runner.resourceMgr()`

只有把真实实例注册进资源管理器，运行时才能在 tool call 发生后找到它：

```java
Runner.resourceMgr().addAgent(translatorCard, () -> translatorAgent, null);
Runner.resourceMgr().addAgent(summarizerCard, () -> summarizerAgent, null);
```

### 第四步：把子 Agent 挂到宿主 Agent 的 `AbilityManager`

```java
hostAgent.getAbilityManager().add(translatorCard);
hostAgent.getAbilityManager().add(summarizerCard);
```

做完这一步后，子 Agent 会出现在宿主 Agent 发给模型的能力列表里，模型就可以像选工具一样选它们。

### 第五步：运行宿主 Agent

```java
Object result = Runner.runAgent(
        hostAgent,
        Map.of("query", "请把以下内容翻译成英文：人工智能正在改变世界。"),
        null,
        null
);
```

## 完整示例

下面的示例里：

- `TranslatorAgent` 负责翻译
- `SummarizerAgent` 负责摘要
- `hostAgent` 是宿主 `ReActAgent`
- 宿主会根据用户请求决定调用哪个子 Agent

运行前请先准备模型配置环境变量：

- `API_KEY`
- `API_BASE`
- `MODEL_NAME`

```java
package examples.agent_as_tool;

import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class AgentAsToolExample {

    private static final String HOST_AGENT_ID = "host_agent";
    private static final String TRANSLATOR_AGENT_ID = "translator_agent";
    private static final String SUMMARIZER_AGENT_ID = "summarizer_agent";

    private AgentAsToolExample() {
    }

    public static void main(String[] args) {
        AgentCard translatorCard = AgentCard.builder()
                .id(TRANSLATOR_AGENT_ID)
                .name(TRANSLATOR_AGENT_ID)
                .description("翻译专家。用户要求翻译、转成英文、转成日文时调用。")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "text", Map.of(
                                        "type", "string",
                                        "description", "需要翻译的原始文本"
                                ),
                                "target_lang", Map.of(
                                        "type", "string",
                                        "description", "目标语言，例如 English、Japanese"
                                )
                        ),
                        "required", List.of("text", "target_lang")
                ))
                .build();

        AgentCard summarizerCard = AgentCard.builder()
                .id(SUMMARIZER_AGENT_ID)
                .name(SUMMARIZER_AGENT_ID)
                .description("摘要专家。用户要求总结、提炼要点、生成摘要时调用。")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "text", Map.of(
                                        "type", "string",
                                        "description", "需要生成摘要的原始文本"
                                )
                        ),
                        "required", List.of("text")
                ))
                .build();

        TranslatorAgent translatorAgent = new TranslatorAgent(translatorCard);
        SummarizerAgent summarizerAgent = new SummarizerAgent(summarizerCard);

        Runner.resourceMgr().addAgent(translatorCard, () -> translatorAgent, null);
        Runner.resourceMgr().addAgent(summarizerCard, () -> summarizerAgent, null);

        AgentCard hostCard = AgentCard.builder()
                .id(HOST_AGENT_ID)
                .name(HOST_AGENT_ID)
                .description("主智能体，负责把任务分发给翻译专家或摘要专家。")
                .build();

        ReActAgent hostAgent = new ReActAgent(hostCard);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of(
                        "role", "system",
                        "content", """
                                你是一个任务分发助手。
                                当用户要求翻译时，调用 translator_agent。
                                当用户要求总结、提炼要点时，调用 summarizer_agent。
                                工具返回后，基于工具结果继续回答用户。
                                """
                )))
                .maxIterations(4)
                .build()
                .configureModelClient(
                        "openai",
                        System.getenv("API_KEY"),
                        System.getenv("API_BASE"),
                        System.getenv("MODEL_NAME"),
                        true
                );

        ModelRequestConfig requestConfig = config.getModelConfigObj();
        requestConfig.setTemperature(0.3);
        requestConfig.setMaxTokens(512);

        hostAgent.configure(config);
        hostAgent.getAbilityManager().add(translatorCard);
        hostAgent.getAbilityManager().add(summarizerCard);

        @SuppressWarnings("unchecked")
        Map<String, Object> translateResult = (Map<String, Object>) Runner.runAgent(
                hostAgent,
                Map.of(
                        "query", "请把以下内容翻译成英文：人工智能正在改变世界。",
                        "conversation_id", "agent_as_tool_translate_demo"
                ),
                null,
                null
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> summarizeResult = (Map<String, Object>) Runner.runAgent(
                hostAgent,
                Map.of(
                        "query", "请总结这段话：人工智能正在改变研发、客服、金融和制造业的工作方式，但落地时仍要关注成本、数据质量和合规风险。",
                        "conversation_id", "agent_as_tool_summary_demo"
                ),
                null,
                null
        );

        System.out.println("Translate result: " + translateResult);
        System.out.println("Summarize result: " + summarizeResult);

        Runner.release("agent_as_tool_translate_demo");
        Runner.release("agent_as_tool_summary_demo");
    }

    static final class TranslatorAgent extends BaseAgent {
        private Object config;

        TranslatorAgent(AgentCard card) {
            super(card);
        }

        @Override
        public BaseAgent configure(Object config) {
            this.config = config;
            return this;
        }

        @Override
        public Object getConfig() {
            return config;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            Map<String, Object> request = normalizeInputs(inputs);
            String text = String.valueOf(request.getOrDefault("text", ""));
            String targetLang = String.valueOf(request.getOrDefault("target_lang", "English"));
            return Map.of(
                    "translated", "[" + targetLang + "] " + text
            );
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return List.of(invoke(inputs, session)).iterator();
        }
    }

    static final class SummarizerAgent extends BaseAgent {
        private Object config;

        SummarizerAgent(AgentCard card) {
            super(card);
        }

        @Override
        public BaseAgent configure(Object config) {
            this.config = config;
            return this;
        }

        @Override
        public Object getConfig() {
            return config;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            Map<String, Object> request = normalizeInputs(inputs);
            String text = String.valueOf(request.getOrDefault("text", ""));
            String summary = text.length() > 40 ? text.substring(0, 40) + "..." : text;
            return Map.of(
                    "summary", "摘要：" + summary
            );
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return List.of(invoke(inputs, session)).iterator();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeInputs(Object inputs) {
        if (inputs instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of("text", String.valueOf(inputs));
    }
}
```

这个示例里，子 Agent 自身没有调用大模型，只是为了演示“宿主 Agent 如何像调工具一样调子 Agent”。在真实业务里，你可以把 `TranslatorAgent` 和 `SummarizerAgent` 替换成更复杂的 Agent 实现。

## 用户视角下应该怎么理解

从使用者角度看，这个能力只需要记住一句话：

> 子 Agent 负责实现能力，`AgentCard` 负责把这项能力描述给模型看。

也就是说：

- 你想让模型更容易选中某个子 Agent，就改好 `description`
- 你想让模型传对参数，就改好 `inputParams`
- 你想让运行时真的能执行，就把实例注册进 `Runner.resourceMgr()`

## 什么时候适合用 AgentAsTool

优先用这条路径的典型场景：

- 一个宿主 Agent 统一调度多个“专家能力”
- 专家之间不需要复杂广播、订阅、团队会话
- 你更关心“让模型选哪个子能力”，而不是“先建一个团队对象”

如果你的重点变成下面这些问题，就该切到 [AgentTeams](AgentTeams.md)：

- 团队边界怎么建
- 成员之间怎么通信
- 团队怎样作为一个整体被注册和运行

## Java 版注意事项

1. `AgentCard.id` 不能为空。`Runner.resourceMgr().addAgent(...)` 会校验资源 ID。
2. 只把 `AgentCard` 加进 `AbilityManager` 还不够。没有注册真实实例时，运行阶段找不到对应 Agent。
3. `AgentCard.id` 和资源注册 ID 要保持一致。运行时按这个 ID 去 `Runner.resourceMgr()` 里解析。
4. `Agent as Tool` 当前是可用能力，不是一个单独的高层封装类。`Tool` 和 `Workflow` 有更多现成辅助入口，子 Agent 这条路径仍然需要手动装配。
5. 如果你在同一个 JVM 里反复执行示例并重复注册相同 `agentId`，资源管理器会拒绝重复注册。这种情况下应复用唯一 ID，或先手动清理旧资源。

例如：

```java
Runner.resourceMgr().removeAgent(
        "translator_agent",
        null,
        com.openjiuwen.core.runner.base.TagMatchStrategy.ALL,
        true
);
Runner.resourceMgr().removeAgent(
        "summarizer_agent",
        null,
        com.openjiuwen.core.runner.base.TagMatchStrategy.ALL,
        true
);
```

## 延伸阅读

- [AgentTeams](AgentTeams.md)
- [预置协作模式](预置协作模式.md)
- [API 文档：BaseAgent](../API文档/com.openjiuwen.core/singleagent/BaseAgent.md)
- [API 文档：AbilityManager](../API文档/com.openjiuwen.core/singleagent/AbilityManager.md)
- [API 文档：Runner](../API文档/com.openjiuwen.core/runner/Runner.md)
- [API 文档：ResourceMgr](../API文档/com.openjiuwen.core/runner/resourcemanager/ResourceMgr.md)
