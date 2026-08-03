/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.multimodal;

import java.util.List;

/**
 * Package facade for multimodal tools.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools.multimodal} in
 * {@code openjiuwen/harness/tools/multimodal/__init__.py}.</p>
 */
public final class MultimodalPackage {

    private MultimodalPackage() {
    }

    public static List<String> exportedSymbols() {
        return List.of("AudioTools", "VisionTools", "VideoUnderstandingTool");
    }
}
