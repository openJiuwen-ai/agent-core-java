/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder.builder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

final class UnwrappedCompletableFuture<T> extends CompletableFuture<T> {

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        UnwrappedCompletableFuture<T> future = new UnwrappedCompletableFuture<>();
        CompletableFuture.supplyAsync(supplier).whenComplete((value, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(unwrap(throwable));
            } else {
                future.complete(value);
            }
        });
        return future;
    }

    @Override
    public T join() {
        try {
            return super.join();
        } catch (CompletionException exception) {
            throw propagate(unwrap(exception));
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && (current instanceof CompletionException
                || current instanceof ExecutionException
                || current.getClass().equals(RuntimeException.class))) {
            current = current.getCause();
        }
        return current;
    }

    private static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        return new RuntimeException(throwable);
    }
}
