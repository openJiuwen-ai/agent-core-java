  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Input data for BEFORE/AFTER_MODEL_CALL events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCallInputs implements EventInputs {
    @Builder.Default
    private List<Object> messages = new ArrayList<>();
    private List<ToolInfo> tools;
    private Object response;
}
