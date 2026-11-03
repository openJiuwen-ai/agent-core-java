/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.updater;

import com.openjiuwen.agentevolving.signal.EvolutionSignal;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.UpdateKey;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

class UpdaterTest {

    @Test
    void protocolShapeSupportsTupleKeyMappings() {
        Updater updater = new DemoUpdater();
        Map<String, Operator> operators = Map.of("demo", new DemoOperator());
        Object updateResult = updater.update(List.of(), List.of("case"), Map.of()).toCompletableFuture().join();
        Object processResult = updater.process(List.of(), List.of(new EvolutionSignal()), Map.of()).toCompletableFuture().join();
        @SuppressWarnings("unchecked")
        Map<UpdateKey, ?> updateMapping = (Map<UpdateKey, ?>) updateResult;

        assertThat(updater.bind(operators, List.of("target"), Map.of("limit", 1))).isEqualTo(1);
        assertThat(updater.requiresForwardData()).isTrue();
        assertThat(updateResult).isInstanceOf(Map.class);
        assertThat(updateMapping.keySet()).containsExactly(UpdateKey.of("demo", "target"));
        assertThat(processResult).isInstanceOf(List.class);
        assertThat(updater.getState()).containsEntry("bound", true);
    }

    private static final class DemoUpdater implements Updater {
        private Map<String, Object> state = Map.of("bound", false);

        @Override
        public int bind(Map<String, Operator> operators, List<String> targets, Map<String, Object> config) {
            state = Map.of("bound", !operators.isEmpty(), "targets", targets == null ? 0 : targets.size());
            return operators.size();
        }

        @Override
        public boolean requiresForwardData() {
            return true;
        }

        @Override
        public CompletionStage<Object> update(List<Trajectory> trajectories, List<Object> evaluatedCases, Map<String, Object> config) {
            return CompletableFuture.completedFuture(Map.of(UpdateKey.of("demo", "target"), "value"));
        }

        @Override
        public CompletionStage<Object> process(List<Trajectory> trajectories, List<EvolutionSignal> signals, Map<String, Object> config) {
            return CompletableFuture.completedFuture(List.of(Map.of(UpdateKey.of("demo", "target"), "value")));
        }

        @Override
        public Map<String, Object> getState() {
            return state;
        }

        @Override
        public void loadState(Map<String, Object> state) {
            this.state = state;
        }
    }

    private static final class DemoOperator extends Operator {
        @Override
        public String getOperatorId() {
            return "demo";
        }

        @Override
        public Map<String, TunableSpec> getTunables() {
            return Map.of();
        }

        @Override
        public Map<String, Object> getState() {
            return Map.of();
        }

        @Override
        public void setParameter(String target, Object value) {
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }
    }
}
