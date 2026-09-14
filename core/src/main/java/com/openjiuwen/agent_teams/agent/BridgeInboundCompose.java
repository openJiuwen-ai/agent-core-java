/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

/**
 * Pure formatter for the bridge avatar's inbound DeepAgent context.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_teams.agent.bridge_inbound_compose} in
 * {@code openjiuwen/agent_teams/agent/bridge_inbound_compose.py}.
 */
public final class BridgeInboundCompose {

    private BridgeInboundCompose() {
    }

    public static String composeBridgeInbound(String originalSender, String originalBody, String remoteReply) {
        return composeBridgeInbound(originalSender, originalBody, remoteReply, "cn", null);
    }

    public static String composeBridgeInbound(
            String originalSender,
            String originalBody,
            String remoteReply,
            String language,
            String timeInfo
    ) {
        if ("en".equals(language)) {
            String header = "[Team message from " + originalSender + "]";
            if (timeInfo != null) {
                header = "[Team message from " + originalSender + " · " + timeInfo + "]";
            }
            return header + "\n"
                    + originalBody + "\n\n"
                    + "[Remote executor's output — relay this verbatim back to the team]\n"
                    + remoteReply + "\n\n"
                    + "Your job: schedule only. Decide whether to send_message "
                    + "the remote output above back to " + originalSender + " verbatim, "
                    + "whether to call claim_task / member_complete_task, or "
                    + "whether to stay silent. Do NOT rewrite or synthesize the "
                    + "remote output — pass it through as-is. The original "
                    + "message has already been forwarded to the remote; do NOT "
                    + "call send_message to forward it again.";
        }
        String header = "[来自团队成员 " + originalSender + " 的消息]";
        if (timeInfo != null) {
            header = "[来自团队成员 " + originalSender + " 的消息 · " + timeInfo + "]";
        }
        return header + "\n"
                + originalBody + "\n\n"
                + "[外部执行者的执行结果（要原样回传给团队的内容）]\n"
                + remoteReply + "\n\n"
                + "你的工作：仅做调度。决定是否使用 send_message 把上述执行结果"
                + "原样回传给 " + originalSender + "，是否需要调用 claim_task / "
                + "member_complete_task 等任务管理工具，或保持沉默。"
                + "**不要改写或综合**执行结果的内容——原样转发即可。"
                + "注意：原消息已自动转发给外部执行者，无需再调用 send_message 转发原消息。";
    }
}
