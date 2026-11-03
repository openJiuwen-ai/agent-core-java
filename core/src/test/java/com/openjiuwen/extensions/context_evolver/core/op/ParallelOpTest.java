/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.op;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelOpTest {

    @Test
    void asyncExecuteRunsAllOperationsAgainstSharedContext() {
        RuntimeContext context = new RuntimeContext();
        ParallelOp parallel = new ParallelOp(
                new SetValueOp("a", 1),
                new SetValueOp("b", 2)
        );

        parallel.asyncExecute(context).join();

        assertThat(context.get("a")).isEqualTo(1);
        assertThat(context.get("b")).isEqualTo(2);
    }

    @Test
    void parallelWithFlattensNestedParallelOperations() {
        SetValueOp first = new SetValueOp("a", 1);
        SetValueOp second = new SetValueOp("b", 2);
        SetValueOp third = new SetValueOp("c", 3);

        ParallelOp combined = new ParallelOp(first).parallelWith(new ParallelOp(second, third));

        assertThat(combined.getOps()).containsExactly(first, second, third);
        assertThat(combined.toString()).isEqualTo("(SetValueOp(key=a, value=1) | SetValueOp(key=b, value=2) | SetValueOp(key=c, value=3))");
    }

    private static final class SetValueOp extends BaseOp {
        private final String key;
        private final Object value;

        private SetValueOp(String key, Object value) {
            super(orderedParams(key, value));
            this.key = key;
            this.value = value;
        }

        @Override
        public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
            context.set(key, value);
            return CompletableFuture.completedFuture(null);
        }

        private static Map<String, Object> orderedParams(String key, Object value) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("key", key);
            params.put("value", value);
            return params;
        }
    }
}
