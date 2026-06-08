/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator.skills.skill_omni_creation.scripts;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests/unit_tests/dev_tools/agent_builder/skill_omni_creation/test_stage01_scrape.py}.
 */
class TestStage01Scrape {

    @Test
    void utilityUrlRecognizesLoginPath() {
        assertTrue(Stage01Scrape.isUtilityUrl("https://example.com/login"));
    }

    @Test
    void utilityUrlIgnoresNormalArticle() {
        assertFalse(Stage01Scrape.isUtilityUrl("https://support.google.com/docs/answer/12345"));
    }

    @Test
    void contentImgRequiresRealSource() {
        Element img = Jsoup.parse("<img src=\"https://example.com/photo.png\">").selectFirst("img");
        assertTrue(Stage01Scrape.isContentImg(img));
    }

    @Test
    void contentImgRejectsDataUri() {
        Element img = Jsoup.parse("<img src=\"data:image/png;base64,abc\">").selectFirst("img");
        assertFalse(Stage01Scrape.isContentImg(img));
    }

    @Test
    void bestImgUrlPicksLastSrcsetEntry() {
        Element img = Jsoup.parse(
                "<img srcset=\"img-small.png 320w, img-medium.png 640w, img-large.png 1024w\" src=\"img-small.png\">"
        ).selectFirst("img");
        assertTrue(Stage01Scrape.bestImgUrl(img, "https://example.com/page").contains("img-large.png"));
    }

    @Test
    void bestImgUrlFallsBackToSrc() {
        Element img = Jsoup.parse("<img src=\"photo.png\">").selectFirst("img");
        assertTrue(Stage01Scrape.bestImgUrl(img, "https://example.com/page").contains("photo.png"));
    }

    @Test
    void buildTabpanelLabelsUsesAriaControls() {
        Document document = Jsoup.parse("<main><div role=\"tab\" aria-controls=\"panel1\">Windows 11</div>"
                + "<div role=\"tabpanel\" id=\"panel1\"><p>Content</p></div></main>");
        assertEquals(Map.of("panel1", "Windows 11"), Stage01Scrape.buildTabpanelLabels(document.selectFirst("main")));
    }

    @Test
    void tabpanelInfoDetectsWrappedElement() {
        Document document = Jsoup.parse("<main><div role=\"tab\" aria-controls=\"p1\">Windows 11</div>"
                + "<div role=\"tabpanel\" id=\"p1\"><img src=\"x.png\"></div></main>");
        Element root = document.selectFirst("main");
        Stage01Scrape.TabpanelInfo info = Stage01Scrape.tabpanelInfo(
                document.selectFirst("img"),
                root,
                Stage01Scrape.buildTabpanelLabels(root)
        );
        assertEquals("p1", info.panelId());
        assertEquals("Windows 11", info.tabLabel());
    }

    @Test
    void buildBlocksProducesOrderedHeadingTextAndImage() {
        Document document = Jsoup.parse("<html><body><main><h2>Heading</h2>"
                + "<p>Text block that is long enough to be included.</p>"
                + "<img src=\"https://example.com/img.png\" alt=\"step\"></main></body></html>");
        List<Map<String, Object>> blocks = Stage01Scrape.buildBlocks(document, "https://example.com/page", "main");
        assertEquals(List.of("heading", "text", "image"), blocks.stream().map(b -> String.valueOf(b.get("type"))).toList());
    }

    @Test
    void buildBlocksInjectsTabLabelAsHeading() {
        Document document = Jsoup.parse("<html><body><main>"
                + "<div role=\"tab\" aria-controls=\"p1\">Windows 11</div>"
                + "<div role=\"tabpanel\" id=\"p1\"><p>Some content that is long enough.</p></div>"
                + "</main></body></html>");
        List<Map<String, Object>> blocks = Stage01Scrape.buildBlocks(document, "https://example.com/page", "main");
        assertTrue(blocks.stream().anyMatch(b -> "Windows 11".equals(b.get("text"))));
    }

    @Test
    void buildBlocksSkipsNoiseTabpanelImages() {
        Document document = Jsoup.parse("<html><body><main>"
                + "<div role=\"tab\" aria-controls=\"community-panel\">Community</div>"
                + "<div role=\"tabpanel\" id=\"community-panel\"><img src=\"https://example.com/community.png\"></div>"
                + "</main></body></html>");
        List<Map<String, Object>> blocks = Stage01Scrape.buildBlocks(document, "https://example.com/page", "main");
        assertTrue(blocks.stream().noneMatch(b -> "https://example.com/community.png".equals(b.get("url"))));
    }

    @Test
    void parsePageHtmlRemovesCookieBanner() {
        List<Map<String, Object>> blocks = Stage01Scrape.parsePageHtml(
                "<html><body><div id=\"onetrust-consent-sdk\">Cookie notice</div>"
                        + "<main><h2>Real content</h2></main></body></html>",
                "https://example.com/page",
                "main"
        );
        assertTrue(blocks.stream().noneMatch(b -> String.valueOf(b.getOrDefault("text", "")).contains("Cookie notice")));
    }

    @Test
    void detectVideoUrlsHandlesYoutubeAndDeduplication() {
        List<String> result = Stage01Scrape.detectVideoUrlsFromHtml(
                "<iframe src=\"https://www.youtube.com/embed/abc123\"></iframe>"
                        + "<iframe src=\"https://www.youtube.com/embed/abc123\"></iframe>"
        );
        assertEquals(List.of("https://www.youtube.com/watch?v=abc123"), result);
    }

    @Test
    void scrapeOnePageReturnsHtmlOnSuccess() {
        Stage01Scrape.BrowserPage page = new Stage01Scrape.BrowserPage() {
            @Override
            public Stage01Scrape.PageResponse gotoUrl(String url) {
                return new Stage01Scrape.PageResponse(200);
            }

            @Override
            public String content() {
                return "<html><body><h1>Hello</h1></body></html>";
            }
        };
        Stage01Scrape.ScrapeResult result = Stage01Scrape.scrapeOnePage(page, "https://example.com");
        assertTrue(result.html().contains("<html>"));
    }

    @Test
    void scrapeOnePageReturnsEmptyOnHttpError() {
        Stage01Scrape.BrowserPage page = url -> new Stage01Scrape.PageResponse(404);
        assertEquals("", Stage01Scrape.scrapeOnePage(page, "https://example.com").html());
    }

    @Test
    void scrapeOnePageReturnsEmptyOnException() {
        Stage01Scrape.BrowserPage page = url -> {
            throw new RuntimeException("network error");
        };
        assertEquals("", Stage01Scrape.scrapeOnePage(page, "https://example.com").html());
    }
}
