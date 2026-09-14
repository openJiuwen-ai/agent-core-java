/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation;

import com.openjiuwen.core.foundation.prompt.assemble.PromptAssemblePackageMarker;
import com.openjiuwen.core.foundation.prompt.assemble.variables.PromptAssembleVariablesPackageMarker;
import com.openjiuwen.core.foundation.store.db.FoundationStoreDbPackageMarker;
import com.openjiuwen.core.foundation.store.object.FoundationStoreObjectPackageMarker;
import com.openjiuwen.core.foundation.store.vector.FoundationStoreVectorPackageMarker;
import com.openjiuwen.core.foundation.store.vector_fields.FoundationStoreVectorFieldsPackageMarker;
import com.openjiuwen.core.foundation.tool.auth.FoundationToolAuthPackageMarker;
import com.openjiuwen.core.foundation.tool.form_handler.FoundationToolFormHandlerPackageMarker;
import com.openjiuwen.core.foundation.tool.function.FoundationToolFunctionPackageMarker;
import com.openjiuwen.core.foundation.tool.mcp.FoundationToolMcpPackageMarker;
import com.openjiuwen.core.foundation.tool.mcp.client.FoundationToolMcpClientPackageMarker;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FoundationPackageMarkersTest {

    @Test
    void foundationPackageMarkersRemainMarkerOnly() throws Exception {
        for (Class<?> markerClass : List.of(
                CoreFoundationPackageMarker.class,
                PromptAssemblePackageMarker.class,
                PromptAssembleVariablesPackageMarker.class,
                FoundationStoreDbPackageMarker.class,
                FoundationStoreObjectPackageMarker.class,
                FoundationStoreVectorPackageMarker.class,
                FoundationStoreVectorFieldsPackageMarker.class,
                FoundationToolAuthPackageMarker.class,
                FoundationToolFormHandlerPackageMarker.class,
                FoundationToolFunctionPackageMarker.class,
                FoundationToolMcpPackageMarker.class,
                FoundationToolMcpClientPackageMarker.class
        )) {
            Constructor<?> constructor = markerClass.getDeclaredConstructor();
            assertThat(Modifier.isFinal(markerClass.getModifiers())).isTrue();
            assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        }
    }
}
