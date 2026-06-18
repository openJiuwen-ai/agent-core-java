/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.query_rewriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context_engine.ContextStats;
import com.openjiuwen.core.context_engine.ContextWindow;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link QueryRewriter}.
 *
 * <p>Mirrors Python's {@code test_query_rewriter.py} in
 * {@code tests/unit_tests/core/retrieval/query_rewriter/test_query_rewriter.py}.</p>
 */
class QueryRewriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fillTemplateReplacesExplicitPlaceholdersOnly() {
        assertEquals("a=1 b=2", QueryRewriter.fillTemplate("a={a} b={b}", Map.of("a", "1", "b", "2")));
        assertEquals(
                "output: hi, example: {\"x\":1}",
                QueryRewriter.fillTemplate("output: {history}, example: {\"x\":1}", Map.of("history", "hi"))
        );
    }

    @Test
    void extractJsonTakesFirstOpenAndLastClose() {
        assertEquals("{\"a\":1}", QueryRewriter.extractJson("prefix {\"a\":1} suffix"));
        assertEquals("", QueryRewriter.extractJson("no json here"));
        assertEquals("", QueryRewriter.extractJson("{"));
        assertEquals("{\"outer\":{\"inner\":1}}", QueryRewriter.extractJson(" {\"outer\":{\"inner\":1}} "));
    }

    @Test
    void parseLlmJsonReturnsOnlyJsonObjectsAndRepairsTrailingComma() {
        assertEquals(Map.of("a", 1), QueryRewriter.parseLlmJson("{\"a\":1}"));
        assertNull(QueryRewriter.parseLlmJson(""));
        assertNull(QueryRewriter.parseLlmJson("   "));
        assertNull(QueryRewriter.parseLlmJson("not json"));
        assertNull(QueryRewriter.parseLlmJson("[1,2,3]"));
        assertNull(QueryRewriter.parseLlmJson("null"));

        Map<String, Object> repaired = QueryRewriter.parseLlmJson("{\"theme\":[\"a\"],\"summary\":\"b\",}");
        assertNotNull(repaired);
        assertEquals(List.of("a"), repaired.get("theme"));
        assertEquals("b", repaired.get("summary"));
    }

    @Test
    void forceHelpersCoercePythonDynamicValues() {
        assertEquals("x", QueryRewriter.forceString("x"));
        assertTrue(QueryRewriter.forceString(Map.of("a", 1)).contains("\"a\""));

        assertEquals(List.of(1, 2), QueryRewriter.forceList(List.of(1, 2)));
        assertEquals(List.of("x"), QueryRewriter.forceList("x"));

        assertEquals(Map.of("a", 1), QueryRewriter.forceJson("k", Map.of("a", 1)));
        assertEquals(Map.of("a", 1), QueryRewriter.forceJson("k", "{\"a\":1}"));
        assertEquals(Map.of("k", List.of(1, 2)), QueryRewriter.forceJson("k", "[1,2]"));
        assertEquals(Map.of("k", "plain"), QueryRewriter.forceJson("k", "plain"));
    }

    @Test
    void schemaRepairFillsDefaultsAndCoercesRewriteFields() {
        Map<String, Object> nullable = new LinkedHashMap<>();
        nullable.put("theme", null);
        nullable.put("summary", null);
        Map<String, Object> repairedNullable = QueryRewriter.schemaRepair(nullable, QueryRewriter.compressSchema());
        assertEquals(new ArrayList<>(), repairedNullable.get("theme"));
        assertEquals("", repairedNullable.get("summary"));

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("before", 1);
        raw.put("intention", null);
        raw.put("standalone_query", "淘美乐退货的运费谁出？");
        raw.put("references", "{\"那\":\"退货运费\"}");
        raw.put("missing", "x");
        raw.put("typo", List.of(Map.of("original", "teh", "corrected", "the", "reason", 1)));
        raw.put("gibberish", null);
        raw.put("from_history", null);

        Map<String, Object> out = QueryRewriter.schemaRepair(raw, QueryRewriter.rewriteSchema());
        assertEquals("1", out.get("before"));
        assertEquals("", out.get("intention"));
        assertEquals("淘美乐退货的运费谁出？", out.get("standalone_query"));
        assertEquals(Map.of("那", "退货运费"), out.get("references"));
        assertEquals(List.of("x"), out.get("missing"));
        assertEquals(new ArrayList<>(), out.get("gibberish"));
        assertEquals("", out.get("from_history"));

        List<?> typo = (List<?>) out.get("typo");
        assertEquals(1, typo.size());
        assertEquals("1", ((Map<?, ?>) typo.getFirst()).get("reason"));
    }

    @Test
    void schemaRepairRejectsNonDictOutput() {
        BaseError error = assertThrows(
                BaseError.class,
                () -> QueryRewriter.schemaRepair(null, Map.of("a", String.class))
        );
        assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID, error.getStatus());
    }

    @Test
    void loadTemplateReadsBundledPromptAndCachesIt() {
        QueryRewriter rewriter = rewriterWith(new SimpleContext(), 5, makeFullRewriteResponse("x"));
        String first = rewriter.loadTemplate("intention_completion");
        String second = rewriter.loadTemplate("intention_completion");

        assertFalse(first.isBlank());
        assertTrue(first.contains("standalone_query") || first.contains("角色"));
        assertEquals(first, second);
    }

    @Test
    void loadTemplateMissingLanguageRaises() {
        QueryRewriter rewriter = new QueryRewriter(queueModel("{}"), new SimpleContext(), 5, "missing_lang");

        BaseError error = assertThrows(BaseError.class, () -> rewriter.loadTemplate("intention_completion"));

        assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_PROMPT_NOT_FOUND, error.getStatus());
    }

    @Test
    void msgToTextFormatsExplicitMessagesAndContextMessages() {
        SimpleContext context = new SimpleContext();
        context.addMessages(List.of(new UserMessage("你好"), new AssistantMessage("你好！")));
        QueryRewriter rewriter = rewriterWith(context, 5, makeFullRewriteResponse("x"));

        String explicit = rewriter.msgToText(List.of(new UserMessage("今天天气如何？"), new AssistantMessage("晴天。")));
        String fromContext = rewriter.msgToText(null);

        assertTrue(explicit.contains("user: 今天天气如何？"));
        assertTrue(explicit.contains("assistant: 晴天。"));
        assertTrue(fromContext.contains("user: 你好"));
        assertTrue(fromContext.contains("assistant: 你好！"));
    }

    @Test
    void msgToTextSerializesMapAndListContentAsJson() {
        QueryRewriter rewriter = rewriterWith(new SimpleContext(), 5, makeFullRewriteResponse("x"));
        List<BaseMessage> messages = List.of(
                new SystemMessage("plain"),
                BaseMessage.builder().role("user").content(Map.of("k", "v")).build(),
                BaseMessage.builder().role("assistant").content(List.of("a", "b")).build()
        );

        String text = rewriter.msgToText(messages);

        assertTrue(text.contains("system: plain"));
        assertTrue(text.contains("user: {\"k\":\"v\"}"));
        assertTrue(text.contains("assistant: [\"a\",\"b\"]"));
    }

    @Test
    void compressParsesValidMockResponse() {
        QueryRewriter rewriter = rewriterWith(new SimpleContext(), 5, makeCompressResponse());

        Map<String, Object> result = rewriter.compress(List.of(new UserMessage("用户问"), new AssistantMessage("助手答")));

        assertEquals(List.of("主题"), result.get("theme"));
        assertEquals("摘要内容", result.get("summary"));
    }

    @Test
    void compressInvalidJsonRaisesOutputInvalid() {
        QueryRewriter rewriter = rewriterWith(new SimpleContext(), 5, "not valid json at all");

        BaseError error = assertThrows(
                BaseError.class,
                () -> rewriter.compress(List.of(new UserMessage("用户问"), new AssistantMessage("助手答")))
        );

        assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID, error.getStatus());
    }

    @Test
    void rewriteValidMockReturnsStructuredPayload() {
        SimpleContext context = new SimpleContext();
        context.addMessages(List.of(new UserMessage("你好"), new AssistantMessage("你好！")));
        QueryRewriter rewriter = rewriterWith(context, 5, makeFullRewriteResponse("那运费呢？"));

        Map<String, Object> result = rewriter.rewrite("那运费呢？");

        assertEquals("那运费呢？", result.get("standalone_query"));
        assertTrue(result.containsKey("before"));
        assertTrue(result.containsKey("intention"));
    }

    @Test
    void rewriteExtractsJsonFromPrefixAndSuffix() {
        SimpleContext context = new SimpleContext();
        context.addMessages(List.of(new UserMessage("你好"), new AssistantMessage("你好！")));
        String payload = makeFullRewriteResponse("测试");
        QueryRewriter rewriter = rewriterWith(context, 5, "这是回答：\n" + payload + "\n以上是结果。");

        assertEquals("测试", rewriter.rewrite("测试").get("standalone_query"));
    }

    @Test
    void rewriteRejectsInvalidInputAndInvalidOutput() {
        QueryRewriter blankRewriter = rewriterWith(new SimpleContext(), 5, makeFullRewriteResponse("x"));
        BaseError blank = assertThrows(BaseError.class, () -> blankRewriter.rewrite("   "));
        assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_INPUT_INVALID, blank.getStatus());

        SimpleContext context = new SimpleContext();
        context.addMessages(List.of(new UserMessage("你好"), new AssistantMessage("你好！")));
        QueryRewriter invalidOutput = rewriterWith(context, 5, "not json");
        BaseError output = assertThrows(BaseError.class, () -> invalidOutput.rewrite("问题"));
        assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID, output.getStatus());
    }

    @Test
    void rewriteTriggersCompressionAndRepairsTypoSchema() {
        SimpleContext context = new SimpleContext();
        context.addMessages(List.of(new UserMessage("你好"), new AssistantMessage("你好！")));
        QueryRewriter rewriter = rewriterWith(context, 2, makeCompressResponse(), makeRewriteResponseWithTypo("那运费呢？"));

        Map<String, Object> result = rewriter.rewrite("那运费呢？");

        assertEquals("那运费呢？", result.get("standalone_query"));
        assertEquals(1, context.getMessages(null, true).size());
        assertEquals("system", context.getMessages(null, true).getFirst().getRole());
        assertEquals("compressed_history", context.getMessages(null, true).getFirst().getName());
        assertInstanceOf(List.class, result.get("typo"));
        assertEquals(1, ((List<?>) result.get("typo")).size());
    }

    @Test
    void rewriteCompressionFailureFallsBackToOriginalHistory() {
        SimpleContext context = new SimpleContext();
        for (int i = 0; i < 3; i++) {
            context.addMessages(List.of(new UserMessage("用户问" + i), new AssistantMessage("助手答" + i)));
        }
        QueryRewriter rewriter = rewriterWith(context, 5, "not json", makeFullRewriteResponse("总结一下"));

        Map<String, Object> result = rewriter.rewrite("总结一下");

        assertEquals("总结一下", result.get("standalone_query"));
        assertEquals(1, context.getMessages(null, true).size());
        assertEquals("original_history", context.getMessages(null, true).getFirst().getName());
    }

    @Test
    void rewriteUsesTrailingCommaRepair() throws JsonProcessingException {
        String broken = "{\"before\":\"x\",\"intention\":\"y\",\"standalone_query\":\"x\",\"references\":{},"
                + "\"missing\":[],\"typo\":[],\"gibberish\":[],\"from_history\":\"\",}";
        assertThrows(JsonProcessingException.class, () -> MAPPER.readValue(broken, Map.class));
        assertEquals("x", QueryRewriter.parseLlmJson(broken).get("standalone_query"));

        SimpleContext context = new SimpleContext();
        context.addMessages(List.of(new UserMessage("你好"), new AssistantMessage("你好！")));
        QueryRewriter rewriter = rewriterWith(context, 5, broken);

        assertEquals("x", rewriter.rewrite("x").get("standalone_query"));
    }

    @Test
    void fullConversationCompressesWhenHistoryReachesRange() {
        SimpleContext context = new SimpleContext();
        QueryRewriter rewriter = rewriterWith(
                context,
                5,
                makeFullRewriteResponse("那运费呢？"),
                makeCompressResponse(),
                makeFullRewriteResponse("会员怎么升级？"),
                makeCompressResponse(),
                makeFullRewriteResponse("生鲜能退吗？")
        );
        List<String[]> conversation = List.of(
                new String[]{"你们这个淘美乐 App 是干什么的？", "淘美乐是一款综合购物与生活服务的 App。"},
                new String[]{"怎么注册和登录？", "使用手机号验证码或第三方账号登录。"},
                new String[]{"我想买点日用品，从哪里进？", "首页有日百等入口。"},
                new String[]{"搜索出来的结果太多，怎么筛选？", "搜索结果页有筛选按钮。"},
                new String[]{"下单后多久能送到？", "一般 1 到 3 天送达。"},
                new String[]{"可以修改订单吗？", "待发货状态下可以修改或取消。"}
        );
        List<Integer> rewriteAfterTurns = List.of(2, 4, 6);

        for (int turn = 1; turn <= conversation.size(); turn++) {
            String[] pair = conversation.get(turn - 1);
            context.addMessages(List.of(new UserMessage(pair[0]), new AssistantMessage(pair[1])));
            if (rewriteAfterTurns.contains(turn)) {
                String query = turn == 2 ? "那运费呢？" : turn == 4 ? "会员怎么升级？" : "生鲜能退吗？";
                assertEquals(query, rewriter.rewrite(query).get("standalone_query"));
            }
        }

        assertTrue(context.getMessages(null, true).size() <= 5);
    }

    private static QueryRewriter rewriterWith(SimpleContext context, int compressRange, String... responses) {
        return new QueryRewriter(queueModel(responses), context, compressRange, "zh");
    }

    private static Model queueModel(String... responses) {
        Queue<String> queue = new ArrayDeque<>(List.of(responses));
        return new Model((List<BaseMessage> messages,
                          ModelRequestConfig modelConfig,
                          ModelClientConfig modelClientConfig,
                          ModelInvokeOptions options) ->
                CompletableFuture.completedFuture(new AssistantMessage(queue.isEmpty() ? "" : queue.remove())));
    }

    private static String makeCompressResponse() {
        return writeJson(Map.of("theme", List.of("主题"), "summary", "摘要内容"));
    }

    private static String makeFullRewriteResponse(String standaloneQuery) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("before", standaloneQuery);
        payload.put("intention", "用户咨询");
        payload.put("standalone_query", standaloneQuery);
        payload.put("references", new LinkedHashMap<>());
        payload.put("missing", new ArrayList<>());
        payload.put("typo", new ArrayList<>());
        payload.put("gibberish", new ArrayList<>());
        payload.put("from_history", "");
        return writeJson(payload);
    }

    private static String makeRewriteResponseWithTypo(String standaloneQuery) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("before", standaloneQuery);
        payload.put("intention", "用户咨询");
        payload.put("standalone_query", standaloneQuery);
        payload.put("references", new LinkedHashMap<>());
        payload.put("missing", new ArrayList<>());
        payload.put("typo", "teh");
        payload.put("gibberish", new ArrayList<>());
        payload.put("from_history", "");
        return writeJson(payload);
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class SimpleContext implements ModelContext {
        private final List<BaseMessage> history = new ArrayList<>();

        @Override
        public int length() {
            return history.size();
        }

        @Override
        public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
            if (size == null || size >= history.size()) {
                return new ArrayList<>(history);
            }
            return new ArrayList<>(history.subList(history.size() - size, history.size()));
        }

        @Override
        public void setMessages(List<BaseMessage> messages, boolean withHistory) {
            history.clear();
            if (messages != null) {
                history.addAll(messages);
            }
        }

        @Override
        public List<BaseMessage> popMessages(int size, boolean withHistory) {
            int fromIndex = Math.max(0, history.size() - size);
            List<BaseMessage> removed = new ArrayList<>(history.subList(fromIndex, history.size()));
            history.subList(fromIndex, history.size()).clear();
            return removed;
        }

        @Override
        public CompletionStage<Void> clearMessages(boolean withHistory) {
            history.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(BaseMessage message) {
            history.add(message);
            return CompletableFuture.completedFuture(new ArrayList<>(history));
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(List<BaseMessage> messages) {
            history.addAll(messages);
            return CompletableFuture.completedFuture(new ArrayList<>(history));
        }

        @Override
        public CompletionStage<ContextWindow> getContextWindow(List<BaseMessage> systemMessages,
                                                               List<ToolInfo> tools,
                                                               Integer windowSize,
                                                               Integer dialogueRound,
                                                               Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public ContextStats statistic() {
            return null;
        }

        @Override
        public String sessionId() {
            return "session";
        }

        @Override
        public String contextId() {
            return "context";
        }

        @Override
        public TokenCounterPort tokenCounter() {
            return null;
        }

        @Override
        public ToolPort reloaderTool() {
            return null;
        }
    }
}
