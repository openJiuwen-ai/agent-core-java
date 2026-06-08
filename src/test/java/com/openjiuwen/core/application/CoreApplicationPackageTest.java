/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoreApplicationPackageTest {

    @Test
    void coreApplicationPackageRemainsMarkerOnly() throws Exception {
        Constructor<CoreApplicationPackage> constructor = CoreApplicationPackage.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(CoreApplicationPackage.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
