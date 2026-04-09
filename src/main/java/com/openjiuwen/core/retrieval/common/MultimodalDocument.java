  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.retrieval.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Multimodal document model.
 */
public class MultimodalDocument extends Document {

    private static final Pattern AUDIO_DATA_PATTERN = Pattern.compile("^data:audio/(.+?);base64,.*$");
    private static final List<String> SUPPORTED_KINDS = List.of("text", "image", "audio", "video");
    private final List<FieldValue> data = new ArrayList<>();

    public MultimodalDocument() {
        super(null, "", null);
    }

    public MultimodalDocument(String id, String text, Map<String, Object> metadata) {
        super(id, text == null ? "" : text, metadata);
    }

    public List<Map<String, Object>> getContent() {
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
                    String format = matcher.matches() ? matcher.group(1) : "unknown";
                    item.put("type", "input_audio");
                    item.put("input_audio", Map.of("data", value.data, "format", format));
                }
                default -> throw RetrievalExceptions.validation("unknown_kind");
            }
            if (value.dataId != null && !value.dataId.isBlank()) {
                item.put("uuid", value.dataId);
            }
            content.add(item);
        }
        return content;
    }

    public MultimodalDocument addField(String kind, String data) {
        return addField(kind, data, null, "");
    }

    public MultimodalDocument addField(String kind, Object data, Object filePath, Object dataId) {
        String loadedKind = loadKind(kind);
        String loadedData = loadData(loadedKind, data, filePath);
        String finalDataId = normalizeDataId(loadedKind, dataId);
        this.data.add(new FieldValue(loadedKind, loadedData, finalDataId));
        return this;
    }

    public MultimodalDocument addField(String kind, Path filePath) {
        return addField(kind, null, filePath, "");
    }

    private static String loadKind(String kind) {
        if (!SUPPORTED_KINDS.contains(kind)) {
            throw RetrievalExceptions.validation("unknown_kind");
        }
        return kind;
    }

    private static String normalizeDataId(String kind, Object dataId) {
        if (dataId == null || dataId.toString().isBlank()) {
            return "text".equals(kind) ? "" : UUID.randomUUID().toString().replace("-", "");
        }
        if (!(dataId instanceof String str) || str.length() > 32) {
            throw RetrievalExceptions.validation("invalid_uuid_provided");
        }
        return str;
    }

    private static String loadData(String kind, Object data, Object filePath) {
        if (data == null && filePath == null) {
            throw RetrievalExceptions.validation("no_" + kind + "_source_provided");
        }
        if (data != null && filePath != null) {
            throw RetrievalExceptions.validation("too_many_" + kind + "_source_provided");
        }
        if (data instanceof String stringData) {
            if ("text".equals(kind)) {
                return stringData;
            }
            if (!stringData.startsWith("data:" + kind + "/")) {
                throw RetrievalExceptions.validation("invalid_" + kind + "_data_provided");
            }
            return stringData;
        }
        if (data != null) {
            throw RetrievalExceptions.validation("invalid_" + kind + "_data_provided");
        }
        if (!(filePath instanceof Path path)) {
            throw RetrievalExceptions.validation("invalid_" + kind + "_file_path_provided");
        }
        if (!Files.isRegularFile(path)) {
            throw RetrievalExceptions.validation(kind + "_path_invalid");
        }
        try {
            if ("text".equals(kind)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
            String mime = Files.probeContentType(path);
            if (mime == null || mime.isBlank()) {
                mime = defaultMime(kind, path);
            }
            if (mime == null || mime.isBlank()) {
                throw RetrievalExceptions.validation("cannot_determine_mimetype");
            }
            if (!mime.startsWith(kind + "/")) {
                String suffix = mime.contains("/") ? mime.substring(mime.indexOf('/') + 1) : "octet-stream";
                mime = kind + "/" + suffix;
            }
            String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(path));
            return "data:" + mime + ";base64," + encoded;
        } catch (IOException e) {
            throw RetrievalExceptions.validation("error_loading_" + kind);
        }
    }

    private static String defaultMime(String kind, Path filePath) {
        String fileName = filePath.getFileName() == null ? "" : filePath.getFileName().toString().toLowerCase();
        if ("image".equals(kind) && fileName.endsWith(".png")) {
            return "image/png";
        }
        if ("audio".equals(kind) && fileName.endsWith(".wav")) {
            return "audio/wav";
        }
        if ("video".equals(kind) && fileName.endsWith(".mp4")) {
            return "video/mp4";
        }
        if ("text".equals(kind)) {
            return "text/plain";
        }
        return null;
    }

    private record FieldValue(String kind, String data, String dataId) {
    }
}
