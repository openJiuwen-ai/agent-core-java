/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.multimodal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.multimodal.ImageSource;
import com.openjiuwen.core.foundation.llm.multimodal.MaterialAccessContext;
import com.openjiuwen.core.foundation.llm.multimodal.MultimodalErrorReasons;
import com.openjiuwen.core.foundation.llm.multimodal.ResolvedImage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Offline acceptance tests of the default material provider covering source
 * kinds, access control, budgets, timeout, MIME judgement and version
 * pinning.
 */
class LocalImageMaterialProviderTest {
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13
    };
    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2};
    private static final byte[] NOT_IMAGE_BYTES = "just some text".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    @Test
    void resolve_rawSource_judgesMimeFromBytesOverDeclaration() {
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider();

        ResolvedImage resolved = provider.resolve(new ImageSource.Raw(PNG_HEADER), "image/jpeg", context());

        assertThat(resolved.mediaType()).isEqualTo("image/png");
        assertThat(resolved.bytes()).isEqualTo(PNG_HEADER);
    }

    @Test
    void resolve_rawUnknownBytes_usesExplicitDeclarationOrFail() {
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider();

        ResolvedImage declared = provider.resolve(new ImageSource.Raw(NOT_IMAGE_BYTES), "image/webp", context());
        assertThat(declared.mediaType()).isEqualTo("image/webp");

        assertThatThrownBy(() -> provider.resolve(new ImageSource.Raw(NOT_IMAGE_BYTES), null, context()))
                .isInstanceOfSatisfying(BaseError.class, error -> {
                    assertThat(details(error)).containsEntry("reason",
                            MultimodalErrorReasons.IMAGE_PROCESS_FAILED);
                    assertThat(error.getMessage()).contains("declare mediaType explicitly");
                });
    }

    @Test
    void resolve_rawEmptyOrOverBudget_failsExplicitly() {
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider();

        assertThatThrownBy(() -> provider.resolve(new ImageSource.Raw(new byte[0]), null, context()))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.IMAGE_LOAD_FAILED)
                        .containsEntry("stage", "raw"));
        assertThatThrownBy(() -> provider.resolve(new ImageSource.Raw(PNG_HEADER), null,
                contextWithLimits(4L, 1024L, 1000L)))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.IMAGE_LOAD_FAILED)
                        .containsEntry("stage", "budget.image")
                        .containsEntry("limit", 4L)
                        .containsEntry("actual", (long) PNG_HEADER.length));
    }

    @Test
    void resolve_urlSource_failsWithSourceUnresolved() throws IOException {
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider();

        assertThatThrownBy(() -> provider.resolve(
                new ImageSource.Url("https://files.example/b.png"), null, context()))
                .isInstanceOfSatisfying(BaseError.class, error -> {
                    assertThat(details(error))
                            .containsEntry("reason", MultimodalErrorReasons.SOURCE_UNRESOLVED)
                            .containsEntry("stage", "kind");
                    assertThat(error.getMessage()).contains("kind=url is not supported");
                });
    }

    @Test
    void resolve_pathInsideRoot_readsAndSniffs() throws IOException {
        Path imageFile = writePng(tempDir.resolve("inside.png"));
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider();

        ResolvedImage resolved = provider.resolve(
                new ImageSource.PathRef(imageFile.toString(), null), null, context());

        assertThat(resolved.bytes()).isEqualTo(Files.readAllBytes(imageFile));
        assertThat(resolved.mediaType()).isEqualTo("image/png");
    }

    @Test
    void resolve_pathOutsideRoot_denied() throws IOException {
        Path outside = writePng(tempDir.resolve("outside-dir").resolve("a.png"));
        Path root = Files.createDirectories(tempDir.resolve("root"));
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider();

        assertThatThrownBy(() -> provider.resolve(new ImageSource.PathRef(outside.toString(), null),
                null, contextWithRoots(List.of(root.toString()))))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.SOURCE_UNRESOLVED)
                        .containsEntry("stage", "access"));
    }

    @Test
    void resolve_pathTraversalEscape_denied() throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("root"));
        Files.createDirectories(root.resolve("sub"));
        Path escaped = writePng(tempDir.resolve("root").resolve("escaped.png"));
        String traversal = root.resolve("sub").resolve("..").resolve("escaped.png").toString();
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider();

        assertThat(escaped).exists();
        assertThatThrownBy(() -> provider.resolve(new ImageSource.PathRef(traversal, null),
                null, contextWithRoots(List.of(root.resolve("sub").toString()))))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.SOURCE_UNRESOLVED)
                        .containsEntry("stage", "access"));
    }

    @Test
    void resolve_pathMissingFile_failsImageLoad() {
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider();
        String missing = tempDir.resolve("missing.png").toString();

        assertThatThrownBy(() -> provider.resolve(new ImageSource.PathRef(missing, null), null, context()))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.IMAGE_LOAD_FAILED)
                        .containsEntry("stage", "stat"));
    }

    @Test
    void resolve_pathSizePrecheck_rejectsBeforeReading() throws IOException {
        Path big = tempDir.resolve("big.png");
        Files.write(big, PNG_HEADER);
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider();

        assertThatThrownBy(() -> provider.resolve(new ImageSource.PathRef(big.toString(), null),
                null, contextWithLimits(4L, 1024L, 10_000L)))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.IMAGE_LOAD_FAILED)
                        .containsEntry("stage", "budget.image")
                        .containsEntry("limit", 4L)
                        .containsEntry("actual", (long) PNG_HEADER.length));
    }

    @Test
    void resolve_pathTotalBudget_prechecksAgainstRemaining() throws IOException {
        Path imageFile = writePng(tempDir.resolve("total.png"));
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider();
        MaterialAccessContext consumed = contextWithLimits(1024L, 16L, 10_000L).withConsumedBytes(10L);

        assertThatThrownBy(() -> provider.resolve(new ImageSource.PathRef(imageFile.toString(), null),
                null, consumed))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.IMAGE_LOAD_FAILED)
                        .containsEntry("stage", "budget.total"));
    }

    @Test
    void resolve_pathReadDeadline_expiresDeterministically() throws IOException {
        Path imageFile = writePng(tempDir.resolve("slow.png"));
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider();

        assertThatThrownBy(() -> provider.resolve(new ImageSource.PathRef(imageFile.toString(), null),
                null, contextWithLimits(1024L, 1024L, 0L)))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.IMAGE_LOAD_FAILED)
                        .containsEntry("stage", "timeout"));
    }

    @Test
    void resolve_pathVersionPin_mismatchConflictsMatchPasses() throws IOException {
        Path imageFile = writePng(tempDir.resolve("pinned.png"));
        byte[] bytes = Files.readAllBytes(imageFile);
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider();
        String correctVersion = "sha256:" + sha256Hex(bytes);
        String wrongVersion = "sha256:" + sha256Hex(NOT_IMAGE_BYTES);

        ResolvedImage resolved = provider.resolve(
                new ImageSource.PathRef(imageFile.toString(), correctVersion), null, context());
        assertThat(resolved.version()).isEqualTo(correctVersion);

        assertThatThrownBy(() -> provider.resolve(
                new ImageSource.PathRef(imageFile.toString(), wrongVersion), null, context()))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.VERSION_CONFLICT)
                        .containsEntry("expected", wrongVersion)
                        .containsEntry("actual", correctVersion));
    }

    @Test
    void resolve_pathMimeJudgedFromBytesDespiteExtension() throws IOException {
        Path mislabeled = tempDir.resolve("picture.txt");
        Files.write(mislabeled, JPEG_HEADER);
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider();

        ResolvedImage resolved = provider.resolve(
                new ImageSource.PathRef(mislabeled.toString(), null), "image/png", context());

        assertThat(resolved.mediaType()).isEqualTo("image/jpeg");
    }

    @Test
    void resolve_assetVersionPinned_neverReadsNewerVersion() throws IOException {
        Path assetRoot = Files.createDirectories(tempDir.resolve("assets"));
        FileSystemImageAssetStore store = new FileSystemImageAssetStore(assetRoot);
        store.put("doc-1", "v1", PNG_HEADER, "image/png");
        store.put("doc-1", "v2", JPEG_HEADER, null);
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider(store);

        ResolvedImage v1 = provider.resolve(new ImageSource.AssetRef("doc-1", "v1"), null, context());
        assertThat(v1.bytes()).isEqualTo(PNG_HEADER);
        assertThat(v1.mediaType()).isEqualTo("image/png");

        ResolvedImage v2 = provider.resolve(new ImageSource.AssetRef("doc-1", "v2"), null, context());
        assertThat(v2.bytes()).isEqualTo(JPEG_HEADER);
        assertThat(v2.mediaType()).isEqualTo("image/jpeg");

        assertThatThrownBy(() -> provider.resolve(new ImageSource.AssetRef("doc-1", "v3"), null, context()))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.VERSION_CONFLICT)
                        .containsEntry("expected", "v3")
                        .containsEntry("actualVersions", List.of("v1", "v2")));
        assertThatThrownBy(() -> provider.resolve(new ImageSource.AssetRef("missing", "v1"), null, context()))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.SOURCE_UNRESOLVED)
                        .containsEntry("stage", "asset"));
    }

    @Test
    void resolve_assetWithoutStore_failsSourceUnresolved() {
        LocalImageMaterialProvider provider = new LocalImageMaterialProvider();

        assertThatThrownBy(() -> provider.resolve(new ImageSource.AssetRef("doc-1", "v1"), null, context()))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.SOURCE_UNRESOLVED)
                        .containsEntry("stage", "asset-store"));
    }

    @Test
    void put_existingVersion_isImmutableAndUnsafeIdsRejected() throws IOException {
        Path assetRoot = Files.createDirectories(tempDir.resolve("assets2"));
        FileSystemImageAssetStore store = new FileSystemImageAssetStore(assetRoot);
        store.put("doc-2", "v1", PNG_HEADER, "image/png");

        assertThatThrownBy(() -> store.put("doc-2", "v1", JPEG_HEADER, null))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.VERSION_CONFLICT));
        assertThatThrownBy(() -> store.put("../escape", "v1", PNG_HEADER, null))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.CONTENT_INVALID)
                        .containsEntry("field", "assetId"));
        assertThat(store.get("doc-2", "v1")).isPresent();
        assertThat(store.listVersions("doc-2")).containsExactly("v1");
    }

    @Test
    void sniff_coversCommonImageContainers() {
        assertThat(ImageMimeSniffer.sniff(PNG_HEADER)).contains("image/png");
        assertThat(ImageMimeSniffer.sniff(JPEG_HEADER)).contains("image/jpeg");
        assertThat(ImageMimeSniffer.sniff("GIF89a----".getBytes(StandardCharsets.US_ASCII)))
                .contains("image/gif");
        assertThat(ImageMimeSniffer.sniff("BM----".getBytes(StandardCharsets.US_ASCII)))
                .contains("image/bmp");
        assertThat(ImageMimeSniffer.sniff("RIFF\0\0\0\0WEBP".getBytes(StandardCharsets.US_ASCII)))
                .contains("image/webp");
        assertThat(ImageMimeSniffer.sniff("<svg xmlns='x'></svg>".getBytes(StandardCharsets.UTF_8)))
                .contains("image/svg+xml");
        assertThat(ImageMimeSniffer.sniff(NOT_IMAGE_BYTES)).isEmpty();
        assertThat(ImageMimeSniffer.sniff(null)).isEmpty();
        assertThat(ImageMimeSniffer.sniff(new byte[0])).isEmpty();
    }

    private Path writePng(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, PNG_HEADER);
        return file;
    }

    private MaterialAccessContext context() {
        return contextWithRoots(List.of(tempDir.toString()));
    }

    private MaterialAccessContext contextWithRoots(List<String> roots) {
        return new MaterialAccessContext(roots, tempDir.resolve("assets").toString(),
                MaterialAccessContext.DEFAULT_MAX_IMAGE_BYTES,
                MaterialAccessContext.DEFAULT_MAX_TOTAL_IMAGE_BYTES,
                MaterialAccessContext.DEFAULT_READ_TIMEOUT_MILLIS, 0L);
    }

    private MaterialAccessContext contextWithLimits(long maxImage, long maxTotal, long timeoutMillis) {
        return new MaterialAccessContext(List.of(tempDir.toString()), tempDir.resolve("assets").toString(),
                maxImage, maxTotal, timeoutMillis, 0L);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            return fail(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(BaseError error) {
        return (Map<String, Object>) error.getDetails();
    }
}
