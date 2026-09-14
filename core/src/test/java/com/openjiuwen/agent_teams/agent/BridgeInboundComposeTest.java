package com.openjiuwen.agent_teams.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BridgeInboundComposeTest {

    @Test
    void shouldComposeChineseBridgeMessageByDefault() {
        String result = BridgeInboundCompose.composeBridgeInbound("alice", "原始消息", "执行结果");

        assertThat(result).isEqualTo(
                "[来自团队成员 alice 的消息]\n"
                        + "原始消息\n\n"
                        + "[外部执行者的执行结果（要原样回传给团队的内容）]\n"
                        + "执行结果\n\n"
                        + "你的工作：仅做调度。决定是否使用 send_message 把上述执行结果"
                        + "原样回传给 alice，是否需要调用 claim_task / "
                        + "member_complete_task 等任务管理工具，或保持沉默。"
                        + "**不要改写或综合**执行结果的内容——原样转发即可。"
                        + "注意：原消息已自动转发给外部执行者，无需再调用 send_message 转发原消息。"
        );
    }

    @Test
    void shouldComposeEnglishBridgeMessageWithTimeHeader() {
        String result = BridgeInboundCompose.composeBridgeInbound(
                "bob",
                "Original body",
                "Remote reply",
                "en",
                "2026-06-06 10:55 (+2m)"
        );

        assertThat(result).isEqualTo(
                "[Team message from bob · 2026-06-06 10:55 (+2m)]\n"
                        + "Original body\n\n"
                        + "[Remote executor's output — relay this verbatim back to the team]\n"
                        + "Remote reply\n\n"
                        + "Your job: schedule only. Decide whether to send_message "
                        + "the remote output above back to bob verbatim, "
                        + "whether to call claim_task / member_complete_task, or "
                        + "whether to stay silent. Do NOT rewrite or synthesize the "
                        + "remote output — pass it through as-is. The original "
                        + "message has already been forwarded to the remote; do NOT "
                        + "call send_message to forward it again."
        );
    }
}
