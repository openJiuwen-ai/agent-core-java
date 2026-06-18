/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageSpec;
import com.openjiuwen.auto_harness.stages.BaseStage;
import com.openjiuwen.auto_harness.stages.SessionStage;
import com.openjiuwen.auto_harness.stages.TaskStage;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.stages.base} in
 * {@code openjiuwen/auto_harness/stages/base.py}.</p>
 */
class TestBaseStage {

    @Test
    void specIncludesSlotScopeAndCopiedCollections() {
        DemoStage stage = new DemoStage();

        StageSpec spec = stage.spec();

        assertThat(spec.getName()).isEqualTo("demo");
        assertThat(spec.getStageCls()).isEqualTo(DemoStage.class);
        assertThat(spec.getDescription()).isEqualTo("Demo stage");
        assertThat(spec.getConsumes()).containsExactly("input");
        assertThat(spec.getProduces()).containsExactly("output");
        assertThat(spec.getScope()).isEqualTo("session");
        assertThat(spec.getSlot()).isEqualTo("plan");
        assertThat(stage.consumes()).containsExactly("input");
        assertThat(stage.produces()).containsExactly("output");
    }

    @Test
    void sessionAndTaskStageScopesMatchPythonDefaults() {
        assertThat(new DemoSessionStage().scope()).isEqualTo("session");
        assertThat(new DemoTaskStage().scope()).isEqualTo("task");
    }

    @Test
    void scopeOutputEventStageMatchesPythonGuardBehavior() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", "hello");
        OutputSchema message = new OutputSchema("message", 0, payload);

        Object scoped = BaseStage.scopeOutputEventStage(message, "plan");

        assertThat(scoped).isInstanceOf(OutputSchema.class);
        assertThat(((OutputSchema) scoped).getPayload()).isEqualTo(Map.of("content", "hello", "stage", "plan"));
        assertThat(message.getPayload()).isEqualTo(Map.of("content", "hello"));

        OutputSchema alreadyScoped = new OutputSchema("message", 0, new LinkedHashMap<>(Map.of("stage", "plan")));
        assertThat(BaseStage.scopeOutputEventStage(alreadyScoped, "plan")).isSameAs(alreadyScoped);

        OutputSchema progress = new OutputSchema("progress", 0, payload);
        assertThat(BaseStage.scopeOutputEventStage(progress, "plan")).isSameAs(progress);

        OutputSchema nonMapPayload = new OutputSchema("message", 0, "text");
        assertThat(BaseStage.scopeOutputEventStage(nonMapPayload, "plan")).isSameAs(nonMapPayload);

        assertThat(BaseStage.scopeOutputEventStage("plain", "plan")).isEqualTo("plain");
        assertThat(BaseStage.scopeOutputEventStage(message, "")).isSameAs(message);
    }

    private static final class DemoStage extends BaseStage {
        @Override
        public String name() {
            return "demo";
        }

        @Override
        public String description() {
            return "Demo stage";
        }

        @Override
        public String slot() {
            return "plan";
        }

        @Override
        public List<String> consumes() {
            return List.of("input");
        }

        @Override
        public List<String> produces() {
            return List.of("output");
        }

        @Override
        public Iterator<Object> stream(BaseExecutionContext ctx) {
            return List.of().iterator();
        }
    }

    private static final class DemoSessionStage extends SessionStage {
    }

    private static final class DemoTaskStage extends TaskStage {
    }
}
