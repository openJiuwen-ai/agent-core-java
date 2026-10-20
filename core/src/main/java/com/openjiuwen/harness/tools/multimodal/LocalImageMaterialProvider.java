/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.multimodal;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.multimodal.ImageAssetStore;
import com.openjiuwen.core.foundation.llm.multimodal.ImageMaterialProvider;
import com.openjiuwen.core.foundation.llm.multimodal.ImageSource;
import com.openjiuwen.core.foundation.llm.multimodal.MaterialAccessContext;
import com.openjiuwen.core.foundation.llm.multimodal.MultimodalErrorReasons;
import com.openjiuwen.core.foundation.llm.multimodal.MultimodalErrors;
import com.openjiuwen.core.foundation.llm.multimodal.ResolvedImage;
import com.openjiuwen.core.sysop.Cwd;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link ImageMaterialProvider} of the image domain.
 *
 * <p>Resolves {@code raw}, {@code path} and {@code asset} sources before the
 * request is sent; {@code url} sources fail explicitly with
 * {@code SOURCE_UNRESOLVED} in this release and are never passed through to a
 * model. Path sources are canonicalized and restricted to the configured
 * allowed roots, read with a size pre-check plus a bounded streaming loop
 * with a deadline, and the effective MIME type is judged from the actual
 * bytes first.</p>
 *
 * @since 0.1.17
 */
public final class LocalImageMaterialProvider implements ImageMaterialProvider {
    private static final int READ_CHUNK_SIZE = 64 * 1024;
    private static final String SHA_256 = "SHA-256";
    private static final String PATH_VERSION_PREFIX = "sha256:";

    private final ImageAssetStore assetStore;

    /**
     * Create a provider without an asset store; {@code asset} sources then
     * fail with {@code SOURCE_UNRESOLVED}.
     */
    public LocalImageMaterialProvider() {
        this(null);
    }

    /**
     * Create a provider backed by the given asset store.
     *
     * @param assetStore version-pinned asset store; may be {@code null}
     */
    public LocalImageMaterialProvider(ImageAssetStore assetStore) {
        this.assetStore = assetStore;
    }

    /**
     * Resolve raw, path and asset sources within the context budgets; url sources
     * fail explicitly in this release.
     *
     * @param source explicit source of the block, never {@code null}
     * @param declaredMediaType caller-declared MIME type used only when the actual
     *                          bytes are not recognizable; may be {@code null}
     * @param access per-request access and budget context, never {@code null}
     * @return resolved material with the effective MIME type, never {@code null}
     */
    @Override
    public ResolvedImage resolve(ImageSource source, String declaredMediaType, MaterialAccessContext access) {
        if (source == null) {
            throw sourceUnresolved(null, "source", "source is null", null);
        }
        if (source instanceof ImageSource.Raw raw) {
            return resolveRaw(raw, declaredMediaType, access);
        }
        if (source instanceof ImageSource.Url url) {
            throw sourceUnresolved(url.url(), "kind",
                    "kind=url is not supported in this release; download the material upstream"
                            + " and pass kind=raw or kind=path", null);
        }
        if (source instanceof ImageSource.PathRef path) {
            return resolvePath(path, declaredMediaType, access);
        }
        if (source instanceof ImageSource.AssetRef asset) {
            return resolveAsset(asset, declaredMediaType, access);
        }
        throw sourceUnresolved(source.sourceId(), "kind", "unsupported source kind " + source.kind(), null);
    }

    private ResolvedImage resolveRaw(ImageSource.Raw raw, String declaredMediaType, MaterialAccessContext access) {
        byte[] bytes = raw.bytes();
        if (bytes.length == 0) {
            throw loadFailed(raw.sourceId(), "raw", "raw source is empty", null);
        }
        if (bytes.length > access.maxImageBytes()) {
            throw budgetFailure(raw.sourceId(), "budget.image", access.maxImageBytes(), bytes.length);
        }
        String mediaType = effectiveMimeType(bytes, declaredMediaType, raw.sourceId(), "raw");
        return new ResolvedImage(bytes, mediaType, raw.sourceId(), null);
    }

    private ResolvedImage resolvePath(ImageSource.PathRef path, String declaredMediaType,
                                      MaterialAccessContext access) {
        Path canonical = canonicalize(path.path(), path.sourceId());
        validateAccess(canonical, access, path.sourceId());
        if (!Files.isRegularFile(canonical)) {
            throw loadFailed(path.sourceId(), "stat", "path is not a regular file", null);
        }
        byte[] bytes = readBounded(canonical, path.sourceId(), access);
        validatePathVersion(bytes, path);
        String mediaType = effectiveMimeType(bytes, declaredMediaType, path.sourceId(), "mime");
        return new ResolvedImage(bytes, mediaType, path.path(), path.version());
    }

    private ResolvedImage resolveAsset(ImageSource.AssetRef asset, String declaredMediaType,
                                       MaterialAccessContext access) {
        if (assetStore == null) {
            throw sourceUnresolved(asset.sourceId(), "asset-store", "no asset store is configured", null);
        }
        OptionalLong storedSize = assetStore.sizeOf(asset.assetId(), asset.version());
        if (storedSize.isPresent()) {
            if (storedSize.getAsLong() > access.maxImageBytes()) {
                throw budgetFailure(asset.sourceId(), "budget.image",
                        access.maxImageBytes(), storedSize.getAsLong());
            }
            if (storedSize.getAsLong() > access.remainingTotalBytes()) {
                throw budgetFailure(asset.sourceId(), "budget.total",
                        access.remainingTotalBytes(), storedSize.getAsLong());
            }
        }
        Optional<ResolvedImage> stored = assetStore.get(asset.assetId(), asset.version());
        if (stored.isEmpty()) {
            throw assetNotFound(asset);
        }
        ResolvedImage candidate = stored.get();
        String declared = firstNonBlank(declaredMediaType, candidate.mediaType());
        String mediaType = effectiveMimeType(candidate.bytes(), declared, asset.sourceId(), "asset");
        return new ResolvedImage(candidate.bytes(), mediaType, asset.sourceId(), asset.version());
    }

    private BaseError assetNotFound(ImageSource.AssetRef asset) {
        List<String> versions = assetStore.listVersions(asset.assetId());
        if (versions.isEmpty()) {
            return sourceUnresolved(asset.sourceId(), "asset", "asset is not found", null);
        }
        Map<String, Object> locators = new LinkedHashMap<>();
        locators.put("sourceId", asset.sourceId());
        locators.put("stage", "asset");
        locators.put("expected", asset.version());
        locators.put("actualVersions", versions);
        return MultimodalErrors.failed(MultimodalErrorReasons.VERSION_CONFLICT,
                "pinned asset version is not stored", locators, null);
    }

    private Path canonicalize(String rawPath, String sourceId) {
        try {
            Path parsed = Path.of(rawPath);
            Path based = parsed.isAbsolute() ? parsed : Path.of(Cwd.getCwd()).resolve(parsed);
            return based.toFile().getCanonicalFile().toPath();
        } catch (InvalidPathException | IOException exception) {
            throw sourceUnresolved(sourceId, "path", "path cannot be resolved", exception);
        }
    }

    private void validateAccess(Path canonical, MaterialAccessContext access, String sourceId) {
        List<String> roots = access.allowedRoots();
        if (roots.isEmpty()) {
            throw sourceUnresolved(sourceId, "access",
                    "no allowed roots are configured for path sources", null);
        }
        String canonicalPath = canonical.toString();
        for (String root : roots) {
            if (isUnderRoot(canonicalPath, root)) {
                return;
            }
        }
        throw sourceUnresolved(sourceId, "access", "path escapes the controlled working directories", null);
    }

    private static boolean isUnderRoot(String canonicalPath, String root) {
        String prefix = root.endsWith(File.separator) ? root : root + File.separator;
        return canonicalPath.startsWith(prefix);
    }

    private byte[] readBounded(Path canonical, String sourceId, MaterialAccessContext access) {
        long size = declaredSize(canonical, sourceId);
        if (size > access.maxImageBytes()) {
            throw budgetFailure(sourceId, "budget.image", access.maxImageBytes(), size);
        }
        if (size > access.remainingTotalBytes()) {
            throw budgetFailure(sourceId, "budget.total", access.remainingTotalBytes(), size);
        }
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(access.readTimeoutMillis());
        try (InputStream input = Files.newInputStream(canonical);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream((int) Math.min(size, Integer.MAX_VALUE))) {
            return readUntilEnd(input, buffer, deadlineNanos, sourceId, access.maxImageBytes());
        } catch (IOException exception) {
            throw loadFailed(sourceId, "read", "material cannot be read", exception);
        }
    }

    private byte[] readUntilEnd(InputStream input, ByteArrayOutputStream buffer, long deadlineNanos,
                                String sourceId, long maxImageBytes) throws IOException {
        byte[] chunk = new byte[READ_CHUNK_SIZE];
        long total = 0L;
        while (true) {
            int read = input.read(chunk);
            if (read < 0) {
                break;
            }
            total += read;
            if (total > maxImageBytes) {
                throw budgetFailure(sourceId, "budget.image", maxImageBytes, total);
            }
            buffer.write(chunk, 0, read);
            if (System.nanoTime() >= deadlineNanos) {
                throw loadFailed(sourceId, "timeout", "material read exceeded the deadline", null);
            }
        }
        return buffer.toByteArray();
    }

    private long declaredSize(Path canonical, String sourceId) {
        try {
            return Files.size(canonical);
        } catch (IOException exception) {
            throw loadFailed(sourceId, "stat", "file size cannot be read", exception);
        }
    }

    private void validatePathVersion(byte[] bytes, ImageSource.PathRef path) {
        if (path.version() == null) {
            return;
        }
        String actual = PATH_VERSION_PREFIX + sha256Hex(bytes, path.sourceId());
        if (!actual.equalsIgnoreCase(path.version())) {
            Map<String, Object> locators = new LinkedHashMap<>();
            locators.put("sourceId", path.sourceId());
            locators.put("stage", "version");
            locators.put("expected", path.version());
            locators.put("actual", actual);
            throw MultimodalErrors.failed(MultimodalErrorReasons.VERSION_CONFLICT,
                    "pinned path version does not match the actual content", locators, null);
        }
    }

    private String sha256Hex(byte[] bytes, String sourceId) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw loadFailed(sourceId, "version", "SHA-256 is unavailable", exception);
        }
    }

    private String effectiveMimeType(byte[] bytes, String declaredMediaType, String sourceId, String stage) {
        Optional<String> sniffed = ImageMimeSniffer.sniff(bytes);
        if (sniffed.isPresent()) {
            return sniffed.get();
        }
        if (declaredMediaType != null && !declaredMediaType.isBlank()) {
            return declaredMediaType.trim().toLowerCase(Locale.ROOT);
        }
        throw processFailed(sourceId, stage,
                "media type cannot be judged from the actual bytes; declare mediaType explicitly", null);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static BaseError sourceUnresolved(String sourceId, String stage, String message, Throwable cause) {
        return MultimodalErrors.failed(MultimodalErrorReasons.SOURCE_UNRESOLVED, message,
                locators(sourceId, stage), cause);
    }

    private static BaseError loadFailed(String sourceId, String stage, String message, Throwable cause) {
        return MultimodalErrors.failed(MultimodalErrorReasons.IMAGE_LOAD_FAILED, message,
                locators(sourceId, stage), cause);
    }

    private static BaseError processFailed(String sourceId, String stage, String message, Throwable cause) {
        return MultimodalErrors.failed(MultimodalErrorReasons.IMAGE_PROCESS_FAILED, message,
                locators(sourceId, stage), cause);
    }

    private static BaseError budgetFailure(String sourceId, String stage, long limit, long actual) {
        Map<String, Object> locators = locators(sourceId, stage);
        locators.put("limit", limit);
        locators.put("actual", actual);
        return MultimodalErrors.failed(MultimodalErrorReasons.IMAGE_LOAD_FAILED,
                "material budget exceeded at stage " + stage, locators, null);
    }

    private static Map<String, Object> locators(String sourceId, String stage) {
        Map<String, Object> locators = new LinkedHashMap<>();
        locators.put("sourceId", sourceId);
        locators.put("stage", stage);
        return locators;
    }
}
