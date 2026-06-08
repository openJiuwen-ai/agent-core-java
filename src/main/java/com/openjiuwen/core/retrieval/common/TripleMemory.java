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
 * Mirrors Python's {@code TripleMemory} in
 * {@code openjiuwen/core/retrieval/common/triple_memory.py}.
 */
public class TripleMemory {

    private final Set<String> includedTriples = new HashSet<>();
    private final List<List<String>> memory = new ArrayList<>();

    public int size() {
        return memory.size();
    }

    public List<List<String>> getMemory() {
        List<List<String>> copy = new ArrayList<>();
        for (List<String> triple : memory) {
            copy.add(new ArrayList<>(triple));
        }
        return copy;
    }

    public String getTriplesStr() {
        List<String> formatted = new ArrayList<>();
        for (List<String> triple : memory) {
            formatted.add("(" + String.join(" ", triple) + ")");
        }
        return String.join("\n", formatted);
    }

    public void extendMemory(List<String> newTriple) {
        String normalized = tupleToString(newTriple);
        if (includedTriples.add(normalized)) {
            memory.add(new ArrayList<>(newTriple));
        }
    }

    public void batchExtendMemory(List<List<String>> newTriples) {
        for (List<String> triple : newTriples) {
            extendMemory(triple);
        }
    }

    public static String tupleToString(List<String> newTriple) {
        List<String> lowered = new ArrayList<>();
        for (String item : newTriple) {
            lowered.add(item.toLowerCase(Locale.ROOT));
        }
        return String.join(" ", lowered);
    }
}
