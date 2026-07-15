/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Process-global i18n for agent team runtime strings.
 * <p>
 * Houses hard-coded user-facing strings that live inside runtime code paths
 * (dispatcher nudges, backend message content, default persona) so they can
 * be switched between Chinese and English without source edits.
 * </p>
 * 
 * @since 0.1.7
 */
public final class I18n {
    /**
     * Language.
     * 
     * @since 0.1.7
     */
    public enum Language {
        CN,
        EN
    }

    private static volatile Language currentLanguage = Language.CN;

    private static final Map<String, String> CN_STRINGS;
    private static final Map<String, String> EN_STRINGS;
    private static final Map<Language, Map<String, String>> STRINGS;

    static {
        Map<String, String> cn = new LinkedHashMap<>();
        cn.put("blueprint.default_persona", "\u5929\u624d\u9879\u76ee\u7ba1\u7406\u4e13\u5bb6");
        cn.put("team.shutdown_request_content",
                "\u5f53\u524d\u4efb\u52a1\u5df2\u5168\u90e8\u5b8c\u6210\uff0c\u8bf7\u7ed3\u675f\u6d41\u7a0b");
        cn.put("team.cancel_request_content",
                "\u5f53\u524d\u4efb\u52a1\u6709\u53d8\u52a8\uff0c\u8bf7\u505c\u6b62\u6267\u884c\u5f53"
                        + "\u524d\u4efb\u52a1\uff0c\u91cd\u65b0\u5c1d\u8bd5\u8ba4\u9886\u5408\u9002\u4efb\u52a1");
        cn.put("dispatcher.member_online", "[\u6210\u5458\u4e8b\u4ef6] \u6210\u5458 {0} \u5df2\u4e0a\u7ebf");
        cn.put("dispatcher.member_restarted",
                "[\u6210\u5458\u4e8b\u4ef6] \u6210\u5458 {0} \u5df2\u91cd\u542f (\u7b2c{1}\u6b21)");
        cn.put("dispatcher.member_status_changed",
                "[\u6210\u5458\u4e8b\u4ef6] \u6210\u5458 {0} \u72b6\u6001\u53d8\u66f4: {1} \u2192 {2}");
        cn.put("dispatcher.member_execution_changed",
                "[\u6210\u5458\u4e8b\u4ef6] \u6210\u5458 {0} \u6267\u884c\u72b6\u6001\u53d8\u66f4: {1} \u2192 {2}");
        cn.put("dispatcher.member_shutdown", "[\u6210\u5458\u4e8b\u4ef6] \u6210\u5458 {0} \u5df2\u5173\u95ed");
        cn.put("dispatcher.member_canceled", "[\u6210\u5458\u4e8b\u4ef6] \u6210\u5458 {0} \u5df2\u53d6\u6d88");
        cn.put("dispatcher.stale_claim_header",
                "\u68c0\u6d4b\u5230\u4f60\u5df2\u8ba4\u9886\u4e14\u8d85\u8fc7 10 \u5206\u949f"
                        + "\u672a\u5b8c\u6210\u7684\u4efb\u52a1\uff08"
                        + "\u5171 {0} \u4e2a\uff09\uff0c\u8bf7\u7ee7\u7eed\u63a8\u8fdb\uff1a");
        cn.put("dispatcher.stale_claim_self",
                "[\u50ac\u4fc3] \u4f60\u5df2\u8ba4\u9886\u7684\u4efb\u52a1 [{0}] {1} \u5df2\u8d85"
                        + "\u8fc7 10 mins \u4ecd\u672a\u5b8c\u6210\uff0c\u8bf7\u7ee7\u7eed\u63a8\u8fdb\uff1a{2}");
        cn.put("dispatcher.task_assigned_to_self",
                "[\u4efb\u52a1\u6307\u6d3e] \u4efb\u52a1 [{0}] \u5df2\u6307\u6d3e\u7ed9\u4f60\uff0c"
                        + "\u8bf7\u901a\u8fc7 view_task \u5de5\u5177\u67e5\u770b\u4efb\u52a1\u8be6\u60c5"
                        + "\u5e76\u6267\u884c\u3002");
        cn.put("dispatcher.msg_type_broadcast", "\u5e7f\u64ad\u6d88\u606f");
        cn.put("dispatcher.msg_type_direct", "\u5355\u64ad\u6d88\u606f");
        cn.put("dispatcher.msg_received",
                "[\u6536\u5230{0}] message_id={1}, " + "\u6765\u81ea: {2}\n" + "\u5185\u5bb9: {3}\n"
                        + "\u63d0\u793a: \u5982\u679c\u5bf9\u65b9\u5728\u63d0\u95ee\u6216\u7b49\u5f85\u56de\u590d\uff0c"
                        + "\u8bf7\u52a1\u5fc5\u901a\u8fc7 send_message \u5de5\u5177\u56de\u590d {2}");
        cn.put("dispatcher.all_done_persistent",
                "\u6240\u6709\u4efb\u52a1\u5df2\u5b8c\u6210\u3002\u8bf7\u6c47\u603b\u672c"
                        + "\u8f6e\u5de5\u4f5c\u6210\u679c\u3002\u56e2\u961f\u7ee7\u7eed\u4fdd\u6301"
                        + "\u8fd0\u884c\uff0c\u7b49\u5f85\u65b0\u7684\u4efb\u52a1\u6307\u4ee4\u3002");
        cn.put("dispatcher.all_done_temporary", "\u6240\u6709\u4efb\u52a1\u5df2\u5b8c\u6210\u3002\u8bf7"
                + "\u6c47\u603b\u56e2\u961f\u5de5\u4f5c\u6210\u679c\uff0c"
                + "\u7136\u540e\u4f9d\u6b21\u8c03\u7528 shutdown_member " + "\u5173\u95ed\u6240\u6709\u6210\u5458\uff0c"
                + "\u7b49\u5f85\u6240\u6709\u6210\u5458\u72b6\u6001\u8f6c\u4e3a shutdown \u540e\uff0c"
                + "\u8c03\u7528 clean_team \u89e3\u6563\u56e2\u961f\u3002");
        cn.put("dispatcher.leader_task_board",
                "\u5f53\u524d\u4efb\u52a1\u770b\u677f\u5982\u4e0b\uff0c\u8bf7\u5ba1\u67e5\uff1a\n"
                        + "- \u662f\u5426\u9700\u8981\u8c03\u6574\u4efb\u52a1"
                        + "\uff08\u589e\u5220\u3001\u4fee\u6539\u3001\u8c03\u6574\u4f9d\u8d56\uff09\n"
                        + "- \u5c31\u7eea\u4efb\u52a1\u662f\u5426\u9700\u8981\u6307\u6d3e\u7ed9 teammate\n"
                        + "- \u6574\u4f53\u8fdb\u5ea6\u662f\u5426\u7b26\u5408\u9884\u671f");
        cn.put("dispatcher.teammate_task_list",
                "\u5f53\u524d\u4efb\u52a1\u5217\u8868\u5982\u4e0b\uff1a\n"
                        + "- \u8bf7\u8ba4\u9886\u9002\u5408\u4f60\u9886\u57df\u7684\u5f85\u9886\u53d6\u4efb\u52a1\n"
                        + "- \u4e86\u89e3\u76f8\u5173\u4efb\u52a1\u7684\u6267\u884c\u60c5\u51b5\uff0c"
                        + "\u5fc5\u8981\u65f6\u4e0e\u4ed6\u4eec\u534f\u8c03\u914d\u5408");
        cn.put("dispatcher.task_unassigned_marker", " (\u5f85\u9886\u53d6)");
        cn.put("dispatcher.stale_pending_header",
                "[\u50ac\u4fc3\u5efa\u8bae] \u4ee5\u4e0b\u4efb\u52a1\u5df2\u957f\u65f6"
                        + "\u95f4\u5904\u4e8e pending \u72b6\u6001\u672a\u88ab\u8ba4\u9886\uff0c"
                        + "\u8bf7\u8bc4\u4f30\u6bcf\u4e2a\u4efb\u52a1\u6700\u9002\u5408\u54ea\u4f4d\u6210\u5458\uff0c"
                        + "\u5e76\u901a\u8fc7 send_message \u5de5\u5177\u70b9\u540d"
                        + "\u5bf9\u65b9\u8ba9\u5176\u4f7f\u7528 claim_task \u8ba4\u9886\uff1a");
        cn.put("hitt.human_agent_display_name", "\u4eba\u7c7b\u6210\u5458");
        cn.put("hitt.human_agent_default_persona",
                "\u56e2\u961f\u4e2d\u7684\u4eba\u7c7b\u534f\u4f5c\u8005\u3002\u4e0e "
                        + "leader\u3001teammate \u5730\u4f4d\u5e73\u7b49\uff1b\u7531\u771f\u5b9e"
                        + "\u64cd\u4f5c\u8005\u9a71\u52a8\uff0c\u53ef\u63a5\u6536\u4efb\u52a1\u3001"
                        + "\u56de\u590d\u6d88\u606f\u3001\u53c2\u4e0e\u534f\u4f5c\u3002");
        cn.put("hitt.human_agent_spawned",
                "[\u6210\u5458\u4e8b\u4ef6] \u4eba\u7c7b\u6210\u5458 human_agent \u5df2\u52a0\u5165\u56e2\u961f");
        CN_STRINGS = Collections.unmodifiableMap(cn);

        Map<String, String> en = new LinkedHashMap<>();
        en.put("blueprint.default_persona", "Genius project management expert");
        en.put("team.shutdown_request_content", "All tasks are complete. Please wrap up and exit.");
        en.put("team.cancel_request_content",
                "The current task has changed. Stop executing it and try claiming a suitable task again.");
        en.put("dispatcher.member_online", "[Member Event] Member {0} is online");
        en.put("dispatcher.member_restarted", "[Member Event] Member {0} restarted (attempt {1})");
        en.put("dispatcher.member_status_changed", "[Member Event] Member {0} status changed: {1} \u2192 {2}");
        en.put("dispatcher.member_execution_changed",
                "[Member Event] Member {0} execution status changed: {1} \u2192 {2}");
        en.put("dispatcher.member_shutdown", "[Member Event] Member {0} has shut down");
        en.put("dispatcher.member_canceled", "[Member Event] Member {0} has been canceled");
        en.put("dispatcher.stale_claim_header",
                "Detected {0} task(s) you claimed that have been open for over 10 minutes. Please push forward:");
        en.put("dispatcher.stale_claim_self",
                "[Nudge] Your claimed task [{0}] {1} has been open for over 10 mins. Please continue: {2}");
        en.put("dispatcher.task_assigned_to_self", "[Task Assigned] Task [{0}] has been assigned to you. "
                + "Use view_task to inspect the details and start working on it.");
        en.put("dispatcher.msg_type_broadcast", "broadcast");
        en.put("dispatcher.msg_type_direct", "direct message");
        en.put("dispatcher.msg_received", "[Received {0}] message_id={1}, " + "from: {2}\n" + "content: {3}\n"
                + "tip: If the sender is asking or waiting for a reply, make sure to reply to {2} via send_message");
        en.put("dispatcher.all_done_persistent", "All tasks are complete. Please summarize this round''s results. "
                + "The team remains running and awaits new task instructions.");
        en.put("dispatcher.all_done_temporary",
                "All tasks are complete. Summarize the team''s work, "
                        + "then call shutdown_member for each member in turn, "
                        + "wait until all members reach status shutdown, "
                        + "and finally call clean_team to disband the team.");
        en.put("dispatcher.leader_task_board",
                "Current task board \u2014 please review:\n"
                        + "- Whether any tasks need adjustment (add/remove/edit/dependencies)\n"
                        + "- Whether ready tasks should be assigned to a teammate\n"
                        + "- Whether the overall progress matches expectations");
        en.put("dispatcher.teammate_task_list", "Current task list:\n" + "- Claim pending tasks that fit your domain\n"
                + "- Know who is working on related tasks and coordinate when needed");
        en.put("dispatcher.task_unassigned_marker", " (unassigned)");
        en.put("dispatcher.stale_pending_header",
                "[Nudge suggestion] The following tasks have been pending unclaimed for a long time. "
                        + "Decide which member fits each task best, then use send_message to call them out "
                        + "and ask them to claim via claim_task:");
        en.put("hitt.human_agent_display_name", "Human Member");
        en.put("hitt.human_agent_default_persona",
                "The human collaborator on the team. Equal in standing with "
                        + "the leader and teammates; driven by a real operator, can "
                        + "receive tasks, reply to messages, and collaborate.");
        en.put("hitt.human_agent_spawned", "[Member Event] Human member 'human_agent' joined the team");
        EN_STRINGS = Collections.unmodifiableMap(en);

        Map<Language, Map<String, String>> all = new LinkedHashMap<>();
        all.put(Language.CN, CN_STRINGS);
        all.put(Language.EN, EN_STRINGS);
        STRINGS = Collections.unmodifiableMap(all);
    }

    /**
     * I18n.
     * 
     * @since 0.1.7
     */
    private I18n() {
    }

    /**
     * setLanguage.
     * 
     * @param lang lang
     * @since 0.1.7
     */
    public static void setLanguage(Language lang) {
        if (lang == null || !STRINGS.containsKey(lang)) {
            throw new IllegalArgumentException("Unsupported language '" + lang + "'. Supported: " + STRINGS.keySet());
        }
        currentLanguage = lang;
    }

    /**
     * getLanguage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Language getLanguage() {
        return currentLanguage;
    }

    /**
     * t.
     * 
     * @param key key
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    public static String t(String key, Object... args) {
        Map<String, String> table = STRINGS.get(currentLanguage);
        if (!table.containsKey(key)) {
            throw new IllegalArgumentException("Missing i18n key '" + key + "' for language '" + currentLanguage + "'");
        }
        String raw = table.get(key);
        if (args.length == 0) {
            return raw;
        }
        return formatMessage(raw, args);
    }

    /**
     * formatMessage.
     * 
     * @param template template
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    private static String formatMessage(String template, Object... args) {
        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", args[i] != null ? args[i].toString() : "null");
        }
        return result;
    }
}
