/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_payload.py} in
 * {@code tests/unit_tests/agent_teams/interaction/test_payload.py}.
 */
class InteractPayloadPythonParityTest {

    @Test
    void deliverResultSuccessCarriesMessageId() {
        DeliverResult result = DeliverResult.success("msg-1");

        assertThat(result.ok()).isTrue();
        assertThat(result.messageId()).isEqualTo("msg-1");
        assertThat(result.reason()).isNull();
    }

    @Test
    void deliverResultSuccessAllowsOmittedId() {
        DeliverResult result = DeliverResult.success();

        assertThat(result.ok()).isTrue();
        assertThat(result.messageId()).isNull();
    }

    @Test
    void deliverResultFailureCarriesReason() {
        DeliverResult result = DeliverResult.failure("send_failed");

        assertThat(result.ok()).isFalse();
        assertThat(result.reason()).isEqualTo("send_failed");
        assertThat(result.messageId()).isNull();
    }

    @Test
    void deliverResultIsFrozenRecord() {
        DeliverResult result = DeliverResult.success("msg-1");

        assertThat(result.getClass().isRecord()).isTrue();
        assertThat(allInstanceFieldsFinal(result.getClass())).isTrue();
    }

    @Test
    void godViewMessageCarriesBodyOnly() {
        GodViewMessage payload = new GodViewMessage("hello");

        assertThat(payload.body()).isEqualTo("hello");
        assertThat(payload.getClass().isRecord()).isTrue();
        assertThat(payload.getClass().getRecordComponents()).hasSize(1);
    }

    @Test
    void operatorMessageDefaultsTargetToNullForBroadcast() {
        OperatorMessage payload = new OperatorMessage("ping");

        assertThat(payload.target()).isNull();
        assertThat(payload.body()).isEqualTo("ping");
    }

    @Test
    void operatorMessageTargetsSpecificMember() {
        OperatorMessage payload = new OperatorMessage("hi", "dev-1");

        assertThat(payload.target()).isEqualTo("dev-1");
    }

    @Test
    void humanAgentMessageRequiresSender() {
        HumanAgentMessage payload = new HumanAgentMessage("ack", "human_pm");

        assertThat(payload.sender()).isEqualTo("human_pm");
        assertThat(payload.target()).isNull();
    }

    @Test
    void humanAgentMessageSupportsTarget() {
        HumanAgentMessage payload = new HumanAgentMessage("ack", "human_pm", "leader");

        assertThat(payload.target()).isEqualTo("leader");
    }

    @Test
    void payloadsAreFrozenRecords() {
        GodViewMessage payload = new GodViewMessage("x");

        assertThat(payload.getClass().isRecord()).isTrue();
        assertThat(allInstanceFieldsFinal(payload.getClass())).isTrue();
    }

    @Test
    void payloadInstanceDispatchWorks() {
        List<InteractPayload> payloads = List.of(
                new GodViewMessage("g"),
                new OperatorMessage("o"),
                new HumanAgentMessage("h", "ha")
        );
        List<String> matched = new ArrayList<>();

        for (InteractPayload payload : payloads) {
            if (payload instanceof GodViewMessage) {
                matched.add("god");
            } else if (payload instanceof HumanAgentMessage) {
                matched.add("human");
            } else if (payload instanceof OperatorMessage) {
                matched.add("operator");
            }
        }

        assertThat(matched).containsExactly("god", "operator", "human");
    }

    private static boolean allInstanceFieldsFinal(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                return false;
            }
        }
        return true;
    }
}
