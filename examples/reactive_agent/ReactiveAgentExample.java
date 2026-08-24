package examples.reactive_agent;

import com.openjiuwen.core.common.reactive.ReactiveAdapters;
import com.openjiuwen.core.runner.Runner;
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
 * 响应式接口端到端示例（纯 Core，无 Spring 依赖）。
 *
 * <p>对齐文档 {@code documents/zh/2.开发指南/高阶用法/响应式接口（Reactor）.md}：
 * {@link ReactiveAdapters}、{@code BaseAgent.invokeAsync/streamAsync}、
 * {@link Runner#runAgentAsync}/{@link Runner#runAgentStreamingAsync}。
 *
 * <p>本仓库不为该示例提供独立 pom；编译运行方式见同目录 README.md。
 */
public class ReactiveAgentExample {

    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BOLD = "\u001B[1m";
    private static final String RESET = "\u001B[0m";

    public static void main(String[] args) throws Exception {
        banner("响应式接口（Reactor）端到端示例");
        scenario1_fromCallable();
        scenario2_fromRunnable();
        scenario3_fromIteratorAndCallableIterator();
        scenario4_fromAutoCloseableIterator();
        scenario5_agentInvokeAsync();
        scenario6_agentStreamAsync();
        scenario7_runnerRunAgentAsync();
        scenario8_runnerRunAgentStreamingAsync();
        banner("所有场景验证完成");
        System.exit(0);
    }

    // ─── 场景 1：ReactiveAdapters.fromCallable ──────────────────────────────────

    static void scenario1_fromCallable() throws Exception {
        header("场景 1｜ReactiveAdapters.fromCallable — 阻塞调用包成 Mono");
        print("  接口: Mono<T> fromCallable(Callable<T>)");
        print("  要点: Callable 跑在 boundedElastic，主线程 subscribe 后立刻返回\n");

        Mono<String> mono = ReactiveAdapters.fromCallable(() -> {
            print(YELLOW + "    [Callable 线程] " + Thread.currentThread().getName() + RESET);
            Thread.sleep(100);
            return "同步结果已包成 Mono";
        });

        CountDownLatch latch = new CountDownLatch(1);
        print(YELLOW + "    [订阅前 / 主线程] " + Thread.currentThread().getName() + RESET);

        mono.subscribe(
                v -> {
                    print(GREEN + "    onNext: " + v + RESET);
                    latch.countDown();
                },
                ex -> {
                    print("    失败: " + ex.getMessage());
                    latch.countDown();
                }
        );

        print(YELLOW + "    [subscribe 后主线程立即继续，不阻塞]" + RESET);
        await(latch, 5);
        println();
    }

    // ─── 场景 2：ReactiveAdapters.fromRunnable ──────────────────────────────────

    static void scenario2_fromRunnable() throws Exception {
        header("场景 2｜ReactiveAdapters.fromRunnable — 无返回值阻塞操作包成 Mono<Void>");
        print("  接口: Mono<Void> fromRunnable(Runnable)\n");

        AtomicBoolean ran = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        ReactiveAdapters.fromRunnable(() -> {
            ran.set(true);
            print(YELLOW + "    [Runnable 线程] " + Thread.currentThread().getName() + RESET);
        }).subscribe(
                unused -> {
                },
                ex -> {
                    print("    失败: " + ex.getMessage());
                    latch.countDown();
                },
                () -> {
                    print(GREEN + "    Mono<Void> 完成, ran=" + ran.get() + RESET);
                    latch.countDown();
                }
        );

        await(latch, 5);
        println();
    }

    // ─── 场景 3：fromIterator / fromCallableIterator ────────────────────────────

    static void scenario3_fromIteratorAndCallableIterator() throws Exception {
        header("场景 3｜fromIterator / fromCallableIterator — Iterator 转 Flux");
        print("  接口: Flux<T> fromIterator(Iterator, cleanup)");
        print("  接口: Flux<T> fromCallableIterator(Callable<Iterator>, cleanup)\n");

        AtomicBoolean cleanupCalled = new AtomicBoolean(false);
        CountDownLatch latch1 = new CountDownLatch(1);
        ReactiveAdapters.fromIterator(List.of("a", "b", "c").iterator(), () -> cleanupCalled.set(true))
                .collectList()
                .doFinally(signal -> latch1.countDown())
                .subscribe(
                        list -> print(GREEN + "    fromIterator: " + list + RESET),
                        ex -> print("    失败: " + ex.getMessage())
                );
        await(latch1, 5);
        print(GREEN + "    fromIterator cleanup=" + cleanupCalled.get() + RESET);

        AtomicInteger prepCalls = new AtomicInteger();
        CountDownLatch latch2 = new CountDownLatch(1);
        ReactiveAdapters.fromCallableIterator(() -> {
                    prepCalls.incrementAndGet();
                    return List.of("x", "y").iterator();
                })
                .collectList()
                .subscribe(
                        list -> {
                            print(GREEN + "    fromCallableIterator: " + list
                                    + ", prepCalls=" + prepCalls.get() + RESET);
                            latch2.countDown();
                        },
                        ex -> {
                            print("    失败: " + ex.getMessage());
                            latch2.countDown();
                        }
                );
        await(latch2, 5);
        println();
    }

    // ─── 场景 4：fromAutoCloseableIterator（取消时 close） ───────────────────────

    static void scenario4_fromAutoCloseableIterator() throws Exception {
        header("场景 4｜ReactiveAdapters.fromAutoCloseableIterator — 取消时自动 close()");
        print("  接口: Flux<T> fromAutoCloseableIterator(Callable<Iterator>)");
        print("  要点: take(N) / dispose 后触发 AutoCloseable.close()，避免连接泄漏\n");

        AtomicBoolean closeCalled = new AtomicBoolean(false);

        class SseIterator implements Iterator<String>, AutoCloseable {
            private final String[] tokens = {"token-A ", "token-B ", "token-C ", "token-D ", "token-E"};
            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < tokens.length;
            }

            @Override
            public String next() {
                return tokens[i++];
            }

            @Override
            public void close() {
                closeCalled.set(true);
                print(YELLOW + "    [AutoCloseable.close()] 底层连接已关闭" + RESET);
            }
        }

        CountDownLatch latch = new CountDownLatch(1);
        ReactiveAdapters.fromAutoCloseableIterator(SseIterator::new)
                .take(3)
                .subscribe(
                        chunk -> print(GREEN + "    chunk: " + chunk + RESET),
                        ex -> {
                            print("    失败: " + ex.getMessage());
                            latch.countDown();
                        },
                        () -> {
                            print(GREEN + "    Flux 完成（take(3) 取消后续）" + RESET);
                            latch.countDown();
                        }
                );
        await(latch, 5);
        Thread.sleep(100);

        print(closeCalled.get()
                ? GREEN + "    AutoCloseable.close() 已被调用" + RESET
                : "    close() 未调用！");
        println();
    }

    // ─── 场景 5：BaseAgent.invokeAsync（文档主示例） ─────────────────────────────

    static void scenario5_agentInvokeAsync() throws Exception {
        header("场景 5｜BaseAgent.invokeAsync — Agent 单次调用返回 Mono");
        print("  接口: Mono<Object> agent.invokeAsync(inputs, session)");
        print("  要点: 与同步 invoke 并存；订阅前不执行，执行在 boundedElastic\n");

        SimpleReactiveAgentExample agent = new SimpleReactiveAgentExample();
        Mono<Object> result = agent.invokeAsync(Map.of("query", "你好，invokeAsync！"), null);

        print(YELLOW + "    [invokeAsync 返回，Agent 尚未运行]" + RESET);
        CountDownLatch latch = new CountDownLatch(1);
        result.subscribe(
                v -> {
                    print(GREEN + "    Agent 结果: " + v + RESET);
                    latch.countDown();
                },
                ex -> {
                    print("    失败: " + ex.getMessage());
                    latch.countDown();
                }
        );
        print(YELLOW + "    [subscribe 后主线程继续]" + RESET);
        await(latch, 5);
        println();
    }

    // ─── 场景 6：BaseAgent.streamAsync ──────────────────────────────────────────

    static void scenario6_agentStreamAsync() throws Exception {
        header("场景 6｜BaseAgent.streamAsync — Agent 流式输出返回 Flux");
        print("  接口: Flux<Object> agent.streamAsync(inputs, session, streamModes)\n");

        SimpleReactiveAgentExample agent = new SimpleReactiveAgentExample();
        Flux<Object> flux = agent.streamAsync(
                Map.of("query", "你好，streamAsync！"),
                null,
                List.of(StreamMode.OUTPUT)
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();
        flux.subscribe(
                chunk -> {
                    count.incrementAndGet();
                    print(GREEN + "    [" + count.get() + "] " + chunk + RESET);
                },
                ex -> {
                    print("    失败: " + ex.getMessage());
                    latch.countDown();
                },
                () -> {
                    print(GREEN + "    流结束，共 " + count.get() + " 块" + RESET);
                    latch.countDown();
                }
        );
        await(latch, 15);
        println();
    }

    // ─── 场景 7：Runner.runAgentAsync（静态入口，文档示例） ───────────────────────

    static void scenario7_runnerRunAgentAsync() throws Exception {
        header("场景 7｜Runner.runAgentAsync — 静态入口单次调用");
        print("  接口: Mono<Object> Runner.runAgentAsync(agent, inputs, session, ctx, envs)\n");

        SimpleReactiveAgentExample agent = new SimpleReactiveAgentExample();
        CountDownLatch latch = new CountDownLatch(1);
        Runner.runAgentAsync(agent, Map.of("query", "你好，Runner Mono！"), null, null, null)
                .subscribe(
                        v -> {
                            print(GREEN + "    Runner 结果: " + v + RESET);
                            latch.countDown();
                        },
                        ex -> {
                            print("    失败: " + ex.getMessage());
                            latch.countDown();
                        }
                );
        await(latch, 10);
        println();
    }

    // ─── 场景 8：Runner.runAgentStreamingAsync ──────────────────────────────────

    static void scenario8_runnerRunAgentStreamingAsync() throws Exception {
        header("场景 8｜Runner.runAgentStreamingAsync — 静态入口流式输出");
        print("  接口: Flux<Object> Runner.runAgentStreamingAsync(...)\n");

        SimpleReactiveAgentExample agent = new SimpleReactiveAgentExample();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();
        Runner.runAgentStreamingAsync(
                        agent,
                        Map.of("query", "你好，Runner Flux！"),
                        null,
                        null,
                        List.of(StreamMode.OUTPUT),
                        null)
                .subscribe(
                        chunk -> {
                            count.incrementAndGet();
                            print(GREEN + "    [" + count.get() + "] " + chunk + RESET);
                        },
                        ex -> {
                            print("    失败: " + ex.getMessage());
                            latch.countDown();
                        },
                        () -> {
                            print(GREEN + "    流结束，共 " + count.get() + " 块" + RESET);
                            latch.countDown();
                        }
                );
        await(latch, 15);
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

    static void print(String msg) {
        System.out.println(msg);
    }

    static void println() {
        System.out.println();
    }

    static void await(CountDownLatch latch, long seconds) throws InterruptedException {
        if (!latch.await(seconds, TimeUnit.SECONDS)) {
            throw new IllegalStateException("场景等待超时（" + seconds + "s）");
        }
    }
}
