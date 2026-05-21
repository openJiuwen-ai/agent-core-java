/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.dev_tools.tune;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.singleagent.legacy.config.LLMCallConfig;
import com.openjiuwen.dev_tools.tune.chat_agent.ChatAgent;
import com.openjiuwen.dev_tools.tune.chat_agent.ChatAgentConfig;
import com.openjiuwen.dev_tools.tune.dataset.CaseLoader;
import com.openjiuwen.dev_tools.tune.evaluator.DefaultEvaluator;
import com.openjiuwen.dev_tools.tune.optimizer.JointOptimizer;
import com.openjiuwen.dev_tools.tune.trainer.Trainer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for information extraction prompt self-optimization.
 * <p>
 * Mirrors Python's {@code test_prompt_tune.py} in
 * {@code tests/system_tests/tune}.
 */
@Tag("system-test")
class PromptTuneTest {

    private static final LoggerProtocol LOGGER = Loggers.RUNNER;

    private static final String API_BASE = System.getenv().getOrDefault("API_BASE", "mock://api.openai.com/v1");
    private static final String API_KEY = System.getenv().getOrDefault("API_KEY", "sk-fake");
    private static final String MODEL_NAME = System.getenv().getOrDefault("MODEL_NAME", "");
    private static final String MODEL_PROVIDER = System.getenv().getOrDefault("MODEL_PROVIDER", "");

    private static final String INFORMATION_EXTRACTION_TEMPLATE = "\n" +
            "你是一个信息抽取助手，请从给定句子中提取所有的人名名称\n" +
            "输出格式为[人名1, 人名2, ...]的列表形式，不要输出其他内容\n" +
            "以下是用户输入：\n";

    private static final String TOOL_CALLS_TEMPLATE = "\n" +
            "你是一个工具调用助手，请根据用户的指令，调用工具\n";

    private static final String INFORMATION_EXTRACTION_TEMPLATE_WITH_VARIABLES = "\n" +
            "你是一个{{role}}助手，请从给定句子中提取所有的人名名称\n" +
            "输出格式为[人名1, 人名2, ...]的列表形式，不要输出其他内容\n" +
            "以下是用户输入：\n" +
            "{{query}}\n";

    private static final List<Case> INFORMATION_EXTRACTION_CASES = List.of(
            Case.builder()
                    .inputs(Map.of("query", "潘之恒（约1536—1621）字景升，号鸾啸生，冰华生，安徽歙县、岩寺人，侨寓金陵（今江苏南京）"))
                    .label(Map.of("output", "[潘之恒]"))
                    .build(),
            Case.builder()
                    .inputs(Map.of("query", "高祖二十二子：窦皇后生建成（李建成）、太宗皇帝（李世民）、玄霸（李玄霸）、元吉（李元吉），万贵妃生智云（李智云），莫嫔生元景（李元景），孙嫔生元昌（李元昌））"))
                    .label(Map.of("output", "[李建成, 李世民, 李玄霸, 李元吉, 李智云, 李元景, 李元昌]"))
                    .build(),
            Case.builder()
                    .inputs(Map.of("query", "郭造卿（1532—1593），字建初，号海岳，福建福清县化南里人（今福清市人），郭遇卿之弟，郭造卿少年的时候就很有名气，曾游学吴越"))
                    .label(Map.of("output", "[郭造卿, 郭遇卿]"))
                    .build(),
            Case.builder()
                    .inputs(Map.of("query", "沈自邠，字茂仁，号几轩，又号茂秀，浙江秀水长溪（今嘉兴南汇）人"))
                    .label(Map.of("output", "[沈自邠]"))
                    .build()
    );

    private static final List<Case> TOOL_CALL_CASES = List.of(
            Case.builder()
                    .inputs(Map.of("query", "请帮我打开空调"))
                    .label(Map.of("output", "", "tool_calls", List.of(
                            new ToolCall("", "function", "ac_open", "{}"))))
                    .build(),
            Case.builder()
                    .inputs(Map.of("query", "请帮我关闭空调"))
                    .label(Map.of("output", "", "tool_calls", List.of(
                            new ToolCall("", "function", "ac_close", "{}"))))
                    .build(),
            Case.builder()
                    .inputs(Map.of("query", "天气太热了，开一下空调"))
                    .label(Map.of("output", "", "tool_calls", List.of(
                            new ToolCall("", "function", "ac_open", "{}"))))
                    .build(),
            Case.builder()
                    .inputs(Map.of("query", "有点冷，先帮我关窗，再调整到21度"))
                    .label(Map.of("output", "", "tool_calls", List.of(
                            new ToolCall("", "function", "ac_control", "{\"temperature\":21}"))))
                    .build(),
            Case.builder()
                    .inputs(Map.of("query", "有点热，先帮我开窗，再调整到29度"))
                    .label(Map.of("output", "", "tool_calls", List.of(
                            new ToolCall("", "function", "ac_control", "{\"temperature\":29}"))))
                    .build()
    );

    private static final List<Case> INFORMATION_EXTRACTION_CASES_WITH_VARIABLES = List.of(
            Case.builder()
                    .inputs(Map.of(
                            "role", "信息提取",
                            "query", "潘之恒（约1536—1621）字景升，号鸾啸生，冰华生，安徽歙县、岩寺人，侨寓金陵（今江苏南京）"))
                    .label(Map.of("output", "[潘之恒]"))
                    .build(),
            Case.builder()
                    .inputs(Map.of(
                            "role", "信息提取",
                            "query", "高祖二十二子：窦皇后生建成（李建成）、太宗皇帝（李世民）、玄霸（李玄霸）、元吉（李元吉），万贵妃生智云（李智云），莫嫔生元景（李元景），孙嫔生元昌（李元昌））"))
                    .label(Map.of("output", "[李建成, 李世民, 李玄霸, 李元吉, 李智云, 李元景, 李元昌]"))
                    .build(),
            Case.builder()
                    .inputs(Map.of(
                            "role", "信息提取",
                            "query", "郭造卿（1532—1593），字建初，号海岳，福建福清县化南里人（今福清市人），郭遇卿之弟，郭造卿少年的时候就很有名气，曾游学吴越"))
                    .label(Map.of("output", "[郭造卿, 郭遇卿]"))
                    .build(),
            Case.builder()
                    .inputs(Map.of(
                            "role", "信息提取",
                            "query", "沈自邠，字茂仁，号几轩，又号茂秀，浙江秀水长溪（今嘉兴南汇）人"))
                    .label(Map.of("output", "[沈自邠]"))
                    .build()
    );

    private void showResult(List<EvaluatedCase> evaluatedCases) {
        for (EvaluatedCase evalResult : evaluatedCases) {
            LOGGER.info("score: {}, reason: {}, answer: {}, label: {}",
                    evalResult.getScore(), evalResult.getReason(),
                    evalResult.getAnswer(), evalResult.getLabel());
        }
    }

    private ChatAgent createAgent(String prompt) {
        return createAgent(prompt, null);
    }

    private ChatAgent createAgent(String prompt, List<ToolInfo> tools) {
        ChatAgentConfig config = ChatAgentConfig.builder()
                .id("chat_agent")
                .version("1.0.0")
                .description("<UNK>")
                .llmCallConfig(new LLMCallConfig(
                        new ModelRequestConfig(MODEL_NAME),
                        new ModelClientConfig(MODEL_PROVIDER, API_KEY, API_BASE, false),
                        List.of(Map.of("role", "system", "content", prompt))
                ))
                .build();
        return new ChatAgent(config, tools);
    }

    private Trainer createTrainer() {
        ModelClientConfig modelClientConfig = new ModelClientConfig(
                MODEL_PROVIDER, API_KEY, API_BASE, false);
        ModelRequestConfig modelConfig = new ModelRequestConfig(MODEL_NAME);

        JointOptimizer optimizer = new JointOptimizer(modelConfig, modelClientConfig, 0);
        DefaultEvaluator evaluator = new DefaultEvaluator(modelConfig, modelClientConfig,
                "1. 如果是非工具调用，两个回答需要一致，包括数量和名字。注意：但可以忽略对引号格式问题以及tool_calls字段" +
                        "2. 如果是工具调用，则只需要关注tool_calls字段中插件名称和插件参数是否一致，无需关注文本内容");
        return new Trainer(evaluator, optimizer, 5);
    }

    @Test
    @Disabled("skip system test")
    void testAgentOptimization() {
        ChatAgent agent = createAgent(INFORMATION_EXTRACTION_TEMPLATE);
        Trainer trainer = createTrainer();
        CaseLoader caseLoader = new CaseLoader(INFORMATION_EXTRACTION_CASES);

        var scoreAndResult = trainer.evaluate(agent, caseLoader);
        LOGGER.info("[原提示词推理效果]: score={}", scoreAndResult.getKey());
        showResult(scoreAndResult.getValue());

        ChatAgent optimizedAgent = trainer.train(agent, caseLoader);

        var optimizedScoreAndResult = trainer.evaluate(optimizedAgent, caseLoader);
        LOGGER.info("[优化后提示词推理效果]: score={}", optimizedScoreAndResult.getKey());
        showResult(optimizedScoreAndResult.getValue());
    }

    @Test
    @Disabled("skip system test")
    void testInformationExtractionPromptOptimization() {
        ChatAgent agent = createAgent(INFORMATION_EXTRACTION_TEMPLATE);
        Trainer trainer = createTrainer();
        CaseLoader caseLoader = new CaseLoader(INFORMATION_EXTRACTION_CASES);

        var scoreAndResult = trainer.evaluate(agent, caseLoader);
        LOGGER.info("[原提示词推理效果]: score={}", scoreAndResult.getKey());
        showResult(scoreAndResult.getValue());

        ChatAgent optimizedAgent = trainer.train(agent, caseLoader);

        var optimizedScoreAndResult = trainer.evaluate(optimizedAgent, caseLoader);
        LOGGER.info("[优化后提示词推理效果]: score={}", optimizedScoreAndResult.getKey());
        showResult(optimizedScoreAndResult.getValue());
    }

    @Test
    @Disabled("skip system test")
    void testToolCallsPromptOptimization() {
        ChatAgent agent = createAgent(TOOL_CALLS_TEMPLATE);
        Trainer trainer = createTrainer();
        CaseLoader caseLoader = new CaseLoader(TOOL_CALL_CASES);

        var scoreAndResult = trainer.evaluate(agent, caseLoader);
        LOGGER.info("[原提示词推理效果]: score={}", scoreAndResult.getKey());
        showResult(scoreAndResult.getValue());

        ChatAgent optimizedAgent = trainer.train(agent, caseLoader, 2);

        var optimizedScoreAndResult = trainer.evaluate(optimizedAgent, caseLoader);
        LOGGER.info("[优化后提示词推理效果]: score={}", optimizedScoreAndResult.getKey());
        showResult(optimizedScoreAndResult.getValue());
    }

    @Test
    @Disabled("skip system test")
    void testInformationExtractionPromptOptimizationWithVariables() {
        ChatAgent agent = createAgent(INFORMATION_EXTRACTION_TEMPLATE_WITH_VARIABLES);
        Trainer trainer = createTrainer();
        CaseLoader caseLoader = new CaseLoader(INFORMATION_EXTRACTION_CASES_WITH_VARIABLES);

        var scoreAndResult = trainer.evaluate(agent, caseLoader);
        LOGGER.info("[原提示词推理效果]: score={}", scoreAndResult.getKey());
        showResult(scoreAndResult.getValue());

        ChatAgent optimizedAgent = trainer.train(agent, caseLoader, 3);

        var optimizedScoreAndResult = trainer.evaluate(optimizedAgent, caseLoader);
        LOGGER.info("[优化后提示词推理效果]: score={}", optimizedScoreAndResult.getKey());
        showResult(optimizedScoreAndResult.getValue());
    }
}
