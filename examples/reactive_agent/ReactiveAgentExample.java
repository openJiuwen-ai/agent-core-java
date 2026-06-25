package examples.reactive_agent;

import com.openjiuwen.core.common.reactive.ReactiveAdapters;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.RunnerImpl;
import com.openjiuwen.core.session.stream.StreamMode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PR #630 响应式接口端到端示例 — 场景 1-4（纯 Core，无 Spring 依赖）
 *
 * <p>本仓库不为该示例提供独立 pom；将本类放入已有应用工程后运行 main 方法。
 */
public class ReactiveAgentExample {

    private static final String CYAN   = "\u001B[36m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BOLD   = "\u001B[1m";
    private static final String RESET  = "\u001B[0m";

    public static void main(String[] args) throws Exception {
        banner("PR #630  响应式接口端到端示例  (场景 1-4)");
        scenario1_fromCallable();
        scenario2_fromAutoCloseableIterator();
        scenario3_agentInvokeMono();
        scenario4_agentStreamFlux();
        banner("✓  所有场景验证完成");
        System.exit(0);
    }

    // ─── 场景 1：ReactiveAdapters.fromCallable ──────────────────────────────────
    // 验证：阻塞调用被包成 Mono 后，subscribe 不阻塞调用线程，执行切换到 boundedElastic

    static void scenario1_fromCallable() throws Exception {
        header("场景 1｜ReactiveAdapters.fromCallable — 阻塞调用包成 Mono");
        print("  接口: Mono<T> fromCallable(Callable<T>)");
        print("  要点: Callable 跑在 boundedElastic 线程，主线程 subscribe 后立刻返回\n");

        Mono<String> mono = ReactiveAdapters.fromCallable(() -> {
            print(YELLOW + "    [Callable 线程] " + Thread.currentThread().getName() + RESET);
            Thread.sleep(100); // 模拟阻塞 IO
            return "同步结果已包成 Mono";
        });

        CountDownLatch latch = new CountDownLatch(1);
        print(YELLOW + "    [订阅前 / 主线程] " + Thread.currentThread().getName() + RESET);

        mono.subscribe(
                v  -> { print(GREEN + "    ✓ onNext: " + v + RESET); latch.countDown(); },
                ex -> { print("    ✗ " + ex.getMessage()); latch.countDown(); }
        );

        print(YELLOW + "    [subscribe 后主线程立即继续，不阻塞]" + RESET);
        latch.await(5, TimeUnit.SECONDS);
        println();
    }

    // ─── 场景 2：ReactiveAdapters.fromAutoCloseableIterator ─────────────────────
    // 验证：客户端取消（cancel）后，底层 AutoCloseable 的 close() 被自动调用，不泄漏连接

    static void scenario2_fromAutoCloseableIterator() throws Exception {
        header("场景 2｜ReactiveAdapters.fromAutoCloseableIterator — SSE Iterator 转 Flux");
        print("  接口: Flux<T> fromAutoCloseableIterator(Callable<Iterator<T>>)");
        print("  要点: 取消订阅（客户端断连）时自动调 close()，释放底层 HTTP 连接\n");

        AtomicBoolean closeCalled = new AtomicBoolean(false);

        // 模拟 SSE 流迭代器：实现 AutoCloseable = 关闭底层 HTTP 读流
        class SseIterator implements Iterator<String>, AutoCloseable {
            private final String[] tokens = {"token-A ", "token-B ", "token-C ", "token-D ", "token-E"};
            private int i = 0;

            @Override public boolean hasNext() { return i < tokens.length; }
            @Override public String next() { return tokens[i++]; }

            @Override
            public void close() {
                closeCalled.set(true);
                print(YELLOW + "    [AutoCloseable.close()] 底层 HTTP 连接已关闭" + RESET);
            }
        }

        Flux<String> flux = ReactiveAdapters.fromAutoCloseableIterator(SseIterator::new);

        // take(3) 后触发 cancel → 期望 close() 被调用
        CountDownLatch latch = new CountDownLatch(1);
        flux.take(3).subscribe(
                chunk -> print(GREEN + "    ✓ chunk: " + chunk + RESET),
                ex    -> { print("    ✗ " + ex.getMessage()); latch.countDown(); },
                ()    -> { print(GREEN + "    ✓ Flux 完成（take(3) 取消后续）" + RESET); latch.countDown(); }
        );
        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(100); // 等 usingWhen asyncCleanup 在 boundedElastic 上跑完

        print(closeCalled.get()
                ? GREEN + "    ✓ AutoCloseable.close() 已被调用，连接无泄漏" + RESET
                : "    ✗ close() 未调用！");
        println();
    }

    // ─── 场景 3：RunnerImpl.runAgentAsync ────────────────────────────────────────
    // 验证：Agent 单次调用走 Mono 路径，执行在 boundedElastic 线程上，不阻塞 Netty event loop

    static void scenario3_agentInvokeMono() throws Exception {
        header("场景 3｜RunnerImpl.runAgentAsync — Agent 单次调用返回 Mono<Object>");
        print("  接口: Mono<Object> runAgentAsync(agent, inputs, session, ctx, envs)");
        print("  要点: WebFlux Controller 直接 return 这个 Mono，不阻塞 Netty event loop\n");

        RunnerImpl runner = new RunnerImpl("demo-runner-3", RunnerConfig.DEFAULT);
        SimpleReactiveAgentExample agent = new SimpleReactiveAgentExample();

        Mono<Object> result = runner.runAgentAsync(
                agent, Map.of("query", "你好，Mono！", "conversation_id", "demo-session-3"),
                null, null, null
        );

        print(YELLOW + "    [runAgentAsync 返回，Agent 尚未运行]" + RESET);

        CountDownLatch latch = new CountDownLatch(1);
        result.subscribe(
                v  -> { print(GREEN + "    ✓ Agent 结果: " + v + RESET); latch.countDown(); },
                ex -> { print("    ✗ " + ex.getMessage()); latch.countDown(); }
        );

        print(YELLOW + "    [subscribe 后主线程继续，Agent 在 boundedElastic 上执行]" + RESET);
        latch.await(5, TimeUnit.SECONDS);
        println();
    }

    // ─── 场景 4：RunnerImpl.runAgentStreamingAsync ────────────────────────────────
    // 验证：Agent 流式输出走 Flux 路径，token 逐块推送；最后一块带线程名，证明迭代在 boundedElastic 上

    static void scenario4_agentStreamFlux() throws Exception {
        header("场景 4｜RunnerImpl.runAgentStreamingAsync — Agent 流式输出返回 Flux<Object>");
        print("  接口: Flux<Object> runAgentStreamingAsync(agent, inputs, session, ctx, modes, envs)");
        print("  要点: 天然背压，WebFlux SSE 直接映射；取消时 postRun 清理保证触发\n");

        RunnerImpl runner = new RunnerImpl("demo-runner-4", RunnerConfig.DEFAULT);
        SimpleReactiveAgentExample agent = new SimpleReactiveAgentExample();

        Flux<Object> flux = runner.runAgentStreamingAsync(
                agent, Map.of("query", "你好，Flux！", "conversation_id", "demo-session-4"),
                null, null, List.of(StreamMode.OUTPUT), null
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();

        flux.subscribe(
                chunk -> {
                    count.incrementAndGet();
                    print(GREEN + "    ✓ [" + count.get() + "] " + chunk + RESET);
                },
                ex -> { print("    ✗ " + ex.getMessage()); latch.countDown(); },
                ()  -> { print(GREEN + "\n    ✓ 流结束，共 " + count.get() + " 块" + RESET); latch.countDown(); }
        );

        latch.await(10, TimeUnit.SECONDS);
        println();
    }

    // ─── 控制台工具 ──────────────────────────────────────────────────────────────

    static void banner(String msg) {
        System.out.println("\n" + BOLD + CYAN
                + "═".repeat(62) + "\n  " + msg + "\n" + "═".repeat(62) + RESET + "\n");
    }

    static void header(String msg) {
        System.out.println(BOLD + CYAN + "┌─ " + msg + RESET);
    }

    static void print(String msg)  { System.out.println(msg); }
    static void println()          { System.out.println(); }
}
