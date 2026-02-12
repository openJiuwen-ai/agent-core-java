/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExtractedData and ExtractedDataType.
 * Corresponds to Python: test_memory_info.py TestExtractedData
 */
class ExtractedDataTest {

    @Test
    void testCreateAndEquality() {
        ExtractedData data1 = new ExtractedData(
                ExtractedDataType.USER,
                "user_name",
                "John Doe"
        );
        ExtractedData data2 = new ExtractedData(
                ExtractedDataType.USER,
                "user_name",
                "John Doe"
        );
        ExtractedData data3 = new ExtractedData(
                ExtractedDataType.USER,
                "user_name",
                "Jane Doe"
        );

        assertEquals(ExtractedDataType.USER, data1.type());
        assertEquals("user_name", data1.key());
        assertEquals("John Doe", data1.value());
        assertEquals(data1, data2);
        assertNotEquals(data1, data3);
    }

    @Test
    void testSpecialValues() {
        // Empty value
        ExtractedData empty = new ExtractedData(
                ExtractedDataType.USER,
                "optional",
                ""
        );
        assertEquals("", empty.value());

        // Special characters
        ExtractedData special = new ExtractedData(
                ExtractedDataType.USER,
                "description",
                "用户喜欢 音乐 & 运动！"
        );
        assertEquals("用户喜欢 音乐 & 运动！", special.value());

        // Long value
        String longValue = "x".repeat(10000);
        ExtractedData longData = new ExtractedData(
                ExtractedDataType.USER,
                "long_content",
                longValue
        );
        assertEquals(10000, longData.value().length());
    }

    @Test
    void testListOperations() {
        List<ExtractedData> dataList = List.of(
                new ExtractedData(ExtractedDataType.USER, "name", "Alice"),
                new ExtractedData(ExtractedDataType.USER, "age", "25"),
                new ExtractedData(ExtractedDataType.USER, "city", "Beijing")
        );

        assertEquals(3, dataList.size());
        List<String> keys = dataList.stream().map(ExtractedData::key).toList();
        assertEquals(List.of("name", "age", "city"), keys);
    }
}


