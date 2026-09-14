/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.op;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class SequentialOpTest {

    @Test
    void asyncExecuteRunsOperationsInOrder() {
        List<String> order = new ArrayList<>();
        RuntimeContext context = new RuntimeContext();
        SequentialOp sequential = new SequentialOp(
                new OrderedOp("first", order),
                new OrderedOp("second", order),
                new OrderedOp("third", order)
        );

        sequential.asyncExecute(context).join();

        assertThat(order).containsExactly("first", "second", "third");
    }

    @Test
    void thenFlattensNestedSequentialOperations() {
        OrderedOp first = new OrderedOp("first", new ArrayList<>());
        OrderedOp second = new OrderedOp("second", new ArrayList<>());
        OrderedOp third = new OrderedOp("third", new ArrayList<>());

        SequentialOp combined = new SequentialOp(first).then(new SequentialOp(second, third));

        assertThat(combined.getOps()).containsExactly(first, second, third);
        assertThat(combined.toString())
                .isEqualTo("(OrderedOp(name=first) >> OrderedOp(name=second) >> OrderedOp(name=third))");
    }

    private static final class OrderedOp extends BaseOp {
        private final String name;
        private final List<String> order;

        private OrderedOp(String name, List<String> order) {
            super(Map.of("name", name));
            this.name = name;
            this.order = order;
        }

        @Override
        public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
            order.add(name);
            return CompletableFuture.completedFuture(null);
        }
    }
}
