/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.query;

/**
 * Minimal LSP location DTO.
 *
 * <p>Java-side supporting type for the query surface mirrored from Python's
 * harness LSP tools.
 */
public class LspLocation {

    private final String filePath;
    private final int line;
    private final int character;

    public LspLocation(String filePath, int line, int character) {
        this.filePath = filePath;
        this.line = line;
        this.character = character;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getLine() {
        return line;
    }

    public int getCharacter() {
        return character;
    }
}
