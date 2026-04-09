  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.agent_evolving.trainer;

import java.util.List;
import java.util.Map;

/**
 * Predict result: model predictions plus their execution sessions.
 */
public record PredictionResult(List<Map<String, Object>> predictions, List<Object> sessions) {
}
