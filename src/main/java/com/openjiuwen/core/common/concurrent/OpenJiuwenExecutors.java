/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
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
 * <p>共享线程池和模块专用线程池均由本类创建，
 * 以统一线程命名、异常记录和 JVM 退出时的资源回收。</p>
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
     * 创建实例专用的有界模块执行器，并纳入统一资源回收。
     *
     * <p>JDK 17 使用有界平台线程池，最大线程数与队列容量可通过系统属性
     * {@code openjiuwen.executor.{模块名}.max-size} / {@code openjiuwen.executor.{模块名}.queue-size}
     * 或对应环境变量覆盖。JDK 21 及以上使用不限制并发的每任务虚拟线程。</p>
     *
     * @param threadNamePrefix 线程名称前缀（与模块名一致，如 {@code pregel-task}）
     * @param isDaemon JDK 17 平台线程是否为守护线程；JDK 21 及以上忽略该参数
     * @return 自适应任务执行器
     * @since 0.1.14
     */
    public static ExecutorService newBoundedModulePool(String threadNamePrefix, boolean isDaemon) {
        ModulePoolDefaults defaults = ModulePoolDefaults.forPrefix(threadNamePrefix);
        return newBoundedModulePool(threadNamePrefix, defaults.resolveMaxSize(), defaults.queueCapacity(), isDaemon);
    }

    /**
     * 创建实例专用的有界模块执行器，并纳入统一资源回收。
     *
     * @param threadNamePrefix 线程名称前缀
     * @param defaultMaxSize 默认最大线程数（可被系统属性/环境变量覆盖）
     * @param defaultQueueCapacity 默认队列容量（可被系统属性/环境变量覆盖）
     * @param isDaemon JDK 17 平台线程是否为守护线程；JDK 21 及以上忽略该参数
     * @return 自适应任务执行器
     * @since 0.1.14
     */
    public static ExecutorService newBoundedModulePool(String threadNamePrefix, int defaultMaxSize,
            int defaultQueueCapacity, boolean isDaemon) {
        Objects.requireNonNull(threadNamePrefix, "threadNamePrefix");
        validatePositive(defaultMaxSize, "defaultMaxSize");
        validatePositive(defaultQueueCapacity, "defaultQueueCapacity");
        if (VirtualThreadSupport.isSupported()) {
            return register(new ManagedVirtualThreadExecutor(threadNamePrefix));
        }
        return register(newBoundedPlatformExecutor(threadNamePrefix, defaultMaxSize, defaultQueueCapacity, isDaemon));
    }

    private static ExecutorService newBoundedPlatformExecutor(String threadNamePrefix, int defaultMaxSize,
            int defaultQueueCapacity, boolean isDaemon) {
        int platformMaxSize = moduleIntSetting(threadNamePrefix, "max-size", defaultMaxSize, 1);
        int queueCapacity = moduleIntSetting(threadNamePrefix, "queue-size", defaultQueueCapacity, 1);
        ModulePoolDefaults defaults = ModulePoolDefaults.forPrefix(threadNamePrefix);
        // 统一排队语义：core=max 使所有线程常热，ArrayBlockingQueue 只做溢出缓冲。
        // JDK 陷阱：core < max + 有界队列时，超过 core 的线程仅在队列满后才创建，
        // 导致 max 永远达不到（长任务被串行化），因此 core 必须等于 max。
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(queueCapacity);
        ThreadPoolExecutor executor = new ManagedThreadPoolExecutor(platformMaxSize, platformMaxSize,
                DEFAULT_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, workQueue,
                namedThreadFactory(threadNamePrefix, isDaemon), defaults.rejectionHandler());
        executor.allowCoreThreadTimeOut(defaults.allowsCoreTimeout());
        return executor;
    }

    /**
     * 创建实例专用的缓存线程池，并纳入统一资源回收。
     *
     * @param threadNamePrefix 线程名称前缀
     * @param isDaemon JDK 17 平台线程是否为守护线程；JDK 21 及以上忽略该参数
     * @return 自适应任务执行器
     * @deprecated 请使用 {@link #newBoundedModulePool(String, boolean)}
     */
    @Deprecated(since = "0.1.14")
    public static ExecutorService newCachedThreadPool(String threadNamePrefix, boolean isDaemon) {
        return newBoundedModulePool(threadNamePrefix, isDaemon);
    }

    /**
     * 创建实例专用的固定并发执行器，并纳入统一资源回收。
     *
     * <p>JDK 17 使用原固定大小平台线程池；JDK 21 及以上使用不限制并发的
     * 每任务虚拟线程，此时 {@code size} 仅用于参数合法性校验，
     * 不限制任务并发。</p>
     *
     * @param threadNamePrefix 线程名称前缀
     * @param size 线程数
     * @param isDaemon JDK 17 平台线程是否为守护线程；JDK 21 及以上忽略该参数
     * @return 自适应任务执行器
     */
    public static ExecutorService newFixedThreadPool(String threadNamePrefix, int size, boolean isDaemon) {
        Objects.requireNonNull(threadNamePrefix, "threadNamePrefix");
        validatePositive(size, "size");
        ThreadPoolConfig config = fixedThreadPoolConfig(size, isDaemon);
        return newThreadPool(threadNamePrefix, config);
    }

    /**
     * 创建实例专用的单线程执行器，并纳入统一资源回收。
     *
     * @param threadNamePrefix 线程名称前缀
     * @param isDaemon 是否创建守护线程
     * @return 单线程执行器
     */
    public static ExecutorService newSingleThreadExecutor(String threadNamePrefix, boolean isDaemon) {
        Objects.requireNonNull(threadNamePrefix, "threadNamePrefix");
        ThreadPoolConfig config = fixedThreadPoolConfig(1, isDaemon);
        return register(newPlatformThreadPool(threadNamePrefix, config));
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
     * 创建参数可定制的实例专用执行器，并纳入统一资源回收。
     *
     * <p>JDK 17 使用调用方配置的平台线程池；JDK 21 及以上使用不限制并发的
     * 每任务虚拟线程，此时线程数、队列、daemon 和拒绝策略配置不生效。</p>
     *
     * @param threadNamePrefix 线程名称前缀
     * @param config JDK 17 平台线程池配置
     * @return 自适应任务执行器
     */
    public static ExecutorService newThreadPool(String threadNamePrefix, ThreadPoolConfig config) {
        Objects.requireNonNull(threadNamePrefix, "threadNamePrefix");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(config.unit, "unit");
        Objects.requireNonNull(config.workQueue, "workQueue");
        Objects.requireNonNull(config.rejectionHandler, "rejectionHandler");
        if (VirtualThreadSupport.isSupported()) {
            return register(new ManagedVirtualThreadExecutor(threadNamePrefix));
        }
        return register(newPlatformThreadPool(threadNamePrefix, config));
    }

    private static ThreadPoolConfig fixedThreadPoolConfig(int size, boolean isDaemon) {
        return ThreadPoolConfig.builder()
                .poolSize(size, size)
                .keepAlive(0L, TimeUnit.MILLISECONDS)
                .workQueue(new LinkedBlockingQueue<>())
                .isDaemon(isDaemon)
                .rejectionHandler(new ThreadPoolExecutor.AbortPolicy())
                .build();
    }

    private static ManagedThreadPoolExecutor newPlatformThreadPool(String threadNamePrefix, ThreadPoolConfig config) {
        return new ManagedThreadPoolExecutor(config.corePoolSize,
                config.maximumPoolSize, config.keepAliveTime, Objects.requireNonNull(config.unit, "unit"),
                Objects.requireNonNull(config.workQueue, "workQueue"), namedThreadFactory(threadNamePrefix,
                config.isDaemon), Objects.requireNonNull(config.rejectionHandler, "rejectionHandler"));
    }

    /**
     * 自定义执行器的 JDK 17 平台线程池配置。
     *
     * <p>使用构建器逐项设置，避免调用方依赖多个位置参数的顺序。
     * JDK 21 及以上使用虚拟线程时不应用这些配置。</p>
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
     * 关闭单个登记过的线程池并从登记表中移除。
     *
     * <p>实例级线程池（如每个 DeepAgent 的 task-scheduler）生命周期结束时必须
     * 调用本方法：仅 {@code shutdown()} 会让已关闭的池对象永久驻留在
     * {@code MANAGED_EXECUTORS} 静态集合中，随实例创建次数线性累积。</p>
     *
     * @param executor 待关闭的线程池
     */
    public static void shutdown(ExecutorService executor) {
        Objects.requireNonNull(executor, "executor");
        MANAGED_EXECUTORS.remove(executor);
        shutdownExecutors(List.of(executor), SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS);
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
     * 创建共享业务任务执行器。
     *
     * @param maxSizeProperty JDK 17 平台线程最大数量的系统属性名
     * @param maxSizeEnv JDK 17 平台线程最大数量的环境变量名
     * @param keepAliveProperty JDK 17 平台线程空闲保留时间的系统属性名
     * @param keepAliveEnv JDK 17 平台线程空闲保留时间的环境变量名
     * @param threadNamePrefix 线程名称前缀
     * @return 自适应共享任务执行器
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
     * I/O 阻塞型流式池默认上限：线程 99% 时间在等 LLM/网络，CPU 占用近零，按
     * {@code max(64, CPU 核数 × 8)} 估算并发槽位。与 runtime 侧 QuerySsePumpExecutor
     * 的默认公式对齐，避免 pump 池放行的并发流在 core 侧成为瓶颈。
     *
     * <p>下界 64 覆盖 JDK17 下实测的 60 并发场景（agent 侧 CPU 耗时 &lt;500ms，
     * 总耗时由 LLM 响应决定，wait/compute 比极高，线程数受限于 I/O 等待而非 CPU）；
     * 核数较多时随核数线性增长，避免高并发机器上成为瓶颈。</p>
     *
     * <p>适用于 deep-agent-stream / vertex-stream / stream-actor 等长驻流式会话池。</p>
     *
     * @return 默认最大线程数
     * @since 0.1.14
     */
    static int defaultIoBoundMaxSize() {
        return Math.max(64, Runtime.getRuntime().availableProcessors() * 8);
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

    private static Thread.UncaughtExceptionHandler virtualThreadExceptionHandler() {
        return (thread, error) ->
                Loggers.COMMON.exception("Uncaught exception in virtual thread=" + thread.getName(), error);
    }

    /**
     * 当前运行时是否支持虚拟线程。
     *
     * <p>JDK 21 及以上返回 {@code true}，JDK 17 返回 {@code false}。调用方可据此决定是否
     * 跳过基于平台线程数量的并发限制——虚拟线程下线程创建开销可忽略，应由上层
     * 准入控制（如 runtime 的 TaskAdmissionGate）统一管控并发，而非 core 层自限。</p>
     *
     * @return 虚拟线程可用时为 {@code true}
     * @since 0.1.15
     */
    public static boolean isVirtualThreadSupported() {
        return VirtualThreadSupport.isSupported();
    }

    /**
     * 平台线程模式下 DeepAgent 任务并发的默认上限。
     *
     * <p>DeepAgent 任务线程 99% 时间在等待 LLM/网络响应，属于 I/O 阻塞型，按
     * {@code max(64, CPU 核数 × 8)} 估算并发槽位，与流式池 {@link #defaultIoBoundMaxSize()}
     * 对齐。仅在 JDK 17（不支持虚拟线程）时作为 {@code maxConcurrentTasks} 的默认值生效；
     * JDK 21+ 并发闸已放开，此值不再参与 gate 判定。</p>
     *
     * @return 平台线程模式下的默认任务并发上限
     * @since 0.1.15
     */
    public static int defaultTaskConcurrency() {
        return defaultIoBoundMaxSize();
    }

    /**
     * 创建一个已配置但未启动的线程。
     *
     * <p>JDK 21 及以上使用虚拟线程，JDK 17 使用平台线程。调用方负责 {@code Thread.start()}。
     * 虚拟线程始终是守护线程，{@code isDaemon} 参数仅在 JDK 17 平台线程路径生效。</p>
     *
     * @param runnable 任务
     * @param threadName 线程名（虚拟线程直接用此名称，平台线程也用此名称）
     * @param isDaemon JDK 17 平台线程是否为守护线程
     * @return 已配置但未启动的线程
     * @since 0.1.15
     */
    public static Thread newThread(Runnable runnable, String threadName, boolean isDaemon) {
        Objects.requireNonNull(runnable, "runnable");
        Objects.requireNonNull(threadName, "threadName");
        Thread.UncaughtExceptionHandler exceptionHandler = (thread, error) ->
                Loggers.COMMON.error("Uncaught exception in {}: {}", thread.getName(), error.getMessage());
        Thread virtualThread = VirtualThreadSupport.newVirtualThread(runnable, threadName, exceptionHandler);
        if (virtualThread != null) {
            return virtualThread;
        }
        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
        thread.setName(threadName);
        thread.setDaemon(isDaemon);
        thread.setUncaughtExceptionHandler(exceptionHandler);
        return thread;
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
     * 各模块线程池默认上限。
     *
     * <p>所有模块池统一使用 {@code core=max + ArrayBlockingQueue} 排队语义，
     * 不使用 SynchronousQueue（direct-handoff）。排队语义把突发流量转为缓冲，
     * 失败模式更可控。</p>
     */
    private enum ModulePoolDefaults {
        PREGEL_TASK("pregel-task", 32, 256),
        WORKFLOW_STREAM("workflow-stream", 32, 256),
        VERTEX_STREAM("vertex-stream", 32, 256),
        STREAM_ACTOR("stream-actor", 32, 256),
        END_TEMPLATE_RENDER("end-template-render", 8, 128),
        CALLBACK_PARALLEL("callback-parallel", 32, 128),
        MQ_SERVER_ADAPTER("mq-server-adapter", 16, 128),
        TASK_MANAGER_WORKER("task-manager-worker", 16, 128),
        DEEP_AGENT_STREAM("deep-agent-stream", 32, 128),
        REACT_AGENT_STREAM("react-agent-stream", 32, 128),
        GENERIC("", 32, 256);

        private final String prefix;
        private final int maxSize;
        private final int queueCapacity;

        ModulePoolDefaults(String prefix, int maxSize, int queueCapacity) {
            this.prefix = prefix;
            this.maxSize = maxSize;
            this.queueCapacity = queueCapacity;
        }

        /**
         * 解析该模块池的最大线程数。
         *
         * @return 流式会话池（deep-agent-stream / react-agent-stream / vertex-stream / stream-actor）
         *         返回 CPU 公式值，其余池返回枚举声明的固定值
         */
        int resolveMaxSize() {
            return switch (this) {
                case DEEP_AGENT_STREAM, REACT_AGENT_STREAM, VERTEX_STREAM, STREAM_ACTOR -> defaultIoBoundMaxSize();
                default -> maxSize;
            };
        }

        int queueCapacity() {
            return queueCapacity;
        }

        /**
         * 判断该模块池是否允许核心线程超时回收。
         *
         * @return {@code false} 当 DEEP_AGENT_STREAM 或 REACT_AGENT_STREAM（用户直接感知的 SSE 会话，
         *         热线程可消除首 token 的线程创建延迟）；其余池返回 {@code true}
         */
        boolean allowsCoreTimeout() {
            return this != DEEP_AGENT_STREAM && this != REACT_AGENT_STREAM;
        }

        RejectedExecutionHandler rejectionHandler() {
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
     * 使用每任务虚拟线程承载任务，并保留统一生命周期管理。
     */
    private static final class ManagedVirtualThreadExecutor extends AbstractExecutorService {
        private final ExecutorService delegate;

        private ManagedVirtualThreadExecutor(String threadNamePrefix) {
            this.delegate = VirtualThreadSupport.newVirtualExecutor(threadNamePrefix,
                    virtualThreadExceptionHandler());
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
            MANAGED_EXECUTORS.remove(this);
        }

        @Override
        public List<Runnable> shutdownNow() {
            List<Runnable> pendingTasks = delegate.shutdownNow();
            MANAGED_EXECUTORS.remove(this);
            return pendingTasks;
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(Objects.requireNonNull(command, "command"));
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
