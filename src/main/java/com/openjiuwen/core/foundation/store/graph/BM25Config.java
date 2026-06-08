/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

/**
 * Graph Database BM25 Options.
 * <p>
 * Mirrors Python's {@code BM25Config} in
 * {@code openjiuwen/core/foundation/store/graph/database_config.py}.
 */
public class BM25Config {

    private double bm25B;
    private double bm25K1;

    public BM25Config() {
        this(0.75d, 1.2d);
    }

    public BM25Config(double bm25B, double bm25K1) {
        setBm25B(bm25B);
        setBm25K1(bm25K1);
    }

    public double getBm25B() {
        return bm25B;
    }

    public void setBm25B(double bm25B) {
        if (bm25B < 0.0d || bm25B > 1.0d) {
            throw new IllegalArgumentException("bm25B must be in [0, 1], got " + bm25B);
        }
        this.bm25B = bm25B;
    }

    public double getBm25K1() {
        return bm25K1;
    }

    public void setBm25K1(double bm25K1) {
        if (bm25K1 < 0.0d) {
            throw new IllegalArgumentException("bm25K1 must be >= 0, got " + bm25K1);
        }
        this.bm25K1 = bm25K1;
    }
}
