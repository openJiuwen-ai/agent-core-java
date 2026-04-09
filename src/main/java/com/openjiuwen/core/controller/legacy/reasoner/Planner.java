/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.controller.legacy.reasoner;

import com.openjiuwen.core.controller.legacy.IntentDetectionController;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.session.Session;

/**
 * Legacy task planner contract.
 */
public interface Planner {

    Task plan(IntentDetectionController.Intent intent, Session session);
}
