/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.runner.callback.AsyncCallbackFramework;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Verifies that {@link Model} collects stream timing at its single stream
 * funnel and backfills the measurements onto the usage object.
 *
 * <p>The window must start <i>before</i> the client opens the connection, must
 * advance only on output-bearing chunks, and must leave absent measurements
 * {@code null} rather than writing zero.</p>
 */
class StreamTimingCollectionTest {

    private static final String PROVIDER = "StreamTimingTestProvider";

    @AfterEach
    void tearDown() {
        Model.clearCallbackFramework();
    }

    /** Installs a real framework; without one the timing wrapper is skipped. */
    private Model newModel() {
        Model.setCallbackFramework(new AsyncCallbackFramework(false, false));
        return Model.init_model(PROVIDER, "m", "k", "https://x.test");
    }

    @Test
    @DisplayName("timing window starts before the client call, not after it")
    void timingWindowStartsBeforeClientCall() {
        // The stub blocks inside stream() to emulate waiting for response
        // headers, which happens before any chunk exists. Chunk iteration after
        // the call costs microseconds, so a window that started after stream()
        // returned would report a near-zero TTFT.
        TimingClient client = new TimingClient(
                List.of(chunk("hello", null, null), chunk("", null, usage(3))), 80);
        register(client);

        long before = System.nanoTime();
        List<AssistantMessageChunk> seen = drain(newModel().stream("hi"));
        long after = System.nanoTime();

        assertTrue(client.returnedAtNanos > before, "stub should have blocked inside stream()");

        double ttftNanos = usageOf(seen).getTtftSeconds() * 1_000_000_000.0d;
        assertTrue(ttftNanos > 70_000_000.0d,
                "TTFT must include the 80ms wait inside stream(), got " + ttftNanos + "ns");
        assertTrue((after - before) >= ttftNanos, "TTFT cannot exceed the whole call");
    }

    @Test
    @DisplayName("framing-only chunks do not advance the timing window")
    void framingChunksDoNotAdvanceWindow() {
        // A role-only frame arrives first; only the content frame carries output.
        register(new TimingClient(List.of(
                chunk(null, null, null),
                chunk("", null, null),
                chunk("done", null, usage(2))), 0));

        List<AssistantMessageChunk> seen = drain(newModel().stream("hi"));

        UsageMetadata usage = usageOf(seen);
        assertNotNull(usage.getTtftSeconds(), "an output chunk did arrive, so TTFT is measurable");
        assertTrue(usage.getTtftSeconds() >= 0.0d);
    }

    @Test
    @DisplayName("total latency is measured even when no output chunk arrived")
    void totalLatencyMeasuredWithoutOutput() {
        // Usage-only stream: an early finish with no content at all.
        register(new TimingClient(List.of(chunk("", null, usage(0))), 30));

        List<AssistantMessageChunk> seen = drain(newModel().stream("hi"));

        UsageMetadata usage = usageOf(seen);
        assertTrue(usage.getTotalLatency() > 0.0d, "total latency covers the whole call");
        assertNull(usage.getTtftSeconds(), "no output chunk means TTFT was never measured");
        assertNull(usage.getInterTokenLatencyMs(), "no token window means TPOT was never measured");
    }

    @Test
    @DisplayName("TPOT divides by output tokens, not by chunk count")
    void tpotUsesOutputTokenDenominator() {
        // Two chunks bound the window, but the provider reports 9 output
        // tokens. A chunk-count denominator would be 8/1 = 8x larger.
        TimingClient client = new TimingClient(List.of(
                chunk("a", null, null),
                chunk("b", null, usage(9))), 0);
        client.chunkGapMillis = 60;
        register(client);

        List<AssistantMessageChunk> seen = drain(newModel().stream("hi"));

        double tpotMs = usageOf(seen).getInterTokenLatencyMs();
        assertTrue(tpotMs > 0.0d, "a real gap between output chunks was produced");
        // With the token denominator the gap is split 8 ways; a chunk-count
        // denominator would report the whole gap as one token's worth.
        assertTrue(tpotMs < 40.0d, "expected the 60ms gap divided by 8, got " + tpotMs);
    }

    @Test
    @DisplayName("reasoning window is measured from reasoning fragments only")
    void reasoningDurationMeasured() {
        TimingClient client = new TimingClient(List.of(
                chunk(null, "think", null),
                chunk("answer", null, null),
                chunk(null, "more", usage(4))), 0);
        client.chunkGapMillis = 40;
        register(client);

        List<AssistantMessageChunk> seen = drain(newModel().stream("hi"));

        Double durationMs = usageOf(seen).getReasoningDurationMs();
        assertNotNull(durationMs, "two reasoning fragments bound a window");
        assertTrue(durationMs > 0.0d);
    }

    @Test
    @DisplayName("reasoning duration stays null when the call produced no reasoning")
    void reasoningDurationNullWithoutReasoning() {
        register(new TimingClient(List.of(chunk("answer", null, usage(3))), 0));

        List<AssistantMessageChunk> seen = drain(newModel().stream("hi"));

        assertNull(usageOf(seen).getReasoningDurationMs());
    }

    @Test
    @DisplayName("retry count reflects the highest retry the client reported")
    void retryCountIsCollected() {
        TimingClient client = new TimingClient(List.of(chunk("answer", null, usage(3))), 0);
        client.onRetryEvents = listener -> {
            listener.onRetry(retryEvent(1));
            listener.onRetry(retryEvent(2));
        };
        register(client);

        List<AssistantMessageChunk> seen = drain(newModel().stream("hi"));

        assertEquals(2, usageOf(seen).getRetryCount());
    }

    @Test
    @DisplayName("caller's retry listener is still invoked while counting")
    void callerRetryListenerStillFires() {
        List<Integer> observed = new ArrayList<>();
        TimingClient client = new TimingClient(List.of(chunk("answer", null, usage(3))), 0);
        client.onRetryEvents = listener -> listener.onRetry(retryEvent(1));
        register(client);

        drain(newModel().stream("hi",
                ModelInvokeOptions.builder().retryListener(e -> observed.add(e.retryCount())).build()));

        assertEquals(List.of(1), observed, "the original listener must not be replaced");
    }

    // ================================================================
    // Helpers
    // ================================================================

    private static void register(TimingClient client) {
        Model.registerClientFactory(PROVIDER, new Model.ModelClientFactory() {
            @Override
            public Model.ModelClient create(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
                return client;
            }

            @Override
            public String providerName() {
                return PROVIDER;
            }
        });
    }

    private static UsageMetadata usageOf(List<AssistantMessageChunk> seen) {
        UsageMetadata usage = seen.get(seen.size() - 1).getUsageMetadata();
        assertNotNull(usage, "the trailing chunk must carry usage");
        return usage;
    }

    private static List<AssistantMessageChunk> drain(Iterator<AssistantMessageChunk> iterator) {
        List<AssistantMessageChunk> seen = new ArrayList<>();
        while (iterator.hasNext()) {
            seen.add(iterator.next());
        }
        return seen;
    }

    private static AssistantMessageChunk chunk(Object content, String reasoning, UsageMetadata usage) {
        return AssistantMessageChunk.builder()
                .content(content)
                .reasoningContent(reasoning)
                .usageMetadata(usage)
                .build();
    }

    private static UsageMetadata usage(int outputTokens) {
        return UsageMetadata.builder().inputTokens(10).outputTokens(outputTokens).build();
    }

    private static ModelRetryEvent retryEvent(int retryCount) {
        return new ModelRetryEvent(retryCount, 3, 429, null, null, "header", null);
    }

    /**
     * Client returning a fixed chunk sequence, with hooks to simulate work done
     * inside {@code stream()} before the iterator is handed back.
     */
    private static final class TimingClient implements Model.ModelClient {
        private final List<AssistantMessageChunk> chunks;
        private final long blockInsideStreamMillis;
        private volatile long returnedAtNanos;
        private long chunkGapMillis;
        private Consumer<ModelRetryListener> onRetryEvents = listener -> { };

        TimingClient(List<AssistantMessageChunk> chunks, long blockInsideStreamMillis) {
            this.chunks = chunks;
            this.blockInsideStreamMillis = blockInsideStreamMillis;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(AssistantMessage.builder().content("").build());
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            if (blockInsideStreamMillis > 0) {
                sleep(blockInsideStreamMillis);
            }
            if (options != null && options.getRetryListener() != null) {
                onRetryEvents.accept(options.getRetryListener());
            }
            returnedAtNanos = System.nanoTime();
            return new GappedIterator(chunks, chunkGapMillis);
        }
    }

    /** Emits the given chunks, optionally pausing between them. */
    private static final class GappedIterator implements Iterator<AssistantMessageChunk> {
        private final List<AssistantMessageChunk> chunks;
        private final long gapMillis;
        private int index;

        GappedIterator(List<AssistantMessageChunk> chunks, long gapMillis) {
            this.chunks = chunks;
            this.gapMillis = gapMillis;
        }

        @Override
        public boolean hasNext() {
            return index < chunks.size();
        }

        @Override
        public AssistantMessageChunk next() {
            if (gapMillis > 0 && index > 0) {
                sleep(gapMillis);
            }
            return chunks.get(index++);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}


