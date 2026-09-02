/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * LspDiagnosticRegistry.
 * 
 * @since 0.1.7
 */
public final class LspDiagnosticRegistry {
    private static volatile LspDiagnosticRegistry instance = new LspDiagnosticRegistry();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<LspDiagnostic> pending = new ArrayList<>();

    /**
     * LspDiagnosticRegistry.
     * 
     * @since 0.1.7
     */
    private LspDiagnosticRegistry() {
    }

    /**
     * getInstance.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static LspDiagnosticRegistry getInstance() {
        return instance;
    }

    /**
     * reset.
     * 
     * @since 0.1.7
     */
    public static void reset() {
        instance = new LspDiagnosticRegistry();
    }

    /**
     * register.
     * 
     * @param serverName serverName
     * @param uri uri
     * @param diagnostics diagnostics
     * @since 0.1.7
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
     * getAndClear.
     * 
     * @return the result
     * @since 0.1.7
     */
    public synchronized List<LspDiagnostic> getAndClear() {
        List<LspDiagnostic> snapshot = new ArrayList<>(pending);
        pending.clear();
        return snapshot;
    }

    /**
     * peek.
     * 
     * @return the result
     * @since 0.1.7
     */
    public synchronized List<LspDiagnostic> peek() {
        return Collections.unmodifiableList(new ArrayList<>(pending));
    }
}
