/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.extractor;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LLMTripleExtractorTest {

    @Test
    void extractParsesObjectWrapperAndIgnoresConfidence() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(new QueueLlmClient(
                "{\"triples\":[[\"Alice\",\"knows\",\"Bob\",0.8],[\"Bob\",\"works_at\",\"ACME\"]]}"), "test-model");

        List<Triple> triples = extractor.extract(List.of(new TextChunk("chunk-1", "Alice knows Bob", "doc-1")), Map.of());

        assertEquals(2, triples.size());
        assertEquals("Alice", triples.get(0).getSubject());
        assertEquals("doc-1", triples.get(0).getMetadata().get("doc_id"));
        assertEquals("chunk-1", triples.get(0).getMetadata().get("chunk_id"));
    }

    @Test
    void extractRejectsInvalidJson() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(new QueueLlmClient("not json"), "test-model");
        BaseError error = assertThrows(BaseError.class,
                () -> extractor.extract(List.of(new TextChunk("chunk-1", "Alice knows Bob", "doc-1")), Map.of()));
        assertEquals(StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR, error.getStatus());
        org.assertj.core.api.Assertions.assertThat(error.getMessage()).contains("parsed");
    }

    @Test
    void extractReturnsEmptyForEmptyChunks() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(new QueueLlmClient("[]"), "test-model");
        assertEquals(List.of(), extractor.extract(List.of(), Map.of()));
    }

    @Test
    void extractShouldRaiseFirstErrorInChunkOrder() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(new QueueLlmClient(
                "not json",
                "{\"triples\":[[\"Bob\",\"works_at\",\"ACME\"]]}"), "test-model", 0.0f, 2);

        BaseError error = assertThrows(BaseError.class, () -> extractor.extract(List.of(
                new TextChunk("chunk-1", "bad", "doc-1"),
                new TextChunk("chunk-2", "Bob works at ACME", "doc-1")), Map.of()));

        org.assertj.core.api.Assertions.assertThat(error.getMessage()).contains("chunk-1");
    }

    @Test
    void parseTriplesShouldAcceptArrayWrapperDictAndMarkdownFence() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(new QueueLlmClient("[]"), "test-model");
        TextChunk chunk = new TextChunk("chunk-1", "Alice knows Bob", "doc-1");

        assertEquals(1, extractor.parseTriples("[[\"a\", \"b\", \"c\"]]", chunk).triples().size());
        assertEquals(1, extractor.parseTriples("{\"triples\": [[\"x\", \"y\", \"z\"]]}", chunk).triples().size());
        assertEquals(1, extractor.parseTriples("```json\n{\"triples\": [[\"m\", \"n\", \"o\"]]}\n```", chunk).triples().size());
    }

    @Test
    void parseTriplesShouldFailMissingTriplesAndAllInvalidButAllowEmptyList() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(new QueueLlmClient("[]"), "test-model");
        TextChunk chunk = new TextChunk("chunk-1", "Alice knows Bob", "doc-1");

        org.assertj.core.api.Assertions.assertThat(extractor.parseTriples("{\"named_entities\": [\"Alice\"]}", chunk).isSuccess()).isFalse();
        org.assertj.core.api.Assertions.assertThat(extractor.parseTriples("{\"triples\": []}", chunk).isSuccess()).isTrue();
        org.assertj.core.api.Assertions.assertThat(extractor.parseTriples("{\"triples\": [[\"x\"], {\"bad\": 1}]}", chunk).isSuccess()).isFalse();
    }

    @Test
    void parseTriplesShouldIgnoreInvalidItemsAndNestedValues() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(new QueueLlmClient("[]"), "test-model");
        TextChunk chunk = new TextChunk("chunk-1", "Alice knows Bob", "doc-1");

        List<Triple> triples = extractor.parseTriples(
                "{\"triples\": [[\"a\", \"b\", \"c\"], [\"x\"], {\"bad\": 1}, [\"y\", [\"nested\"], \"z\"]]}",
                chunk).triples();

        assertEquals(1, triples.size());
        assertEquals("a", triples.get(0).getSubject());
    }

    @Test
    void buildPromptShouldExposePythonPromptShape() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(new QueueLlmClient("[]"), "test-model");

        String prompt = extractor.buildPrompt("Alice knows Bob", "DocTitle");

        org.assertj.core.api.Assertions.assertThat(prompt)
                .contains("RDF-style graph")
                .contains("named_entities")
                .contains("Magic Johnson")
                .contains("Elden Ring")
                .contains("DocTitle")
                .contains("Alice knows Bob");
    }

    private static final class QueueLlmClient extends BaseModelClient {
        private final Queue<String> responses = new ArrayDeque<>();

        private QueueLlmClient(String... responses) {
            super(
                    ModelRequestConfig.builder().modelName("test-model").build(),
                    ModelClientConfig.builder()
                            .clientProvider("test")
                            .apiKey("key")
                            .apiBase("http://localhost")
                            .verifySsl(false)
                            .build());
            this.responses.addAll(List.of(responses));
        }

        @Override
        public AssistantMessage invoke(Object messages,
                                       Object tools,
                                       Float temperature,
                                       Float topP,
                                       String model,
                                       Integer maxTokens,
                                       String stop,
                                       BaseOutputParser outputParser,
                                       Float timeout,
                                       Map<String, Object> kwargs) {
            return new AssistantMessage(responses.isEmpty() ? "" : responses.remove());
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages,
                                                      Object tools,
                                                      Float temperature,
                                                      Float topP,
                                                      String model,
                                                      Integer maxTokens,
                                                      String stop,
                                                      BaseOutputParser outputParser,
                                                      Float timeout,
                                                      Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages,
                                                     String model,
                                                     String size,
                                                     String negativePrompt,
                                                     int n,
                                                     boolean promptExtend,
                                                     boolean watermark,
                                                     int seed,
                                                     Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                      String model,
                                                      String voice,
                                                      String languageType,
                                                      Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages,
                                                     String imgUrl,
                                                     String audioUrl,
                                                     String model,
                                                     String size,
                                                     String resolution,
                                                     int duration,
                                                     boolean promptExtend,
                                                     boolean watermark,
                                                     String negativePrompt,
                                                     Integer seed,
                                                     Map<String, Object> kwargs) {
            return null;
        }
    }
}
