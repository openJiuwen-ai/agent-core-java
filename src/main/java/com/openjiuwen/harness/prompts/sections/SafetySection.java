/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;

import java.util.Map;

/**
 * Mirrors Python's {@code safety} in
 * {@code openjiuwen/harness/prompts/sections/safety.py}.
 */
public final class SafetySection {

    private static final String DEFAULT_LANGUAGE = "cn";
    private static final int PRIORITY = 20;

    private static final String SAFETY_PROMPT_CN = """
# 瀹夊叏鍘熷垯

- 姘歌繙涓嶈娉勯湶闅愮鏁版嵁
- 浠ヤ笅鎿嶄綔鍓嶉渶璇风ず鐢ㄦ埛锛氫慨鏀?鍒犻櫎閲嶈鏂囦欢銆佸奖鍝嶇郴缁熺殑鍛戒护銆佹秹鍙婇噾閽?璐﹀彿/鏁忔劅淇℃伅
- 杩濇硶銆佹湁瀹炽€佷镜鐘粬浜烘潈鐩婄殑璇锋眰涓嶄簣澶勭悊
- 澶栭儴鎿嶄綔锛堝彂閭欢銆佸彂鎺ㄦ枃銆佸叕寮€鍙戝竷锛夊厛闂啀鍋?-
- 鍐呴儴鎿嶄綔锛堣鏂囦欢銆佹悳绱€佹暣鐞嗭級鍙斁蹇冩墽琛?-
- 浠诲姟澶辫触鏃剁畝瑕佽鏄庡師鍥犲苟缁欏嚭寤鸿
- 涓嶇‘瀹氭椂鍏堣鏄庝笉纭畾鎬э紝鍐嶇粰鍑烘渶鍙兘鐨勬柟妗?""";

    private static final String SAFETY_PROMPT_EN = """
# Safety

- Never leak private data
- Ask first before modifying/deleting important files, running system-affecting commands, or handling money/accounts/sensitive information
- Refuse illegal, harmful, or rights-infringing requests
- Ask first before external actions such as emails, tweets, or public posts
- Internal actions such as reading files, searching, and organizing are safe to do directly
- If a task fails, briefly explain why and suggest the most practical next step
- If uncertain, state the uncertainty first, then give the most likely answer or plan
""";

    private SafetySection() {
    }

    public static PromptSection buildSafetySection(String language) {
        String resolvedLanguage = "en".equals(language) ? "en" : DEFAULT_LANGUAGE;
        String content = "en".equals(resolvedLanguage) ? SAFETY_PROMPT_EN : SAFETY_PROMPT_CN;
        return new PromptSection(
            SectionName.SAFETY,
            Map.of(resolvedLanguage, content),
            PRIORITY
        );
    }

    public static PromptSection build() {
        return buildSafetySection(DEFAULT_LANGUAGE);
    }
}
