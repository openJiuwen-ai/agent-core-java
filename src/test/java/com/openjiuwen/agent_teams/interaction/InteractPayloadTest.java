/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InteractPayloadTest {

    @Test
    void operatorMessageDefaultsTargetToNull() {
        OperatorMessage message = new OperatorMessage("hello");

        assertThat(message.body()).isEqualTo("hello");
        assertThat(message.target()).isNull();
    }

    @Test
    void humanAgentMessageDefaultsTargetToNull() {
        HumanAgentMessage message = new HumanAgentMessage("body", "alice");

        assertThat(message.body()).isEqualTo("body");
        assertThat(message.sender()).isEqualTo("alice");
        assertThat(message.target()).isNull();
    }

    @Test
    void deliverResultSuccessPreservesOptionalMessageId() {
        DeliverResult withoutMessageId = DeliverResult.success();
        DeliverResult withMessageId = DeliverResult.success("msg-1");

        assertThat(withoutMessageId.ok()).isTrue();
        assertThat(withoutMessageId.messageId()).isNull();
        assertThat(withoutMessageId.reason()).isNull();
        assertThat(withMessageId.ok()).isTrue();
        assertThat(withMessageId.messageId()).isEqualTo("msg-1");
        assertThat(withMessageId.reason()).isNull();
    }

    @Test
    void deliverResultFailureCarriesStableReasonToken() {
        DeliverResult result = DeliverResult.failure("unknown_human_agent");

        assertThat(result.ok()).isFalse();
        assertThat(result.messageId()).isNull();
        assertThat(result.reason()).isEqualTo("unknown_human_agent");
    }

    @Test
    void interactPayloadSealedHierarchyAcceptsAllSupportedShapes() {
        assertThat((InteractPayload) new GodViewMessage("leader")).isInstanceOf(GodViewMessage.class);
        assertThat((InteractPayload) new OperatorMessage("body", "member"))
                .isInstanceOf(OperatorMessage.class);
        assertThat((InteractPayload) new HumanAgentMessage("body", "sender", "*"))
                .isInstanceOf(HumanAgentMessage.class);
    }
}
