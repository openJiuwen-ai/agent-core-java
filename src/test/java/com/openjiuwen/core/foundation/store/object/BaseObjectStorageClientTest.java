/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.object;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseObjectStorageClientTest {

    @Test
    void abstractApiMatchesCurrentPythonModule() {
        Map<String, Method> methods = Arrays.stream(BaseObjectStorageClient.class.getDeclaredMethods())
                .collect(Collectors.toMap(Method::getName, method -> method));

        assertEquals(
                Set.of(
                        "uploadFile",
                        "downloadFile",
                        "deleteObject",
                        "createBucket",
                        "deleteBucket",
                        "listObjects"
                ),
                methods.keySet()
        );

        methods.values().forEach(method -> assertTrue(Modifier.isAbstract(method.getModifiers())));

        assertEquals(boolean.class, methods.get("uploadFile").getReturnType());
        assertEquals(boolean.class, methods.get("downloadFile").getReturnType());
        assertEquals(boolean.class, methods.get("deleteObject").getReturnType());
        assertEquals(boolean.class, methods.get("createBucket").getReturnType());
        assertEquals(boolean.class, methods.get("deleteBucket").getReturnType());
        assertEquals(List.class, methods.get("listObjects").getReturnType());

        assertEquals(3, methods.get("uploadFile").getParameterCount());
        assertEquals(3, methods.get("downloadFile").getParameterCount());
        assertEquals(2, methods.get("deleteObject").getParameterCount());
        assertEquals(2, methods.get("createBucket").getParameterCount());
        assertEquals(1, methods.get("deleteBucket").getParameterCount());
        assertEquals(3, methods.get("listObjects").getParameterCount());
    }
}
