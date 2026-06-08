/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoordinateUtilsTest {

    @Test
    void unwrapXyCoordsHandlesListAndScalarPairs() {
        assertEquals(
                new CoordinateUtils.CoordinatePair(100, 200),
                CoordinateUtils.unwrapXyCoords(List.of(100, 200), null)
        );
        assertEquals(
                new CoordinateUtils.CoordinatePair(50, 60),
                CoordinateUtils.unwrapXyCoords(null, List.of(50, 60))
        );
        assertEquals(
                new CoordinateUtils.CoordinatePair(10, 20),
                CoordinateUtils.unwrapXyCoords(10, 20)
        );
    }

    @Test
    void getVlmScreenMetadataMissingFieldsReturnsError() {
        CoordinateUtils.MetadataResult result = CoordinateUtils.getVlmScreenMetadata(Map.of());

        assertNull(result.metadata());
        assertNotNull(result.error());
        assertTrue(result.error().contains("VlmScreenMetadataMissing"));
    }

    @Test
    void getVlmScreenMetadataInvalidDimensionsReturnsError() {
        CoordinateUtils.MetadataResult result = CoordinateUtils.getVlmScreenMetadata(
                Map.of(
                        "vlm_screen_width", 0,
                        "vlm_screen_height", 1080,
                        "vlm_coordinate_scale", 1000
                )
        );

        assertNull(result.metadata());
        assertNotNull(result.error());
        assertTrue(result.error().contains("VlmScreenMetadataInvalid"));
    }

    @Test
    void getVlmScreenMetadataSuccessWithSplitScales() {
        CoordinateUtils.MetadataResult result = CoordinateUtils.getVlmScreenMetadata(
                Map.of(
                        "vlm_screen_width", 1080,
                        "vlm_screen_height", 1920,
                        "vlm_coordinate_scale_x", 800,
                        "vlm_coordinate_scale_y", 600
                )
        );

        assertNull(result.error());
        assertEquals(new CoordinateUtils.VlmScreenMetadata(1080, 1920, 800, 600), result.metadata());
    }

    @Test
    void normalizedToPixelMapsCenterOfNormalizedRange() {
        CoordinateUtils.PixelResult result = CoordinateUtils.normalizedToPixel(500, 500, 100, 200, 1000, null, null);

        assertNull(result.error());
        assertEquals(new CoordinateUtils.PixelPoint(50, 100), result.point());
    }

    @Test
    void normalizedToPixelClampsToScreenEdges() {
        CoordinateUtils.PixelResult result = CoordinateUtils.normalizedToPixel(1000, 1000, 10, 10, 1000, null, null);

        assertNull(result.error());
        assertEquals(new CoordinateUtils.PixelPoint(9, 9), result.point());
    }

    @Test
    void normalizedToPixelRejectsOutOfRangeCoordinates() {
        CoordinateUtils.PixelResult result = CoordinateUtils.normalizedToPixel(1001, 0, 100, 100, 1000, null, null);

        assertNull(result.point());
        assertNotNull(result.error());
        assertTrue(result.error().contains("CoordinateOutOfRange"));
    }

    @Test
    void normalizedToPixelRejectsNonNumericCoordinates() {
        CoordinateUtils.PixelResult result = CoordinateUtils.normalizedToPixel("left", 0, 100, 100, 1000, null, null);

        assertNull(result.point());
        assertTrue(result.error().contains("InvalidCoordinate"));
    }

    @Test
    void resolveVlmPixelEndToEnd() {
        CoordinateUtils.PixelResult result = CoordinateUtils.resolveVlmPixel(
                Map.of(
                        "vlm_screen_width", 200,
                        "vlm_screen_height", 400,
                        "vlm_coordinate_scale", 1000
                ),
                250,
                750
        );

        assertNull(result.error());
        assertEquals(new CoordinateUtils.PixelPoint(50, 300), result.point());
    }

    @Test
    void resolveVlmPixelPropagatesMissingMetadata() {
        CoordinateUtils.PixelResult result = CoordinateUtils.resolveVlmPixel(Map.of(), 1, 1);

        assertNull(result.point());
        assertTrue(result.error().contains("VlmScreenMetadataMissing"));
    }
}
