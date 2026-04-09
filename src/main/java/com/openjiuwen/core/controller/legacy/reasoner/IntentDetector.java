/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.controller.legacy.reasoner;

import com.openjiuwen.core.controller.legacy.IntentDetectionController;
import com.openjiuwen.core.controller.legacy.config.ReasonerConfig;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.session.Session;

/**
 * Legacy intent detector contract.
 */
public interface IntentDetector {

    IntentDetectionController.Intent detect(Event event, Session session, ReasonerConfig config);
}
