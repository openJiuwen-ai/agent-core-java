/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HTMLFileParserTest {
    @TempDir
    Path tempDir;

    @Test
    void constantsShouldMatchPythonDefaults() {
        assertThat(HTMLFileParser.DEFAULT_USER_AGENT).contains("Chrome/120.0.0.0");
        assertThat(HTMLFileParser.DEFAULT_TIMEOUT).isEqualTo(30.0);
        assertThat(HTMLFileParser.MAIN_CONTENT_SELECTORS).contains("article", "main", ".content");
    }

    @Test
    void parseHtmlShouldPreferOgTitleAndExtractMainContent() {
        String html = minimalHtml("<article><p>" + longText(120) + "</p></article>",
                "<meta property=\"og:title\" content=\"  OG Title  \"/>", "TitleTag");

        List<Document> docs = HTMLFileParser.parseHtml(html, "id-1", "/tmp/x.html");

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getId()).isEqualTo("id-1");
        assertThat(docs.get(0).getMetadata()).containsEntry("title", "OG Title");
        assertThat(docs.get(0).getMetadata()).containsEntry("source_type", "web_page");
        assertThat(docs.get(0).getText()).contains(longText(40));
    }

    @Test
    void parseHtmlShouldUsePlaceholderTitleWhenMissing() {
        String html = "<html><body><article><p>" + longText(120) + "</p></article></body></html>";

        List<Document> docs = HTMLFileParser.parseHtml(html, "id-1", "x.html");

        assertThat(docs.get(0).getMetadata()).containsEntry("title", "(无标题)");
    }

    @Test
    void findMainContentShouldTrySelectorsInOrderThenFallback() {
        String html = minimalHtml(
                "<article>" + longText(50) + "</article>"
                        + "<div class=\"content\"><p>" + longText(120) + "</p></div>",
                "", "DocTitle");

        String node = HTMLFileParser.findMainContent(html);

        assertThat(HTMLFileParser.getTextFromHtml(node)).hasSizeGreaterThan(100);
        assertThat(node).contains("content");
    }

    @Test
    void parseHtmlShouldRaiseWhenNoMainContentOrTextTooShort() {
        assertThatThrownBy(() -> HTMLFileParser.parseHtml("", "", "empty.html"))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus()).isEqualTo(StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR));

        String html = minimalHtml("<article><p>short</p></article>", "", "DocTitle");
        assertThatThrownBy(() -> HTMLFileParser.parseHtml(html, "", "short.html"))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("too short or empty");
    }

    @Test
    void parseFileShouldDefaultDocIdToPathAndReadUtf8() throws IOException {
        String html = minimalHtml("<main><p>日本語 " + longText(120) + "</p></main>", "", "DocTitle");
        Path file = tempDir.resolve("sample.HTML");
        Files.writeString(file, html, StandardCharsets.UTF_8);

        List<Document> docs = new HTMLFileParser().parse(file.toString(), "", null, Map.of());

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getId()).isEqualTo(file.toString());
        assertThat(docs.get(0).getText()).contains("日本語");
    }

    @Test
    void parseEmptyOrMissingFileShouldRaiseFetchError() throws IOException {
        Path empty = tempDir.resolve("empty.html");
        Files.writeString(empty, "", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new HTMLFileParser().parse(empty.toString(), "", null, Map.of()))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus()).isEqualTo(StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR));
        assertThatThrownBy(() -> new HTMLFileParser().parse(tempDir.resolve("missing.html").toString(), "", null, Map.of()))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus()).isEqualTo(StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR));
    }

    @Test
    void autoFileParserShouldRouteHtmlFiles() throws IOException {
        String html = minimalHtml("<article><p>" + longText(120) + "</p></article>", "", "DocTitle");
        Path file = tempDir.resolve("sample.html");
        Files.writeString(file, html, StandardCharsets.UTF_8);

        AutoFileParser parser = new AutoFileParser();
        List<Document> docs = parser.parse(file.toString(), "auto-1", null, Map.of());

        assertThat(AutoFileParser.getSupportedFormats()).contains(".html", ".htm");
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getMetadata()).containsEntry("doc_id", "auto-1");
        assertThat(docs.get(0).getMetadata()).containsEntry("file_ext", ".html");
        assertThat(docs.get(0).getText()).contains(longText(40));
    }

    private static String minimalHtml(String inner, String headExtra, String title) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>"
                + headExtra
                + "<title>" + title + "</title></head><body>"
                + inner
                + "</body></html>";
    }

    private static String longText(int minChars) {
        return "w".repeat(minChars);
    }
}
