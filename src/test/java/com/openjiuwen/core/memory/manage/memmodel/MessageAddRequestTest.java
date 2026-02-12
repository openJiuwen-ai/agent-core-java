/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageAddRequest model.
 * Corresponds to Python: test_message_manager.py TestMessageAddRequest
 */
@DisplayName("MessageAddRequest Tests")
class MessageAddRequestTest {

    @Test
    @DisplayName("Test default timestamp is set")
    void testDefaultTimestamp() {
        MessageAddRequest req = MessageAddRequest.builder()
                .userId("user1")
                .scopeId("scope1")
                .content("test")
                .build();

        assertNotNull(req.timestamp());
        assertTrue(req.timestamp() instanceof Instant);
    }
}

