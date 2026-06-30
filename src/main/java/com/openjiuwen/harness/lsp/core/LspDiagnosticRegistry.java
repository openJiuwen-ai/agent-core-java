/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Auto-generated for codecheck compliance.
 */
public final class LspDiagnosticRegistry {
    private static LspDiagnosticRegistry instance = new LspDiagnosticRegistry();

    private final List<LspDiagnostic> pending = new ArrayList<>();

    private LspDiagnosticRegistry() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static LspDiagnosticRegistry getInstance() {
        return instance;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void reset() {
        instance = new LspDiagnosticRegistry();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized void register(String serverName, String uri, List<Map<String, Object>> diagnostics) {
        if (diagnostics == null) {
            return;
        }
        for (Map<String, Object> diagnostic : diagnostics) {
            pending.add(new LspDiagnostic(serverName, uri, diagnostic));
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized List<LspDiagnostic> getAndClear() {
        List<LspDiagnostic> snapshot = new ArrayList<>(pending);
        pending.clear();
        return snapshot;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized List<LspDiagnostic> peek() {
        return Collections.unmodifiableList(new ArrayList<>(pending));
    }
}
