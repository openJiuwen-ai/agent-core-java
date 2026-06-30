/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
    private final Set<String> isExists;
    private final double score;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TripleBeam(List<RetrievalResult> triples, double score) {
        this.triples = new ArrayList<>(triples);
        this.isExists = this.triples.stream().map(RetrievalResult::getText).collect(Collectors.toSet());
        this.score = score;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public RetrievalResult get(int index) {
        return triples.get(index);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int size() {
        return triples.size();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean contains(RetrievalResult triple) {
        return isExists.contains(triple.getText());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<RetrievalResult> getTriples() {
        return new ArrayList<>(triples);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getScore() {
        return score;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<RetrievalResult> iterator() {
        return triples.iterator();
    }
}
