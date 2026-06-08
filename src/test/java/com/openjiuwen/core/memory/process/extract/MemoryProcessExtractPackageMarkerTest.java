/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryProcessExtractPackageMarkerTest {

    @Test
    void memoryProcessExtractRemainsMarkerOnly() throws Exception {
        Constructor<MemoryProcessExtractPackageMarker> constructor =
                MemoryProcessExtractPackageMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MemoryProcessExtractPackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
