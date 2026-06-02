/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                "使用 action 接口：status、list、add、update、remove、run、runs、wake，并兼容结构化 "
                        + "schedule/payload/delivery 字段。处理“2分钟后”“明天上午9点”“下周一”这类时间时，"
                        + "优先根据系统提示中已提供的当前日期与时间直接换算并调用 cron。创建一次性提醒时，"
                        + "schedule.at 默认直接使用用户当前本地时区偏移来写，例如 +08:00；除非用户明确要求，"
                        + "不要改写成 Z 或 UTC。给当前聊天创建提醒时，优先使用 payload.kind=systemEvent 和 "
                        + "sessionTarget=current。向用户确认创建结果时，优先按 schedule.at 里的原始时区/偏移表述。"
                        + "\n\n【重要：cron 表达式格式】只支持7段式(Quartz格式)：秒 分 时 日 月 周 年。"
                        + "日和周字段不能同时指定具体值，其中一个必须用 ?。一次性任务建议优先使用 schedule.at。"
                        + "\n\n【重要：cron 表达式限制】标准 cron 的 */X 语义是“当字段值能被 X 整除时触发”，"
                        + "而非“每隔 X 单位触发”。未确认限制前不要直接创建不均匀间隔任务。");
        DESCRIPTIONS.put("en",
                "Use the cron action interface. Supports status, list, add, update, remove, run, runs, "
                        + "and wake using structured schedule/payload/delivery fields. "
                        + "For requests like 'in 2 minutes', 'tomorrow at 9am', or 'next Monday', "
                        + "prefer converting the time directly from the current date/time already provided in the system prompt "
                        + "and call cron directly instead of using code or bash for simple time math. "
                        + "When creating one-shot reminders, write schedule.at using the user's current local timezone offset "
                        + "directly, for example +08:00; unless the user explicitly asks for it, do not rewrite it into Z or UTC. "
                        + "For reminders targeting the current chat, prefer payload.kind=systemEvent with sessionTarget=current. "
                        + "When confirming a created reminder to the user, prefer the original timezone/offset from schedule.at "
                        + "instead of rewriting it into UTC."
                        + "\n\n[CRITICAL: Cron Expression Format] Only supports 7-field Quartz format: "
                        + "second minute hour day month dow year. Day and dow fields cannot both have specific values; "
                        + "one must be '?'. Prefer schedule.at for one-shot tasks."
                        + "\n\n[CRITICAL: Cron Expression Limits] Standard cron's */X means 'trigger when the field value "
                        + "is divisible by X', NOT 'every X units'. Do not create uneven intervals without confirmation.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        INPUT_PARAMS.put("cn", buildCronInputParams("cn"));
        INPUT_PARAMS.put("en", buildCronInputParams("en"));
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

    private static Map<String, Object> buildCronInputParams(String language) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", Collections.singletonList("action"));
        schema.put("additionalProperties", true);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("action", Map.of(
                "type", "string",
                "enum", List.of("status", "list", "add", "update", "remove", "run", "runs", "wake"),
                "description", desc(language, "Cron action to execute", "要执行的 cron 操作")));
        props.put("job", jobSchema(language, desc(language,
                "Job object for add; supports structured fields and compatibility fields",
                "用于 add 的任务对象，支持结构化字段和兼容字段")));
        props.put("jobId", Map.of(
                "type", "string",
                "description", desc(language, "Job id used by update/remove/run/runs",
                        "用于 update/remove/run/runs 的任务 ID")));
        props.put("patch", jobSchema(language, desc(language,
                "Patch object used by update", "用于 update 的补丁对象")));
        props.put("includeDisabled", Map.of(
                "type", "boolean",
                "description", desc(language, "Whether list should include disabled jobs",
                        "list 时是否包含已禁用任务")));
        props.put("text", Map.of(
                "type", "string",
                "description", desc(language, "Wake text to inject for action=wake",
                        "wake 动作要发送的提示文本")));
        props.put("mode", Map.of(
                "type", "string",
                "enum", List.of("now", "next-heartbeat"),
                "description", desc(language, "Wake delivery mode", "wake 的触发模式")));
        props.put("contextMessages", Map.of(
                "type", "integer",
                "description", desc(language, "Reserved compatibility field for context hints",
                        "保留给上下文提示的兼容字段")));

        schema.put("properties", props);
        return schema;
    }

    private static Map<String, Object> jobSchema(String language, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("description", description);
        schema.put("required", List.of());
        schema.put("additionalProperties", true);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("name", Map.of("type", "string",
                "description", desc(language, "Job name", "任务名称")));
        props.put("enabled", Map.of("type", "boolean",
                "description", desc(language, "Whether the job is enabled", "任务是否启用")));
        props.put("schedule", scheduleSchema(language));
        props.put("payload", Map.of("type", "object",
                "description", desc(language, "Structured job payload supporting systemEvent or agentTurn",
                        "结构化任务负载，支持 systemEvent 或 agentTurn"),
                "additionalProperties", true));
        props.put("delivery", Map.of("type", "object",
                "description", desc(language, "How reminder output should be delivered",
                        "提醒结果的投递方式"),
                "additionalProperties", true));
        props.put("sessionTarget", Map.of("type", "string",
                "description", desc(language, "Session target: main, isolated, current, or session:<id>",
                        "会话目标：main、isolated、current 或 session:<id>")));
        props.put("wakeMode", Map.of("type", "string",
                "enum", List.of("now", "next-heartbeat"),
                "description", desc(language, "Wake mode: now or next-heartbeat",
                        "唤醒模式：now 或 next-heartbeat")));
        props.put("deleteAfterRun", Map.of("type", "boolean",
                "description", desc(language, "Whether the job should be deleted after it runs",
                        "执行后是否自动删除该任务")));
        props.put("cron_expr", Map.of("type", "string",
                "description", desc(language, "Compatibility cron expression (Quartz format)",
                        "兼容层 cron 表达式（Quartz 格式）")));
        props.put("timezone", Map.of("type", "string",
                "description", desc(language, "Compatibility timezone field", "兼容层时区字段")));
        props.put("wake_offset_seconds", Map.of("type", "integer",
                "description", desc(language, "Compatibility wake offset in seconds",
                        "兼容层提前唤醒秒数")));
        props.put("description", Map.of("type", "string",
                "description", desc(language, "Task content sent to assistant at scheduled time",
                        "到点执行时发给助手的任务内容")));
        props.put("targets", Map.of("type", "string",
                "description", desc(language, "Legacy compatibility target channel",
                        "兼容层目标频道字段")));

        schema.put("properties", props);
        return schema;
    }

    private static Map<String, Object> scheduleSchema(String language) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("description", desc(language,
                "Structured schedule definition supporting at/every/cron",
                "结构化调度定义，支持 at/every/cron"));
        schema.put("required", List.of());
        schema.put("additionalProperties", true);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("kind", Map.of(
                "type", "string",
                "enum", List.of("at", "every", "cron"),
                "description", desc(language, "Schedule type: at, every, or cron",
                        "调度类型：at、every 或 cron")));
        props.put("at", Map.of(
                "type", "string",
                "description", desc(language, "One-shot execution time in ISO 8601",
                        "一次性执行时间，ISO 8601")));
        props.put("everyMs", Map.of(
                "type", "integer",
                "description", desc(language, "Recurring interval in milliseconds",
                        "循环间隔，毫秒")));
        props.put("expr", Map.of(
                "type", "string",
                "description", desc(language, "Cron expression (Quartz format)",
                        "cron 表达式（Quartz 格式）")));
        props.put("tz", Map.of(
                "type", "string",
                "description", desc(language, "Timezone used by cron schedules",
                        "cron 调度使用的时区")));
        schema.put("properties", props);
        return schema;
    }

    private static String desc(String language, String en, String cn) {
        return "en".equals(language) ? en : cn;
    }
}
