/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.openjiuwen.core.memory.manage.mem_model.MemoryType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Memory information containing id, content, and type.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemInfo {
    @Builder.Default
    private String memId = "";
    @Builder.Default
    private String content = "";
    @Builder.Default
    private MemoryType type = MemoryType.USER_PROFILE;
}
