/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Tool description reviewer and post-processor.
 *
 * <p>Mirrors Python's {@code ToolDescriptionReviewer} in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/customized_reviewer.py}.</p>
 */
public class ToolDescriptionReviewer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ObjectWriter PRETTY_WRITER = OBJECT_MAPPER.writerWithDefaultPrettyPrinter();
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern ENGLISH_LETTER = Pattern.compile("[a-zA-Z]");

    private final String evalModelId;
    private final String llmApiKey;
    private final List<Function<?, ?>> processors;

    public ToolDescriptionReviewer(String evalModelId, String llmApiKey) {
        this.evalModelId = evalModelId;
        this.llmApiKey = llmApiKey;
        this.processors = new ArrayList<>();
    }

    public Object format(Map<String, Object> jsonSchema, String description) {
        return format(jsonSchema, description, null);
    }

    public Object format(Map<String, Object> jsonSchema, String description, String example) {
        String prompt = buildFormatPrompt(jsonSchema, description);
        return invokeRitsJsonLikePython("gpt-5.2", prompt);
    }

    public Object cleanAndDeduplicate(Object data) {
        String prompt = buildCleanPrompt(data);
        return invokeRitsJsonLikePython(evalModelId, prompt);
    }

    public Object crossCheck(Object data, String oriTool) {
        String prompt = buildCrossCheckPrompt(data, oriTool);
        return invokeRitsJsonLikePython(evalModelId, prompt);
    }

    public Object translateToChinese(Object data) {
        String jsonString = toJson(data, false);
        if (!isMostlyEnglish(jsonString)) {
            return data;
        }
        String prompt = buildTranslatePrompt(data);
        return invokeRitsJsonLikePython(evalModelId, prompt);
    }

    public Object process(Object data, String oriTool, List<String> steps) {
        Object result = data;
        for (String step : steps) {
            if ("cross_check".equals(step)) {
                result = crossCheck(data, oriTool);
            } else if ("clean".equals(step)) {
                result = cleanAndDeduplicate(result);
            } else if ("translate".equals(step)) {
                result = translateToChinese(result);
            } else {
                throw new IllegalArgumentException("Unknown processing step: " + step);
            }
        }
        return result;
    }

    public boolean isMostlyEnglish(String text) {
        String textNoSpace = WHITESPACE.matcher(text == null ? "" : text).replaceAll("");
        if (textNoSpace.isEmpty()) {
            return false;
        }
        long englishChars = ENGLISH_LETTER.matcher(textNoSpace).results().count();
        double englishRatio = (double) englishChars / textNoSpace.length();
        return englishRatio > 0.7d;
    }

    public String getEvalModelId() {
        return evalModelId;
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public List<Function<?, ?>> getProcessors() {
        return processors;
    }

    protected String buildFormatPrompt(Map<String, Object> jsonSchema, String description) {
        return """
将下面输入转换为目标 JSON 结构。必须满足：

- 输出只允许是有效 JSON，且严格匹配目标结构的键路径与层级（不多不少）。
- 语义必须完全保留：不新增、不删减、不改写含义；可改写措辞以压缩。
- description 去冗余是强制要求：
    - 任何 “每项包含/含有/由…组成/字段包括…” 这类字段清单式描述都必须删除或改写为非清单表述。
    - 不得在 description 中重复 schema 已表达的信息：字段名、字段类型、required 已涵盖的“必填”。
    - 仅保留 schema 无法表达或未显式表达的约束到 description，例如：
        - 覆盖区间/不得留隙/分段规则
        - 默认值语义（如 inflationRate 默认 0）
        - 业务规则（按年累加、考虑通胀等）
    - 枚举值列表只出现一次，放在最贴近字段的位置（通常是该字段的 description）；不得在父级/子级重复。
    如输入中 description 同时包含“字段清单 + 业务约束”，只保留业务约束部分。
    - 若某个 description 完全是冗余字段清单，允许变为简短描述，但不得留空（除非输入本身为空）。
- 请直接输出转换后的 JSON，不要附加解释。

这是目标的json 模板:
%s

下面是你需要修改的json，生成后请自检：所有 description 中不得出现“含/包含/包括/each item/contains/fields”等字段列举句式；否则重写直到满足。

Input:
%s
""".formatted(toJson(jsonSchema, true), stringValue(description));
    }

    protected String buildCleanPrompt(Object data) {
        return """

Given a tool description JSON, go throught the content sentence\s
by sentence and perform the following cleaning tasks:

1. Remove usage example in the main tool description
2. Remove redundant "必填"/"可选"/"required"/"optional" markers in parameter\s
descriptions if they appear in 'required' session
3. Remove verbose, redundant descriptions including:
   - Disclaimers like "若输入无效会返回空结果",\s
    "若输入代码无效或未收录会返回未找到或空结果"
   - Obvious statements like "结果可能有延迟"
   - Suggestions like "调用者应自行进行进一步分析或合成总结",\s
    "调用者应在本接口返回后自行进行进一步分析"
   - Irrelevant exclusions that are clearly not in the tool's\s
    functional scope. e.g. the tool name is maps_directions,\s
    since it's a direction tool, statements like "不提供预订或支付功能"\s
    or "不支持语音导航" is clearly irrelevant and need to be removed.
   - Any other unnecessary verbose content
4. Clean up descriptions: for parameter descriptions incorrectly\s
mixed into the tool descriptions, relocate them to ensure that\s
each parameter description is correctly placed in its corresponding\s
parameter description instead of the main tool description session.

**Pay attention to KEEP statements on ACTUAL functionality boundaries**
Keep only unique, essential, and actionable information. Output only the\s
cleaned JSON without explanations. DO NOT change the overall structure of JSON.

Input JSON:
%s
""".formatted(toJson(data, true));
    }

    protected String buildCrossCheckPrompt(Object data, String oriTool) {
        return """
比较原始描述和修改后的描述，按照以下要求整理修改后的描述：
1. 补充修改后的描述丢失的信息：例如，参数可选值列表丢失，需把原始描述中的列表补充道修改后的对应位置。
2. 确保参数描述信息和工具描述信息位置正确：参考原始描述，确保工具描述中只包含对工具能力、边界等信息，确保参数具体细节要求应在对应的参数描述中，例如：“仅支持经纬度作为输入”应当放在对应的参数描述中，不应当放在主工具能力边界中。

确保不要改变json格式，仅修改文字内容。不要删除内容，仅做整理和补充丢失信息。

原始描述：
%s

修改后描述（待优化）：
%s
""".formatted(stringValue(oriTool), toJson(data, true));
    }

    protected String buildTranslatePrompt(Object data) {
        return """
Translate all English text in the following JSON to Chinese.
Keep JSON structure unchanged. Keep technical terms and code examples as-is.
Output only the translated JSON without explanations.

Input JSON:
%s
""".formatted(toJson(data, true));
    }

    protected Object invokeRitsJsonLikePython(String modelId, String prompt) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("verify_output", (Function<String, Object>) this::parseJsonResponse);
        kwargs.put("max_attempts", 5);
        kwargs.put("include_stop_sequence", false);
        return invokeRitsResponse(modelId, prompt, false, kwargs);
    }

    protected Object invokeRitsResponse(
            String modelId,
            String prompt,
            boolean verbose,
            Map<String, Object> kwargs
    ) {
        try {
            Class<?> ritsClass = Class.forName(
                    "com.openjiuwen.agent_evolving.optimizer.tool_call.utils.RitsUtils"
            );
            for (Method method : ritsClass.getMethods()) {
                if (!"getRitsResponse".equals(method.getName())) {
                    continue;
                }
                if (method.getParameterCount() == 6) {
                    return method.invoke(null, modelId, prompt, llmApiKey, null, verbose, kwargs);
                }
                if (method.getParameterCount() == 3) {
                    return method.invoke(null, modelId, prompt, llmApiKey);
                }
            }
            throw new IllegalStateException("RitsUtils getRitsResponse method is unavailable");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("RitsUtils dependency is unavailable", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("RitsUtils dependency could not be invoked", exception);
        }
    }

    protected Object parseJsonResponse(String response) {
        try {
            return OBJECT_MAPPER.readValue(response, Object.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse JSON response", exception);
        }
    }

    private static String toJson(Object value, boolean pretty) {
        try {
            return pretty ? PRETTY_WRITER.writeValueAsString(value) : OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to serialize JSON", exception);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
