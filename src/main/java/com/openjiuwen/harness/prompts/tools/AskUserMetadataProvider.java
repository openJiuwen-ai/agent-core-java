/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code AskUserMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/ask_user.py}.
 */
public final class AskUserMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTION = Map.of(
            "cn",
            """
            鍚戠敤鎴锋彁闂互鏀堕泦淇℃伅銆佹緞娓呮涔夋垨鍋氬嚭鍐崇瓥銆傛敮鎸?-4涓棶棰橈紝姣忎釜闂2-4涓€夐」銆?

            浣曟椂涓诲姩浣跨敤锛氶渶姹傛ā绯娿€佸绉嶆柟妗堝彲閫夈€佹秹鍙婄敤鎴峰亸濂芥椂锛屽簲涓诲姩璇㈤棶鑰岄潪鍋囪銆?

            銆愮姝€戦€夐」涓坊鍔?鍏朵粬'銆?鑷畾涔?绛夊厹搴曢€夐」锛岀郴缁熷凡鑷姩鎻愪緵銆?
            銆愭帹鑽愩€戝皢鎺ㄨ崘閫夐」鏀剧涓€浣嶏紝label鏈熬鍔?锛堟帹鑽愶級'銆?
            preview瀛楁浠呯敤浜庡崟閫夐棶棰樼殑瑙嗚姣旇緝鍦烘櫙銆?
            """,
            "en",
            """
            Ask user questions to gather info, clarify ambiguity, or make decisions. Supports 1-4 questions, each with 2-4 options.

            When to use proactively: Ask when requirements are vague, multiple approaches exist, or user preferences matter. Don't assume.

            FORBIDDEN: Adding 'Other', 'Custom' etc. as options 鈥?system provides this automatically.
            RECOMMENDED: Place recommended option first, append '(Recommended)' to its label.
            Preview field is only for single-select questions with visual comparison needs.
            """
    );

    private static final Map<String, Map<String, String>> ASK_USER_PARAMS = createParams();

    @Override
    public String getName() {
        return "ask_user";
    }

    @Override
    public String getDescription(String language) {
        return DESCRIPTION.getOrDefault(language, DESCRIPTION.get("cn"));
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return getAskUserInputParams(language);
    }

    public static Map<String, Object> getAskUserInputParams(String language) {
        String lang = "en".equals(language) ? "en" : "cn";

        Map<String, Object> optionProperties = new LinkedHashMap<>();
        optionProperties.put("label", property("string", ASK_USER_PARAMS.get("options_label").get(lang)));
        optionProperties.put("description", property("string", ASK_USER_PARAMS.get("options_description").get(lang)));
        optionProperties.put("preview", property("string", ASK_USER_PARAMS.get("options_preview").get(lang)));

        Map<String, Object> optionItem = new LinkedHashMap<>();
        optionItem.put("type", "object");
        optionItem.put("properties", optionProperties);
        optionItem.put("required", List.of("label", "description"));

        Map<String, Object> questionProperties = new LinkedHashMap<>();
        questionProperties.put("header", property("string", ASK_USER_PARAMS.get("header").get(lang)));
        questionProperties.put("question", property("string", ASK_USER_PARAMS.get("question").get(lang)));
        questionProperties.put("options", arrayProperty(
                ASK_USER_PARAMS.get("options").get(lang),
                optionItem
        ));

        Map<String, Object> multiSelect = property("boolean", ASK_USER_PARAMS.get("multi_select").get(lang));
        multiSelect.put("default", Boolean.FALSE);
        questionProperties.put("multi_select", multiSelect);

        Map<String, Object> questionItem = new LinkedHashMap<>();
        questionItem.put("type", "object");
        questionItem.put("properties", questionProperties);
        questionItem.put("required", List.of("header", "question", "options"));

        Map<String, Object> questions = arrayProperty(ASK_USER_PARAMS.get("questions").get(lang), questionItem);
        questions.put("minItems", 1);
        questions.put("maxItems", 4);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("questions", questions);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("questions"));
        return schema;
    }

    private static Map<String, Map<String, String>> createParams() {
        Map<String, Map<String, String>> params = new LinkedHashMap<>();
        params.put("questions", Map.of(
                "cn", "鍚戠敤鎴锋彁鍑虹殑闂鍒楄〃锛?-4涓級",
                "en", "Questions to ask the user (1-4 questions)"
        ));
        params.put("header", Map.of(
                "cn", "闂鐨勭畝鐭爣棰樻垨鏍囩",
                "en", "A short label or tag for the question (max 12 chars)"
        ));
        params.put("question", Map.of(
                "cn", "瀹屾暣鐨勯棶棰樻枃鏈?",
                "en", "The complete question to ask"
        ));
        params.put("options", Map.of(
                "cn", "鍙€夌瓟妗堝垪琛紙2-4涓級",
                "en", "Available choices for this question (2-4 options)"
        ));
        params.put("options_label", Map.of(
                "cn", "閫夐」鏄剧ず鏂囨湰锛?-5涓瘝锛?",
                "en", "The display text for this option (1-5 words)."
        ));
        params.put("options_description", Map.of(
                "cn", "閫夐」璇︾粏璇存槑",
                "en", "Explanation of what this option means or what will happen if chosen."
        ));
        params.put("options_preview", Map.of(
                "cn", "鍙€夌殑棰勮鍐呭锛岀敤浜嶶I妯″瀷銆佷唬鐮佺墖娈垫垨瑙嗚姣旇緝銆備粎鍦ㄥ崟閫夐棶棰樹腑鏀寔銆?",
                "en", "Optional preview content rendered when this option is focused. Use for mockups, code snippets, or visual comparisons. Only supported for single-select questions."
        ));
        params.put("multi_select", Map.of(
                "cn", "鏄惁鍏佽澶氶€?",
                "en", "Set to true to allow the user to select multiple options instead of just one."
        ));
        return params;
    }

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> arrayProperty(String description, Object items) {
        Map<String, Object> property = property("array", description);
        property.put("items", items);
        return property;
    }
}
