/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.prompt.assemble.PromptAssembler;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneConstant;
import com.openjiuwen.dev_tools.tune.TuneUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Textual-gradient instruction optimizer for legacy prompt tuning.
 *
 * <p>Mirrors Python's {@code InstructionOptimizer} in
 * {@code openjiuwen/dev_tools/tune/optimizer/instruction_optimizer.py}.</p>
 */
public class InstructionOptimizer extends BaseOptimizer {

    public static final PromptTemplate PROMPT_INSTRUCTION_OPTIMIZE_TEMPLATE = PromptTemplate.builder()
            .content("""
                    你是一位提示词优化专家，你的任务是根据提供的信息对提示词进行优化。具体信息如下:
                    首先，请阅读以下提示词:
                    <prompt_base>
                    {{prompt_instruction}}
                    </prompt_base>

                    你拥有的的工具和API说明如下:
                    <tools_description>
                    {{tools_description}}
                    </tools_description>

                    提示词在应用的过程中出现的错误case如下：
                    <bad_cases>
                    {{bad_cases}}
                    </bad_cases>

                    对这些错误case的反思如下:
                    <reflections_on_bad_cases>
                    {{reflections_on_bad_cases}}
                    </reflections_on_bad_cases>

                    在优化提示词模版时，请遵循如下要求:
                    1. 在<思考>标签中，请根据错误示例及其对应的反思内容，深入、全面地分析提示词中可能导致错误的部分。分析应覆盖：错误原因的识别、原始提示词中存在的问题，以及通过哪些具体修改可以有效规避这些问题。
                    2. 在<PROMPT_OPTIMIZED>标签中，基于上述分析，输出优化后的提示词版本。
                    3. 分析过程中应聚焦于问题的具体成因，结合模板结构、语意表达和格式规范等方面，系统性地进行优化。
                    4. 优化过程中务必信息表达完整、逻辑侵袭，不可遗漏重要内容或引入模糊表达
                    5. 不可直接使用给定的示例，也不要在提示词中加入示例中的具体信息，可以通过抽象、改写的方式总结

                    输出格式：
                    <思考>
                    [在此详细说明你对提示词的优化分析]
                    </思考>
                    <PROMPT_OPTIMIZED>
                    [在此输出优化后的提示词]
                    </PROMPT_OPTIMIZED>
                    请确保优化后的内容能够有效避免之前出现的错误case。
                    """)
            .build();

    public static final PromptTemplate PROMPT_INSTRUCTION_OPTIMIZE_BOTH_TEMPLATE = PromptTemplate.builder()
            .content("""
                    你是一位提示词优化专家，你的任务是根据提供的信息对提示词进行优化。具体信息如下:
                    首先，请阅读以下system和user提示词:
                    <system_prompt_base>
                    {{system_prompt}}
                    </system_prompt_base>

                    <user_prompt_base>
                    {{user_prompt}}
                    </user_prompt_base>

                    你拥有的的工具和API说明如下:
                    <tools_description>
                    {{tools_description}}
                    </tools_description>

                    提示词在应用的过程中出现的错误case如下：
                    <bad_cases>
                    {{bad_cases}}
                    </bad_cases>

                    对这些错误case的反思如下:
                    <reflections_on_bad_cases>
                    {{reflections_on_bad_cases}}
                    </reflections_on_bad_cases>

                    在优化提示词模版时，请遵循如下要求:
                    1. 在<思考>标签中，请根据错误示例及其对应的反思内容，深入、全面地分析提示词中可能导致错误的部分。分析应覆盖：错误原因的识别、原始提示词中存在的问题，以及通过哪些具体修改可以有效规避这些问题。
                    2. 在<SYSTEM_PROMPT_OPTIMIZED>和<USER_PROMPT_OPTIMIZED>标签中，基于上述分析，输出优化后的system和user提示词版本。
                    3. 分析过程中应聚焦于问题的具体成因，结合模板结构、语意表达和格式规范等方面，系统性地进行优化。
                    4. 优化过程中务必信息表达完整、逻辑侵袭，不可遗漏重要内容或引入模糊表达
                    5. 不可直接使用给定的示例，也不要在提示词中加入示例中的具体信息，可以通过抽象、改写的方式总结

                    输出格式：
                    <思考>
                    [在此详细说明你对提示词的优化分析]
                    </思考>
                    <SYSTEM_PROMPT_OPTIMIZED>
                    [在此输出优化后的system提示词]
                    </SYSTEM_PROMPT_OPTIMIZED>
                    <USER_PROMPT_OPTIMIZED>
                    [在此输出优化后的user提示词]
                    </USER_PROMPT_OPTIMIZED>
                    请确保优化后的内容能够有效避免之前出现的错误case。
                    """)
            .build();

    public static final PromptTemplate CREATE_PROMPT_TEXTUAL_GRADIENT_TEMPLATE = PromptTemplate.builder()
            .content("""
                    作为提示词优化专家，我的目标是帮助代理高效且成功地完成任务
                    当前的system和user提示词是:
                    <system_prompt_base>
                    {{system_prompt}}
                    </system_prompt_base>

                    <user_prompt_base>
                    {{user_prompt}}
                    </user_prompt_base>

                    提示词涉及的可用的工具列表如下：
                    <tools_description>
                    {{tools_description}}
                    </tools_description>

                    然而，这个提示词在以下实例中并未能给出正确的结果
                    <bad_cases>
                    {{bad_cases}}
                    </bad_cases>

                    请提供详细的反馈，分析指令可能出错的原因。
                    针对每个实例，具体说明指令中的问题，解释代理为何会误解指令，并提出如何让指令更加清晰和精确的建议
                    针对因模型调用失败导致的失败原因，可以不分析
                    每个反馈信息请用<INS>和</INS>包裹
                    """)
            .build();

    public static final PromptTemplate CREATE_BAD_CASE_TEMPLATE = PromptTemplate.builder()
            .content("""
                    [question]: {{question}}
                    [expected answer]: {{label}}
                    [assistant answer]: {{answer}}
                    [reason]: {{reason}}
                    ===\s
                    """)
            .build();

    public static final PromptTemplate PLACEHOLDER_RESTORE_TEMPLATE = PromptTemplate.builder()
            .content("""
                    作为提示词优化专家，你的任务是根据给定信息补全提示词中的占位符
                    原始提示词：
                    <original_prompt>
                    {{original_prompt}}
                    </original_prompt>>

                    修改后的提示词：
                    <revised_prompt>
                    {{revised_prompt}}
                    </revised_prompt>

                    原提示词的占位符全集为：
                    <all_placeholders>
                    {{all_placeholders}}
                    </all_placeholders>

                    经比较，修改后的提示词比原提示词缺少了以下占位符：
                    <missing_placeholders>
                    {{missing_placeholders}}
                    </missing_placeholders>

                    你的目标是：
                    1. 恢复所有缺失的占位符到修改后的提示词<revised_prompt>中，参考原提示词，将占位符添加到合适的位置
                    2. 占位符需要以双花括号的形式添加到提示词中，例如“{{占位符名称}}”
                    3. 除了占位符的必要修改外，不许修改提示词内容
                    4. 直接返回添加完占位符的提示词，不要添加思考过程或其他额外内容
                    """)
            .build();

    private final Model model;

    public InstructionOptimizer(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(modelConfig, modelClientConfig, null);
    }

    public InstructionOptimizer(ModelRequestConfig modelConfig,
                                ModelClientConfig modelClientConfig,
                                Map<String, LLMCall> parameters) {
        super(parameters);
        this.model = new Model(modelClientConfig, modelConfig);
    }

    InstructionOptimizer(Model model) {
        this(model, null);
    }

    protected InstructionOptimizer(Model model, Map<String, LLMCall> parameters) {
        super(parameters);
        this.model = model;
    }

    @Override
    protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            TextualParameter parameter = entry.getValue();
            String textualGradient = getTextualGradient(entry.getKey(), parameter, null);
            LLMCall llmCall = parameter.getLlmCall();
            if (!llmCall.getFreezeSystemPrompt()) {
                parameter.setGradient("system_prompt", textualGradient);
            }
            if (!llmCall.getFreezeUserPrompt()) {
                parameter.setGradient("user_prompt", textualGradient);
            }
        }
    }

    @Override
    protected void doUpdate() {
        for (TextualParameter parameter : parameters.values()) {
            LLMCall llmCall = parameter.getLlmCall();
            if (!llmCall.getFreezeSystemPrompt() && !llmCall.getFreezeUserPrompt()) {
                optimizeBothSystemAndUserPrompt(parameter);
            } else if (!llmCall.getFreezeSystemPrompt()) {
                optimizeSystemOrUserPrompt(parameter, "system_prompt");
            } else if (!llmCall.getFreezeUserPrompt()) {
                optimizeSystemOrUserPrompt(parameter, "user_prompt");
            }
        }
    }

    @Override
    public void update() {
        doUpdate();
    }

    void optimizeBothSystemAndUserPrompt(TextualParameter parameter) {
        PromptPair optimizedPrompts = optimizeBothInstruction(
                parameter.getLlmCall().getSystemPrompt(),
                parameter.getLlmCall().getUserPrompt(),
                parameter.getGradient("system_prompt"),
                null
        );
        String optimizedSystemPrompt = validateAndReviseOptimizedPrompt(
                TuneUtils.getContentStringFromTemplate(parameter.getLlmCall().getSystemPrompt()),
                optimizedPrompts.systemPrompt()
        );
        String optimizedUserPrompt = validateAndReviseOptimizedPrompt(
                TuneUtils.getContentStringFromTemplate(parameter.getLlmCall().getUserPrompt()),
                optimizedPrompts.userPrompt()
        );
        parameter.getLlmCall().updateSystemPrompt(optimizedSystemPrompt);
        parameter.getLlmCall().updateUserPrompt(optimizedUserPrompt);
    }

    void optimizeSystemOrUserPrompt(TextualParameter parameter, String promptType) {
        PromptTemplate targetPrompt = "system_prompt".equals(promptType)
                ? parameter.getLlmCall().getSystemPrompt()
                : parameter.getLlmCall().getUserPrompt();
        String optimizedPrompt = optimizeInstruction(
                targetPrompt,
                parameter.getGradient("system_prompt"),
                null
        );
        optimizedPrompt = validateAndReviseOptimizedPrompt(
                TuneUtils.getContentStringFromTemplate(targetPrompt),
                optimizedPrompt
        );
        if ("system_prompt".equals(promptType)) {
            parameter.getLlmCall().updateSystemPrompt(optimizedPrompt);
        } else {
            parameter.getLlmCall().updateUserPrompt(optimizedPrompt);
        }
    }

    protected String getTextualGradient(String name, TextualParameter parameter, Object tools) {
        LLMCall llmCall = parameter.getLlmCall();
        Map<String, Object> keywords = new LinkedHashMap<>();
        keywords.put("system_prompt", TuneUtils.getContentStringFromTemplate(llmCall.getSystemPrompt()));
        keywords.put("user_prompt", TuneUtils.getContentStringFromTemplate(llmCall.getUserPrompt()));
        keywords.put("bad_cases", getBadCasesString());
        keywords.put("tools_description", tools == null ? "None" : String.valueOf(tools));
        List<BaseMessage> messages = CREATE_PROMPT_TEXTUAL_GRADIENT_TEMPLATE.format(keywords).toMessages();
        return invokeModel(messages);
    }

    String optimizeInstruction(PromptTemplate instruction, String textualGradient, Object tools) {
        Map<String, Object> keywords = new LinkedHashMap<>();
        keywords.put("prompt_instruction", TuneUtils.getContentStringFromTemplate(instruction));
        keywords.put("bad_cases", getBadCasesString());
        keywords.put("reflections_on_bad_cases", textualGradient);
        keywords.put("tools_description", tools == null ? "None" : String.valueOf(tools));
        List<BaseMessage> messages = PROMPT_INSTRUCTION_OPTIMIZE_TEMPLATE.format(keywords).toMessages();
        String response = invokeModel(messages);
        return extractOptimizedPromptFromResponse(response, "PROMPT_OPTIMIZED");
    }

    PromptPair optimizeBothInstruction(PromptTemplate systemPrompt,
                                       PromptTemplate userPrompt,
                                       String textualGradient,
                                       Object tools) {
        Map<String, Object> keywords = new LinkedHashMap<>();
        keywords.put("system_prompt", TuneUtils.getContentStringFromTemplate(systemPrompt));
        keywords.put("user_prompt", TuneUtils.getContentStringFromTemplate(userPrompt));
        keywords.put("bad_cases", getBadCasesString());
        keywords.put("reflections_on_bad_cases", textualGradient);
        keywords.put("tools_description", tools == null ? "None" : String.valueOf(tools));
        List<BaseMessage> messages = PROMPT_INSTRUCTION_OPTIMIZE_BOTH_TEMPLATE.format(keywords).toMessages();
        String response = invokeModel(messages);
        String optimizedSystemPrompt = extractOptimizedPromptFromResponse(response, "SYSTEM_PROMPT_OPTIMIZED");
        String optimizedUserPrompt = extractOptimizedPromptFromResponse(response, "USER_PROMPT_OPTIMIZED");
        return new PromptPair(optimizedSystemPrompt, optimizedUserPrompt);
    }

    static String extractOptimizedPromptFromResponse(String response, String tag) {
        Pattern pattern = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response == null ? "" : response);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1)
                .replace("<prompt_base>", "")
                .replace("</prompt_base>", "");
    }

    String getBadCasesString() {
        StringBuilder builder = new StringBuilder();
        for (EvaluatedCase evaluatedCase : badCases) {
            Map<String, Object> keywords = new LinkedHashMap<>();
            keywords.put("question", pythonStyleValue(evaluatedCase.getInputs()));
            keywords.put("label", pythonStyleValue(evaluatedCase.getLabel()));
            keywords.put("answer", pythonStyleValue(evaluatedCase.getAnswer()));
            keywords.put("reason", evaluatedCase.getReason());
            Object content = CREATE_BAD_CASE_TEMPLATE.format(keywords).getContent();
            if (content != null) {
                builder.append(content);
            }
        }
        return builder.toString();
    }

    @Override
    protected List<EvaluatedCase> getBadCases(List<EvaluatedCase> evaluatedCases) {
        List<EvaluatedCase> selected = (evaluatedCases == null ? List.<EvaluatedCase>of() : evaluatedCases).stream()
                .filter(caseValue -> Double.compare(caseValue.getScore(), 0.0d) == 0)
                .collect(Collectors.toCollection(ArrayList::new));
        if (selected.size() > TuneConstant.DEFAULT_MAX_SAMPLED_EXAMPLE_NUM) {
            selected = sample(selected, TuneConstant.DEFAULT_MAX_SAMPLED_EXAMPLE_NUM);
        }
        badCases = selected;
        return badCases;
    }

    String validateAndReviseOptimizedPrompt(String originalPrompt, String optimizedPrompt) {
        List<String> placeholders = findPlaceholdersFromPrompt(originalPrompt);
        List<String> updatedPlaceholders = findPlaceholdersFromPrompt(optimizedPrompt);
        List<String> missingPlaceholders = findMissingPlaceholders(placeholders, updatedPlaceholders);
        if (!missingPlaceholders.isEmpty()) {
            return addMissingPlaceholdersToPrompt(
                    originalPrompt,
                    optimizedPrompt,
                    missingPlaceholders,
                    placeholders
            );
        }
        return optimizedPrompt;
    }

    static List<String> findPlaceholdersFromPrompt(Object prompt) {
        Object content = prompt instanceof PromptTemplate template ? template.getContent() : prompt;
        return new PromptAssembler(content, "{{", "}}").getInputKeys();
    }

    static List<String> findMissingPlaceholders(List<String> originalPlaceholders, List<String> optimizedPlaceholders) {
        Set<String> optimizedSet = new LinkedHashSet<>(optimizedPlaceholders == null ? List.of() : optimizedPlaceholders);
        List<String> missing = new ArrayList<>();
        for (String placeholder : originalPlaceholders == null ? List.<String>of() : originalPlaceholders) {
            if (!optimizedSet.contains(placeholder)) {
                missing.add(placeholder);
            }
        }
        return missing;
    }

    String addMissingPlaceholdersToPrompt(String originalPrompt,
                                          String optimizedPrompt,
                                          List<String> missingPlaceholders,
                                          List<String> allPlaceholders) {
        Map<String, Object> keywords = new LinkedHashMap<>();
        keywords.put("original_prompt", originalPrompt);
        keywords.put("revised_prompt", optimizedPrompt);
        keywords.put("all_placeholders", String.valueOf(allPlaceholders));
        keywords.put("missing_placeholders", String.valueOf(missingPlaceholders));
        List<BaseMessage> messages = PLACEHOLDER_RESTORE_TEMPLATE.format(keywords).toMessages();
        String restoredPrompt = invokeModel(messages);
        List<String> restoredPlaceholders = new PromptAssembler(restoredPrompt, "{{", "}}").getInputKeys();
        List<String> stillMissing = findMissingPlaceholders(allPlaceholders, restoredPlaceholders);
        if (!stillMissing.isEmpty()) {
            restoredPrompt = restoredPrompt + "\n" + stillMissing.stream()
                    .map(placeholder -> "{{" + placeholder + "}}")
                    .collect(Collectors.joining("\n"));
        }
        return restoredPrompt;
    }

    private String invokeModel(List<BaseMessage> messages) {
        return model.invoke(messages).toCompletableFuture().join().getContentAsString();
    }

    private static <T> List<T> sample(List<T> values, int count) {
        if (count <= 0 || values.isEmpty()) {
            return new ArrayList<>();
        }
        if (count >= values.size()) {
            return new ArrayList<>(values);
        }
        List<T> shuffled = new ArrayList<>(values);
        Collections.shuffle(shuffled);
        return new ArrayList<>(shuffled.subList(0, count));
    }

    private static String pythonStyleValue(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String text) {
            return "'" + text + "'";
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                parts.add(pythonStyleValue(entry.getKey()) + ": " + pythonStyleValue(entry.getValue()));
            }
            return "{" + String.join(", ", parts) + "}";
        }
        if (value instanceof List<?> list) {
            return "[" + list.stream().map(InstructionOptimizer::pythonStyleValue).collect(Collectors.joining(", "))
                    + "]";
        }
        return String.valueOf(value);
    }

    record PromptPair(String systemPrompt, String userPrompt) {
    }
}
