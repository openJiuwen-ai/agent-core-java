/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.security.BaseSecurityRail;
import com.openjiuwen.harness.rails.security.SafetyPromptRail;
import com.openjiuwen.harness.rails.security.SecurityCheckContext;
import com.openjiuwen.harness.rails.security.SecurityDecision;
import com.openjiuwen.harness.rails.security.SecurityInterrupt;
import com.openjiuwen.harness.rails.security.SecurityRail;
import com.openjiuwen.harness.rails.security.SecurityReject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.harness.rails.test_base_security_rail} in
 * {@code tests/unit_tests/harness/rails/test_base_security_rail.py}.</p>
 */
class BaseSecurityRailPythonParityTest {

    @Test
    void securityRailsExportExpectedNames() {
        assertThat(SafetyPromptRail.class).isAssignableTo(BaseSecurityRail.class);
        assertThat(SecurityRail.class.getSuperclass()).isEqualTo(SafetyPromptRail.class);
        assertThat(SafetyPromptRail.class.getSimpleName()).isEqualTo("SafetyPromptRail");
    }

    @Test
    void baseSecurityRailRegistersOnlySupportedEvents() {
        TestPromptRail rail = new TestPromptRail();

        assertThat(rail.getSupportedEvents()).containsExactly(BaseSecurityRail.BEFORE_MODEL_CALL);
    }

    @Test
    void modelRejectRequestsForceFinishEquivalent() {
        RejectModelRail rail = new RejectModelRail();
        CallbackContext ctx = modelContext();

        rail.beforeModelCall(ctx);

        assertThat(ctx.isRejected()).isTrue();
        assertThat(ctx.getRejectionMessage()).isEqualTo("blocked by test rail");
        assertThat(ctx.get("security_reject")).isInstanceOf(SecurityReject.class);
    }

    @Test
    void testPromptRailAllowsModelCall() {
        TestPromptRail rail = new TestPromptRail();
        CallbackContext ctx = modelContext();

        rail.beforeModelCall(ctx);

        assertThat(ctx.isRejected()).isFalse();
        assertThat(ctx.get("security_reject")).isNull();
    }

    @Test
    void securityInterruptOnModelEventAutoRejected() {
        ModelInterruptRail rail = new ModelInterruptRail();
        CallbackContext ctx = modelContext();

        rail.beforeModelCall(ctx);

        assertThat(ctx.isRejected()).isTrue();
        assertThat(ctx.getRejectionMessage()).contains("Should be auto-rejected");
    }

    private static CallbackContext modelContext() {
        return new CallbackContext(new DeepAgent(), Map.of());
    }

    private static class RejectModelRail extends BaseSecurityRail {
        RejectModelRail() {
            setSupportedEvents(Set.of(BEFORE_MODEL_CALL));
        }

        @Override
        protected SecurityDecision runSecurityCheck(SecurityCheckContext securityCtx) {
            return reject("blocked by test rail");
        }
    }

    private static final class TestPromptRail extends BaseSecurityRail {
        private TestPromptRail() {
            setSupportedEvents(Set.of(BEFORE_MODEL_CALL));
        }

        @Override
        protected SecurityDecision runSecurityCheck(SecurityCheckContext securityCtx) {
            return allow();
        }
    }

    private static final class ModelInterruptRail extends BaseSecurityRail {
        private ModelInterruptRail() {
            setSupportedEvents(Set.of(BEFORE_MODEL_CALL));
        }

        @Override
        protected SecurityDecision runSecurityCheck(SecurityCheckContext securityCtx) {
            return new SecurityInterrupt(
                    Map.of("message", "Should be auto-rejected"),
                    "test_interrupt"
            );
        }

        @Override
        protected void applySecurityDecision(SecurityCheckContext securityCtx, SecurityDecision decision) {
            if (decision instanceof SecurityInterrupt interrupt) {
                Object message = interrupt.request().get("message");
                securityCtx.callbackContext().reject(String.valueOf(message));
                return;
            }
            super.applySecurityDecision(securityCtx, decision);
        }
    }
}
