// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Process-global i18n for agent team runtime strings.
 * 
 * Houses hard-coded user-facing strings that live inside runtime code paths
 * (dispatcher nudges, backend message content, default persona) so they can
 * be switched between Chinese and English without source edits.
 * 
 * Mirrors Python's {@code i18n.py} module in {@code openjiuwen.agent_teams.i18n}.
 * 
 * Usage:
 *     I18n.setLanguage("en");
 *     String msg = I18n.t("dispatcher.member_online", "target_id", "dev-1");
 * 
 * @since 0.1.12
 */
public final class I18n {
    private static final Pattern FORMAT_FIELD = Pattern.compile("\\{([^{}]+)}");
    
    /**
     * Supported language codes.
     */
    public enum Language {
        CN("cn"),
        EN("en");
        
        private final String code;
        
        Language(String code) {
            this.code = code;
        }
        
        public String getCode() {
            return code;
        }
        
        public static Language fromCode(String code) {
            for (Language lang : Language.values()) {
                if (lang.code.equals(code)) {
                    return lang;
                }
            }
            throw new IllegalArgumentException("Unsupported language '" + code + "'");
        }
    }
    
    private static final Language DEFAULT_LANGUAGE = Language.CN;
    private static volatile Language currentLanguage = DEFAULT_LANGUAGE;
    
    /**
     * Translation strings for each language.
     * Map structure: language_code -> key -> translation
     */
    public static final Map<String, Map<String, String>> STRINGS = createStrings();
    
    private static Map<String, Map<String, String>> createStrings() {
        Map<String, Map<String, String>> strings = new HashMap<>();
        
        // Chinese translations
        Map<String, String> cnStrings = new HashMap<>();
        cnStrings.put("blueprint.default_persona", "天才项目管理专家");
        cnStrings.put("team.shutdown_request_content", "当前任务已全部完成，请结束流程");
        cnStrings.put("team.cancel_request_content", "当前任务有变动，请停止执行当前任务，重新尝试认领合适任务");
        cnStrings.put("dispatcher.member_online", "[成员事件] 成员 {target_id} 已上线");
        cnStrings.put("dispatcher.member_restarted", "[成员事件] 成员 {target_id} 已重启 (第{restart_count}次)");
        cnStrings.put("dispatcher.member_status_changed", "[成员事件] 成员 {target_id} 状态变更: {old_status} → {new_status}");
        cnStrings.put("dispatcher.member_execution_changed", "[成员事件] 成员 {target_id} 执行状态变更: {old_status} → {new_status}");
        cnStrings.put("dispatcher.member_shutdown", "[成员事件] 成员 {target_id} 已关闭");
        cnStrings.put("dispatcher.member_canceled", "[成员事件] 成员 {target_id} 已取消");
        cnStrings.put("dispatcher.stale_claim_header", "检测到你已认领且超过 10 分钟未完成的任务（共 {count} 个），请继续推进：");
        cnStrings.put("dispatcher.stale_claim_self", "[催促] 你已认领的任务 [{task_id}] {title} 已超过 10 mins 仍未完成，请继续推进：{content}");
        cnStrings.put("dispatcher.task_assigned_to_self", "[任务指派] 任务 [{task_id}] 已指派给你，请通过 view_task 工具查看任务详情并执行。");
        cnStrings.put("dispatcher.msg_type_broadcast", "广播消息");
        cnStrings.put("dispatcher.msg_type_direct", "单播消息");
        cnStrings.put("dispatcher.msg_received", "[收到{msg_type}] message_id={message_id}, 来自: {sender}\n内容: {content}\n提示: 如果对方在提问或等待回复，请务必通过 send_message 工具回复 {sender}");
        cnStrings.put("dispatcher.all_done_persistent", "所有任务已完成。请汇总本轮工作成果。团队继续保持运行，等待新的任务指令。");
        cnStrings.put("dispatcher.all_done_temporary", "所有任务已完成。请汇总团队工作成果，然后依次调用 shutdown_member 关闭所有成员，等待所有成员状态转为 shutdown 后，调用 clean_team 解散团队。");
        cnStrings.put("dispatcher.leader_task_board", "当前任务看板如下，请审查：\n- 是否需要调整任务（增删、修改、调整依赖）\n- 就绪任务是否需要指派给 teammate\n- 整体进度是否符合预期");
        cnStrings.put("dispatcher.teammate_task_list", "当前任务列表如下：\n- 请认领适合你领域的待领取任务\n- 了解相关任务的执行者，必要时与他们协调配合");
        cnStrings.put("dispatcher.task_unassigned_marker", " (待领取)");
        cnStrings.put("dispatcher.stale_pending_header", "[催促建议] 以下任务已长时间处于 pending 状态未被认领，请评估每个任务最适合哪位成员，并通过 send_message 工具点名对方让其使用 claim_task 认领：");
        cnStrings.put("hitt.human_agent_display_name", "人类成员");
        cnStrings.put("hitt.human_agent_default_persona", "团队中的人类协作者。与 leader、teammate 地位平等；由真实操作者驱动，可接收任务、回复消息、参与协作。");
        cnStrings.put("hitt.human_agent_spawned", "[成员事件] 人类成员 human_agent 已加入团队");
        strings.put("cn", Collections.unmodifiableMap(cnStrings));
        
        // English translations
        Map<String, String> enStrings = new HashMap<>();
        enStrings.put("blueprint.default_persona", "Genius project management expert");
        enStrings.put("team.shutdown_request_content", "All tasks are complete. Please wrap up and exit.");
        enStrings.put("team.cancel_request_content", "The current task has changed. Stop executing it and try claiming a suitable task again.");
        enStrings.put("dispatcher.member_online", "[Member Event] Member {target_id} is online");
        enStrings.put("dispatcher.member_restarted", "[Member Event] Member {target_id} restarted (attempt {restart_count})");
        enStrings.put("dispatcher.member_status_changed", "[Member Event] Member {target_id} status changed: {old_status} → {new_status}");
        enStrings.put("dispatcher.member_execution_changed", "[Member Event] Member {target_id} execution status changed: {old_status} → {new_status}");
        enStrings.put("dispatcher.member_shutdown", "[Member Event] Member {target_id} has shut down");
        enStrings.put("dispatcher.member_canceled", "[Member Event] Member {target_id} has been canceled");
        enStrings.put("dispatcher.stale_claim_header", "Detected {count} task(s) you claimed that have been open for over 10 minutes. Please push forward:");
        enStrings.put("dispatcher.stale_claim_self", "[Nudge] Your claimed task [{task_id}] {title} has been open for over 10 mins. Please continue: {content}");
        enStrings.put("dispatcher.task_assigned_to_self", "[Task Assigned] Task [{task_id}] has been assigned to you. Use view_task to inspect the details and start working on it.");
        enStrings.put("dispatcher.msg_type_broadcast", "broadcast");
        enStrings.put("dispatcher.msg_type_direct", "direct message");
        enStrings.put("dispatcher.msg_received", "[Received {msg_type}] message_id={message_id}, from: {sender}\ncontent: {content}\ntip: If the sender is asking or waiting for a reply, make sure to reply to {sender} via send_message");
        enStrings.put("dispatcher.all_done_persistent", "All tasks are complete. Please summarize this round's results. The team remains running and awaits new task instructions.");
        enStrings.put("dispatcher.all_done_temporary", "All tasks are complete. Summarize the team's work, then call shutdown_member for each member in turn, wait until all members reach status shutdown, and finally call clean_team to disband the team.");
        enStrings.put("dispatcher.leader_task_board", "Current task board — please review:\n- Whether any tasks need adjustment (add/remove/edit/dependencies)\n- Whether ready tasks should be assigned to a teammate\n- Whether the overall progress matches expectations");
        enStrings.put("dispatcher.teammate_task_list", "Current task list:\n- Claim pending tasks that fit your domain\n- Know who is working on related tasks and coordinate when needed");
        enStrings.put("dispatcher.task_unassigned_marker", " (unassigned)");
        enStrings.put("dispatcher.stale_pending_header", "[Nudge suggestion] The following tasks have been pending unclaimed for a long time. Decide which member fits each task best, then use send_message to call them out and ask them to claim via claim_task:");
        enStrings.put("hitt.human_agent_display_name", "Human Member");
        enStrings.put("hitt.human_agent_default_persona", "The human collaborator on the team. Equal in standing with the leader and teammates; driven by a real operator, can receive tasks, reply to messages, and collaborate.");
        enStrings.put("hitt.human_agent_spawned", "[Member Event] Human member 'human_agent' joined the team");
        strings.put("en", Collections.unmodifiableMap(enStrings));
        
        return Collections.unmodifiableMap(strings);
    }
    
    /**
     * Set the process-global language for runtime strings.
     * 
     * @param lang Language code, one of "cn" or "en"
     * @throws IllegalArgumentException if lang is not a supported language
     */
    public static void setLanguage(String lang) {
        if (!STRINGS.containsKey(lang)) {
            String supported = STRINGS.keySet().stream().sorted().collect(java.util.stream.Collectors.joining(", "));
            throw new IllegalArgumentException("Unsupported language '" + lang + "'. Supported: " + supported);
        }
        currentLanguage = Language.fromCode(lang);
    }
    
    /**
     * Set the process-global language for runtime strings.
     * 
     * @param lang Language enum value
     */
    public static void setLanguage(Language lang) {
        if (lang == null) {
            throw new IllegalArgumentException("Language cannot be null");
        }
        currentLanguage = lang;
    }
    
    /**
     * Return the current process-global language code.
     * 
     * @return The current language
     */
    public static Language getLanguage() {
        return currentLanguage;
    }
    
    /**
     * Resolve a localized string for the current language.
     * 
     * @param key Dotted lookup key (e.g. "dispatcher.member_online")
     * @return The localized string for the current language
     * @throws IllegalArgumentException if key is missing for the active language
     */
    public static String t(String key) {
        return t(key, Collections.emptyMap());
    }
    
    /**
     * Resolve a localized string for the current language with parameter interpolation.
     * 
     * @param key Dotted lookup key (e.g. "dispatcher.member_online")
     * @param params Key-value pairs for string interpolation
     * @return The localized string with parameters interpolated
     * @throws IllegalArgumentException if key is missing for the active language
     */
    public static String t(String key, Map<String, Object> params) {
        Map<String, String> table = STRINGS.get(currentLanguage.getCode());
        if (table == null || !table.containsKey(key)) {
            throw new IllegalArgumentException("Missing i18n key '" + key + "' for language '" + currentLanguage.getCode() + "'");
        }
        String raw = table.get(key);
        if (params == null || params.isEmpty()) {
            return raw;
        }
        return formatMap(raw, params);
    }

    private static String formatMap(String raw, Map<String, Object> params) {
        Matcher matcher = FORMAT_FIELD.matcher(raw);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!params.containsKey(name)) {
                throw new IllegalArgumentException("Missing i18n format key '" + name + "'");
            }
            Object value = params.get(name);
            String replacement = value == null ? "None" : String.valueOf(value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Resolve a localized string with convenience parameter passing.
     * 
     * @param key Dotted lookup key
     * @param paramName Single parameter name
     * @param paramValue Single parameter value
     * @return The localized string with parameter interpolated
     */
    public static String t(String key, String paramName, Object paramValue) {
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, paramValue);
        return t(key, params);
    }
    
    /**
     * Resolve a localized string with two convenience parameters.
     * 
     * @param key Dotted lookup key
     * @param param1 First parameter name
     * @param value1 First parameter value
     * @param param2 Second parameter name
     * @param value2 Second parameter value
     * @return The localized string with parameters interpolated
     */
    public static String t(String key, String param1, Object value1, String param2, Object value2) {
        Map<String, Object> params = new HashMap<>();
        params.put(param1, value1);
        params.put(param2, value2);
        return t(key, params);
    }
    
    // Private constructor to prevent instantiation
    private I18n() {
        throw new AssertionError("I18n class should not be instantiated");
    }
}
