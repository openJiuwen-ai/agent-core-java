/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.context.processor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Event emitted by a {@link ContextProcessor} describing what was modified.
 * <p>
 * Mirrors Python's {@code ContextEvent} from {@code processor/base.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextEvent {

    private String eventType;

    @Builder.Default
    private List<Integer> messagesToModify = new ArrayList<>();
}
