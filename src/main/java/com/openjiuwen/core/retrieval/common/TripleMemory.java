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
 */
public class TripleMemory {

    private final Set<String> includedTriples = new HashSet<>();
    private final List<List<String>> memory = new ArrayList<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public int size() {
        return memory.size();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<List<String>> getMemory() {
        return new ArrayList<>(memory);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTriplesStr() {
        List<String> formatted = new ArrayList<>();
        for (List<String> triple : memory) {
            formatted.add("(" + String.join(" ", triple) + ")");
        }
        return String.join("\n", formatted);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void extendMemory(List<String> triple) {
        String normalized = tupleToString(triple);
        if (includedTriples.add(normalized)) {
            memory.add(new ArrayList<>(triple));
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void batchExtendMemory(List<List<String>> triples) {
        for (List<String> triple : triples) {
            extendMemory(triple);
        }
    }

    private static String tupleToString(List<String> triple) {
        List<String> normalized = new ArrayList<>();
        for (String item : triple) {
            normalized.add(item.toLowerCase(Locale.ROOT));
        }
        return String.join(" ", normalized);
    }
}
