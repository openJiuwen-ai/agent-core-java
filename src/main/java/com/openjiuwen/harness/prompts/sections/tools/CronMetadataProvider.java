/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Cron tool metadata provider.
 *
 * <p>This keeps the action interface and core structured fields aligned with Python. Deep nested schedule,
 * payload, and delivery descriptions are tracked in the migration report for continued expansion.
 *
 * @since 0.1.12
 */
public final class CronMetadataProvider implements ToolMetadataProvider {
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return "cron";
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription(String language) {
        return ToolSchemaSupport.localized(language,
                "使用 action 接口：status、list、add、update、remove、run、runs、wake，并兼容结构化 "
                        + "schedule/payload/delivery 字段。",
                "Use the cron action interface. Supports status, list, add, update, remove, run, runs, and wake "
                        + "using structured schedule/payload/delivery fields.");
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getInputParams(String language) {
        return ToolSchemaSupport.objectSchema(
                ToolSchemaSupport.properties(new Object[] {
                        "action", ToolSchemaSupport.enumProperty("string",
                                List.of("status", "list", "add", "update", "remove", "run", "runs", "wake"),
                                text(language, "要执行的 cron 操作", "Cron action to execute")),
                        "job", ToolSchemaSupport.property("object", text(language,
                                "用于 add 的任务对象；支持结构化字段和兼容层字段",
                                "Job object for add; supports structured fields and compatibility fields")),
                        "jobId", ToolSchemaSupport.property("string", text(language,
                                "用于 update/remove/run/runs 的任务 ID",
                                "Job id used by update/remove/run/runs")),
                        "patch", ToolSchemaSupport.property("object", text(language,
                                "用于 update 的补丁对象", "Patch object used by update")),
                        "includeDisabled", ToolSchemaSupport.property("boolean", text(language,
                                "list 时是否包含已禁用任务", "Whether list should include disabled jobs")),
                        "text", ToolSchemaSupport.property("string", text(language,
                                "wake 动作要发送的提示文本", "Wake text to inject for action=wake")),
                        "mode", ToolSchemaSupport.property("string", text(language,
                                "wake 的触发模式", "Wake delivery mode")),
                        "contextMessages", ToolSchemaSupport.property("array", text(language,
                                "保留给上下文提示的兼容字段",
                                "Reserved compatibility field for context hints"))
                }),
                List.of("action")
        );
    }

    private String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
