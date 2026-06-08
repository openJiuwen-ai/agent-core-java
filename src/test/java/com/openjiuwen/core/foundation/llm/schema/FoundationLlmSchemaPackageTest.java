/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoundationLlmSchemaPackageTest {

    @Test
    void exportsMatchPythonPackageSurface() {
        assertEquals("openjiuwen/core/foundation/llm/schema/__init__.py",
                FoundationLlmSchemaPackage.PYTHON_MODULE);
        assertEquals(ImageGenerationResponse.class, FoundationLlmSchemaPackage.IMAGE_GENERATION_RESPONSE);
        assertEquals(AudioGenerationResponse.class, FoundationLlmSchemaPackage.AUDIO_GENERATION_RESPONSE);
        assertEquals(VideoGenerationResponse.class, FoundationLlmSchemaPackage.VIDEO_GENERATION_RESPONSE);
    }
}
