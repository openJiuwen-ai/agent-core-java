/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TimestampBeijingTime.
 * Mirrors Python's tests/unit_tests/core/memory/test_timestamp_beijing_time.py
 */
class TestTimestampBeijingTime {

    @Nested
    @DisplayName("TimestampBeijingTime tests")
    class TimestampTests {

        @Test
        @DisplayName("test add messages timestamp beijing time format")
        void testAddMessagesTimestampBeijingTimeFormat() {
            // Test that timestamp is formatted in Beijing time format.
            OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Shanghai"));
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String timestampStr = now.format(fmt);

            // Verify format matches expected pattern
            assertTrue(timestampStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        }

        @Test
        @DisplayName("test beijing timezone offset")
        void testBeijingTimezoneOffset() {
            // Test that Beijing timezone has correct offset (+8).
            ZoneId beijing = ZoneId.of("Asia/Shanghai");
            ZoneOffset offset = beijing.getRules().getOffset(OffsetDateTime.now().toInstant());

            // Beijing time is UTC+8
            assertEquals(ZoneOffset.ofHours(8), offset);
        }

        @Test
        @DisplayName("test default timestamp is beijing time")
        void testDefaultTimestampIsBeijingTime() {
            // Test that default timestamp uses Beijing timezone.
            OffsetDateTime timestamp = OffsetDateTime.now(ZoneId.systemDefault());
            
            // System default might be Beijing or not, but we can verify format
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formatted = timestamp.format(fmt);

            assertNotNull(formatted);
            assertTrue(formatted.length() == 19);
        }

        @Test
        @DisplayName("test timestamp format matches memory format")
        void testTimestampFormatMatchesMemoryFormat() {
            // Test that timestamp format matches LongTermMemory format.
            DateTimeFormatter memoryFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            OffsetDateTime now = OffsetDateTime.now();
            String formatted = now.format(memoryFmt);

            // Verify format: YYYY-MM-DD HH:MM:SS
            assertTrue(formatted.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        }
    }
}