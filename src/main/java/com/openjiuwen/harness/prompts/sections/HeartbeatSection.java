/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Heartbeat prompt section builder.
 *
 * <p>Mirrors Python's {@code heartbeat} in
 * {@code openjiuwen.harness.prompts.sections.heartbeat}.</p>
 */
public final class HeartbeatSection {

    private static final String CN_TEMPLATE = """
            ## 蹇冭烦妫€娴?
            %s

            褰撴敹鍒板績璺虫娴嬫秷鎭椂锛?
            - 鑻ヤ笂鏂规棤蹇冭烦鍐呭锛岃绮剧‘鍥炲锛欻EARTBEAT_OK
            - 鑻ヤ笂鏂规湁蹇冭烦鍐呭锛屽繀椤诲鐞嗗績璺冲唴瀹圭殑浜嬮」骞惰繘琛屽洖澶嶏紙涓嶈鍖呭惈 HEARTBEAT_OK锛?

            绯荤粺浼氳瘑鍒?HEARTBEAT_OK 浣滀负蹇冭烦纭銆?
            """;

    private static final String EN_TEMPLATE = """
            ## Heartbeat
            %s

            When you receive a heartbeat message:
            - If there is no heartbeat content above, reply exactly: HEARTBEAT_OK
            - If there is heartbeat content above, handle it and do not include HEARTBEAT_OK

            The system recognizes HEARTBEAT_OK as a heartbeat acknowledgment.
            """;

    private HeartbeatSection() {
    }

    public static PromptSection build() {
        return build("cn", "");
    }

    public static PromptSection build(String language, String heartbeatContent) {
        String effectiveLanguage = language == null || language.isBlank() ? "cn" : language;
        String cleaned = cleanHeartbeatContent(heartbeatContent);
        if (cleaned.isBlank()) {
            cleaned = "cn".equals(effectiveLanguage) ? "锛堟棤蹇冭烦鍐呭锛?" : "(No heartbeat content)";
        }
        Map<String, String> content = new LinkedHashMap<>();
        content.put(effectiveLanguage, ("en".equals(effectiveLanguage) ? EN_TEMPLATE : CN_TEMPLATE).formatted(cleaned));
        return new PromptSection(SectionName.HEARTBEAT, content, 80);
    }

    public static String cleanHeartbeatContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder();
        for (String line : content.split("\\R")) {
            String stripped = line.trim();
            if (stripped.isEmpty()) {
                continue;
            }
            if (stripped.startsWith("<!--") && stripped.endsWith("-->")) {
                continue;
            }
            if (cleaned.length() > 0) {
                cleaned.append('\n');
            }
            cleaned.append(stripped);
        }
        return cleaned.toString();
    }
}
