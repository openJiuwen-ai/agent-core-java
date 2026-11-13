/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.deep_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.deep_agent.DeepAgentSession;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DeepAgent 引导模式（steering）演示：invoke 运行期间从外部注入引导消息。
 *
 * <p>本示例验证引导模式的三个核心行为：
 * <ol>
 *   <li><b>不打断当前轮次</b> —— steer 发生在第 1 次模型调用（模拟长思考）期间，
 *       该调用仍正常返回中间答案 {@code "draft answer (before steering)"}；</li>
 *   <li><b>下一轮可见</b> —— 第 2 次模型请求的上下文中出现一条内容以
 *       {@code "[STEERING] "} 开头的用户消息，携带主线程注入的引导内容；</li>
 *   <li><b>收敛后返回</b> —— invoke 不在中间答案处退出，而是继续一轮拿到
 *       {@code "final answer incorporating steering guidance"} 作为最终 output。</li>
 * </ol>
 *
 * <p>本示例使用<b>确定性 fake 模型</b>（脚本化 {@link Model.ModelInvoker}，无需 API Key、
 * 无网络）：第 1 次调用 sleep 约 1.5 秒模拟长思考后返回中间答案，第 2 次起返回收敛答案。
 * 运行结束打印 PASS/FAIL 判定（FAIL 时以退出码 1 结束）。
 *
 * <p>真实 LLM 用法：steer API 与本示例完全一致。真实场景中用
 * {@code agent.invokeAsync(inputs, session)}（或如本示例在工作线程上阻塞 invoke）启动任务，
 * 在 UI/调度线程随时调用 {@code agent.steer(message, session)} 注入中途指令（例如
 * "重点分析 X"）。运行中的模型调用不会被打断；指令在下一轮请求以
 * {@code "[STEERING] "} 前缀消息注入上下文；最终答案由模型消化引导后收敛返回。
 *
 * <p>运行（见目录 README）：
 * <pre>
 * mvn -q compile exec:java "-Dexec.args=steering"
 * </pre>
 */
public final class DeepAgentSteeringExample {
    /** 第 1 次模型调用模拟"长思考"的毫秒数，为主线程留出 steer 窗口。 */
    private static final long FIRST_CALL_THINKING_MILLIS = 1500L;

    /** 第 1 次模型调用进入后，主线程等待多少毫秒再注入 steer（仍在长思考窗口内）。 */
    private static final long STEER_DELAY_MILLIS = 500L;

    /** 主线程等待第 1 次模型调用进入的超时（秒）。 */
    private static final long LATCH_TIMEOUT_SECONDS = 10L;

    /** invoke 工作线程 join 超时（毫秒）。 */
    private static final long JOIN_TIMEOUT_MILLIS = 30000L;

    /** 任务循环完成超时（秒），覆盖 2 轮模型调用与间隔开销。 */
    private static final double COMPLETION_TIMEOUT_SECONDS = 30.0;

    /** 任务循环最大轮次。 */
    private static final int MAX_ITERATIONS = 4;

    /** 主线程注入的引导消息。 */
    private static final String STEER_MESSAGE = "focus on the steering requirement";

    /** 第 1 轮模型的中间答案（不包含引导内容）。 */
    private static final String DRAFT_ANSWER = "draft answer (before steering)";

    /** 第 2 轮起模型的收敛答案（包含引导内容）。 */
    private static final String FINAL_ANSWER = "final answer incorporating steering guidance";

    /** 引导消息注入上下文时的内容前缀。 */
    private static final String STEERING_PREFIX = "[STEERING] ";

    private DeepAgentSteeringExample() {
    }

    /**
     * Entry point.
     *
     * @param args unused
     * @throws Exception if the example fails
     */
    public static void run(String[] args) throws Exception {
        System.out.println("=== DeepAgent 引导模式（steering）示例 ===");
        System.out.println("[note] 本示例使用确定性 fake 模型（脚本化 ModelInvoker），无需 API Key / 网络；");
        System.out.println("[note] 同一 steer API 对真实 LLM 同样生效，真实用法见本类 javadoc。");

        long startNanos = System.nanoTime();
        Path workspacePath = Files.createTempDirectory("deep-agent-steering-");
        System.out.println("[setup] workspace=" + workspacePath);

        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        Runner.setConfig(RunnerConfig.DEFAULT);
        Runner.start();

        List<String> timeline = new CopyOnWriteArrayList<>();
        ScriptedSteeringInvoker scriptedModel = new ScriptedSteeringInvoker(timeline, startNanos);
        Model fakeModel = new Model(scriptedModel);
        DeepAgent agent = buildAgent(workspacePath, fakeModel);

        boolean pass;
        try {
            DeepAgentSession session = new DeepAgentSession("steering-demo-session", null, agent.getCard());
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("query", "draft an answer first; a steering requirement may arrive mid-run");
            inputs.put("conversation_id", "steering-demo-session");

            // 工作线程：阻塞式 invoke，ReAct 任务循环在其上收敛；主线程保留 steer 能力。
            AtomicReference<Map<String, Object>> resultRef = new AtomicReference<>();
            List<Throwable> workerFailures = new CopyOnWriteArrayList<>();
            Thread worker = new Thread(() -> resultRef.set(agent.invoke(inputs, session)),
                    "steering-invoke-worker");
            worker.setUncaughtExceptionHandler((thread, error) -> workerFailures.add(error));
            worker.start();
            timeline.add(elapsed(startNanos) + " [invoke] 工作线程已发起阻塞式 invoke");

            // 等第 1 次模型调用进入（长思考中）后再注入 steer，保证时序确定：
            // steer 一定落在第 1 次调用进行期间，且 500ms 仍在 1500ms 思考窗口内。
            if (!scriptedModel.awaitFirstCallEntered(LATCH_TIMEOUT_SECONDS)) {
                throw new IllegalStateException("第 1 次模型调用未在 " + LATCH_TIMEOUT_SECONDS
                        + "s 内开始" + (workerFailures.isEmpty() ? "" : "；工作线程异常: " + workerFailures.get(0)));
            }
            Thread.sleep(STEER_DELAY_MILLIS);
            timeline.add(elapsed(startNanos) + " [steer] 主线程注入引导消息（第 1 次模型调用仍在进行中）: "
                    + STEER_MESSAGE);
            agent.steer(STEER_MESSAGE, session);

            worker.join(JOIN_TIMEOUT_MILLIS);
            if (worker.isAlive()) {
                throw new IllegalStateException("invoke 工作线程未在 " + JOIN_TIMEOUT_MILLIS + "ms 内结束");
            }
            if (!workerFailures.isEmpty()) {
                throw new IllegalStateException("invoke 工作线程失败: " + workerFailures.get(0));
            }
            timeline.add(elapsed(startNanos) + " [invoke] 工作线程结束，拿到最终结果");

            pass = report(startNanos, timeline, scriptedModel, resultRef.get());
        } finally {
            try {
                CheckpointerFactory.setDefaultCheckpointer(null);
            } catch (Exception ignored) {
                // best-effort
            }
            try {
                agent.close();
            } catch (Exception ignored) {
                // best-effort
            }
            Runner.stop();
        }

        System.out.println();
        if (pass) {
            System.out.println("=== 示例结束（PASS）===");
        } else {
            System.out.println("=== 示例结束（FAIL）===");
            System.exit(1);
        }
    }

    // ===================== Agent builder =====================

    private static DeepAgent buildAgent(Path workspacePath, Model fakeModel) {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTaskLoop(true)
                .systemPrompt("你是引导模式演示助手。先给出草稿答案；收到用户中途注入的引导消息后，"
                        + "给出融合引导要求的最终答案。")
                .maxIterations(MAX_ITERATIONS)
                .completionTimeout(COMPLETION_TIMEOUT_SECONDS)
                .language("cn")
                .workspacePath(workspacePath.toString())
                .build();

        AgentCard card = AgentCard.builder()
                .name("steering_demo_agent")
                .description("DeepAgent 引导模式示例").build();
        Workspace ws = new Workspace(workspacePath.toString(), "cn");
        DeepAgent agent = HarnessFactory.createDeepAgent(card, config, ws);
        // 安装确定性 fake 模型（真实 LLM 时此处换成 Model.initModel(...) 的真实实例，
        // 其余 steer 流程完全相同）。
        agent.getAgent().setLlm(fakeModel);
        agent.ensureInitialized();
        return agent;
    }

    // ===================== Report & verdict =====================

    /**
     * 打印时间线、每轮模型请求的 [STEERING] 可见性、最终 output，并给出 PASS/FAIL 判定。
     *
     * <p>PASS 判定（对应需求金标）：
     * <ol>
     *   <li>最终 output 等于 {@value #FINAL_ANSWER}（收敛答案，而非 steer 前的中间答案）；</li>
     *   <li>第 2 次模型请求包含 {@value #STEERING_PREFIX} 消息。</li>
     * </ol>
     *
     * @param startNanos     示例起点
     * @param timeline       时间线（invoke / steer / 模型调用事件）
     * @param scriptedModel  脚本化 fake 模型（含捕获的每次请求）
     * @param result         invoke 返回结果
     * @return true 表示 PASS
     */
    private static boolean report(long startNanos, List<String> timeline,
            ScriptedSteeringInvoker scriptedModel, Map<String, Object> result) {
        List<List<BaseMessage>> requests = scriptedModel.capturedRequests();

        System.out.println();
        System.out.println("---- 时间线（自示例启动起） ----");
        for (String entry : timeline) {
            System.out.println("  " + entry);
        }

        System.out.println();
        System.out.println("---- 每轮模型请求的 [STEERING] 可见性 ----");
        for (int i = 0; i < requests.size(); i++) {
            List<BaseMessage> request = requests.get(i);
            String steering = steeringContent(request);
            System.out.println("  模型请求 #" + (i + 1) + ": 消息数=" + request.size()
                    + " 角色序列=" + describeRoles(request)
                    + " [STEERING] 可见=" + (steering == null ? "否" : "是 -> " + truncate(steering, 200)));
        }

        System.out.println();
        System.out.println("---- 最终结果 ----");
        String output = result == null ? null : String.valueOf(result.get("output"));
        System.out.println("  模型调用总次数: " + requests.size());
        Object rounds = result == null ? null : result.get("rounds");
        System.out.println("  外层轮次(rounds): " + (rounds instanceof List<?> roundList
                ? roundList.size() + " 轮" : String.valueOf(rounds)));
        System.out.println("  最终 output: " + output);

        boolean outputConverged = FINAL_ANSWER.equals(output);
        boolean secondRequestSawSteering = requests.size() >= 2
                && steeringContent(requests.get(1)) != null;

        System.out.println();
        System.out.println("---- 判定 ----");
        System.out.println("  [check-1] output 为收敛答案 (" + FINAL_ANSWER + "): "
                + (outputConverged ? "满足" : "不满足，实际=" + output));
        System.out.println("  [check-2] 第 2 次模型请求含 user 角色的 " + STEERING_PREFIX + "消息: "
                + (secondRequestSawSteering ? "满足" : "不满足"));
        if (secondRequestSawSteering) {
            String expected = STEERING_PREFIX + STEER_MESSAGE;
            String actual = steeringContent(requests.get(1));
            System.out.println("  [info]   引导消息内容精确匹配 (" + expected + "): "
                    + (expected.equals(actual) ? "满足" : "不满足，实际=" + actual));
        }
        if (!requests.isEmpty()) {
            System.out.println("  [info]   第 1 次模型请求不含引导消息（steer 不影响当前轮次）: "
                    + (steeringContent(requests.get(0)) == null ? "满足" : "不满足"));
        }

        boolean pass = outputConverged && secondRequestSawSteering;
        System.out.println();
        System.out.println("  判定结果: " + (pass ? "PASS" : "FAIL"));
        return pass;
    }

    // ===================== Scripted fake model =====================

    /**
     * 脚本化的确定性 fake 模型入口（{@link Model.ModelInvoker}）。
     *
     * <p>脚本：第 1 次调用先 sleep {@value #FIRST_CALL_THINKING_MILLIS}ms 模拟长思考
     * （给主线程留出 steer 窗口），然后返回 {@value #DRAFT_ANSWER}；第 2 次起返回
     * {@value #FINAL_ANSWER}。同时捕获每次收到的完整消息列表，用于 [STEERING]
     * 可见性检测与证据输出。
     */
    private static final class ScriptedSteeringInvoker implements Model.ModelInvoker {
        private final List<String> timeline;
        private final long startNanos;
        private final CountDownLatch firstCallEntered = new CountDownLatch(1);
        private final AtomicInteger callCounter = new AtomicInteger();
        private final List<List<BaseMessage>> capturedRequests = new CopyOnWriteArrayList<>();

        private ScriptedSteeringInvoker(List<String> timeline, long startNanos) {
            this.timeline = timeline;
            this.startNanos = startNanos;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages,
                ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig,
                ModelInvokeOptions options) {
            int callIndex = callCounter.incrementAndGet();
            capturedRequests.add(List.copyOf(messages));
            if (callIndex == 1) {
                firstCallEntered.countDown();
            }
            timeline.add(elapsed(startNanos) + " [model] 第 " + callIndex + " 次调用进入, 消息数=" + messages.size());
            if (callIndex > 1) {
                timeline.add(elapsed(startNanos) + " [model] 第 " + callIndex + " 次调用返回: " + FINAL_ANSWER);
                return CompletableFuture.completedFuture(AssistantMessage.builder()
                        .content(FINAL_ANSWER).finishReason("stop").build());
            }
            try {
                // 模拟真实 LLM 的长思考：此窗口内主线程注入 steer，调用本身不受影响。
                Thread.sleep(FIRST_CALL_THINKING_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                CompletableFuture<AssistantMessage> failed = new CompletableFuture<>();
                failed.completeExceptionally(ex);
                return failed;
            }
            timeline.add(elapsed(startNanos) + " [model] 第 1 次调用返回（steer 未打断本轮）: " + DRAFT_ANSWER);
            return CompletableFuture.completedFuture(AssistantMessage.builder()
                    .content(DRAFT_ANSWER).finishReason("stop").build());
        }

        boolean awaitFirstCallEntered(long timeoutSeconds) throws InterruptedException {
            return firstCallEntered.await(timeoutSeconds, TimeUnit.SECONDS);
        }

        List<List<BaseMessage>> capturedRequests() {
            return List.copyOf(capturedRequests);
        }
    }

    // ===================== Misc helpers =====================

    /** 距示例启动 startNanos 的耗时（毫秒）文本。 */
    private static String elapsed(long startNanos) {
        return String.format("%6d ms", (System.nanoTime() - startNanos) / 1_000_000);
    }

    /**
     * 找到请求中真正的引导消息内容：user 角色、内容以 {@value #STEERING_PREFIX} 开头。
     * 系统提示等普通文本中偶然出现 "[STEERING]" 字样不算引导消息；没有则返回 null。
     */
    private static String steeringContent(List<BaseMessage> request) {
        for (BaseMessage message : request) {
            if (!"user".equals(message.getRole())) {
                continue;
            }
            String content = String.valueOf(message.getContent());
            if (content.startsWith(STEERING_PREFIX)) {
                return content;
            }
        }
        return null;
    }

    private static String describeRoles(List<BaseMessage> request) {
        StringBuilder roles = new StringBuilder("[");
        for (int i = 0; i < request.size(); i++) {
            if (i > 0) {
                roles.append(", ");
            }
            roles.append(request.get(i).getRole());
        }
        return roles.append("]").toString();
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').replace('\r', ' ');
        if (oneLine.length() <= maxLen) {
            return oneLine;
        }
        return oneLine.substring(0, maxLen) + "...";
    }
}
