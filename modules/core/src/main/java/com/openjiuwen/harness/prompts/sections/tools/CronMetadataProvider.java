/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Cron tool metadata provider.
 * <p>
 * This keeps the action interface and core structured fields aligned with Python. Deep nested schedule,
 * payload, and delivery descriptions are tracked in the migration report for continued expansion.
 * 
 * @since 0.1.7
 */
public final class CronMetadataProvider implements ToolMetadataProvider {
    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "cron";
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
        return ToolSchemaSupport.localized(language,
                "使用 action 接口：status、list、add、update、remove、run、runs、wake，并兼容结构化 " + "schedule/payload/delivery 字段。",
                "Use the cron action interface. Supports status, list, add, update, remove, run, runs, and wake "
                        + "using structured schedule/payload/delivery fields.");
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
                /**
                 * ToolSchemaSupport.properties.
                 * 
                 * @param Object[]{"action" Object[]{"action"
                 * @param "wake" "wake"
                 * @since 0.1.7
                 */
                ToolSchemaSupport.properties(new Object[]{"action",
                        /**
                         * ToolSchemaSupport.enumProperty.
                         * 
                         * @param "wake" "wake"
                         * @since 0.1.7
                         */
                        ToolSchemaSupport.enumProperty(
                                /**
                                 * List.of.
                                 * 
                                 * @since 0.1.7
                                 */
                                "string", List.of("status", "list", "add", "update", "remove", "run", "runs", "wake"),
                                /**
                                 * text.
                                 * 
                                 * @param 操作" 操作"
                                 * @param execute" execute"
                                 * @since 0.1.7
                                 */
                                text(language, "要执行的 cron 操作", "Cron action to execute")),
                        "job",
                        /**
                         * ToolSchemaSupport.property.
                         * 
                         * @param fields" fields"
                         * @since 0.1.7
                         */
                        ToolSchemaSupport.property("object",
                                /**
                                 * text.
                                 * 
                                 * @param 的任务对象；支持结构化字段和兼容层字段" 的任务对象；支持结构化字段和兼容层字段"
                                 * @param fields" fields"
                                 * @since 0.1.7
                                 */
                                text(language, "用于 add 的任务对象；支持结构化字段和兼容层字段",
                                        "Job object for add; supports structured fields and compatibility fields")),
                        "jobId",
                        /**
                         * ToolSchemaSupport.property.
                         * 
                         * @param update/remove/run/runs" update/remove/run/runs"
                         * @since 0.1.7
                         */
                        ToolSchemaSupport.property("string",
                                /**
                                 * text.
                                 * 
                                 * @param ID" ID"
                                 * @param update/remove/run/runs" update/remove/run/runs"
                                 * @since 0.1.7
                                 */
                                text(language, "用于 update/remove/run/runs 的任务 ID",
                                        "Job id used by update/remove/run/runs")),
                        "patch", ToolSchemaSupport
                                /**
                                 * .property.
                                 * 
                                 * @param update" update"
                                 * @since 0.1.7
                                 */
                                .property("object", text(language, "用于 update 的补丁对象", "Patch object used by update")),
                        "includeDisabled",
                        /**
                         * ToolSchemaSupport.property.
                         * 
                         * @param jobs" jobs"
                         * @since 0.1.7
                         */
                        ToolSchemaSupport.property("boolean",
                                /**
                                 * text.
                                 * 
                                 * @param 时是否包含已禁用任务" 时是否包含已禁用任务"
                                 * @param jobs" jobs"
                                 * @since 0.1.7
                                 */
                                text(language, "list 时是否包含已禁用任务", "Whether list should include disabled jobs")),
                        "text",
                        /**
                         * ToolSchemaSupport.property.
                         * 
                         * @param action=wake" action=wake"
                         * @since 0.1.7
                         */
                        ToolSchemaSupport.property("string",
                                /**
                                 * text.
                                 * 
                                 * @param 动作要发送的提示文本" 动作要发送的提示文本"
                                 * @param action=wake" action=wake"
                                 * @since 0.1.7
                                 */
                                text(language, "wake 动作要发送的提示文本", "Wake text to inject for action=wake")),
                        "mode",
                        /**
                         * ToolSchemaSupport.property.
                         * 
                         * @param mode" mode"
                         * @since 0.1.7
                         */
                        ToolSchemaSupport.property("string", text(language, "wake 的触发模式", "Wake delivery mode")),
                        "contextMessages",
                        /**
                         * ToolSchemaSupport.property.
                         * 
                         * @param hints" hints"
                         * @since 0.1.7
                         */
                        ToolSchemaSupport.property("array",
                                /**
                                 * text.
                                 * 
                                 * @param hints" hints"
                                 * @since 0.1.7
                                 */
                                text(language, "保留给上下文提示的兼容字段", "Reserved compatibility field for context hints"))}),
                /**
                 * List.of.
                 * 
                 * @since 0.1.7
                 */
                List.of("action"));
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
    private String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
