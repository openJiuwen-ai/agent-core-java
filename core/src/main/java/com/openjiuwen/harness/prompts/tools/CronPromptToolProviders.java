/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.harness.prompts.tools.cron} in
 * {@code openjiuwen/harness/prompts/tools/cron.py}.
 */
public final class CronPromptToolProviders {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private CronPromptToolProviders() {
    }

    private static Map<String, Object> parseSchema(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse embedded prompt-tool schema JSON", ex);
        }
    }

    private static String resolve(String chinese, String english, String language) {
        return "en".equals(language) ? english : chinese;
    }

    private static final String CRONCREATEJOBMETADATAPROVIDER_DESCRIPTION_CN = "\n创建新的 cron 定时任务，使用扁平字段（name, cron_expr, timezone, targets, description, wake_offset_seconds）。\n\n【targets】用户未明确指定投递渠道时不填，系统自动使用当前对话渠道；**禁止从历史记录推断。**\n\n【重要：cron 表达式格式】只支持7段式(Quartz格式)：秒 分 时 日 月 周 年。\n日和周字段：不能同时指定具体值，其中一个必须用?表示'不指定'。\n年份字段：*表示跨年周期执行，固定年份只在该年执行。\n真正只执行一次：所有字段均为固定值（无*和?），如'0 0 17 28 3 ? 2026'。\n例：每天9点 -> '0 0 9 * * ? *'；每15分钟 -> '0 */15 * * * ? *'；每周一9点 -> '0 0 9 ? * MON *'。\n\n【重要：cron 表达式限制】标准 cron 的 */X 语义是'当字段值能被 X 整除时触发'，而非'每隔 X 单位触发'。\n只有当周期单位能被 X 整除时，间隔才是均匀的。以下是各字段的限制：\n- 秒/分(0-59)：*/X 仅支持 X 整除60的值：1/2/3/4/5/6/10/12/15/20/30。\n  例如 */40 实际在每小时第0分和第40分触发（间隔40分→20分交替），并非每40分钟。\n  用户要求'每隔40分钟'时，必须先告知此限制并让用户确认是否接受不均匀间隔，\n  或建议改用整除60的间隔（如20分钟或30分钟）。未经用户确认不得直接创建。\n- 小时(0-23)：*/X 仅支持 X 整除24的值：1/2/3/4/6/8/12。\n  例如 */5 实际在每天0/5/10/15/20时触发（间隔5h→4h→5h→4h交替），并非每5小时。\n- 日(1-31)：*/X 不可靠，因为不同月份天数不同（28/29/30/31）。\n- 月(1-12)：*/X 仅支持 X 整除12的值：1/2/3/4/6。\n- 周(1-7)：*/X 仅支持 X 整除7的值：1/7。1=SUN,7=SAT。\n\n处理'每隔X分钟/小时/天'需求时，务必检查 X 是否整除对应周期单位；\n若不整除，必须告知用户限制，让用户确认后再创建，或建议替代方案。\n";
    private static final String CRONCREATEJOBMETADATAPROVIDER_DESCRIPTION_EN = "\nCreate a new cron job using flat fields (name, cron_expr, timezone, targets, description, wake_offset_seconds).\n\n[targets] Leave empty unless user explicitly specifies a channel; system uses current channel. **Never infer from history.**\n\n[CRITICAL: Cron Expression Format] Only supports 7-field Quartz format: second minute hour day month dow year.\nDay and dow fields: cannot both have specific values; one must be '?' (no specific value).\nYear field: '*' for recurring, fixed year for one-shot within that year.\nTrue one-shot: all fields fixed (no '*' or '?'), e.g. '0 0 17 28 3 ? 2026'.\nExamples: daily 9am -> '0 0 9 * * ? *'; every 15min -> '0 */15 * * * ? *'; every Monday 9am -> '0 0 9 ? * MON *'.\n\n[CRITICAL: Cron Expression Limits] Standard cron's */X means 'trigger when the field value is divisible by X',\nNOT 'every X units'. Uniform intervals only work when the cycle unit is divisible by X. Field limits:\n- Second/Minute(0-59): */X only works for X dividing 60: 1/2/3/4/5/6/10/12/15/20/30.\n  Example: */40 triggers at minute 0 and 40 each hour (alternating 40min-20min gaps), NOT every 40 minutes.\n  When user requests 'every 40 minutes', MUST inform user of this limitation first\n  and let user confirm whether to accept uneven intervals, or suggest intervals that divide 60.\n  Do NOT create without user confirmation.\n- Hour(0-23): */X only works for X dividing 24: 1/2/3/4/6/8/12.\n  Example: */5 triggers at hours 0/5/10/15/20 (alternating 5h-4h gaps), NOT every 5 hours.\n- Day(1-31): */X is unreliable due to varying month lengths (28/29/30/31 days).\n- Month(1-12): */X only works for X dividing 12: 1/2/3/4/6.\n- Dow(1-7): */X only works for X dividing 7: 1/7. 1=SUN, 7=SAT.\n\nWhen handling 'every X minutes/hours/days' requests, always check if X divides the cycle unit.\nIf not, MUST inform user and let user confirm before creating.\n";
    private static final String CRONCREATEJOBMETADATAPROVIDER_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"任务名称\"},\"cron_expr\":{\"type\":\"string\",\"description\":\"Cron表达式(Quartz格式)。7段式：秒 分 时 日 月 周 年。日和周：不能同时指定具体值，其中一个用?。年份：*跨年周期，固定年份只在该年执行。一次性：所有字段固定值，如'0 0 17 28 3 ? 2026'。例：每天9点'0 0 9 * * ? *'；每15分钟'0 */15 * * * ? *'。详见工具描述。\"},\"timezone\":{\"type\":\"string\",\"description\":\"时区，如 Asia/Shanghai\",\"default\":\"Asia/Shanghai\"},\"targets\":{\"type\":\"string\",\"description\":\"目标频道。用户未明确指定时不填，系统自动使用当前对话渠道；**禁止从历史记录推断。**\"},\"enabled\":{\"type\":\"boolean\",\"description\":\"是否启用\",\"default\":true},\"description\":{\"type\":\"string\",\"description\":\"具体任务内容，到点执行时发给助手。不要包含时间/频率信息（如'每隔40分钟'、'每天9点'）\"},\"wake_offset_seconds\":{\"type\":\"integer\",\"description\":\"提前多少秒执行，默认 300。若用户未指定，则默认使用 300 秒。\",\"default\":300}},\"required\":[\"name\",\"cron_expr\",\"timezone\",\"description\"]}";
    private static final String CRONCREATEJOBMETADATAPROVIDER_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"Job name\"},\"cron_expr\":{\"type\":\"string\",\"description\":\"Cron expression (Quartz format). 7-field: second minute hour day month dow year. Day/dow: cannot both be specific; use '?' for one. Year: '*' for recurring, fixed year limits to that year. One-shot: all fields fixed, e.g. '0 0 17 28 3 ? 2026'. Examples: daily 9am '0 0 9 * * ? *'; every 15min '0 */15 * * * ? *'. See tool description.\"},\"timezone\":{\"type\":\"string\",\"description\":\"Timezone, e.g. Asia/Shanghai\",\"default\":\"Asia/Shanghai\"},\"targets\":{\"type\":\"string\",\"description\":\"Target channel. Leave empty unless user explicitly specifies; system uses current channel. **Never infer from history.**\"},\"enabled\":{\"type\":\"boolean\",\"description\":\"Whether to enable the job\",\"default\":true},\"description\":{\"type\":\"string\",\"description\":\"Task content sent to assistant at scheduled time. Do NOT include time/frequency info\"},\"wake_offset_seconds\":{\"type\":\"integer\",\"description\":\"Wake offset in seconds, default 300. If user does not specify, use 300 seconds by default.\",\"default\":300}},\"required\":[\"name\",\"cron_expr\",\"timezone\",\"description\"]}";

    /**
     * Mirrors Python's {@code CronCreateJobMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/cron.py}.
     */
    public static final class CronCreateJobMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "cron_create_job";
        }

        @Override
        public String getDescription(String language) {
            return resolve(CRONCREATEJOBMETADATAPROVIDER_DESCRIPTION_CN, CRONCREATEJOBMETADATAPROVIDER_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(CRONCREATEJOBMETADATAPROVIDER_SCHEMA_CN, CRONCREATEJOBMETADATAPROVIDER_SCHEMA_EN, language));
        }
    }

    private static final String CRONDELETEJOBMETADATAPROVIDER_DESCRIPTION_CN = "根据任务 ID 删除 cron 定时任务。";
    private static final String CRONDELETEJOBMETADATAPROVIDER_DESCRIPTION_EN = "Delete a cron job by its ID.";
    private static final String CRONDELETEJOBMETADATAPROVIDER_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"job_id\":{\"type\":\"string\",\"description\":\"要删除的任务 ID\"}},\"required\":[\"job_id\"]}";
    private static final String CRONDELETEJOBMETADATAPROVIDER_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"job_id\":{\"type\":\"string\",\"description\":\"Job ID to delete\"}},\"required\":[\"job_id\"]}";

    /**
     * Mirrors Python's {@code CronDeleteJobMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/cron.py}.
     */
    public static final class CronDeleteJobMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "cron_delete_job";
        }

        @Override
        public String getDescription(String language) {
            return resolve(CRONDELETEJOBMETADATAPROVIDER_DESCRIPTION_CN, CRONDELETEJOBMETADATAPROVIDER_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(CRONDELETEJOBMETADATAPROVIDER_SCHEMA_CN, CRONDELETEJOBMETADATAPROVIDER_SCHEMA_EN, language));
        }
    }

    private static final String CRONGETJOBMETADATAPROVIDER_DESCRIPTION_CN = "根据任务 ID 获取单个 cron 定时任务的详细信息。";
    private static final String CRONGETJOBMETADATAPROVIDER_DESCRIPTION_EN = "Get a single cron job by its ID.";
    private static final String CRONGETJOBMETADATAPROVIDER_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"job_id\":{\"type\":\"string\",\"description\":\"要查询的任务 ID\"}},\"required\":[\"job_id\"]}";
    private static final String CRONGETJOBMETADATAPROVIDER_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"job_id\":{\"type\":\"string\",\"description\":\"The job ID to look up\"}},\"required\":[\"job_id\"]}";

    /**
     * Mirrors Python's {@code CronGetJobMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/cron.py}.
     */
    public static final class CronGetJobMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "cron_get_job";
        }

        @Override
        public String getDescription(String language) {
            return resolve(CRONGETJOBMETADATAPROVIDER_DESCRIPTION_CN, CRONGETJOBMETADATAPROVIDER_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(CRONGETJOBMETADATAPROVIDER_SCHEMA_CN, CRONGETJOBMETADATAPROVIDER_SCHEMA_EN, language));
        }
    }

    private static final String CRONLISTJOBSMETADATAPROVIDER_DESCRIPTION_CN = "列出所有 cron 定时任务。";
    private static final String CRONLISTJOBSMETADATAPROVIDER_DESCRIPTION_EN = "List all cron jobs.";
    private static final String CRONLISTJOBSMETADATAPROVIDER_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
    private static final String CRONLISTJOBSMETADATAPROVIDER_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{},\"required\":[]}";

    /**
     * Mirrors Python's {@code CronListJobsMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/cron.py}.
     */
    public static final class CronListJobsMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "cron_list_jobs";
        }

        @Override
        public String getDescription(String language) {
            return resolve(CRONLISTJOBSMETADATAPROVIDER_DESCRIPTION_CN, CRONLISTJOBSMETADATAPROVIDER_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(CRONLISTJOBSMETADATAPROVIDER_SCHEMA_CN, CRONLISTJOBSMETADATAPROVIDER_SCHEMA_EN, language));
        }
    }

    private static final String CRONMETADATAPROVIDER_DESCRIPTION_CN = "使用 action 接口：status、list、add、update、remove、run、runs、wake，并兼容结构化 schedule/payload/delivery 字段。处理“2分钟后”“明天上午9点”“下周一”这类时间时，优先根据系统提示中已提供的当前日期与时间直接换算并调用 cron，不要为了简单的时间换算先调用 code 或 bash。创建一次性提醒时，schedule.at 默认直接使用用户当前本地时区偏移来写，例如 +08:00；除非用户明确要求，否则不要改写成 Z 或 UTC。给当前聊天创建提醒时，优先使用 payload.kind=systemEvent 和 sessionTarget=current。向用户确认创建结果时，优先按 schedule.at 里的原始时区/偏移表述，不要自行改写成 UTC。\n\n【投递频道】delivery.channel / targets：用户未明确指定时不填，系统自动使用当前对话渠道；**禁止从历史记录推断。**\n\n【重要：cron 表达式格式】只支持7段式(Quartz格式)：秒 分 时 日 月 周 年。字段取值范围：秒(0-59)，分(0-59)，时(0-23)，日(1-31)，月(1-12)，周(1-7或?)，年(1970-2099或*)。日和周字段：不能同时指定具体值，其中一个必须用?表示'不指定'。年份字段：*表示跨年周期，固定年份只在该年执行。真正只执行一次：所有字段均为固定值(无*和?)，如'0 30 17 29 4 ? 2026'表示2026年4月29日17:30:00执行一次。例：每天9点 -> '0 0 9 * * ? *'；每15分钟 -> '0 */15 * * * ? *'；每周一9点 -> '0 0 9 ? * MON *'。注意：一次性任务建议优先使用 schedule.at (ISO8601格式)，cron更适合周期性任务。\n\n【重要：cron 表达式限制】标准 cron 的 */X 语义是'当字段值能被 X 整除时触发'，而非'每隔 X 单位触发'。只有当周期单位能被 X 整除时，间隔才是均匀的。以下是各字段的限制：\n- 秒/分(0-59)：*/X 仅支持 X 整除60的值：1/2/3/4/5/6/10/12/15/20/30。  例如 */40 实际在每小时第0分和第40分触发（间隔40分→20分交替），并非每40分钟。  用户要求'每隔40分钟'时，必须先告知此限制并让用户确认是否接受不均匀间隔，  或建议改用整除60的间隔（如20分钟或30分钟）。未经用户确认不得直接创建。\n- 小时(0-23)：*/X 仅支持 X 整除24的值：1/2/3/4/6/8/12。  例如 */5 实际在每天0/5/10/15/20时触发（间隔5h→4h→5h→4h交替），并非每5小时。  用户要求'每隔5小时'时，必须告知限制并让用户确认，或建议改用整除24的间隔。\n- 日(1-31)：*/X 不可靠，因为不同月份天数不同（28/29/30/31）。  例如 */15 在2月只触发1、15日（共2次），在31天月份触发1、16、31日（共3次）。  用户要求'每隔X天'时，建议改用'每周X'或指定固定日期（如每月1号、15号）。\n- 月(1-12)：*/X 仅支持 X 整除12的值：1/2/3/4/6。  例如 */5 实际在1/5/10月触发，并非每5个月均匀触发。\n- 周(1-7)：*/X 仅支持 X 整除7的值：1/7。1=SUN,7=SAT。  例如 */2 实际在SUN/TUE/THU触发，并非'每隔2周'。  用户要求'每隔2周'时，应直接指定具体星期几或建议简化为'每周一'。\n处理'每隔X分钟/小时/天'需求时，务必检查 X 是否整除对应周期单位；若不整除，必须告知用户限制，让用户确认后再创建，或建议替代方案。";
    private static final String CRONMETADATAPROVIDER_DESCRIPTION_EN = "Use the cron action interface. Supports status, list, add, update, remove, run, runs, and wake using structured schedule/payload/delivery fields. For requests like 'in 2 minutes', 'tomorrow at 9am', or 'next Monday', prefer converting the time directly from the current date/time already provided in the system prompt and call cron directly instead of using code or bash for simple time math. When creating one-shot reminders, write schedule.at using the user's current local timezone offset directly, for example +08:00; unless the user explicitly asks for it, do not rewrite it into Z or UTC. For reminders targeting the current chat, prefer payload.kind=systemEvent with sessionTarget=current. When confirming a created reminder to the user, prefer the original timezone/offset from schedule.at instead of rewriting it into UTC.\n\n[Delivery Channel] delivery.channel / targets: leave empty unless user explicitly specifies; system uses current channel. **Never infer from history.**\n\n[CRITICAL: Cron Expression Format] Only supports 7-field Quartz format: second minute hour day month dow year. Field ranges: second(0-59), minute(0-59), hour(0-23), day(1-31), month(1-12), dow(1-7 or ?), year(1970-2099 or *). Day and dow fields: cannot both have specific values; one must be '?' (no specific value). Year field: '*' for recurring, fixed year for one-shot within that year. True one-shot: all fields fixed (no '*' or '?'), e.g. '0 30 17 29 4 ? 2026' runs once at 2026-04-29 17:30:00.Examples: daily 9am -> '0 0 9 * * ? *'; every 15min -> '0 */15 * * * ? *'; every Monday 9am -> '0 0 9 ? * MON *'. Note: for one-shot tasks, prefer schedule.at (ISO8601 format); cron is better for recurring tasks.\n\n[CRITICAL: Cron Expression Limits] Standard cron's */X means 'trigger when the field value is divisible by X', NOT 'every X units'. Uniform intervals only work when the cycle unit is divisible by X. Field limits:\n- Second/Minute(0-59): */X only works for X dividing 60: 1/2/3/4/5/6/10/12/15/20/30.  Example: */40 triggers at minute 0 and 40 each hour (alternating 40min-20min gaps), NOT every 40 minutes.  When user requests 'every 40 minutes', MUST inform user of this limitation first and let user confirm whether to accept uneven intervals, or suggest intervals that divide 60 (e.g. 20 or 30 minutes). Do NOT create without user confirmation.\n- Hour(0-23): */X only works for X dividing 24: 1/2/3/4/6/8/12.  Example: */5 triggers at hours 0/5/10/15/20 (alternating 5h-4h gaps), NOT every 5 hours.  When user requests 'every 5 hours', MUST inform and let user confirm, or suggest 4 or 6 hours.\n- Day(1-31): */X is unreliable due to varying month lengths (28/29/30/31 days).  Example: */15 triggers on day 1,15 in Feb (2 times), but 1,16,31 in 31-day months (3 times).  When user requests 'every X days', suggest using 'every week on day X' or fixed dates.\n- Month(1-12): */X only works for X dividing 12: 1/2/3/4/6.  Example: */5 triggers in Jan/May/Oct, NOT uniformly every 5 months.\n- Dow(1-7): */X only works for X dividing 7: 1/7. 1=SUN, 7=SAT.  Example: */2 triggers on SUN/TUE/THU, NOT 'every 2 weeks'.  When user requests 'every 2 weeks', suggest simplifying to a specific weekday.\nWhen handling 'every X minutes/hours/days' requests, always check if X divides the cycle unit. If not, MUST inform user of the limitation, let user confirm before creating, or suggest alternatives.";
    private static final String CRONMETADATAPROVIDER_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"action\":{\"type\":\"string\",\"enum\":[\"status\",\"list\",\"add\",\"update\",\"remove\",\"run\",\"runs\",\"wake\"],\"description\":\"要执行的 cron 操作\"},\"job\":{\"type\":\"object\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"任务名称\"},\"enabled\":{\"type\":\"boolean\",\"description\":\"任务是否启用\"},\"schedule\":{\"type\":\"object\",\"description\":\"结构化调度定义，支持 at/every/cron\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"kind\":{\"type\":\"string\",\"enum\":[\"at\",\"every\",\"cron\"],\"description\":\"调度类型：at、every 或 cron\"},\"at\":{\"type\":\"string\",\"description\":\"一次性执行时间，ISO 8601\"},\"everyMs\":{\"type\":\"integer\",\"description\":\"循环间隔，毫秒\"},\"anchorMs\":{\"type\":\"integer\",\"description\":\"every 调度的起始锚点毫秒时间戳\"},\"expr\":{\"type\":\"string\",\"description\":\"cron表达式(Quartz格式)。7段式：秒 分 时 日 月 周 年。日和周：不能同时指定具体值，其中一个用?。年份：*跨年周期，固定年份只在该年执行。一次性：所有字段固定值，如'0 0 17 28 3 ? 2026'。例：每天9点'0 0 9 * * ? *'；每15分钟'0 */15 * * * ? *'。详见工具描述。\"},\"tz\":{\"type\":\"string\",\"description\":\"cron 调度使用的时区\"},\"staggerMs\":{\"type\":\"integer\",\"description\":\"cron 调度的可选抖动毫秒数\"}}},\"payload\":{\"type\":\"object\",\"description\":\"结构化任务负载，支持 systemEvent 或 agentTurn\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"kind\":{\"type\":\"string\",\"enum\":[\"systemEvent\",\"agentTurn\"],\"description\":\"负载类型：systemEvent 或 agentTurn\"},\"text\":{\"type\":\"string\",\"description\":\"systemEvent提醒文本。不要包含时间/频率信息（如'每隔40分钟'、'每天9点'）\"},\"message\":{\"type\":\"string\",\"description\":\"agentTurn 发送给代理的消息\"},\"model\":{\"type\":\"string\",\"description\":\"agentTurn 可选模型覆盖\"},\"thinking\":{\"type\":\"string\",\"description\":\"agentTurn 的思考预算或模式\"},\"timeoutSeconds\":{\"type\":\"integer\",\"description\":\"agentTurn 超时时间（秒）\"},\"allowUnsafeExternalContent\":{\"type\":\"boolean\",\"description\":\"是否允许不安全的外部内容\"},\"lightContext\":{\"type\":\"boolean\",\"description\":\"是否使用轻量上下文执行\"},\"deliver\":{\"type\":\"string\",\"description\":\"agentTurn 自带的投递策略字段\"},\"channel\":{\"type\":\"string\",\"description\":\"agentTurn 的默认投递频道\"},\"to\":{\"type\":\"string\",\"description\":\"agentTurn 的目标收件人\"},\"bestEffortDeliver\":{\"type\":\"boolean\",\"description\":\"是否最佳努力投递\"},\"fallbacks\":{\"type\":\"array\",\"description\":\"agentTurn 的回退投递列表\",\"items\":{\"type\":\"string\",\"description\":\"agentTurn 的回退投递列表\"}}}},\"delivery\":{\"type\":\"object\",\"description\":\"提醒结果的投递方式\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"mode\":{\"type\":\"string\",\"enum\":[\"none\",\"announce\",\"webhook\"],\"description\":\"投递模式：none、announce 或 webhook\"},\"channel\":{\"type\":\"string\",\"description\":\"announce 模式投递频道。用户未明确指定时不填，系统自动使用当前对话渠道；**禁止从历史记录推断。**\"},\"to\":{\"type\":\"string\",\"description\":\"目标收件人或会话标识\"},\"accountId\":{\"type\":\"string\",\"description\":\"投递账号标识\"},\"bestEffort\":{\"description\":\"announce/webhook 是否最佳努力投递\"},\"failureDestination\":{\"description\":\"失败时的兜底投递目标\"}}},\"sessionTarget\":{\"type\":\"string\",\"description\":\"会话目标：main、isolated、current 或 session:<id>\"},\"wakeMode\":{\"type\":\"string\",\"enum\":[\"now\",\"next-heartbeat\"],\"description\":\"唤醒模式：now 或 next-heartbeat\"},\"deleteAfterRun\":{\"type\":\"boolean\",\"description\":\"执行后是否自动删除该任务\"},\"cron_expr\":{\"type\":\"string\",\"description\":\"兼容层cron表达式(Quartz格式)。7段式：秒 分 时 日 月 周 年。日和周：不能同时指定具体值，其中一个用?。年份：*跨年周期，固定年份只在该年执行。一次性：所有字段固定值，如'0 0 17 28 3 ? 2026'。例：每天9点'0 0 9 * * ? *'；每15分钟'0 */15 * * * ? *'。详见工具描述。\"},\"timezone\":{\"type\":\"string\",\"description\":\"兼容层时区字段\"},\"wake_offset_seconds\":{\"type\":\"integer\",\"description\":\"兼容层提前唤醒秒数。默认 300，若用户未指定则使用 300 秒。\"},\"description\":{\"type\":\"string\",\"description\":\"具体任务内容，到点执行时发给助手。不要包含时间/频率信息（如'每隔40分钟'、'每天9点'）\"},\"targets\":{\"type\":\"string\",\"description\":\"目标频道。用户未明确指定时不填，系统自动使用当前对话渠道；**禁止从历史记录推断。**\"}},\"description\":\"用于 add 的任务对象；支持结构化字段和兼容层字段\"},\"jobId\":{\"type\":\"string\",\"description\":\"用于 update/remove/run/runs 的任务 ID\"},\"patch\":{\"type\":\"object\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"任务名称\"},\"enabled\":{\"type\":\"boolean\",\"description\":\"任务是否启用\"},\"schedule\":{\"type\":\"object\",\"description\":\"结构化调度定义，支持 at/every/cron\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"kind\":{\"type\":\"string\",\"enum\":[\"at\",\"every\",\"cron\"],\"description\":\"调度类型：at、every 或 cron\"},\"at\":{\"type\":\"string\",\"description\":\"一次性执行时间，ISO 8601\"},\"everyMs\":{\"type\":\"integer\",\"description\":\"循环间隔，毫秒\"},\"anchorMs\":{\"type\":\"integer\",\"description\":\"every 调度的起始锚点毫秒时间戳\"},\"expr\":{\"type\":\"string\",\"description\":\"cron表达式(Quartz格式)。7段式：秒 分 时 日 月 周 年。日和周：不能同时指定具体值，其中一个用?。年份：*跨年周期，固定年份只在该年执行。一次性：所有字段固定值，如'0 0 17 28 3 ? 2026'。例：每天9点'0 0 9 * * ? *'；每15分钟'0 */15 * * * ? *'。详见工具描述。\"},\"tz\":{\"type\":\"string\",\"description\":\"cron 调度使用的时区\"},\"staggerMs\":{\"type\":\"integer\",\"description\":\"cron 调度的可选抖动毫秒数\"}}},\"payload\":{\"type\":\"object\",\"description\":\"结构化任务负载，支持 systemEvent 或 agentTurn\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"kind\":{\"type\":\"string\",\"enum\":[\"systemEvent\",\"agentTurn\"],\"description\":\"负载类型：systemEvent 或 agentTurn\"},\"text\":{\"type\":\"string\",\"description\":\"systemEvent提醒文本。不要包含时间/频率信息（如'每隔40分钟'、'每天9点'）\"},\"message\":{\"type\":\"string\",\"description\":\"agentTurn 发送给代理的消息\"},\"model\":{\"type\":\"string\",\"description\":\"agentTurn 可选模型覆盖\"},\"thinking\":{\"type\":\"string\",\"description\":\"agentTurn 的思考预算或模式\"},\"timeoutSeconds\":{\"type\":\"integer\",\"description\":\"agentTurn 超时时间（秒）\"},\"allowUnsafeExternalContent\":{\"type\":\"boolean\",\"description\":\"是否允许不安全的外部内容\"},\"lightContext\":{\"type\":\"boolean\",\"description\":\"是否使用轻量上下文执行\"},\"deliver\":{\"type\":\"string\",\"description\":\"agentTurn 自带的投递策略字段\"},\"channel\":{\"type\":\"string\",\"description\":\"agentTurn 的默认投递频道\"},\"to\":{\"type\":\"string\",\"description\":\"agentTurn 的目标收件人\"},\"bestEffortDeliver\":{\"type\":\"boolean\",\"description\":\"是否最佳努力投递\"},\"fallbacks\":{\"type\":\"array\",\"description\":\"agentTurn 的回退投递列表\",\"items\":{\"type\":\"string\",\"description\":\"agentTurn 的回退投递列表\"}}}},\"delivery\":{\"type\":\"object\",\"description\":\"提醒结果的投递方式\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"mode\":{\"type\":\"string\",\"enum\":[\"none\",\"announce\",\"webhook\"],\"description\":\"投递模式：none、announce 或 webhook\"},\"channel\":{\"type\":\"string\",\"description\":\"announce 模式投递频道。用户未明确指定时不填，系统自动使用当前对话渠道；**禁止从历史记录推断。**\"},\"to\":{\"type\":\"string\",\"description\":\"目标收件人或会话标识\"},\"accountId\":{\"type\":\"string\",\"description\":\"投递账号标识\"},\"bestEffort\":{\"description\":\"announce/webhook 是否最佳努力投递\"},\"failureDestination\":{\"description\":\"失败时的兜底投递目标\"}}},\"sessionTarget\":{\"type\":\"string\",\"description\":\"会话目标：main、isolated、current 或 session:<id>\"},\"wakeMode\":{\"type\":\"string\",\"enum\":[\"now\",\"next-heartbeat\"],\"description\":\"唤醒模式：now 或 next-heartbeat\"},\"deleteAfterRun\":{\"type\":\"boolean\",\"description\":\"执行后是否自动删除该任务\"},\"cron_expr\":{\"type\":\"string\",\"description\":\"兼容层cron表达式(Quartz格式)。7段式：秒 分 时 日 月 周 年。日和周：不能同时指定具体值，其中一个用?。年份：*跨年周期，固定年份只在该年执行。一次性：所有字段固定值，如'0 0 17 28 3 ? 2026'。例：每天9点'0 0 9 * * ? *'；每15分钟'0 */15 * * * ? *'。详见工具描述。\"},\"timezone\":{\"type\":\"string\",\"description\":\"兼容层时区字段\"},\"wake_offset_seconds\":{\"type\":\"integer\",\"description\":\"兼容层提前唤醒秒数。默认 300，若用户未指定则使用 300 秒。\"},\"description\":{\"type\":\"string\",\"description\":\"具体任务内容，到点执行时发给助手。不要包含时间/频率信息（如'每隔40分钟'、'每天9点'）\"},\"targets\":{\"type\":\"string\",\"description\":\"目标频道。用户未明确指定时不填，系统自动使用当前对话渠道；**禁止从历史记录推断。**\"}},\"description\":\"用于 update 的补丁对象\"},\"includeDisabled\":{\"type\":\"boolean\",\"description\":\"list 时是否包含已禁用任务\"},\"text\":{\"type\":\"string\",\"description\":\"wake 动作要发送的提示文本\"},\"mode\":{\"type\":\"string\",\"enum\":[\"now\",\"next-heartbeat\"],\"description\":\"wake 的触发模式\"},\"contextMessages\":{\"type\":\"integer\",\"description\":\"保留给上下文提示的兼容字段\"}},\"required\":[\"action\"],\"additionalProperties\":true}";
    private static final String CRONMETADATAPROVIDER_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"action\":{\"type\":\"string\",\"enum\":[\"status\",\"list\",\"add\",\"update\",\"remove\",\"run\",\"runs\",\"wake\"],\"description\":\"Cron action to execute\"},\"job\":{\"type\":\"object\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"Job name\"},\"enabled\":{\"type\":\"boolean\",\"description\":\"Whether the job is enabled\"},\"schedule\":{\"type\":\"object\",\"description\":\"Structured schedule definition supporting at/every/cron\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"kind\":{\"type\":\"string\",\"enum\":[\"at\",\"every\",\"cron\"],\"description\":\"Schedule type: at, every, or cron\"},\"at\":{\"type\":\"string\",\"description\":\"One-shot execution time in ISO 8601\"},\"everyMs\":{\"type\":\"integer\",\"description\":\"Recurring interval in milliseconds\"},\"anchorMs\":{\"type\":\"integer\",\"description\":\"Anchor timestamp in milliseconds for every schedules\"},\"expr\":{\"type\":\"string\",\"description\":\"Cron expression (Quartz format). 7-field: second minute hour day month dow year. Day/dow: cannot both be specific; use '?' for one. Year: '*' for recurring, fixed year limits to that year. One-shot: all fields fixed, e.g. '0 0 17 28 3 ? 2026'. Examples: daily 9am '0 0 9 * * ? *'; every 15min '0 */15 * * * ? *'. See tool description.\"},\"tz\":{\"type\":\"string\",\"description\":\"Timezone used by cron schedules\"},\"staggerMs\":{\"type\":\"integer\",\"description\":\"Optional cron jitter in milliseconds\"}}},\"payload\":{\"type\":\"object\",\"description\":\"Structured job payload supporting systemEvent or agentTurn\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"kind\":{\"type\":\"string\",\"enum\":[\"systemEvent\",\"agentTurn\"],\"description\":\"Payload type: systemEvent or agentTurn\"},\"text\":{\"type\":\"string\",\"description\":\"Reminder text for systemEvent. Do NOT include time/frequency info\"},\"message\":{\"type\":\"string\",\"description\":\"Message sent to the agent for agentTurn payloads\"},\"model\":{\"type\":\"string\",\"description\":\"Optional model override for agentTurn\"},\"thinking\":{\"type\":\"string\",\"description\":\"Thinking mode or budget for agentTurn\"},\"timeoutSeconds\":{\"type\":\"integer\",\"description\":\"Timeout in seconds for agentTurn\"},\"allowUnsafeExternalContent\":{\"type\":\"boolean\",\"description\":\"Whether unsafe external content is allowed\"},\"lightContext\":{\"type\":\"boolean\",\"description\":\"Whether to run with lighter context\"},\"deliver\":{\"type\":\"string\",\"description\":\"Embedded delivery strategy field for agentTurn\"},\"channel\":{\"type\":\"string\",\"description\":\"Default delivery channel for agentTurn\"},\"to\":{\"type\":\"string\",\"description\":\"Target recipient for agentTurn\"},\"bestEffortDeliver\":{\"type\":\"boolean\",\"description\":\"Whether delivery should be best effort\"},\"fallbacks\":{\"type\":\"array\",\"description\":\"Fallback delivery list for agentTurn\",\"items\":{\"type\":\"string\",\"description\":\"Fallback delivery list for agentTurn\"}}}},\"delivery\":{\"type\":\"object\",\"description\":\"How reminder output should be delivered\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"mode\":{\"type\":\"string\",\"enum\":[\"none\",\"announce\",\"webhook\"],\"description\":\"Delivery mode: none, announce, or webhook\"},\"channel\":{\"type\":\"string\",\"description\":\"Delivery channel for announce mode. Leave empty unless user explicitly specifies; system uses current channel. **Never infer from history.**\"},\"to\":{\"type\":\"string\",\"description\":\"Target recipient or session identifier\"},\"accountId\":{\"type\":\"string\",\"description\":\"Account identifier for delivery\"},\"bestEffort\":{\"description\":\"Whether announce/webhook delivery is best effort\"},\"failureDestination\":{\"description\":\"Fallback destination when delivery fails\"}}},\"sessionTarget\":{\"type\":\"string\",\"description\":\"Session target: main, isolated, current, or session:<id>\"},\"wakeMode\":{\"type\":\"string\",\"enum\":[\"now\",\"next-heartbeat\"],\"description\":\"Wake mode: now or next-heartbeat\"},\"deleteAfterRun\":{\"type\":\"boolean\",\"description\":\"Whether the job should be deleted after it runs\"},\"cron_expr\":{\"type\":\"string\",\"description\":\"Compatibility cron expression (Quartz format). 7-field: second minute hour day month dow year. Day/dow: cannot both be specific; use '?' for one. Year: '*' for recurring, fixed year limits to that year. One-shot: all fields fixed, e.g. '0 0 17 28 3 ? 2026'. Examples: daily 9am '0 0 9 * * ? *'; every 15min '0 */15 * * * ? *'. See tool description.\"},\"timezone\":{\"type\":\"string\",\"description\":\"Compatibility timezone field\"},\"wake_offset_seconds\":{\"type\":\"integer\",\"description\":\"Compatibility wake offset in seconds. Default 300; use 300 seconds if user does not specify.\"},\"description\":{\"type\":\"string\",\"description\":\"Task content sent to assistant at scheduled time. Do NOT include time/frequency info\"},\"targets\":{\"type\":\"string\",\"description\":\"Target channel. Leave empty unless user explicitly specifies; system uses current channel. **Never infer from history.**\"}},\"description\":\"Job object for add; supports structured fields and compatibility fields\"},\"jobId\":{\"type\":\"string\",\"description\":\"Job id used by update/remove/run/runs\"},\"patch\":{\"type\":\"object\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"Job name\"},\"enabled\":{\"type\":\"boolean\",\"description\":\"Whether the job is enabled\"},\"schedule\":{\"type\":\"object\",\"description\":\"Structured schedule definition supporting at/every/cron\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"kind\":{\"type\":\"string\",\"enum\":[\"at\",\"every\",\"cron\"],\"description\":\"Schedule type: at, every, or cron\"},\"at\":{\"type\":\"string\",\"description\":\"One-shot execution time in ISO 8601\"},\"everyMs\":{\"type\":\"integer\",\"description\":\"Recurring interval in milliseconds\"},\"anchorMs\":{\"type\":\"integer\",\"description\":\"Anchor timestamp in milliseconds for every schedules\"},\"expr\":{\"type\":\"string\",\"description\":\"Cron expression (Quartz format). 7-field: second minute hour day month dow year. Day/dow: cannot both be specific; use '?' for one. Year: '*' for recurring, fixed year limits to that year. One-shot: all fields fixed, e.g. '0 0 17 28 3 ? 2026'. Examples: daily 9am '0 0 9 * * ? *'; every 15min '0 */15 * * * ? *'. See tool description.\"},\"tz\":{\"type\":\"string\",\"description\":\"Timezone used by cron schedules\"},\"staggerMs\":{\"type\":\"integer\",\"description\":\"Optional cron jitter in milliseconds\"}}},\"payload\":{\"type\":\"object\",\"description\":\"Structured job payload supporting systemEvent or agentTurn\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"kind\":{\"type\":\"string\",\"enum\":[\"systemEvent\",\"agentTurn\"],\"description\":\"Payload type: systemEvent or agentTurn\"},\"text\":{\"type\":\"string\",\"description\":\"Reminder text for systemEvent. Do NOT include time/frequency info\"},\"message\":{\"type\":\"string\",\"description\":\"Message sent to the agent for agentTurn payloads\"},\"model\":{\"type\":\"string\",\"description\":\"Optional model override for agentTurn\"},\"thinking\":{\"type\":\"string\",\"description\":\"Thinking mode or budget for agentTurn\"},\"timeoutSeconds\":{\"type\":\"integer\",\"description\":\"Timeout in seconds for agentTurn\"},\"allowUnsafeExternalContent\":{\"type\":\"boolean\",\"description\":\"Whether unsafe external content is allowed\"},\"lightContext\":{\"type\":\"boolean\",\"description\":\"Whether to run with lighter context\"},\"deliver\":{\"type\":\"string\",\"description\":\"Embedded delivery strategy field for agentTurn\"},\"channel\":{\"type\":\"string\",\"description\":\"Default delivery channel for agentTurn\"},\"to\":{\"type\":\"string\",\"description\":\"Target recipient for agentTurn\"},\"bestEffortDeliver\":{\"type\":\"boolean\",\"description\":\"Whether delivery should be best effort\"},\"fallbacks\":{\"type\":\"array\",\"description\":\"Fallback delivery list for agentTurn\",\"items\":{\"type\":\"string\",\"description\":\"Fallback delivery list for agentTurn\"}}}},\"delivery\":{\"type\":\"object\",\"description\":\"How reminder output should be delivered\",\"required\":[],\"additionalProperties\":true,\"properties\":{\"mode\":{\"type\":\"string\",\"enum\":[\"none\",\"announce\",\"webhook\"],\"description\":\"Delivery mode: none, announce, or webhook\"},\"channel\":{\"type\":\"string\",\"description\":\"Delivery channel for announce mode. Leave empty unless user explicitly specifies; system uses current channel. **Never infer from history.**\"},\"to\":{\"type\":\"string\",\"description\":\"Target recipient or session identifier\"},\"accountId\":{\"type\":\"string\",\"description\":\"Account identifier for delivery\"},\"bestEffort\":{\"description\":\"Whether announce/webhook delivery is best effort\"},\"failureDestination\":{\"description\":\"Fallback destination when delivery fails\"}}},\"sessionTarget\":{\"type\":\"string\",\"description\":\"Session target: main, isolated, current, or session:<id>\"},\"wakeMode\":{\"type\":\"string\",\"enum\":[\"now\",\"next-heartbeat\"],\"description\":\"Wake mode: now or next-heartbeat\"},\"deleteAfterRun\":{\"type\":\"boolean\",\"description\":\"Whether the job should be deleted after it runs\"},\"cron_expr\":{\"type\":\"string\",\"description\":\"Compatibility cron expression (Quartz format). 7-field: second minute hour day month dow year. Day/dow: cannot both be specific; use '?' for one. Year: '*' for recurring, fixed year limits to that year. One-shot: all fields fixed, e.g. '0 0 17 28 3 ? 2026'. Examples: daily 9am '0 0 9 * * ? *'; every 15min '0 */15 * * * ? *'. See tool description.\"},\"timezone\":{\"type\":\"string\",\"description\":\"Compatibility timezone field\"},\"wake_offset_seconds\":{\"type\":\"integer\",\"description\":\"Compatibility wake offset in seconds. Default 300; use 300 seconds if user does not specify.\"},\"description\":{\"type\":\"string\",\"description\":\"Task content sent to assistant at scheduled time. Do NOT include time/frequency info\"},\"targets\":{\"type\":\"string\",\"description\":\"Target channel. Leave empty unless user explicitly specifies; system uses current channel. **Never infer from history.**\"}},\"description\":\"Patch object used by update\"},\"includeDisabled\":{\"type\":\"boolean\",\"description\":\"Whether list should include disabled jobs\"},\"text\":{\"type\":\"string\",\"description\":\"Wake text to inject for action=wake\"},\"mode\":{\"type\":\"string\",\"enum\":[\"now\",\"next-heartbeat\"],\"description\":\"Wake delivery mode\"},\"contextMessages\":{\"type\":\"integer\",\"description\":\"Reserved compatibility field for context hints\"}},\"required\":[\"action\"],\"additionalProperties\":true}";

    /**
     * Mirrors Python's {@code CronMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/cron.py}.
     */
    public static final class CronMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "cron";
        }

        @Override
        public String getDescription(String language) {
            return resolve(CRONMETADATAPROVIDER_DESCRIPTION_CN, CRONMETADATAPROVIDER_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(CRONMETADATAPROVIDER_SCHEMA_CN, CRONMETADATAPROVIDER_SCHEMA_EN, language));
        }
    }

    private static final String CRONPREVIEWJOBMETADATAPROVIDER_DESCRIPTION_CN = "预览 cron 定时任务的下 N 次计划执行时间。";
    private static final String CRONPREVIEWJOBMETADATAPROVIDER_DESCRIPTION_EN = "Preview next N scheduled run times for a cron job.";
    private static final String CRONPREVIEWJOBMETADATAPROVIDER_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"job_id\":{\"type\":\"string\",\"description\":\"要预览的任务 ID\"},\"count\":{\"type\":\"integer\",\"description\":\"预览的执行次数（1-50，默认 5）\",\"default\":5}},\"required\":[\"job_id\"]}";
    private static final String CRONPREVIEWJOBMETADATAPROVIDER_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"job_id\":{\"type\":\"string\",\"description\":\"Job ID\"},\"count\":{\"type\":\"integer\",\"description\":\"Number of runs to preview (1-50, default 5)\",\"default\":5}},\"required\":[\"job_id\"]}";

    /**
     * Mirrors Python's {@code CronPreviewJobMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/cron.py}.
     */
    public static final class CronPreviewJobMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "cron_preview_job";
        }

        @Override
        public String getDescription(String language) {
            return resolve(CRONPREVIEWJOBMETADATAPROVIDER_DESCRIPTION_CN, CRONPREVIEWJOBMETADATAPROVIDER_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(CRONPREVIEWJOBMETADATAPROVIDER_SCHEMA_CN, CRONPREVIEWJOBMETADATAPROVIDER_SCHEMA_EN, language));
        }
    }

    private static final String CRONTOGGLEJOBMETADATAPROVIDER_DESCRIPTION_CN = "启用或禁用指定的 cron 定时任务。";
    private static final String CRONTOGGLEJOBMETADATAPROVIDER_DESCRIPTION_EN = "Enable or disable a cron job.";
    private static final String CRONTOGGLEJOBMETADATAPROVIDER_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"job_id\":{\"type\":\"string\",\"description\":\"要启用/禁用的任务 ID\"},\"enabled\":{\"type\":\"boolean\",\"description\":\"是否启用该任务\"}},\"required\":[\"job_id\",\"enabled\"]}";
    private static final String CRONTOGGLEJOBMETADATAPROVIDER_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"job_id\":{\"type\":\"string\",\"description\":\"Job ID\"},\"enabled\":{\"type\":\"boolean\",\"description\":\"Whether to enable the job\"}},\"required\":[\"job_id\",\"enabled\"]}";

    /**
     * Mirrors Python's {@code CronToggleJobMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/cron.py}.
     */
    public static final class CronToggleJobMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "cron_toggle_job";
        }

        @Override
        public String getDescription(String language) {
            return resolve(CRONTOGGLEJOBMETADATAPROVIDER_DESCRIPTION_CN, CRONTOGGLEJOBMETADATAPROVIDER_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(CRONTOGGLEJOBMETADATAPROVIDER_SCHEMA_CN, CRONTOGGLEJOBMETADATAPROVIDER_SCHEMA_EN, language));
        }
    }

    private static final String CRONUPDATEJOBMETADATAPROVIDER_DESCRIPTION_CN = "使用扁平字段更新已有的 cron 定时任务。";
    private static final String CRONUPDATEJOBMETADATAPROVIDER_DESCRIPTION_EN = "Update an existing cron job with a flat patch dict.";
    private static final String CRONUPDATEJOBMETADATAPROVIDER_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"job_id\":{\"type\":\"string\",\"description\":\"要更新的任务 ID\"},\"patch\":{\"type\":\"object\",\"description\":\"要更新的字段\",\"additionalProperties\":true}},\"required\":[\"job_id\",\"patch\"]}";
    private static final String CRONUPDATEJOBMETADATAPROVIDER_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"job_id\":{\"type\":\"string\",\"description\":\"Job ID to update\"},\"patch\":{\"type\":\"object\",\"description\":\"Fields to update\",\"additionalProperties\":true}},\"required\":[\"job_id\",\"patch\"]}";

    /**
     * Mirrors Python's {@code CronUpdateJobMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/cron.py}.
     */
    public static final class CronUpdateJobMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "cron_update_job";
        }

        @Override
        public String getDescription(String language) {
            return resolve(CRONUPDATEJOBMETADATAPROVIDER_DESCRIPTION_CN, CRONUPDATEJOBMETADATAPROVIDER_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(CRONUPDATEJOBMETADATAPROVIDER_SCHEMA_CN, CRONUPDATEJOBMETADATAPROVIDER_SCHEMA_EN, language));
        }
    }

}
