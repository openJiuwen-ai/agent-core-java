/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OnlineRLConfigTest {

    @Test
    void validateSyncsJudgeModelAndAcceptsCompletePorts() {
        OnlineRLConfig config = new OnlineRLConfig();
        config.getInference().setModelPath("/models/inference");
        config.getInference().setModelName("model-a");
        config.getInference().setPort(9001);
        config.getJudge().setPort(9002);
        config.getGateway().setPort(9003);
        config.getGateway().setRedisUrl("redis://localhost:6379/0");
        config.getJiuwen().setAgentServerPort(9004);
        config.getJiuwen().setWsPort(9005);
        config.getJiuwen().setWebPort(9006);

        config.validate();

        assertThat(config.getJudge().getModelPath()).isEqualTo("/models/inference");
        assertThat(config.getJudge().getModelName()).isEqualTo("model-a");
    }

    @Test
    void validateRejectsMissingGatewayRedisAndOverlayContainsExpectedKeys() {
        OnlineRLConfig config = new OnlineRLConfig();
        config.getInference().setPort(9001);
        config.getJudge().setPort(9002);
        config.getGateway().setPort(9003);
        config.getJiuwen().setAgentServerPort(9004);
        config.getJiuwen().setWsPort(9005);
        config.getJiuwen().setWebPort(9006);

        assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gateway.redisUrl");

        Map<String, Object> overlay = OnlinePpoVerlConfig.getOnlinePpoVerlHydraOverlay();
        assertThat(overlay).containsKeys("data", "algorithm", "actor_rollout_ref", "trainer", "reward_model", "JiuwenRL");
    }
}
