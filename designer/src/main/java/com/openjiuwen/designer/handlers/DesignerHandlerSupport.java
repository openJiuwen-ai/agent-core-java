/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.handlers;

import com.openjiuwen.designer.schema.DesignerAssetRef;
import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerNode;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

/**
 * Shared helpers for built-in designer handlers.
 *
 * @since 2026-11-06
 */
final class DesignerHandlerSupport {

    private static final int[] IMAGE_1K = {1024, 1024};
    private static final Map<String, int[]> IMAGE_1K_SIZES = Map.of(
            "1:1", new int[]{1024, 1024},
            "4:3", new int[]{1152, 864},
            "3:4", new int[]{864, 1152},
            "16:9", new int[]{1424, 800},
            "9:16", new int[]{800, 1424});

    private DesignerHandlerSupport() {
    }

    static String prompt(DesignerNode node, DesignerGraph graph) {
        String prompt = text(node.config().get("prompt"));
        if (prompt.isBlank()) {
            prompt = text(graph.description());
        }
        if (prompt.isBlank()) {
            prompt = text(node.label());
        }
        return prompt;
    }

    static String imageRatio(DesignerGraph graph) {
        Map<String, Object> settings = map(graph.metadata().get("image_settings"));
        String ratio = text(settings.get("aspect_ratio"));
        return ratio.isBlank() ? "16:9" : ratio;
    }

    static int[] imageSize(DesignerGraph graph) {
        Map<String, Object> settings = map(graph.metadata().get("image_settings"));
        String ratio = imageRatio(graph);
        String resolution = text(settings.get("resolution"));
        if ("1.5K".equals(resolution)) {
            int[] size = IMAGE_1K_SIZES.get(ratio);
            return size == null ? new int[]{1536, 1536}
                    : new int[]{size[0] * 3 / 2, size[1] * 3 / 2};
        }
        int[] size = IMAGE_1K_SIZES.get(ratio);
        return size == null ? IMAGE_1K : size;
    }

    static String videoRatio(DesignerGraph graph) {
        Map<String, Object> settings = map(graph.metadata().get("video_settings"));
        String ratio = text(settings.get("aspect_ratio"));
        return ratio.isBlank() ? "16:9" : ratio;
    }

    static String videoResolution(DesignerGraph graph) {
        Map<String, Object> settings = map(graph.metadata().get("video_settings"));
        String resolution = text(settings.get("resolution"));
        return resolution.isBlank() ? "768P" : resolution;
    }

    static int duration(DesignerNode node) {
        Object raw = node.config().get("duration_sec");
        int seconds = raw instanceof Number number ? number.intValue() : 5;
        return Math.max(4, Math.min(15, seconds));
    }

    static String safeStem(String value) {
        StringBuilder builder = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.') {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }
        return builder.length() == 0 ? "node" : builder.toString();
    }

    static DesignerAssetRef asset(Path file, String kind, String mimeType) {
        Path absolute = file.toAbsolutePath().normalize();
        return new DesignerAssetRef(kind, absolute.toUri().toString(), mimeType,
                absolute.getFileName().toString());
    }

    static Path path(DesignerAssetRef ref) {
        String uri = ref == null ? "" : text(ref.uri());
        if (uri.startsWith("file:")) {
            return Path.of(URI.create(uri));
        }
        return Path.of(uri);
    }

    static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }
}
