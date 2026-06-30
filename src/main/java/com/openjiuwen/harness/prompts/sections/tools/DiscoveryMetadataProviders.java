/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Tool-discovery and skill metadata providers.
 *
 * @since 0.1.12
 */
final class DiscoveryMetadataProviders {
    private DiscoveryMetadataProviders() {
    }

    static final class ListSkillMetadataProvider implements ToolMetadataProvider {
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getName() {
            return "list_skill";
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getDescription(String language) {
            return text(language,
                    "列出可用技能或为当前任务选择相关技能。",
                    "List available skills or select relevant skills for the current task.");
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Object> getInputParams(String language) {
            return ToolSchemaSupport.objectSchema(
                    ToolSchemaSupport.properties(new Object[] {
                            "query", ToolSchemaSupport.property("string", text(language,
                                    "可选。当前用户任务。为空时返回所有可用技能。",
                                    "Optional. Current user task. If empty, return all available skills."))
                    }),
                    List.of()
            );
        }
    }

    static final class LoadToolsMetadataProvider implements ToolMetadataProvider {
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getName() {
            return "load_tools";
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getDescription(String language) {
            return text(language,
                    "将选定的真实工具加载到当前 session 可见工具集合中。",
                    "Load selected real tools into the current session-visible tool set.");
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Object> getInputParams(String language) {
            return ToolSchemaSupport.objectSchema(
                    ToolSchemaSupport.properties(new Object[] {
                            "tool_names", Map.of(
                                    "type", "array",
                                    "items", Map.of("type", "string"),
                                    "description", text(language,
                                            "要在当前 session 中可见的工具名称列表",
                                            "Names of tools to make visible for the current session")),
                            "replace", ToolSchemaSupport.property("boolean", text(language,
                                    "如果为 true，替换当前可见工具集，否则合并",
                                    "If true, replace the current visible tool set instead of merging"))
                    }),
                    List.of("tool_names")
            );
        }
    }

    static final class SearchToolsMetadataProvider implements ToolMetadataProvider {
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getName() {
            return "search_tools";
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getDescription(String language) {
            return text(language,
                    "根据能力、名称、描述或参数提示搜索候选工具。仅用于发现，不会直接调用工具。",
                    "Search candidate tools by capability, name, description, or parameter hints. Discovery only; "
                            + "tools are not directly callable.");
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Object> getInputParams(String language) {
            return ToolSchemaSupport.objectSchema(
                    ToolSchemaSupport.properties(new Object[] {
                            "query", ToolSchemaSupport.property("string", text(language,
                                    "搜索候选工具的查询文本",
                                    "Search query for finding relevant candidate tools")),
                            "limit", ToolSchemaSupport.property("integer", text(language,
                                    "返回候选工具的最大数量",
                                    "Maximum number of candidate tools to return")),
                            "detail_level", ToolSchemaSupport.property("integer", text(language,
                                    "1=name+描述, 2=+参数摘要, 3=+完整参数",
                                    "1=name+description, 2=+parameter summary, 3=+full parameters"))
                    }),
                    List.of("query")
            );
        }
    }

    static final class SkillToolMetadataProvider implements ToolMetadataProvider {
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getName() {
            return "skill_tool";
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getDescription(String language) {
            return text(language, "使用此工具查看特定技能的内容",
                    "Use this tool to view the skill contents of a certain skill");
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Object> getInputParams(String language) {
            return ToolSchemaSupport.objectSchema(
                    ToolSchemaSupport.properties(new Object[] {
                            "skill_name", ToolSchemaSupport.property("string", text(language,
                                    "技能的名称", "Name of the skill")),
                            "relative_file_path", ToolSchemaSupport.property("string", text(language,
                                    "可选。查看技能目录中指定路径下的特定文件。留空则查看主 SKILL.md 文件。",
                                    "Optional. View a specific file within the skill directory. Leave blank for "
                                            + "SKILL.md."))
                    }),
                    List.of("skill_name")
            );
        }
    }

    private static String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
