/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.prompts;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryPromptsPackageMarkerTest {

    @Test
    void memoryPromptsRemainsMarkerOnly() throws Exception {
        Constructor<MemoryPromptsPackageMarker> constructor =
                MemoryPromptsPackageMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MemoryPromptsPackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
