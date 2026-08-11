/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * OpenJiuwen 运行时的统一线程池入口。
 *
 * <p>共享线程池和模块专用线程池均由本类创建，以统一线程命名、异常记录和 JVM 退出时的资源回收。</p>
 *
 * @since 0.1.13
 */
public final class OpenJiuwenExecutors {
    private static final String TOOL_CALL_MAX_SIZE_PROPERTY = "openjiuwen.executor.tool-call.max-size";
    private static final String TOOL_CALL_MAX_SIZE_ENV = "OPENJIUWEN_EXECUTOR_TOOL_CALL_MAX_SIZE";
    private static final String TOOL_CALL_KEEP_ALIVE_PROPERTY = "openjiuwen.executor.tool-call.keep-alive-seconds";
    private static final String TOOL_CALL_KEEP_ALIVE_ENV = "OPENJIUWEN_EXECUTOR_TOOL_CALL_KEEP_ALIVE_SECONDS";
    private static final String TOOL_CALL_TIMEOUT_PROPERTY = "openjiuwen.executor.tool-call.timeout-millis";
    private static final String TOOL_CALL_TIMEOUT_ENV = "OPENJIUWEN_EXECUTOR_TOOL_CALL_TIMEOUT_MILLIS";

    private static final String BACKGROUND_MAX_SIZE_PROPERTY = "openjiuwen.executor.background.max-size";
    private static final String BACKGROUND_MAX_SIZE_ENV = "OPENJIUWEN_EXECUTOR_BACKGROUND_MAX_SIZE";
    private static final String BACKGROUND_KEEP_ALIVE_PROPERTY = "openjiuwen.executor.background.keep-alive-seconds";
    private static final String BACKGROUND_KEEP_ALIVE_ENV = "OPENJIUWEN_EXECUTOR_BACKGROUND_KEEP_ALIVE_SECONDS";

    private static final int DEFAULT_KEEP_ALIVE_SECONDS = 60;
    private static final int DEFAULT_TOOL_CALL_TIMEOUT_MILLIS = 0;
    private static final long SHUTDOWN_AWAIT_SECONDS = 5L;

    private static final Set<ExecutorService> MANAGED_EXECUTORS = ConcurrentHashMap.newKeySet();

    private static final ExecutorService TOOL_CALL_EXECUTOR = buildSharedExecutor(
            TOOL_CALL_MAX_SIZE_PROPERTY,
            TOOL_CALL_MAX_SIZE_ENV,
            TOOL_CALL_KEEP_ALIVE_PROPERTY,
            TOOL_CALL_KEEP_ALIVE_ENV,
            "openjiuwen-tool-call"
    );
    private static final ExecutorService BACKGROUND_EXECUTOR = buildSharedExecutor(
            BACKGROUND_MAX_SIZE_PROPERTY,
            BACKGROUND_MAX_SIZE_ENV,
            BACKGROUND_KEEP_ALIVE_PROPERTY,
            BACKGROUND_KEEP_ALIVE_ENV,
            "openjiuwen-background"
    );

    static {
        Thread shutdownHook = namedThreadFactory("openjiuwen-executors-shutdown", false)
                .newThread(OpenJiuwenExecutors::shutdownAll);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /**
     * 禁止外部实例化该工具类。
     */
    private OpenJiuwenExecutors() {
    }

    /**
     * 获取同一轮工具或能力调用使用的共享线程池。
     *
     * @return 共享工具调用线程池
     */
    public static ExecutorService toolCallExecutor() {
        return TOOL_CALL_EXECUTOR;
    }

    /**
     * 获取未指定执行器的异步任务使用的共享后台线程池。
     *
     * @return 共享后台线程池
     */
    public static ExecutorService backgroundExecutor() {
        return BACKGROUND_EXECUTOR;
    }

    /**
     * 将任务提交到共享工具调用线程池中执行。
     *
     * @param supplier 待执行任务
     * @param <T> 任务结果类型
     * @return 异步执行结果
     */
    public static <T> CompletableFuture<T> supplyToolCallAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, TOOL_CALL_EXECUTOR);
    }

    /**
     * 将任务提交到共享后台线程池中执行。
     *
     * @param supplier 待执行任务
     * @param <T> 任务结果类型
     * @return 异步执行结果
     */
    public static <T> CompletableFuture<T> supplyBackgroundAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, BACKGROUND_EXECUTOR);
    }

    /**
     * 将无返回值任务提交到共享后台线程池中执行。
     *
     * @param runnable 待执行任务
     * @return 异步执行结果
     */
    public static CompletableFuture<Void> runBackgroundAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, BACKGROUND_EXECUTOR);
    }

    /**
     * 创建实例专用的有界模块线程池，并纳入统一资源回收。
     *
     * <p>最大线程数与队列容量可通过系统属性 {@code openjiuwen.executor.{模块名}.max-size} /
     * {@code openjiuwen.executor.{模块名}.queue-size} 或对应环境变量覆盖。</p>
     *
     * @param threadNamePrefix 线程名称前缀（与模块名一致，如 {@code pregel-task}）
     * @param isDaemon 是否创建守护线程
     * @return 有界线程池
     * @since 0.1.14
     */
    public static ExecutorService newBoundedModulePool(String threadNamePrefix, boolean isDaemon) {
        ModulePoolDefaults defaults = ModulePoolDefaults.forPrefix(threadNamePrefix);
        return newBoundedModulePool(threadNamePrefix, defaults.resolveMaxSize(), defaults.queueCapacity(), isDaemon);
    }

    /**
     * 创建实例专用的有界模块线程池，并纳入统一资源回收。
     *
     * @param threadNamePrefix 线程名称前缀
     * @param defaultMaxSize 默认最大线程数（可被系统属性/环境变量覆盖）
     * @param defaultQueueCapacity 默认队列容量（可被系统属性/环境变量覆盖）
     * @param isDaemon 是否创建守护线程
     * @return 有界线程池
     * @since 0.1.14
     */
    public static ExecutorService newBoundedModulePool(String threadNamePrefix, int defaultMaxSize,
            int defaultQueueCapacity, boolean isDaemon) {
        Objects.requireNonNull(threadNamePrefix, "threadNamePrefix");
        validatePositive(defaultMaxSize, "defaultMaxSize");
        validatePositive(defaultQueueCapacity, "defaultQueueCapacity");
        int maxSize = moduleIntSetting(threadNamePrefix, "max-size", defaultMaxSize, 1);
        int queueCapacity = moduleIntSetting(threadNamePrefix, "queue-size", defaultQueueCapacity, 1);
        ModulePoolDefaults defaults = ModulePoolDefaults.forPrefix(threadNamePrefix);
        boolean directHandoff = defaults.isDirectHandoff();
        BlockingQueue<Runnable> workQueue = directHandoff
                ? new SynchronousQueue<>()
                : new ArrayBlockingQueue<>(queueCapacity);
        // core=0 + ArrayBlockingQueue: JDK pools queue first and may create only one worker until the
        // queue is full (serial long tasks when burst < queue capacity). Direct-handoff pools use core=0;
        // bounded-queue pools use core=max so max workers stay hot and the queue is overflow only.
        int corePoolSize = directHandoff ? 0 : maxSize;
        return newThreadPool(threadNamePrefix, ThreadPoolConfig.builder()
                .poolSize(corePoolSize, maxSize)
                .keepAlive(DEFAULT_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS)
                .workQueue(workQueue)
                .isDaemon(isDaemon)
                .rejectionHandler(defaults.rejectionHandler())
                .build());
    }

    /**
     * 创建实例专用的缓存线程池，并纳入统一资源回收。
     *
     * @param threadNamePrefix 线程名称前缀
     * @param isDaemon 是否创建守护线程
     * @return 有界模块线程池（自 0.1.14 起不再无界）
     * @deprecated 请使用 {@link #newBoundedModulePool(String, boolean)}
     */
    @Deprecated(since = "0.1.14")
    public static ExecutorService newCachedThreadPool(String threadNamePrefix, boolean isDaemon) {
        return newBoundedModulePool(threadNamePrefix, isDaemon);
    }

    /**
     * 创建实例专用的固定大小线程池，并纳入统一资源回收。
     *
     * @param threadNamePrefix 线程名称前缀
     * @param size 线程数
     * @param isDaemon 是否创建守护线程
     * @return 固定大小线程池
     */
    public static ExecutorService newFixedThreadPool(String threadNamePrefix, int size, boolean isDaemon) {
        validatePositive(size, "size");
        return newThreadPool(threadNamePrefix, ThreadPoolConfig.builder()
                .poolSize(size, size)
                .keepAlive(0L, TimeUnit.MILLISECONDS)
                .workQueue(new LinkedBlockingQueue<>())
                .isDaemon(isDaemon)
                .rejectionHandler(new ThreadPoolExecutor.AbortPolicy())
                .build());
    }

    /**
     * 创建实例专用的单线程执行器，并纳入统一资源回收。
     *
     * @param threadNamePrefix 线程名称前缀
     * @param isDaemon 是否创建守护线程
     * @return 单线程执行器
     */
    public static ExecutorService newSingleThreadExecutor(String threadNamePrefix, boolean isDaemon) {
        return newFixedThreadPool(threadNamePrefix, 1, isDaemon);
    }

    /**
     * 创建实例专用的定时线程池，并纳入统一资源回收。
     *
     * @param threadNamePrefix 线程名称前缀
     * @param corePoolSize 核心线程数
     * @param isDaemon 是否创建守护线程
     * @return 定时线程池
     */
    public static ScheduledExecutorService newScheduledThreadPool(String threadNamePrefix, int corePoolSize,
            boolean isDaemon) {
        validatePositive(corePoolSize, "corePoolSize");
        return newScheduledThreadPool(corePoolSize, namedThreadFactory(threadNamePrefix, isDaemon));
    }

    /**
     * 使用调用方提供的线程工厂创建实例专用定时线程池。
     *
     * @param corePoolSize 核心线程数
     * @param threadFactory 线程工厂
     * @return 定时线程池
     */
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize, ThreadFactory threadFactory) {
        validatePositive(corePoolSize, "corePoolSize");
        ManagedScheduledThreadPoolExecutor executor = new ManagedScheduledThreadPoolExecutor(corePoolSize,
                Objects.requireNonNull(threadFactory, "threadFactory"));
        return register(executor);
    }

    /**
     * 创建参数可定制的实例专用线程池，并纳入统一资源回收。
     *
     * @param threadNamePrefix 线程名称前缀
     * @param config 线程池配置
     * @return 线程池
     */
    public static ExecutorService newThreadPool(String threadNamePrefix, ThreadPoolConfig config) {
        Objects.requireNonNull(config, "config");
        ManagedThreadPoolExecutor executor = new ManagedThreadPoolExecutor(config.corePoolSize,
                config.maximumPoolSize, config.keepAliveTime, Objects.requireNonNull(config.unit, "unit"),
                Objects.requireNonNull(config.workQueue, "workQueue"), namedThreadFactory(threadNamePrefix,
                config.isDaemon), Objects.requireNonNull(config.rejectionHandler, "rejectionHandler"));
        return register(executor);
    }

    /**
     * 自定义线程池的创建配置。
     *
     * <p>使用构建器逐项设置，避免调用方依赖多个位置参数的顺序。</p>
     *
     * @since 0.1.13
     */
    public static final class ThreadPoolConfig {
        private final int corePoolSize;
        private final int maximumPoolSize;
        private final long keepAliveTime;
        private final TimeUnit unit;
        private final BlockingQueue<Runnable> workQueue;
        private final boolean isDaemon;
        private final RejectedExecutionHandler rejectionHandler;

        private ThreadPoolConfig(Builder builder) {
            this.corePoolSize = builder.corePoolSize;
            this.maximumPoolSize = builder.maximumPoolSize;
            this.keepAliveTime = builder.keepAliveTime;
            this.unit = builder.unit;
            this.workQueue = builder.workQueue;
            this.isDaemon = builder.isDaemon;
            this.rejectionHandler = builder.rejectionHandler;
        }

        /**
         * 创建线程池配置构建器。
         *
         * @return 配置构建器
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * 线程池配置构建器。
         */
        public static final class Builder {
            private int corePoolSize;
            private int maximumPoolSize;
            private long keepAliveTime;
            private TimeUnit unit;
            private BlockingQueue<Runnable> workQueue;
            private boolean isDaemon;
            private RejectedExecutionHandler rejectionHandler;

            private Builder() {
            }

            /**
             * 设置核心和最大线程数。
             *
             * @param corePoolSize 核心线程数
             * @param maximumPoolSize 最大线程数
             * @return 当前构建器
             */
            public Builder poolSize(int corePoolSize, int maximumPoolSize) {
                this.corePoolSize = corePoolSize;
                this.maximumPoolSize = maximumPoolSize;
                return this;
            }

            /**
             * 设置空闲线程保留时间。
             *
             * @param keepAliveTime 保留时间
             * @param unit 时间单位
             * @return 当前构建器
             */
            public Builder keepAlive(long keepAliveTime, TimeUnit unit) {
                this.keepAliveTime = keepAliveTime;
                this.unit = unit;
                return this;
            }

            /**
             * 设置工作队列。
             *
             * @param workQueue 工作队列
             * @return 当前构建器
             */
            public Builder workQueue(BlockingQueue<Runnable> workQueue) {
                this.workQueue = workQueue;
                return this;
            }

            /**
             * 设置线程是否为守护线程。
             *
             * @param isDaemon 是否创建守护线程
             * @return 当前构建器
             */
            public Builder isDaemon(boolean isDaemon) {
                this.isDaemon = isDaemon;
                return this;
            }

            /**
             * 设置线程池饱和时的拒绝策略。
             *
             * @param rejectionHandler 拒绝策略
             * @return 当前构建器
             */
            public Builder rejectionHandler(RejectedExecutionHandler rejectionHandler) {
                this.rejectionHandler = rejectionHandler;
                return this;
            }

            /**
             * 构建线程池配置。
             *
             * @return 线程池配置
             */
            public ThreadPoolConfig build() {
                return new ThreadPoolConfig(this);
            }
        }
    }

    /**
     * 在启用工具调用超时配置时，为异步任务追加等待结果的超时控制。
     *
     * <p>超时只会使 Future 以异常完成，不会中断底层已开始执行的工具任务。</p>
     *
     * @param future 异步执行结果
     * @param <T> 任务结果类型
     * @return 追加超时控制后的异步执行结果
     */
    public static <T> CompletableFuture<T> withToolCallTimeout(CompletableFuture<T> future) {
        long timeoutMillis = toolCallTimeoutMillis();
        if (timeoutMillis <= 0) {
            return future;
        }
        return future.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 读取单次工具或能力调用的超时时间，非正数表示不启用超时。
     *
     * @return 超时时间，单位为毫秒
     */
    public static long toolCallTimeoutMillis() {
        return intSetting(
                TOOL_CALL_TIMEOUT_PROPERTY,
                TOOL_CALL_TIMEOUT_ENV,
                DEFAULT_TOOL_CALL_TIMEOUT_MILLIS,
                0
        );
    }

    /**
     * 关闭当前类登记的全部线程池，并在有限时间内等待执行中的任务结束。
     */
    public static void shutdownAll() {
        shutdownExecutors(List.copyOf(MANAGED_EXECUTORS), SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 关闭指定线程池，并在超时后中断仍在执行的任务。
     *
     * @param executors 待关闭的线程池
     * @param timeout 等待超时时间
     * @param unit 超时时间单位
     */
    static void shutdownExecutors(List<? extends ExecutorService> executors, long timeout, TimeUnit unit) {
        Objects.requireNonNull(executors, "executors");
        Objects.requireNonNull(unit, "unit");
        for (ExecutorService executor : executors) {
            executor.shutdown();
        }

        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        for (ExecutorService executor : executors) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                break;
            }
            try {
                if (!executor.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Loggers.COMMON.warning("Interrupted while waiting for executors to terminate");
                break;
            }
        }
        for (ExecutorService executor : executors) {
            if (!executor.isTerminated()) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * 创建可配置的共享业务线程池。
     *
     * @param maxSizeProperty 最大线程数的系统属性名
     * @param maxSizeEnv 最大线程数的环境变量名
     * @param keepAliveProperty 空闲线程保留时间的系统属性名
     * @param keepAliveEnv 空闲线程保留时间的环境变量名
     * @param threadNamePrefix 线程名称前缀
     * @return 配置完成的共享线程池
     */
    private static ExecutorService buildSharedExecutor(String maxSizeProperty, String maxSizeEnv,
            String keepAliveProperty, String keepAliveEnv, String threadNamePrefix) {
        int maxSize = intSetting(maxSizeProperty, maxSizeEnv, defaultParallelMaxSize(), 1);
        int keepAliveSeconds = intSetting(keepAliveProperty, keepAliveEnv, DEFAULT_KEEP_ALIVE_SECONDS, 1);
        return newThreadPool(threadNamePrefix, ThreadPoolConfig.builder()
                .poolSize(0, maxSize)
                .keepAlive(keepAliveSeconds, TimeUnit.SECONDS)
                .workQueue(new SynchronousQueue<>())
                .isDaemon(true)
                .rejectionHandler(new ThreadPoolExecutor.CallerRunsPolicy())
                .build());
    }

    /**
     * 登记线程池，使 JVM 退出时能够统一关闭。
     *
     * @param executor 待登记的线程池
     * @param <T> 线程池类型
     * @return 已登记的线程池
     */
    private static <T extends ExecutorService> T register(T executor) {
        MANAGED_EXECUTORS.add(executor);
        return executor;
    }

    /**
     * 根据当前机器 CPU 数量计算共享并行线程池的默认最大线程数。
     *
     * @return 默认最大线程数
     */
    private static int defaultParallelMaxSize() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(8, processors * 2);
    }

    /**
     * DeepAgent stream 会话池默认上限：I/O 型 workload，按 {@code max(16, CPU 核数 × 4)} 估算并发 session 槽位。
     *
     * @return 默认最大线程数
     * @since 0.1.14
     */
    static int defaultDeepAgentStreamMaxSize() {
        return Math.max(16, Runtime.getRuntime().availableProcessors() * 4);
    }

    /**
     * 创建带统一名称和异常日志的线程工厂。
     *
     * @param threadNamePrefix 线程名称前缀
     * @param isDaemon 是否创建守护线程
     * @return 配置完成的线程工厂
     */
    private static ThreadFactory namedThreadFactory(String threadNamePrefix, boolean isDaemon) {
        AtomicInteger index = new AtomicInteger();
        ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();
        return runnable -> {
            Thread thread = defaultThreadFactory.newThread(runnable);
            thread.setName(threadNamePrefix + "-" + index.incrementAndGet());
            thread.setDaemon(isDaemon);
            thread.setUncaughtExceptionHandler((ignoredThread, error) ->
                    Loggers.COMMON.error("Uncaught exception in {}: {}", ignoredThread.getName(), error.getMessage()));
            return thread;
        };
    }

    /**
     * 校验线程池大小参数。
     *
     * @param value 待校验的参数值
     * @param name 参数名称
     */
    private static void validatePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }

    /**
     * 按系统属性优先、环境变量兜底的顺序读取整数配置，并限制最小值。
     *
     * @param propertyName 系统属性名
     * @param envName 环境变量名
     * @param defaultValue 默认值
     * @param minValue 允许的最小值
     * @return 解析后的配置值
     */
    private static int intSetting(String propertyName, String envName, int defaultValue, int minValue) {
        String raw = System.getProperty(propertyName);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(envName);
        }
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(value, minValue);
        } catch (NumberFormatException e) {
            Loggers.COMMON.warning("Invalid integer for {} / {}: {}", propertyName, envName, raw);
            return defaultValue;
        }
    }

    private static int moduleIntSetting(String modulePrefix, String suffix, int defaultValue, int minValue) {
        String propertyName = "openjiuwen.executor." + modulePrefix + "." + suffix;
        String envName = moduleEnvName(modulePrefix, suffix);
        return intSetting(propertyName, envName, defaultValue, minValue);
    }

    private static String moduleEnvName(String modulePrefix, String suffix) {
        return "OPENJIUWEN_EXECUTOR_"
                + modulePrefix.toUpperCase(Locale.ROOT).replace('-', '_')
                + "_"
                + suffix.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    /**
     * 各模块线程池默认上限（维度 I-B：无界池整改）。
     */
    private enum ModulePoolDefaults {
        PREGEL_TASK("pregel-task", 32, 512, true),
        WORKFLOW_STREAM("workflow-stream", 16, 256, false),
        VERTEX_STREAM("vertex-stream", 8, 256, true),
        STREAM_ACTOR("stream-actor", 8, 256, true),
        END_TEMPLATE_RENDER("end-template-render", 8, 128, false),
        CALLBACK_PARALLEL("callback-parallel", 16, 256, true),
        MQ_SERVER_ADAPTER("mq-server-adapter", 8, 128, false),
        TASK_MANAGER_WORKER("task-manager-worker", 16, 512, true),
        DEEP_AGENT_STREAM("deep-agent-stream", 16, 128, true),
        GENERIC("", 16, 256, false);

        private final String prefix;
        private final int maxSize;
        private final int queueCapacity;
        private final boolean isDirectHandoff;

        ModulePoolDefaults(String prefix, int maxSize, int queueCapacity, boolean isDirectHandoff) {
            this.prefix = prefix;
            this.maxSize = maxSize;
            this.queueCapacity = queueCapacity;
            this.isDirectHandoff = isDirectHandoff;
        }

        int resolveMaxSize() {
            if (this == DEEP_AGENT_STREAM) {
                return defaultDeepAgentStreamMaxSize();
            }
            return maxSize;
        }

        int queueCapacity() {
            return queueCapacity;
        }

        boolean isDirectHandoff() {
            return isDirectHandoff;
        }

        RejectedExecutionHandler rejectionHandler() {
            if (this == DEEP_AGENT_STREAM) {
                return new ThreadPoolExecutor.CallerRunsPolicy();
            }
            return new ThreadPoolExecutor.AbortPolicy();
        }

        static ModulePoolDefaults forPrefix(String threadNamePrefix) {
            for (ModulePoolDefaults defaults : values()) {
                if (defaults.prefix.equals(threadNamePrefix)) {
                    return defaults;
                }
            }
            return GENERIC;
        }
    }

    /**
     * 在线程池终止后自动解除登记。
     */
    private static final class ManagedThreadPoolExecutor extends ThreadPoolExecutor {
        private ManagedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
                RejectedExecutionHandler rejectionHandler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, rejectionHandler);
        }

        @Override
        protected void terminated() {
            MANAGED_EXECUTORS.remove(this);
            super.terminated();
        }
    }

    /**
     * 在线程池终止后自动解除登记的定时线程池。
     */
    private static final class ManagedScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {
        private ManagedScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
            super(corePoolSize, threadFactory);
        }

        @Override
        protected void terminated() {
            MANAGED_EXECUTORS.remove(this);
            super.terminated();
        }
    }
}
