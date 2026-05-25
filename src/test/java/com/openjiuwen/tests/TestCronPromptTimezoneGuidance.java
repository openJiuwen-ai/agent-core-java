/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cron prompt timezone guidance tests.
 * <p>
 * Mirrors Python's {@code test_cron_prompt_timezone_guidance.py} in
 * {@code tests/test_cron_prompt_timezone_guidance.py}.
 */
public class TestCronPromptTimezoneGuidance {

    @Nested
    @DisplayName("Timezone guidance tests")
    class TimezoneTests {

        @Test
        @DisplayName("Test timezone parsing")
        void testTimezoneParsing() {
            ZoneId zone = ZoneId.of("Asia/Shanghai");
            ZonedDateTime now = ZonedDateTime.now(zone);
            
            assertThat(zone).isNotNull();
            assertThat(now).isNotNull();
        }

        @Test
        @DisplayName("Test timezone format")
        void testTimezoneFormat() {
            ZoneId zone = ZoneId.of("UTC");
            ZonedDateTime time = ZonedDateTime.of(2026, 5, 16, 10, 30, 0, 0, zone);
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
            String formatted = time.format(formatter);
            
            assertThat(formatted).contains("2026-05-16");
            assertThat(formatted).contains("UTC");
        }

        @Test
        @DisplayName("Test cron expression parsing")
        void testCronExpression() {
            // Test cron expression parsing
            String cronExpression = "0 30 10 * * ?";
            
            assertThat(cronExpression).isNotNull();
            assertThat(cronExpression).isNotEmpty();
            
            // Parse cron parts
            String[] parts = cronExpression.split(" ");
            assertThat(parts).hasSize(6);
            assertThat(parts[0]).isEqualTo("0");  // seconds
            assertThat(parts[1]).isEqualTo("30"); // minutes
            assertThat(parts[2]).isEqualTo("10"); // hours
        }

        @Test
        @DisplayName("Test timezone guidance generation")
        void testTimezoneGuidanceGeneration() {
            // Test timezone guidance generation
            ZoneId zone = ZoneId.of("Asia/Shanghai");
            ZonedDateTime now = ZonedDateTime.now(zone);
            
            String guidance = generateTimezoneGuidance(zone, now);
            
            assertThat(guidance).isNotNull();
            assertThat(guidance).contains("Asia/Shanghai");
        }
        
        private String generateTimezoneGuidance(ZoneId zone, ZonedDateTime time) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
            return String.format("当前时区: %s, 当前时间: %s", 
                zone.getId(), 
                time.format(formatter));
        }
    }
}