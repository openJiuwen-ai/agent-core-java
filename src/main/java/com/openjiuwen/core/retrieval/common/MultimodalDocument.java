/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code MultimodalDocument} in
 * {@code openjiuwen/core/retrieval/common/document.py}.
 */
public class MultimodalDocument extends Document {

    private static final Pattern AUDIO_DATA_PATTERN = Pattern.compile("^data:audio/(.+?);base64,.*$");
    private static final Set<String> SUPPORTED_KINDS = Set.of("text", "image", "audio", "video");

    private final List<FieldValue> data = new ArrayList<>();
    private List<Map<String, Object>> contentCache = new ArrayList<>();
    private Map<String, Object> dashscopeCache = new LinkedHashMap<>();

    public MultimodalDocument() {
        this(null, "", null);
    }

    public MultimodalDocument(String id) {
        this(id, "", null);
    }

    public MultimodalDocument(Map<String, Object> metadata) {
        this(null, "", metadata);
    }

    public MultimodalDocument(String id, String text, Map<String, Object> metadata) {
        super(id, text == null ? "" : text, metadata);
    }

    public List<Map<String, Object>> getContent() {
        if (!contentCache.isEmpty()) {
            return deepCopyList(contentCache);
        }
        List<Map<String, Object>> content = new ArrayList<>();
        for (FieldValue value : data) {
            Map<String, Object> item = new LinkedHashMap<>();
            switch (value.kind) {
                case "text" -> {
                    item.put("type", "text");
                    item.put("text", value.data);
                }
                case "image", "video" -> {
                    item.put("type", value.kind + "_url");
                    item.put(value.kind + "_url", Map.of("url", value.data));
                }
                case "audio" -> {
                    Matcher matcher = AUDIO_DATA_PATTERN.matcher(value.data);
                    String format = matcher.find() ? matcher.group(1) : "";
                    item.put("type", "input_audio");
                    item.put("input_audio", Map.of("data", value.data, "format", format));
                }
                default -> throw validation("unknown_kind", "Unknown kind of multimodal file: " + value.kind, Map.of("kind", value.kind));
            }
            if (!value.dataId.isBlank()) {
                item.put("uuid", value.dataId);
            }
            content.add(item);
        }
        contentCache = deepCopyList(content);
        return deepCopyList(content);
    }

    public Map<String, Object> getDashscopeInput() {
        if (!dashscopeCache.isEmpty()) {
            return deepCopyMap(dashscopeCache);
        }
        Map<String, Object> content = new LinkedHashMap<>();
        List<String> images = new ArrayList<>();
        Set<String> hasField = new LinkedHashSet<>();
        for (FieldValue value : data) {
            if ("image".equals(value.kind)) {
                images.add(value.data);
                continue;
            }
            if (hasField.contains(value.kind)) {
                throw validation(
                        "multiple_" + value.kind + "_fields_present",
                        "Dashscope format does not support multiple fields of same modality",
                        Map.of("fields", data.toString())
                );
            }
            switch (value.kind) {
                case "text" -> {
                    hasField.add(value.kind);
                    content.put("text", value.data);
                }
                case "video" -> {
                    if (value.data.startsWith("data:video/")) {
                        throw validation(
                                "unsupported_format",
                                "Dashscope format only support video in url format, not base64",
                                Map.of("kind", value.kind, "data_id", value.dataId, "data", value.data)
                        );
                    }
                    hasField.add(value.kind);
                    content.put("video", value.data);
                }
                case "audio" -> throw validation(
                        "unsupported_format",
                        "Dashscope format does not support modality audio",
                        Map.of("kind", value.kind, "data_id", value.dataId, "data", value.data)
                );
                default -> throw validation(
                        "unsupported_format",
                        "Dashscope format does not support modality " + value.kind,
                        Map.of("kind", value.kind, "data_id", value.dataId, "data", value.data)
                );
            }
        }
        if (!images.isEmpty()) {
            if (images.size() == 1) {
                content.put("image", images.get(0));
            } else {
                content.put("multi_images", new ArrayList<>(images));
            }
        }
        dashscopeCache = deepCopyMap(content);
        return deepCopyMap(content);
    }

    public MultimodalDocument addField(String kind) {
        return addField(kind, null, null, "");
    }

    public MultimodalDocument addField(String kind, String data) {
        return addField(kind, data, null, "");
    }

    public MultimodalDocument addField(String kind, Path filePath) {
        return addField(kind, null, filePath, "");
    }

    public MultimodalDocument addField(String kind, String data, Path filePath, String dataId) {
        return addField(kind, (Object) data, (Object) filePath, dataId);
    }

    public MultimodalDocument addField(String kind, Object data, Object filePath, Object dataId) {
        clearCaches();
        LoadedField loaded = loadMultimodalData(kind, data, filePath);
        String normalizedDataId;
        if (dataId == null || dataId.toString().isBlank()) {
            normalizedDataId = "text".equals(loaded.kind) ? "" : UUID.randomUUID().toString().replace("-", "");
        } else if (!(dataId instanceof String value) || value.length() > 32) {
            throw validation(
                    "invalid_uuid_provided",
                    "MultimodalDocument.add_field received invalid \"data_id\", uuid is a string of length 32",
                    Map.of("data_id", dataId)
            );
        } else {
            normalizedDataId = value;
        }
        this.data.add(new FieldValue(loaded.kind, loaded.data, normalizedDataId));
        return this;
    }

    public MultimodalDocument strip() {
        return data.isEmpty() ? null : this;
    }

    private void clearCaches() {
        contentCache = new ArrayList<>();
        dashscopeCache = new LinkedHashMap<>();
    }

    private static LoadedField loadMultimodalData(String kind, Object data, Object filePath) {
        if (!SUPPORTED_KINDS.contains(kind)) {
            throw validation(
                    "unknown_kind",
                    "Unknown kind of multimodal file: " + kind + ", supported option: [\"text\", \"image\", \"audio\", \"video\"]",
                    Map.of("kind", kind)
            );
        }
        if (data == null && filePath == null) {
            throw validation(
                    "no_" + kind + "_source_provided",
                    "MultimodalDocument.add_field received no data of " + kind + " type",
                    Map.of("data", "", "file_path", "")
            );
        }
        if (data != null && filePath != null) {
            throw validation(
                    "too_many_" + kind + "_source_provided",
                    "MultimodalDocument.add_field cannot accept both \"file_path\" and \"data\", please only set one",
                    Map.of("data", data.toString(), "file_path", filePath.toString())
            );
        }
        if (data instanceof String stringData) {
            if ("text".equals(kind) || stringData.matches("^data:" + kind + "/(.+?);base64,.*$")) {
                return new LoadedField(kind, stringData);
            }
            if (!"audio".equals(kind) && stringData.startsWith("http") && stringData.contains("://")) {
                return new LoadedField(kind, stringData);
            }
            throw validation(
                    "invalid_" + kind + "_data_provided",
                    "MultimodalDocument.add_field received invalid \"data\", this can be url or start with \"data:" + kind + "/\"",
                    Map.of("data", stringData)
            );
        }
        if (!(filePath instanceof Path path)) {
            throw validation(
                    "invalid_" + kind + "_file_path_provided",
                    "MultimodalDocument.add_field received invalid \"file_path\", this value should be a Path",
                    Map.of("file_path", String.valueOf(filePath))
            );
        }
        if (!Files.isRegularFile(path)) {
            throw validation(
                    kind + "_path_invalid",
                    "Unable to open " + kind + " file at " + path,
                    Map.of("kind", kind, "file_path", path.toString())
            );
        }
        try {
            if ("text".equals(kind)) {
                return new LoadedField(kind, Files.readString(path, StandardCharsets.UTF_8));
            }
            String mimeType = guessMimeType(kind, path);
            if (mimeType == null || !mimeType.contains("/")) {
                throw validation(
                        "cannot_determine_mimetype",
                        "Unable to determine mimetype for " + kind + " file: " + path,
                        Map.of("kind", kind, "file_path", path.toString())
                );
            }
            if (!mimeType.startsWith(kind + "/")) {
                mimeType = kind + "/" + mimeType.substring(mimeType.indexOf('/') + 1);
            }
            String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(path));
            return new LoadedField(kind, "data:" + mimeType + ";base64," + encoded);
        } catch (IOException ex) {
            throw validation(
                    "error_loading_" + kind,
                    "Unable to load " + kind + " file into base64: " + ex.getMessage(),
                    Map.of("kind", kind, "file_path", path.toString())
            );
        }
    }

    private static String guessMimeType(String kind, Path path) throws IOException {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
        if (name.endsWith(".jfif")) {
            return "image/jpeg";
        }
        String mime = Files.probeContentType(path);
        if (mime != null && mime.contains("/")) {
            return mime;
        }
        if ("image".equals(kind) && name.endsWith(".png")) {
            return "image/png";
        }
        if ("image".equals(kind) && (name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
            return "image/jpeg";
        }
        if ("audio".equals(kind) && name.endsWith(".wav")) {
            return "audio/wav";
        }
        if ("video".equals(kind) && name.endsWith(".mp4")) {
            return "video/mp4";
        }
        return null;
    }

    private static List<Map<String, Object>> deepCopyList(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> item : source) {
            copy.add(deepCopyMap(item));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> mapValue) {
                copy.put(entry.getKey(), deepCopyMap((Map<String, Object>) mapValue));
            } else if (value instanceof List<?> listValue) {
                copy.put(entry.getKey(), new ArrayList<>(listValue));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    static com.openjiuwen.core.common.exception.ValidationError validation(
            String errorType,
            String message,
            Map<String, Object> context
    ) {
        return Document.validation(errorType, message, context);
    }

    private record FieldValue(String kind, String data, String dataId) {
    }

    private record LoadedField(String kind, String data) {
    }
}
