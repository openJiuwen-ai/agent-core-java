package com.openjiuwen.harness;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TaskPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's test_steer_inner_loop.py.
 * System test: steering injection in inner ReAct loop.
 */
@Tag("system-test")
class SteerInnerLoopTest {

    static class BlockingTool extends Tool {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch gate = new CountDownLatch(1);
        final AtomicInteger callCount = new AtomicInteger(0);

        BlockingTool() {
            super(buildCard());
        }

        private static ToolCard buildCard() {
            ToolCard card = new ToolCard();
            assignCardField(card, "id", "blocking_tool");
            assignCardField(card, "name", "blocking_tool");
            assignCardField(card, "description", "A tool that blocks until released");
            return card;
        }

        private static void assignCardField(Object target, String fieldName, Object value) {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            callCount.incrementAndGet();
            entered.countDown();
            gate.await(30, TimeUnit.SECONDS);
            return "tool done";
        }

        @Override
        public java.util.Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return java.util.List.of().iterator();
        }
    }

    static class ModelCallObserver extends AgentRail {
        final List<List<Object>> modelCallMessages = new ArrayList<>();

        @Override
        public void beforeModelCall(AgentCallbackContext ctx) {
            Object messages = null;
            if (ctx.getInputs() instanceof Map<?, ?> map) {
                messages = map.get("messages");
            }
            if (messages instanceof List<?> list) {
                modelCallMessages.add(new ArrayList<>(list));
            }
        }
    }

    private static String extractContent(Object msg) {
        if (msg instanceof Map<?, ?> map) {
            return String.valueOf(map.getOrDefault("content", ""));
        }
        try {
            return String.valueOf(msg.getClass().getMethod("getContent").invoke(msg));
        } catch (Exception e) {
            return String.valueOf(msg);
        }
    }

    private static TaskPlan seedPlan(Session session) {
        List<TodoItem> tasks = List.of(
                TodoItem.create("step-1", "step-1 active", "\u6267\u884c\u7b2c\u4e00\u6b65\u64cd\u4f5c", null, null),
                TodoItem.create("step-2", "step-2 active", "\u6267\u884c\u7b2c\u4e8c\u6b65\u64cd\u4f5c", null, List.of("t1"))
        );
        Map<String, Object> planDict = new HashMap<>();
        planDict.put("goal", "test steering injection");
        planDict.put("tasks", tasks.stream().map(TodoItem::toMap).toList());
        Map<String, Object> deepagent = new HashMap<>();
        deepagent.put("iteration", 0);
        deepagent.put("task_plan", planDict);
        session.updateState(Map.of("deepagent", deepagent));
        return new TaskPlan("test steering injection", tasks);
    }

    private DeepAgentConfig buildConfig(Tool tool, AgentRail rail) {
        AgentCard card = new AgentCard();
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setSystemPrompt("\u4f60\u662f\u4e00\u4e2a\u6d4b\u8bd5\u52a9\u624b\u3002");
        config.setMaxIterations(6);
        config.setRails(List.of(rail));
        config.setTools(List.of(tool.getCard()));
        return config;
    }

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    void testSteerVisibleInSameInvoke() throws Exception {
        String steerText = "\u8bf7\u7528\u4e2d\u6587\u8f93\u51fa\u7b80\u6d01\u8981\u70b9";

        BlockingTool blockingTool = new BlockingTool();
        ModelCallObserver observer = new ModelCallObserver();

        DeepAgentConfig config = buildConfig(blockingTool, observer);
        DeepAgent agent = HarnessFactory.createDeepAgent(config);

        Session session = new AgentSessionApi(
                "steer_inner_" + UUID.randomUUID().toString().replace("-", ""),
                null, null
        );
        seedPlan(session);

        AtomicReference<Object> resultRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Thread invokeThread = new Thread(() -> {
            try {
                Map<String, Object> inputs = new HashMap<>();
                inputs.put("query", "\u6267\u884c\u4e24\u6b65\u8ba1\u5212");
                Object result = Runner.runAgent(agent, inputs, session, null);
                resultRef.set(result);
            } catch (Exception e) {
                resultRef.set(e);
            } finally {
                done.countDown();
            }
        }, "agent-invoke");
        invokeThread.start();

        assertTrue(blockingTool.entered.await(10, TimeUnit.SECONDS),
                "blocking_tool should have been entered");

        if (agent instanceof DeepAgent da) {
            da.steer(steerText, session);
        }

        blockingTool.gate.countDown();

        assertTrue(done.await(15, TimeUnit.SECONDS), "agent invoke should complete");

        Object result = resultRef.get();
        if (result instanceof Exception e) {
            throw e;
        }
        assertInstanceOf(Map.class, result);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("answer", resultMap.get("result_type"));

        assertTrue(observer.modelCallMessages.size() >= 2,
                "At least 2 model calls should have happened in round 1");

        List<Object> firstMsgs = observer.modelCallMessages.get(0);
        boolean steerInFirst = firstMsgs.stream()
                .anyMatch(m -> extractContent(m).contains("[STEERING]"));
        assertFalse(steerInFirst,
                "[STEERING] should NOT appear in model call #1");

        List<Object> secondMsgs = observer.modelCallMessages.get(1);
        boolean steeringFound = secondMsgs.stream()
                .anyMatch(m -> {
                    String content = extractContent(m);
                    return content.contains("[STEERING]") && content.contains(steerText);
                });
        assertTrue(steeringFound,
                "[STEERING] with steer text should appear in model call #2 (same invoke)");
    }
}
