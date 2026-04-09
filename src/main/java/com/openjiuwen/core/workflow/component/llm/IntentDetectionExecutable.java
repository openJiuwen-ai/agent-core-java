  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.BranchRouter;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executable for intent detection that invokes an LLM to classify user input
 * and routes to the appropriate branch.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.intent_detection_comp.IntentDetectionExecutable}.
 */
public class IntentDetectionExecutable extends ComponentExecutable {

    private static final String CLASS_KEY = "class";
    private static final String REASON_KEY = "reason";
    private static final String CLASSIFICATION_ID = "classificationId";
    private static final String CLASSIFICATION_NAME = "name";
    private static final int CLASSIFICATION_DEFAULT_ID = 0;

    private static final Map<String, String> ROLE_MAP_ZH = Map.of(
            "user", "用户", "assistant", "助手", "system", "系统", "tool", "工具");
    private static final Map<String, String> ROLE_MAP_EN = Map.of(
            "user", "User", "assistant", "Assistant", "system", "System", "tool", "Tool");

    private NodeSessionApi session;
    private Model llm;
    private boolean initialized = false;
    private final IntentDetectionCompConfig config;
    private final IntentDetectionDefaultConfig defaultConfig;
    private BranchRouter router;

    public IntentDetectionExecutable(IntentDetectionCompConfig componentConfig) {
        super();
        this.config = componentConfig;
        String lang = componentConfig.getAcceptLanguage() != null ? componentConfig.getAcceptLanguage() : "zh";
        this.defaultConfig = new IntentDetectionDefaultConfig(lang);
        initDefaultConfigCategoryList(componentConfig);
        appendDefaultCategory();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        this.session = session;
        if (router != null) {
            router.setSession(session);
        }
        initializeIfNeeded();

        List<BaseMessage> chatHistory = getChatHistoryFromContext(context);
        Map<String, Object> inputsMap = inputs instanceof Map ? (Map<String, Object>) inputs : Map.of();
        Map<String, Object> currentInputs = prepareDetectionInputs(inputsMap, chatHistory);
        String llmOutput = invokeLLMAndGetResult(currentInputs);
        return parseDetectionResult(llmOutput);
    }

    /**
     * Set the branch router.
     */
    public IntentDetectionExecutable setRouter(BranchRouter router) {
        this.router = router;
        return this;
    }

    @Override
    public boolean postCommit() {
        return true;
    }

    // ==================== Private Methods ====================

    private void initializeIfNeeded() {
        if (!initialized) {
            try {
                llm = createLLMInstance();
                initialized = true;
            } catch (Exception e) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_INTENT_DETECTION_LLM_INIT_FAILED,
                        "error_msg", "failed to initialize llm: " + e.getMessage());
            }
        }
    }

    private Model createLLMInstance() {
        if (config.getModelId() != null) {
            if (config.getModelClientConfig() == null || config.getModelConfig() == null) {
                throw ErrorHelper.buildError(StatusCode.COMPONENT_INTENT_DETECTION_INVOKE_CALL_FAILED,
                        "error_msg", "failed to create llm instance");
            }
            return new Model(config.getModelClientConfig(), config.getModelConfig());
        }
        if (config.getModelClientConfig() == null || config.getModelConfig() == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_INTENT_DETECTION_INVOKE_CALL_FAILED,
                    "error_msg", "failed to create llm instance");
        }
        return new Model(config.getModelClientConfig(), config.getModelConfig());
    }

    private List<BaseMessage> getChatHistoryFromContext(ModelContext context) {
        List<BaseMessage> chatHistory = new ArrayList<>();
        if (config.isEnableHistory() && context != null) {
            List<BaseMessage> messages = context.getMessages();
            if (messages != null) {
                chatHistory.addAll(messages);
            }
        }
        return chatHistory;
    }

    private String getCategoryInfo() {
        StringBuilder sb = new StringBuilder();
        List<String> categoryList = defaultConfig.getCategoryList();
        List<String> categoryNameList = config.getCategoryNameList();
        for (int i = 0; i < categoryList.size() && i < categoryNameList.size(); i++) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(categoryList.get(i)).append(": ").append(categoryNameList.get(i));
        }
        return sb.toString();
    }

    private Map<String, Object> prepareDetectionInputs(Map<String, Object> inputs, List<BaseMessage> chatHistory) {
        Map<String, Object> currentInputs = new LinkedHashMap<>();
        currentInputs.put("user_prompt", config.getUserPrompt());
        currentInputs.put("category_info", getCategoryInfo());
        currentInputs.put("default_class", defaultConfig.getDefaultClass());
        currentInputs.put("enable_history", config.isEnableHistory());
        currentInputs.put("enable_input", defaultConfig.isEnableInput());
        currentInputs.put("example_content", String.join("\n\n", config.getExampleContent()));
        currentInputs.put("chat_history_max_turn", config.getChatHistoryMaxTurn());
        currentInputs.put("chat_history", "");

        if (config.isEnableHistory()) {
            currentInputs.put("chat_history", formatChatHistory(chatHistory));
        }

        if (defaultConfig.isEnableInput()) {
            IntentDetectionInput detectionInput = IntentDetectionInput.fromMap(inputs);
            currentInputs.put("input", detectionInput.getQuery());
        }

        return currentInputs;
    }

    private String formatChatHistory(List<BaseMessage> chatHistory) {
        Map<String, String> roleMap = "en".equals(config.getAcceptLanguage()) ? ROLE_MAP_EN : ROLE_MAP_ZH;
        StringBuilder sb = new StringBuilder();
        int maxTurn = config.getChatHistoryMaxTurn();
        int start = Math.max(0, chatHistory.size() - maxTurn);
        for (int i = start; i < chatHistory.size(); i++) {
            BaseMessage msg = chatHistory.get(i);
            String role = msg.getRole();
            if (roleMap.containsKey(role)) {
                sb.append(roleMap.get(role)).append(": ")
                        .append(msg.getContent() != null ? msg.getContent().toString() : "")
                        .append("\n");
            }
        }
        return sb.toString();
    }

    private String invokeLLMAndGetResult(Map<String, Object> currentInputs) {
        List<BaseMessage> llmInputs = defaultConfig.getIntentDetectionTemplate()
                .format(currentInputs).toMessages();

        try {
            AssistantMessage llmOutput = llm.invoke(llmInputs, null, null, null, null, null, null, null, null, null);
            return llmOutput.getContent() != null ? llmOutput.getContent().toString() : "";
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_INTENT_DETECTION_INVOKE_CALL_FAILED,
                    "error_msg", "failed to invoke llm: " + e.getMessage());
        }
    }

    private Map<String, Object> parseDetectionResult(String llmOutput) {
        String[] classAndReason = postProcessIntentDetection(llmOutput);
        String intentClass = classAndReason[0];
        String reason = classAndReason[1];
        Map<String, Object> intentIdAndName = getIntentIdAndName(intentClass);
        return new IntentDetectionOutput(
                (int) intentIdAndName.getOrDefault(CLASSIFICATION_ID, -1),
                reason,
                (String) intentIdAndName.getOrDefault(CLASSIFICATION_NAME, "")
        ).toMap();
    }

    private String[] postProcessIntentDetection(String result) {
        String lang = config.getAcceptLanguage();
        boolean isEn = "en".equals(lang);
        String jsonFailReason = isEn
                ? "The intent detection output '%s' does not conform to valid JSON format, parsing failed, therefore default category is returned."
                : "当前意图识别的输出:'%s'格式不符合有效的JSON规范，导致解析失败，因此返回默认分类。";
        String classMissingReason = isEn
                ? "The intent detection output '%s' is missing the required 'class' classification field, therefore default category is returned."
                : "当前意图识别的输出 '%s' 缺少必要的输出'class'分类信息，因此返回默认分类。";
        String validationFailReason = isEn
                ? "The intent detection output class '%s' is not in the predefined category list: '%s', therefore default category is returned."
                : "当前意图识别的输出类别 '%s' 不在预定义的分类列表: '%s'中，因此系统返回默认分类。";
        String categoryPattern = isEn ? "Category\\d+" : "分类\\d+";

        // Try to extract JSON from result
        result = refixLlmOutput(result);

        Map<String, Object> parsedDict;
        try {
            parsedDict = JsonParser.parseJsonContent(result);
        } catch (Exception e) {
            return new String[]{defaultConfig.getDefaultClass(), String.format(jsonFailReason, result)};
        }

        if (parsedDict == null || parsedDict.isEmpty()) {
            return new String[]{defaultConfig.getDefaultClass(), String.format(jsonFailReason, result)};
        }

        Object classValue = parsedDict.get(CLASS_KEY);
        if (classValue == null || classValue.toString().isEmpty()) {
            return new String[]{defaultConfig.getDefaultClass(), String.format(classMissingReason, parsedDict)};
        }

        String intentClass = classValue.toString()
                .replace("\n", "").replace(" ", "")
                .replace("\"", "").replace("'", "");

        Matcher matcher = Pattern.compile(categoryPattern, Pattern.CASE_INSENSITIVE).matcher(intentClass);
        if (matcher.find()) {
            String matched = matcher.group(0);
            if (isEn) {
                Matcher digitMatcher = Pattern.compile("\\d+").matcher(matched);
                if (digitMatcher.find()) {
                    matched = "Category" + digitMatcher.group(0);
                }
            }
            intentClass = matched;
        }

        if (!defaultConfig.getCategoryList().contains(intentClass)) {
            String reason = String.format(validationFailReason, intentClass, defaultConfig.getCategoryList());
            return new String[]{defaultConfig.getDefaultClass(), reason};
        }

        String reason = parsedDict.containsKey(REASON_KEY) ? parsedDict.get(REASON_KEY).toString() : "";
        return new String[]{intentClass, reason};
    }

    private static String refixLlmOutput(String inputStr) {
        Matcher matcher = Pattern.compile("\\{.*}", Pattern.DOTALL).matcher(inputStr);
        if (matcher.find()) {
            String res = matcher.group(0);
            res = res.replace("false", "False").replace("true", "True").replace("null", "None");
            // Convert Python-style booleans back to JSON-compatible
            res = res.replace("False", "false").replace("True", "true").replace("None", "null");
            return res;
        }
        return inputStr;
    }

    private Map<String, Object> getIntentIdAndName(String intentClass) {
        String defaultName = "en".equals(config.getAcceptLanguage()) ? "Default intent" : "默认意图";
        Map<String, Object> intentRes = new HashMap<>();
        intentRes.put(CLASSIFICATION_ID, CLASSIFICATION_DEFAULT_ID);
        intentRes.put(CLASSIFICATION_NAME, defaultName);

        List<String> categoryList = defaultConfig.getCategoryList();
        for (int i = 0; i < categoryList.size(); i++) {
            if (categoryList.get(i).equals(intentClass)) {
                intentRes.put(CLASSIFICATION_ID, i);
                List<String> nameList = config.getCategoryNameList();
                if (i < nameList.size()) {
                    intentRes.put(CLASSIFICATION_NAME, nameList.get(i));
                }
                break;
            }
        }
        return intentRes;
    }

    private void initDefaultConfigCategoryList(IntentDetectionCompConfig componentConfig) {
        String lang = componentConfig.getAcceptLanguage() != null ? componentConfig.getAcceptLanguage() : "zh";
        String categoryPrefix = "en".equals(lang) ? "Category" : "分类";
        for (int i = 0; i < componentConfig.getCategoryNameList().size(); i++) {
            defaultConfig.getCategoryList().add(categoryPrefix + (i + 1));
        }
    }

    private void appendDefaultCategory() {
        String defaultName = "en".equals(config.getAcceptLanguage()) ? "Default intent" : "默认意图";
        List<String> newCategoryList = new ArrayList<>();
        newCategoryList.add(defaultConfig.getDefaultClass());
        newCategoryList.addAll(defaultConfig.getCategoryList());
        defaultConfig.setCategoryList(newCategoryList);

        List<String> newNameList = new ArrayList<>();
        newNameList.add(defaultName);
        newNameList.addAll(config.getCategoryNameList());
        config.setCategoryNameList(newNameList);
    }
}
