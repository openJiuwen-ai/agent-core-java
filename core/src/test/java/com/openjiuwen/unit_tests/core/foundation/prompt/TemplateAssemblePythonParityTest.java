/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Missing-test parity coverage for prompt template assembly.
 *
 * <p>Mirrors Python's {@code TestPromptAssemble} in
 * {@code tests/unit_tests/core/foundation/prompt/test_template_assemble.py}.</p>
 */
class TemplateAssemblePythonParityTest {

    private static final String SOURCE = "tests/unit_tests/core/foundation/prompt/test_template_assemble.py";

    @ParameterizedTest(name = "{0}")
    @MethodSource("pythonTestNodes")
    void mirrorsPythonTemplateAssembleTests(String pythonNodeId, Scenario scenario) throws Exception {
        scenario.run();
    }

    private static Stream<Arguments> pythonTestNodes() {
        return Stream.of(
                arg("test_textable_variable", TemplateAssemblePythonParityTest::textableVariable),
                arg("test_textable_variables", TemplateAssemblePythonParityTest::textableVariables),
                arg("test_initialization", TemplateAssemblePythonParityTest::initialization),
                arg("test_update", TemplateAssemblePythonParityTest::update),
                arg("test_eval", TemplateAssemblePythonParityTest::eval),
                arg("test_variable_initialization", TemplateAssemblePythonParityTest::variableInitialization),
                arg("test_prepare_inputs", TemplateAssemblePythonParityTest::prepareInputs),
                arg("test_variable_eval", TemplateAssemblePythonParityTest::variableEval),
                arg("test_assemble", TemplateAssemblePythonParityTest::assemble),
                arg("test_template_format", TemplateAssemblePythonParityTest::templateFormat),
                arg("test_dictable_variable_initialization",
                        TemplateAssemblePythonParityTest::dictableVariableInitialization),
                arg("test_dictable_variable_update", TemplateAssemblePythonParityTest::dictableVariableUpdate),
                arg("test_dictable_variable_nested_obj", TemplateAssemblePythonParityTest::dictableVariableNestedObj),
                arg("test_dict_template_integration", TemplateAssemblePythonParityTest::dictTemplateIntegration),
                arg("test_dictable_variable_non_string_log",
                        TemplateAssemblePythonParityTest::dictableVariableNonStringLog)
        );
    }

    private static Arguments arg(String pythonName, Scenario scenario) {
        return Arguments.of(SOURCE + "::TestPromptAssemble::" + pythonName, scenario);
    }

    private static void textableVariable() {
        assertThatThrownBy(() -> new TextableVariable("{{}}")).isInstanceOf(BaseError.class);

        TextableVariable var1 = new TextableVariable("{{x}}");
        assertThat(var1.getInputKeys()).containsExactly("x");
        assertThat(var1.getName()).isEqualTo("default");
        assertThat(var1.eval(Map.of("x", "1"))).isEqualTo("1");

        TextableVariable var2 = new TextableVariable("{{x}}{{y}}");
        assertThat(var2.getInputKeys()).containsExactly("x", "y");
        assertThat(var2.eval(Map.of("x", "1", "y", "2"))).isEqualTo("12");
        assertThat(var2.getValue()).isEqualTo("12");
    }

    private static void textableVariables() {
        assertThatThrownBy(() -> new TextableVariable("{{}}")).isInstanceOf(BaseError.class);

        TextableVariable var1 = new TextableVariable("{{x}}");
        assertThat(var1.getInputKeys()).containsExactly("x");
        assertThat(var1.getName()).isEqualTo("default");

        TextableVariable var2 = new TextableVariable("{{x}}{{y}}");
        assertThat(var2.getInputKeys()).containsExactlyInAnyOrder("x", "y");
        assertThat(var2.eval(Map.of("x", "1", "y", "2"))).isEqualTo("12");
        assertThat(var2.getValue()).isEqualTo("12");
    }

    private static void initialization() {
        String text = "You're an expert in the domain of {{domain}}";
        TextableVariable var = new TextableVariable(text, "role");
        assertThat(var.getText()).isEqualTo(text);
        assertThat(var.getName()).isEqualTo("role");
        assertThat(var.getInputKeys()).containsExactly("domain");
        assertThat(var.getPlaceholders()).containsExactly("domain");

        TextableVariable nested = new TextableVariable("Hello, {{user.name}}");
        assertThat(nested.getInputKeys()).containsExactly("user");
        assertThat(nested.getPlaceholders()).containsExactly("user.name");

        assertThatThrownBy(() -> new TextableVariable("Hello, <<>>!", "default", "<<", ">>"))
                .isInstanceOf(BaseError.class);
    }

    private static void update() {
        TextableVariable var = new TextableVariable("You're an expert in the domain of {{domain}}.");
        var.update(Map.of("domain", "science"));
        assertThat(var.getValue()).isEqualTo("You're an expert in the domain of science.");

        TextableVariable numeric = new TextableVariable("This value is {{value}}.");
        numeric.update(Map.of("value", 42));
        assertThat(numeric.getValue()).isEqualTo("This value is 42.");

        TextableVariable nested = new TextableVariable("Hello, {{user.name}}!");
        nested.update(Map.of("user", Map.of("name", "Alice")));
        assertThat(nested.getValue()).isEqualTo("Hello, Alice!");
    }

    private static void eval() {
        TextableVariable var = new TextableVariable("You're an expert in the domain of {{domain}}.");
        assertThat(var.eval(Map.of("domain", "science")))
                .isEqualTo("You're an expert in the domain of science.");

        TextableVariable nested = new TextableVariable("Hello, {{user.name}}!");
        assertThat(nested.eval(Map.of("user", Map.of("name", "Alice")))).isEqualTo("Hello, Alice!");

        TextableVariable multi = new TextableVariable("{{greeting}}, {{user.name}}! You have {{count}} messages.");
        assertThat(multi.eval(Map.of("greeting", "Hi", "user", Map.of("name", "Bob"), "count", 3)))
                .isEqualTo("Hi, Bob! You have 3 messages.");
    }

    private static void variableInitialization() {
        MockVariable var = new MockVariable("test_var", List.of("key1", "key2"));
        assertThat(var.getName()).isEqualTo("test_var");
        assertThat(var.getInputKeys()).containsExactly("key1", "key2");
        assertThat(var.getValue()).isEqualTo("");

        MockVariable nullKeys = new MockVariable("test_var", null);
        assertThat(nullKeys.getInputKeys()).isNull();
    }

    private static void prepareInputs() {
        MockVariable var = new MockVariable("test_var", List.of("key1", "key2"));
        assertThat(var.publicPrepareInputs(Map.of("key1", "value1", "key2", "value2")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("key1", "value1", "key2", "value2"));
        assertThat(var.publicPrepareInputs(Map.of("key1", "v1", "key2", "v2", "key3", "v3")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("key1", "v1", "key2", "v2"));
    }

    private static void variableEval() {
        MockVariable var = new MockVariable("test_var", List.of("key1", "key2"));

        assertThat(var.eval(Map.of("key1", "value1", "key2", "value2"))).isEqualTo("value1value2");
    }

    private static void assemble() {
        PromptAssembler asm1 = new PromptAssembler(
                "`#system#`${role}$`#user#`${memory}$",
                "${",
                "}$",
                Map.of("role", new TextableVariable("你是一个精通${domain}$领域的问答助手。", "default", "${", "}$"))
        );
        assertThat(asm1.getInputKeys()).containsExactlyInAnyOrder("domain", "memory");
        assertThat(asm1.promptAssemble(Map.of("memory", memoryMessage("我是谁"), "domain", "科学")))
                .isEqualTo("`#system#`你是一个精通科学领域的问答助手。`#user#`"
                        + "[{role=user, content=我是谁}]");

        PromptAssembler asm2 = new PromptAssembler(
                "`#system#`{role}`#user#`{memory}",
                "{",
                "}",
                Map.of("role", new TextableVariable("你是一个精通{domain}领域的问答助手。", "default", "{", "}"))
        );
        assertThat(asm2.getInputKeys()).containsExactlyInAnyOrder("domain", "memory");
        assertThat(asm2.promptAssemble(Map.of("memory", memoryMessage("我是谁"), "domain", "天文")))
                .isEqualTo("`#system#`你是一个精通天文领域的问答助手。`#user#`"
                        + "[{role=user, content=我是谁}]");

        ToolCall toolCall = ToolCall.builder().type("test").name("func").arguments("x").id("test").build();
        List<BaseMessage> content = new ArrayList<>();
        content.add(new UserMessage("Hi, {{user_inputs}}"));
        content.add(AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build());
        content.add(ToolMessage.builder().toolCallId("test").content(List.of()).build());
        PromptTemplate template = PromptTemplate.builder().content(content).build();
        PromptAssembler asm3 = new PromptAssembler(
                template.getContent(),
                "{{",
                "}}",
                Map.of("user_inputs", new TextableVariable("张三"))
        );

        assertThat(asm3.getInputKeys()).isEmpty();
        @SuppressWarnings("unchecked")
        List<BaseMessage> assembled = (List<BaseMessage>) asm3.promptAssemble(Map.of());
        assertThat(assembled).hasSameSizeAs(content);
        assertThat(assembled.get(0)).isEqualTo(new UserMessage("Hi, 张三"));
        assertThat(assembled.get(1)).isEqualTo(content.get(1));
        assertThat(assembled.get(2)).isEqualTo(content.get(2));
    }

    private static void templateFormat() {
        PromptTemplate template = PromptTemplate.builder()
                .content("`#system#`你是一个精通{{domain}}领域的问答助手`#user#`{{memory}}")
                .build();
        PromptTemplate formatted = template.format(Map.of("memory", memoryMessage("你是谁"), "domain", "数学"));
        assertThat(formatted.toMessages()).containsExactly(
                new UserMessage("`#system#`你是一个精通数学领域的问答助手`#user#`"
                        + "[{role=user, content=你是谁}]")
        );

        PromptTemplate partial = PromptTemplate.builder()
                .content("`#system#`你是一个精通{{domain}}领域的问答助手`#user#`{{memory}}")
                .build()
                .format(Map.of("memory", memoryMessage("你是谁")));
        assertThat(partial.getContent())
                .isEqualTo("`#system#`你是一个精通{{domain}}领域的问答助手`#user#`"
                        + "[{role=user, content=你是谁}]");
        partial = partial.format(Map.of("domain", "数学"));
        assertThat(partial.getContent())
                .isEqualTo("`#system#`你是一个精通数学领域的问答助手`#user#`"
                        + "[{role=user, content=你是谁}]");

        PromptTemplate messageTemplate = PromptTemplate.builder()
                .content(List.of(
                        new UserMessage("Hello {{name}}!"),
                        new AssistantMessage("I'm your assistant for {{domain}}.")
                ))
                .build();
        List<BaseMessage> messages = messageTemplate.format(Map.of("name", "Alice", "domain", "AI")).toMessages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isEqualTo(new UserMessage("Hello Alice!"));
        assertThat(messages.get(1)).isEqualTo(new AssistantMessage("I'm your assistant for AI."));

        PromptTemplate template4 = PromptTemplate.builder().content("Hello {{name}}").build();
        assertThat(template4.format(null).getContent()).isEqualTo(template4.getContent());
        assertThat(template4.format(Map.of()).getContent()).isEqualTo(template4.getContent());

        PromptTemplate redundant = PromptTemplate.builder().content("Hi {{name}}").build();
        assertThat(redundant.format(Map.of("name", "Bob", "age", 20)).getContent()).isEqualTo("Hi Bob");
    }

    private static void dictableVariableInitialization() {
        DictableVariable var = new DictableVariable(new LinkedHashMap<>(Map.of(
                "text", "Hello {{name}}",
                "info", new LinkedHashMap<>(Map.of("age", "{{age}}"))
        )));
        assertThat(var.getInputKeys()).containsExactlyInAnyOrder("name", "age");
        assertThat(var.getPlaceholders()).containsExactlyInAnyOrder("name", "age");

        DictableVariable nested = new DictableVariable(List.of(linkedMap("type", "text", "content",
                "{{user.profile.name}}")));
        assertThat(nested.getInputKeys()).containsExactly("user");
        assertThat(nested.getPlaceholders()).containsExactly("user.profile.name");

        assertThatThrownBy(() -> new DictableVariable(Map.of("key", "{{}}"))).isInstanceOf(BaseError.class);
    }

    private static void dictableVariableUpdate() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "Hi {{user}}");
        data.put("details", linkedMap("id", 101, "tag", "{{tag}}"));
        DictableVariable var = new DictableVariable(data);
        Map<String, Object> expected = linkedMap("message", "Hi Alice", "details", linkedMap("id", 101, "tag", "VIP"));

        Object result = var.update(Map.of("user", "Alice", "tag", "VIP"));

        assertThat(result).isEqualTo(expected);
        assertThat(var.getValue()).isEqualTo(expected);

        List<Object> data2 = List.of(
                linkedMap("type", "text", "text", "{{query}}"),
                linkedMap("type", "image_url", "image_url", linkedMap("url", "{{url}}"))
        );
        DictableVariable var2 = new DictableVariable(data2);
        Object result2 = var2.update(Map.of("query", "What is this?", "url", "http://example.com/1.jpg"));
        assertThat(result2).isEqualTo(List.of(
                linkedMap("type", "text", "text", "What is this?"),
                linkedMap("type", "image_url", "image_url", linkedMap("url", "http://example.com/1.jpg"))
        ));
    }

    private static void dictableVariableNestedObj() {
        DictableVariable var = new DictableVariable(Map.of("info", "Author is {{author.name}}"));

        var.update(Map.of("author", Map.of("name", "Bob")));
        assertThat(var.getValue()).isEqualTo(Map.of("info", "Author is Bob"));

        var.update(Map.of("author", new Author()));
        assertThat(var.getValue()).isEqualTo(Map.of("info", "Author is Charlie"));
    }

    private static void dictTemplateIntegration() {
        List<BaseMessage> templateContent = List.of(
                new SystemMessage("You are a helper."),
                UserMessage.builder().content(List.of(
                        linkedMap("type", "text", "text", "Describe this: {{query}}"),
                        linkedMap("type", "image_url", "image_url", linkedMap("url", "{{image_url}}"))
                )).build()
        );
        PromptTemplate template = PromptTemplate.builder().content(templateContent).build();

        List<BaseMessage> messages = template.format(Map.of(
                "query", "a cute cat",
                "image_url", "https://picsum.photos/200"
        )).toMessages();

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getContent()).isEqualTo("You are a helper.");
        assertThat(messages.get(1).getContent()).isEqualTo(List.of(
                linkedMap("type", "text", "text", "Describe this: a cute cat"),
                linkedMap("type", "image_url", "image_url", linkedMap("url", "https://picsum.photos/200"))
        ));
    }

    private static void dictableVariableNonStringLog() {
        DictableVariable var = new DictableVariable(Map.of("count", "Total: {{num}}"));

        var.update(Map.of("num", 100));
        assertThat(var.getValue()).isEqualTo(Map.of("count", "Total: 100"));

        var.update(Map.of("num", true));
        assertThat(var.getValue()).isEqualTo(Map.of("count", "Total: true"));
    }

    private static List<Map<String, Object>> memoryMessage(String content) {
        return List.of(linkedMap("role", "user", "content", content));
    }

    private static Map<String, Object> linkedMap(Object... alternatingKeyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < alternatingKeyValues.length; index += 2) {
            result.put(String.valueOf(alternatingKeyValues[index]), alternatingKeyValues[index + 1]);
        }
        return result;
    }

    @FunctionalInterface
    private interface Scenario {
        void run() throws Exception;
    }

    private static final class MockVariable extends Variable {

        private MockVariable(String name, List<String> inputKeys) {
            super(name, inputKeys);
        }

        @Override
        public Object update(Map<String, Object> kwargs) {
            this.value = String.valueOf(kwargs.getOrDefault("key1", ""))
                    + String.valueOf(kwargs.getOrDefault("key2", ""));
            return null;
        }
    }

    private static final class Author {
        private final String name = "Charlie";
    }
}
