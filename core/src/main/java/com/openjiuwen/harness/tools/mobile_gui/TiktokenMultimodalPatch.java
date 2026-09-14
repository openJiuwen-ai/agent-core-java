/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

/**
 * Text-token fallback helpers for multimodal message blocks.
 *
 * <p>Mirrors Python's tiktoken patch module in
 * {@code openjiuwen/harness/tools/mobile_gui/tiktoken_multimodal_patch.py}.</p>
 */
public final class TiktokenMultimodalPatch {

    private static final int IMAGE_TOKEN_PLACEHOLDER = 85;

    private TiktokenMultimodalPatch() {
    }

    public static int estimateImageTokens() {
        return IMAGE_TOKEN_PLACEHOLDER;
    }

    public static int estimateTextTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}
