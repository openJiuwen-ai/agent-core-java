/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator.skills.skill_omni_creation.scripts;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests/unit_tests/dev_tools/agent_builder/skill_omni_creation/test_stage02_download.py}.
 */
class TestStage02Download {

    @AfterEach
    void resetFetcher() {
        Stage02Download.setFetcher(null);
    }

    @Test
    void fetchOneReturnsDataAndMimeOnSuccess() throws Exception {
        byte[] png = makePng(200, 200);
        Stage02Download.setFetcher(url -> new Stage02Download.FetchResponse(png, "image/png"));
        Stage02Download.FetchResult result = Stage02Download.fetchOne("https://example.com/img.png");
        assertArrayEquals(png, result.data());
        assertEquals("image/png", result.mime());
    }

    @Test
    void fetchOneRejectsUnsupportedMime() {
        Stage02Download.setFetcher(url -> new Stage02Download.FetchResponse("data".getBytes(), "image/tiff"));
        assertNull(Stage02Download.fetchOne("https://example.com/img.tiff").data());
    }

    @Test
    void fetchOneReturnsNoneOnNetworkException() {
        Stage02Download.setFetcher(url -> {
            throw new RuntimeException("timeout");
        });
        Stage02Download.FetchResult result = Stage02Download.fetchOne("https://example.com/img.png");
        assertNull(result.data());
        assertNull(result.mime());
    }

    @Test
    void fetchOneRejectsTooSmallImage() throws Exception {
        Stage02Download.setFetcher(url -> new Stage02Download.FetchResponse(makePng(10, 10), "image/png"));
        assertNull(Stage02Download.fetchOne("https://example.com/tiny.png").data());
    }

    @Test
    void fetchOneRejectsDataOverMaxBytes() {
        byte[] data = new byte[SkillOmniCommon.MAX_IMAGE_BYTES + 1];
        Stage02Download.setFetcher(url -> new Stage02Download.FetchResponse(data, "image/png"));
        assertNull(Stage02Download.fetchOne("https://example.com/huge.png").data());
    }

    @Test
    void fetchOneStripsMimeParameters() throws Exception {
        byte[] png = makePng(200, 200);
        Stage02Download.setFetcher(url -> new Stage02Download.FetchResponse(png, "image/png; charset=utf-8"));
        assertEquals("image/png", Stage02Download.fetchOne("https://example.com/img.png").mime());
    }

    @Test
    void fetchOneReturnsOriginalUrl() throws Exception {
        String target = "https://example.com/specific.png";
        Stage02Download.setFetcher(url -> new Stage02Download.FetchResponse(makePng(200, 200), "image/png"));
        assertEquals(target, Stage02Download.fetchOne(target).url());
    }

    @Test
    void downloadImageBlocksKeepsDownloadedImage() throws Exception {
        byte[] png = makePng(200, 200);
        Stage02Download.setFetcher(url -> new Stage02Download.FetchResponse(png, "image/png"));
        Stage02Download.DownloadResult result = Stage02Download.downloadImageBlocks(List.of(imageBlock("https://example.com/img.png")));
        assertEquals(1, result.blocks().stream().filter(b -> "image".equals(b.get("type"))).count());
        assertTrue(result.fetched().containsKey("https://example.com/img.png"));
    }

    @Test
    void downloadImageBlocksRemovesFailedImageBlocks() {
        Stage02Download.setFetcher(url -> {
            throw new RuntimeException("timeout");
        });
        Stage02Download.DownloadResult result = Stage02Download.downloadImageBlocks(List.of(imageBlock("https://example.com/broken.png")));
        assertTrue(result.blocks().stream().noneMatch(b -> "image".equals(b.get("type"))));
    }

    @Test
    void downloadImageBlocksDeduplicatesByContentHash() throws Exception {
        byte[] png = makePng(200, 200);
        Stage02Download.setFetcher(url -> new Stage02Download.FetchResponse(png, "image/png"));
        Stage02Download.DownloadResult result = Stage02Download.downloadImageBlocks(List.of(
                imageBlock("https://example.com/a.png"),
                imageBlock("https://example.com/b.png")
        ));
        assertEquals(1, result.blocks().stream().filter(b -> "image".equals(b.get("type"))).count());
    }

    @Test
    void downloadImageBlocksKeepsDistinctImagesByContent() throws Exception {
        byte[] first = makePng(200, 200);
        byte[] second = makePng(300, 300);
        Stage02Download.setFetcher(url -> new Stage02Download.FetchResponse(
                url.endsWith("img1.png") ? first : second,
                "image/png"
        ));
        Stage02Download.DownloadResult result = Stage02Download.downloadImageBlocks(List.of(
                imageBlock("https://example.com/img1.png"),
                imageBlock("https://example.com/img2.png")
        ));
        assertEquals(2, result.blocks().stream().filter(b -> "image".equals(b.get("type"))).count());
        assertEquals(2, result.fetched().size());
    }

    @Test
    void downloadImageBlocksPreservesNonImageBlocks() throws Exception {
        byte[] png = makePng(200, 200);
        Stage02Download.setFetcher(url -> new Stage02Download.FetchResponse(png, "image/png"));
        Stage02Download.DownloadResult result = Stage02Download.downloadImageBlocks(List.of(
                textBlock("Keep me"),
                headingBlock("Also keep"),
                imageBlock("https://example.com/img.png")
        ));
        assertTrue(result.blocks().stream().anyMatch(b -> "text".equals(b.get("type"))));
        assertTrue(result.blocks().stream().anyMatch(b -> "heading".equals(b.get("type"))));
    }

    @Test
    void downloadImageBlocksPreservesDomOrderOfSurvivingBlocks() throws Exception {
        byte[] png = makePng(200, 200);
        Stage02Download.setFetcher(url -> new Stage02Download.FetchResponse(png, "image/png"));
        Stage02Download.DownloadResult result = Stage02Download.downloadImageBlocks(List.of(
                headingBlock("H"),
                imageBlock("https://example.com/img.png"),
                textBlock("TTTTTTTTTTTTTTTTTTTT")
        ));
        assertEquals(List.of("heading", "image", "text"), result.blocks().stream()
                .map(block -> block.get("type"))
                .toList());
    }

    @Test
    void downloadImageBlocksReturnsEmptyFetchedWhenAllFail() {
        Stage02Download.setFetcher(url -> new Stage02Download.FetchResponse(null, "image/png"));
        Stage02Download.DownloadResult result = Stage02Download.downloadImageBlocks(List.of(
                imageBlock("https://example.com/a.png"),
                imageBlock("https://example.com/b.png")
        ));
        assertTrue(result.fetched().isEmpty());
        assertTrue(result.blocks().isEmpty());
    }

    @Test
    void downloadImageBlocksHandlesEmptyBlocksList() {
        Stage02Download.DownloadResult result = Stage02Download.downloadImageBlocks(List.of());
        assertTrue(result.blocks().isEmpty());
        assertTrue(result.fetched().isEmpty());
    }

    private static Map<String, Object> imageBlock(String url) {
        java.util.LinkedHashMap<String, Object> block = new java.util.LinkedHashMap<>();
        block.put("type", "image");
        block.put("url", url);
        block.put("alt", "");
        block.put("source", "main");
        block.put("path", null);
        return block;
    }

    private static Map<String, Object> textBlock(String text) {
        java.util.LinkedHashMap<String, Object> block = new java.util.LinkedHashMap<>();
        block.put("type", "text");
        block.put("text", text);
        block.put("source", "main");
        return block;
    }

    private static Map<String, Object> headingBlock(String text) {
        java.util.LinkedHashMap<String, Object> block = new java.util.LinkedHashMap<>();
        block.put("type", "heading");
        block.put("level", 2);
        block.put("text", text);
        block.put("source", "main");
        return block;
    }

    private static byte[] makePng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
