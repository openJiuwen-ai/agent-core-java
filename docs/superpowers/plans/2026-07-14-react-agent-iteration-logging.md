# ReActAgent Iteration Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `ReActAgent#innerInvoke` 增加可靠的单轮迭代起止日志、调用与清理异常堆栈日志、流式错误处理日志和最大轮次耗尽日志，同时保持原有控制流语义。

**Architecture:** 在现有 ReAct 主循环、调用初始化、流式错误写回和自动清理周围增加分层的 `try/catch/finally` 边界。迭代异常在轮次边界记录一次并重新抛出，外层只补充迭代外异常；流式错误处理和清理异常分别记录后原样抛出，并用 `initializationComplete` 保持初始化失败时原有的转换和清理边界。新建聚焦的日志测试类，通过 `Loggers.AGENT` 的 JUL 镜像捕获日志。

**Tech Stack:** Java 17、Maven、JUnit 5、AssertJ、项目内 `LoggerProtocol`/`Loggers.AGENT`

---

## 文件边界

- Create: `src/test/java/com/openjiuwen/core/singleagent/agents/ReActAgentIterationLoggingTest.java`：仅验证 ReAct 迭代生命周期和错误日志。
- Modify: `src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java`：在 `innerInvoke` 中增加日志，不改变其他代理或公共 API。
- Create: `docs/superpowers/plans/2026-07-14-react-agent-iteration-logging.md`：记录本实施计划。
- Existing spec: `docs/superpowers/specs/2026-07-14-react-agent-iteration-logging-design.md`：作为验收依据。

仓库规则禁止未经用户明确要求自行提交，因此下列任务不包含 `git commit` 步骤。

### Task 1: 正常轮次起止日志

**Files:**
- Create: `src/test/java/com/openjiuwen/core/singleagent/agents/ReActAgentIterationLoggingTest.java`
- Modify: `src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java:1255`

- [ ] **Step 1: 写入正常轮次失败测试和最小测试支撑**

创建聚焦测试类，首个测试使用脚本化代理直接返回答案，并通过可自动移除的日志处理器捕获 `Loggers.AGENT`：

```java
@Test
void logsIterationStartAndEndWhenModelReturnsAnswer() {
    ScriptedAgent agent = scriptedAgent(1, new AssistantMessage("done"));

    try (AgentLogCapture logs = new AgentLogCapture()) {
        Map<String, Object> result = invokeMap(agent, Map.of("query", "hello"));

        assertThat(result).containsEntry("output", "done")
                .containsEntry("result_type", "answer");
        assertThat(logs.reactMessages()).containsExactly(
                "ReAct iteration 1/1 started",
                "ReAct iteration 1/1 ended"
        );
    }
}
```

测试类使用以下导入和支撑代码。`ScriptedAgent#callModel` 返回脚本化结果，`MemorySession` 实现 `AgentSessionApi` 和 `ContextEngine.SessionPort`，`AgentLogCapture` 保存 `LogRecord` 并在 `close()` 中移除自身：

```java
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReActAgentIterationLoggingTest {

    private static ScriptedAgent scriptedAgent(int maxIterations, Object... responses) {
        ScriptedAgent agent = new ScriptedAgent(List.of(responses));
        ReActAgentConfig config = new ReActAgentConfig().configureMaxIterations(maxIterations);
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        agent.configure(config);
        return agent;
    }

    private static ToolCall toolCall(String id, String name, String arguments) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .name(name)
                .arguments(arguments)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeMap(ReActAgent agent, Object inputs) {
        return (Map<String, Object>) agent.invoke(inputs, new MemorySession())
                .toCompletableFuture()
                .join();
    }

    private static final class ScriptedAgent extends ReActAgent {
        private final List<Object> responses;
        private int callCount;

        private ScriptedAgent(List<Object> responses) {
            super(new AgentCard("logging-agent", "logging-agent", "Logging test agent"));
            this.responses = new ArrayList<>(responses);
        }

        @Override
        public Object callModel(AgentCallbackContext ctx, ModelContext context, List<ToolInfo> tools) {
            int index = Math.min(callCount++, responses.size() - 1);
            Object response = responses.get(index);
            if (response instanceof RuntimeException exception) {
                throw exception;
            }
            return response;
        }
    }

    private static final class EchoTool extends Tool {
        private EchoTool() {
            super(new ToolCard("echo-id", "echo", "Echo tool", Map.of()));
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return inputs;
        }
    }

    private static final class AgentLogCapture extends Handler implements AutoCloseable {
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        private AgentLogCapture() {
            Loggers.AGENT.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        private List<String> reactMessages() {
            return records.stream()
                    .map(LogRecord::getMessage)
                    .filter(message -> message.startsWith("ReAct"))
                    .toList();
        }

        private LogRecord record(String message) {
            return records.stream()
                    .filter(record -> message.equals(record.getMessage()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing log record: " + message));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            Loggers.AGENT.removeHandler(this);
        }
    }

    private static final class MemorySession implements AgentSessionApi, ContextEngine.SessionPort {
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        @Override
        public String getSessionId() {
            return "iteration-logging-session";
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }
}
```

- [ ] **Step 2: 运行测试确认因缺少日志而失败**

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest#logsIterationStartAndEndWhenModelReturnsAnswer" test
```

Expected: FAIL；结果断言通过，但 `reactMessages()` 为空，缺少两条生命周期日志。

- [ ] **Step 3: 添加最小起止日志边界**

在 `innerInvoke` 的每轮循环中先记录开始日志，再用 `try/finally` 包住原有轮次体：

```java
for (int iteration = startIteration; iteration < config.getMaxIterations(); iteration++) {
    int iterationNumber = iteration + 1;
    Loggers.AGENT.info("ReAct iteration {}/{} started", iterationNumber, config.getMaxIterations());
    try {
        List<String> steering = ctx.drainSteering();
        if (!steering.isEmpty()) {
            context.addMessages(new UserMessage("[STEERING] " + String.join("\n", steering)))
                    .toCompletableFuture()
                    .join();
        }
        List<ToolInfo> tools = listEffectiveToolInfo(session);
        Object modelResult = callModel(ctx, context, tools);
        ForceFinishRequest finish = ctx.consumeForceFinish();
        if (finish != null) {
            contextEngine.saveContexts(session);
            invokeInputs.setResult(finish.getResult());
            break;
        }
        if (!(modelResult instanceof AssistantMessage aiMessage)) {
            invokeInputs.setResult(modelResult instanceof Map<?, ?> map ? stringObjectMap(map) : Map.of());
            break;
        }
        List<ToolCall> toolCalls = aiMessage.getToolCalls();
        ensureToolCallIds(toolCalls);
        context.addMessages(copyAssistantMessage(aiMessage)).toCompletableFuture().join();
        if (toolCalls == null || toolCalls.isEmpty()) {
            if (ctx.hasPendingSteering()) {
                continue;
            }
            contextEngine.saveContexts(session);
            invokeInputs.setResult(new LinkedHashMap<>(Map.of(
                    "output", Objects.toString(aiMessage.getContent(), ""),
                    "result_type", "answer"
            )));
            break;
        }
        writeToolCallOutputs(ctx, session, toolCalls);
        if (hasExternalToolCall(toolCalls)) {
            ExternalToolPendingState pendingState = new ExternalToolPendingState(
                    copyAssistantMessage(aiMessage),
                    iteration,
                    Objects.toString(ctx.getExtra().get("_original_query"), ""),
                    toolCalls,
                    externalToolCallRequests(toolCalls)
            );
            saveExternalToolPendingState(pendingState, session);
            contextEngine.saveContexts(session);
            writeExternalToolPendingOutput(ctx, session, pendingState);
            invokeInputs.setResult(buildExternalToolPendingResult(pendingState));
            break;
        }
        List<AbilityManager.ExecutionResult> results = executeToolCall(ctx, toolCalls, session, context);
        activateSkillsLoadedByToolCalls(toolCalls, results, session);
        if (completeToolExecutionTurn(
                ctx,
                context,
                session,
                invokeInputs,
                toolCalls,
                results,
                aiMessage,
                iteration,
                Objects.toString(ctx.getExtra().get("_original_query"), ""),
                ToolExecutionTurnOrigin.NORMAL_TOOL_LOOP)) {
            break;
        }
    } finally {
        Loggers.AGENT.info("ReAct iteration {}/{} ended", iterationNumber, config.getMaxIterations());
    }
}
```

所有现有 `break` 和 `continue` 必须保留在 `try` 内，以保证退出前执行 `finally`。

- [ ] **Step 4: 运行正常轮次测试确认通过**

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest#logsIterationStartAndEndWhenModelReturnsAnswer" test
```

Expected: PASS，且业务结果仍为 `done`/`answer`。

### Task 2: 迭代异常和迭代外异常日志

**Files:**
- Modify: `src/test/java/com/openjiuwen/core/singleagent/agents/ReActAgentIterationLoggingTest.java`
- Modify: `src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java:1185,1255,1340`

- [ ] **Step 1: 写入迭代异常失败测试**

让脚本化代理在 `callModel` 中抛出给定异常，断言业务异常语义、日志顺序和 `LogRecord#getThrown()`：

```java
@Test
void logsIterationFailureWithOriginalExceptionAndStillLogsEnd() {
    IllegalStateException failure = new IllegalStateException("model exploded");
    ScriptedAgent agent = scriptedAgent(2, failure);

    try (AgentLogCapture logs = new AgentLogCapture()) {
        assertThatThrownBy(() -> invokeMap(agent, Map.of("query", "hello")))
                .hasRootCauseMessage("model exploded");

        assertThat(logs.reactMessages()).containsExactly(
                "ReAct iteration 1/2 started",
                "ReAct iteration 1/2 failed",
                "ReAct iteration 1/2 ended"
        );
        assertThat(logs.record("ReAct iteration 1/2 failed").getThrown()).isSameAs(failure);
        assertThat(logs.reactMessages()).doesNotContain("ReActAgent invoke failed");
    }
}
```

- [ ] **Step 2: 写入流式迭代异常去重测试**

使用流式调用触发模型异常，断言返回现有 error Map，同时只保留轮次失败日志，不重复记录调用失败：

```java
@Test
void logsStreamingIterationFailureWithoutDuplicateInvokeFailure() {
    IllegalStateException failure = new IllegalStateException("streaming model exploded");
    ScriptedAgent agent = scriptedAgent(1, failure);
    MemorySession session = new MemorySession();

    try (AgentLogCapture logs = new AgentLogCapture()) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) agent.innerInvoke(
                session,
                Map.of("query", "hello"),
                "hello",
                false,
                null,
                Map.of("_streaming", true)
        );

        assertThat(result).containsEntry("output", "streaming model exploded")
                .containsEntry("result_type", "error");
        assertThat(logs.reactMessages()).containsExactly(
                "ReAct iteration 1/1 started",
                "ReAct iteration 1/1 failed",
                "ReAct iteration 1/1 ended"
        );
        assertThat(logs.record("ReAct iteration 1/1 failed").getThrown()).isSameAs(failure);
        assertThat(logs.reactMessages()).doesNotContain("ReActAgent invoke failed");
    }
}
```

- [ ] **Step 3: 写入迭代外异常失败测试**

使用缺少 `query` 的输入在进入循环前触发校验异常：

```java
@Test
void logsInvokeFailureOutsideIterationWithOriginalException() {
    ScriptedAgent agent = scriptedAgent(1, new AssistantMessage("unused"));

    try (AgentLogCapture logs = new AgentLogCapture()) {
        assertThatThrownBy(() -> invokeMap(agent, Map.of()))
                .hasRootCauseMessage("Input must contain 'query'");

        assertThat(logs.reactMessages()).containsExactly("ReActAgent invoke failed");
        assertThat(logs.record("ReActAgent invoke failed").getThrown())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Input must contain 'query'");
    }
}
```

- [ ] **Step 4: 运行三个异常测试确认失败**

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest#logsIterationFailureWithOriginalExceptionAndStillLogsEnd+logsStreamingIterationFailureWithoutDuplicateInvokeFailure+logsInvokeFailureOutsideIterationWithOriginalException" test
```

Expected: FAIL；结束日志已经存在，但 `failed`、`invoke failed` 和异常堆栈尚未记录。

- [ ] **Step 5: 实现单次异常记录**

在外层 `try` 前声明标记；轮次 `catch` 记录异常并重新抛出，外层 `catch` 仅补充未记录的迭代外异常：

```java
boolean iterationFailureLogged = false;
try {
```

把 Task 1 轮次体末尾的 `finally` 扩展为：

```java
    } catch (RuntimeException exception) {
        iterationFailureLogged = true;
        Loggers.AGENT.exception(
                "ReAct iteration %d/%d failed".formatted(iterationNumber, config.getMaxIterations()),
                exception
        );
        throw exception;
    } finally {
        Loggers.AGENT.info("ReAct iteration {}/{} ended", iterationNumber, config.getMaxIterations());
    }
}
```

把现有外层 `catch` 完整替换为：

```java
} catch (RuntimeException exception) {
    if (!iterationFailureLogged) {
        Loggers.AGENT.exception("ReActAgent invoke failed", exception);
    }
    if (Boolean.TRUE.equals(ctx.getExtra().get("_streaming"))) {
        Map<String, Object> errorResult = buildErrorResult(exception);
        writeInvokeResultToStreamInternal(errorResult, session, streamIndexRef(ctx));
        return errorResult;
    }
    throw exception;
}
```

异常消息预先格式化，确保当前日志后端的 SLF4J 异常输出和 JUL 镜像都显示实际轮次，而不是保留 `{}` 占位符。

- [ ] **Step 6: 运行异常测试确认通过**

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest#logsIterationFailureWithOriginalExceptionAndStillLogsEnd+logsStreamingIterationFailureWithoutDuplicateInvokeFailure+logsInvokeFailureOutsideIterationWithOriginalException" test
```

Expected: PASS；迭代异常只记录一次，原异常对象进入 `LogRecord#getThrown()`，原调用异常语义不变。

### Task 3: 最大轮次耗尽日志

**Files:**
- Modify: `src/test/java/com/openjiuwen/core/singleagent/agents/ReActAgentIterationLoggingTest.java`
- Modify: `src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java:1320`

- [ ] **Step 1: 写入最大轮次耗尽失败测试**

让模型持续返回可执行的工具调用，配置两轮上限，并断言每轮日志成对、错误结果不变、最后有明确 ERROR 日志：

```java
@Test
void logsEveryIterationAndErrorWhenMaxIterationsAreReached() {
    AssistantMessage toolRequest = AssistantMessage.builder()
            .content("")
            .toolCalls(List.of(toolCall("call-1", "echo", "{}")))
            .build();
    ScriptedAgent agent = scriptedAgent(2, toolRequest);
    agent.getAbilityManager().add(new EchoTool());

    try (AgentLogCapture logs = new AgentLogCapture()) {
        Map<String, Object> result = invokeMap(agent, Map.of("query", "hello"));

        assertThat(result).containsEntry("result_type", "error")
                .containsEntry("output", "Max iterations reached without completion");
        assertThat(logs.reactMessages()).containsExactly(
                "ReAct iteration 1/2 started",
                "ReAct iteration 1/2 ended",
                "ReAct iteration 2/2 started",
                "ReAct iteration 2/2 ended",
                "ReActAgent reached max iterations without completion: 2"
        );
        assertThat(logs.record("ReActAgent reached max iterations without completion: 2").getLevel())
                .isEqualTo(Level.SEVERE);
    }
}
```

- [ ] **Step 2: 运行测试确认因缺少最大轮次日志而失败**

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest#logsEveryIterationAndErrorWhenMaxIterationsAreReached" test
```

Expected: FAIL；四条轮次日志存在，但缺少最后的最大轮次 ERROR 日志。

- [ ] **Step 3: 在既有错误结果生成前增加 ERROR 日志**

```java
if (invokeInputs.getResult() == null) {
    Loggers.AGENT.error(
            "ReActAgent reached max iterations without completion: {}",
            config.getMaxIterations()
    );
    contextEngine.saveContexts(session);
    invokeInputs.setResult(new LinkedHashMap<>(Map.of(
            "output", "Max iterations reached without completion",
            "result_type", "error"
    )));
}
```

- [ ] **Step 4: 运行聚焦测试类确认全部通过**

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest" test
```

Expected: PASS，4 个日志测试全部通过。

### Task 4: 回归验证与差异检查

**Files:**
- Verify: `src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java`
- Verify: `src/test/java/com/openjiuwen/core/singleagent/agents/ReActAgentIterationLoggingTest.java`
- Verify: `docs/superpowers/specs/2026-07-14-react-agent-iteration-logging-design.md`

- [ ] **Step 1: 运行 ReActAgent 相关回归测试**

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest,NewReActAgentMockTest,ReActAgentTest,ReActAgentModelRetryOutputTest" test
```

Expected: PASS；包含既有同步、流式错误、最大轮次和模型重试场景。

- [ ] **Step 2: 运行完整 Maven 测试**

Run:

```powershell
mvn test
```

Expected: BUILD SUCCESS；如果仓库既有环境型失败阻止全量通过，必须保留完整失败输出并区分与本改动的关系，不能声称全量通过。

- [ ] **Step 3: 检查格式和工作区边界**

Run:

```powershell
git diff --check
git status --short
git diff -- src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java src/test/java/com/openjiuwen/core/singleagent/agents/ReActAgentIterationLoggingTest.java docs/superpowers
```

Expected: `git diff --check` 无输出；改动仅位于目标仓的实现、测试、设计和计划文件，不包含 `.references/`、Demo 或其他独立仓。

- [ ] **Step 4: 对照设计逐项确认**

确认实际日志满足：每轮恰好一条开始和结束 INFO；迭代异常只有一条带原始堆栈的 ERROR；迭代外异常带原始堆栈；最大轮次耗尽有 ERROR；日志不包含输入、模型内容或工具参数；同步和流式返回语义未改变。

### Task 5: 覆盖 `innerInvoke` 初始化阶段异常

**Files:**
- Modify: `src/test/java/com/openjiuwen/core/singleagent/agents/ReActAgentIterationLoggingTest.java`
- Modify: `src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java:1159-1186`

- [ ] **Step 1: 写入初始化异常失败测试**

新增一个 `Map<String, Object>` 测试替身，在读取 `_streaming` 时抛出指定异常。直接调用公开的 `innerInvoke`，断言异常对象保持不变，并要求出现携带同一 throwable 的 `ReActAgent invoke failed`。

- [ ] **Step 2: 运行测试确认缺少调用失败日志**

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest#logsInitializationFailureWithOriginalException" test
```

Expected: FAIL；原异常已经抛出，但日志捕获器找不到 `ReActAgent invoke failed`。

- [ ] **Step 3: 把调用级异常边界扩展到初始化阶段**

在 `innerInvoke` 顶部先声明调用级状态，再从初始化逻辑开始进入 `try`：

```java
boolean iterationFailureLogged = false;
boolean streaming = false;
boolean initializationComplete = false;
AgentCallbackContext ctx = null;
try {
    InvokeInputs invokeInputs = new InvokeInputs();
    invokeInputs.setQuery(query);
    invokeInputs.setConversationId(conversationId);

    ctx = new AgentCallbackContext(this);
    ctx.setInputs(invokeInputs);
    ctx.setSession(session);
    streaming = Boolean.TRUE.equals(kwargs.get("_streaming"));
    ctx.getExtra().put("_streaming", streaming);
```

随后接回当前已有的 steering queue、回调和迭代逻辑，并删除原来位于初始化之后的重复 `try` 开头。这个阶段先保留调用级 `catch` 使用局部 `streaming`，Task 5 后续步骤再用 `initializationComplete` 恢复初始化失败原有的流式转换和 cleanup 边界。

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest#logsInitializationFailureWithOriginalException" test
```

Expected: PASS；日志和抛出异常携带同一个 throwable。

- [ ] **Step 5: 写入初始化后半段的控制流回归测试并确认失败**

新增两条真实 `innerInvoke` 测试：

1. `propagatesStreamingInitializationFailureWithOriginalException`：先成功读取 `_streaming=true`，再让输入 Map 在读取 `user_id` 时抛出异常；断言原异常继续抛出而不是转换为流式 error Map。
2. `preservesInitializationFailureWithoutCleanup`：使用 `needCleanup=true` 和会在 `commit` 时抛出独立异常的生命周期会话；断言初始化异常不触发 save/commit/close，也不被 cleanup 异常覆盖。

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest#propagatesStreamingInitializationFailureWithOriginalException" test
mvn "-Dtest=ReActAgentIterationLoggingTest#preservesInitializationFailureWithoutCleanup" test
```

Expected: 两条命令均 FAIL；第一条错误地返回流式 error Map，第二条 cleanup 异常覆盖初始化异常。

- [ ] **Step 6: 用初始化完成状态恢复原控制流边界**

完成 streaming extra 和所有输入附加信息初始化后、进入 `BEFORE_INVOKE` 前设置：

```java
initializationComplete = true;
```

调用级异常转换和自动清理分别使用：

```java
if (initializationComplete && streaming) {
    Map<String, Object> errorResult = buildErrorResult(exception);
    writeInvokeResultToStreamInternal(errorResult, session, streamIndexRef(ctx));
    return errorResult;
}

if (needCleanup && initializationComplete) {
    contextEngine.saveContexts(session);
    if (session instanceof AgentSessionLifecycle lifecycle) {
        closeStreamAndCommit(lifecycle);
    }
}
```

- [ ] **Step 7: 运行初始化日志和控制流测试确认通过**

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest#logsInitializationFailureWithOriginalException+propagatesStreamingInitializationFailureWithOriginalException+preservesInitializationFailureWithoutCleanup" test
```

Expected: PASS；三条测试均保留原 throwable，初始化未完成时不做流式错误转换或 cleanup。

### Task 6: 记录流式错误处理的二次异常

**Files:**
- Modify: `src/test/java/com/openjiuwen/core/singleagent/agents/ReActAgentIterationLoggingTest.java`
- Modify: `src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java:1363-1372`

- [ ] **Step 1: 写入流式错误写回失败测试**

新增继承现有内存会话的测试会话，使 `writeStream` 抛出指定异常。让模型先抛出独立的迭代异常，断言日志顺序为 `started`、迭代 `failed`、`ended`、`ReActAgent streaming error handling failed`；两条 ERROR 分别携带原迭代 throwable 和写回 throwable，最终抛出写回 throwable。

- [ ] **Step 2: 运行测试确认二次异常没有日志**

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest#logsStreamingErrorHandlingFailureWithOriginalException" test
```

Expected: FAIL；最终抛出写回异常，但缺少 `ReActAgent streaming error handling failed`。

- [ ] **Step 3: 为流式错误处理增加嵌套异常边界**

```java
if (initializationComplete && streaming) {
    try {
        Map<String, Object> errorResult = buildErrorResult(exception);
        writeInvokeResultToStreamInternal(errorResult, session, streamIndexRef(ctx));
        return errorResult;
    } catch (RuntimeException streamingErrorHandlingException) {
        Loggers.AGENT.exception(
                "ReActAgent streaming error handling failed",
                streamingErrorHandlingException
        );
        throw streamingErrorHandlingException;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest#logsStreamingErrorHandlingFailureWithOriginalException" test
```

Expected: PASS；两次独立失败均只记录一次，最终异常传播语义不变。

### Task 7: 记录自动清理异常

**Files:**
- Modify: `src/test/java/com/openjiuwen/core/singleagent/agents/ReActAgentIterationLoggingTest.java`
- Modify: `src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java:1384-1395`

- [ ] **Step 1: 写入清理失败测试**

新增实现 `AgentSessionLifecycle` 的内存会话，使 `commit` 抛出给定异常；调用 `innerInvoke` 时启用 `needCleanup`，断言正常轮次日志后出现 `ReActAgent cleanup failed`，日志携带同一 throwable 且级别为 ERROR，方法原样抛出该异常，并且 `closeStream` 和 `commit` 都已调用。

- [ ] **Step 2: 运行测试确认缺少清理异常日志**

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest#logsCleanupFailureWithOriginalException" test
```

Expected: FAIL；清理异常仍会抛出，但缺少 `ReActAgent cleanup failed` 及其原始 throwable。

- [ ] **Step 3: 为自动清理增加独立异常边界**

```java
if (needCleanup && initializationComplete) {
    try {
        contextEngine.saveContexts(session);
        if (session instanceof AgentSessionLifecycle lifecycle) {
            closeStreamAndCommit(lifecycle);
        }
    } catch (RuntimeException exception) {
        Loggers.AGENT.exception("ReActAgent cleanup failed", exception);
        throw exception;
    }
}
```

- [ ] **Step 4: 运行清理失败测试确认通过**

Run:

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest#logsCleanupFailureWithOriginalException" test
```

Expected: PASS；ERROR 日志保留原 throwable，异常原样抛出，即使提交失败仍执行流关闭。

### Task 8: 增量复审与验证

**Files:**
- Verify: `src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java`
- Verify: `src/test/java/com/openjiuwen/core/singleagent/agents/ReActAgentIterationLoggingTest.java`
- Verify: `docs/superpowers/specs/2026-07-14-react-agent-iteration-logging-design.md`

- [ ] **Step 1: 运行日志测试类和 ReActAgent 定向回归**

```powershell
mvn "-Dtest=ReActAgentIterationLoggingTest,NewReActAgentMockTest,ReActAgentTest,ReActAgentModelRetryOutputTest" test
```

Expected: PASS，失败数和错误数均为 0。

- [ ] **Step 2: 检查差异与空白错误**

```powershell
git diff --check
git status --short
```

Expected: 无空白错误，改动仍限定在目标仓的实现、测试和既有设计/计划文档。

- [ ] **Step 3: 请求代码复审并执行最终验证**

复审必须确认两个 Important 问题均已关闭；随后按 `verification-before-completion` 的要求运行最终验证命令并据实报告结果。仓库规则禁止未经用户明确要求自动提交，因此不包含提交步骤。
