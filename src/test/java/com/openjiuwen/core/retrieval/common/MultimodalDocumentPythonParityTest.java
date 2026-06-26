/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.openjiuwen.core.common.exception.ValidationError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestMultimodalDocument} in
 * {@code tests/unit_tests/core/retrieval/common/test_multimodal_document.py}.
 */
class MultimodalDocumentPythonParityTest {

    private static final String VALID_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SECOND_VALID_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void testCreateMultimodalDocument() {
        MultimodalDocument doc = new MultimodalDocument();

        assertThat(doc.getId_()).isNotBlank();
        assertThat(doc.getMetadata()).isEmpty();
        assertThat(doc.getText()).isEmpty();
        assertThat(doc.getContent()).isEmpty();
    }

    @Test
    void testCreateMultimodalDocumentWithId() {
        MultimodalDocument doc = new MultimodalDocument("test_id");

        assertThat(doc.getId_()).isEqualTo("test_id");
    }

    @Test
    void testCreateMultimodalDocumentWithMetadata() {
        Map<String, Object> metadata = Map.of("source", "test", "author", "test_author");

        MultimodalDocument doc = new MultimodalDocument(metadata);

        assertThat(doc.getMetadata()).containsExactlyInAnyOrderEntriesOf(metadata);
    }

    @Test
    void testCreateMultimodalDocumentWithText() {
        MultimodalDocument doc = new MultimodalDocument(null, "dummy", null);

        assertThat(doc.getText()).isEqualTo("dummy");
    }

    @Test
    void testAddTextField() {
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("text", "Hello world");

        assertThat(doc.getContent()).containsExactly(Map.of("type", "text", "text", "Hello world"));
    }

    @Test
    void testAddMultipleTextFields() {
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("text", "First text");
        doc.addField("text", "Second text");

        assertThat(doc.getContent()).hasSize(2);
        assertThat(doc.getContent().get(0).get("text")).isEqualTo("First text");
        assertThat(doc.getContent().get(1).get("text")).isEqualTo("Second text");
    }

    @Test
    void testAddImageFieldFromBase64() {
        String imageData = tinyPngBase64();
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("image", imageData);

        Map<String, Object> item = doc.getContent().get(0);
        assertThat(item.get("type")).isEqualTo("image_url");
        assertThat(nestedMap(item, "image_url").get("url")).isEqualTo(imageData);
        assertThat(item).containsKey("uuid");
    }

    @Test
    void testAddImageFieldFromFile(@TempDir Path tempDir) throws Exception {
        Path imageFile = tempDir.resolve("test_image.png");
        Files.write(imageFile, new byte[]{1, 2, 3});
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("image", imageFile);

        Map<String, Object> item = doc.getContent().get(0);
        assertThat(item.get("type")).isEqualTo("image_url");
        assertThat(nestedMap(item, "image_url").get("url")).asString().startsWith("data:image/");
        assertThat(item).containsKey("uuid");
    }

    @Test
    void testAddAudioFieldFromBase64() {
        String audioData = tinyWavBase64();
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("audio", audioData);

        Map<String, Object> item = doc.getContent().get(0);
        assertThat(item.get("type")).isEqualTo("input_audio");
        assertThat(nestedMap(item, "input_audio").get("data")).isEqualTo(audioData);
        assertThat(nestedMap(item, "input_audio").get("format")).isEqualTo("wav");
        assertThat(item).containsKey("uuid");
    }

    @Test
    void testAddAudioFieldFromFile(@TempDir Path tempDir) throws Exception {
        Path audioFile = tempDir.resolve("test_audio.wav");
        Files.write(audioFile, new byte[]{4, 5, 6});
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("audio", audioFile);

        Map<String, Object> item = doc.getContent().get(0);
        assertThat(item.get("type")).isEqualTo("input_audio");
        assertThat(nestedMap(item, "input_audio").get("data")).asString().startsWith("data:audio/");
        assertThat(nestedMap(item, "input_audio")).containsKey("format");
        assertThat(item).containsKey("uuid");
    }

    @Test
    void testAddVideoFieldFromBase64() {
        String videoData = fakeMp4Base64();
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("video", videoData);

        Map<String, Object> item = doc.getContent().get(0);
        assertThat(item.get("type")).isEqualTo("video_url");
        assertThat(nestedMap(item, "video_url").get("url")).isEqualTo(videoData);
        assertThat(item).containsKey("uuid");
    }

    @Test
    void testAddVideoFieldFromFile(@TempDir Path tempDir) throws Exception {
        Path videoFile = tempDir.resolve("test_video.mp4");
        Files.write(videoFile, new byte[]{7, 8, 9});
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("video", videoFile);

        Map<String, Object> item = doc.getContent().get(0);
        assertThat(item.get("type")).isEqualTo("video_url");
        assertThat(nestedMap(item, "video_url").get("url")).asString().startsWith("data:video/");
        assertThat(item).containsKey("uuid");
    }

    @Test
    void testAddFieldWithDataId() {
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("image", tinyPngBase64(), null, VALID_ID);

        assertThat(doc.getContent().get(0).get("uuid")).isEqualTo(VALID_ID);
    }

    @Test
    void testMethodChaining() {
        String imageData = tinyPngBase64();

        MultimodalDocument doc = new MultimodalDocument()
                .addField("text", "Hello")
                .addField("text", "World")
                .addField("image", imageData);

        assertThat(doc.getContent()).hasSize(3);
        assertThat(doc.getContent().get(0).get("text")).isEqualTo("Hello");
        assertThat(doc.getContent().get(1).get("text")).isEqualTo("World");
        assertThat(doc.getContent().get(2).get("type")).isEqualTo("image_url");
    }

    @Test
    void testMixedModalities() {
        Map<String, String> media = allMedia();
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("text", "Description");
        doc.addField("image", media.get("image"));
        doc.addField("audio", media.get("audio"));
        doc.addField("video", media.get("video"));

        assertThat(doc.getContent()).hasSize(4);
        assertThat(doc.getContent().get(0).get("type")).isEqualTo("text");
        assertThat(doc.getContent().get(1).get("type")).isEqualTo("image_url");
        assertThat(doc.getContent().get(2).get("type")).isEqualTo("input_audio");
        assertThat(doc.getContent().get(3).get("type")).isEqualTo("video_url");
    }

    @Test
    void testTextFieldNoUuid() {
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("text", "Hello");

        assertThat(doc.getContent().get(0)).doesNotContainKey("uuid");
    }

    @Test
    void testInvalidKind() {
        MultimodalDocument doc = new MultimodalDocument();

        assertThatThrownBy(() -> doc.addField("invalid", "test"))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("unknown_kind");
    }

    @Test
    void testNoDataSourceProvided() {
        MultimodalDocument doc = new MultimodalDocument();

        assertThatThrownBy(() -> doc.addField("image"))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("no_image_source_provided");
    }

    @Test
    void testBothDataAndFilePathProvided(@TempDir Path tempDir) throws Exception {
        MultimodalDocument doc = new MultimodalDocument();
        Path imageFile = tempDir.resolve("test.png");
        Files.write(imageFile, new byte[]{1});

        assertThatThrownBy(() -> doc.addField("image", tinyPngBase64(), imageFile, ""))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("too_many_image_source_provided");
    }

    @Test
    void testInvalidDataFormat() {
        MultimodalDocument doc = new MultimodalDocument();

        assertThatThrownBy(() -> doc.addField("image", "invalid_data"))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("invalid_image_data_provided");
    }

    @Test
    void testAddImageFieldFromUrl() {
        MultimodalDocument doc = new MultimodalDocument();
        String url = "https://example.com/image.png";

        doc.addField("image", url);

        assertThat(doc.getContent()).hasSize(1);
        assertThat(doc.getContent().get(0).get("type")).isEqualTo("image_url");
        assertThat(nestedMap(doc.getContent().get(0), "image_url").get("url")).isEqualTo(url);
    }

    @Test
    void testAddVideoFieldFromUrl() {
        MultimodalDocument doc = new MultimodalDocument();
        String url = "https://example.com/video.mp4";

        doc.addField("video", url);

        assertThat(doc.getContent()).hasSize(1);
        assertThat(doc.getContent().get(0).get("type")).isEqualTo("video_url");
        assertThat(nestedMap(doc.getContent().get(0), "video_url").get("url")).isEqualTo(url);
    }

    @Test
    void testAddAudioFieldFromUrlRejected() {
        MultimodalDocument doc = new MultimodalDocument();

        assertThatThrownBy(() -> doc.addField("audio", "https://example.com/audio.wav"))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("invalid_audio_data_provided");
    }

    @Test
    void testInvalidFilePathType() {
        MultimodalDocument doc = new MultimodalDocument();

        assertThatThrownBy(() -> doc.addField("image", null, "not_a_path", ""))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("invalid_image_file_path_provided");
    }

    @Test
    void testFileNotFound(@TempDir Path tempDir) {
        MultimodalDocument doc = new MultimodalDocument();
        Path missing = tempDir.resolve("missing.png");

        assertThatThrownBy(() -> doc.addField("image", missing))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("image_path_invalid");
    }

    @Test
    void testInvalidDataIdTooLong() {
        MultimodalDocument doc = new MultimodalDocument();
        String invalidId = "a".repeat(33);

        assertThatThrownBy(() -> doc.addField("image", tinyPngBase64(), null, invalidId))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("invalid_uuid_provided");
    }

    @Test
    void testInvalidDataIdNotString() {
        MultimodalDocument doc = new MultimodalDocument();

        assertThatThrownBy(() -> doc.addField("image", tinyPngBase64(), null, 12345))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("invalid_uuid_provided");
    }

    @Test
    void testTextFromFile(@TempDir Path tempDir) throws Exception {
        Path textFile = tempDir.resolve("test.txt");
        Files.writeString(textFile, "Hello from file", StandardCharsets.UTF_8);
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("text", textFile);

        assertThat(doc.getContent().get(0).get("text")).isEqualTo("Hello from file");
    }

    @Test
    void testAudioFormatExtraction() {
        MultimodalDocument doc = new MultimodalDocument();
        String mp3Data = "data:audio/mp3;base64," + base64("fake mp3 data");

        doc.addField("audio", mp3Data);

        assertThat(nestedMap(doc.getContent().get(0), "input_audio").get("format")).isEqualTo("mp3");
    }

    @Test
    void testForbidExtraFields() {
        assertThatThrownBy(() -> MultimodalDocument.class.getConstructor(
                String.class,
                String.class,
                Map.class,
                Object.class
        )).isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void testContentWithMultipleUuids() {
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("image", tinyPngBase64(), null, VALID_ID);
        doc.addField("video", fakeMp4Base64(), null, SECOND_VALID_ID);

        assertThat(doc.getContent().get(0).get("uuid")).isEqualTo(VALID_ID);
        assertThat(doc.getContent().get(1).get("uuid")).isEqualTo(SECOND_VALID_ID);
    }

    @Test
    void testDashscopeInputEmptyDoc() {
        assertThat(new MultimodalDocument().getDashscopeInput()).isEmpty();
    }

    @Test
    void testDashscopeInputTextOnly() {
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("text", "Hello world");

        assertThat(doc.getDashscopeInput()).containsExactly(Map.entry("text", "Hello world"));
    }

    @Test
    void testDashscopeInputSingleImageBase64() {
        MultimodalDocument doc = new MultimodalDocument();
        String imageData = tinyPngBase64();

        doc.addField("image", imageData);

        assertThat(doc.getDashscopeInput()).containsExactly(Map.entry("image", imageData));
    }

    @Test
    void testDashscopeInputSingleImageUrl() {
        MultimodalDocument doc = new MultimodalDocument();
        String url = "https://example.com/photo.png";

        doc.addField("image", url);

        assertThat(doc.getDashscopeInput()).containsExactly(Map.entry("image", url));
    }

    @Test
    void testDashscopeInputMultiImages() {
        MultimodalDocument doc = new MultimodalDocument();
        String url1 = "https://example.com/a.png";
        String url2 = "https://example.com/b.png";

        doc.addField("image", url1);
        doc.addField("image", url2);

        assertThat(doc.getDashscopeInput()).containsExactly(Map.entry("multi_images", List.of(url1, url2)));
    }

    @Test
    void testDashscopeInputVideoUrl() {
        MultimodalDocument doc = new MultimodalDocument();
        String url = "https://example.com/clip.mp4";

        doc.addField("video", url);

        assertThat(doc.getDashscopeInput()).containsExactly(Map.entry("video", url));
    }

    @Test
    void testDashscopeInputVideoBase64Raises() {
        MultimodalDocument doc = new MultimodalDocument();
        doc.addField("video", fakeMp4Base64());

        assertThatThrownBy(doc::getDashscopeInput)
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("unsupported_format")
                .hasMessageContaining("only support video in url format");
    }

    @Test
    void testDashscopeInputAudioRaises() {
        MultimodalDocument doc = new MultimodalDocument();
        doc.addField("audio", tinyWavBase64());

        assertThatThrownBy(doc::getDashscopeInput)
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("unsupported_format")
                .hasMessageContaining("does not support modality");
    }

    @Test
    void testDashscopeInputTextAndImage() {
        MultimodalDocument doc = new MultimodalDocument();
        String url = "https://example.com/img.png";

        doc.addField("text", "Caption");
        doc.addField("image", url);

        assertThat(doc.getDashscopeInput()).containsExactly(
                Map.entry("text", "Caption"),
                Map.entry("image", url)
        );
    }

    @Test
    void testDashscopeInputTextAndVideoUrl() {
        MultimodalDocument doc = new MultimodalDocument();

        doc.addField("text", "Description");
        doc.addField("video", "https://example.com/v.mp4");

        assertThat(doc.getDashscopeInput()).containsExactly(
                Map.entry("text", "Description"),
                Map.entry("video", "https://example.com/v.mp4")
        );
    }

    @Test
    void testDashscopeInputMultipleVideoRaises() {
        MultimodalDocument doc = new MultimodalDocument();
        doc.addField("video", "https://example.com/a.mp4");
        doc.addField("video", "https://example.com/b.mp4");

        assertThatThrownBy(doc::getDashscopeInput)
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("multiple_video_fields_present");
    }

    @Test
    void testDashscopeInputMultipleTextRaises() {
        MultimodalDocument doc = new MultimodalDocument();
        doc.addField("text", "First");
        doc.addField("text", "Second");

        assertThatThrownBy(doc::getDashscopeInput)
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("multiple_text_fields_present");
    }

    @Test
    void testDashscopeInputReturnsDeepcopy() {
        MultimodalDocument doc = new MultimodalDocument();
        doc.addField("text", "Hello");

        Map<String, Object> out1 = doc.getDashscopeInput();
        Map<String, Object> out2 = doc.getDashscopeInput();
        out1.put("text", "mutated");

        assertThat(out2).containsExactly(Map.entry("text", "Hello"));
        assertThat(doc.getDashscopeInput().get("text")).isEqualTo("Hello");
    }

    private static String tinyPngBase64() {
        return "data:image/png;base64," + base64("fake png data");
    }

    private static String tinyWavBase64() {
        return "data:audio/wav;base64," + base64("fake wav data");
    }

    private static String fakeMp4Base64() {
        return "data:video/mp4;base64," + base64("fake mp4 data");
    }

    private static Map<String, String> allMedia() {
        return Map.of(
                "image", tinyPngBase64(),
                "audio", tinyWavBase64(),
                "video", fakeMp4Base64()
        );
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Map<String, Object> map, String key) {
        return (Map<String, Object>) map.get(key);
    }
}
