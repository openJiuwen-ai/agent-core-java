/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.toolgroup;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Mock LLM client that captures every request for offline model-call tests. */
public final class CapturingModelClient implements Model.ModelClient {
    private final AssistantMessage response;
    private final Queue<List<BaseMessage>> capturedMessageLists = new ConcurrentLinkedQueue<>();
    private final Queue<List<?>> capturedToolLists = new ConcurrentLinkedQueue<>();
    private final AtomicInteger invokeCount = new AtomicInteger();

    public CapturingModelClient() {
        this(new AssistantMessage("Issue #196 fixture response"));
    }

    public CapturingModelClient(AssistantMessage response) {
        this.response = Objects.requireNonNull(response, "response must not be null");
    }

    @Override
    public CompletionStage<AssistantMessage> invoke(
            List<BaseMessage> messages, ModelInvokeOptions options) {
        invokeCount.incrementAndGet();
        capturedMessageLists.add(List.copyOf(messages));
        List<?> tools =
                options == null || options.getTools() == null ? List.of() : options.getTools();
        capturedToolLists.add(List.copyOf(tools));
        return CompletableFuture.completedFuture(response);
    }

    /**
     * Returns every captured message list.
     *
     * @return immutable copy of captured message lists
     */
    public List<List<BaseMessage>> capturedMessageLists() {
        return List.copyOf(capturedMessageLists);
    }

    /**
     * Returns every captured tool list.
     *
     * @return immutable copy of captured tool lists
     */
    public List<List<?>> capturedToolLists() {
        return List.copyOf(capturedToolLists);
    }

    /**
     * Returns the most recently captured tool list.
     *
     * @return latest tool list, or an empty list when no call was captured
     */
    public List<?> lastCapturedToolList() {
        return capturedToolLists.stream().reduce((first, second) -> second).orElseGet(List::of);
    }

    /**
     * Returns the number of model invocations captured by this client.
     *
     * @return captured invocation count
     */
    public int invokeCount() {
        return invokeCount.get();
    }
}
