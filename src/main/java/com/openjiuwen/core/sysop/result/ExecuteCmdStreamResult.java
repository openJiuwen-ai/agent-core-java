/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Iterator;
import java.util.List;

/**
 * Result type for streaming shell command execution.
 */
@SuperBuilder
@NoArgsConstructor
public class ExecuteCmdStreamResult extends BaseResult<ExecuteCmdChunkData> implements Iterable<ExecuteCmdChunkData> {

    public ExecuteCmdStreamResult(int code, String message, ExecuteCmdChunkData data) {
        super(code, message, data);
    }

    @Override
    public Iterator<ExecuteCmdChunkData> iterator() {
        ExecuteCmdChunkData data = getData();
        return data == null ? List.<ExecuteCmdChunkData>of().iterator() : List.of(data).iterator();
    }
}
