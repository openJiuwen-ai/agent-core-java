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
 * 
 * @since 0.1.7
 */
public class TripleBeam implements Iterable<RetrievalResult> {
    private final List<RetrievalResult> triples;
    private final Set<String> isExists;
    private final double score;

    /**
     * TripleBeam.
     * 
     * @param triples triples
     * @param score score
     * @since 0.1.7
     */
    public TripleBeam(List<RetrievalResult> triples, double score) {
        this.triples = new ArrayList<>(triples);
        this.isExists = this.triples.stream().map(RetrievalResult::getText).collect(Collectors.toSet());
        this.score = score;
    }

    /**
     * get.
     * 
     * @param index index
     * @return the result
     * @since 0.1.7
     */
    public RetrievalResult get(int index) {
        return triples.get(index);
    }

    /**
     * size.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int size() {
        return triples.size();
    }

    /**
     * contains.
     * 
     * @param triple triple
     * @return the result
     * @since 0.1.7
     */
    public boolean contains(RetrievalResult triple) {
        return isExists.contains(triple.getText());
    }

    /**
     * getTriples.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<RetrievalResult> getTriples() {
        return new ArrayList<>(triples);
    }

    /**
     * getScore.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getScore() {
        return score;
    }

    /**
     * iterator.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<RetrievalResult> iterator() {
        return triples.iterator();
    }
}
