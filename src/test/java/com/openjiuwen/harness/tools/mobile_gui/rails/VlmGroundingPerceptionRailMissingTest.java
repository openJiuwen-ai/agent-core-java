/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.openjiuwen.harness.tools.mobile_gui.MobileGuiRuntimeSettings;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's
 * {@code tests/unit_tests/harness/tools/mobile_gui/test_vlm_grounding_perception_rail.py}.
 */
class VlmGroundingPerceptionRailMissingTest {

    @Test
    void prepareImageForModelResizesWideScreenshotsProportionally() {
        VlmGroundingPerceptionRail rail = new VlmGroundingPerceptionRail(settings(Map.of(
                "VLM_GROUNDING_MAX_WIDTH", "64"
        )));

        BufferedImage prepared = rail.prepareImageForModel(image(128, 32, Color.RED));

        assertEquals(64, prepared.getWidth());
        assertEquals(16, prepared.getHeight());
        assertEquals(BufferedImage.TYPE_INT_RGB, prepared.getType());
    }

    @Test
    void prepareImageForModelLeavesNarrowImagesUnchanged() {
        VlmGroundingPerceptionRail rail = new VlmGroundingPerceptionRail(settings(Map.of(
                "VLM_GROUNDING_MAX_WIDTH", "200"
        )));

        BufferedImage prepared = rail.prepareImageForModel(image(40, 80, Color.BLUE));

        assertEquals(40, prepared.getWidth());
        assertEquals(80, prepared.getHeight());
    }

    @Test
    void activeCoordinateScaleUsesConfiguredNormalizedScale() {
        VlmGroundingPerceptionRail rail = new VlmGroundingPerceptionRail(settings(Map.of(
                "VLM_COORDINATE_SCALE", "999"
        )));

        assertArrayEquals(new int[]{999, 999}, rail.activeCoordinateScale(image(10, 20, Color.BLACK)));
    }

    @Test
    void prepareImageForModelResizesToClaudeDimensions() {
        VlmGroundingPerceptionRail rail = new VlmGroundingPerceptionRail(settings(Map.of(
                "VLM_CLAUDE_IMAGE_WIDTH", "100",
                "VLM_CLAUDE_IMAGE_HEIGHT", "50"
        )), "claude-sonnet");

        BufferedImage prepared = rail.prepareImageForModel(image(20, 40, Color.WHITE));

        assertEquals(100, prepared.getWidth());
        assertEquals(50, prepared.getHeight());
    }

    @Test
    void activeCoordinateScaleUsesDisplayedPixelsForClaudeModels() {
        VlmGroundingPerceptionRail rail = new VlmGroundingPerceptionRail(settings(Map.of(
                "VLM_COORDINATE_SCALE", "999"
        )), "claude-sonnet");

        assertArrayEquals(new int[]{400, 300}, rail.activeCoordinateScale(image(400, 300, Color.GRAY)));
    }

    @Test
    void coordinateInstructionNormalizedVsPixelWording() {
        VlmGroundingPerceptionRail rail = new VlmGroundingPerceptionRail(settings(Map.of(
                "VLM_COORDINATE_SCALE", "1000"
        )));

        String normalized = rail.coordinateInstruction(1000, 1000);
        assertTrue(normalized.contains("[0, 1000]"));
        assertTrue(normalized.toLowerCase().contains("normalized"));

        String pixel = rail.coordinateInstruction(800, 600);
        assertTrue(pixel.contains("x in [0, 800]"));
        assertTrue(pixel.contains("y in [0, 600]"));
    }

    @Test
    void activeCoordinateScaleUnitScaleForKimiModels() {
        VlmGroundingPerceptionRail rail = new VlmGroundingPerceptionRail(settings(Map.of(
                "VLM_COORDINATE_SCALE", "1000"
        )), "kimi-k2");

        assertArrayEquals(new int[]{1, 1}, rail.activeCoordinateScale(image(500, 800, Color.ORANGE)));
    }

    @Test
    void prepareImageForModelAdaptiveResizeForOpus() {
        VlmGroundingPerceptionRail rail = new VlmGroundingPerceptionRail(settings(Map.of(
                "VLM_CLAUDE_OPUS_MAX_DIMENSION", "100"
        )), "claude-opus-4");

        BufferedImage prepared = rail.prepareImageForModel(image(50, 200, Color.MAGENTA));

        assertEquals(100, Math.max(prepared.getWidth(), prepared.getHeight()));
        assertEquals(25, prepared.getWidth());
        assertEquals(100, prepared.getHeight());
    }

    @Test
    void pilToBase64ReturnsJpegPayload() {
        VlmGroundingPerceptionRail rail = new VlmGroundingPerceptionRail(settings(Map.of()));

        Object encoded = rail.pilToBase64(image(4, 4, Color.GREEN));

        String base64 = assertInstanceOf(String.class, encoded);
        assertTrue(base64.length() > 20);
    }

    @Test
    void getForegroundAppReturnsPackageFromDevice() {
        VlmGroundingPerceptionRail rail = new VlmGroundingPerceptionRail(settings(Map.of()));

        assertEquals("com.example.app", rail.getForegroundApp(new Device()));
    }

    @Test
    void getForegroundAppReturnsUnknownOnDeviceError() {
        VlmGroundingPerceptionRail rail = new VlmGroundingPerceptionRail(settings(Map.of()));

        assertEquals("Unknown", rail.getForegroundApp(new BrokenDevice()));
    }

    private static MobileGuiRuntimeSettings settings(Map<String, String> env) {
        return MobileGuiRuntimeSettings.fromEnvironment(env);
    }

    private static BufferedImage image(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * Mirrors Python's fake device with {@code app_current()} returning a package.
     */
    private static final class Device {
        Map<String, String> app_current() {
            return Map.of("package", "com.example.app");
        }
    }

    /**
     * Mirrors Python's fake device whose {@code app_current()} raises.
     */
    private static final class BrokenDevice {
        Map<String, String> app_current() {
            throw new IllegalStateException("adb offline");
        }
    }
}
