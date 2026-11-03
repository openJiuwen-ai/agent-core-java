/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.reactive;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

/**
 * 将框架阻塞同步 API 提升为 Reactor Mono/Flux 的适配器。
 * 所有响应式包装方法统一委托到这里，保证调度器选择和取消语义一致。
 * 本类不依赖 Spring，core 模块保持 Spring-free。
 * 
 * @since 0.1.7
 */
public final class ReactiveAdapters {
    /**
     * ReactiveAdapters.
     * 
     * @since 0.1.7
     */
    private ReactiveAdapters() {
    }

    /**
     * 将阻塞调用包装为 Mono。
     * checked exception 直接作为 onError 信号透传，不会被包装为 RuntimeException。
     * 
     * @param callable 阻塞调用
     * @return 在 boundedElastic 上执行的 Mono
     * @since 0.1.7
     */
    public static <T> Mono<T> fromCallable(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 将 {@link CompletionStage} 包装为 Mono，并在 boundedElastic 上订阅。
     *
     * @param stage 异步完成阶段
     * @return 在 boundedElastic 上订阅的 Mono
     * @since 0.1.7
     */
    public static <T> Mono<T> fromCompletionStage(CompletionStage<T> stage) {
        return Mono.fromCompletionStage(stage).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 将阻塞 Runnable 包装为 Mono。
     * 
     * @param runnable 阻塞操作
     * @return 在 boundedElastic 上执行的 Mono
     * @since 0.1.7
     */
    public static Mono<Void> fromRunnable(Runnable runnable) {
        return Mono.<Void>fromRunnable(runnable).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 将阻塞 Iterator 提升为 Flux。
     * cleanup 在 COMPLETE / ERROR / CANCEL 三种终态下各触发恰好一次，
     * 可安全放入资源释放逻辑（如 postRun()）。
     * 
     * @param iterator 阻塞 Iterator
     * @param cleanup 终态清理回调，可为空
     * @return 在 boundedElastic 上消费 Iterator 的 Flux
     * @since 0.1.7
     */
    public static <T> Flux<T> fromIterator(Iterator<T> iterator, Runnable cleanup) {
        return Flux.<T>generate(sink -> {
            if (iterator.hasNext()) {
                sink.next(iterator.next());
            } else {
                sink.complete();
            }
        }).subscribeOn(Schedulers.boundedElastic()).doFinally(signal -> {
            if (cleanup != null) {
                cleanup.run();
            }
        });
    }

    /**
     * 将阻塞 Iterator 提升为 Flux。
     * 
     * @param iterator 阻塞 Iterator
     * @return 在 boundedElastic 上消费 Iterator 的 Flux
     * @since 0.1.7
     */
    public static <T> Flux<T> fromIterator(Iterator<T> iterator) {
        return fromIterator(iterator, null);
    }

    /**
     * cleanup 应保证幂等（参考 AgentSessionApi.postRun() 的 CAS 模式），
     * 因为它与 iterator 自然耗尽时的清理路径相互独立。
     * 
     * @param source 延迟创建 Iterator 的阻塞调用
     * @param cleanup 终态清理回调，可为空
     * @return 延迟创建并消费 Iterator 的 Flux
     * @since 0.1.7
     */
    public static <T> Flux<T> fromCallableIterator(java.util.concurrent.Callable<? extends Iterator<T>> source,
            Runnable cleanup) {
        return Mono.fromCallable(source::call).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(it -> fromIterator(it, cleanup));
    }

    /**
     * 将延迟创建的阻塞 Iterator 提升为 Flux。
     * 
     * @param source 延迟创建 Iterator 的阻塞调用
     * @return 延迟创建并消费 Iterator 的 Flux
     * @since 0.1.7
     */
    public static <T> Flux<T> fromCallableIterator(java.util.concurrent.Callable<? extends Iterator<T>> source) {
        return fromCallableIterator(source, null);
    }

    /**
     * 专为 SSE 场景设计：iterator 的 hasNext() 阻塞在 BufferedReader.readLine()，
     * 该调用不响应 Thread.interrupt()，唯一可靠的取消方式是调 close() 关闭底层 socket。
     * 使用 Flux.usingWhen 而非 doFinally：后者挂在内层 Flux 上，若 source.call()
     * 执行期间取消到来，flatMapMany 从未订阅内层 Flux，cleanup 永远不触发。
     * usingWhen 的 asyncCleanup 在任意终态下都会执行。
     * iterator 未实现 AutoCloseable 时降级为普通迭代，取消仍会翻转 cancelled 标志，
     * 但无法中断已在途的阻塞读。
     * 
     * @param source 延迟创建 Iterator 的阻塞调用
     * @return 自动关闭 Iterator 的 Flux
     * @since 0.1.7
     */
    public static <T> Flux<T> fromAutoCloseableIterator(java.util.concurrent.Callable<? extends Iterator<T>> source) {
        return fromAutoCloseableIterator(source, null);
    }

    /**
     * 将延迟创建的阻塞 Iterator 提升为 Flux，并在终态关闭 AutoCloseable Iterator。
     * 
     * @param source 延迟创建 Iterator 的阻塞调用
     * @param cleanup 关闭 Iterator 后执行的清理回调，可为空
     * @return 自动关闭 Iterator 并执行清理回调的 Flux
     * @since 0.1.7
     */
    public static <T> Flux<T> fromAutoCloseableIterator(java.util.concurrent.Callable<? extends Iterator<T>> source,
            Runnable cleanup) {
        return Flux.usingWhen(Mono.fromCallable(source::call).subscribeOn(Schedulers.boundedElastic()),
                it -> fromIterator(it), it -> closeAndCleanup(it, cleanup));
    }

    /**
     * closeAndCleanup.
     * 
     * @param iterator iterator
     * @param cleanup cleanup
     * @return the result
     * @since 0.1.7
     */
    private static Mono<Void> closeAndCleanup(Iterator<?> iterator, Runnable cleanup) {
        Mono<Void> close = iterator instanceof AutoCloseable closeable ? Mono.fromCallable(() -> {
            closeable.close();
            return Boolean.TRUE;
        }).onErrorResume(ignored -> Mono.empty()).then() : Mono.empty();
        Mono<Void> cleanupMono = cleanup == null ? Mono.empty() : Mono.fromRunnable(cleanup).then();
        return close.then(cleanupMono).subscribeOn(Schedulers.boundedElastic());
    }
}
