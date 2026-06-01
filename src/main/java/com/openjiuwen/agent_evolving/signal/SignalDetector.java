/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import java.util.Set;

/**
 * Backward-compatible alias for {@link ConversationSignalDetector}.
 *
 * <p>Mirrors Python's {@code SignalDetector} alias in
 * {@code openjiuwen.agent_evolving.signal.from_conv}.
 */
public class SignalDetector extends ConversationSignalDetector {

    public SignalDetector() {
        super();
    }

    public SignalDetector(Set<String> existingSkills) {
        super(existingSkills);
    }
}
