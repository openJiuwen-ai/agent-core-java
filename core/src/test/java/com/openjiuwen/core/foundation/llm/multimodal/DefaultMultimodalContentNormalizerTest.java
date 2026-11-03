/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.multimodal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contract tests of {@link DefaultMultimodalContentNormalizer} covering the
 * four accepted input shapes and the explicit failure surface.
 */
class DefaultMultimodalContentNormalizerTest {
    private static final String PNG_BASE64 = "iVBORw0KGgo=";
    private static final byte[] PNG_BYTES = PNG_BASE64.getBytes(StandardCharsets.UTF_8);

    private final DefaultMultimodalContentNormalizer normalizer = new DefaultMultimodalContentNormalizer();

    /**
     * Cast with an explicit type guard so failures name the wrong block type.
     *
     * @param value block to cast
     * @param type  expected block type
     * @param <T>   expected type parameter
     * @return the value as the expected type
     */
    private static <T> T as(Object value, Class<T> type) {
        if (!type.isInstance(value)) {
            throw new AssertionError("unexpected block type: "
                    + (value == null ? "null" : value.getClass().getName()));
        }
        return type.cast(value);
    }

    @Test
    void normalize_stringInput_producesSingleTextBlock() {
        List<ContentBlock> blocks = normalizer.normalize("hello");

        assertThat(blocks).hasSize(1);
        ContentBlock.Text text = as(blocks.get(0), ContentBlock.Text.class);
        assertThat(text.id()).isEqualTo("block-0");
        assertThat(text.text()).isEqualTo("hello");
        assertThat(text.type()).isEqualTo(ContentBlockType.TEXT);
    }

    @Test
    void normalize_textAndImageBlocks_preservesOrderFieldsAndGeneratedIds() {
        List<Object> input = List.of(
                Map.of("type", "text", "text", "compare these"),
                imageBlockMap("img-1", rawSource(PNG_BYTES), Map.of(
                        "mediaType", "image/png",
                        "description", "first page",
                        "provenance", Map.of("originalSource", "upload:a.png"))));

        List<ContentBlock> blocks = normalizer.normalize(input);

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).id()).isEqualTo("block-0");
        ContentBlock.Image image = as(blocks.get(1), ContentBlock.Image.class);
        assertThat(image.id()).isEqualTo("img-1");
        assertThat(image.source()).isEqualTo(new ImageSource.Raw(PNG_BYTES));
        assertThat(image.mediaType()).isEqualTo("image/png");
        assertThat(image.description()).isEqualTo("first page");
        assertThat(image.provenance()).containsEntry("originalSource", "upload:a.png");
    }

    @Test
    void normalize_duplicateImages_keepsEveryItemIndependent() {
        Map<String, Object> block = imageBlockMap("img-dup", rawSource(PNG_BYTES), Map.of());

        List<ContentBlock> blocks = normalizer.normalize(List.of(block, block, block));

        assertThat(blocks).hasSize(3);
        assertThat(blocks).allSatisfy(item -> assertThat(item.id()).isEqualTo("img-dup"));
    }

    @Test
    void normalize_legacyOpenAiParts_convertsToInternalSources() {
        List<Object> input = List.of(
                Map.of("type", "text", "text", "what is this"),
                Map.of("type", "image_url",
                        "image_url", Map.of("url", "data:image/png;base64," + PNG_BASE64)));

        List<ContentBlock> blocks = normalizer.normalize(input);

        assertThat(blocks).hasSize(2);
        ContentBlock.Image image = as(blocks.get(1), ContentBlock.Image.class);
        ImageSource.Raw raw = as(image.source(), ImageSource.Raw.class);
        assertThat(raw.bytes()).isEqualTo(java.util.Base64.getDecoder().decode(PNG_BASE64));
        assertThat(image.mediaType()).isEqualTo("image/png");
    }

    @Test
    void normalize_legacyHttpUrl_becomesUrlSource() {
        List<ContentBlock> blocks = normalizer.normalize(List.of(
                Map.of("type", "image_url",
                        "image_url", Map.of("url", "https://files.example/b.png"))));

        ContentBlock.Image image = as(blocks.get(0), ContentBlock.Image.class);
        assertThat(image.source()).isEqualTo(new ImageSource.Url("https://files.example/b.png"));
    }

    @Test
    void normalize_legacyLocalPathUrl_failsExplicitly() {
        List<Object> input = List.of(Map.of("type", "image_url",
                "image_url", Map.of("url", "C:\\images\\a.png")));

        assertThatThrownBy(() -> normalizer.normalize(input))
                .isInstanceOfSatisfying(BaseError.class, error -> {
                    assertThat(details(error))
                            .containsEntry("reason", MultimodalErrorReasons.CONTENT_INVALID)
                            .containsEntry("field", "image_url.url")
                            .containsEntry("index", 0);
                    assertThat(error.getMessage()).contains("data: or http(s): URL");
                });
    }

    @Test
    void normalize_rawSourceBase64String_decodedExactlyOnce() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "raw");
        source.put("bytes", PNG_BASE64);

        List<ContentBlock> blocks = normalizer.normalize(List.of(imageBlockMap("img-b64", source, Map.of())));

        ImageSource.Raw raw = as(as(blocks.get(0), ContentBlock.Image.class).source(), ImageSource.Raw.class);
        assertThat(raw.bytes()).isEqualTo(java.util.Base64.getDecoder().decode(PNG_BASE64));
    }

    @Test
    void normalize_unknownType_failsWithBlockIdFieldAndIndex() {
        List<Object> input = new ArrayList<>();
        input.add(Map.of("type", "text", "text", "ok"));
        input.add(Map.of("type", "audio", "id", "snd-1"));

        assertThatThrownBy(() -> normalizer.normalize(input))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.CONTENT_INVALID)
                        .containsEntry("blockId", "snd-1")
                        .containsEntry("field", "type")
                        .containsEntry("index", 1));
    }

    @Test
    void normalize_sourceWithTwoKinds_failsWithConflictLocator() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "path");
        source.put("path", "/workspace/a.png");
        source.put("assetId", "asset-1");

        assertThatThrownBy(() -> normalizer.normalize(List.of(imageBlockMap("img-x", source, Map.of()))))
                .isInstanceOfSatisfying(BaseError.class, error -> {
                    assertThat(details(error))
                            .containsEntry("reason", MultimodalErrorReasons.CONTENT_INVALID)
                            .containsEntry("field", "source.assetId");
                    assertThat(error.getMessage()).contains("only one source kind");
                });
    }

    @Test
    void normalize_missingRequiredFields_failWithFieldLocators() {
        assertThatThrownBy(() -> normalizer.normalize(List.of(Map.of("type", "image", "id", "img-no-source"))))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("field", "source")
                        .containsEntry("blockId", "img-no-source"));
        assertThatThrownBy(() -> normalizer.normalize(List.of(Map.of("type", "text"))))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("field", "text"));
        assertThatThrownBy(() -> normalizer.normalize(List.of(imageBlockMap("img-no-version",
                Map.of("kind", "asset", "assetId", "a-1"), Map.of()))))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("field", "source.version"));
    }

    @Test
    void normalize_pathVersionMustBeSha256Form() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "path");
        source.put("path", "/workspace/a.png");
        source.put("version", "v1");

        assertThatThrownBy(() -> normalizer.normalize(List.of(imageBlockMap("img-v", source, Map.of()))))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.CONTENT_INVALID)
                        .containsEntry("field", "source.version"));
    }

    @Test
    void normalize_unknownSourceField_failsExplicitly() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "raw");
        source.put("bytes", PNG_BYTES);
        source.put("location", "somewhere");

        assertThatThrownBy(() -> normalizer.normalize(List.of(imageBlockMap("img-u", source, Map.of()))))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("field", "source.location"));
    }

    @Test
    void normalize_structuredMessageMap_usesContentKey() {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(Map.of("type", "text", "text", "hi")));

        List<ContentBlock> blocks = normalizer.normalize(message);

        assertThat(blocks).hasSize(1);
        assertThat(as(blocks.get(0), ContentBlock.Text.class).text()).isEqualTo("hi");
    }

    @Test
    void normalize_baseMessageWithObjectContent_producesBlocks() {
        BaseMessage message = new BaseMessage("user",
                List.of(Map.of("type", "text", "text", "look"),
                        imageBlockMap("img-2", rawSource(PNG_BYTES), Map.of())));

        List<ContentBlock> blocks = normalizer.normalize(message);

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(1)).isInstanceOf(ContentBlock.Image.class);
    }

    @Test
    void normalize_emptyListOrNonMapItem_failsWithoutStringFallback() {
        assertThatThrownBy(() -> normalizer.normalize(List.of()))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("reason", MultimodalErrorReasons.CONTENT_INVALID));
        assertThatThrownBy(() -> normalizer.normalize(List.of("plain string item")))
                .isInstanceOfSatisfying(BaseError.class, error -> assertThat(details(error))
                        .containsEntry("field", "block")
                        .containsEntry("index", 0));
    }

    @Test
    void normalize_toolResultBlock_parsesContractFields() {
        List<ContentBlock> blocks = normalizer.normalize(List.of(
                Map.of("type", "tool_result", "id", "tr-1", "toolCallId", "call-1",
                        "toolName", "read_file", "status", "success",
                        "items", List.of(Map.of("index", 0)))));

        ContentBlock.ToolResult result = as(blocks.get(0), ContentBlock.ToolResult.class);
        assertThat(result.toolCallId()).isEqualTo("call-1");
        assertThat(result.toolName()).isEqualTo("read_file");
        assertThat(result.status()).isEqualTo("success");
        assertThat(result.items()).hasSize(1);
        assertThat(result.type()).isEqualTo(ContentBlockType.TOOL_RESULT);
    }

    @Test
    void normalize_videoBlock_parsedIntoReservedSlot() {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "video");
        block.put("source", rawSource(PNG_BYTES));

        List<ContentBlock> blocks = normalizer.normalize(List.of(block));

        assertThat(blocks.get(0)).isInstanceOf(ContentBlock.Video.class);
        assertThat(blocks.get(0).type()).isEqualTo(ContentBlockType.VIDEO);
    }

    @Test
    void isMultimodalContent_detectsOnlyContractShapes() {
        assertThat(MultimodalContentNormalizer.isMultimodalContent("text")).isFalse();
        assertThat(MultimodalContentNormalizer.isMultimodalContent(null)).isFalse();
        assertThat(MultimodalContentNormalizer.isMultimodalContent(List.of())).isFalse();
        assertThat(MultimodalContentNormalizer.isMultimodalContent(List.of("a", "b"))).isFalse();
        assertThat(MultimodalContentNormalizer.isMultimodalContent(Map.of("query", "hi"))).isFalse();
        assertThat(MultimodalContentNormalizer.isMultimodalContent(
                List.of(Map.of("type", "text", "text", "hi")))).isTrue();
        assertThat(MultimodalContentNormalizer.isMultimodalContent(
                Map.of("content", "hi"))).isTrue();
        assertThat(MultimodalContentNormalizer.isMultimodalContent(
                new BaseMessage("user", "hi"))).isTrue();
    }

    private static Map<String, Object> imageBlockMap(String id, Map<String, Object> source,
                                                     Map<String, Object> extraFields) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "image");
        if (id != null) {
            block.put("id", id);
        }
        block.put("source", source);
        block.putAll(extraFields);
        return block;
    }

    private static Map<String, Object> rawSource(byte[] bytes) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "raw");
        source.put("bytes", bytes);
        return source;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(BaseError error) {
        return (Map<String, Object>) error.getDetails();
    }
}
