/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.common;

import com.openjiuwen.core.retrieval.common.MultimodalDocument;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MultimodalDocument data model test cases.
 * <p>
 * Mirrors Python's {@code TestMultimodalDocument} in
 * {@code tests/unit_tests/core/retrieval/common/test_multimodal_document.py}.
 */
class TestMultimodalDocument {

    // ---------------------------------------------------------------------------
    // Helper methods to generate test data
    // ---------------------------------------------------------------------------

    private static String tinyPngBase64() {
        // Minimal 1x1 PNG image
        byte[] pngData = new byte[] {
            (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,  // PNG signature
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,  // IHDR chunk
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte)0x90, 0x77, 0x53,
            (byte)0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,  // IDAT chunk
            0x54, 0x08, (byte)0xD7, 0x63, (byte)0xF8, (byte)0xFF, (byte)0xFF,
            0x3F, 0x00, 0x05, (byte)0xFE, 0x02, (byte)0xFE, (byte)0xDC,
            (byte)0xCC, 0x59, 0x45, 0x00, 0x00, 0x00, 0x00,  // IEND chunk
            0x49, 0x45, 0x4E, 0x44, (byte)0xAE, 0x42, 0x60, (byte)0x82
        };
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
    }

    private static String tinyWavBase64() {
        // Minimal WAV file header (44 bytes) + 2 bytes of audio data
        byte[] wavData = new byte[46];
        // RIFF header
        wavData[0] = 'R'; wavData[1] = 'I'; wavData[2] = 'F'; wavData[3] = 'F';
        wavData[4] = 0x24; wavData[5] = 0; wavData[6] = 0; wavData[7] = 0;
        wavData[8] = 'W'; wavData[9] = 'A'; wavData[10] = 'V'; wavData[11] = 'E';
        // fmt chunk
        wavData[12] = 'f'; wavData[13] = 'm'; wavData[14] = 't'; wavData[15] = ' ';
        wavData[16] = 0x10; wavData[17] = 0; wavData[18] = 0; wavData[19] = 0;
        wavData[20] = 1; wavData[21] = 0;  // audio format = 1 (PCM)
        wavData[22] = 1; wavData[23] = 0;  // num channels = 1
        wavData[24] = 0x44; wavData[25] = (byte)0xAC; wavData[26] = 0; wavData[27] = 0;  // sample rate
        wavData[28] = (byte)0x88; wavData[29] = 0x58; wavData[30] = 0x01; wavData[31] = 0;  // byte rate
        wavData[32] = 2; wavData[33] = 0;  // block align
        wavData[34] = 0x10; wavData[35] = 0;  // bits per sample
        // data chunk
        wavData[36] = 'd'; wavData[37] = 'a'; wavData[38] = 't'; wavData[39] = 'a';
        wavData[40] = 0x06; wavData[41] = 0; wavData[42] = 0; wavData[43] = 0;
        // audio data
        wavData[44] = 0; wavData[45] = 0;
        return "data:audio/wav;base64," + Base64.getEncoder().encodeToString(wavData);
    }

    private static String fakeMp4Base64() {
        return "data:video/mp4;base64," + Base64.getEncoder().encodeToString("fake mp4 data".getBytes());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic construction)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Test default constructor")
    void testDefaultConstructor() {
        MultimodalDocument doc = new MultimodalDocument();
        assertNotNull(doc);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test constructor with parameters")
    void testConstructorWithParameters() {
        MultimodalDocument doc = new MultimodalDocument("test-id", "test text", null);
        assertNotNull(doc);
        assertEquals("test-id", doc.getId());
        assertEquals("test text", doc.getText());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Field operations)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Test add text field")
    void testAddTextField() {
        MultimodalDocument doc = new MultimodalDocument();
        doc.addField("text", "Hello world");
        
        List<Map<String, Object>> content = doc.getContent();
        assertFalse(content.isEmpty());
        assertEquals("text", content.get(0).get("type"));
        assertEquals("Hello world", content.get(0).get("text"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test add image field")
    void testAddImageField() {
        MultimodalDocument doc = new MultimodalDocument();
        String imageData = tinyPngBase64();
        doc.addField("image", imageData);
        
        List<Map<String, Object>> content = doc.getContent();
        assertFalse(content.isEmpty());
        assertEquals("image_url", content.get(0).get("type"));
        assertNotNull(content.get(0).get("image_url"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test add audio field")
    void testAddAudioField() {
        MultimodalDocument doc = new MultimodalDocument();
        String audioData = tinyWavBase64();
        doc.addField("audio", audioData);
        
        List<Map<String, Object>> content = doc.getContent();
        assertFalse(content.isEmpty());
        assertEquals("input_audio", content.get(0).get("type"));
        assertNotNull(content.get(0).get("input_audio"));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test add video field")
    void testAddVideoField() {
        MultimodalDocument doc = new MultimodalDocument();
        String videoData = fakeMp4Base64();
        doc.addField("video", videoData);
        
        List<Map<String, Object>> content = doc.getContent();
        assertFalse(content.isEmpty());
        assertEquals("video_url", content.get(0).get("type"));
        assertNotNull(content.get(0).get("video_url"));
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Multiple fields)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    @DisplayName("Test multiple fields")
    void testMultipleFields() {
        MultimodalDocument doc = new MultimodalDocument();
        doc.addField("text", "Describe this image");
        doc.addField("image", tinyPngBase64());
        
        List<Map<String, Object>> content = doc.getContent();
        assertEquals(2, content.size());
        assertEquals("text", content.get(0).get("type"));
        assertEquals("image_url", content.get(1).get("type"));
    }

    @Test
    @Tag("level2")
    @DisplayName("Test all media types")
    void testAllMediaTypes() {
        MultimodalDocument doc = new MultimodalDocument();
        doc.addField("text", "Multimodal content");
        doc.addField("image", tinyPngBase64());
        doc.addField("audio", tinyWavBase64());
        doc.addField("video", fakeMp4Base64());
        
        List<Map<String, Object>> content = doc.getContent();
        assertEquals(4, content.size());
    }
}