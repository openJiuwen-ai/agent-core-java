/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Process-global i18n strings for agent team runtime text.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.i18n} in
 * {@code openjiuwen/agent_teams/i18n.py}.</p>
 */
public final class AgentTeamI18n {

    public static final String DEFAULT_LANGUAGE = "cn";
    public static final Set<String> SUPPORTED_LANGUAGES = Set.of("cn", "en");
    public static final Map<String, Map<String, String>> STRINGS = Map.of(
            "cn", cnStrings(),
            "en", enStrings()
    );

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");

    private static volatile String currentLanguage = DEFAULT_LANGUAGE;

    private AgentTeamI18n() {
    }

    public static void setLanguage(String lang) {
        if (!STRINGS.containsKey(lang)) {
            throw new IllegalArgumentException(
                    "Unsupported language '" + lang + "'. Supported: cn, en"
            );
        }
        currentLanguage = lang;
    }

    public static String getLanguage() {
        return currentLanguage;
    }

    public static String t(String key) {
        return t(key, Map.of());
    }

    public static String t(String key, Object... keyValuePairs) {
        if (keyValuePairs.length == 0) {
            return t(key);
        }
        if ((keyValuePairs.length & 1) != 0) {
            throw new IllegalArgumentException("Key/value arguments must come in pairs.");
        }
        Map<String, Object> kwargs = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            kwargs.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return t(key, kwargs);
    }

    public static String t(String key, Map<String, ?> kwargs) {
        Map<String, String> table = STRINGS.get(currentLanguage);
        if (!table.containsKey(key)) {
            throw new NoSuchElementException(
                    "Missing i18n key '" + key + "' for language '" + currentLanguage + "'"
            );
        }
        String raw = table.get(key);
        if (kwargs.isEmpty()) {
            return raw;
        }
        Matcher matcher = PLACEHOLDER.matcher(raw);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!kwargs.containsKey(placeholder)) {
                throw new NoSuchElementException(placeholder);
            }
            Object value = kwargs.get(placeholder);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static Map<String, String> cnStrings() {
        return Map.ofEntries(
                Map.entry("time.just_now", "刚刚"),
                Map.entry("time.seconds_ago", "{value} 秒前"),
                Map.entry("time.minutes_ago", "{value} 分钟前"),
                Map.entry("time.hours_ago", "{value} 小时前"),
                Map.entry("time.days_ago", "{value} 天前"),
                Map.entry("time.unknown", "时间未知"),
                Map.entry("blueprint.default_persona", "天才项目管理专家"),
                Map.entry("team.shutdown_request_content", "当前任务已全部完成，请结束流程"),
                Map.entry("team.cancel_request_content", "当前任务有变动，请停止执行当前任务，重新尝试认领合适任务"),
                Map.entry("dispatcher.member_online", "[成员事件] 成员 {target_id} 已上线"),
                Map.entry("dispatcher.member_restarted", "[成员事件] 成员 {target_id} 已重启 (第{restart_count}次)"),
                Map.entry("dispatcher.member_status_changed", "[成员事件] 成员 {target_id} 状态变更: {old_status} → {new_status}"),
                Map.entry("dispatcher.member_execution_changed", "[成员事件] 成员 {target_id} 执行状态变更: {old_status} → {new_status}"),
                Map.entry("dispatcher.member_shutdown", "[成员事件] 成员 {target_id} 已关闭"),
                Map.entry("dispatcher.member_canceled", "[成员事件] 成员 {target_id} 已取消"),
                Map.entry("dispatcher.stale_claim_header", "检测到你已认领且超过 10 分钟未完成的任务（共 {count} 个），请继续推进："),
                Map.entry("dispatcher.stale_claim_self",
                        "[催促] 你已认领的任务 [{task_id}] {title}（认领于 {time_info}）仍未完成，请继续推进：{content}"),
                Map.entry("dispatcher.task_assigned_to_self",
                        "[任务指派] 任务 [{task_id}] 已指派给你，请通过 view_task 工具查看任务详情并执行。"),
                Map.entry("dispatcher.task_plan_approved_to_self",
                        "[计划已批准] 任务 [{task_id}] 的执行计划已通过。请开始执行，完成后用 claim_task(status='completed') 标记完成。{feedback}"),
                Map.entry("dispatcher.task_plan_rejected_to_self",
                        "[计划需修改] 任务 [{task_id}] 的执行计划未通过。请根据反馈修改并重新调用 submit_plan。反馈：{feedback}"),
                Map.entry("dispatcher.msg_type_broadcast", "广播消息"),
                Map.entry("dispatcher.msg_type_direct", "单播消息"),
                Map.entry("dispatcher.msg_received",
                        "[收到{msg_type}] message_id={message_id}, 来自: {sender}\n时间: {time_info}\n内容: {content}\n提示: 如果对方在提问或等待回复，请务必通过 send_message 工具回复 {sender}"),
                Map.entry("dispatcher.all_done_persistent", "所有任务已完成。请汇总本轮工作成果。团队继续保持运行，等待新的任务指令。"),
                Map.entry("dispatcher.all_done_temporary",
                        "所有任务已完成。请汇总团队工作成果，然后依次调用 shutdown_member 关闭所有成员，等待所有成员状态转为 shutdown 后，调用 clean_team 解散团队。"),
                Map.entry("dispatcher.leader_task_board",
                        "当前任务看板如下，请审查：\n- 是否需要调整任务（增删、修改、调整依赖）\n- 就绪任务是否需要指派给 teammate\n- 整体进度是否符合预期"),
                Map.entry("dispatcher.teammate_task_list",
                        "当前任务列表如下：\n- 请认领适合你领域的待领取任务\n- 了解相关任务的执行者，必要时与他们协调配合"),
                Map.entry("dispatcher.task_unassigned_marker", " (待领取)"),
                Map.entry("dispatcher.stale_pending_header",
                        "[催促建议] 以下任务已长时间处于 pending 状态未被认领，请评估每个任务最适合哪位成员，并通过 send_message 工具点名对方让其使用 claim_task 认领："),
                Map.entry("hitt.human_agent_display_name", "人类成员"),
                Map.entry("hitt.human_agent_default_persona",
                        "外部用户在团队里的代理（avatar）。所有动作都由对应的真人通过 Inbox 驱动；可使用文件、任务、工作空间等工具替用户完成事务，但不主动发声、不自主认领任务。"),
                Map.entry("hitt.human_agent_spawned", "[成员事件] 人类成员 human_agent 已加入团队"),
                Map.entry("hitt.task_assigned_to_self_human",
                        "[任务指派给控制者] 你被指派了新任务 [{task_id}] {title}。\n**这是给控制者看的通知，不是给你的工作指令**；运行时已经把通知原样展示给控制者。\n**严格禁止任何自主行为**：禁止主动回复发起指派的成员、禁止自主调用 send_message / member_complete_task / claim_task / 文件 / shell 等任何工具去回应或推进任务、禁止用纯文本输出表达意图或承诺。\n**保持静默**，等控制者在 Inbox 里下达明确指令后再行动。"),
                Map.entry("hitt.msg_received_for_human",
                        "[转发给控制者的{msg_type}] message_id={message_id}, 来自: {sender}\n时间: {time_info}\n内容: {content}\n**这条消息已经原样转给控制者，不是要你回应的指令**。\n**严格禁止任何自主行为**：禁止主动回复发送方（包括调用 send_message）、禁止自主调用任何其它工具去回应或采取行动、禁止用纯文本输出表达意图或承诺。\n**保持静默**，等控制者在 Inbox 里明确指示你转告或回复时再调 send_message。")
        );
    }

    private static Map<String, String> enStrings() {
        return Map.ofEntries(
                Map.entry("time.just_now", "just now"),
                Map.entry("time.seconds_ago", "{value}s ago"),
                Map.entry("time.minutes_ago", "{value}m ago"),
                Map.entry("time.hours_ago", "{value}h ago"),
                Map.entry("time.days_ago", "{value}d ago"),
                Map.entry("time.unknown", "unknown time"),
                Map.entry("blueprint.default_persona", "Genius project management expert"),
                Map.entry("team.shutdown_request_content", "All tasks are complete. Please wrap up and exit."),
                Map.entry("team.cancel_request_content",
                        "The current task has changed. Stop executing it and try claiming a suitable task again."),
                Map.entry("dispatcher.member_online", "[Member Event] Member {target_id} is online"),
                Map.entry("dispatcher.member_restarted", "[Member Event] Member {target_id} restarted (attempt {restart_count})"),
                Map.entry("dispatcher.member_status_changed",
                        "[Member Event] Member {target_id} status changed: {old_status} → {new_status}"),
                Map.entry("dispatcher.member_execution_changed",
                        "[Member Event] Member {target_id} execution status changed: {old_status} → {new_status}"),
                Map.entry("dispatcher.member_shutdown", "[Member Event] Member {target_id} has shut down"),
                Map.entry("dispatcher.member_canceled", "[Member Event] Member {target_id} has been canceled"),
                Map.entry("dispatcher.stale_claim_header",
                        "Detected {count} task(s) you claimed that have been open for over 10 minutes. Please push forward:"),
                Map.entry("dispatcher.stale_claim_self",
                        "[Nudge] Your claimed task [{task_id}] {title} (claimed {time_info}) is still open. Please continue: {content}"),
                Map.entry("dispatcher.task_assigned_to_self",
                        "[Task Assigned] Task [{task_id}] has been assigned to you. Use view_task to inspect the details and start working on it."),
                Map.entry("dispatcher.task_plan_approved_to_self",
                        "[Plan Approved] Your execution plan for task [{task_id}] was approved. Start execution and call claim_task(status='completed') when done. {feedback}"),
                Map.entry("dispatcher.task_plan_rejected_to_self",
                        "[Plan Rejected] Your execution plan for task [{task_id}] needs revision. Update it and call submit_plan again. Feedback: {feedback}"),
                Map.entry("dispatcher.msg_type_broadcast", "broadcast"),
                Map.entry("dispatcher.msg_type_direct", "direct message"),
                Map.entry("dispatcher.msg_received",
                        "[Received {msg_type}] message_id={message_id}, from: {sender}\ntime: {time_info}\ncontent: {content}\ntip: If the sender is asking or waiting for a reply, make sure to reply to {sender} via send_message"),
                Map.entry("dispatcher.all_done_persistent",
                        "All tasks are complete. Please summarize this round's results. The team remains running and awaits new task instructions."),
                Map.entry("dispatcher.all_done_temporary",
                        "All tasks are complete. Summarize the team's work, then call shutdown_member for each member in turn, wait until all members reach status shutdown, and finally call clean_team to disband the team."),
                Map.entry("dispatcher.leader_task_board",
                        "Current task board — please review:\n- Whether any tasks need adjustment (add/remove/edit/dependencies)\n- Whether ready tasks should be assigned to a teammate\n- Whether the overall progress matches expectations"),
                Map.entry("dispatcher.teammate_task_list",
                        "Current task list:\n- Claim pending tasks that fit your domain\n- Know who is working on related tasks and coordinate when needed"),
                Map.entry("dispatcher.task_unassigned_marker", " (unassigned)"),
                Map.entry("dispatcher.stale_pending_header",
                        "[Nudge suggestion] The following tasks have been pending unclaimed for a long time. Decide which member fits each task best, then use send_message to call them out and ask them to claim via claim_task:"),
                Map.entry("hitt.human_agent_display_name", "Human Member"),
                Map.entry("hitt.human_agent_default_persona",
                        "An external user's avatar on the team. Every action is driven by the corresponding human via the Inbox; uses file, task, and workspace tools to act on the user's behalf, but does not speak up on its own and does not autonomously claim tasks."),
                Map.entry("hitt.human_agent_spawned", "[Member Event] Human member 'human_agent' joined the team"),
                Map.entry("hitt.task_assigned_to_self_human",
                        "[Task Assigned For Controller] You have been assigned task [{task_id}] \"{title}\".\n**This is a notification for your controller, NOT a work instruction for you**; the runtime has already surfaced the notification to the controller as-is.\n**Autonomous behavior is strictly forbidden**: do not reply to the assigner, do not autonomously call send_message / member_complete_task / claim_task / file tools / shell tools or any other tool to act on the assignment, and do not emit plain-text intent or promises.\n**Stay silent** and act only after the controller issues an explicit instruction via the Inbox."),
                Map.entry("hitt.msg_received_for_human",
                        "[For-Controller {msg_type}] message_id={message_id}, from: {sender}\ntime: {time_info}\ncontent: {content}\n**This message has already been surfaced to your controller as-is; it is NOT an instruction for you to act on**.\n**Autonomous behavior is strictly forbidden**: do not reply to the sender (including via send_message), do not autonomously call any other tool to respond or take action, and do not emit plain-text intent or promises.\n**Stay silent** and only call send_message after the controller explicitly instructs you via the Inbox to relay or reply.")
        );
    }
}
