/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public workflow component runtime state shell.
 *
 * <p>Mirrors Python's {@code ComponentState}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComponentState {
    private String compId;
    private Enum<?> status;
}
