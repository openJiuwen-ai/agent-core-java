/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Tool-discovery and skill metadata providers.
 *
 * @since 0.1.7
 */
final class DiscoveryMetadataProviders {
    /**
     * DiscoveryMetadataProviders.
     * 
     * @since 0.1.7
     */
    private DiscoveryMetadataProviders() {
    }

    static final class ListSkillMetadataProvider implements ToolMetadataProvider {
        private static final String DESCRIPTION_CN = "列出可用技能或为当前任务选择相关技能。";
        private static final String DESCRIPTION_EN =
                "List available skills or select relevant skills for the current task.";
        private static final String QUERY_CN = "可选。当前用户任务。为空或无匹配时返回所有可用技能。";
        private static final String QUERY_EN =
                "Optional. Current user task. If empty or unmatched, return all available skills.";

        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "list_skill";
        }

        /**
         * getDescription.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getDescription(String language) {
            return text(language, DESCRIPTION_CN, DESCRIPTION_EN);
        }

        /**
         * getInputParams.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> getInputParams(String language) {
            return ToolSchemaSupport
                    .objectSchema(
                            ToolSchemaSupport.properties(new Object[] {"query",
                                    ToolSchemaSupport.property("string", text(language, QUERY_CN, QUERY_EN))}),
                            List.of());
        }
    }

    static final class LoadToolsMetadataProvider implements ToolMetadataProvider {
        private static final String DESCRIPTION_CN = "将选定的真实工具加载到当前 session 可见工具集合中。";
        private static final String DESCRIPTION_EN =
                "Load selected real tools into the current session-visible tool set.";
        private static final String TOOL_NAMES_CN = "要在当前 session 中可见的工具名称列表";
        private static final String TOOL_NAMES_EN = "Names of tools to make visible for the current session";
        private static final String REPLACE_CN = "如果为 true，替换当前可见工具集，否则合并";
        private static final String REPLACE_EN = "If true, replace the current visible tool set instead of merging";

        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "load_tools";
        }

        /**
         * getDescription.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getDescription(String language) {
            return text(language, DESCRIPTION_CN, DESCRIPTION_EN);
        }

        /**
         * getInputParams.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> getInputParams(String language) {
            return ToolSchemaSupport
                    .objectSchema(
                            ToolSchemaSupport.properties(new Object[] {"tool_names",
                                    Map.of("type", "array", "items", Map.of("type", "string"),
                                            "description", text(language, TOOL_NAMES_CN, TOOL_NAMES_EN)),
                                    "replace",
                                    ToolSchemaSupport.property("boolean",
                                            text(language, REPLACE_CN, REPLACE_EN))}),
                            List.of("tool_names"));
        }
    }

    static final class SearchToolsMetadataProvider implements ToolMetadataProvider {
        private static final String DESCRIPTION_CN = "根据能力、名称、描述或参数提示搜索候选工具。仅用于发现，不会直接调用工具。";
        private static final String DESCRIPTION_EN =
                "Search candidate tools by capability, name, description, or parameter hints. Discovery only; "
                        + "tools are not directly callable.";
        private static final String QUERY_CN = "搜索候选工具的查询文本";
        private static final String QUERY_EN = "Search query for finding relevant candidate tools";
        private static final String LIMIT_CN = "返回候选工具的最大数量";
        private static final String LIMIT_EN = "Maximum number of candidate tools to return";
        private static final String DETAIL_LEVEL_CN = "1=name+描述, 2=+参数摘要, 3=+完整参数";
        private static final String DETAIL_LEVEL_EN = "1=name+description, 2=+parameter summary, 3=+full parameters";

        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "search_tools";
        }

        /**
         * getDescription.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getDescription(String language) {
            return text(language, DESCRIPTION_CN, DESCRIPTION_EN);
        }

        /**
         * getInputParams.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> getInputParams(String language) {
            return ToolSchemaSupport.objectSchema(
                    ToolSchemaSupport.properties(new Object[] {"query",
                            ToolSchemaSupport.property("string", text(language, QUERY_CN, QUERY_EN)),
                            "limit",
                            ToolSchemaSupport.property("integer", text(language, LIMIT_CN, LIMIT_EN)),
                            "detail_level",
                            ToolSchemaSupport.property("integer",
                                    text(language, DETAIL_LEVEL_CN, DETAIL_LEVEL_EN))}),
                    List.of("query"));
        }
    }

    static final class SkillToolMetadataProvider implements ToolMetadataProvider {
        private static final String DESCRIPTION_CN = "使用此工具查看特定技能的内容";
        private static final String DESCRIPTION_EN = "Use this tool to view the skill contents of a certain skill";
        private static final String SKILL_NAME_CN = "技能的名称";
        private static final String SKILL_NAME_EN = "Name of the skill";
        private static final String RELATIVE_FILE_PATH_CN = "可选。查看技能目录中指定路径下的特定文件。留空则查看主SKILL.md文件。";
        private static final String RELATIVE_FILE_PATH_EN =
                "Optional. View a specific file within the skill directory. Leave blank for SKILL.md.";

        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "skill_tool";
        }

        /**
         * getDescription.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getDescription(String language) {
            return text(language, DESCRIPTION_CN, DESCRIPTION_EN);
        }

        /**
         * getInputParams.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> getInputParams(String language) {
            return ToolSchemaSupport.objectSchema(ToolSchemaSupport.properties(new Object[] {"skill_name",
                    ToolSchemaSupport.property("string", text(language, SKILL_NAME_CN, SKILL_NAME_EN)),
                    "relative_file_path",
                    ToolSchemaSupport.property("string",
                            text(language, RELATIVE_FILE_PATH_CN, RELATIVE_FILE_PATH_EN))}),
                    List.of("skill_name"));
        }
    }

    /**
     * text.
     * 
     * @param language language
     * @param cn cn
     * @param en en
     * @return the result
     * @since 0.1.7
     */
    private static String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
