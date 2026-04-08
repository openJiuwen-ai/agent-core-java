/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.extractor;

import com.openjiuwen.core.common.exception.BaseError;
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
    void extractParsesObjectWrapperAndConfidence() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(new QueueLlmClient(
                "{\"triples\":[[\"Alice\",\"knows\",\"Bob\",0.8],[\"Bob\",\"works_at\",\"ACME\"]]}"), "test-model");

        List<Triple> triples = extractor.extract(List.of(new TextChunk("chunk-1", "Alice knows Bob", "doc-1")), Map.of());

        assertEquals(2, triples.size());
        assertEquals("Alice", triples.get(0).getSubject());
        assertEquals(0.8, triples.get(0).getConfidence());
        assertEquals("doc-1", triples.get(0).getMetadata().get("doc_id"));
        assertEquals("chunk-1", triples.get(0).getMetadata().get("chunk_id"));
    }

    @Test
    void extractRejectsInvalidJson() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(new QueueLlmClient("not json"), "test-model");
        assertThrows(BaseError.class, () -> extractor.extract(List.of(new TextChunk("chunk-1", "Alice knows Bob", "doc-1")), Map.of()));
    }

    @Test
    void extractReturnsEmptyForEmptyChunks() {
        LLMTripleExtractor extractor = new LLMTripleExtractor(new QueueLlmClient("[]"), "test-model");
        assertEquals(List.of(), extractor.extract(List.of(), Map.of()));
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
