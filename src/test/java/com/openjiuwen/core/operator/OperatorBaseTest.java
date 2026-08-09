/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.operator;

import com.openjiuwen.agentevolving.ApplyResult;
import com.openjiuwen.agentevolving.Protocols;
import com.openjiuwen.agentevolving.UpdateValue;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestTunableSpec} and {@code TestOperator} in
 * {@code tests/unit_tests/core/operator/test_base.py}.
 */
class OperatorBaseTest {

    @Test
    void tunableSpecStoresAllParameters() {
        TunableSpec spec = new TunableSpec(
                "temperature",
                "continuous",
                "model.temperature",
                Map.of("min", 0.0, "max", 1.0)
        );

        assertThat(spec.name()).isEqualTo("temperature");
        assertThat(spec.kind()).isEqualTo("continuous");
        assertThat(spec.path()).isEqualTo("model.temperature");
        assertThat(spec.constraint()).isEqualTo(Map.of("min", 0.0, "max", 1.0));
    }

    @Test
    void tunableSpecSupportsMinimalParameters() {
        TunableSpec spec = new TunableSpec("prompt", "prompt", "prompt");

        assertThat(spec.name()).isEqualTo("prompt");
        assertThat(spec.kind()).isEqualTo("prompt");
        assertThat(spec.path()).isEqualTo("prompt");
        assertThat(spec.constraint()).isNull();
    }

    @Test
    void tunableSpecIsImmutableRecord() {
        assertThat(TunableSpec.class.isRecord()).isTrue();
        assertThat(TunableSpec.class.getDeclaredMethods())
                .extracting(Method::getName)
                .doesNotContain("setName", "setKind", "setPath", "setConstraint");
    }

    @Test
    void operatorClassIsAbstract() {
        assertThat(Modifier.isAbstract(Operator.class.getModifiers())).isTrue();
    }

    @Test
    void previewableOperatorClassIsAbstract() {
        assertThat(Modifier.isAbstract(PreviewableOperator.class.getModifiers())).isTrue();
    }

    @Test
    void operatorAbstractMethodsStayAbstract() throws NoSuchMethodException {
        assertThat(Modifier.isAbstract(Operator.class.getDeclaredMethod("getOperatorId").getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(Operator.class.getDeclaredMethod("getTunables").getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(Operator.class.getDeclaredMethod("getState").getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(Operator.class.getDeclaredMethod("setParameter", String.class, Object.class).getModifiers()))
                .isTrue();
        assertThat(Modifier.isAbstract(Operator.class.getDeclaredMethod("loadState", Map.class).getModifiers())).isTrue();
    }

    @Test
    void fakeOperatorExposesPromptTunableSpec() {
        FakeOperator operator = new FakeOperator("old");

        assertThat(operator.getTunables())
                .containsEntry("system_prompt", new TunableSpec("system_prompt", "prompt", "prompt"));
    }

    @Test
    void applyUpdateDelegatesReplaceStateToSetParameter() {
        FakeOperator operator = new FakeOperator("old");

        ApplyResult result = operator.applyUpdate(
                "system_prompt",
                UpdateValue.builder()
                        .payload("new")
                        .metadata(Map.of("source", "test"))
                        .build()
        );

        assertThat(operator.getStoredValue()).isEqualTo("new");
        assertThat(result.isApplied()).isTrue();
        assertThat(result.getOperatorId()).isEqualTo("fake/operator");
        assertThat(result.getTarget()).isEqualTo("system_prompt");
        assertThat(result.getValue()).isEqualTo("new");
        assertThat(result.getMode()).isEqualTo(Protocols.REPLACE_MODE);
        assertThat(result.getEffect()).isEqualTo(Protocols.STATE_EFFECT);
        assertThat(result.getMetadata()).containsEntry("source", "test");
    }

    @Test
    void applyUpdateReportsNoopWhenStateDoesNotChange() {
        FakeOperator operator = new FakeOperator("old");

        ApplyResult result = operator.applyUpdate("unknown_target", new UpdateValue("new"));

        assertThat(operator.getStoredValue()).isEqualTo("old");
        assertThat(result.isApplied()).isFalse();
    }

    @Test
    void applyUpdateRejectsUnsupportedModeOrEffect() {
        FakeOperator operator = new FakeOperator("old");

        ApplyResult result = operator.applyUpdate(
                "system_prompt",
                UpdateValue.builder()
                        .payload("append-me")
                        .mode(Protocols.APPEND_MODE)
                        .effect(Protocols.PENDING_CHANGE_EFFECT)
                        .build()
        );

        assertThat(operator.getStoredValue()).isEqualTo("old");
        assertThat(result.isApplied()).isFalse();
        assertThat(result.getErrors()).containsExactly(
                "unsupported update mode/effect for compatibility operator: append/pending_change"
        );
    }

    @Test
    void operatorDoesNotExposeInvokeOrStreamExecutionMethods() {
        assertThat(Operator.class.getDeclaredMethods())
                .extracting(Method::getName)
                .doesNotContain("invoke", "stream");
    }

    @Test
    void previewableOperatorRoutesApplyUpdateToPreviewUpdate() {
        FakePreviewableOperator operator = new FakePreviewableOperator();
        UpdateValue update = UpdateValue.builder()
                .payload(Map.of("delta", 1))
                .mode(Protocols.MERGE_MODE)
                .effect(Protocols.PENDING_CHANGE_EFFECT)
                .build();

        ApplyResult result = operator.applyUpdate("experience", update);

        assertThat(operator.previewInvoked()).isTrue();
        assertThat(result.getEffect()).isEqualTo(Protocols.PENDING_CHANGE_EFFECT);
        assertThat(result.getValue()).isEqualTo(update.getPayload());
    }

    private static final class FakeOperator extends Operator {
        private String storedValue;

        private FakeOperator(String storedValue) {
            this.storedValue = storedValue;
        }

        @Override
        public String getOperatorId() {
            return "fake/operator";
        }

        @Override
        public Map<String, TunableSpec> getTunables() {
            return Map.of("system_prompt", new TunableSpec("system_prompt", "prompt", "prompt"));
        }

        @Override
        public Map<String, Object> getState() {
            return Map.of("system_prompt", storedValue);
        }

        @Override
        public void setParameter(String target, Object value) {
            if ("system_prompt".equals(target)) {
                this.storedValue = String.valueOf(value);
            }
        }

        @Override
        public void loadState(Map<String, Object> state) {
            this.storedValue = String.valueOf(state.get("system_prompt"));
        }

        private String getStoredValue() {
            return storedValue;
        }
    }

    private static final class FakePreviewableOperator extends PreviewableOperator {
        private boolean previewInvoked;

        @Override
        public String getOperatorId() {
            return "preview/operator";
        }

        @Override
        public Map<String, TunableSpec> getTunables() {
            return Map.of();
        }

        @Override
        public Map<String, Object> getState() {
            return new LinkedHashMap<>();
        }

        @Override
        public void setParameter(String target, Object value) {
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }

        @Override
        public ApplyResult previewUpdate(String target, UpdateValue update) {
            previewInvoked = true;
            return ApplyResult.builder()
                    .operatorId(getOperatorId())
                    .target(target)
                    .applied(true)
                    .mode(update.getMode())
                    .effect(update.getEffect())
                    .value(update.getPayload())
                    .build();
        }

        private boolean previewInvoked() {
            return previewInvoked;
        }
    }
}
