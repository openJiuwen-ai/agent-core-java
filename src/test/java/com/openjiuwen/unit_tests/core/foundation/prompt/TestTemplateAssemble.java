/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.foundation.prompt;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.prompt.assemble.PromptAssembler;
import com.openjiuwen.core.foundation.prompt.assemble.variables.DictableVariable;
import com.openjiuwen.core.foundation.prompt.assemble.variables.TextableVariable;
import com.openjiuwen.core.foundation.prompt.assemble.variables.Variable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

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
 * Mirrors Python's {@code TestPromptAssemble} in {@code openjiuwen.tests.unit_tests.core.foundation.prompt.test_template_assemble}.
 */
class TestTemplateAssemble {

    // ============================== TextableVariable tests ==============================

    @Nested
    @DisplayName("TextableVariable tests")
    @Tag("level0")
    class TextableVariableTests {

        @Test
        @Tag("level0")
        @DisplayName("Empty placeholder throws exception")
        void testEmptyPlaceholderThrows() {
            assertThrows(Throwable.class, () ->
                    new TextableVariable("{{}}", "default", "{{", "}}"));
        }

        @Test
        @Tag("level0")
        @DisplayName("Single placeholder: parsing and eval")
        void testTextableVariable() {
            // Test single placeholder
            TextableVariable var1 = new TextableVariable("{{x}}", "default", "{{", "}}");
            assertEquals(List.of("x"), var1.getInputKeys());
            assertEquals("default", var1.getName());
            assertEquals("1", var1.eval(Map.of("x", "1")));
        }

        @Test
        @Tag("level0")
        @DisplayName("Multiple placeholders: parsing and eval")
        void testTextableVariables() {
            TextableVariable var2 = new TextableVariable("{{x}}{{y}}", "default", "{{", "}}");
            assertEquals(Set.of("x", "y"), new HashSet<>(var2.getInputKeys()));
            assertEquals("12", var2.eval(Map.of("x", "1", "y", "2")));
            assertEquals("12", var2.getValue());
        }

        @Test
        @Tag("level0")
        @DisplayName("Initialization with named variable and standard placeholders")
        void testInitialization() {
            // Test standard placeholders ({{}} format)
            String text = "You're an expert in the domain of {{domain}}";
            TextableVariable var = new TextableVariable(text, "role", "{{", "}}");
            assertEquals("role", var.getName());
            assertEquals(List.of("domain"), var.getInputKeys());

            // Test nested placeholders
            String nestedText = "Hello, {{user.name}}";
            TextableVariable varNested = new TextableVariable(nestedText, "default", "{{", "}}");
            assertEquals(List.of("user"), varNested.getInputKeys());

            // Test empty placeholder (<<>>) - need to specify corresponding prefix and suffix
            String emptyText = "Hello, <<>>!";
            assertThrows(Throwable.class, () ->
                    new TextableVariable(emptyText, "default", "<<", ">>"));
        }

        @Test
        @Tag("level0")
        @DisplayName("Update replaces placeholders correctly")
        void testUpdate() {
            // Test normal placeholder replacement
            String text = "You're an expert in the domain of {{domain}}.";
            TextableVariable var = new TextableVariable(text, "default", "{{", "}}");
            var.update(Map.of("domain", "science"));
            assertEquals("You're an expert in the domain of science.", var.getValue());

            // Test numeric type replacement
            TextableVariable varNum = new TextableVariable("This value is {{value}}.", "default", "{{", "}}");
            varNum.update(Map.of("value", 42));
            assertEquals("This value is 42.", varNum.getValue());

            // Test nested placeholder replacement
            TextableVariable varNested = new TextableVariable("Hello, {{user.name}}!", "default", "{{", "}}");
            varNested.update(Map.of("user", Map.of("name", "Alice")));
            assertEquals("Hello, Alice!", varNested.getValue());
        }

        @Test
        @Tag("level0")
        @DisplayName("Eval returns formatted text")
        void testEval() {
            // Test normal placeholder eval
            TextableVariable var = new TextableVariable(
                    "You're an expert in the domain of {{domain}}.", "default", "{{", "}}");
            Object result = var.eval(Map.of("domain", "science"));
            assertEquals("You're an expert in the domain of science.", result);

            // Test nested placeholder eval
            TextableVariable varNested = new TextableVariable("Hello, {{user.name}}!", "default", "{{", "}}");
            Object resultNested = varNested.eval(Map.of("user", Map.of("name", "Alice")));
            assertEquals("Hello, Alice!", resultNested);

            // Test multiple placeholders eval
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
    @Tag("level0")
    class VariableBaseTests {

        @Test
        @Tag("level0")
        @DisplayName("Variable initialization with name and inputKeys")
        void testVariableInitialization() {
            Variable var = new TestVariable("test_var", List.of("key1", "key2"));
            assertEquals("test_var", var.getName());
            assertEquals(List.of("key1", "key2"), var.getInputKeys());
            assertEquals("", var.getValue());  // Initial value is empty string
        }

        @Test
        @Tag("level0")
        @DisplayName("Variable with null inputKeys uses empty list")
        void testVariableNullInputKeys() {
            Variable var = new TestVariable("test_var", null);
            assertNotNull(var.getInputKeys());
            assertTrue(var.getInputKeys().isEmpty());
        }

        @Test
        @Tag("level0")
        @DisplayName("prepareInputs filters to inputKeys only")
        void testPrepareInputs() {
            RecordingVariable var = new RecordingVariable("test_var", List.of("key1", "key2"));

            Map<String, Object> input = new LinkedHashMap<>();
            input.put("key1", "value1");
            input.put("key2", "value2");
            input.put("key3", "value3");  // This should be filtered out

            var.eval(input);

            // Redundant key3 should have been filtered out by prepareInputs
            assertEquals(Map.of("key1", "value1", "key2", "value2"), var.getRecordedInputs());
        }

        @Test
        @Tag("level0")
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

    // ============================== PromptAssembler tests ==============================

    @Nested
    @DisplayName("PromptAssembler tests")
    @Tag("level0")
    class PromptAssemblerTests {

        @Test
        @Tag("level0")
        @DisplayName("Assemble string template with custom prefix/suffix ${}$")
        @SuppressWarnings("unchecked")
        void testAssembleCustomDelimiters1() {
            PromptAssembler asm1 = new PromptAssembler(
                    "`#system#`${role}$`#user#`${memory}$",
                    "${", "}$");

            Map<String, Variable> initialVars = new LinkedHashMap<>();
            initialVars.put("role", new TextableVariable(
                    "你是一个精通${domain}$领域的问答助手。", "role", "${", "}$"));

            PromptAssembler asm = new PromptAssembler(
                    "`#system#`${role}$`#user#`${memory}$",
                    "${", "}$", initialVars);

            assertEquals(Set.of("domain", "memory"), new HashSet<>(asm.getInputKeys()));

            Object result = asm.promptAssemble(Map.of(
                    "memory", List.of(Map.of("role", "user", "content", "我是谁")).toString(),
                    "domain", "科学"));

            String expected = "`#system#`你是一个精通科学领域的问答助手。`#user#`" +
                    List.of(Map.of("role", "user", "content", "我是谁"));
            assertEquals(expected, result);
        }

        @Test
        @Tag("level0")
        @DisplayName("Assemble string template with custom prefix/suffix {}")
        @SuppressWarnings("unchecked")
        void testAssembleCustomDelimiters2() {
            Map<String, Variable> initialVars = new LinkedHashMap<>();
            initialVars.put("role", new TextableVariable(
                    "你是一个精通{domain}领域的问答助手。", "role", "{", "}"));

            PromptAssembler asm2 = new PromptAssembler(
                    "`#system#`{role}`#user#`{memory}",
                    "{", "}", initialVars);

            assertEquals(Set.of("domain", "memory"), new HashSet<>(asm2.getInputKeys()));

            Object result = asm2.promptAssemble(Map.of(
                    "memory", List.of(Map.of("role", "user", "content", "我是谁")).toString(),
                    "domain", "天文"));

            String expected = "`#system#`你是一个精通天文领域的问答助手。`#user#`" +
                    List.of(Map.of("role", "user", "content", "我是谁"));
            assertEquals(expected, result);
        }

        @Test
        @Tag("level0")
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

            Map<String, Variable> initialVars = new LinkedHashMap<>();
            initialVars.put("user_inputs", new TextableVariable("张三", "default", "{{", "}}"));
            PromptAssembler asm3 = new PromptAssembler(content, "{{", "}}", initialVars);
            assertEquals(List.of(), asm3.getInputKeys());  // No variables need to be filled

            Object assembled = asm3.promptAssemble(Map.of());
            assertInstanceOf(List.class, assembled);

            List<BaseMessage> messages = (List<BaseMessage>) assembled;
            assertEquals(3, messages.size());
            assertEquals("Hi, 张三", messages.get(0).getContentAsString());
            assertEquals(content.get(1), messages.get(1));
            assertEquals(content.get(2), messages.get(2));
        }
    }

    // ============================== PromptTemplate tests ==============================

    @Nested
    @DisplayName("PromptTemplate tests")
    @Tag("level0")
    class PromptTemplateTests {

        @Test
        @Tag("level0")
        @DisplayName("Format string template with all variables")
        void testTemplateFormat() {
            // 1. Test string template format (complete variable filling)
            PromptTemplate template = PromptTemplate.builder()
                    .content("`#system#`你是一个精通{{domain}}领域的问答助手`#user#`{{memory}}")
                    .build();

            Map<String, Object> keywords = new LinkedHashMap<>();
            keywords.put("memory", List.of(Map.of("role", "user", "content", "你是谁")).toString());
            keywords.put("domain", "数学");

            PromptTemplate formattedTemplate = template.format(keywords);
            List<BaseMessage> messages = formattedTemplate.toMessages();

            assertEquals(1, messages.size());
            assertInstanceOf(UserMessage.class, messages.get(0));
            assertTrue(messages.get(0).getContentAsString().contains("数学"));
        }

        @Test
        @Tag("level0")
        @DisplayName("Format string template with partial variables preserves unfilled placeholders")
        void testTemplateFormatPartial() {
            // 2. Test partial variable filling (only pass memory, retain domain placeholder)
            PromptTemplate template2 = PromptTemplate.builder()
                    .content("`#system#`你是一个精通{{domain}}领域的问答助手`#user#`{{memory}}")
                    .build();

            template2 = template2.format(Map.of("memory", List.of(Map.of("role", "user", "content", "你是谁")).toString()));
            String content = (String) template2.getContent();
            assertTrue(content.contains("{{domain}}"));
            assertTrue(content.contains("你是谁"));
        }

        @Test
        @Tag("level0")
        @DisplayName("Format remaining variables completes all replacements")
        void testTemplateFormatRemaining() {
            // 3. Test filling remaining variables
            PromptTemplate template2 = PromptTemplate.builder()
                    .content("`#system#`你是一个精通{{domain}}领域的问答助手`#user#`{{memory}}")
                    .build();

            template2 = template2.format(Map.of("memory", List.of(Map.of("role", "user", "content", "你是谁")).toString()));
            template2 = template2.format(Map.of("domain", "数学"));

            String content = (String) template2.getContent();
            assertTrue(content.contains("数学"));
            assertFalse(content.contains("{{"));
        }

        @Test
        @Tag("level0")
        @DisplayName("Format BaseMessage list template with multiple messages")
        void testTemplateFormatBaseMessageList() {
            // 4. Test template with BaseMessage list format (multiple messages + multiple variables)
            List<BaseMessage> contentList = new ArrayList<>();
            contentList.add(UserMessage.builder().content("Hello {{name}}!").role("user").build());
            contentList.add(AssistantMessage.builder()
                    .content("I'm your assistant for {{domain}}.")
                    .role("assistant").build());

            PromptTemplate template3 = PromptTemplate.builder()
                    .content(contentList)
                    .build();

            PromptTemplate formatted3 = template3.format(Map.of("name", "Alice", "domain", "AI"));
            List<BaseMessage> messages3 = formatted3.toMessages();

            assertEquals(2, messages3.size());
            assertEquals("Hello Alice!", messages3.get(0).getContentAsString());
            assertEquals("I'm your assistant for AI.", messages3.get(1).getContentAsString());
        }

        @Test
        @Tag("level0")
        @DisplayName("Format with null/empty keywords returns deep copy")
        void testTemplateFormatNullEmpty() {
            PromptTemplate template4 = PromptTemplate.builder()
                    .content("Hello {{name}}")
                    .build();

            // Keywords is null
            PromptTemplate template4Copy1 = template4.format(null);
            assertEquals(template4.getContent(), template4Copy1.getContent());

            // Keywords is empty dictionary
            PromptTemplate template4Copy2 = template4.format(Map.of());
            assertEquals(template4.getContent(), template4Copy2.getContent());
        }

        @Test
        @Tag("level0")
        @DisplayName("Format with redundant keywords ignores extra keys")
        void testTemplateFormatRedundant() {
            PromptTemplate template5 = PromptTemplate.builder()
                    .content("Hi {{name}}")
                    .build();

            PromptTemplate formatted5 = template5.format(Map.of("name", "Bob", "age", 20));
            assertEquals("Hi Bob", formatted5.getContent());
        }
    }

    // ============================== DictableVariable tests ==============================

    @Nested
    @DisplayName("DictableVariable tests")
    @Tag("level0")
    class DictableVariableTests {

        @Test
        @Tag("level0")
        @DisplayName("Initialization scans placeholders from Map data")
        void testDictableVariableInitialization() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("text", "Hello {{name}}");
            data.put("info", Map.of("age", "{{age}}"));

            DictableVariable var = new DictableVariable(data, "default", "{{", "}}");
            assertEquals(Set.of("name", "age"), new HashSet<>(var.getInputKeys()));

            // Test with list data
            List<Map<String, Object>> data2 = List.of(
                    Map.of("type", "text", "content", "{{user.profile.name}}")
            );
            DictableVariable var2 = new DictableVariable(data2, "default", "{{", "}}");
            assertEquals(List.of("user"), var2.getInputKeys());

            // Test empty placeholder throws
            Map<String, Object> data3 = Map.of("key", "{{}}");
            assertThrows(Throwable.class, () ->
                    new DictableVariable(data3, "default", "{{", "}}"));
        }

        @Test
        @Tag("level0")
        @DisplayName("Update replaces placeholders in nested Map")
        @SuppressWarnings("unchecked")
        void testDictableVariableUpdate() {
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
        @Tag("level0")
        @DisplayName("Update replaces placeholders in List of Maps")
        @SuppressWarnings("unchecked")
        void testDictableVariableUpdateList() {
            List<Map<String, Object>> data2 = new ArrayList<>();
            data2.add(new LinkedHashMap<>(Map.of("type", "text", "text", "{{query}}")));
            data2.add(new LinkedHashMap<>(Map.of("type", "image_url", "image_url",
                    new LinkedHashMap<>(Map.of("url", "{{url}}")))));

            DictableVariable var2 = new DictableVariable(data2, "default", "{{", "}}");
            var2.update(Map.of("query", "What is this?", "url", "http://example.com/1.jpg"));

            List<Map<String, Object>> expected2 = new ArrayList<>();
            expected2.add(Map.of("type", "text", "text", "What is this?"));
            expected2.add(Map.of("type", "image_url", "image_url",
                    Map.of("url", "http://example.com/1.jpg")));

            assertEquals(expected2, var2.getValue());
        }

        @Test
        @Tag("level0")
        @DisplayName("Nested object access via dot notation")
        @SuppressWarnings("unchecked")
        void testDictableVariableNestedObj() {
            Map<String, Object> data = Map.of("info", "Author is {{author.name}}");
            DictableVariable var = new DictableVariable(data, "default", "{{", "}}");

            var.update(Map.of("author", Map.of("name", "Bob")));
            assertEquals(Map.of("info", "Author is Bob"), var.getValue());

            // Test with class object
            class Author {
                public String name = "Charlie";
            }
            var.update(Map.of("author", new Author()));
            assertEquals(Map.of("info", "Author is Charlie"), var.getValue());
        }

        @Test
        @Tag("level0")
        @DisplayName("Dict template integration with BaseMessage")
        @SuppressWarnings("unchecked")
        void testDictTemplateIntegration() {
            List<Object> templateContent = new ArrayList<>();
            templateContent.add(SystemMessage.builder().content("You are a helper.").role("system").build());
            templateContent.add(UserMessage.builder().content(List.of(
                    Map.of("type", "text", "text", "Describe this: {{query}}"),
                    Map.of("type", "image_url", "image_url", Map.of("url", "{{image_url}}"))
            )).role("user").build());

            PromptTemplate template = PromptTemplate.builder()
                    .content(templateContent)
                    .build();

            PromptTemplate formattedTemplate = template.format(Map.of(
                    "query", "a cute cat",
                    "image_url", "https://picsum.photos/200"
            ));

            List<BaseMessage> messages = formattedTemplate.toMessages();
            assertEquals(2, messages.size());
            assertEquals("You are a helper.", messages.get(0).getContentAsString());

            List<Map<String, Object>> expectedUserContent = List.of(
                    Map.of("type", "text", "text", "Describe this: a cute cat"),
                    Map.of("type", "image_url", "image_url", Map.of("url", "https://picsum.photos/200"))
            );
            assertEquals(expectedUserContent, messages.get(1).getContent());
        }

        @Test
        @Tag("level0")
        @DisplayName("Non-string values converted via toString")
        @SuppressWarnings("unchecked")
        void testDictableVariableNonStringLog() {
            Map<String, Object> data = Map.of("count", "Total: {{num}}");
            DictableVariable var = new DictableVariable(data, "default", "{{", "}}");

            var.update(Map.of("num", 100));
            assertEquals(Map.of("count", "Total: 100"), var.getValue());

            var.update(Map.of("num", true));
            assertEquals(Map.of("count", "Total: true"), var.getValue());
        }
    }
}
