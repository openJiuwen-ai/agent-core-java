/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;

/**
 * Formats team mailbox messages before relaying them to a remote bridge agent.
 *
 * <p>Mirrors Python's {@code wrap_outbound_to_remote} in
 * {@code openjiuwen/agent_teams/agent/bridge_outbound_wrap.py}.</p>
 */
public final class BridgeOutboundWrap {

    private BridgeOutboundWrap() {
    }

    /**
     * Bridge mailbox injection mode.
     *
     * <p>Mirrors Python's {@code BridgeMailboxInjectMode} use in
     * {@code openjiuwen/agent_teams/agent/bridge_outbound_wrap.py}.</p>
     */
    public enum BridgeMailboxInjectMode {
        PASSTHROUGH("passthrough"),
        REPHRASE("rephrase");

        private final String value;

        BridgeMailboxInjectMode(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static String wrapOutboundToRemote(
            String sender,
            String senderDisplayName,
            TeamRole senderRole,
            String senderPersona,
            String body,
            boolean broadcast,
            String taskHint,
            BridgeMailboxInjectMode mode,
            String language
    ) {
        String display = senderDisplayName != null ? senderDisplayName : sender;
        String resolvedLanguage = language == null ? "cn" : language;
        if (mode == BridgeMailboxInjectMode.PASSTHROUGH) {
            return wrapPassthrough(display, body, broadcast, resolvedLanguage);
        }
        return wrapRephrase(display, senderRole, senderPersona, body, broadcast, taskHint, resolvedLanguage);
    }

    private static String wrapPassthrough(
            String senderLabel,
            String body,
            boolean broadcast,
            String language
    ) {
        if ("en".equals(language)) {
            String suffix = broadcast ? " (broadcast)" : "";
            return "[from " + senderLabel + suffix + "] " + body;
        }
        String suffix = broadcast ? "（广播）" : "";
        return "[来自 " + senderLabel + suffix + "] " + body;
    }

    private static String wrapRephrase(
            String senderLabel,
            TeamRole senderRole,
            String senderPersona,
            String body,
            boolean broadcast,
            String taskHint,
            String language
    ) {
        String roleValue = senderRole == null ? "unknown" : senderRole.value();
        String persona = senderPersona == null ? "" : senderPersona;
        if ("en".equals(language)) {
            String kind = broadcast ? "broadcast" : "direct";
            String header = "[from " + senderLabel
                    + " (role=" + roleValue
                    + ", persona=" + pythonRepr(persona)
                    + ", kind=" + kind + ")]";
            String suffix = taskHint == null ? "" : "\nRe: " + taskHint;
            return header + "\n" + body + suffix;
        }
        String kind = broadcast ? "广播" : "点对点";
        String header = "[来自 " + senderLabel
                + "（角色=" + roleValue
                + "，人设=" + pythonRepr(persona)
                + "，类型=" + kind + "）]";
        String suffix = taskHint == null ? "" : "\n相关任务：" + taskHint;
        return header + "\n" + body + suffix;
    }

    private static String pythonRepr(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
