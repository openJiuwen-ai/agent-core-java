/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.multimodal;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves normalized content blocks into Chat Completions content parts.
 *
 * <p>The resolver keeps input order and count: images are never sorted,
 * deduplicated or removed. Per-item resolution failures never fake an image
 * part; the failed position is represented by an explicit text note carrying
 * the block id, input index and reason, while the remaining items continue.
 * When every material block of the request fails, the whole request fails
 * explicitly instead of sending a silently degraded request.</p>
 *
 * <p>Non-image file blocks fail the whole request with
 * {@code FILE_PROCESSING_REQUIRED} because this release has no office or
 * document processing chain; video and tool_result blocks are rejected at
 * the entry as well.</p>
 *
 * @since 0.1.17
 */
public final class MultimodalContentResolver {
    private static final LoggerProtocol LOGGER = Loggers.LLM;

    private final ImageMaterialProvider provider;
    private final String imageDetail;

    /**
     * Create a resolver without a wire {@code detail} hint.
     *
     * @param provider injected material provider; {@code null} makes every
     *                 material block fail with {@code SOURCE_UNRESOLVED}
     */
    public MultimodalContentResolver(ImageMaterialProvider provider) {
        this(provider, null);
    }

    /**
     * Create a resolver with an explicit wire {@code detail} hint.
     *
     * @param provider injected material provider; may be {@code null}
     * @param imageDetail Chat Completions {@code image_url.detail} value such
     *                    as {@code low}; {@code null} or blank omits the field
     */
    public MultimodalContentResolver(ImageMaterialProvider provider, String imageDetail) {
        this.provider = provider;
        this.imageDetail = imageDetail;
    }

    /**
     * Resolve ordered blocks into wire content parts.
     *
     * @param blocks normalized blocks, never {@code null}
     * @param access per-request access and budget context, never {@code null}
     * @return wire parts plus per-item errors and provenance entries
     * @throws BaseError when the block type is not processable at the entry,
     *         a non-image file block appears, or every material block fails
     */
    public ResolutionResult resolve(List<ContentBlock> blocks, MaterialAccessContext access) {
        if (blocks == null) {
            throw MultimodalErrors.contentInvalid("content block list is null", null, null, null);
        }
        ResolveState state = new ResolveState(access);
        for (int index = 0; index < blocks.size(); index++) {
            appendBlock(blocks.get(index), index, state);
        }
        state.failIfNoMaterialResolved();
        return new ResolutionResult(List.copyOf(state.contentParts),
                List.copyOf(state.itemErrors), List.copyOf(state.provenance));
    }

    private void appendBlock(ContentBlock block, int index, ResolveState state) {
        if (block == null) {
            throw MultimodalErrors.contentInvalid("content block is null", null, "block", index);
        }
        if (block instanceof ContentBlock.Text text) {
            state.addText(text.text());
        } else if (block instanceof ContentBlock.Image image) {
            resolveMaterial(MaterialRequest.ofImage(image), index, state);
        } else if (block instanceof ContentBlock.FileRef file) {
            resolveFileBlock(file, index, state);
        } else if (block instanceof ContentBlock.Video video) {
            throw MultimodalErrors.contentInvalid(
                    "video content blocks are reserved and not supported in this release",
                    video.id(), "type", index);
        } else if (block instanceof ContentBlock.ToolResult result) {
            throw MultimodalErrors.contentInvalid(
                    "tool_result blocks are only supported on the tool-result channel",
                    result.id(), "type", index);
        } else {
            throw MultimodalErrors.contentInvalid(
                    "unsupported content block " + block.getClass().getName(), block.id(), "type", index);
        }
    }

    private void resolveFileBlock(ContentBlock.FileRef file, int index, ResolveState state) {
        MaterialRequest request = MaterialRequest.ofFile(file);
        String declared = file.mediaType();
        if (declared != null && !declared.isBlank() && !isImageMimeType(declared)) {
            throw fileProcessingRequired(request, declared, index);
        }
        resolveMaterial(request, index, state);
    }

    private void resolveMaterial(MaterialRequest request, int index, ResolveState state) {
        state.countMaterial();
        state.addProvenance(request, index);
        if (request.source() == null) {
            state.addFailure(index, request, new FailureDetail(MultimodalErrorReasons.SOURCE_UNRESOLVED,
                    "source", null, "block has no source", null));
            return;
        }
        if (provider == null) {
            state.addFailure(index, request, new FailureDetail(MultimodalErrorReasons.SOURCE_UNRESOLVED,
                    "provider", request.source().sourceId(), "no image material provider is configured", null));
            return;
        }
        Optional<ResolvedImage> resolved = resolveThroughProvider(request, index, state);
        if (resolved.isEmpty()) {
            return;
        }
        if (request.isFileBlock() && !isImageMimeType(resolved.get().mediaType())) {
            throw fileProcessingRequired(request, resolved.get().mediaType(), index);
        }
        acceptResolvedMaterial(resolved.get(), request, index, state);
    }

    private Optional<ResolvedImage> resolveThroughProvider(MaterialRequest request, int index, ResolveState state) {
        try {
            return Optional.of(provider.resolve(request.source(), request.mediaType(), state.accessForItem()));
        } catch (BaseError error) {
            state.addFailureFrom(index, request, error);
            return Optional.empty();
        }
    }

    private void acceptResolvedMaterial(ResolvedImage resolved, MaterialRequest request, int index,
                                        ResolveState state) {
        if (!isImageMimeType(resolved.mediaType())) {
            state.addFailure(index, request, new FailureDetail(MultimodalErrorReasons.IMAGE_PROCESS_FAILED,
                    "mime", resolved.sourceId(),
                    "resolved media type '" + resolved.mediaType() + "' is not an image", null));
            return;
        }
        if (resolved.bytes().length == 0) {
            state.addFailure(index, request, new FailureDetail(MultimodalErrorReasons.IMAGE_PROCESS_FAILED,
                    "empty", resolved.sourceId(), "resolved material is empty", null));
            return;
        }
        long projectedTotal = state.consumed() + resolved.bytes().length;
        if (projectedTotal > state.access().maxTotalImageBytes()) {
            Map<String, Object> budget = new LinkedHashMap<>();
            budget.put("limit", state.access().maxTotalImageBytes());
            budget.put("actual", projectedTotal);
            state.addFailure(index, request, new FailureDetail(MultimodalErrorReasons.IMAGE_LOAD_FAILED,
                    "budget.total", resolved.sourceId(), "total image budget exceeded", budget));
            return;
        }
        state.consume(resolved.bytes().length);
        if (request.description() != null && !request.description().isBlank()) {
            state.addText(request.description());
        }
        state.addImagePart(resolved, imageDetail);
    }

    private static BaseError fileProcessingRequired(MaterialRequest request, String mediaType, int index) {
        Map<String, Object> locators = new LinkedHashMap<>();
        locators.put("blockId", request.blockId());
        locators.put("field", "type");
        locators.put("index", index);
        locators.put("filename", request.filename());
        locators.put("mediaType", mediaType);
        return MultimodalErrors.failed(MultimodalErrorReasons.FILE_PROCESSING_REQUIRED,
                "file block has no available processing chain in this release", locators, null);
    }

    private static boolean isImageMimeType(String mediaType) {
        return mediaType != null && mediaType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private static Map<String, Object> textPart(String text) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", "text");
        part.put("text", text);
        return part;
    }

    private static Map<String, Object> imagePart(ResolvedImage image, String detail) {
        Map<String, Object> imageUrl = new LinkedHashMap<>();
        imageUrl.put("url", "data:" + image.mediaType() + ";base64,"
                + Base64.getEncoder().encodeToString(image.bytes()));
        if (detail != null && !detail.isBlank()) {
            imageUrl.put("detail", detail);
        }
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", "image_url");
        part.put("image_url", imageUrl);
        return part;
    }

    /**
     * Outcome of one entry resolution pass.
     *
     * @param contentParts ordered Chat Completions content parts, serializable
     * @param itemErrors per-item failure records with index, blockId, reason,
     *                   stage, sourceId and message
     * @param provenance per-material origin entries kept in framework context
     * @since 0.1.17
     */
    public record ResolutionResult(List<Map<String, Object>> contentParts,
            List<Map<String, Object>> itemErrors,
            List<Map<String, Object>> provenance) {
    }

    /**
     * Bundled failure detail of one material block, keeping addFailure within the
     * five parameter budget.
     *
     * @param reason   structured failure reason
     * @param stage    pipeline stage locator
     * @param sourceId source locator; may be {@code null}
     * @param message  human readable message
     * @param extra    additional locator entries; may be {@code null}
     */
    private record FailureDetail(String reason, String stage, String sourceId, String message,
            Map<String, Object> extra) {
    }

    /**
     * Uniform view over image and file blocks for the resolution pipeline.
     */
    private record MaterialRequest(String blockId, String label, ImageSource source, String mediaType,
            String filename, String description, Map<String, Object> provenance,
            boolean isFileBlock) {

        private static MaterialRequest ofImage(ContentBlock.Image image) {
            return new MaterialRequest(image.id(), "image", image.source(), image.mediaType(),
                    image.filename(), image.description(), image.provenance(), false);
        }

        private static MaterialRequest ofFile(ContentBlock.FileRef file) {
            return new MaterialRequest(file.id(), "file", file.source(), file.mediaType(),
                    file.filename(), file.description(), file.provenance(), true);
        }
    }

    /**
     * Mutable accumulation state of one resolution pass.
     */
    private static final class ResolveState {
        private final MaterialAccessContext access;
        private final List<Map<String, Object>> contentParts = new ArrayList<>();
        private final List<Map<String, Object>> itemErrors = new ArrayList<>();
        private final List<Map<String, Object>> provenance = new ArrayList<>();

        private long consumed;
        private int materialTotal;
        private int materialFailed;

        private ResolveState(MaterialAccessContext access) {
            this.access = access;
            this.consumed = access.bytesAlreadyConsumed();
        }

        private MaterialAccessContext access() {
            return access;
        }

        private MaterialAccessContext accessForItem() {
            return consumed == access.bytesAlreadyConsumed() ? access : access.withConsumedBytes(consumed);
        }

        private long consumed() {
            return consumed;
        }

        private void consume(long bytes) {
            consumed += bytes;
        }

        private void countMaterial() {
            materialTotal++;
        }

        private void addText(String text) {
            contentParts.add(textPart(text));
        }

        private void addImagePart(ResolvedImage image, String detail) {
            contentParts.add(imagePart(image, detail));
        }

        private void addProvenance(MaterialRequest request, int index) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", index);
            entry.put("blockId", request.blockId());
            entry.put("label", request.label());
            if (request.source() != null) {
                entry.put("sourceKind", request.source().kind());
                entry.put("sourceId", request.source().sourceId());
                versionOf(request.source()).ifPresent(version -> entry.put("version", version));
            }
            if (request.filename() != null) {
                entry.put("filename", request.filename());
            }
            if (request.provenance() != null && !request.provenance().isEmpty()) {
                entry.put("provenance", new LinkedHashMap<>(request.provenance()));
            }
            provenance.add(entry);
        }

        private void addFailure(int index, MaterialRequest request, FailureDetail detail) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("index", index);
            error.put("blockId", request.blockId());
            error.put("reason", detail.reason());
            error.put("stage", detail.stage());
            if (detail.sourceId() != null) {
                error.put("sourceId", detail.sourceId());
            }
            error.put("message", detail.message());
            if (detail.extra() != null) {
                error.putAll(detail.extra());
            }
            itemErrors.add(error);
            materialFailed++;
            contentParts.add(textPart("[" + request.label() + " block '" + request.blockId()
                    + "' at input index " + index + " could not be loaded: reason=" + detail.reason()
                    + ", stage=" + detail.stage() + "]"));
            LOGGER.warning("multimodal material block failed, index={} blockId={} reason={} stage={}",
                    index, request.blockId(), detail.reason(), detail.stage());
        }

        private void addFailureFrom(int index, MaterialRequest request, BaseError error) {
            String sourceId = MultimodalErrors.stringDetail(error, "sourceId").orElse(null);
            if (sourceId == null && request.source() != null) {
                sourceId = request.source().sourceId();
            }
            Map<String, Object> extra = null;
            if (error.getDetails() instanceof Map<?, ?> details
                    && (details.containsKey("limit") || details.containsKey("actual"))) {
                extra = new LinkedHashMap<>();
                copyDetail(details, "limit", extra);
                copyDetail(details, "actual", extra);
                copyDetail(details, "actualVersions", extra);
                copyDetail(details, "expected", extra);
            }
            String reason = MultimodalErrors.reasonOf(error).orElse(MultimodalErrorReasons.IMAGE_LOAD_FAILED);
            String stage = MultimodalErrors.stringDetail(error, "stage").orElse(null);
            addFailure(index, request, new FailureDetail(reason, stage, sourceId, error.getMessage(), extra));
        }

        private void failIfNoMaterialResolved() {
            if (materialTotal == 0 || materialFailed < materialTotal) {
                return;
            }
            Map<String, Object> locators = new LinkedHashMap<>();
            locators.put("items", new ArrayList<>(itemErrors));
            throw MultimodalErrors.failed(String.valueOf(itemErrors.get(0).get("reason")),
                    "all " + materialTotal + " material blocks failed to resolve", locators, null);
        }

        private static void copyDetail(Map<?, ?> details, String key, Map<String, Object> target) {
            Object value = details.get(key);
            if (value != null) {
                target.put(key, value);
            }
        }

        private static Optional<String> versionOf(ImageSource source) {
            if (source instanceof ImageSource.PathRef path) {
                return Optional.ofNullable(path.version());
            }
            if (source instanceof ImageSource.AssetRef asset) {
                return Optional.ofNullable(asset.version());
            }
            return Optional.empty();
        }
    }
}
