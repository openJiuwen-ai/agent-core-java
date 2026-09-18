/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Abstract base for prompt builders backed by an LLM model.
 *
 * <p>Mirrors Python's {@code BasePromptBuilder} in
 * {@code openjiuwen/dev_tools/prompt_builder/base.py}.</p>
 */
public abstract class BasePromptBuilder {
    protected final Model model;

    protected BasePromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this.model = new Model(modelClientConfig, modelConfig);
    }

    public Model getModel() {
        return model;
    }

    public CompletableFuture<Optional<String>> build() {
        return build(List.of(), Map.of());
    }

    public abstract CompletableFuture<Optional<String>> build(
            List<Object> args,
            Map<String, Object> kwargs);

    public Flow.Publisher<?> streamBuild() {
        return streamBuild(List.of(), Map.of());
    }

    public abstract Flow.Publisher<?> streamBuild(
            List<Object> args,
            Map<String, Object> kwargs);

    /**
     * Stream result that preserves legacy collected-string future usage while also exposing publisher subscription.
     */
    public static final class PromptBuilderStreamResult extends CompletableFuture<String>
            implements Flow.Publisher<String> {
        private final Flow.Publisher<String> source;
        private final List<String> chunks = new ArrayList<>();
        private final List<Flow.Subscriber<? super String>> subscribers = new ArrayList<>();
        private boolean completed;
        private Throwable error;

        public PromptBuilderStreamResult(Flow.Publisher<String> source) {
            this.source = Objects.requireNonNull(source, "source");
            collect();
        }

        @Override
        public void subscribe(Flow.Subscriber<? super String> subscriber) {
            Objects.requireNonNull(subscriber, "subscriber");
            subscriber.onSubscribe(new Flow.Subscription() {
                private boolean started;
                private boolean canceled;

                @Override
                public void request(long n) {
                    if (n <= 0) {
                        subscriber.onError(new IllegalArgumentException("non-positive subscription request"));
                        return;
                    }
                    if (started || canceled) {
                        return;
                    }
                    started = true;
                    subscribeRequested(subscriber);
                }

                @Override
                public void cancel() {
                    canceled = true;
                    synchronized (PromptBuilderStreamResult.this) {
                        subscribers.remove(subscriber);
                    }
                }
            });
        }

        private void collect() {
            StringBuilder result = new StringBuilder();
            source.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(String item) {
                    String chunk = item == null ? "" : item;
                    result.append(chunk);
                    publishNext(chunk);
                }

                @Override
                public void onError(Throwable throwable) {
                    publishError(throwable);
                }

                @Override
                public void onComplete() {
                    publishComplete(result.toString());
                }
            });
        }

        private void subscribeRequested(Flow.Subscriber<? super String> subscriber) {
            List<String> snapshot;
            Throwable failure;
            boolean done;
            synchronized (this) {
                snapshot = new ArrayList<>(chunks);
                failure = error;
                done = completed;
                if (!done && failure == null) {
                    subscribers.add(subscriber);
                }
            }
            for (String chunk : snapshot) {
                subscriber.onNext(chunk);
            }
            if (failure != null) {
                subscriber.onError(failure);
            } else if (done) {
                subscriber.onComplete();
            }
        }

        private void publishNext(String chunk) {
            List<Flow.Subscriber<? super String>> targets;
            synchronized (this) {
                chunks.add(chunk);
                targets = new ArrayList<>(subscribers);
            }
            for (Flow.Subscriber<? super String> subscriber : targets) {
                subscriber.onNext(chunk);
            }
        }

        private void publishError(Throwable throwable) {
            List<Flow.Subscriber<? super String>> targets;
            synchronized (this) {
                error = throwable;
                targets = new ArrayList<>(subscribers);
                subscribers.clear();
            }
            completeExceptionally(throwable);
            for (Flow.Subscriber<? super String> subscriber : targets) {
                subscriber.onError(throwable);
            }
        }

        private void publishComplete(String result) {
            List<Flow.Subscriber<? super String>> targets;
            synchronized (this) {
                completed = true;
                targets = new ArrayList<>(subscribers);
                subscribers.clear();
            }
            complete(result);
            for (Flow.Subscriber<? super String> subscriber : targets) {
                subscriber.onComplete();
            }
        }
    }
}
