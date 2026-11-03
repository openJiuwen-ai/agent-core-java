/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.multimodal;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.multimodal.ImageAssetStore;
import com.openjiuwen.core.foundation.llm.multimodal.MultimodalErrorReasons;
import com.openjiuwen.core.foundation.llm.multimodal.MultimodalErrors;
import com.openjiuwen.core.foundation.llm.multimodal.ResolvedImage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * File-system {@link ImageAssetStore} rooted at the workspace-bound asset
 * root.
 *
 * <p>Layout: {@code <root>/<assetId>/<version>/content.bin} plus an optional
 * {@code mime.txt} sidecar. A stored version is immutable: writing an
 * existing version fails with {@code VERSION_CONFLICT}, so one asset id can
 * never silently read new content. Identifiers are restricted to safe path
 * segments; the global temporary directory is never used as an implicit
 * shared library.</p>
 *
 * @since 0.1.17
 */
public final class FileSystemImageAssetStore implements ImageAssetStore {
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final String CONTENT_FILE_NAME = "content.bin";
    private static final String MIME_FILE_NAME = "mime.txt";
    private static final String TEMP_FILE_PREFIX = "content-";
    private static final String TEMP_FILE_SUFFIX = ".tmp";

    private final Path root;

    /**
     * Create a store rooted at the given directory.
     *
     * @param root asset root directory bound to the execution workspace,
     *             never {@code null}
     */
    public FileSystemImageAssetStore(Path root) {
        if (root == null) {
            throw MultimodalErrors.contentInvalid("asset store root is null", null, "assetRoot", null);
        }
        this.root = root;
    }

    /**
     * Read the pinned version from the {@code assetId/version} directory layout.
     *
     * @param assetId asset identifier, never {@code null} or blank
     * @param version pinned version, never {@code null} or blank
     * @return stored material, or empty when the content file is absent
     */
    @Override
    public Optional<ResolvedImage> get(String assetId, String version) {
        Path directory = resolveVersionDirectory(assetId, version);
        Path content = directory.resolve(CONTENT_FILE_NAME);
        if (!Files.isRegularFile(content)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(content);
            return Optional.of(new ResolvedImage(bytes, readMediaType(directory).orElse(null),
                    assetId + "@" + version, version));
        } catch (IOException exception) {
            throw loadFailed(assetId + "@" + version, "asset-read", "asset content cannot be read", exception);
        }
    }

    /**
     * Return the stored content size without reading the bytes.
     *
     * @param assetId asset identifier, never {@code null} or blank
     * @param version pinned version, never {@code null} or blank
     * @return byte size, or empty when absent or not readable
     */
    @Override
    public OptionalLong sizeOf(String assetId, String version) {
        Path content = resolveVersionDirectory(assetId, version).resolve(CONTENT_FILE_NAME);
        if (!Files.isRegularFile(content)) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Files.size(content));
        } catch (IOException exception) {
            return OptionalLong.empty();
        }
    }

    /**
     * List the stored version directories of one asset in sorted order.
     *
     * @param assetId asset identifier, never {@code null} or blank
     * @return sorted version names, empty when the asset directory is absent
     */
    @Override
    public List<String> listVersions(String assetId) {
        validateSegment(assetId, "assetId");
        Path assetDirectory = root.resolve(assetId);
        if (!Files.isDirectory(assetDirectory)) {
            return List.of();
        }
        List<String> versions = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(assetDirectory)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    versions.add(entry.getFileName().toString());
                }
            }
        } catch (IOException exception) {
            throw loadFailed(assetId, "asset-list", "asset versions cannot be listed", exception);
        }
        Collections.sort(versions);
        return versions;
    }

    /**
     * Store one immutable version; an existing version is a conflict, never an overwrite.
     *
     * @param assetId asset identifier, never {@code null} or blank
     * @param version pinned version, never {@code null} or blank
     * @param bytes material bytes, never {@code null}
     * @param mediaType declared MIME type; may be {@code null}
     */
    @Override
    public void put(String assetId, String version, byte[] bytes, String mediaType) {
        validateSegment(assetId, "assetId");
        validateSegment(version, "version");
        if (bytes == null) {
            throw MultimodalErrors.contentInvalid("asset bytes are null", null, "bytes", null);
        }
        Path directory = root.resolve(assetId).resolve(version);
        Path content = directory.resolve(CONTENT_FILE_NAME);
        if (Files.exists(content)) {
            throw versionConflict(assetId + "@" + version,
                    "asset version is immutable and already exists");
        }
        try {
            Files.createDirectories(directory);
            writeMimeSidecar(directory, mediaType);
            Path temporary = Files.createTempFile(directory, TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
            Files.write(temporary, bytes);
            moveIntoPlace(temporary, content);
        } catch (IOException exception) {
            throw loadFailed(assetId + "@" + version, "asset-write", "asset cannot be stored", exception);
        }
    }

    private void moveIntoPlace(Path temporary, Path content) throws IOException {
        try {
            Files.move(temporary, content, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailure) {
            // File systems without atomic move support fall back to a plain
            // rename; the temporary file is already fully written.
            Files.move(temporary, content);
        }
    }

    private void writeMimeSidecar(Path directory, String mediaType) throws IOException {
        if (mediaType == null || mediaType.isBlank()) {
            return;
        }
        Files.write(directory.resolve(MIME_FILE_NAME), mediaType.getBytes(StandardCharsets.UTF_8));
    }

    private Optional<String> readMediaType(Path directory) {
        Path mimeFile = directory.resolve(MIME_FILE_NAME);
        if (!Files.isRegularFile(mimeFile)) {
            return Optional.empty();
        }
        try {
            String mediaType = Files.readString(mimeFile, StandardCharsets.UTF_8).trim();
            return mediaType.isEmpty() ? Optional.empty() : Optional.of(mediaType);
        } catch (IOException exception) {
            // A missing or unreadable sidecar only drops the declared media
            // type; byte sniffing and the caller declaration still apply.
            return Optional.empty();
        }
    }

    private Path resolveVersionDirectory(String assetId, String version) {
        validateSegment(assetId, "assetId");
        validateSegment(version, "version");
        return root.resolve(assetId).resolve(version);
    }

    private static void validateSegment(String value, String field) {
        if (value == null || !SAFE_SEGMENT.matcher(value).matches()) {
            throw MultimodalErrors.contentInvalid(
                    "asset " + field + " must match [A-Za-z0-9][A-Za-z0-9._-]{0,127}", null, field, null);
        }
    }

    private static BaseError loadFailed(
            String sourceId, String stage, String message, Throwable cause) {
        Map<String, Object> locators = new LinkedHashMap<>();
        locators.put("sourceId", sourceId);
        locators.put("stage", stage);
        return MultimodalErrors.failed(MultimodalErrorReasons.IMAGE_LOAD_FAILED, message, locators, cause);
    }

    private static BaseError versionConflict(String sourceId, String message) {
        Map<String, Object> locators = new LinkedHashMap<>();
        locators.put("sourceId", sourceId);
        locators.put("stage", "asset");
        return MultimodalErrors.failed(MultimodalErrorReasons.VERSION_CONFLICT, message, locators, null);
    }
}
