/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.foundation.prompt;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.assemble.PromptAssembler;
import com.openjiuwen.core.foundation.prompt.assemble.variables.DictableVariable;
import com.openjiuwen.core.foundation.prompt.assemble.variables.TextableVariable;
import com.openjiuwen.core.foundation.prompt.assemble.variables.Variable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PromptTemplate, PromptAssembler, TextableVariable, DictableVariable, Variable.
 * Ported from Python: tests/unit_tests/core/foundation/prompt/test_template_assemble.py
 */
class PromptAssembleTest {

    // ============================== TextableVariable tests ==============================

    @Nested
    @DisplayName("TextableVariable tests")
    class TextableVariableTests {

        @Test
        @DisplayName("Empty placeholder throws exception")
        void testEmptyPlaceholderThrows() {
            assertThrows(Throwable.class, () ->
                    new TextableVariable("{{}}", "default", "{{", "}}"));
        }

        @Test
        @DisplayName("Single placeholder: parsing and eval")
        void testSinglePlaceholder() {
            TextableVariable var1 = new TextableVariable("{{x}}", "default", "{{", "}}");
            assertEquals(List.of("x"), var1.getInputKeys());
            assertEquals("default", var1.getName());
            assertEquals("1", var1.eval(Map.of("x", "1")));
        }

        @Test
        @DisplayName("Multiple placeholders: parsing and eval")
        void testMultiplePlaceholders() {
            TextableVariable var2 = new TextableVariable("{{x}}{{y}}", "default", "{{", "}}");
            assertEquals(Set.of("x", "y"), new HashSet<>(var2.getInputKeys()));
            assertEquals("12", var2.eval(Map.of("x", "1", "y", "2")));
            assertEquals("12", var2.getValue());
        }

        @Test
        @DisplayName("Initialization with named variable")
        void testInitializationNamed() {
            String text = "You're an expert in the domain of {{domain}}";
            TextableVariable var = new TextableVariable(text, "role", "{{", "}}");
            assertEquals("role", var.getName());
            assertEquals(List.of("domain"), var.getInputKeys());
        }

        @Test
        @DisplayName("Nested placeholder (dot notation)")
        void testNestedPlaceholder() {
            String text = "Hello, {{user.name}}";
            TextableVariable var = new TextableVariable(text, "default", "{{", "}}");
            assertEquals(List.of("user"), var.getInputKeys());
        }

        @Test
        @DisplayName("Empty placeholder with custom prefix throws")
        void testEmptyPlaceholderCustomPrefix() {
            assertThrows(Throwable.class, () ->
                    new TextableVariable("Hello, <<>>!", "default", "<<", ">>"));
        }

        @Test
        @DisplayName("Update replaces placeholders correctly")
        void testUpdate() {
            // Normal replacement
            TextableVariable var = new TextableVariable(
                    "You're an expert in the domain of {{domain}}.", "default", "{{", "}}");
            var.update(Map.of("domain", "science"));
            assertEquals("You're an expert in the domain of science.", var.getValue());

            // Numeric type replacement
            TextableVariable varNum = new TextableVariable(
                    "This value is {{value}}.", "default", "{{", "}}");
            varNum.update(Map.of("value", 42));
            assertEquals("This value is 42.", varNum.getValue());

            // Nested placeholder replacement
            TextableVariable varNested = new TextableVariable(
                    "Hello, {{user.name}}!", "default", "{{", "}}");
            varNested.update(Map.of("user", Map.of("name", "Alice")));
            assertEquals("Hello, Alice!", varNested.getValue());
        }

        @Test
        @DisplayName("Eval returns formatted text without modifying original")
        void testEval() {
            // Normal eval
            TextableVariable var = new TextableVariable(
                    "You're an expert in the domain of {{domain}}.", "default", "{{", "}}");
            Object result = var.eval(Map.of("domain", "science"));
            assertEquals("You're an expert in the domain of science.", result);

            // Nested eval
            TextableVariable varNested = new TextableVariable(
                    "Hello, {{user.name}}!", "default", "{{", "}}");
            Object resultNested = varNested.eval(Map.of("user", Map.of("name", "Alice")));
            assertEquals("Hello, Alice!", resultNested);

            // Multiple placeholders eval
            TextableVariable varMulti = new TextableVariable(
                    "{{greeting}}, {{user.name}}! You have {{count}} messages.",
                    "default", "{{", "}}");
            Object resultMulti = varMulti.eval(Map.of(
                    "greeting", "Hi",
                    "user", Map.of("name", "Bob"),
                    "count", 3));
            assertEquals("Hi, Bob! You have 3 messages.", resultMulti);
        }
    }

    // ============================== Variable base class tests ==============================

    @Nested
    @DisplayName("Variable base class tests")
    class VariableBaseTests {

        @Test
        @DisplayName("Variable initialization with name and inputKeys")
        void testVariableInitialization() {
            Variable var = new TestVariable("test_var", List.of("key1", "key2"));
            assertEquals("test_var", var.getName());
            assertEquals(List.of("key1", "key2"), var.getInputKeys());
            assertEquals("", var.getValue());
        }

        @Test
        @DisplayName("Variable with null inputKeys uses empty list")
        void testVariableNullInputKeys() {
            Variable var = new TestVariable("test_var", null);
            assertNotNull(var.getInputKeys());
            assertTrue(var.getInputKeys().isEmpty());
        }

        @Test
        @DisplayName("prepareInputs filters to inputKeys only (via eval)")
        void testPrepareInputs() {
            // prepareInputs is protected, so we verify it indirectly via eval
            // The RecordingVariable records inputs received in update() after prepareInputs filtering
            RecordingVariable var = new RecordingVariable("test_var", List.of("key1", "key2"));

            Map<String, Object> input = new LinkedHashMap<>();
            input.put("key1", "v1");
            input.put("key2", "v2");
            input.put("key3", "v3");
            var.eval(input);

            // Redundant key3 should have been filtered out by prepareInputs
            assertEquals(Map.of("key1", "v1", "key2", "v2"), var.getRecordedInputs());
        }

        @Test
        @DisplayName("Eval calls update and returns value")
        void testVariableEval() {
            Variable var = new ConcatVariable("test_var", List.of("key1", "key2"));
            Object result = var.eval(Map.of("key1", "value1", "key2", "value2"));
            assertEquals("value1value2", result);
        }

        /** Simple test Variable that does nothing on update. */
        private static class TestVariable extends Variable {
            TestVariable(String name, List<String> inputKeys) {
                super(name, inputKeys);
            }

            @Override
            public void update(Map<String, Object> kwargs) {
                // No-op
            }
        }

        /** Variable that records inputs received in update() for verification. */
        private static class RecordingVariable extends Variable {
            private Map<String, Object> recordedInputs;

            RecordingVariable(String name, List<String> inputKeys) {
                super(name, inputKeys);
            }

            @Override
            public void update(Map<String, Object> kwargs) {
                this.recordedInputs = new LinkedHashMap<>(kwargs);
            }

            public Map<String, Object> getRecordedInputs() {
                return recordedInputs;
            }
        }

        /** Variable that concatenates key1 and key2 */
        private static class ConcatVariable extends Variable {
            ConcatVariable(String name, List<String> inputKeys) {
                super(name, inputKeys);
            }

            @Override
            public void update(Map<String, Object> kwargs) {
                String k1 = kwargs.getOrDefault("key1", "").toString();
                String k2 = kwargs.getOrDefault("key2", "").toString();
                this.value = k1 + k2;
            }
        }
    }

    // ============================== DictableVariable tests ==============================

    @Nested
    @DisplayName("DictableVariable tests")
    class DictableVariableTests {

        @Test
        @DisplayName("Initialization scans placeholders from Map data")
        void testInitializationMapData() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("text", "Hello {{name}}");
            data.put("info", Map.of("age", "{{age}}"));

            DictableVariable var = new DictableVariable(data, "default", "{{", "}}");
            assertEquals(Set.of("name", "age"), new HashSet<>(var.getInputKeys()));
        }

        @Test
        @DisplayName("Initialization scans placeholders from List data with nested dot notation")
        void testInitializationListData() {
            List<Map<String, Object>> data = List.of(
                    Map.of("type", "text", "content", "{{user.profile.name}}")
            );

            DictableVariable var = new DictableVariable(data, "default", "{{", "}}");
            assertEquals(List.of("user"), var.getInputKeys());
        }

        @Test
        @DisplayName("Empty placeholder in dict throws exception")
        void testEmptyPlaceholderThrows() {
            Map<String, Object> data = Map.of("key", "{{}}");
            assertThrows(Throwable.class, () ->
                    new DictableVariable(data, "default", "{{", "}}"));
        }

        @Test
        @DisplayName("Update replaces placeholders in nested Map")
        @SuppressWarnings("unchecked")
        void testUpdateNestedMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("message", "Hi {{user}}");
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("id", 101);
            details.put("tag", "{{tag}}");
            data.put("details", details);

            DictableVariable var = new DictableVariable(data, "default", "{{", "}}");
            var.update(Map.of("user", "Alice", "tag", "VIP"));

            Map<String, Object> expected = new LinkedHashMap<>();
            expected.put("message", "Hi Alice");
            Map<String, Object> expectedDetails = new LinkedHashMap<>();
            expectedDetails.put("id", 101);
            expectedDetails.put("tag", "VIP");
            expected.put("details", expectedDetails);

            assertEquals(expected, var.getValue());
        }

        @Test
        @DisplayName("Update replaces placeholders in List of Maps")
        @SuppressWarnings("unchecked")
        void testUpdateListOfMaps() {
            List<Map<String, Object>> data = new ArrayList<>();
            data.add(new LinkedHashMap<>(Map.of("type", "text", "text", "{{query}}")));
            data.add(new LinkedHashMap<>(Map.of("type", "image_url", "image_url",
                    new LinkedHashMap<>(Map.of("url", "{{url}}")))));

            DictableVariable var = new DictableVariable(data, "default", "{{", "}}");
            var.update(Map.of("query", "What is this?", "url", "http://example.com/1.jpg"));

            List<Map<String, Object>> expected = new ArrayList<>();
            expected.add(Map.of("type", "text", "text", "What is this?"));
            expected.add(Map.of("type", "image_url", "image_url",
                    Map.of("url", "http://example.com/1.jpg")));

            assertEquals(expected, var.getValue());
        }

        @Test
        @DisplayName("Nested object access via dot notation in dict")
        @SuppressWarnings("unchecked")
        void testNestedObjAccess() {
            Map<String, Object> data = Map.of("info", "Author is {{author.name}}");
            DictableVariable var = new DictableVariable(data, "default", "{{", "}}");

            var.update(Map.of("author", Map.of("name", "Bob")));
            assertEquals(Map.of("info", "Author is Bob"), var.getValue());
        }

        @Test
        @DisplayName("Non-string values converted via toString")
        @SuppressWarnings("unchecked")
        void testNonStringConversion() {
            Map<String, Object> data = Map.of("count", "Total: {{num}}");
            DictableVariable var = new DictableVariable(data, "default", "{{", "}}");

            var.update(Map.of("num", 100));
            assertEquals(Map.of("count", "Total: 100"), var.getValue());

            var.update(Map.of("num", true));
            assertEquals(Map.of("count", "Total: true"), var.getValue());
        }
    }

    // ============================== PromptAssembler tests ==============================

    @Nested
    @DisplayName("PromptAssembler tests")
    class PromptAssemblerTests {

        @Test
        @DisplayName("Assemble string template with custom prefix/suffix")
        void testAssembleStringTemplateCustomDelimiters() {
            PromptAssembler asm = new PromptAssembler(
                    "`#system#`{role}`#user#`{memory}",
                    "{", "}");

            assertEquals(Set.of("role", "memory"), new HashSet<>(asm.getInputKeys()));

            Map<String, Object> kwargs = new LinkedHashMap<>();
            kwargs.put("role", "你是一个精通天文领域的问答助手。");
            kwargs.put("memory", "用户消息");

            Object result = asm.promptAssemble(kwargs);
            assertEquals(
                    "`#system#`你是一个精通天文领域的问答助手。`#user#`用户消息",
                    result);
        }

        @Test
        @DisplayName("Assemble BaseMessage list template")
        @SuppressWarnings("unchecked")
        void testAssembleBaseMessageList() {
            List<BaseMessage> content = new ArrayList<>();
            content.add(UserMessage.builder().content("Hi, {{user_inputs}}").role("user").build());
            content.add(AssistantMessage.builder()
                    .content("")
                    .role("assistant")
                    .toolCalls(List.of(
                            ToolCall.builder().type("test").name("func").arguments("x").id("test").build()
                    ))
                    .build());
            content.add(ToolMessage.builder().toolCallId("test").content(List.of()).role("tool").build());

            PromptAssembler asm = new PromptAssembler(content, "{{", "}}");
            assertEquals(List.of("user_inputs"), asm.getInputKeys());

            Object assembled = asm.promptAssemble(Map.of("user_inputs", "张三"));
            assertInstanceOf(List.class, assembled);

            List<BaseMessage> messages = (List<BaseMessage>) assembled;
            assertEquals(3, messages.size());
            assertEquals("Hi, 张三", messages.get(0).getContentAsString());
        }
    }

    // ============================== PromptTemplate tests ==============================

    @Nested
    @DisplayName("PromptTemplate tests")
    class PromptTemplateTests {

        @Test
        @DisplayName("Format string template with all variables")
        void testFormatStringTemplateComplete() {
            PromptTemplate template = PromptTemplate.builder()
                    .content("`#system#`你是一个精通{{domain}}领域的问答助手`#user#`{{memory}}")
                    .build();

            Map<String, Object> keywords = new LinkedHashMap<>();
            keywords.put("memory", List.of(Map.of("role", "user", "content", "你是谁")).toString());
            keywords.put("domain", "数学");

            PromptTemplate formatted = template.format(keywords);
            List<BaseMessage> messages = formatted.toMessages();
            assertEquals(1, messages.size());
            assertInstanceOf(UserMessage.class, messages.get(0));
        }

        @Test
        @DisplayName("Format string template with partial variables preserves unfilled placeholders")
        void testFormatStringTemplatePartial() {
            PromptTemplate template = PromptTemplate.builder()
                    .content("`#system#`你是一个精通{{domain}}领域的问答助手`#user#`{{memory}}")
                    .build();

            Map<String, Object> keywords = new LinkedHashMap<>();
            keywords.put("memory", "用户消息内容");

            PromptTemplate partial = template.format(keywords);
            // domain should remain as placeholder
            String content = (String) partial.getContent();
            assertTrue(content.contains("{{domain}}"),
                    "Expected {{domain}} placeholder to be preserved, got: " + content);
            assertTrue(content.contains("用户消息内容"),
                    "Expected memory replacement, got: " + content);
        }

        @Test
        @DisplayName("Format remaining variables completes all replacements")
        void testFormatRemainingVariables() {
            PromptTemplate template = PromptTemplate.builder()
                    .content("`#system#`你是一个精通{{domain}}领域的问答助手`#user#`{{memory}}")
                    .build();

            // First pass: fill memory
            Map<String, Object> kw1 = new LinkedHashMap<>();
            kw1.put("memory", "用户消息内容");
            PromptTemplate partial = template.format(kw1);

            // Second pass: fill domain
            PromptTemplate completed = partial.format(Map.of("domain", "数学"));
            String content = (String) completed.getContent();
            assertFalse(content.contains("{{"),
                    "Expected no remaining placeholders, got: " + content);
            assertTrue(content.contains("数学"));
        }

        @Test
        @DisplayName("Format BaseMessage list template with multiple messages")
        void testFormatBaseMessageList() {
            List<BaseMessage> contentList = new ArrayList<>();
            contentList.add(UserMessage.builder().content("Hello {{name}}!").role("user").build());
            contentList.add(AssistantMessage.builder()
                    .content("I'm your assistant for {{domain}}.")
                    .role("assistant").build());

            PromptTemplate template = PromptTemplate.builder()
                    .content(contentList)
                    .build();

            PromptTemplate formatted = template.format(Map.of("name", "Alice", "domain", "AI"));
            List<BaseMessage> messages = formatted.toMessages();

            assertEquals(2, messages.size());
            assertEquals("Hello Alice!", messages.get(0).getContentAsString());
            assertEquals("I'm your assistant for AI.", messages.get(1).getContentAsString());
        }

        @Test
        @DisplayName("Format with null keywords returns deep copy")
        void testFormatNullKeywords() {
            PromptTemplate template = PromptTemplate.builder()
                    .content("Hello {{name}}")
                    .build();

            PromptTemplate copy = template.format(null);
            assertEquals(template.getContent(), copy.getContent());
        }

        @Test
        @DisplayName("Format with empty keywords returns deep copy")
        void testFormatEmptyKeywords() {
            PromptTemplate template = PromptTemplate.builder()
                    .content("Hello {{name}}")
                    .build();

            PromptTemplate copy = template.format(Map.of());
            assertEquals(template.getContent(), copy.getContent());
        }

        @Test
        @DisplayName("Format with redundant keywords ignores extra keys")
        void testFormatRedundantKeywords() {
            PromptTemplate template = PromptTemplate.builder()
                    .content("Hi {{name}}")
                    .build();

            Map<String, Object> keywords = new LinkedHashMap<>();
            keywords.put("name", "Bob");
            keywords.put("age", 20);

            PromptTemplate formatted = template.format(keywords);
            assertEquals("Hi Bob", formatted.getContent());
        }

        @Test
        @DisplayName("toMessages wraps string as single UserMessage")
        void testToMessagesString() {
            PromptTemplate template = PromptTemplate.builder()
                    .content("Hello world")
                    .build();

            List<BaseMessage> messages = template.toMessages();
            assertEquals(1, messages.size());
            assertInstanceOf(UserMessage.class, messages.get(0));
            assertEquals("Hello world", messages.get(0).getContentAsString());
        }

        @Test
        @DisplayName("toMessages returns empty list for empty content")
        void testToMessagesEmpty() {
            PromptTemplate template = PromptTemplate.builder()
                    .content("")
                    .build();

            List<BaseMessage> messages = template.toMessages();
            assertTrue(messages.isEmpty());
        }

        @Test
        @DisplayName("Dict template integration: SystemMessage + UserMessage with dict content")
        void testDictTemplateIntegration() {
            List<Object> userContent = new ArrayList<>();
            userContent.add(new LinkedHashMap<>(Map.of("type", "text", "text", "Describe this: {{query}}")));
            userContent.add(new LinkedHashMap<>(Map.of("type", "image_url", "image_url",
                    new LinkedHashMap<>(Map.of("url", "{{image_url}}")))));

            List<BaseMessage> templateContent = new ArrayList<>();
            templateContent.add(SystemMessage.builder().content("You are a helper.").role("system").build());
            templateContent.add(UserMessage.builder().content(userContent).role("user").build());

            PromptTemplate template = PromptTemplate.builder()
                    .content(templateContent)
                    .build();

            PromptTemplate formatted = template.format(Map.of(
                    "query", "a cute cat",
                    "image_url", "https://picsum.photos/200"
            ));

            List<BaseMessage> messages = formatted.toMessages();
            assertEquals(2, messages.size());
            assertEquals("You are a helper.", messages.get(0).getContentAsString());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> userContentResult = messages.get(1).getContentAsList()
                    .stream().map(o -> (Map<String, Object>) o).toList();
            assertEquals("text", userContentResult.get(0).get("type"));
            assertEquals("Describe this: a cute cat", userContentResult.get(0).get("text"));
            assertEquals("image_url", userContentResult.get(1).get("type"));
            @SuppressWarnings("unchecked")
            Map<String, Object> imageUrl = (Map<String, Object>) userContentResult.get(1).get("image_url");
            assertEquals("https://picsum.photos/200", imageUrl.get("url"));
        }
    }
}
