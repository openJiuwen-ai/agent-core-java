/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's helper surface in
 * {@code openjiuwen/harness/prompts/sections/progressive_tool_rail.py}.
 */
public final class ProgressiveToolRailSection {

    private ProgressiveToolRailSection() {
    }

    private static final String PROGRESSIVE_TOOL_NAVIGATION_HEADER_CN = """
## 宸ュ叿瀵艰埅
浠ヤ笅鏉＄洰鐢ㄤ簬甯姪浣犵悊瑙ｅ綋鍓?session 涓嬬殑宸ュ叿鐢熸€併€俓n
璇锋敞鎰忥細杩欓噷灞曠ず鐨勬槸鈥滃伐鍏峰湴鍥锯€濓紝涓嶆槸鈥滃叏閮ㄥ彲绔嬪嵆璋冪敤鐨勫伐鍏锋竻鍗曗€濄€俓n
鍙湁鍦ㄥ綋鍓?session 涓樉寮忚皟鐢?`load_tools` 鍚庯紝鐩爣宸ュ叿鎵嶄細杩涘叆鍙皟鐢ㄧ姸鎬併€俓n
""";

    private static final String PROGRESSIVE_TOOL_NAVIGATION_HEADER_EN = """
## Tool Navigation
The entries below help you understand the tool ecosystem available in the current session.
Treat this section as a tool map, not as a full list of immediately callable tools.
A tool becomes callable only after `load_tools` has been explicitly called for it in the current session.
""";

    private static final String PROGRESSIVE_TOOL_NAVIGATION_EMPTY_CN = "- 锛堝綋鍓嶆棤鍙睍绀虹殑瀵艰埅鏉＄洰锛?";
    private static final String PROGRESSIVE_TOOL_NAVIGATION_EMPTY_EN = "- (no navigation entries available)";

    private static final String PROGRESSIVE_TOOL_RULES_HEADER_CN = "## 娓愯繘寮忓伐鍏蜂娇鐢ㄨ鍒橽n";
    private static final String PROGRESSIVE_TOOL_RULES_HEADER_EN = "## Progressive Tool Usage Rules\n";

    private static final String PROGRESSIVE_TOOL_RULES_BODY_CN = """
浣犳鍦ㄤ竴涓笎杩涘紡宸ュ叿鐜涓伐浣溿€俓n
璇蜂弗鏍奸伒寰互涓嬭鍒欙細
1. 褰撲綘涓嶇‘瀹氳浣跨敤鍝釜宸ュ叿鏃讹紝鍏堣皟鐢?`search_tools` 鏌ユ壘鍊欓€夊伐鍏枫€俓n
2. 濡傞渶鏌ョ湅鏇村缁嗚妭锛屽彲鐩存帴鎻愰珮 `search_tools` 鐨?`detail_level`锛?=鍙傛暟鎽樿锛?=瀹屾暣鍙傛暟锛夈€俓n
3. 鍦ㄥ鑸尯鎴栨悳绱㈢粨鏋滀腑鐪嬪埌鏌愪釜宸ュ叿锛屽苟涓嶆剰鍛崇潃瀹冨凡缁忓彲璋冪敤銆俓n
4. 鐪熷疄宸ュ叿鍙湁鍦ㄥ綋鍓?session 涓樉寮忚皟鐢?`load_tools` 鍚庢墠鍙皟鐢ㄣ€俓n
5. 涓€鏃︿綘宸茬粡閫氳繃 `search_tools` 鎵惧埌瑕佷娇鐢ㄧ殑鐩爣宸ュ叿锛屼笅涓€姝ュ簲绔嬪嵆璋冪敤 `load_tools`锛岃€屼笉鏄户缁彧鐢ㄦ枃瀛楁弿杩拌鍒掋€俓n
6. 鍦ㄦ墍闇€宸ュ叿灏氭湭鍔犺浇鍓嶏紝涓嶈澹扮О浣犲皢瑕佹鏌ユ枃浠躲€佽鍙栫洰褰曘€佽В鏋愭枃妗ｃ€佺敓鎴愯〃鏍兼垨鎵ц浠讳綍渚濊禆杩欎簺宸ュ叿鐨勫姩浣滐紱搴斿厛鍔犺浇宸ュ叿锛屽啀鎵ц銆俓n
7. 濡傛灉浠诲姟娑夊強鏂囦欢妫€鏌ャ€丳DF 澶勭悊銆乆LSX 鐢熸垚銆佺洰褰曟祻瑙堟垨鏁版嵁澶勭悊锛屼綘搴斿敖蹇粠鎼滅储缁撴灉涓€夋嫨鍚堥€傚伐鍏峰苟璋冪敤 `load_tools`锛岄殢鍚庣珛鍒讳娇鐢ㄧ湡瀹炲伐鍏锋墽琛屻€俓n
8. 涓嶈鍋滅暀鍦ㄢ€滀笅涓€姝ユ垜灏嗏€︹€︹€濊繖绫昏嚜鐒惰瑷€璁″垝涓婏紱鑻ュ凡鏈夎冻澶熶俊鎭€夋嫨宸ュ叿锛屽氨鐩存帴杩涘叆 `load_tools` 鍜岀湡瀹炲伐鍏疯皟鐢ㄣ€俓n
9. 宸ヤ綔椤哄簭搴斿敖閲忎繚鎸佷负锛氬厛瀵艰埅锛屽啀鎼滅储锛屽繀瑕佹椂鐪嬫洿璇︾粏缁撴灉锛屽啀鍔犺浇锛屾渶鍚庢墽琛屻€俓n
""";

    private static final String PROGRESSIVE_TOOL_RULES_BODY_EN = """
You are operating in a progressive tool environment.
Follow these rules strictly:
1. If you are unsure which tool to use, call `search_tools` first.
2. If you need more detail, increase `search_tools.detail_level` directly (2=parameter summary, 3=full parameters).
3. Seeing a tool in navigation or search results does NOT make it callable.
4. A real tool becomes callable only after `load_tools` has been explicitly called for it in the current session.
5. Once `search_tools` has identified the tools you want, the next step should be to call `load_tools` immediately, rather than continuing with natural-language planning only.
6. Do not claim that you will inspect files, browse directories, parse documents, generate spreadsheets, or perform any other tool-dependent action before the required tools have been loaded.
7. If the task involves file inspection, PDF processing, XLSX generation, directory browsing, or data processing, select suitable tools from search results, call `load_tools`, and then use the real tools right away.
8. Do not stop at statements like 'next I will ...'. If you already have enough information to choose tools, move directly to `load_tools` and then to real tool execution.
9. Prefer this sequence: navigate first, search second, inspect richer results when needed, load third, execute last.
""";

    public static String buildNavigationPrompt(Iterable<String> entries, String language) {
        List<String> items = new ArrayList<>();
        if (entries != null) {
            for (String item : entries) {
                if (item != null && !item.isEmpty()) {
                    items.add(item);
                }
            }
        }

        String resolvedLanguage = "en".equals(language) ? "en" : "cn";
        String header = "en".equals(resolvedLanguage)
            ? PROGRESSIVE_TOOL_NAVIGATION_HEADER_EN
            : PROGRESSIVE_TOOL_NAVIGATION_HEADER_CN;

        if (items.isEmpty()) {
            String emptyText = "en".equals(resolvedLanguage)
                ? PROGRESSIVE_TOOL_NAVIGATION_EMPTY_EN
                : PROGRESSIVE_TOOL_NAVIGATION_EMPTY_CN;
            return header + "\n" + emptyText;
        }
        return header + "\n" + String.join("\n", items);
    }

    public static String buildProgressiveToolRulesPrompt(String language) {
        return "en".equals(language)
            ? PROGRESSIVE_TOOL_RULES_HEADER_EN + PROGRESSIVE_TOOL_RULES_BODY_EN
            : PROGRESSIVE_TOOL_RULES_HEADER_CN + PROGRESSIVE_TOOL_RULES_BODY_CN;
    }

    public static PromptSection buildNavigationSection(Iterable<String> entries, String language) {
        String resolvedLanguage = "en".equals(language) ? "en" : "cn";
        return new PromptSection(
            SectionName.TOOL_NAVIGATION,
            Map.of(resolvedLanguage, buildNavigationPrompt(entries, resolvedLanguage)),
            70
        );
    }

    public static PromptSection buildProgressiveToolRulesSection(String language) {
        String resolvedLanguage = "en".equals(language) ? "en" : "cn";
        return new PromptSection(
            SectionName.PROGRESSIVE_TOOL_RULES,
            Map.of(resolvedLanguage, buildProgressiveToolRulesPrompt(resolvedLanguage)),
            75
        );
    }

    public static String buildNavigationEntry(String name, String group, String status, String summary, String language) {
        if ("en".equals(language)) {
            return "- " + name + " [" + group + ", " + status + "]: " + summary;
        }
        return "- " + name + " [" + group + ", " + status + "]锛歿summary}";
    }

    public static PromptSection buildMultilingualNavigationSection(Iterable<String> entriesCn, Iterable<String> entriesEn) {
        Map<String, String> content = new LinkedHashMap<>();
        content.put("cn", buildNavigationPrompt(entriesCn, "cn"));
        content.put("en", buildNavigationPrompt(entriesEn, "en"));
        return new PromptSection(SectionName.TOOL_NAVIGATION, content, 70);
    }

    public static PromptSection buildMultilingualProgressiveToolRulesSection() {
        Map<String, String> content = new LinkedHashMap<>();
        content.put("cn", buildProgressiveToolRulesPrompt("cn"));
        content.put("en", buildProgressiveToolRulesPrompt("en"));
        return new PromptSection(SectionName.PROGRESSIVE_TOOL_RULES, content, 75);
    }

    public static PromptSection build() {
        return buildProgressiveToolRulesSection("cn");
    }
}
