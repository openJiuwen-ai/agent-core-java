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

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code TripleExtractor} in
 * {@code openjiuwen/core/retrieval/indexing/processor/extractor/triple_extractor.py}.
 *
 * <p>Mirrors Python's {@code TestTripleExtractor} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/extractor/test_triple_extractor.py}.</p>
 */
class TripleExtractorTest {

    @Test
    void initStoresProvidedClientAndModel() {
        RecordingInvoker invoker = new RecordingInvoker();
        TripleExtractor extractor = new TripleExtractor(invoker, "test-model", 0.0d, 10);

        assertSame(invoker, fieldValue(extractor, "llmClient"));
        assertEquals("test-model", fieldValue(extractor, "modelName"));
    }

    @Test
    void initWithDefaultsCreatesUsableExtractor() {
        RecordingInvoker invoker = new RecordingInvoker(
                CompletableFuture.completedFuture(new AssistantMessage("{\"triples\":[[\"Alice\",\"knows\",\"Bob\"]]}"))
        );
        TripleExtractor extractor = new TripleExtractor(invoker, "test-model");

        List<Triple> triples = extractor.extract(List.of(new TextChunk("1", "Alice knows Bob", "doc_1"))).join();

        assertEquals(1, triples.size());
        assertEquals(1, invoker.messages.size());
        assertEquals(0.0d, invoker.options.getFirst().get("temperature"));
    }

    @Test
    void extractMultipleChunksInvokesLlmForEachChunk() {
        RecordingInvoker invoker = new RecordingInvoker(
                CompletableFuture.completedFuture(new AssistantMessage("{\"triples\":[[\"Alice\",\"knows\",\"Bob\"]]}")),
                CompletableFuture.completedFuture(new AssistantMessage("{\"triples\":[[\"Charlie\",\"knows\",\"David\"]]}"))
        );
        TripleExtractor extractor = new TripleExtractor(invoker, "test-model", 0.0d, 2);
        List<TextChunk> chunks = List.of(
                new TextChunk("1", "Alice knows Bob", "doc_1"),
                new TextChunk("2", "Charlie knows David", "doc_1")
        );

        List<Triple> triples = extractor.extract(chunks).join();

        assertEquals(2, invoker.messages.size());
        assertEquals(2, triples.size());
    }

    @Test
    void extractWithExceptionWrapsRetrievalStatusAndMessage() {
        RecordingInvoker invoker = new RecordingInvoker(
                CompletableFuture.failedFuture(new IllegalStateException("429 too many requests"))
        );
        TripleExtractor extractor = new TripleExtractor(invoker, "test-model");
        TextChunk chunk = new TextChunk("1", "Alice knows Bob", "doc_1");

        BaseError error = expectExtractionError(extractor, List.of(chunk));

        assertEquals(StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR, error.getStatus());
        assertTrue(String.valueOf(error.getParams().get("error_msg")).contains("429 too many requests"));
    }

    @Test
    void extractInvalidJsonRaisesParseError() {
        RecordingInvoker invoker = new RecordingInvoker(
                CompletableFuture.completedFuture(new AssistantMessage("Invalid JSON response"))
        );
        TripleExtractor extractor = new TripleExtractor(invoker, "test-model");
        TextChunk chunk = new TextChunk("1", "Alice knows Bob", "doc_1");

        BaseError error = expectExtractionError(extractor, List.of(chunk));

        assertEquals(StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR, error.getStatus());
        assertTrue(String.valueOf(error.getParams().get("error_msg")).toLowerCase().contains("parsed"));
    }

    @Test
    void extractEmptyChunksReturnsEmptyAndDoesNotInvokeLlm() {
        RecordingInvoker invoker = new RecordingInvoker();
        TripleExtractor extractor = new TripleExtractor(invoker, "test-model");

        List<Triple> triples = extractor.extract(List.of()).join();

        assertTrue(triples.isEmpty());
        assertTrue(invoker.messages.isEmpty());
    }

    @Test
    void parseTriplesJsonArray() {
        TripleExtractor.ParseResult result = parseTriples("[[\"a\", \"b\", \"c\"]]");

        assertTrue(result.parseSuccess());
        assertEquals(1, result.triples().size());
        assertEquals("a", result.triples().get(0).getSubject());
        assertEquals("b", result.triples().get(0).getPredicate());
        assertEquals("c", result.triples().get(0).getObject());
    }

    @Test
    void parseTriplesExtraFieldsIgnored() {
        TripleExtractor.ParseResult result = parseTriples("[[\"a\", \"b\", \"c\", \"ignored\", 99]]");

        assertTrue(result.parseSuccess());
        assertEquals(1, result.triples().size());
        assertEquals("c", result.triples().get(0).getObject());
    }

    @Test
    void parseTriplesWrappedDict() {
        TripleExtractor.ParseResult result = parseTriples("{\"triples\": [[\"x\", \"y\", \"z\"]]}");

        assertTrue(result.parseSuccess());
        assertEquals(1, result.triples().size());
        assertEquals("x", result.triples().get(0).getSubject());
    }

    @Test
    void parseTriplesPromptShape() {
        TripleExtractor.ParseResult result = parseTriples(
                "{\"named_entities\": [\"Alice\", \"Bob\"], \"triples\": [[\"Alice\", \"knows\", \"Bob\"]]}"
        );

        assertTrue(result.parseSuccess());
        assertEquals(1, result.triples().size());
        assertEquals("Alice", result.triples().get(0).getSubject());
    }

    @Test
    void parseTriplesMissingTriplesKeyFails() {
        TripleExtractor.ParseResult result = parseTriples("{\"named_entities\": [\"Alice\", \"Bob\"]}");

        assertFalse(result.parseSuccess());
        assertTrue(result.triples().isEmpty());
    }

    @Test
    void parseTriplesInvalidItemsIgnored() {
        TripleExtractor.ParseResult result = parseTriples(
                "{\"triples\": [[\"a\", \"b\", \"c\"], [\"x\"], {\"bad\": 1}, [\"y\", [\"nested\"], \"z\"]]}"
        );

        assertTrue(result.parseSuccess());
        assertEquals(1, result.triples().size());
        assertEquals("a", result.triples().get(0).getSubject());
    }

    @Test
    void parseTriplesAllInvalidFails() {
        TripleExtractor.ParseResult result = parseTriples("{\"triples\": [[\"x\"], {\"bad\": 1}]}");

        assertFalse(result.parseSuccess());
        assertTrue(result.triples().isEmpty());
    }

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
        PromptAwareFailingInvoker invoker = new PromptAwareFailingInvoker(original);
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
        public synchronized CompletableFuture<? extends BaseMessage> invoke(List<BaseMessage> messages, Map<String, Object> options) {
            this.messages.add(messages);
            this.options.add(options);
            return responses.remove();
        }
    }

    private static final class PromptAwareFailingInvoker implements TripleExtractor.LlmInvoker {
        private final BaseError firstChunkError;

        private PromptAwareFailingInvoker(BaseError firstChunkError) {
            this.firstChunkError = firstChunkError;
        }

        @Override
        public CompletableFuture<? extends BaseMessage> invoke(List<BaseMessage> messages, Map<String, Object> options) {
            String prompt = messages.getFirst().getContentAsString();
            if (prompt.contains("Passage:\nfirst")) {
                return CompletableFuture.failedFuture(firstChunkError);
            }
            return CompletableFuture.failedFuture(new IllegalStateException("bad json"));
        }
    }

    private static TripleExtractor.ParseResult parseTriples(String content) {
        return new TripleExtractor(new RecordingInvoker(), "m").parseTriples(content, "d1", "c1");
    }

    private static BaseError expectExtractionError(TripleExtractor extractor, List<TextChunk> chunks) {
        CompletionException exception = assertThrows(CompletionException.class, () -> extractor.extract(chunks).join());
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof BaseError);
        return (BaseError) exception.getCause();
    }

    private static Object fieldValue(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
