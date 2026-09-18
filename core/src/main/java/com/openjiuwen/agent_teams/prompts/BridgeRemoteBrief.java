/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds briefing text handed to a remote bridge agent via {@code adapter.connect}.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.prompts.bridge_remote_brief} in
 * {@code openjiuwen/agent_teams/prompts/bridge_remote_brief.py}.</p>
 */
public final class BridgeRemoteBrief {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/prompts/bridge_remote_brief.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "MemberSummary",
            "build_bridge_persona",
            "build_team_overview"
    );

    private BridgeRemoteBrief() {
    }

    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    public static String buildBridgePersona(String memberName, String persona) {
        return buildBridgePersona(memberName, persona, "cn");
    }

    public static String buildBridgePersona(String memberName, String persona, String language) {
        if ("en".equals(language)) {
            return "You are " + pyText(memberName) + " (persona: " + pyText(persona) + ").\n"
                    + "You are the EXECUTOR backing a bridge_agent member of the "
                    + "same name on a jiuwen team. Each turn you receive a message "
                    + "from the team, perform the requested work (code, analysis, "
                    + "answer, ...) and return the result as plain text. Your "
                    + "reply will be relayed VERBATIM back to the team by the "
                    + "bridge agent, so respond with the final result directly — "
                    + "no 'I suggest...' framing.\n"
                    + "You do NOT have tools and cannot observe team state. The "
                    + "bridge agent owns all team-facing actions (sending "
                    + "messages, claiming/completing tasks).";
        }
        return "你是 " + pyText(memberName) + "（人设：" + pyText(persona) + "）。\n"
                + "你是 jiuwen 团队中同名 bridge_agent 成员的**实际执行者**。"
                + "每次你将收到一段来自团队的消息文本，请直接**执行**对应工作"
                + "（如代码、分析、答案）并返回执行结果文本。你的回复会被 bridge "
                + "agent **原样**转交给团队，所以请直接给出最终结果，"
                + "不要使用[建议你这么做]之类的提示性语言。\n"
                + "你**没有工具**也无法感知团队内部状态——所有与团队的交互"
                + "（发送消息、认领/完成任务）由 bridge agent 完成。";
    }

    public static String buildTeamOverview(String teamName, Iterable<MemberSummary> members) {
        return buildTeamOverview(teamName, members, "cn");
    }

    public static String buildTeamOverview(String teamName, Iterable<MemberSummary> members, String language) {
        List<String> lines = new ArrayList<>();
        lines.add(overviewHeader(teamName, language));
        for (MemberSummary member : members) {
            lines.add(formatMemberLine(member, language));
        }
        lines.add(overviewFooter(language));
        return String.join("\n", lines);
    }

    static String overviewHeader(String teamName, String language) {
        if ("en".equals(language)) {
            return "Team " + pyText(teamName) + " roster:";
        }
        return "团队 " + pyText(teamName) + " 当前成员：";
    }

    static String formatMemberLine(MemberSummary member, String language) {
        String persona = member.persona();
        if (persona == null || persona.isEmpty()) {
            persona = "en".equals(language) ? "(no persona)" : "（无人设）";
        }
        return "- " + pyText(member.memberName()) + " (" + member.role().value() + "): " + persona;
    }

    static String overviewFooter(String language) {
        if ("en".equals(language)) {
            return "Use the above when crafting replies; do not assume any other team state.";
        }
        return "以上信息供你回答时参考；除此之外的团队状态请勿假设。";
    }

    private static String pyText(String value) {
        return value == null ? "None" : value;
    }
}
