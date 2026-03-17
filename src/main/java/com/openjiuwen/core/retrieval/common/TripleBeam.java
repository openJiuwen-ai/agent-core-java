/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Beam of retrieval triples.
 */
public class TripleBeam implements Iterable<RetrievalResult> {

    private final List<RetrievalResult> triples;
    private final Set<String> exists;
    private final double score;

    public TripleBeam(List<RetrievalResult> triples, double score) {
        this.triples = new ArrayList<>(triples);
        this.exists = this.triples.stream().map(RetrievalResult::getText).collect(Collectors.toSet());
        this.score = score;
    }

    public RetrievalResult get(int index) {
        return triples.get(index);
    }

    public int size() {
        return triples.size();
    }

    public boolean contains(RetrievalResult triple) {
        return exists.contains(triple.getText());
    }

    public List<RetrievalResult> getTriples() {
        return new ArrayList<>(triples);
    }

    public double getScore() {
        return score;
    }

    @Override
    public Iterator<RetrievalResult> iterator() {
        return triples.iterator();
    }
}
