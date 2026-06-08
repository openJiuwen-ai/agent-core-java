/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.search;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryManageSearchPackageMarkerTest {

    @Test
    void memoryManageSearchRemainsMarkerOnly() throws Exception {
        Constructor<MemoryManageSearchPackageMarker> constructor =
                MemoryManageSearchPackageMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MemoryManageSearchPackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
