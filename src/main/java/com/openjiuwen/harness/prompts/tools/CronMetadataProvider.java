/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual description and input params for the cron tool.
 * <p>
 * Mirrors Python's {@code CronMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.cron}.
 */
public class CronMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn",
                "使用 action 接口：status、list、add、update、remove、run、runs、wake，并兼容结构化 schedule/payload/delivery 字段。"
                + "处理\"2分钟后\"\"明天上午9点\"\"下周一\"这类时间时，优先根据系统提示中已提供的当前日期与时间直接换算并调用 cron。"
                + "【重要：cron 表达式格式】只支持7段式(Quartz格式)：秒 分 时 日 月 周 年。"
                + "【重要：cron 表达式限制】标准 cron 的 */X 语义是'当字段值能被 X 整除时触发'，而非'每隔 X 单位触发'。"
                + "秒/分(0-59)：*/X 仅支持 X 整除60的值。小时(0-23)：*/X 仅支持 X 整除24的值。");
        DESCRIPTIONS.put("en",
                "Use the cron action interface. Supports status, list, add, update, remove, run, runs, "
                + "and wake using structured schedule/payload/delivery fields. "
                + "For requests like 'in 2 minutes', 'tomorrow at 9am', prefer converting the time directly "
                + "from the current date/time in the system prompt and call cron directly. "
                + "[CRITICAL: Cron Expression Format] Only supports 7-field Quartz format: second minute hour day month dow year. "
                + "[CRITICAL: Cron Expression Limits] Standard cron's */X means 'trigger when field value divisible by X'. "
                + "Second/Minute(0-59): */X only for X dividing 60. Hour(0-23): */X only for X dividing 24.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("action", Map.of("type", "string", "description", "操作类型：status、list、add、update、remove、run、runs、wake"));
        cnProps.put("schedule", Map.of("type", "object", "description", "调度配置（cron 表达式或 ISO8601 时间）"));
        cnProps.put("payload", Map.of("type", "object", "description", "提醒内容配置"));
        cnProps.put("delivery", Map.of("type", "object", "description", "投递配置"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Collections.singletonList("action"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("action", Map.of("type", "string", "description", "Action type: status, list, add, update, remove, run, runs, wake"));
        enProps.put("schedule", Map.of("type", "object", "description", "Schedule configuration (cron expression or ISO8601 time)"));
        enProps.put("payload", Map.of("type", "object", "description", "Reminder content configuration"));
        enProps.put("delivery", Map.of("type", "object", "description", "Delivery configuration"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Collections.singletonList("action"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "cron";
    }

    @Override
    public String getDescription(String language) {
        return DESCRIPTIONS.getOrDefault(language, DESCRIPTIONS.get("cn"));
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return INPUT_PARAMS.getOrDefault(language, INPUT_PARAMS.get("cn"));
    }
}