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
## 工具导航
以下条目用于帮助你理解当前 session 下的工具生态。
请注意：这里展示的是“工具地图”，不是“全部可立即调用的工具清单”。
只有在当前 session 中显式调用 `load_tools` 后，目标工具才会进入可调用状态。
""";

    private static final String PROGRESSIVE_TOOL_NAVIGATION_HEADER_EN = """
## Tool Navigation
The entries below help you understand the tool ecosystem available in the current session.
Treat this section as a tool map, not as a full list of immediately callable tools.
A tool becomes callable only after `load_tools` has been explicitly called for it in the current session.
""";

    private static final String PROGRESSIVE_TOOL_NAVIGATION_EMPTY_CN = "- （当前无可以展示的导航条目）";
    private static final String PROGRESSIVE_TOOL_NAVIGATION_EMPTY_EN = "- (no navigation entries available)";

    private static final String PROGRESSIVE_TOOL_RULES_HEADER_CN = "## 渐进式工具使用规则\n";
    private static final String PROGRESSIVE_TOOL_RULES_HEADER_EN = "## Progressive Tool Usage Rules\n";

    private static final String PROGRESSIVE_TOOL_RULES_BODY_CN = """
你正在一个渐进式工具环境中工作。
请严格遵循以下规则：
1. 当你不确定该使用哪个工具时，先调用 `search_tools` 查找候选工具。
2. 如需查看更多细节，可直接提高 `search_tools` 的 `detail_level`（2=参数摘要，3=完整参数）。
3. 在导航区或搜索结果中看到某个工具，并不意味着它已经可调用。
4. 真实工具只有在当前 session 中显式调用 `load_tools` 后才可调用。
5. 一旦你已经通过 `search_tools` 找到要使用的目标工具，下一步应立即调用 `load_tools`，而不是继续只用文字描述计划。
6. 在所需工具尚未加载前，不要声称你将要检查文件、读取目录、解析文档、生成表格或执行任何依赖这些工具的动作；应先加载工具，再执行。
7. 如果任务涉及文件检查、PDF 处理、XLSX 生成、目录浏览或数据处理，你应尽快从搜索结果中选择合适工具并调用 `load_tools`，随后立刻使用真实工具执行。
8. 不要停留在“下一步我将……”这类自然语言计划上；若已有足够信息选择工具，就直接进入 `load_tools` 和真实工具调用。
9. 工作顺序应尽量保持为：先导航，再搜索，必要时看更详细结果，再加载，最后执行。
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
        return "- " + name + " [" + group + ", " + status + "]：" + summary;
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
