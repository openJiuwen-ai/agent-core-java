/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's coordinate helpers in
 * {@code openjiuwen/harness/tools/mobile_gui/coordinate_utils.py}.
 */
public final class CoordinateUtils {

    private CoordinateUtils() {
    }

    public static CoordinatePair unwrapXyCoords(Object x, Object y) {
        if (x instanceof List<?> xList && xList.size() == 2 && y == null) {
            return new CoordinatePair(xList.get(0), xList.get(1));
        }
        if (y instanceof List<?> yList && yList.size() == 2 && x == null) {
            return new CoordinatePair(yList.get(0), yList.get(1));
        }
        return new CoordinatePair(x, y);
    }

    public static MetadataResult getVlmScreenMetadata(Map<?, ?> extra) {
        Map<?, ?> values = extra == null ? Map.of() : extra;
        Object width = values.get("vlm_screen_width");
        Object height = values.get("vlm_screen_height");
        Object scaleX = values.containsKey("vlm_coordinate_scale_x")
                ? values.get("vlm_coordinate_scale_x")
                : (values.containsKey("vlm_coordinate_scale") ? values.get("vlm_coordinate_scale") : 1000);
        Object scaleY = values.containsKey("vlm_coordinate_scale_y")
                ? values.get("vlm_coordinate_scale_y")
                : (values.containsKey("vlm_coordinate_scale") ? values.get("vlm_coordinate_scale") : 1000);

        final int widthI;
        final int heightI;
        final int scaleXI;
        final int scaleYI;
        try {
            widthI = parseInt(width);
            heightI = parseInt(height);
            scaleXI = parseInt(scaleX);
            scaleYI = parseInt(scaleY);
        } catch (NumberFormatException ex) {
            return new MetadataResult(null,
                    "Error: VlmScreenMetadataMissing: latest VLM screenshot metadata is "
                            + "not available. Ensure VlmGroundingPerceptionRail ran before coordinate tools.");
        }

        if (!metadataDimensionsPositive(widthI, heightI, scaleXI, scaleYI)) {
            return new MetadataResult(null,
                    "Error: VlmScreenMetadataInvalid: screen width, height, and coordinate "
                            + "scales must be positive.");
        }

        return new MetadataResult(new VlmScreenMetadata(widthI, heightI, scaleXI, scaleYI), null);
    }

    public static PixelResult normalizedToPixel(
            Object x,
            Object y,
            int width,
            int height,
            Integer scale,
            Integer scaleX,
            Integer scaleY
    ) {
        CoordinatePair coords = unwrapXyCoords(x, y);
        final double xF;
        final double yF;
        try {
            xF = parseDouble(coords.x());
            yF = parseDouble(coords.y());
        } catch (NumberFormatException ex) {
            return new PixelResult(null,
                    "Error: InvalidCoordinate: coordinates must be numeric, got ("
                            + coords.x() + ", " + coords.y() + ").");
        }

        Integer effectiveScaleX = scale != null ? scale : scaleX;
        Integer effectiveScaleY = scale != null ? scale : scaleY;
        if (effectiveScaleX == null) {
            effectiveScaleX = 1000;
        }
        if (effectiveScaleY == null) {
            effectiveScaleY = 1000;
        }

        if (!normalizedCoordsInRange(xF, yF, effectiveScaleX, effectiveScaleY)) {
            return new PixelResult(null,
                    "Error: CoordinateOutOfRange: coordinates (" + formatFloat(xF) + ", " + formatFloat(yF)
                            + ") must be inside x=[0, " + effectiveScaleX + "], y=[0, " + effectiveScaleY + "].");
        }

        int px = (int) Math.round(xF * width / effectiveScaleX);
        int py = (int) Math.round(yF * height / effectiveScaleY);
        px = Math.max(0, Math.min(width - 1, px));
        py = Math.max(0, Math.min(height - 1, py));
        return new PixelResult(new PixelPoint(px, py), null);
    }

    public static PixelResult resolveVlmPixel(Map<?, ?> extra, Object x, Object y) {
        MetadataResult metadata = getVlmScreenMetadata(extra);
        if (metadata.error() != null) {
            return new PixelResult(null, metadata.error());
        }
        return normalizedToPixel(
                x,
                y,
                metadata.metadata().width(),
                metadata.metadata().height(),
                null,
                metadata.metadata().scaleX(),
                metadata.metadata().scaleY()
        );
    }

    private static boolean metadataDimensionsPositive(int width, int height, int scaleX, int scaleY) {
        return width > 0 && height > 0 && scaleX > 0 && scaleY > 0;
    }

    private static boolean normalizedCoordsInRange(double x, double y, int scaleX, int scaleY) {
        return 0 <= x && x <= scaleX && 0 <= y && y <= scaleY;
    }

    private static int parseInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static String formatFloat(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    public record CoordinatePair(Object x, Object y) {
    }

    /**
     * Mirrors Python's VLM screen metadata bundle in
     * {@code openjiuwen/harness/tools/mobile_gui/coordinate_utils.py}.
     */
    public record VlmScreenMetadata(int width, int height, int scaleX, int scaleY) {
    }

    public record MetadataResult(VlmScreenMetadata metadata, String error) {
    }

    public record PixelPoint(int x, int y) {
    }

    public record PixelResult(PixelPoint point, String error) {
    }
}
