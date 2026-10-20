/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.multimodal;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Default stateless {@link MultimodalContentNormalizer} implementation.
 *
 * <p>Structure processing only: no file I/O happens here. Legacy OpenAI
 * {@code image_url} parts are converted into internal sources; a base64 data
 * URL is decoded exactly once, and byte arrays received as {@code raw}
 * sources are never decoded again.</p>
 *
 * @since 0.1.17
 */
public final class DefaultMultimodalContentNormalizer implements MultimodalContentNormalizer {
    private static final String LEGACY_TYPE_IMAGE_URL = "image_url";
    private static final String DATA_URL_PREFIX = "data:";
    private static final String BASE64_DATA_MARKER = ";base64,";
    private static final Pattern PATH_VERSION_PATTERN = Pattern.compile("^sha256:[0-9a-fA-F]{64}$");
    private static final Set<String> KIND_SPECIFIC_FIELDS = Set.of("bytes", "url", "path", "assetId");
    private static final Set<String> RAW_SOURCE_FIELDS = Set.of("kind", "bytes");
    private static final Set<String> URL_SOURCE_FIELDS = Set.of("kind", "url");
    private static final Set<String> PATH_SOURCE_FIELDS = Set.of("kind", "path", "version");
    private static final Set<String> ASSET_SOURCE_FIELDS = Set.of("kind", "assetId", "version");

    /**
     * Normalize one agent input into ordered content blocks.
     *
     * @param input string, legacy business map, content block list, structured
     *              message map or {@code BaseMessage}
     * @return ordered immutable content blocks
     */
    @Override
    public List<ContentBlock> normalize(Object input) {
        if (input == null) {
            throw MultimodalErrors.contentInvalid("multimodal input is null", null, null, null);
        }
        if (input instanceof String text) {
            return List.of(new ContentBlock.Text(defaultId(0), text));
        }
        if (input instanceof BaseMessage message) {
            return normalizeMessageContent(message.getContent());
        }
        if (input instanceof List<?> list) {
            return normalizeBlockList(list);
        }
        if (input instanceof Map<?, ?> map) {
            return normalizeMapInput(map);
        }
        throw MultimodalErrors.contentInvalid(
                "unsupported multimodal input type " + input.getClass().getName(), null, null, null);
    }

    private List<ContentBlock> normalizeMapInput(Map<?, ?> map) {
        if (map.containsKey("content")) {
            return normalizeMessageContent(map.get("content"));
        }
        if (map.get("type") instanceof String) {
            return normalizeBlockList(List.of(map));
        }
        throw MultimodalErrors.contentInvalid(
                "map input must contain 'content' (structured message) or a string 'type' (content block)",
                null, null, null);
    }

    private List<ContentBlock> normalizeMessageContent(Object content) {
        if (content == null) {
            throw MultimodalErrors.contentInvalid("structured message content is null", null, "content", null);
        }
        if (content instanceof String text) {
            return List.of(new ContentBlock.Text(defaultId(0), text));
        }
        if (content instanceof List<?> list) {
            return normalizeBlockList(list);
        }
        if (content instanceof Map<?, ?> map && map.get("type") instanceof String) {
            return normalizeBlockList(List.of(map));
        }
        throw MultimodalErrors.contentInvalid(
                "message content must be a string, a content block list or a content block map",
                null, "content", null);
    }

    private List<ContentBlock> normalizeBlockList(List<?> list) {
        if (list.isEmpty()) {
            throw MultimodalErrors.contentInvalid("content block list must not be empty", null, null, null);
        }
        List<ContentBlock> blocks = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            blocks.add(parseBlock(list.get(index), index));
        }
        return Collections.unmodifiableList(blocks);
    }

    private ContentBlock parseBlock(Object item, int index) {
        if (!(item instanceof Map<?, ?> map)) {
            throw MultimodalErrors.contentInvalid(
                    "content block must be a map, got " + describeType(item), null, "block", index);
        }
        String blockId = parseBlockId(map, index);
        Object rawType = map.get("type");
        if (!(rawType instanceof String type)) {
            throw MultimodalErrors.contentInvalid(
                    "content block 'type' must be a string", blockId, "type", index);
        }
        if (LEGACY_TYPE_IMAGE_URL.equals(type)) {
            return parseLegacyImageUrlBlock(map, blockId, index);
        }
        ContentBlockType blockType = ContentBlockType.fromWire(type).orElse(null);
        if (blockType == null) {
            throw MultimodalErrors.contentInvalid(
                    "unrecognized content block type '" + type + "'", blockId, "type", index);
        }
        return switch (blockType) {
            case TEXT -> parseTextBlock(map, blockId, index);
            case IMAGE -> parseMediaBlock(map, blockId, index, ContentBlockType.IMAGE);
            case VIDEO -> parseMediaBlock(map, blockId, index, ContentBlockType.VIDEO);
            case FILE -> parseMediaBlock(map, blockId, index, ContentBlockType.FILE);
            case TOOL_RESULT -> parseToolResultBlock(map, blockId, index);
        };
    }

    private String parseBlockId(Map<?, ?> map, int index) {
        Object rawId = map.get("id");
        if (rawId == null) {
            return defaultId(index);
        }
        if (rawId instanceof String id && !id.isBlank()) {
            return id;
        }
        throw MultimodalErrors.contentInvalid(
                "content block 'id' must be a non-blank string", null, "id", index);
    }

    private ContentBlock parseTextBlock(Map<?, ?> map, String blockId, int index) {
        Object text = map.get("text");
        if (!(text instanceof String value)) {
            throw MultimodalErrors.contentInvalid(
                    "text block requires a string 'text' field", blockId, "text", index);
        }
        return new ContentBlock.Text(blockId, value);
    }

    private ContentBlock parseMediaBlock(Map<?, ?> map, String blockId, int index, ContentBlockType blockType) {
        Object rawSource = map.get("source");
        if (!(rawSource instanceof Map<?, ?> sourceMap)) {
            throw MultimodalErrors.contentInvalid(
                    blockType.wireValue() + " block requires a 'source' object", blockId, "source", index);
        }
        ImageSource source = parseSource(sourceMap, blockId, index);
        String mediaType = optionalString(map, "mediaType", blockId, index).orElse(null);
        String filename = optionalString(map, "filename", blockId, index).orElse(null);
        String description = optionalString(map, "description", blockId, index).orElse(null);
        Map<String, Object> provenance = optionalProvenance(map, blockId, index);
        return switch (blockType) {
            case IMAGE -> new ContentBlock.Image(blockId, source, mediaType, filename, description, provenance);
            case VIDEO -> new ContentBlock.Video(blockId, source, mediaType, filename, description, provenance);
            case FILE -> new ContentBlock.FileRef(blockId, source, mediaType, filename, description, provenance);
            default -> throw MultimodalErrors.contentInvalid(
                    "block type '" + blockType.wireValue() + "' carries no source", blockId, "type", index);
        };
    }

    private ContentBlock parseToolResultBlock(Map<?, ?> map, String blockId, int index) {
        String toolCallId = requireNonBlankString(map, "toolCallId", "toolCallId", blockId, index);
        String toolName = requireNonBlankString(map, "toolName", "toolName", blockId, index);
        String status = requireNonBlankString(map, "status", "status", blockId, index);
        Object rawItems = map.get("items");
        if (rawItems == null) {
            return new ContentBlock.ToolResult(blockId, toolCallId, toolName, status, List.of());
        }
        if (rawItems instanceof List<?> items) {
            return new ContentBlock.ToolResult(blockId, toolCallId, toolName, status, new ArrayList<>(items));
        }
        throw MultimodalErrors.contentInvalid(
                "tool_result block 'items' must be a list", blockId, "items", index);
    }

    private ImageSource parseSource(Map<?, ?> sourceMap, String blockId, int index) {
        Object rawKind = sourceMap.get("kind");
        if (!(rawKind instanceof String kind)) {
            throw MultimodalErrors.contentInvalid(
                    "source requires a string 'kind' field", blockId, "source.kind", index);
        }
        validateSourceFields(sourceMap, kind, blockId, index);
        return switch (kind) {
            case ImageSource.KIND_RAW -> parseRawSource(sourceMap, blockId, index);
            case ImageSource.KIND_URL -> new ImageSource.Url(
                    requireNonBlankString(sourceMap, "url", "source.url", blockId, index));
            case ImageSource.KIND_PATH -> parsePathSource(sourceMap, blockId, index);
            case ImageSource.KIND_ASSET -> new ImageSource.AssetRef(
                    requireNonBlankString(sourceMap, "assetId", "source.assetId", blockId, index),
                    requireNonBlankString(sourceMap, "version", "source.version", blockId, index));
            default -> throw MultimodalErrors.contentInvalid(
                    "unrecognized source kind '" + kind + "'", blockId, "source.kind", index);
        };
    }

    private void validateSourceFields(Map<?, ?> sourceMap, String kind, String blockId, int index) {
        Set<String> allowedFields = allowedSourceFields(kind);
        if (allowedFields == null) {
            return;
        }
        for (Object rawKey : sourceMap.keySet()) {
            String key = String.valueOf(rawKey);
            if (allowedFields.contains(key)) {
                continue;
            }
            if (KIND_SPECIFIC_FIELDS.contains(key)) {
                throw MultimodalErrors.contentInvalid(
                        "source kind '" + kind + "' conflicts with field '" + key
                                + "'; only one source kind is allowed",
                        blockId, "source." + key, index);
            }
            throw MultimodalErrors.contentInvalid(
                    "unknown source field '" + key + "' for kind '" + kind + "'",
                    blockId, "source." + key, index);
        }
    }

    private Set<String> allowedSourceFields(String kind) {
        return switch (kind) {
            case ImageSource.KIND_RAW -> RAW_SOURCE_FIELDS;
            case ImageSource.KIND_URL -> URL_SOURCE_FIELDS;
            case ImageSource.KIND_PATH -> PATH_SOURCE_FIELDS;
            case ImageSource.KIND_ASSET -> ASSET_SOURCE_FIELDS;
            default -> null;
        };
    }

    private ImageSource parseRawSource(Map<?, ?> sourceMap, String blockId, int index) {
        Object rawBytes = sourceMap.get("bytes");
        if (rawBytes instanceof byte[] bytes) {
            return new ImageSource.Raw(bytes);
        }
        if (rawBytes instanceof String text) {
            return new ImageSource.Raw(decodeBase64(text, blockId, index));
        }
        throw MultimodalErrors.contentInvalid(
                "raw source requires 'bytes' as byte[] or a base64 string", blockId, "source.bytes", index);
    }

    private ImageSource parsePathSource(Map<?, ?> sourceMap, String blockId, int index) {
        String path = requireNonBlankString(sourceMap, "path", "source.path", blockId, index);
        Object rawVersion = sourceMap.get("version");
        if (rawVersion == null) {
            return new ImageSource.PathRef(path, null);
        }
        if (rawVersion instanceof String version && PATH_VERSION_PATTERN.matcher(version).matches()) {
            return new ImageSource.PathRef(path, version);
        }
        throw MultimodalErrors.contentInvalid(
                "path source 'version' must be in 'sha256:<64 hex chars>' form", blockId, "source.version", index);
    }

    private byte[] decodeBase64(String text, String blockId, int index) {
        try {
            return Base64.getMimeDecoder().decode(text);
        } catch (IllegalArgumentException exception) {
            throw MultimodalErrors.failed(MultimodalErrorReasons.CONTENT_INVALID,
                    "source.bytes is not valid base64", locators(blockId, "source.bytes", index), exception);
        }
    }

    private ContentBlock parseLegacyImageUrlBlock(Map<?, ?> map, String blockId, int index) {
        String url = legacyImageUrl(map, blockId, index);
        if (url.startsWith(DATA_URL_PREFIX)) {
            return decodeDataUrlImage(url, blockId, index);
        }
        String normalized = url.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return new ContentBlock.Image(blockId, new ImageSource.Url(url), null, null, null, null);
        }
        throw MultimodalErrors.contentInvalid(
                "legacy image_url must be a data: or http(s): URL; local files require source kind 'path'",
                blockId, "image_url.url", index);
    }

    private String legacyImageUrl(Map<?, ?> map, String blockId, int index) {
        Object rawImageUrl = map.get("image_url");
        if (rawImageUrl instanceof Map<?, ?> urlMap) {
            Object rawUrl = urlMap.get("url");
            if (rawUrl instanceof String text) {
                return text;
            }
            throw MultimodalErrors.contentInvalid(
                    "legacy image_url requires a string 'url' field", blockId, "image_url.url", index);
        }
        if (rawImageUrl instanceof String text) {
            return text;
        }
        throw MultimodalErrors.contentInvalid(
                "legacy image_url block requires an 'image_url' object or string", blockId, "image_url", index);
    }

    private ContentBlock decodeDataUrlImage(String url, String blockId, int index) {
        int marker = url.indexOf(BASE64_DATA_MARKER);
        if (marker < 0) {
            throw MultimodalErrors.contentInvalid(
                    "only base64 data URLs are supported in legacy image_url", blockId, "image_url.url", index);
        }
        String mediaType = url.substring(DATA_URL_PREFIX.length(), marker);
        if (mediaType.isBlank()) {
            throw MultimodalErrors.contentInvalid(
                    "data URL must declare a media type", blockId, "image_url.url", index);
        }
        byte[] bytes = decodeBase64Payload(url.substring(marker + BASE64_DATA_MARKER.length()), blockId, index);
        return new ContentBlock.Image(blockId, new ImageSource.Raw(bytes), mediaType, null, null, null);
    }

    private byte[] decodeBase64Payload(String payload, String blockId, int index) {
        try {
            return Base64.getMimeDecoder().decode(payload);
        } catch (IllegalArgumentException exception) {
            throw MultimodalErrors.failed(MultimodalErrorReasons.CONTENT_INVALID,
                    "data URL payload is not valid base64", locators(blockId, "image_url.url", index), exception);
        }
    }

    private Optional<String> optionalString(Map<?, ?> map, String key, String blockId, int index) {
        Object value = map.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String text) {
            return Optional.of(text);
        }
        throw MultimodalErrors.contentInvalid(
                "'" + key + "' must be a string", blockId, key, index);
    }

    private Map<String, Object> optionalProvenance(Map<?, ?> map, String blockId, int index) {
        Object value = map.get("provenance");
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> provenanceMap)) {
            throw MultimodalErrors.contentInvalid(
                    "'provenance' must be an object", blockId, "provenance", index);
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : provenanceMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw MultimodalErrors.contentInvalid(
                        "'provenance' keys must be strings", blockId, "provenance", index);
            }
            copy.put(key, entry.getValue());
        }
        return copy;
    }

    private String requireNonBlankString(Map<?, ?> map, String key, String field, String blockId, int index) {
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            throw MultimodalErrors.contentInvalid(
                    "'" + field + "' must be a string", blockId, field, index);
        }
        if (text.isBlank()) {
            throw MultimodalErrors.contentInvalid(
                    "'" + field + "' must not be blank", blockId, field, index);
        }
        return text;
    }

    private static Map<String, Object> locators(String blockId, String field, Integer index) {
        Map<String, Object> locators = new LinkedHashMap<>();
        locators.put("blockId", blockId);
        locators.put("field", field);
        locators.put("index", index);
        return locators;
    }

    private static String defaultId(int index) {
        return "block-" + index;
    }

    private static String describeType(Object item) {
        return item == null ? "null" : item.getClass().getName();
    }
}
