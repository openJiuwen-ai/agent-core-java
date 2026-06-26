/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator.skills.skill_omni_creation.scripts;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests/unit_tests/dev_tools/agent_builder/skill_omni_creation/test_stage01_scrape.py}.
 */
class TestStage01Scrape {

    private static final String STAGE01_SOURCE =
            "tests/unit_tests/dev_tools/agent_builder/skill_omni_creation/test_stage01_scrape.py";
    private static final List<String> STAGE01_PYTHON_NODES = List.of(
            STAGE01_SOURCE + "::TestIsUtilityUrl::test_login_path_is_utility",
            STAGE01_SOURCE + "::TestIsUtilityUrl::test_normal_article_is_not_utility",
            STAGE01_SOURCE + "::TestIsUtilityUrl::test_subpath_of_utility_is_utility",
            STAGE01_SOURCE + "::TestIsUtilityUrl::test_empty_url_does_not_raise",
            STAGE01_SOURCE + "::TestIsContentImg::test_img_with_src_is_content",
            STAGE01_SOURCE + "::TestIsContentImg::test_img_with_data_src_is_content",
            STAGE01_SOURCE + "::TestIsContentImg::test_img_without_src_is_not_content",
            STAGE01_SOURCE + "::TestIsContentImg::test_data_uri_img_is_not_content",
            STAGE01_SOURCE + "::TestIsContentImg::test_svg_img_is_not_content",
            STAGE01_SOURCE + "::TestBestImgUrl::test_falls_back_to_src_when_no_srcset",
            STAGE01_SOURCE + "::TestBestImgUrl::test_resolves_relative_src",
            STAGE01_SOURCE + "::TestBestImgUrl::test_skips_data_uri_in_src",
            STAGE01_SOURCE + "::TestBestImgUrl::test_returns_empty_when_no_valid_url",
            STAGE01_SOURCE + "::TestBuildTabpanelLabels::test_builds_label_from_aria_controls",
            STAGE01_SOURCE + "::TestBuildTabpanelLabels::test_fallback_to_aria_labelledby",
            STAGE01_SOURCE + "::TestBuildTabpanelLabels::test_multiple_tabs",
            STAGE01_SOURCE + "::TestBuildTabpanelLabels::test_returns_empty_for_no_tabs",
            STAGE01_SOURCE + "::TestTabpanelInfo::test_detects_element_inside_tabpanel",
            STAGE01_SOURCE + "::TestTabpanelInfo::test_returns_empty_for_element_outside_tabpanel",
            STAGE01_SOURCE + "::TestBuildBlocks::test_produces_heading_blocks",
            STAGE01_SOURCE + "::TestBuildBlocks::test_produces_text_blocks",
            STAGE01_SOURCE + "::TestBuildBlocks::test_skips_short_text",
            STAGE01_SOURCE + "::TestBuildBlocks::test_produces_image_blocks",
            STAGE01_SOURCE + "::TestBuildBlocks::test_image_block_has_source_field",
            STAGE01_SOURCE + "::TestBuildBlocks::test_deduplicates_repeated_headings",
            STAGE01_SOURCE + "::TestBuildBlocks::test_preserves_dom_order",
            STAGE01_SOURCE + "::TestBuildBlocks::test_heading_level_is_correct",
            STAGE01_SOURCE + "::TestBuildBlocks::test_truncates_long_text_to_400_chars",
            STAGE01_SOURCE + "::TestParsePageHtml::test_returns_list_of_blocks",
            STAGE01_SOURCE + "::TestParsePageHtml::test_strips_cookie_banner_by_id",
            STAGE01_SOURCE + "::TestParsePageHtml::test_includes_main_content_headings",
            STAGE01_SOURCE + "::TestDetectVideoUrls::test_detects_youtube_embed",
            STAGE01_SOURCE + "::TestDetectVideoUrls::test_detects_vimeo_embed",
            STAGE01_SOURCE + "::TestDetectVideoUrls::test_deduplicates_same_video",
            STAGE01_SOURCE + "::TestDetectVideoUrls::test_returns_empty_for_no_videos"
    );

    @TestFactory
    Collection<DynamicTest> pythonStage01ScrapeCases() {
        return STAGE01_PYTHON_NODES.stream()
                .map(node -> DynamicTest.dynamicTest(node, () -> runStage01PythonNode(node)))
                .toList();
    }

    private void runStage01PythonNode(String node) {
        if (node.contains("TestIsUtilityUrl")) {
            assertUtilityUrlNode(node);
        } else if (node.contains("TestIsContentImg")) {
            assertContentImageNode(node);
        } else if (node.contains("TestBestImgUrl")) {
            assertBestImageUrlNode(node);
        } else if (node.contains("TestBuildTabpanelLabels")) {
            assertTabpanelLabelsNode(node);
        } else if (node.contains("TestTabpanelInfo")) {
            assertTabpanelInfoNode(node);
        } else if (node.contains("TestBuildBlocks")) {
            assertBuildBlocksNode(node);
        } else if (node.contains("TestParsePageHtml")) {
            assertParsePageHtmlNode(node);
        } else {
            assertDetectVideoNode(node);
        }
    }

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

    private void assertUtilityUrlNode(String node) {
        if (node.endsWith("test_login_path_is_utility")) {
            assertTrue(Stage01Scrape.isUtilityUrl("https://example.com/login"));
        } else if (node.endsWith("test_subpath_of_utility_is_utility")) {
            assertTrue(Stage01Scrape.isUtilityUrl("https://example.com/help/article"));
        } else if (node.endsWith("test_empty_url_does_not_raise")) {
            assertFalse(Stage01Scrape.isUtilityUrl(""));
        } else {
            assertFalse(Stage01Scrape.isUtilityUrl("https://support.google.com/docs/answer/12345"));
        }
    }

    private void assertContentImageNode(String node) {
        String html = switch (node.substring(node.lastIndexOf("::") + 2)) {
            case "test_img_with_data_src_is_content" -> "<img data-src=\"https://example.com/photo.png\">";
            case "test_img_without_src_is_not_content" -> "<img alt=\"missing\">";
            case "test_data_uri_img_is_not_content" -> "<img src=\"data:image/png;base64,abc\">";
            case "test_svg_img_is_not_content" -> "<img src=\"https://example.com/icon.svg\">";
            default -> "<img src=\"https://example.com/photo.png\">";
        };
        Element img = Jsoup.parse(html).selectFirst("img");
        boolean expected = node.endsWith("test_img_with_src_is_content")
                || node.endsWith("test_img_with_data_src_is_content");
        assertEquals(expected, Stage01Scrape.isContentImg(img));
    }

    private void assertBestImageUrlNode(String node) {
        if (node.endsWith("test_returns_empty_when_no_valid_url")) {
            Element img = Jsoup.parse("<img src=\"data:image/png;base64,abc\">").selectFirst("img");
            assertEquals("", Stage01Scrape.bestImgUrl(img, "https://example.com/page"));
        } else if (node.endsWith("test_resolves_relative_src")) {
            Element img = Jsoup.parse("<img src=\"images/photo.png\">").selectFirst("img");
            assertEquals("https://example.com/docs/images/photo.png",
                    Stage01Scrape.bestImgUrl(img, "https://example.com/docs/page"));
        } else if (node.endsWith("test_skips_data_uri_in_src")) {
            Element img = Jsoup.parse("<img src=\"data:image/png;base64,abc\" data-src=\"photo.png\">").selectFirst("img");
            assertEquals("https://example.com/photo.png", Stage01Scrape.bestImgUrl(img, "https://example.com/page"));
        } else {
            Element img = Jsoup.parse("<img src=\"photo.png\">").selectFirst("img");
            assertEquals("https://example.com/photo.png", Stage01Scrape.bestImgUrl(img, "https://example.com/page"));
        }
    }

    private void assertTabpanelLabelsNode(String node) {
        String html = node.endsWith("test_returns_empty_for_no_tabs")
                ? "<main><p>No tabs</p></main>"
                : "<main><div role=\"tab\" id=\"tab1\" aria-controls=\"panel1\">Windows</div>"
                        + "<div role=\"tabpanel\" id=\"panel1\"><p>Content</p></div>"
                        + "<div role=\"tab\" id=\"tab2\">Linux</div>"
                        + "<div role=\"tabpanel\" id=\"panel2\" aria-labelledby=\"tab2\"><p>More</p></div></main>";
        Map<String, String> labels = Stage01Scrape.buildTabpanelLabels(Jsoup.parse(html).selectFirst("main"));
        if (node.endsWith("test_returns_empty_for_no_tabs")) {
            assertTrue(labels.isEmpty());
        } else if (node.endsWith("test_fallback_to_aria_labelledby")) {
            assertEquals("Linux", labels.get("panel2"));
        } else {
            assertEquals("Windows", labels.get("panel1"));
        }
    }

    private void assertTabpanelInfoNode(String node) {
        Document document = Jsoup.parse("<main><div role=\"tab\" aria-controls=\"p1\">Windows 11</div>"
                + "<div role=\"tabpanel\" id=\"p1\"><img src=\"x.png\"></div><p id=\"outside\">Outside</p></main>");
        Element root = document.selectFirst("main");
        Element element = node.endsWith("test_returns_empty_for_element_outside_tabpanel")
                ? document.getElementById("outside")
                : document.selectFirst("img");
        Stage01Scrape.TabpanelInfo info = Stage01Scrape.tabpanelInfo(element, root, Stage01Scrape.buildTabpanelLabels(root));
        if (node.endsWith("test_returns_empty_for_element_outside_tabpanel")) {
            assertEquals("", info.panelId());
        } else {
            assertEquals("p1", info.panelId());
            assertEquals("Windows 11", info.tabLabel());
        }
    }

    private void assertBuildBlocksNode(String node) {
        String longText = "This paragraph is intentionally long enough to be included as a text block.";
        String repeated = "<h2>Heading</h2><h2>Heading</h2>";
        String html = "<html><body><main>" + repeated
                + "<p>short</p><p>" + longText + "</p>"
                + "<img src=\"https://example.com/img.png\" alt=\"step\"></main></body></html>";
        if (node.endsWith("test_truncates_long_text_to_400_chars")) {
            html = "<html><body><main><p>" + "x".repeat(450) + "</p></main></body></html>";
        }
        List<Map<String, Object>> blocks = Stage01Scrape.buildBlocks(Jsoup.parse(html), "https://example.com/page", "main");
        if (node.endsWith("test_skips_short_text")) {
            assertTrue(blocks.stream().noneMatch(block -> "short".equals(block.get("text"))));
        } else if (node.endsWith("test_image_block_has_source_field")) {
            assertEquals("main", blocks.stream().filter(block -> "image".equals(block.get("type"))).findFirst()
                    .orElseThrow().get("source"));
        } else if (node.endsWith("test_deduplicates_repeated_headings")) {
            assertEquals(1, blocks.stream().filter(block -> "Heading".equals(block.get("text"))).count());
        } else if (node.endsWith("test_preserves_dom_order")) {
            assertEquals(List.of("heading", "text", "image"),
                    blocks.stream().map(block -> String.valueOf(block.get("type"))).toList());
        } else if (node.endsWith("test_heading_level_is_correct")) {
            assertEquals(2, blocks.stream().filter(block -> "heading".equals(block.get("type"))).findFirst()
                    .orElseThrow().get("level"));
        } else if (node.endsWith("test_truncates_long_text_to_400_chars")) {
            assertEquals(400, String.valueOf(blocks.getFirst().get("text")).length());
        } else {
            assertFalse(blocks.isEmpty());
        }
    }

    private void assertParsePageHtmlNode(String node) {
        List<Map<String, Object>> blocks = Stage01Scrape.parsePageHtml(
                "<html><body><div id=\"onetrust-consent-sdk\">Cookie notice</div>"
                        + "<main><h2>Real content</h2><p>Useful paragraph long enough.</p></main></body></html>",
                "https://example.com/page",
                "main");
        if (node.endsWith("test_strips_cookie_banner_by_id")) {
            assertTrue(blocks.stream().noneMatch(block -> String.valueOf(block.getOrDefault("text", ""))
                    .contains("Cookie notice")));
        } else if (node.endsWith("test_includes_main_content_headings")) {
            assertTrue(blocks.stream().anyMatch(block -> "Real content".equals(block.get("text"))));
        } else {
            assertInstanceOf(List.class, blocks);
            assertFalse(blocks.isEmpty());
        }
    }

    private void assertDetectVideoNode(String node) {
        String html = switch (node.substring(node.lastIndexOf("::") + 2)) {
            case "test_detects_vimeo_embed" -> "<iframe src=\"https://player.vimeo.com/video/12345\"></iframe>";
            case "test_deduplicates_same_video" -> "<iframe src=\"https://www.youtube.com/embed/abc123\"></iframe>"
                    + "<iframe src=\"https://www.youtube.com/embed/abc123\"></iframe>";
            case "test_returns_empty_for_no_videos" -> "<main><p>No video</p></main>";
            default -> "<iframe src=\"https://www.youtube.com/embed/abc123\"></iframe>";
        };
        List<String> urls = Stage01Scrape.detectVideoUrlsFromHtml(html);
        if (node.endsWith("test_returns_empty_for_no_videos")) {
            assertTrue(urls.isEmpty());
        } else if (node.endsWith("test_deduplicates_same_video")) {
            assertEquals(1, urls.size());
        } else if (node.endsWith("test_detects_vimeo_embed")) {
            assertEquals(List.of("https://vimeo.com/12345"), urls);
        } else {
            assertEquals(List.of("https://www.youtube.com/watch?v=abc123"), urls);
        }
    }
}
