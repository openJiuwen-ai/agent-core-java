/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.runner.DistributedConfig;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Focused tests for server-adapter behavior.
 *
 * <p>Mirrors Python's {@code AgentAdapter} in
 * {@code openjiuwen/core/runner/drunner/server_adapter/agent_adapter.py} and
 * {@code MqServerAdapter} helpers in
 * {@code openjiuwen/core/runner/drunner/server_adapter/mq_server_adapter.py}.</p>
 */
class ServerAdapterTest {

    @AfterEach
    void resetRunnerConfig() {
        RunnerConfig.setRunnerConfig(null);
    }

    @Test
    void agentAdapterUsesConfiguredAgentTopicTemplate() {
        RunnerConfig.setRunnerConfig(RunnerConfig.builder()
                .distributedConfig(DistributedConfig.builder()
                        .agentTopicTemplate("custom.agent.{agent_id}.{version}")
                        .build())
                .build());

        AgentAdapter adapter = new AgentAdapter("agent-1", "v1", null);

        assertThat(adapter.getTopic()).isEqualTo("custom.agent.agent-1.v1");
        assertThat(adapter.isEnableA2a()).isFalse();
    }

    @Test
    void agentAdapterRequiresCardWhenA2aIsEnabled() {
        RunnerConfig.setRunnerConfig(RunnerConfig.builder()
                .enableA2a(true)
                .build());

        assertThatThrownBy(() -> new AgentAdapter("agent-1", "v1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agent_card is required when enable_a2a is True");
    }

    @Test
    void messageUtilsBuildsBatchResponseWithPythonFields() {
        DmqRequestMessage request = new DmqRequestMessage();
        request.setType(DMessageType.INPUT);
        request.setMessageId("msg-1");
        request.setSenderId("client-1");
        request.setReplyTopic("reply-topic");

        DmqResponseMessage response = MqMessageUtils.buildBatchResponse(
                request, "adapter-1", Map.of("ok", true));

        assertThat(response.getType()).isEqualTo(DMessageType.OUTPUT);
        assertThat(response.getMessageId()).isEqualTo("msg-1");
        assertThat(response.getSenderId()).isEqualTo("adapter-1");
        assertThat(response.getReceiverId()).isEqualTo("client-1");
        assertThat(response.getSeq()).isZero();
        assertThat(response.isLastChunk()).isTrue();
        assertThat(response.getBody()).isEqualTo(Map.of("ok", true));
    }
}
