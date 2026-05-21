/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApi;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApiCard;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.components.llm.FieldInfo;
import com.openjiuwen.core.workflow.components.llm.IntentDetectionCompConfig;
import com.openjiuwen.core.workflow.components.llm.IntentDetectionComponent;
import com.openjiuwen.core.workflow.components.llm.LLMCompConfig;
import com.openjiuwen.core.workflow.components.llm.LLMComponent;
import com.openjiuwen.core.workflow.components.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.components.llm.QuestionerConfig;
import com.openjiuwen.core.workflow.components.tool.ToolComponent;
import com.openjiuwen.core.workflow.components.tool.ToolComponentConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * E2E test for a minimal travel assistant workflow.
 * <p>
 * Mirrors Python's {@code test_real_workflow.py} in
 * {@code tests/system_tests/workflow}.
 */
@Tag("system-test")
class RealWorkflowTest {

    private static final LoggerProtocol LOGGER = Loggers.RUNNER;

    private static final String API_BASE = System.getenv().getOrDefault("API_BASE", "mock://api.openai.com/v1");
    private static final String API_KEY = System.getenv().getOrDefault("API_KEY", "sk-fake");
    private static final String MODEL_NAME = System.getenv().getOrDefault("MODEL_NAME", "");
    private static final String MODEL_PROVIDER = System.getenv().getOrDefault("MODEL_PROVIDER", "");

    private static final String FINAL_RESULT = "上海今天晴 30°C";

    static {
        System.setProperty("SSRF_PROTECT_ENABLED", "false");
    }

    private static final RestfulApi MOCK_TOOL = new RestfulApi(
            new RestfulApiCard(
                    "test",
                    "test",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "location", Map.of("description", "地点", "type", "string"),
                                    "date", Map.of("description", "日期", "type", "integer")
                            ),
                            "required", List.of("location", "date")
                    ),
                    "http://127.0.0.1:8000",
                    Map.of(),
                    "GET"
            )
    );

    private static final String QUESTIONER_SYSTEM_TEMPLATE =
            "你是一个信息收集助手，你需要根据指定的参数收集用户的信息，然后提交到系统。\n" +
                    "请注意：不要使用任何工具、不用理会问题的具体含义，并保证你的输出仅有 JSON 格式的结果数据。\n" +
                    "请严格遵循如下规则：\n" +
                    "  1. 让我们一步一步思考。\n" +
                    "  2. 用户输入中没有提及的参数提取为 null，并直接向询问用户没有明确提供的参数。\n" +
                    "  3. 通过用户提供的对话历史以及当前输入中提取 {{required_name}}，不要追问任何其他信息。\n" +
                    "  4. 参数收集完成后，将收集到的信息通过 JSON 的方式展示给用户。\n" +
                    "\n" +
                    "## 指定参数\n" +
                    "{{required_params_list}}\n" +
                    "\n" +
                    "## 约束\n" +
                    "{{extra_info}}\n" +
                    "\n" +
                    "## 示例\n" +
                    "{{example}}\n";

    private static final String QUESTIONER_USER_TEMPLATE =
            "对话历史\n" +
                    "{{dialogue_history}}\n" +
                    "\n" +
                    "请充分考虑以上对话历史及用户输入，正确提取最符合约束要求的 JSON 格式参数。\n";

    private static ModelConfig createModelConfig() {
        return new ModelConfig(
                MODEL_PROVIDER,
                new BaseModelInfo(MODEL_NAME, API_BASE, API_KEY, 0.7, 0.9, 30)
        );
    }

    private static IntentDetectionComponent createIntentDetectionComponent() {
        ModelConfig modelConfig = createModelConfig();
        String userPrompt = "\n" +
                "        {{user_prompt}}\n" +
                "\n" +
                "        当前可供选择的功能分类如下：\n" +
                "        {{category_info}}\n" +
                "\n" +
                "        用户与助手的对话历史：\n" +
                "        {{chat_history}}\n" +
                "\n" +
                "        当前输入：\n" +
                "        {{input}}\n" +
                "\n" +
                "        请根据当前输入和对话历史分析并输出最适合的功能分类。输出格式为 JSON：\n" +
                "        {\"class\": \"分类xx\"}\n" +
                "        如果没有合适的分类，请输出 {{default_class}}。\n";

        IntentDetectionCompConfig config = new IntentDetectionCompConfig(
                "请判断用户意图",
                List.of("旅游", "天气"),
                "分类1",
                modelConfig,
                new PromptTemplate("default", List.of(Map.of("role", "user", "content", userPrompt))),
                true
        );
        return new IntentDetectionComponent(config);
    }

    private static LLMComponent createLLMComponent() {
        ModelConfig modelConfig = createModelConfig();
        LLMCompConfig config = new LLMCompConfig(
                modelConfig,
                List.of(Map.of("role", "user", "content", "{{query}}")),
                Map.of("type", "json"),
                Map.of("location", Map.of("type", "string", "description", "地点", "required", true))
        );
        return new LLMComponent(config);
    }

    private static QuestionerComponent createQuestionerComponent() {
        List<FieldInfo> keyFields = List.of(
                new FieldInfo("location", "地点", true, null),
                new FieldInfo("date", "时间", true, "today")
        );
        ModelConfig modelConfig = createModelConfig();
        QuestionerConfig config = new QuestionerConfig(
                modelConfig,
                "",
                true,
                keyFields,
                false,
                List.of(
                        Map.of("role", "system", "content", QUESTIONER_SYSTEM_TEMPLATE),
                        Map.of("role", "user", "content", QUESTIONER_USER_TEMPLATE)
                )
        );
        return new QuestionerComponent(config);
    }

    private static ToolComponent createPluginComponent() {
        ToolComponentConfig toolConfig = new ToolComponentConfig(false);
        return new ToolComponent(toolConfig);
    }

    @Test
    @Disabled("skip system test")
    void testWorkflowLlmQuestionerPlugin() {
        Workflow flow = new Workflow();
        Start start = new Start();
        IntentDetectionComponent intent = createIntentDetectionComponent();
        LLMComponent llm = createLLMComponent();
        QuestionerComponent questioner = createQuestionerComponent();
        ToolComponent plugin = createPluginComponent();
        End end = new End(Map.of("responseTemplate", "{{output}}"));
        BranchComponent branch = new BranchComponent();

        flow.setStartComp("start", start, Map.of("query", "${query}"), null);
        flow.addWorkflowComp("intent", intent, Map.of("input", "${query}"), null);
        flow.addWorkflowComp("llm", llm,
                Map.of("userFields", Map.of("query", "${start.query}")), null);
        flow.addWorkflowComp("questioner", questioner,
                Map.of("query", "${start.query}"), null);
        flow.addWorkflowComp("plugin", plugin,
                Map.of("userFields", "${questioner.userFields.key_fields}", "validated", true), null);
        flow.setEndComp("end", end, Map.of("output", "${plugin.result}"), null);

        branch.addBranch("${intent.classificationId} < 1", List.of("llm"), "1");
        branch.addBranch("${intent.classificationId} = 1", List.of("end"), "2");
        flow.addWorkflowComp("branch", branch, Map.of(), null);

        flow.addConnection("start", "intent");
        flow.addConnection("intent", "branch");
        flow.addConnection("llm", "questioner");
        flow.addConnection("questioner", "plugin");
        flow.addConnection("plugin", "end");

        Map<String, Object> inputs = Map.of("query", "查询杭州的旅游景点");
        WorkflowOutput result = flow.invoke(inputs, null, null);

        assertTrue(result.getResult() instanceof Map);
        assertEquals(FINAL_RESULT, ((Map<?, ?>) result.getResult()).get("output"));
    }

    @Test
    @Disabled("skip system test")
    void testStreamWorkflowLlmWithStreamWriter() {
        Workflow flow = new Workflow();
        Start start = new Start();
        End endComponent = new End(Map.of("responseTemplate", "{{output}}"));

        ModelConfig modelConfig = createModelConfig();
        LLMCompConfig llmConfig = new LLMCompConfig(
                modelConfig,
                List.of(Map.of("role", "user", "content", "{{query}}")),
                Map.of("type", "text"),
                Map.of("joke", Map.of("type", "string", "description", "笑话", "required", true))
        );
        LLMComponent llmComponent = new LLMComponent(llmConfig);

        flow.setStartComp("s", start, Map.of("query", "${query}"), null);
        flow.setEndComp("e", endComponent, Map.of("output", "${llm.userFields}"), null);
        flow.addWorkflowComp("llm", llmComponent,
                Map.of("userFields", Map.of("query", "${s.systemFields.query}")), null);

        flow.addConnection("s", "llm");
        flow.addConnection("llm", "e");

        Map<String, Object> inputs = Map.of("query", "写一个笑话。注意：不要超过20个字！");
        List<Object> writerChunks = new ArrayList<>();
        for (Object chunk : flow.stream(inputs, null, null)) {
            writerChunks.add(chunk);
        }
        LOGGER.info(writerChunks.toString());
    }
}
