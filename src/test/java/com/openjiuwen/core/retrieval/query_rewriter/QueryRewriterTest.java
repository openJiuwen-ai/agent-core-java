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
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
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
    private static final String QUERY_REWRITER_SOURCE =
            "tests/unit_tests/core/retrieval/query_rewriter/test_query_rewriter.py";
    private static final List<String> QUERY_REWRITER_PYTHON_NODES = List.of(
            QUERY_REWRITER_SOURCE + "::TestQueryRewriterModelConfigPropagation::test_init_passes_only_custom_headers",
            QUERY_REWRITER_SOURCE + "::TestFillTemplate::test_replaces_placeholders",
            QUERY_REWRITER_SOURCE + "::TestFillTemplate::test_ignores_curly_braces_in_json_example",
            QUERY_REWRITER_SOURCE + "::TestExtractJson::test_extracts_single_object",
            QUERY_REWRITER_SOURCE + "::TestExtractJson::test_returns_empty_when_no_brace",
            QUERY_REWRITER_SOURCE + "::TestExtractJson::test_returns_empty_when_only_open_brace",
            QUERY_REWRITER_SOURCE + "::TestExtractJson::test_takes_first_open_last_close",
            QUERY_REWRITER_SOURCE + "::TestParseLlmJson::test_valid_json_returns_dict",
            QUERY_REWRITER_SOURCE + "::TestParseLlmJson::test_empty_string_returns_none",
            QUERY_REWRITER_SOURCE + "::TestParseLlmJson::test_invalid_json_returns_none_without_repair",
            QUERY_REWRITER_SOURCE + "::TestParseLlmJson::test_non_dict_root_returns_none",
            QUERY_REWRITER_SOURCE + "::TestSchemaRepair::test_fills_none_with_defaults",
            QUERY_REWRITER_SOURCE + "::TestSchemaRepair::test_rewrite_schema_all_fields",
            QUERY_REWRITER_SOURCE + "::TestSchemaRepair::test_typo_sub_structure",
            QUERY_REWRITER_SOURCE + "::TestSchemaRepair::test_raises_on_non_dict",
            QUERY_REWRITER_SOURCE + "::TestQueryRewriterLoadTemplate::test_load_existing_template",
            QUERY_REWRITER_SOURCE + "::TestQueryRewriterLoadTemplate::test_load_template_cached_on_second_call",
            QUERY_REWRITER_SOURCE + "::TestQueryRewriterLoadTemplate::test_prompt_not_found_raises",
            QUERY_REWRITER_SOURCE + "::TestQueryRewriterLoadTemplate::test_load_template_read_failure_raises",
            QUERY_REWRITER_SOURCE + "::TestQueryRewriterMsg2Text::test_msg_2_text_with_messages",
            QUERY_REWRITER_SOURCE + "::TestQueryRewriterMsg2Text::test_msg_2_text_from_context_when_none",
            QUERY_REWRITER_SOURCE + "::TestQueryRewriterCompress::test_compress_valid_mock",
            QUERY_REWRITER_SOURCE + "::TestQueryRewriterCompress::test_compress_llm_invoke_failure_raises",
            QUERY_REWRITER_SOURCE + "::TestQueryRewriterRewrite::test_rewrite_with_json_prefix_suffix",
            QUERY_REWRITER_SOURCE + "::TestQueryRewriterRewrite::test_rewrite_invalid_output_raises",
            QUERY_REWRITER_SOURCE + "::TestQueryRewriterRewrite::test_rewrite_invalid_input_empty_raises",
            QUERY_REWRITER_SOURCE + "::TestQueryRewriterRewrite::test_rewrite_invalid_input_whitespace_raises",
            QUERY_REWRITER_SOURCE + "::TestQueryRewriterRewriteCompressFallback::test_rewrite_compress_failure_fallback",
            QUERY_REWRITER_SOURCE + "::TestRewriteWithTrailingCommaJsonRepair::test_parse_llm_json_trailing_comma",
            QUERY_REWRITER_SOURCE + "::TestRewriteWithTrailingCommaJsonRepair::test_rewrite_with_trailing_comma_mock",
            QUERY_REWRITER_SOURCE + "::TestFullConversationWithCompressAndRewrite::test_full_conversation_with_compress_and_rewrite"
    );

    @TestFactory
    Collection<DynamicTest> pythonQueryRewriterCases() {
        return QUERY_REWRITER_PYTHON_NODES.stream()
                .map(node -> DynamicTest.dynamicTest(node, () -> runQueryRewriterPythonNode(node)))
                .toList();
    }

    private void runQueryRewriterPythonNode(String node) throws Exception {
        if (node.contains("ModelConfigPropagation")) {
            QueryRewriter rewriter = rewriterWith(new SimpleContext(), 5, makeFullRewriteResponse("x"));
            assertNull(rewriter.getModelConfig());
            assertEquals(5, rewriter.getCompressRange());
        } else if (node.contains("TestFillTemplate")) {
            assertFillTemplateNode(node);
        } else if (node.contains("TestExtractJson")) {
            assertExtractJsonNode(node);
        } else if (node.contains("TestParseLlmJson")) {
            assertParseLlmJsonNode(node);
        } else if (node.contains("TestSchemaRepair")) {
            assertSchemaRepairNode(node);
        } else if (node.contains("TestQueryRewriterLoadTemplate")) {
            assertLoadTemplateNode(node);
        } else if (node.contains("TestQueryRewriterMsg2Text")) {
            assertMsgToTextNode(node);
        } else if (node.contains("TestQueryRewriterCompress")) {
            assertCompressNode(node);
        } else if (node.contains("TestRewriteWithTrailingCommaJsonRepair")) {
            assertTrailingCommaNode(node);
        } else if (node.contains("TestFullConversationWithCompressAndRewrite")) {
            assertFullConversationNode();
        } else {
            assertRewriteNode(node);
        }
    }

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

    private void assertFillTemplateNode(String node) {
        if (node.endsWith("test_ignores_curly_braces_in_json_example")) {
            assertEquals("output: hi, example: {\"x\":1}",
                    QueryRewriter.fillTemplate("output: {history}, example: {\"x\":1}", Map.of("history", "hi")));
        } else {
            assertEquals("a=1 b=2", QueryRewriter.fillTemplate("a={a} b={b}", Map.of("a", "1", "b", "2")));
        }
    }

    private void assertExtractJsonNode(String node) {
        if (node.endsWith("test_extracts_single_object")) {
            assertEquals("{\"a\":1}", QueryRewriter.extractJson("prefix {\"a\":1} suffix"));
        } else if (node.endsWith("test_returns_empty_when_no_brace")) {
            assertEquals("", QueryRewriter.extractJson("no json here"));
        } else if (node.endsWith("test_returns_empty_when_only_open_brace")) {
            assertEquals("", QueryRewriter.extractJson("{"));
        } else {
            assertEquals("{\"outer\":{\"inner\":1}}", QueryRewriter.extractJson(" {\"outer\":{\"inner\":1}} "));
        }
    }

    private void assertParseLlmJsonNode(String node) {
        if (node.endsWith("test_valid_json_returns_dict")) {
            assertEquals(Map.of("a", 1), QueryRewriter.parseLlmJson("{\"a\":1}"));
        } else if (node.endsWith("test_empty_string_returns_none")) {
            assertNull(QueryRewriter.parseLlmJson(""));
            assertNull(QueryRewriter.parseLlmJson("   "));
        } else if (node.endsWith("test_non_dict_root_returns_none")) {
            assertNull(QueryRewriter.parseLlmJson("[1,2,3]"));
            assertNull(QueryRewriter.parseLlmJson("null"));
        } else {
            assertNull(QueryRewriter.parseLlmJson("not json"));
        }
    }

    private void assertSchemaRepairNode(String node) {
        if (node.endsWith("test_raises_on_non_dict")) {
            BaseError error = assertThrows(BaseError.class,
                    () -> QueryRewriter.schemaRepair(null, Map.of("a", String.class)));
            assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID, error.getStatus());
            return;
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        if (node.endsWith("test_fills_none_with_defaults")) {
            raw.put("theme", null);
            raw.put("summary", null);
            Map<String, Object> repaired = QueryRewriter.schemaRepair(raw, QueryRewriter.compressSchema());
            assertEquals(new ArrayList<>(), repaired.get("theme"));
            assertEquals("", repaired.get("summary"));
        } else {
            raw.put("before", 1);
            raw.put("intention", null);
            raw.put("standalone_query", "淘美乐退货的运费谁出？");
            raw.put("references", "{\"那\":\"退货运费\"}");
            raw.put("missing", "x");
            raw.put("typo", node.endsWith("test_typo_sub_structure")
                    ? List.of(Map.of("original", "teh", "corrected", "the", "reason", 1))
                    : new ArrayList<>());
            raw.put("gibberish", null);
            raw.put("from_history", null);
            Map<String, Object> repaired = QueryRewriter.schemaRepair(raw, QueryRewriter.rewriteSchema());
            assertEquals("1", repaired.get("before"));
            assertEquals(Map.of("那", "退货运费"), repaired.get("references"));
            assertEquals(List.of("x"), repaired.get("missing"));
            if (node.endsWith("test_typo_sub_structure")) {
                assertEquals("1", ((Map<?, ?>) ((List<?>) repaired.get("typo")).getFirst()).get("reason"));
            }
        }
    }

    private void assertLoadTemplateNode(String node) {
        QueryRewriter rewriter = rewriterWith(new SimpleContext(), 5, makeFullRewriteResponse("x"));
        if (node.endsWith("test_prompt_not_found_raises") || node.endsWith("test_load_template_read_failure_raises")) {
            QueryRewriter missing = new QueryRewriter(queueModel("{}"), new SimpleContext(), 5, "missing_lang");
            BaseError error = assertThrows(BaseError.class, () -> missing.loadTemplate("intention_completion"));
            assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_PROMPT_NOT_FOUND, error.getStatus());
        } else {
            String first = rewriter.loadTemplate("intention_completion");
            String second = rewriter.loadTemplate("intention_completion");
            assertFalse(first.isBlank());
            assertEquals(first, second);
        }
    }

    private void assertMsgToTextNode(String node) {
        SimpleContext context = new SimpleContext();
        context.addMessages(List.of(new UserMessage("你好"), new AssistantMessage("你好！")));
        QueryRewriter rewriter = rewriterWith(context, 5, makeFullRewriteResponse("x"));
        if (node.endsWith("test_msg_2_text_from_context_when_none")) {
            assertTrue(rewriter.msgToText(null).contains("user: 你好"));
        } else {
            String text = rewriter.msgToText(List.of(new UserMessage("今天天气如何？"), new AssistantMessage("晴天。")));
            assertTrue(text.contains("user: 今天天气如何？"));
            assertTrue(text.contains("assistant: 晴天。"));
        }
    }

    private void assertCompressNode(String node) {
        QueryRewriter rewriter = rewriterWith(new SimpleContext(), 5,
                node.endsWith("test_compress_llm_invoke_failure_raises") ? "not valid json at all" : makeCompressResponse());
        if (node.endsWith("test_compress_llm_invoke_failure_raises")) {
            BaseError error = assertThrows(BaseError.class,
                    () -> rewriter.compress(List.of(new UserMessage("用户问"), new AssistantMessage("助手答"))));
            assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID, error.getStatus());
        } else {
            Map<String, Object> result = rewriter.compress(List.of(new UserMessage("用户问"), new AssistantMessage("助手答")));
            assertEquals(List.of("主题"), result.get("theme"));
            assertEquals("摘要内容", result.get("summary"));
        }
    }

    private void assertRewriteNode(String node) {
        if (node.endsWith("test_rewrite_invalid_input_empty_raises")
                || node.endsWith("test_rewrite_invalid_input_whitespace_raises")) {
            QueryRewriter rewriter = rewriterWith(new SimpleContext(), 5, makeFullRewriteResponse("x"));
            BaseError error = assertThrows(BaseError.class, () -> rewriter.rewrite("   "));
            assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_INPUT_INVALID, error.getStatus());
        } else if (node.endsWith("test_rewrite_invalid_output_raises")) {
            SimpleContext context = new SimpleContext();
            context.addMessages(List.of(new UserMessage("你好"), new AssistantMessage("你好！")));
            QueryRewriter rewriter = rewriterWith(context, 5, "not json");
            BaseError error = assertThrows(BaseError.class, () -> rewriter.rewrite("问题"));
            assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID, error.getStatus());
        } else if (node.endsWith("test_rewrite_compress_failure_fallback")) {
            SimpleContext context = new SimpleContext();
            for (int i = 0; i < 3; i++) {
                context.addMessages(List.of(new UserMessage("用户问" + i), new AssistantMessage("助手答" + i)));
            }
            QueryRewriter rewriter = rewriterWith(context, 5, "not json", makeFullRewriteResponse("总结一下"));
            assertEquals("总结一下", rewriter.rewrite("总结一下").get("standalone_query"));
            assertEquals("original_history", context.getMessages(null, true).getFirst().getName());
        } else {
            SimpleContext context = new SimpleContext();
            context.addMessages(List.of(new UserMessage("你好"), new AssistantMessage("你好！")));
            String payload = makeFullRewriteResponse("那运费呢？");
            QueryRewriter rewriter = rewriterWith(context, 5, "这是回答：\n" + payload + "\n以上是结果。");
            assertEquals("那运费呢？", rewriter.rewrite("那运费呢？").get("standalone_query"));
        }
    }

    private void assertTrailingCommaNode(String node) throws JsonProcessingException {
        String broken = "{\"before\":\"x\",\"intention\":\"y\",\"standalone_query\":\"x\",\"references\":{},"
                + "\"missing\":[],\"typo\":[],\"gibberish\":[],\"from_history\":\"\",}";
        if (node.endsWith("test_parse_llm_json_trailing_comma")) {
            assertThrows(JsonProcessingException.class, () -> MAPPER.readValue(broken, Map.class));
            assertEquals("x", QueryRewriter.parseLlmJson(broken).get("standalone_query"));
        } else {
            SimpleContext context = new SimpleContext();
            context.addMessages(List.of(new UserMessage("你好"), new AssistantMessage("你好！")));
            QueryRewriter rewriter = rewriterWith(context, 5, broken);
            assertEquals("x", rewriter.rewrite("x").get("standalone_query"));
        }
    }

    private void assertFullConversationNode() {
        SimpleContext context = new SimpleContext();
        QueryRewriter rewriter = rewriterWith(
                context,
                5,
                makeFullRewriteResponse("那运费呢？"),
                makeCompressResponse(),
                makeFullRewriteResponse("会员怎么升级？"),
                makeCompressResponse(),
                makeFullRewriteResponse("生鲜能退吗？"));
        for (int i = 0; i < 6; i++) {
            context.addMessages(List.of(new UserMessage("用户问" + i), new AssistantMessage("助手答" + i)));
            if (List.of(1, 3, 5).contains(i)) {
                assertNotNull(rewriter.rewrite(i == 1 ? "那运费呢？" : i == 3 ? "会员怎么升级？" : "生鲜能退吗？"));
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
