本章节演示了如何基于openJiuwen构建一个用于天气查询的`ReActAgent`应用，该应用支持通过ReAct规划模式引导大模型生成插件调用命令，进而结合插件执行结果生成最终答案。通过示例，你将会了解到如下信息：

- 如何创建提示词。
- 如何使用插件模块。
- 如何创建和执行`ReActAgent`。

# 应用设计流程

`ReActAgent`是一种遵循ReAct（Reasoning + Action）规划模式的Agent，通过 "思考（Thought）→ 行动（Action）→ 观察（Observe）"的循环迭代完成用户任务。

1. 思考：`ReActController`调用LLM进行任务规划，解析 LLM 输出里是否包含工具执行指令。
2. 行动：根据思考中 LLM 的输出，分两种情况进行操作：
    - 有工具执行指令：调工具，选择并调用合适的工具（如检索、数据库、第三方API、代码执行等）执行具体的操作，本用例中是调用一个根据时间和地点查询天气的工具；
    - 无工具执行指令：把LLM输出作为最终答案。
3. 观察：`ReActController`把工具返回的Observation追加到对话历史，再调用LLM进行下一次任务规划。

`ReActAgent`能根据工具执行结果观察与反馈，不断调整策略，优化推理路径，直至达成任务目标或获得最终答案。其强大的多轮推理与自我修正能力，使ReActAgent具备动态决策能力且能够灵活应对环境变化，适用于需要复杂推理和策略调整的多样化任务场景。

  <div align="center">
    <img src="../images/ReActAgent.png" alt="ReActAgent" width="70%">
  </div>

# 前提条件

JDK 版本应为 JDK 17。Maven 版本建议 3.9+。

# 添加 Maven 依赖

在 `pom.xml` 中添加 openJiuwen Java SDK 依赖：

```xml
<dependency>
    <groupId>com.openjiuwen</groupId>
    <artifactId>openjiuwen-core</artifactId>
    <version>0.1.14</version>
</dependency>
```

# 创建提示词模板

创建系统提示词模板，用于设定`ReActAgent`的整体行为和角色定位。以下系统提示词不仅定义了人设、任务目标，同时提供了当前日期信息，还给定了约束限制，帮助`ReActAgent`在与用户交互时正确理解当前时间完成任务目标。示例代码如下：

```java
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class PromptHelper {
    public static String buildCurrentDate() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    public static List<Map<String, String>> createPromptTemplate() {
        String systemPrompt = "你是一个AI助手，在适当的时候调用合适的工具，帮助我完成任务！\n"  // 人设&任务目标
                + "今天的日期为：" + buildCurrentDate() + "\n"                       // 当前日期
                + "注意：1. 如果用户请求中未指定具体时间，则默认为今天。";                // 约束限制
        return List.of(Map.of("role", "system", "content", systemPrompt));
    }
}
```

# 创建插件对象及其描述信息

本示例创建了天气查询插件，并定义了其输入参数的结构和要求。首先通过`RestfulApi`接口将天气查询服务封装成可以被框架使用的工具类。示例代码如下：

> **注意**
> 本地测试请求服务（http 开头），可以通过配置相关的系统属性关闭SSL校验。但禁用SSL会跳过证书验证，可能遭遇数据篡改、中间人攻击，导致敏感信息泄露，仅允许测试环境临时使用，生产环境务必启用SSL校验保障安全。

```java
import com.openjiuwen.core.foundation.tool.service_api.RestfulApi;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApiCard;

System.setProperty("SSRF_PROTECT_ENABLED", "false");  // 关闭IP校验仅用于本地调试，生产环境请务必打开
System.setProperty("RESTFUL_SSL_VERIFY", "false");    // 关闭SSL校验仅用于本地调试，生产环境请务必打开

public class ToolFactory {
    public static RestfulApi createTool() {
        RestfulApiCard weatherCard = new RestfulApiCard();
        weatherCard.setName("WeatherReporter");
        weatherCard.setDescription("天气查询插件");
        weatherCard.setUrl("your weather search api url");  // 天气查询服务部署地址
        weatherCard.setMethod("GET");
        weatherCard.setHeaders(Map.of());
        weatherCard.setInputParams(Map.of(
            "type", "object",
            "properties", Map.of(
                "location", Map.of("type", "string", "description", "天气查询的地点，必须为英文"),
                "date", Map.of("type", "string", "description", "天气查询的时间，格式为YYYY-MM-DD")
            ),
            "required", List.of("location", "date")
        ));
        return new RestfulApi(weatherCard);
    }
}
```

# 创建ReActAgent

在创建Agent之前，需要准备好`ModelRequestConfig`和`ModelClientConfig`配置。然后使用`Runner`和`AgentCard`初始化`ReActAgent`，启动`Runner`后可执行同步或流式调用。示例代码如下：

```java
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.agents.ReActAgent;

// 准备模型配置
ModelClientConfig clientConfig = new ModelClientConfig();
clientConfig.setClientProvider(MODEL_PROVIDER);
clientConfig.setApiKey(API_KEY);
clientConfig.setApiBase(API_BASE);
clientConfig.setTimeout(30);

ModelRequestConfig requestConfig = new ModelRequestConfig();
requestConfig.setModelName(MODEL_NAME);
requestConfig.setTemperature(0.8);
requestConfig.setTopP(0.9);

// 创建ReActAgent配置
ReActAgentConfig reactConfig = new ReActAgentConfig();
reactConfig.setModelConfigObj(requestConfig);
reactConfig.setModelClientConfig(clientConfig);
reactConfig.setPromptTemplate(PromptHelper.createPromptTemplate());

// 创建Agent卡片
AgentCard agentCard = new AgentCard();
agentCard.setId("react_agent_123");
agentCard.setDescription("AI助手");

// 构建并配置ReActAgent
ReActAgent reactAgent = new ReActAgent(agentCard).configure(reactConfig);

// 注册工具
RestfulApi tool = ToolFactory.createTool();
Runner.resourceMgr().addTool(tool);
reactAgent.getAbilityManager().add(tool.getCard());

// 启动Runner并执行
Runner.start();
Object result = reactAgent.invoke(
        Map.of("query", "查询杭州的天气"),
        (Session) null
).toCompletableFuture().join();
System.out.println("ReActAgent 最终输出结果：" + result);
```

# 动态模型请求头

如果同一个 `ReActAgent` 需要按当前用户、租户或会话获取模型凭证，可以注册 `ModelRequestHeadersRail`。Provider 接收本次模型调用的 `AgentCallbackContext`，并统一返回 `CompletionStage<Map<String, String>>`；实现方可以同步完成，也可以真正异步解析凭证。

`ReActAgent` 会把调用输入中非空的 `run_context` 对象原样放入 `AgentCallbackContext.extra["run_context"]`。下面使用 SDK 的 `RunContext` 类型，并把本次调用的 token 放入其 `extra` Map；Provider 从当前 callback context 解析该值，不依赖全局可变状态：

```java
import com.openjiuwen.core.singleagent.rail.ModelRequestHeadersProvider;
import com.openjiuwen.core.singleagent.rail.ModelRequestHeadersRail;
import com.openjiuwen.core.singleagent.rail.RunContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

ModelRequestHeadersProvider provider = context -> {
    Object rawRunContext = context.getExtra().get("run_context");
    if (!(rawRunContext instanceof RunContext runContext)) {
        return CompletableFuture.failedFuture(
                new IllegalStateException("run context is unavailable"));
    }
    Object rawToken = runContext.getExtra().get("model_access_token");
    if (!(rawToken instanceof String token) || token.isBlank()) {
        return CompletableFuture.failedFuture(
                new IllegalStateException("model access token is unavailable"));
    }
    return CompletableFuture.completedFuture(Map.of(
            "Authorization", "Bearer " + token,
            "X-Tenant-Id", "tenant-a"
    ));
};

ModelRequestHeadersRail headersRail = new ModelRequestHeadersRail(provider);
reactAgent.registerRail(headersRail).toCompletableFuture().join();
```

调用方按下面的输入结构提供 token。`run_context` 的值是 `RunContext`，业务字段放在其 `extra` 中；`query` 和 `run_context` 一起传给同一次 `invoke`：

```java
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.rail.RunContext;

import java.util.Map;
import java.util.Objects;

String requestToken = Objects.requireNonNull(
        System.getenv("OPENJIUWEN_REQUEST_TOKEN"),
        "OPENJIUWEN_REQUEST_TOKEN is required");

RunContext runContext = new RunContext();
runContext.setExtra(Map.of("model_access_token", requestToken));

Map<String, Object> invokeInput = Map.of(
        "query", "查询杭州的天气",
        "run_context", runContext
);

Object resultWithDynamicHeaders = reactAgent.invoke(
        invokeInput,
        (Session) null
).toCompletableFuture().join();
```

`Authorization` 的 value 是完整请求头值。上例显式拼出 `Bearer `；SDK 不会自动添加该前缀，也可以按网关要求返回 `Basic ...` 或自定义认证方案。

凭证来自远程服务时，可以注入应用自己的异步服务。Provider 原样返回异步链，SDK 会在进入模型调用前等待它完成：

```java
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelRequestHeadersProvider;
import com.openjiuwen.core.singleagent.rail.ModelRequestHeadersRail;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public final class DynamicHeadersRegistration {
    private final TokenService tokenService;

    public DynamicHeadersRegistration(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public void register(ReActAgent agent) {
        ModelRequestHeadersProvider provider = context -> tokenService.resolve(context)
                .thenApply(token -> {
                    if (token == null || token.isBlank()) {
                        throw new IllegalStateException("model access token is unavailable");
                    }
                    return Map.of("Authorization", "Bearer " + token);
                });
        agent.registerRail(new ModelRequestHeadersRail(provider))
                .toCompletableFuture()
                .join();
    }

    public interface TokenService {
        CompletionStage<String> resolve(AgentCallbackContext context);
    }
}
```

运行时与安全规则如下：

- 构造 Rail 时 Provider 为 `null` 会立即抛出 `NullPointerException`；Provider 返回 `null` stage、同步抛出异常、stage 异常完成、返回 `null` / 空 Map，或者返回空白 `Authorization`，都会通过 `AbortError` 终止本次模型调用（fail-closed），不会自动使用静态 `apiKey`。如需备用凭证，Provider 必须自行解析并显式返回备用 `Authorization`。
- 多个 Rail 的不同 header 会合并；同名 header 按大小写不敏感方式匹配，由后执行的 Rail 覆盖。Rail 按 priority 数值从大到小执行；相同 priority 时按注册顺序执行，先注册的先执行。因此需要覆盖前值的 Rail 应设置更低的 priority，或者在相同 priority 下后注册。
- Agent 级模型重试的每个 attempt 都会重新执行 `BEFORE_MODEL_CALL` 并再次调用 Provider，便于刷新短期 token。单个 attempt 进入 OpenAI HTTP 重试后使用已复制的同一份请求头快照，不会在每次 HTTP 重试时再次调用 Provider。
- headers 属于当前 `ModelCallInputs`，进入 `ModelInvokeOptions` 时会被消费并清空；即使后续 before-model Rail 失败、调用异常或取消，也会在 exception / after callback 之前清理，避免旧凭证跨 retry attempt 残留。不同 Agent 调用各自持有请求级 Map，不修改共享模型配置，可并发隔离。
- 不要记录 Provider 结果或完整 headers。正式请求头不会进入序列化、模型请求体、模型参数日志、tracer 或异常文本；非法 header 会被明确拒绝，OpenAI 正式路径的非 2xx 错误也不会拼接上游响应 body。
- 当前 OpenAI / OpenRouter 兼容客户端支持正式请求头；未实现该能力的客户端收到非空 headers 时明确失败。直接调用 `Model` 的正式与 legacy 用法、优先级和校验规则见[接入大模型](../基础功能/接入大模型.md#正式入口单次调用动态请求头)。

最终输出结果为：

```
ReActAgent 最终输出结果：
当前杭州的天气情况如下：
- 天气现象：小雨
- 实时温度：30.78℃
- 体感温度：37.78℃
- 空气湿度：74%
- 风速：0.77米/秒（约2.8公里/小时）

建议外出时携带雨具，注意防雨防滑。需要其他天气信息可以随时告诉我哦~
```
