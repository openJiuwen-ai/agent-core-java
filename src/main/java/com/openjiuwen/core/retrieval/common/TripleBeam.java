/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Mirrors Python's {@code TripleBeam} in
 * {@code openjiuwen/core/retrieval/common/triple_beam.py}.
 */
public class TripleBeam implements Iterable<RetrievalResult> {

    private final List<RetrievalResult> beam;
    private final Set<String> existTriples;
    private final double score;

    public TripleBeam(List<RetrievalResult> nodes, double score) {
        this.beam = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
        this.existTriples = new LinkedHashSet<>();
        for (RetrievalResult item : this.beam) {
            this.existTriples.add(item.getText());
        }
        this.score = score;
    }

    public RetrievalResult get(int index) {
        return beam.get(index);
    }

    public int size() {
        return beam.size();
    }

    public boolean contains(RetrievalResult triple) {
        return triple != null && existTriples.contains(triple.getText());
    }

    public List<RetrievalResult> getTriples() {
        return new ArrayList<>(beam);
    }

    public double getScore() {
        return score;
    }

    @Override
    public Iterator<RetrievalResult> iterator() {
        return beam.iterator();
    }
}
