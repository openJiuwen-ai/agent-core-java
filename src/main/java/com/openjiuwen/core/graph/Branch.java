/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

/**
 * Branch condition for graph edges.
 * <p>
 * Mirrors Python's {@code Branch} dataclass from
 * <code>graph/graph.py</code>.
 *
 * @param <T> the condition result type
 */
public class Branch<T> {

    private final String source;
    private final String target;
    private final java.util.function.Function<T, Boolean> condition;

    public Branch(String source, String target, java.util.function.Function<T, Boolean> condition) {
        this.source = source;
        this.target = target;
        this.condition = condition;
    }

    public String getSource() { return source; }
    public String getTarget() { return target; }
    public java.util.function.Function<T, Boolean> getCondition() { return condition; }

    public boolean evaluate(T input) {
        return condition != null && condition.apply(input);
    }
}
