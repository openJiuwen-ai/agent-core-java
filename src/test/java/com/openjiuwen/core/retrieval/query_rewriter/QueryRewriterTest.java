/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.query_rewriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
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
import com.openjiuwen.core.retrieval.common.RetrievalResult;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

class QueryRewriterTest {
    @Test
    void helperMethodsRepairJsonAndTemplate() {
        assertEquals("a=1 b=2", QueryRewriter.fillTemplate("a={a} b={b}", Map.of("a", "1", "b", "2")));
        Map<String, Object> parsed = QueryRewriter.parseLlmJson("{\"standalone_query\":\"x\",}");
        assertEquals("x", parsed.get("standalone_query"));
    }

    @Test
    void loadTemplateReadsBundledPrompt() {
        QueryRewriter rewriter = new QueryRewriter(new QueueLlmClient("{}"));
        assertTrue(rewriter.loadTemplate("intention_completion").contains("standalone_query"));
    }

    @Test
    void rewriteWithRetrievalContextParsesJsonPayload() {
        QueryRewriter rewriter = new QueryRewriter(new QueueLlmClient(
                "prefix {\"before\":\"q\",\"intention\":\"ask\",\"standalone_query\":\"standalone\",\"references\":{},\"missing\":[],\"typo\":[],\"gibberish\":[],\"from_history\":\"\"} suffix"));

        String rewritten = rewriter.rewrite("q", List.of(new RetrievalResult("doc", 0.5)));

        assertEquals("standalone", rewritten);
    }

    @Test
    void contextAwareRewriteSupportsCompressionAndSchemaRepair() {
        SessionModelContext context = new SessionModelContext("ctx", "session",
                ContextEngineConfig.builder().maxContextMessageNum(50).defaultWindowMessageNum(50).build(), List.of(),
                List.of(), null);
        context.addMessages(List.of(new UserMessage("你好"), new AssistantMessage("你好！")));

        QueryRewriter rewriter = new QueryRewriter(new QueueLlmClient("{\"theme\":[\"主题\"],\"summary\":\"摘要\"}",
                "{\"before\":\"那运费呢？\",\"intention\":\"咨询\",\"standalone_query\":\"退货运费是谁承担\","
                        + "\"references\":{},\"missing\":[],\"typo\":\"teh\",\"gibberish\":[],\"from_history\":\"摘要\",}"),
                context, 2, "zh");

        Map<String, Object> rewritten = rewriter.rewrite("那运费呢？");

        assertEquals("退货运费是谁承担", rewritten.get("standalone_query"));
        assertEquals(1, context.getMessages(null, true).size());
        assertEquals("system", context.getMessages(null, true).get(0).getRole());
        assertEquals(1, ((List<?>) rewritten.get("typo")).size());
    }

    @Test
    void rewriteRejectsBlankInput() {
        QueryRewriter rewriter = new QueryRewriter(new QueueLlmClient("{}"));
        assertThrows(BaseError.class, () -> rewriter.rewrite(" ", List.of()));
    }

    private static final class QueueLlmClient extends BaseModelClient {
        private final Queue<String> responses = new ArrayDeque<>();

        private QueueLlmClient(String... responses) {
            super(ModelRequestConfig.builder().modelName("test-model").build(), ModelClientConfig.builder()
                    .clientProvider("test").apiKey("key").apiBase("http://localhost").verifySsl(false).build());
            this.responses.addAll(List.of(responses));
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            return new AssistantMessage(responses.isEmpty() ? "" : responses.remove());
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed,
                Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                String languageType, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark,
                String negativePrompt, Integer seed, Map<String, Object> kwargs) {
            return null;
        }
    }
}
