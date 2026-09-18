/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.op;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseOpTest {

    @Test
    void callExecutesAsyncExecuteAndReturnsSameContext() {
        RuntimeContext context = new RuntimeContext();
        RecordingOp op = new RecordingOp(Map.of("name", "demo"));

        RuntimeContext result = op.call(context).join();

        assertThat(result).isSameAs(context);
        assertThat(context.get("executed")).isEqualTo(true);
        assertThat(op.toString()).isEqualTo("RecordingOp(name=demo)");
    }

    @Test
    void serviceAccessorsReadFromServiceContext() {
        RecordingOp op = new RecordingOp(Map.of());
        op.context().registerService("llm", "llm-service");
        op.context().registerService("embedding_model", "embedding-service");
        op.context().registerService("vector_store", "vector-service");

        assertThat(op.getLlm()).isEqualTo("llm-service");
        assertThat(op.getEmbeddingModel()).isEqualTo("embedding-service");
        assertThat(op.getVectorStore()).isEqualTo("vector-service");
    }

    @Test
    void callPropagatesExecutionFailure() {
        BaseOp op = new BaseOp() {
            @Override
            public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
                return CompletableFuture.failedFuture(new IllegalStateException("boom"));
            }
        };

        assertThatThrownBy(() -> op.call(new RuntimeContext()).join()).hasMessageContaining("boom");
    }

    @Test
    void compositionHelpersCreateSequentialAndParallelOps() {
        RecordingOp first = new RecordingOp(Map.of("name", "first"));
        RecordingOp second = new RecordingOp(Map.of("name", "second"));

        assertThat(first.then(second)).isInstanceOf(SequentialOp.class);
        assertThat(first.parallelWith(second)).isInstanceOf(ParallelOp.class);
    }

    private static final class RecordingOp extends BaseOp {
        private RecordingOp(Map<String, Object> params) {
            super(new LinkedHashMap<>(params));
        }

        @Override
        public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
            context.set("executed", true);
            return CompletableFuture.completedFuture(null);
        }

        private ServiceContext context() {
            return getServiceContext();
        }
    }
}
