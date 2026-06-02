/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.rail;

import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.ModelBackupRail;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_model_backup_rail.py} in
 * {@code tests/system_tests/rail/test_model_backup_rail.py}.
 */
public class TestModelBackupRail {

    @Test
    void testMiddlewareExecutesWhenReactAgentInvoke() {
        RecordingAgent agent = new RecordingAgent();
        Object backupModel = new Object();
        ModelBackupRail rail = new ModelBackupRail(List.of(backupModel));
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(agent)
                .exception(new RuntimeException("primary model failed"))
                .build();

        rail.onModelException(ctx);

        assertThat(agent.models).containsExactly(backupModel);
        assertThat(ctx.getRetryRequest()).isNotNull();
        assertThat(ctx.getRetryRequest().getDelaySeconds()).isZero();
    }

    static final class RecordingAgent {
        private final List<Object> models = new ArrayList<>();

        public void setLlm(Object model) {
            models.add(model);
        }
    }
}
