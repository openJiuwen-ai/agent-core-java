/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.multimodal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wire assembly tests of {@link MultimodalContentResolver}: order and count
 * preservation, per-item failure without faked images, request-level failure
 * surface.
 */
class MultimodalContentResolverTest {
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
    private static final String PNG_BASE64 = Base64.getEncoder().encodeToString(PNG_BYTES);
    private static final String PNG_DATA_URL = "data:image/png;base64," + PNG_BASE64;

    private final DefaultMultimodalContentNormalizer normalizer = new DefaultMultimodalContentNormalizer();

    @Test
    void resolve_textAndRawImage_producesOrderedWireParts() {
        List<ContentBlock> blocks = normalizer.normalize(List.of(
                Map.of("type", "text", "text", "what is this"),
                imageBlock("img-1", rawSource(PNG_BYTES), Map.of())));
        MultimodalContentResolver resolver =
                new MultimodalContentResolver(new ScriptedProvider(Map.of()));

        MultimodalContentResolver.ResolutionResult result = resolver.resolve(blocks, context());

        assertThat(result.contentParts()).hasSize(2);
        assertThat(result.contentParts().get(0))
                .containsEntry("type", "text")
                .containsEntry("text", "what is this");
        assertThat(result.contentParts().get(1)).containsEntry("type", "image_url");
        assertThat(imageUrlOf(result.contentParts().get(1))).containsEntry("url", PNG_DATA_URL);
        assertThat(result.itemErrors()).isEmpty();
    }

    @Test
    void resolve_descriptionBecomesCaptionPartBeforeImage() {
        List<ContentBlock> blocks = normalizer.normalize(List.of(
                imageBlock("img-1", rawSource(PNG_BYTES), Map.of("description", "first page"))));
        MultimodalContentResolver resolver =
                new MultimodalContentResolver(new ScriptedProvider(Map.of()));

        MultimodalContentResolver.ResolutionResult result = resolver.resolve(blocks, context());

        assertThat(result.contentParts()).hasSize(2);
        assertThat(result.contentParts().get(0))
                .containsEntry("type", "text")
                .containsEntry("text", "first page");
        assertThat(result.contentParts().get(1)).containsEntry("type", "image_url");
    }

    @Test
    void resolve_duplicateImages_areAllKeptInOrder() {
        List<Object> input = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            input.add(imageBlock("img-" + index, rawSource(PNG_BYTES), Map.of()));
        }
        List<ContentBlock> blocks = normalizer.normalize(input);
        MultimodalContentResolver resolver =
                new MultimodalContentResolver(new ScriptedProvider(Map.of()));

        MultimodalContentResolver.ResolutionResult result = resolver.resolve(blocks, context());

        assertThat(result.contentParts()).hasSize(20);
        assertThat(result.contentParts()).allSatisfy(part ->
                assertThat(imageUrlOf(part)).containsEntry("url", PNG_DATA_URL));
    }

    @Test
    void resolve_oneBrokenImage_othersContinueWithoutFakedPart() {
        List<ContentBlock> blocks = normalizer.normalize(List.of(
                imageBlock("img-good", rawSource(PNG_BYTES), Map.of()),
                imageBlock("img-bad", Map.of("kind", "path", "path", "/controlled/missing.png"), Map.of())));
        Map<String, BaseError> failures = Map.of("missing.png", MultimodalErrors.failed(
                MultimodalErrorReasons.IMAGE_LOAD_FAILED, "material cannot be read",
                locators("/controlled/missing.png", "read"), null));
        MultimodalContentResolver resolver = new MultimodalContentResolver(new ScriptedProvider(failures));

        MultimodalContentResolver.ResolutionResult result = resolver.resolve(blocks, context());

        assertThat(result.contentParts()).hasSize(2);
        assertThat(result.contentParts().get(0)).containsEntry("type", "image_url");
        assertThat(result.contentParts().get(1)).containsEntry("type", "text");
        assertThat(String.valueOf(result.contentParts().get(1).get("text")))
                .contains("img-bad").contains("input index 1")
                .contains(MultimodalErrorReasons.IMAGE_LOAD_FAILED);
        assertThat(result.itemErrors()).hasSize(1);
        assertThat(result.itemErrors().get(0))
                .containsEntry("index", 1)
                .containsEntry("blockId", "img-bad")
                .containsEntry("reason", MultimodalErrorReasons.IMAGE_LOAD_FAILED)
                .containsEntry("stage", "read")
                .containsEntry("sourceId", "/controlled/missing.png");
    }

    @Test
    void resolve_allMaterialFailed_requestFailsWithAggregatedItems() {
        List<ContentBlock> blocks = normalizer.normalize(List.of(
                imageBlock("img-url", Map.of("kind", "url", "url", "https://files.example/b.png"), Map.of())));
        Map<String, BaseError> failures = Map.of("files.example", MultimodalErrors.failed(
                MultimodalErrorReasons.SOURCE_UNRESOLVED, "kind=url is not supported",
                locators("https://files.example/b.png", "kind"), null));
        MultimodalContentResolver resolver = new MultimodalContentResolver(new ScriptedProvider(failures));

        assertThatThrownBy(() -> resolver.resolve(blocks, context()))
                .isInstanceOfSatisfying(BaseError.class, error -> {
                    assertThat(details(error)).containsEntry("reason",
                            MultimodalErrorReasons.SOURCE_UNRESOLVED);
                    assertThat(error.getMessage()).contains("all 1 material blocks failed");
                });
    }

    @Test
    void resolve_nullProvider_materialFailsWithSourceUnresolved() {
        List<ContentBlock> blocks = normalizer.normalize(List.of(
                imageBlock("img-1", rawSource(PNG_BYTES), Map.of())));
        MultimodalContentResolver resolver = new MultimodalContentResolver(null);

        assertThatThrownBy(() -> resolver.resolve(blocks, context()))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.SOURCE_UNRESOLVED));
    }

    @Test
    void resolve_videoAndToolResultBlocks_rejectedAtEntry() {
        List<ContentBlock> video = normalizer.normalize(List.of(
                videoBlock("vid-1", rawSource(PNG_BYTES))));
        MultimodalContentResolver resolver =
                new MultimodalContentResolver(new ScriptedProvider(Map.of()));

        assertThatThrownBy(() -> resolver.resolve(video, context()))
                .isInstanceOfSatisfying(BaseError.class, error -> {
                    assertThat(details(error))
                            .containsEntry("reason", MultimodalErrorReasons.CONTENT_INVALID)
                            .containsEntry("field", "type");
                    assertThat(error.getMessage()).contains("reserved and not supported");
                });
        List<ContentBlock> toolResult = normalizer.normalize(List.of(
                Map.of("type", "tool_result", "id", "tr-1", "toolCallId", "call-1",
                        "toolName", "read_file", "status", "success")));
        assertThatThrownBy(() -> resolver.resolve(toolResult, context()))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.CONTENT_INVALID)
                        .containsEntry("blockId", "tr-1"));
    }

    @Test
    void resolve_nonImageFileBlock_failsWithFileProcessingRequired() {
        List<ContentBlock> blocks = normalizer.normalize(List.of(
                fileBlock("file-1", rawSource("pdf-bytes".getBytes(StandardCharsets.UTF_8)),
                        Map.of("mediaType", "application/pdf", "filename", "report.pdf"))));
        MultimodalContentResolver resolver =
                new MultimodalContentResolver(new ScriptedProvider(Map.of()));

        assertThatThrownBy(() -> resolver.resolve(blocks, context()))
                .isInstanceOfSatisfying(BaseError.class, error -> {
                    assertThat(details(error))
                            .containsEntry("reason", MultimodalErrorReasons.FILE_PROCESSING_REQUIRED)
                            .containsEntry("blockId", "file-1")
                            .containsEntry("index", 0)
                            .containsEntry("filename", "report.pdf")
                            .containsEntry("mediaType", "application/pdf");
                });
    }

    @Test
    void resolve_imageMimeTypeFileBlock_convertedIntoImagePart() {
        List<ContentBlock> blocks = normalizer.normalize(List.of(
                fileBlock("file-2", rawSource(PNG_BYTES), Map.of("filename", "scan.png"))));
        MultimodalContentResolver resolver =
                new MultimodalContentResolver(new ScriptedProvider(Map.of()));

        MultimodalContentResolver.ResolutionResult result = resolver.resolve(blocks, context());

        assertThat(result.contentParts()).hasSize(1);
        assertThat(imageUrlOf(result.contentParts().get(0))).containsEntry("url", PNG_DATA_URL);
    }

    @Test
    void resolve_totalBudgetExceeded_failsOnlyTheOverflowingItem() {
        List<ContentBlock> blocks = normalizer.normalize(List.of(
                imageBlock("img-small", rawSource(PNG_BYTES), Map.of()),
                imageBlock("img-big", rawSource(new byte[64]), Map.of())));
        MaterialAccessContext tight = new MaterialAccessContext(List.of(), "/assets", 1024L, 32L,
                MaterialAccessContext.DEFAULT_READ_TIMEOUT_MILLIS, 0L);
        MultimodalContentResolver resolver =
                new MultimodalContentResolver(new ScriptedProvider(Map.of()));

        assertThat(blocks).hasSize(2);
        MultimodalContentResolver.ResolutionResult result = resolver.resolve(blocks, tight);

        assertThat(result.contentParts()).hasSize(2);
        assertThat(result.contentParts().get(0)).containsEntry("type", "image_url");
        assertThat(result.contentParts().get(1)).containsEntry("type", "text");
        assertThat(result.itemErrors()).hasSize(1);
        assertThat(result.itemErrors().get(0))
                .containsEntry("index", 1)
                .containsEntry("stage", "budget.total")
                .containsEntry("limit", 32L);
    }

    @Test
    void resolve_imageDetailHint_addedToWirePartWhenConfigured() {
        List<ContentBlock> blocks = normalizer.normalize(List.of(
                imageBlock("img-1", rawSource(PNG_BYTES), Map.of())));
        MultimodalContentResolver resolver =
                new MultimodalContentResolver(new ScriptedProvider(Map.of()), "low");

        MultimodalContentResolver.ResolutionResult result = resolver.resolve(blocks, context());

        assertThat(imageUrlOf(result.contentParts().get(0)))
                .containsEntry("url", PNG_DATA_URL)
                .containsEntry("detail", "low");
    }

    @Test
    void resolve_provenanceCollectedForFrameworkContext() {
        List<ContentBlock> blocks = normalizer.normalize(List.of(
                imageBlock("img-1", Map.of("kind", "asset", "assetId", "doc-1", "version", "v2"),
                        Map.of("filename", "a.png", "provenance", Map.of("originalSource", "upload:a.png")))));
        MultimodalContentResolver resolver =
                new MultimodalContentResolver(new ScriptedProvider(Map.of()));

        MultimodalContentResolver.ResolutionResult result = resolver.resolve(blocks, context());

        assertThat(result.provenance()).hasSize(1);
        assertThat(result.provenance().get(0))
                .containsEntry("blockId", "img-1")
                .containsEntry("index", 0)
                .containsEntry("sourceKind", "asset")
                .containsEntry("sourceId", "doc-1@v2")
                .containsEntry("version", "v2")
                .containsEntry("filename", "a.png");
        assertThat(provenanceOf(result.provenance().get(0)))
                .containsEntry("originalSource", "upload:a.png");
    }

    private static MaterialAccessContext context() {
        return new MaterialAccessContext(List.of(), "/assets",
                MaterialAccessContext.DEFAULT_MAX_IMAGE_BYTES,
                MaterialAccessContext.DEFAULT_MAX_TOTAL_IMAGE_BYTES,
                MaterialAccessContext.DEFAULT_READ_TIMEOUT_MILLIS, 0L);
    }

    private static Map<String, Object> rawSource(byte[] bytes) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "raw");
        source.put("bytes", bytes);
        return source;
    }

    private static Map<String, Object> imageBlock(String id, Map<String, Object> source,
                                                  Map<String, Object> extraFields) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "image");
        block.put("id", id);
        block.put("source", source);
        block.putAll(extraFields);
        return block;
    }

    private static Map<String, Object> videoBlock(String id, Map<String, Object> source) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "video");
        block.put("id", id);
        block.put("source", source);
        return block;
    }

    private static Map<String, Object> fileBlock(String id, Map<String, Object> source,
                                                 Map<String, Object> extraFields) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "file");
        block.put("id", id);
        block.put("source", source);
        block.putAll(extraFields);
        return block;
    }

    private static Map<String, Object> locators(String sourceId, String stage) {
        Map<String, Object> locators = new LinkedHashMap<>();
        locators.put("sourceId", sourceId);
        locators.put("stage", stage);
        return locators;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> imageUrlOf(Map<String, Object> part) {
        return (Map<String, Object>) part.get("image_url");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(BaseError error) {
        return (Map<String, Object>) error.getDetails();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> provenanceOf(Map<String, Object> entry) {
        return (Map<String, Object>) entry.get("provenance");
    }

    /**
     * Scripted provider: raw sources echo their own bytes; every other source
     * resolves as the fixed PNG sample. Sources whose id contains a key of
     * the failure map raise the scripted error instead.
     */
    private static final class ScriptedProvider implements ImageMaterialProvider {
        private final Map<String, BaseError> failuresBySourceIdPart;

        private ScriptedProvider(Map<String, BaseError> failuresBySourceIdPart) {
            this.failuresBySourceIdPart = failuresBySourceIdPart;
        }

        @Override
        public ResolvedImage resolve(ImageSource source, String declaredMediaType,
                                     MaterialAccessContext access) {
            for (Map.Entry<String, BaseError> failure : failuresBySourceIdPart.entrySet()) {
                if (source.sourceId().contains(failure.getKey())) {
                    throw failure.getValue();
                }
            }
            byte[] bytes = source instanceof ImageSource.Raw raw ? raw.bytes() : PNG_BYTES;
            return new ResolvedImage(bytes, "image/png", source.sourceId(), null);
        }
    }
}
