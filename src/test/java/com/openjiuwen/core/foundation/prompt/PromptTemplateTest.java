// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.prompt;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.assemble.PromptAssembler;
import com.openjiuwen.core.foundation.prompt.assemble.variables.TextableVariable;
import com.openjiuwen.core.foundation.prompt.assemble.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for Prompt Template and Assembler functionality.
 * Converted from: agent-core/tests/unit_tests/core/foundation/prompt/test_template_assemble.py
 */
class PromptTemplateTest {

    @Test
    void testTextableVariable() {
        // Test empty placeholder throws exception
        assertThrows(BaseError.class, () -> new TextableVariable("{{}}", "default", "{{", "}}"));

        // Test single placeholder
        TextableVariable var1 = new TextableVariable("{{x}}", "default", "{{", "}}");
        assertEquals(List.of("x"), var1.getInputKeys());
        assertEquals("default", var1.getName());
        assertEquals("1", var1.eval(Map.of("x", "1")));

        // Test multiple placeholders
        TextableVariable var2 = new TextableVariable("{{x}}{{y}}", "default", "{{", "}}");
        assertEquals(Set.of("x", "y"), new HashSet<>(var2.getInputKeys()));
        assertEquals("12", var2.eval(Map.of("x", "1", "y", "2")));
        assertEquals("12", var2.getValue());
    }

    @Test
    void testTextableVariables() {
        // Duplicate test (retained and optimized)
        assertThrows(BaseError.class, () -> new TextableVariable("{{}}", "default", "{{", "}}"));
        
        TextableVariable var1 = new TextableVariable("{{x}}", "default", "{{", "}}");
        assertEquals(List.of("x"), var1.getInputKeys());
        assertEquals("default", var1.getName());

        TextableVariable var2 = new TextableVariable("{{x}}{{y}}", "default", "{{", "}}");
        assertEquals(Set.of("x", "y"), new HashSet<>(var2.getInputKeys()));
        assertEquals("12", var2.eval(Map.of("x", "1", "y", "2")));
        assertEquals("12", var2.getValue());
    }

    @Test
    void testInitialization() {
        // Test standard placeholders ({{}} format)
        String text = "You're an expert in the domain of {{domain}}";
        TextableVariable var = new TextableVariable(text, "role", "{{", "}}");
        assertEquals(text, var.getText());
        assertEquals("role", var.getName());
        assertEquals(List.of("domain"), var.getInputKeys());
        assertEquals(List.of("domain"), var.getPlaceholders());

        // Test nested placeholders
        String text2 = "Hello, {{user.name}}";
        TextableVariable var2 = new TextableVariable(text2, "default", "{{", "}}");
        assertEquals(List.of("user"), var2.getInputKeys());
        assertEquals(List.of("user.name"), var2.getPlaceholders());

        // Test empty placeholder (<<>>) - should throw exception
        String text3 = "Hello, <<>>!";
        assertThrows(BaseError.class, () -> new TextableVariable(text3, "default", "<<", ">>"));
    }

    @Test
    void testUpdate() {
        // Test normal placeholder replacement
        String text = "You're an expert in the domain of {{domain}}.";
        TextableVariable var = new TextableVariable(text, "default", "{{", "}}");
        var.update(Map.of("domain", "science"));
        assertEquals("You're an expert in the domain of science.", var.getValue());

        // Test numeric type replacement
        String text2 = "This value is {{value}}.";
        TextableVariable var2 = new TextableVariable(text2, "default", "{{", "}}");
        var2.update(Map.of("value", 42));
        assertEquals("This value is 42.", var2.getValue());

        // Test nested placeholder replacement
        String text3 = "Hello, {{user.name}}!";
        TextableVariable var3 = new TextableVariable(text3, "default", "{{", "}}");
        var3.update(Map.of("user", Map.of("name", "Alice")));
        assertEquals("Hello, Alice!", var3.getValue());
    }

    @Test
    void testEval() {
        // Test normal placeholder eval
        String text = "You're an expert in the domain of {{domain}}.";
        TextableVariable var = new TextableVariable(text, "default", "{{", "}}");
        String result = var.eval(Map.of("domain", "science"));
        assertEquals("You're an expert in the domain of science.", result);

        // Test nested placeholder eval
        String text2 = "Hello, {{user.name}}!";
        TextableVariable var2 = new TextableVariable(text2, "default", "{{", "}}");
        String result2 = var2.eval(Map.of("user", Map.of("name", "Alice")));
        assertEquals("Hello, Alice!", result2);

        // Test multiple placeholders eval
        String text3 = "{{greeting}}, {{user.name}}! You have {{count}} messages.";
        TextableVariable var3 = new TextableVariable(text3, "default", "{{", "}}");
        Map<String, Object> params = new HashMap<>();
        params.put("greeting", "Hi");
        params.put("user", Map.of("name", "Bob"));
        params.put("count", 3);
        String result3 = var3.eval(params);
        assertEquals("Hi, Bob! You have 3 messages.", result3);
    }

    @Test
    void testVariableInitialization() {
        // Test Variable class initialization using MockVariable
        class MockVariable extends Variable {
            public MockVariable(String name, List<String> inputKeys) {
                super(name, inputKeys);
            }

            @Override
            public void update(Map<String, Object> kwargs) {
                // Mock implementation
            }
        }

        MockVariable var = new MockVariable("test_var", List.of("key1", "key2"));
        assertEquals("test_var", var.getName());
        assertEquals(List.of("key1", "key2"), var.getInputKeys());
        assertEquals("", var.getValue());

        // Test case where input_keys is None
        MockVariable var2 = new MockVariable("test_var", null);
        assertNull(var2.getInputKeys());
    }

    @Test
    void testPrepareInputs() {
        // Test Variable's prepareInputs method
        class MockVariable extends Variable {
            public MockVariable(String name, List<String> inputKeys) {
                super(name, inputKeys);
            }

            @Override
            public void update(Map<String, Object> kwargs) {
                // Mock implementation
            }
        }

        MockVariable var = new MockVariable("test_var", List.of("key1", "key2"));
        Map<String, Object> inputKwargs = var.prepareInputs(Map.of("key1", "value1", "key2", "value2"));
        assertEquals(Map.of("key1", "value1", "key2", "value2"), inputKwargs);

        // Test redundant parameters are filtered out
        Map<String, Object> input = new HashMap<>();
        input.put("key1", "v1");
        input.put("key2", "v2");
        input.put("key3", "v3");
        Map<String, Object> filtered = var.prepareInputs(input);
        assertEquals(Map.of("key1", "v1", "key2", "v2"), filtered);
    }

    @Test
    void testVariableEval() {
        // Test eval method of custom Variable subclass
        class MockVariable extends Variable {
            public MockVariable(String name, List<String> inputKeys) {
                super(name, inputKeys);
            }

            @Override
            public void update(Map<String, Object> kwargs) {
                String v1 = (String) kwargs.getOrDefault("key1", "");
                String v2 = (String) kwargs.getOrDefault("key2", "");
                setValue(v1 + v2);
            }
        }

        MockVariable var = new MockVariable("test_var", List.of("key1", "key2"));
        String result = var.eval(Map.of("key1", "value1", "key2", "value2"));
        assertEquals("value1value2", result);
    }

    @Test
    void testAssemble() {
        // 1. Test string template (using non-default placeholder ${}$)
        Map<String, Variable> vars1 = new HashMap<>();
        vars1.put("role", new TextableVariable("你是一个精通${domain}$领域的问答助手。", "role", "${", "}$"));
        
        PromptAssembler asm1 = new PromptAssembler(
            "`#system#`${role}$`#user#`${memory}$",
            "${", "}$",
            vars1
        );
        assertEquals(Set.of("domain", "memory"), new HashSet<>(asm1.getInputKeys()));
        
        Map<String, Object> params1 = new HashMap<>();
        params1.put("memory", List.of(Map.of("role", "user", "content", "我是谁")));
        params1.put("domain", "科学");
        Object assembled1 = asm1.promptAssemble(params1);
        String result1 = (String) assembled1;
        assertTrue(result1.startsWith("`#system#`你是一个精通科学领域的问答助手。`#user#`[{"));
        assertTrue(result1.contains("role=user"));
        assertTrue(result1.contains("content=我是谁"));
        assertTrue(result1.endsWith("}]"));

        // 2. Test another placeholder format {role}
        Map<String, Variable> vars2 = new HashMap<>();
        vars2.put("role", new TextableVariable("你是一个精通{domain}领域的问答助手。", "role", "{", "}"));
        
        PromptAssembler asm2 = new PromptAssembler(
            "`#system#`{role}`#user#`{memory}",
            "{", "}",
            vars2
        );
        assertEquals(Set.of("domain", "memory"), new HashSet<>(asm2.getInputKeys()));
        
        Map<String, Object> params2 = new HashMap<>();
        params2.put("memory", List.of(Map.of("role", "user", "content", "我是谁")));
        params2.put("domain", "天文");
        Object assembled2 = asm2.promptAssemble(params2);
        String result2 = (String) assembled2;
        assertTrue(result2.startsWith("`#system#`你是一个精通天文领域的问答助手。`#user#`[{"));
        assertTrue(result2.contains("role=user"));
        assertTrue(result2.contains("content=我是谁"));
        assertTrue(result2.endsWith("}]"));

        // 3. Test BaseMessage type template content
        ToolCall toolCall = new ToolCall("test", "func", "x", "test");
        PromptTemplate template = new PromptTemplate(
            "",
            Arrays.asList(
                new UserMessage("Hi, {{user_inputs}}"),
                new AssistantMessage("", List.of(toolCall)),
                new ToolMessage("test", new ArrayList<>())
            ),
            "{{", "}}"
        );
        
        Map<String, Variable> vars3 = new HashMap<>();
        vars3.put("user_inputs", new TextableVariable("张三", "user_inputs", "{{", "}}"));
        
        PromptAssembler asm3 = new PromptAssembler(
            template.getContent(),
            "{{", "}}",
            vars3
        );
        assertEquals(Collections.emptyList(), asm3.getInputKeys());
        
        Object assembled3 = asm3.promptAssemble(new HashMap<>());
        assertTrue(assembled3 instanceof List);
        @SuppressWarnings("unchecked")
        List<BaseMessage> messages = (List<BaseMessage>) assembled3;
        assertEquals(3, messages.size());
        assertEquals("Hi, 张三", messages.get(0).getContent());
    }

    @Test
    void testTemplateFormat() {
        // 1. Test string template format (complete variable filling)
        PromptTemplate template = new PromptTemplate(
            "",
            "`#system#`你是一个精通{{domain}}领域的问答助手`#user#`{{memory}}",
            "{{", "}}"
        );
        
        Map<String, Object> params = new HashMap<>();
        params.put("memory", List.of(Map.of("role", "user", "content", "你是谁")));
        params.put("domain", "数学");
        
        PromptTemplate formatted = template.format(params);
        List<BaseMessage> messages = formatted.toMessages();
        assertEquals(1, messages.size());
        String content = (String) messages.get(0).getContent();
        assertTrue(content.startsWith("`#system#`你是一个精通数学领域的问答助手`#user#`[{"));
        assertTrue(content.contains("role=user"));
        assertTrue(content.contains("content=你是谁"));
        assertTrue(content.endsWith("}]"));

        // 2. Test partial variable filling (only pass memory, retain domain placeholder)
        PromptTemplate template2 = new PromptTemplate(
            "",
            "`#system#`你是一个精通{{domain}}领域的问答助手`#user#`{{memory}}",
            "{{", "}}"
        );
        PromptTemplate partial = template2.format(Map.of("memory", List.of(Map.of("role", "user", "content", "你是谁"))));
        String partialContent = (String) partial.getContent();
        assertTrue(partialContent.startsWith("`#system#`你是一个精通{{domain}}领域的问答助手`#user#`[{"));
        assertTrue(partialContent.contains("role=user"));
        assertTrue(partialContent.contains("content=你是谁"));
        assertTrue(partialContent.endsWith("}]"));

        // 3. Test filling remaining variables
        PromptTemplate complete = partial.format(Map.of("domain", "数学"));
        String completeContent = (String) complete.getContent();
        assertTrue(completeContent.startsWith("`#system#`你是一个精通数学领域的问答助手`#user#`[{"));
        assertTrue(completeContent.contains("role=user"));
        assertTrue(completeContent.contains("content=你是谁"));
        assertTrue(completeContent.endsWith("}]"));

        // 4. Test template with BaseMessage list format
        PromptTemplate template3 = new PromptTemplate(
            "",
            Arrays.asList(
                new UserMessage("Hello {{name}}!"),
                new AssistantMessage("I'm your assistant for {{domain}}.")
            ),
            "{{", "}}"
        );
        
        Map<String, Object> params3 = new HashMap<>();
        params3.put("name", "Alice");
        params3.put("domain", "AI");
        
        PromptTemplate formatted3 = template3.format(params3);
        List<BaseMessage> messages3 = formatted3.toMessages();
        assertEquals(2, messages3.size());
        assertEquals("Hello Alice!", messages3.get(0).getContent());
        assertEquals("I'm your assistant for AI.", messages3.get(1).getContent());

        // Test keywords as None/empty map (return deep copy of original template)
        PromptTemplate template4 = new PromptTemplate("", "Hello {{name}}", "{{", "}}");
        
        // Keywords is null
        PromptTemplate copy1 = template4.format(null);
        assertEquals(template4.getContent(), copy1.getContent());
        
        // Keywords is empty map
        PromptTemplate copy2 = template4.format(new HashMap<>());
        assertEquals(template4.getContent(), copy2.getContent());

        // Test passing redundant keywords
        PromptTemplate template5 = new PromptTemplate("", "Hi {{name}}", "{{", "}}");
        Map<String, Object> redundant = new HashMap<>();
        redundant.put("name", "Bob");
        redundant.put("age", 20);
        PromptTemplate formatted5 = template5.format(redundant);
        assertEquals("Hi Bob", formatted5.getContent());
    }
}

