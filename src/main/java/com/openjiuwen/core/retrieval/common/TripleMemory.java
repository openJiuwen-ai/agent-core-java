/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deduplicated triple memory.
 * 
 * @since 0.1.7
 */
public class TripleMemory {
    private final Set<String> includedTriples = new HashSet<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<List<String>> memory = new ArrayList<>();

    /**
     * size.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int size() {
        return memory.size();
    }

    /**
     * getMemory.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<List<String>> getMemory() {
        return new ArrayList<>(memory);
    }

    /**
     * getTriplesStr.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTriplesStr() {
        List<String> formatted = new ArrayList<>();
        for (List<String> triple : memory) {
            formatted.add("(" + String.join(" ", triple) + ")");
        }
        return String.join("\n", formatted);
    }

    /**
     * extendMemory.
     * 
     * @param triple triple
     * @since 0.1.7
     */
    public void extendMemory(List<String> triple) {
        String normalized = tupleToString(triple);
        if (includedTriples.add(normalized)) {
            memory.add(new ArrayList<>(triple));
        }
    }

    /**
     * batchExtendMemory.
     * 
     * @param triples triples
     * @since 0.1.7
     */
    public void batchExtendMemory(List<List<String>> triples) {
        for (List<String> triple : triples) {
            extendMemory(triple);
        }
    }

    /**
     * tupleToString.
     * 
     * @param triple triple
     * @return the result
     * @since 0.1.7
     */
    private static String tupleToString(List<String> triple) {
        List<String> normalized = new ArrayList<>();
        for (String item : triple) {
            normalized.add(item.toLowerCase(Locale.ROOT));
        }
        return String.join(" ", normalized);
    }
}
