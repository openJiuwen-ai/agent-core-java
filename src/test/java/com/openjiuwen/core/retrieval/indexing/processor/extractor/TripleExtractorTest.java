/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.extractor;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's tests for
 * {@code openjiuwen/core/retrieval/indexing/processor/extractor/triple_extractor.py}.
 */
class TripleExtractorTest {

    @Test
    void extractBuildsPromptUsesTemperatureAndReturnsTriples() {
        RecordingInvoker invoker = new RecordingInvoker(
                CompletableFuture.completedFuture(new AssistantMessage("{\"triples\":[[\"Alice\",\"knows\",\"Bob\"]]}"))
        );
        TripleExtractor extractor = new TripleExtractor(invoker, "test-model", 0.25d, 4);
        TextChunk chunk = new TextChunk("chunk-1", "Alice knows Bob.", "doc-1", Map.of(), null);

        List<Triple> triples = extractor.extract(List.of(chunk)).join();

        assertEquals(1, triples.size());
        assertEquals("Alice", triples.get(0).getSubject());
        assertEquals("knows", triples.get(0).getPredicate());
        assertEquals("Bob", triples.get(0).getObject());
        assertEquals(Map.of("doc_id", "doc-1", "chunk_id", "chunk-1"), triples.get(0).getMetadata());
        assertEquals(1, invoker.messages.size());
        assertTrue(invoker.messages.getFirst().get(0).getContentAsString().contains("Title:\nUntitled"));
        assertEquals(0.25d, invoker.options.getFirst().get("temperature"));
    }

    @Test
    void extractAcceptsMarkdownJsonAndSkipsInvalidTriples() {
        RecordingInvoker invoker = new RecordingInvoker(
                CompletableFuture.completedFuture(new AssistantMessage("""
                        ```json
                        {
                          "triples": [
                            ["Alice", "knows", "Bob"],
                            ["too-short"],
                            [{"nested": true}, "invalid", "value"]
                          ]
                        }
                        ```
                        """))
        );
        TripleExtractor extractor = new TripleExtractor(invoker, "test-model");
        TextChunk chunk = new TextChunk("chunk-1", "Alice knows Bob.", "doc-1", Map.of("title", "Graph"), null);

        List<Triple> triples = extractor.extract(List.of(chunk)).join();

        assertEquals(1, triples.size());
        assertEquals("Alice", triples.get(0).getSubject());
        assertTrue(invoker.messages.getFirst().get(0).getContentAsString().contains("Title:\nGraph"));
    }

    @Test
    void extractRaisesFirstChunkErrorInInputOrder() {
        BaseError original = ErrorHelper.buildError(
                StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR,
                null,
                null,
                null,
                Map.of("error_msg", "chunk-1: original failure")
        );
        RecordingInvoker invoker = new RecordingInvoker(
                CompletableFuture.failedFuture(original),
                CompletableFuture.failedFuture(new IllegalStateException("bad json"))
        );
        TripleExtractor extractor = new TripleExtractor(invoker, "test-model");
        TextChunk first = new TextChunk("chunk-1", "first", "doc-1", new LinkedHashMap<>(), null);
        TextChunk second = new TextChunk("chunk-2", "second", "doc-1", new LinkedHashMap<>(), null);

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> extractor.extract(List.of(first, second)).join()
        );
        BaseError error = (BaseError) exception.getCause();

        assertEquals(StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR, error.getStatus());
        assertEquals("chunk-1: original failure", error.getParams().get("error_msg"));
    }

    @Test
    void extractFailsWhenTriplesCannotBeParsed() {
        RecordingInvoker invoker = new RecordingInvoker(
                CompletableFuture.completedFuture(new AssistantMessage("{\"triples\":[[\"missing\",\"shape\"]]}"))
        );
        TripleExtractor extractor = new TripleExtractor(invoker, "test-model");
        TextChunk chunk = new TextChunk("chunk-9", "bad", "doc-9", Map.of(), null);

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> extractor.extract(List.of(chunk)).join()
        );
        BaseError error = (BaseError) exception.getCause();

        assertEquals(StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR, error.getStatus());
        assertEquals(
                "chunk-9: LLM response could not be parsed as valid triple JSON",
                error.getParams().get("error_msg")
        );
    }

    private static final class RecordingInvoker implements TripleExtractor.LlmInvoker {

        private final Deque<CompletableFuture<? extends BaseMessage>> responses = new ArrayDeque<>();
        private final Deque<List<BaseMessage>> messages = new ArrayDeque<>();
        private final Deque<Map<String, Object>> options = new ArrayDeque<>();

        @SafeVarargs
        private RecordingInvoker(CompletableFuture<? extends BaseMessage>... futures) {
            responses.addAll(List.of(futures));
        }

        @Override
        public CompletableFuture<? extends BaseMessage> invoke(List<BaseMessage> messages, Map<String, Object> options) {
            this.messages.add(messages);
            this.options.add(options);
            return responses.remove();
        }
    }
}
