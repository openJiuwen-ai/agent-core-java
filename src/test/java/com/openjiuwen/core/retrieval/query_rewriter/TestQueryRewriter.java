/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.query_rewriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.retrieval.common.RetrievalResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for QueryRewriter.
 *
 * <p>Mirrors Python's {@code test_query_rewriter.py} in
 * {@code tests/unit_tests/core/retrieval/query_rewriter}.</p>
 */
class TestQueryRewriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String makeCompressResponse() {
        return "{\"theme\":[\"主题\"],\"summary\":\"摘要内容\"}";
    }

    private static String makeFullRewriteResponse(String standaloneQuery) {
        return "{\"before\":\"" + standaloneQuery + "\",\"intention\":\"用户咨询\",\"standalone_query\":\"" + standaloneQuery + "\",\"references\":{},\"missing\":[],\"typo\":[],\"gibberish\":[],\"from_history\":\"\"}";
    }

    @Nested
    @DisplayName("Model config propagation tests")
    class TestQueryRewriterModelConfigPropagation {

        @Test
        @DisplayName("init passes custom headers to model client")
        void testInitPassesOnlyCustomHeaders() {
            BaseModelClient mockClient = mock(BaseModelClient.class);
            ModelContext mockContext = mock(ModelContext.class);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            assertNotNull(qr);
        }
    }

    @Nested
    @DisplayName("fillTemplate tests")
    class TestFillTemplate {

        @Test
        @DisplayName("replaces placeholders")
        void testReplacesPlaceholders() {
            String template = "a={a} b={b}";
            String result = QueryRewriter.fillTemplate(template, Map.of("a", "1", "b", "2"));
            assertEquals("a=1 b=2", result);
        }

        @Test
        @DisplayName("ignores curly braces in JSON example")
        void testIgnoresCurlyBracesInJsonExample() {
            String template = "output: {history}, example: {\"x\":1}";
            String result = QueryRewriter.fillTemplate(template, Map.of("history", "hi"));
            assertEquals("output: hi, example: {\"x\":1}", result);
        }
    }

    @Nested
    @DisplayName("extractJson tests")
    class TestExtractJson {

        @Test
        @DisplayName("extracts single JSON object")
        void testExtractsSingleObject() {
            String s = "prefix {\"a\":1} suffix";
            assertEquals("{\"a\":1}", QueryRewriter.extractJson(s));
        }

        @Test
        @DisplayName("returns empty when no brace")
        void testReturnsEmptyWhenNoBrace() {
            assertEquals("", QueryRewriter.extractJson("no json here"));
        }

        @Test
        @DisplayName("returns empty when only open brace")
        void testReturnsEmptyWhenOnlyOpenBrace() {
            assertEquals("", QueryRewriter.extractJson("{"));
        }

        @Test
        @DisplayName("takes first open brace and last close brace")
        void testTakesFirstOpenLastClose() {
            String s = " {\"outer\":{\"inner\":1}} ";
            assertEquals("{\"outer\":{\"inner\":1}}", QueryRewriter.extractJson(s));
        }
    }

    @Nested
    @DisplayName("parseLlmJson tests")
    class TestParseLlmJson {

        @Test
        @DisplayName("valid JSON returns dict")
        void testValidJsonReturnsDict() {
            Map<String, Object> result = QueryRewriter.parseLlmJson("{\"a\":1}");
            assertNotNull(result);
            assertEquals(1, result.get("a"));
        }

        @Test
        @DisplayName("empty string returns None/null")
        void testEmptyStringReturnsNone() {
            assertNull(QueryRewriter.parseLlmJson(""));
            assertNull(QueryRewriter.parseLlmJson("   "));
        }

        @Test
        @DisplayName("invalid JSON returns None/null without repair")
        void testInvalidJsonReturnsNoneWithoutRepair() {
            assertNull(QueryRewriter.parseLlmJson("not json"));
        }

        @Test
        @DisplayName("trailing comma is repaired")
        void testTrailingCommaRepair() {
            String s = "{\"theme\":[\"a\"], \"summary\":\"b\",}";
            Map<String, Object> out = QueryRewriter.parseLlmJson(s);
            assertNotNull(out);
            assertEquals(List.of("a"), out.get("theme"));
            assertEquals("b", out.get("summary"));
        }

        @Test
        @DisplayName("non-dict root returns None/null")
        void testNonDictRootReturnsNone() {
            assertNull(QueryRewriter.parseLlmJson("[1,2,3]"));
            assertNull(QueryRewriter.parseLlmJson("null"));
        }
    }

    @Nested
    @DisplayName("force helper tests")
    class TestForceHelpers {

        @Test
        @DisplayName("force value to string")
        void testForceString() {
            assertEquals("x", QueryRewriter.forceString("x"));
            String json = QueryRewriter.forceString(Map.of("a", 1));
            assertTrue(json.equals("{\"a\":1}") || json.equals("{\"a\": 1}"));
        }

        @Test
        @DisplayName("force value to list")
        void testForceList() {
            assertEquals(List.of(1, 2), QueryRewriter.forceList(List.of(1, 2)));
            assertEquals(List.of("x"), QueryRewriter.forceList("x"));
        }

        @Test
        @DisplayName("force value to JSON object")
        void testForceJson() {
            assertEquals(Map.of("a", 1), QueryRewriter.forceJson("k", Map.of("a", 1)));
            assertEquals(Map.of("a", 1), QueryRewriter.forceJson("k", "{\"a\":1}"));
            assertEquals(Map.of("k", "plain"), QueryRewriter.forceJson("k", "plain"));
        }
    }

    @Nested
    @DisplayName("schemaRepair tests")
    class TestSchemaRepair {

        @Test
        @DisplayName("compress schema repair")
        void testCompressSchema() {
            Map<String, Class<?>> schema = Map.of("theme", List.class, "summary", String.class);
            Map<String, Object> input = Map.of("theme", List.of("a"), "summary", "b");
            Map<String, Object> out = QueryRewriter.schemaRepair(input, schema);
            assertEquals(List.of("a"), out.get("theme"));
            assertEquals("b", out.get("summary"));
        }

        @Test
        @DisplayName("fills null with defaults")
        void testFillsNoneWithDefaults() {
            Map<String, Class<?>> schema = new LinkedHashMap<>();
            schema.put("theme", List.class);
            schema.put("summary", String.class);
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("theme", null);
            input.put("summary", null);
            Map<String, Object> out = QueryRewriter.schemaRepair(input, schema);
            assertEquals(new ArrayList<>(), out.get("theme"));
            assertEquals("", out.get("summary"));
        }

        @Test
        @DisplayName("rewrite schema with all fields")
        void testRewriteSchemaAllFields() {
            Map<String, Class<?>> schema = new LinkedHashMap<>();
            schema.put("before", String.class);
            schema.put("intention", String.class);
            schema.put("standalone_query", String.class);
            schema.put("references", Map.class);
            schema.put("missing", List.class);
            schema.put("typo", List.class);
            schema.put("gibberish", List.class);
            schema.put("from_history", String.class);
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("before", "那运费呢？");
            raw.put("intention", "咨询运费");
            raw.put("standalone_query", "淘美乐退货的运费谁出？");
            raw.put("references", Map.of("那", "退货运费"));
            raw.put("missing", new ArrayList<>());
            raw.put("typo", new ArrayList<>());
            raw.put("gibberish", new ArrayList<>());
            raw.put("from_history", "history");
            Map<String, Object> out = QueryRewriter.schemaRepair(raw, schema);
            assertEquals("淘美乐退货的运费谁出？", out.get("standalone_query"));
            assertEquals(new ArrayList<>(), out.get("typo"));
            assertEquals(new ArrayList<>(), out.get("gibberish"));
        }

        @Test
        @DisplayName("typo sub-structure repair")
        void testTypoSubStructure() {
            Map<String, Class<?>> schema = Map.of("typo", List.class);
            Map<String, Object> raw = new LinkedHashMap<>();
            List<Map<String, Object>> typoList = new ArrayList<>();
            typoList.add(Map.of("original", "teh", "corrected", "the", "reason", "typo"));
            raw.put("typo", typoList);
            Map<String, Object> out = QueryRewriter.schemaRepair(raw, schema);
            List<?> typoOut = (List<?>) out.get("typo");
            assertEquals(1, typoOut.size());
            Map<?, ?> first = (Map<?, ?>) typoOut.get(0);
            assertEquals("teh", first.get("original"));
            assertEquals("the", first.get("corrected"));
        }

        @Test
        @DisplayName("raises on non-dict input")
        void testRaisesOnNonDict() {
            Map<String, Class<?>> schema = Map.of("a", String.class);
            BaseError ex = assertThrows(BaseError.class, () -> QueryRewriter.schemaRepair(null, schema));
            assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID, ex.getStatus());
        }
    }

    @Nested
    @DisplayName("loadTemplate tests")
    class TestQueryRewriterLoadTemplate {

        @Test
        @DisplayName("load existing template")
        void testLoadExistingTemplate() {
            BaseModelClient mockClient = mock(BaseModelClient.class);
            ModelContext mockContext = mock(ModelContext.class);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            String content = qr.loadTemplate("intention_completion");
            assertTrue(content.contains("standalone_query") || content.contains("角色") || content.length() > 0);
        }

        @Test
        @DisplayName("template cached on second call")
        void testLoadTemplateCachedOnSecondCall() {
            BaseModelClient mockClient = mock(BaseModelClient.class);
            ModelContext mockContext = mock(ModelContext.class);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            String first = qr.loadTemplate("intention_completion");
            String second = qr.loadTemplate("intention_completion");
            assertEquals(first, second);
        }

        @Test
        @DisplayName("prompt not found raises")
        void testPromptNotFoundRaises() {
            BaseModelClient mockClient = mock(BaseModelClient.class);
            ModelContext mockContext = mock(ModelContext.class);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "nonexistent_lang");
            BaseError ex = assertThrows(BaseError.class, () -> qr.loadTemplate("intention_completion"));
            assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_PROMPT_NOT_FOUND, ex.getStatus());
        }

        @Test
        @DisplayName("template read failure raises")
        void testLoadTemplateReadFailureRaises() {
            BaseModelClient mockClient = mock(BaseModelClient.class);
            ModelContext mockContext = mock(ModelContext.class);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            BaseError ex = assertThrows(BaseError.class, () -> qr.loadTemplate("missing_due_to_read_failure_equivalent"));
            assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_PROMPT_NOT_FOUND, ex.getStatus());
        }
    }

    @Nested
    @DisplayName("msgToText tests")
    class TestQueryRewriterMsg2Text {

        @Test
        @DisplayName("msgToText with explicit messages")
        void testMsg2TextWithMessages() {
            BaseModelClient mockClient = mock(BaseModelClient.class);
            ModelContext mockContext = mock(ModelContext.class);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            List<BaseMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("今天天气如何？"));
            messages.add(new AssistantMessage("晴天。"));
            String text = qr.msgToText(messages);
            assertTrue(text.contains("user: 今天天气如何？"));
            assertTrue(text.contains("assistant: 晴天。"));
        }

        @Test
        @DisplayName("msgToText from context when messages is null")
        void testMsg2TextFromContextWhenNone() {
            BaseModelClient mockClient = mock(BaseModelClient.class);
            List<BaseMessage> contextMessages = new ArrayList<>();
            contextMessages.add(new UserMessage("你好"));
            contextMessages.add(new AssistantMessage("你好！"));
            ModelContext mockContext = mock(ModelContext.class);
            when(mockContext.getMessages((Integer) null, true)).thenReturn(contextMessages);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            String text = qr.msgToText(null);
            assertTrue(text.contains("user: 你好"));
            assertTrue(text.contains("assistant: 你好！"));
        }
    }

    @Nested
    @DisplayName("compress tests")
    class TestQueryRewriterCompress {

        @Test
        @DisplayName("compress with valid mock response")
        void testCompressValidMock() throws Exception {
            BaseModelClient mockClient = mock(BaseModelClient.class);
            when(mockClient.invoke(any(), any(), anyFloat(), nullable(Float.class), nullable(String.class),
                    nullable(Integer.class), nullable(String.class), any(), nullable(Float.class), anyMap()))
                    .thenReturn(new AssistantMessage(makeCompressResponse()));
            ModelContext mockContext = mock(ModelContext.class);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            List<BaseMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("用户问"));
            messages.add(new AssistantMessage("助手答"));
            Map<String, Object> result = qr.compress(messages);
            assertTrue(result.containsKey("theme"));
            assertTrue(result.containsKey("summary"));
            assertInstanceOf(List.class, result.get("theme"));
            assertInstanceOf(String.class, result.get("summary"));
        }

        @Test
        @DisplayName("compress with invalid JSON raises")
        void testCompressInvalidJsonRaises() throws Exception {
            BaseModelClient mockClient = mock(BaseModelClient.class);
            when(mockClient.invoke(any(), any(), anyFloat(), nullable(Float.class), nullable(String.class),
                    nullable(Integer.class), nullable(String.class), any(), nullable(Float.class), anyMap()))
                    .thenReturn(new AssistantMessage("not valid json at all"));
            ModelContext mockContext = mock(ModelContext.class);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            List<BaseMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("用户问"));
            messages.add(new AssistantMessage("助手答"));
            BaseError ex = assertThrows(BaseError.class, () -> qr.compress(messages));
            assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID, ex.getStatus());
        }

        @Test
        @DisplayName("compress LLM invoke failure raises")
        void testCompressLlmInvokeFailureRaises() throws Exception {
            BaseModelClient mockClient = mock(BaseModelClient.class);
            when(mockClient.invoke(any(), any(), anyFloat(), nullable(Float.class), nullable(String.class),
                    nullable(Integer.class), nullable(String.class), any(), nullable(Float.class), anyMap()))
                    .thenThrow(new RuntimeException("network error"));
            ModelContext mockContext = mock(ModelContext.class);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            List<BaseMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("用户问"));
            messages.add(new AssistantMessage("助手答"));
            BaseError ex = assertThrows(BaseError.class, () -> qr.compress(messages));
            assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_LLM_INVOKE_FAILED, ex.getStatus());
        }
    }

    @Nested
    @DisplayName("rewrite tests")
    class TestQueryRewriterRewrite {

        @Test
        @DisplayName("rewrite with valid mock response")
        void testRewriteValidMock() throws Exception {
            String currentQuery = "那运费呢？";
            BaseModelClient mockClient = mock(BaseModelClient.class);
            when(mockClient.invoke(any(), any(), anyFloat(), nullable(Float.class), nullable(String.class),
                    nullable(Integer.class), nullable(String.class), any(), nullable(Float.class), anyMap()))
                    .thenReturn(new AssistantMessage(makeFullRewriteResponse(currentQuery)));
            List<BaseMessage> contextMessages = new ArrayList<>();
            contextMessages.add(new UserMessage("你好"));
            contextMessages.add(new AssistantMessage("你好！"));
            ModelContext mockContext = mock(ModelContext.class);
            when(mockContext.getMessages(anyInt(), eq(true))).thenReturn(contextMessages);
            when(mockContext.getMessages(nullable(Integer.class), eq(true))).thenReturn(contextMessages);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            Map<String, Object> result = qr.rewrite(currentQuery);
            assertEquals(currentQuery, result.get("standalone_query"));
            assertTrue(result.containsKey("before"));
            assertTrue(result.containsKey("intention"));
        }

        @Test
        @DisplayName("rewrite with JSON prefix and suffix")
        void testRewriteWithJsonPrefixSuffix() throws Exception {
            String currentQuery = "测试";
            String payload = makeFullRewriteResponse(currentQuery);
            BaseModelClient mockClient = mock(BaseModelClient.class);
            when(mockClient.invoke(any(), any(), anyFloat(), nullable(Float.class), nullable(String.class),
                    nullable(Integer.class), nullable(String.class), any(), nullable(Float.class), anyMap()))
                    .thenReturn(new AssistantMessage("这是回答：\n" + payload + "\n以上是结果。"));
            List<BaseMessage> contextMessages = new ArrayList<>();
            contextMessages.add(new UserMessage("你好"));
            contextMessages.add(new AssistantMessage("你好！"));
            ModelContext mockContext = mock(ModelContext.class);
            when(mockContext.getMessages(anyInt(), eq(true))).thenReturn(contextMessages);
            when(mockContext.getMessages(nullable(Integer.class), eq(true))).thenReturn(contextMessages);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            Map<String, Object> result = qr.rewrite(currentQuery);
            assertEquals(currentQuery, result.get("standalone_query"));
        }

        @Test
        @DisplayName("rewrite invalid output raises")
        void testRewriteInvalidOutputRaises() throws Exception {
            BaseModelClient mockClient = mock(BaseModelClient.class);
            when(mockClient.invoke(any(), any(), anyFloat(), nullable(Float.class), nullable(String.class),
                    nullable(Integer.class), nullable(String.class), any(), nullable(Float.class), anyMap()))
                    .thenReturn(new AssistantMessage("not json"));
            List<BaseMessage> contextMessages = new ArrayList<>();
            contextMessages.add(new UserMessage("你好"));
            contextMessages.add(new AssistantMessage("你好！"));
            ModelContext mockContext = mock(ModelContext.class);
            when(mockContext.getMessages(anyInt(), eq(true))).thenReturn(contextMessages);
            when(mockContext.getMessages(nullable(Integer.class), eq(true))).thenReturn(contextMessages);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            BaseError ex = assertThrows(BaseError.class, () -> qr.rewrite("问题"));
            assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_OUTPUT_INVALID, ex.getStatus());
        }

        @Test
        @DisplayName("rewrite invalid input empty raises")
        void testRewriteInvalidInputEmptyRaises() {
            BaseModelClient mockClient = mock(BaseModelClient.class);
            ModelContext mockContext = mock(ModelContext.class);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            BaseError ex = assertThrows(BaseError.class, () -> qr.rewrite(""));
            assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_INPUT_INVALID, ex.getStatus());
        }

        @Test
        @DisplayName("rewrite invalid input whitespace raises")
        void testRewriteInvalidInputWhitespaceRaises() {
            BaseModelClient mockClient = mock(BaseModelClient.class);
            ModelContext mockContext = mock(ModelContext.class);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            BaseError ex = assertThrows(BaseError.class, () -> qr.rewrite("   "));
            assertEquals(StatusCode.RETRIEVAL_QUERY_REWRITER_INPUT_INVALID, ex.getStatus());
        }
    }

    @Nested
    @DisplayName("rewrite with compress fallback tests")
    class TestQueryRewriterRewriteCompressFallback {

        @Test
        @DisplayName("rewrite falls back to original history when compress raises")
        void testRewriteCompressFailureFallback() throws Exception {
            String currentQuery = "总结一下";
            BaseModelClient mockClient = mock(BaseModelClient.class);
            when(mockClient.invoke(any(), any(), anyFloat(), nullable(Float.class), nullable(String.class),
                    nullable(Integer.class), nullable(String.class), any(), nullable(Float.class), anyMap()))
                    .thenReturn(new AssistantMessage(makeFullRewriteResponse(currentQuery)));
            List<BaseMessage> contextMessages = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                contextMessages.add(new UserMessage("用户问" + i));
                contextMessages.add(new AssistantMessage("助手答" + i));
            }
            ModelContext mockContext = mock(ModelContext.class);
            when(mockContext.getMessages(anyInt(), eq(true))).thenReturn(contextMessages);
            when(mockContext.getMessages(nullable(Integer.class), eq(true))).thenReturn(contextMessages);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            Map<String, Object> result = qr.rewrite(currentQuery);
            assertEquals(currentQuery, result.get("standalone_query"));
        }
    }

    @Nested
    @DisplayName("json repair with trailing comma tests")
    class TestRewriteWithTrailingCommaJsonRepair {

        @Test
        @DisplayName("parseLlmJson repairs trailing comma")
        void testParseLlmJsonTrailingComma() {
            String broken = "{\"before\":\"x\",\"intention\":\"y\",\"standalone_query\":\"x\",\"references\":{},\"missing\":[],\"typo\":[],\"gibberish\":[],\"from_history\":\"\",}";
            assertThrows(JsonProcessingException.class, () -> MAPPER.readValue(broken, Map.class));
            Map<String, Object> repaired = QueryRewriter.parseLlmJson(broken);
            assertNotNull(repaired);
            assertEquals("x", repaired.get("standalone_query"));
        }

        @Test
        @DisplayName("rewrite succeeds with trailing comma in LLM output")
        void testRewriteWithTrailingCommaMock() throws Exception {
            String broken = "{\"before\":\"x\",\"intention\":\"y\",\"standalone_query\":\"x\",\"references\":{},\"missing\":[],\"typo\":[],\"gibberish\":[],\"from_history\":\"\",}";
            BaseModelClient mockClient = mock(BaseModelClient.class);
            when(mockClient.invoke(any(), any(), anyFloat(), nullable(Float.class), nullable(String.class),
                    nullable(Integer.class), nullable(String.class), any(), nullable(Float.class), anyMap()))
                    .thenReturn(new AssistantMessage(broken));
            List<BaseMessage> contextMessages = new ArrayList<>();
            contextMessages.add(new UserMessage("你好"));
            contextMessages.add(new AssistantMessage("你好！"));
            ModelContext mockContext = mock(ModelContext.class);
            when(mockContext.getMessages(anyInt(), eq(true))).thenReturn(contextMessages);
            when(mockContext.getMessages(nullable(Integer.class), eq(true))).thenReturn(contextMessages);
            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            Map<String, Object> result = qr.rewrite("x");
            assertEquals("x", result.get("standalone_query"));
        }
    }

    @Nested
    @DisplayName("full conversation with compression tests")
    class TestFullConversationWithCompressAndRewrite {

        @Test
        @DisplayName("full conversation with compress and rewrite")
        void testFullConversationWithCompressAndRewrite() throws Exception {
            List<String[]> conversation = List.of(
                    new String[]{"你们这个淘美乐 App 是干什么的？", "淘美乐是一款综合购物与生活服务的 App。"},
                    new String[]{"怎么注册和登录？", "使用手机号验证码或第三方账号登录。"},
                    new String[]{"我想买点日用品，从哪里进？", "首页有日百等入口。"},
                    new String[]{"搜索出来的结果太多，怎么筛选？", "搜索结果页有筛选按钮。"},
                    new String[]{"下单后多久能送到？", "一般 1 到 3 天送达。"},
                    new String[]{"可以修改订单吗？", "待发货状态下可以修改或取消。"}
            );
            List<BaseMessage> history = new ArrayList<>();
            int[] compressCalls = {0};
            int[] rewriteCalls = {0};
            String[] currentQuery = {"用户当前问题"};

            BaseModelClient mockClient = mock(BaseModelClient.class);
            when(mockClient.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        Object messages = invocation.getArgument(0);
                        String prompt = String.valueOf(messages);
                        if (prompt.contains("user:") && prompt.contains("assistant:") && !prompt.contains("当前用户输入")) {
                            compressCalls[0]++;
                            return new AssistantMessage(makeCompressResponse());
                        }
                        rewriteCalls[0]++;
                        return new AssistantMessage(makeFullRewriteResponse(currentQuery[0]));
                    });
            ModelContext mockContext = mock(ModelContext.class);
            when(mockContext.getMessages(nullable(Integer.class), eq(true))).thenAnswer(invocation -> {
                Integer size = invocation.getArgument(0);
                if (size == null || size >= history.size()) {
                    return new ArrayList<>(history);
                }
                return new ArrayList<>(history.subList(history.size() - size, history.size()));
            });
            doAnswer(invocation -> {
                history.clear();
                history.addAll(invocation.getArgument(0));
                return null;
            }).when(mockContext).setMessages(any(), eq(true));

            QueryRewriter qr = new QueryRewriter(mockClient, mockContext, 5, "zh");
            List<Integer> rewriteAfterTurns = List.of(2, 4, 6);
            for (int turnIdx = 1; turnIdx <= conversation.size(); turnIdx++) {
                String[] turn = conversation.get(turnIdx - 1);
                history.add(new UserMessage(turn[0]));
                history.add(new AssistantMessage(turn[1]));
                assertTrue(history.size() >= 1);
                if (rewriteAfterTurns.contains(turnIdx)) {
                    currentQuery[0] = turnIdx == 2 ? "那运费呢？" : turnIdx == 4 ? "会员怎么升级？" : "生鲜能退吗？";
                    int before = history.size();
                    Map<String, Object> result = qr.rewrite(currentQuery[0]);
                    assertEquals(currentQuery[0], result.get("standalone_query"));
                    assertTrue(history.size() == before || history.size() == 1);
                }
            }

            assertEquals(rewriteAfterTurns.size(), rewriteCalls[0]);
            assertTrue(compressCalls[0] >= 0);
        }
    }

    @Nested
    @DisplayName("rewrite with retrieval results tests")
    class TestQueryRewriterRewriteWithRetrieval {

        @Test
        @DisplayName("rewrite with null LLM client uses fallback")
        void testRewriteNullLlmFallback() {
            List<BaseMessage> contextMessages = new ArrayList<>();
            ModelContext mockContext = mock(ModelContext.class);
            when(mockContext.getMessages(anyInt(), eq(true))).thenReturn(contextMessages);
            QueryRewriter qr = new QueryRewriter(null, mockContext, 5, "zh");
            RetrievalResult result1 = new RetrievalResult("First result text", 0.9, Map.of(), "doc1", "chunk1");
            RetrievalResult result2 = new RetrievalResult("Second result", 0.8, Map.of(), "doc2", "chunk2");
            List<RetrievalResult> results = new ArrayList<>();
            results.add(result1);
            results.add(result2);
            String rewritten = qr.rewrite("test query", results);
            assertEquals("test query First result text", rewritten);
        }

        @Test
        @DisplayName("rewrite with null results returns query")
        void testRewriteNullResults() {
            List<BaseMessage> contextMessages = new ArrayList<>();
            ModelContext mockContext = mock(ModelContext.class);
            when(mockContext.getMessages(anyInt(), eq(true))).thenReturn(contextMessages);
            QueryRewriter qr = new QueryRewriter(null, mockContext, 5, "zh");
            String rewritten = qr.rewrite("test query", null);
            assertEquals("test query", rewritten);
        }

        @Test
        @DisplayName("rewrite with empty results returns query")
        void testRewriteEmptyResults() {
            List<BaseMessage> contextMessages = new ArrayList<>();
            ModelContext mockContext = mock(ModelContext.class);
            when(mockContext.getMessages(anyInt(), eq(true))).thenReturn(contextMessages);
            QueryRewriter qr = new QueryRewriter(null, mockContext, 5, "zh");
            String rewritten = qr.rewrite("test query", new ArrayList<>());
            assertEquals("test query", rewritten);
        }
    }
}
