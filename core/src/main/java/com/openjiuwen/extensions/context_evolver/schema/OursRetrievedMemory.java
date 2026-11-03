/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

/**
 * Mirrors Python's {@code OursRetrievedMemory} in
 * {@code openjiuwen/extensions/context_evolver/schema/io_schema.py}.
 */
public class OursRetrievedMemory extends ReMeRetrievedMemory {

    public OursRetrievedMemory() {
        super();
    }

    public OursRetrievedMemory(String whenToUse, String content) {
        super(whenToUse, content);
    }
}
