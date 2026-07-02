/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.tools.browser_move.test_browser_site_profiles} in
 * {@code tests/unit_tests/harness/tools/browser_move/test_browser_site_profiles.py}.
 */
class BrowserSiteProfilesTest {

    @TempDir
    Path tempDir;

    @Test
    void builtinProfilesIncludeBooksToScrape() {
        List<Map<String, Object>> profiles = BrowserSiteProfiles.builtinSiteProfiles();
        Map<String, Object> books = profiles.stream()
                .filter(item -> "books_to_scrape".equals(item.get("id")))
                .findFirst()
                .orElseThrow();

        assertTrue(((List<?>) books.get("domains")).contains("books.toscrape.com"));
        assertTrue(((List<?>) books.get("card_container_selectors")).contains("article.product_pod"));
        assertTrue(((List<?>) books.get("title_selectors")).contains("h3 a[title]"));
        assertTrue(((List<?>) books.get("price_selectors")).contains(".price_color"));
    }

    @Test
    void normalizeRouteSignatureGeneralizesNumericPaths() {
        assertEquals(
                "/catalogue/a-light-in-the-attic_*/index.html",
                BrowserSiteProfiles.normalizeRouteSignature(
                        "https://books.toscrape.com/catalogue/a-light-in-the-attic_1000/index.html"
                )
        );
    }

    @Test
    void domainFromUrlExtractsHostname() {
        assertEquals("books.toscrape.com", BrowserSiteProfiles.domainFromUrl("https://books.toscrape.com/"));
    }

    @Test
    void selectorCacheRecordsCardProbeResult() {
        BrowserSelectorCache cache = new BrowserSelectorCache(tempDir.resolve("selector_cache.json"));
        cache.recordCardProbeResult(Map.of(
                "ok", true,
                "url", "https://books.toscrape.com/",
                "cards", List.of(
                        Map.ofEntries(
                                Map.entry("title", "A Light in the Attic"),
                                Map.entry("price", "51.77"),
                                Map.entry("rating", "Three stars"),
                                Map.entry("availability", "In stock"),
                                Map.entry("has_image", true),
                                Map.entry("primary_link", "https://books.toscrape.com/catalogue/a-light-in-the-attic_1000/index.html"),
                                Map.entry("text_preview", "A Light in the Attic Three stars 51.77 In stock Add to basket"),
                                Map.entry("selector_hint", "ol:nth-of-type(1) > li.col-xs-6:nth-of-type(1) > article.product_pod:nth-of-type(1)"),
                                Map.entry("title_selector_hint", "article.product_pod:nth-of-type(1) > h3:nth-of-type(1) > a:nth-of-type(1)"),
                                Map.entry("price_selector_hint", "article.product_pod:nth-of-type(1) > div.product_price:nth-of-type(2) > p.price_color:nth-of-type(1)"),
                                Map.entry("rating_selector_hint", "article.product_pod:nth-of-type(1) > p.star-rating:nth-of-type(1)"),
                                Map.entry("primary_link_selector_hint", "article.product_pod:nth-of-type(1) > h3:nth-of-type(1) > a:nth-of-type(1)"),
                                Map.entry("buttons", List.of(Map.of(
                                        "selector_hint",
                                        "article.product_pod:nth-of-type(1) > div.product_price:nth-of-type(2) > form:nth-of-type(1) > button.btn.btn-primary:nth-of-type(1)"
                                )))
                        ),
                        Map.ofEntries(
                                Map.entry("title", "Tipping the Velvet"),
                                Map.entry("price", "53.74"),
                                Map.entry("rating", "One star"),
                                Map.entry("availability", "In stock"),
                                Map.entry("has_image", true),
                                Map.entry("primary_link", "https://books.toscrape.com/catalogue/tipping-the-velvet_999/index.html"),
                                Map.entry("text_preview", "Tipping the Velvet One star 53.74 In stock Add to basket"),
                                Map.entry("selector_hint", "ol:nth-of-type(1) > li.col-xs-6:nth-of-type(2) > article.product_pod:nth-of-type(1)"),
                                Map.entry("title_selector_hint", "article.product_pod:nth-of-type(1) > h3:nth-of-type(1) > a:nth-of-type(1)"),
                                Map.entry("price_selector_hint", "article.product_pod:nth-of-type(1) > div.product_price:nth-of-type(2) > p.price_color:nth-of-type(1)"),
                                Map.entry("rating_selector_hint", "article.product_pod:nth-of-type(1) > p.star-rating:nth-of-type(1)"),
                                Map.entry("primary_link_selector_hint", "article.product_pod:nth-of-type(1) > h3:nth-of-type(1) > a:nth-of-type(1)"),
                                Map.entry("buttons", List.of(Map.of(
                                        "selector_hint",
                                        "article.product_pod:nth-of-type(1) > div.product_price:nth-of-type(2) > form:nth-of-type(1) > button.btn.btn-primary:nth-of-type(1)"
                                )))
                        )
                )
        ));

        List<Map<String, Object>> exported = cache.exportForProbe();
        assertEquals(1, exported.size());
        assertEquals("books.toscrape.com", exported.get(0).get("domain"));
        assertEquals("card_probe", exported.get(0).get("kind"));
        assertTrue(Double.parseDouble(String.valueOf(exported.get(0).get("quality_score"))) > 0);
        assertEquals(2, Integer.parseInt(String.valueOf(exported.get(0).get("sample_card_count"))));
        Map<?, ?> selectors = (Map<?, ?>) exported.get(0).get("selectors");
        assertTrue(String.valueOf(selectors.get("card_container_selectors")).contains("article.product_pod"));
        assertTrue(String.valueOf(selectors.get("price_selectors")).contains("p.price_color"));
        assertTrue(String.valueOf(selectors.get("rating_selectors")).contains("p.star-rating"));
    }

    @Test
    void selectorCacheDoesNotRecordNavigationOnlyCards() {
        BrowserSelectorCache cache = new BrowserSelectorCache(tempDir.resolve("selector_cache.json"));
        cache.recordCardProbeResult(Map.of(
                "ok", true,
                "url", "https://www.amazon.sg/s?k=wireless+mouse",
                "cards", List.of(
                        Map.of(
                                "title", "Fresh & Fast",
                                "selector_hint", "li.nav-li:nth-of-type(1)",
                                "text_preview", "Fresh & Fast",
                                "primary_link", "https://www.amazon.sg/fresh",
                                "buttons", List.of(Map.of("selector_hint", "li.nav-li > a"))
                        ),
                        Map.of(
                                "title", "Sell",
                                "selector_hint", "li.nav-li:nth-of-type(2)",
                                "text_preview", "Sell",
                                "primary_link", "https://www.amazon.sg/sell",
                                "buttons", List.of(Map.of("selector_hint", "li.nav-li > a"))
                        )
                )
        ));

        assertTrue(cache.exportForProbe().isEmpty());
    }

    @Test
    void selectorCacheKeepsHighQualityCardsOnly() {
        BrowserSelectorCache cache = new BrowserSelectorCache(tempDir.resolve("selector_cache.json"));
        cache.recordCardProbeResult(Map.of(
                "ok", true,
                "url", "https://www.amazon.sg/s?k=wireless+mouse",
                "cards", List.of(
                        Map.ofEntries(
                                Map.entry("title", "Wireless Mouse Example"),
                                Map.entry("selector_hint", "div.s-result-item:nth-of-type(1)"),
                                Map.entry("title_selector_hint", "h2 a span"),
                                Map.entry("price_selector_hint", ".a-price .a-offscreen"),
                                Map.entry("rating_selector_hint", "span.a-icon-alt"),
                                Map.entry("primary_link_selector_hint", "h2 a"),
                                Map.entry("price", "S$20.00"),
                                Map.entry("rating", "4.5 out of 5 stars"),
                                Map.entry("review_count", "1,000 ratings"),
                                Map.entry("has_image", true),
                                Map.entry("text_preview", "Wireless Mouse Example S$20.00 4.5 out of 5 stars 1,000 ratings"),
                                Map.entry("buttons", List.of(Map.of("selector_hint", "button[name='submit.addToCart']")))
                        ),
                        Map.ofEntries(
                                Map.entry("title", "Another Wireless Mouse"),
                                Map.entry("selector_hint", "div.s-result-item:nth-of-type(2)"),
                                Map.entry("title_selector_hint", "h2 a span"),
                                Map.entry("price_selector_hint", ".a-price .a-offscreen"),
                                Map.entry("rating_selector_hint", "span.a-icon-alt"),
                                Map.entry("primary_link_selector_hint", "h2 a"),
                                Map.entry("price", "S$25.00"),
                                Map.entry("rating", "4.3 out of 5 stars"),
                                Map.entry("review_count", "500 ratings"),
                                Map.entry("has_image", true),
                                Map.entry("text_preview", "Another Wireless Mouse S$25.00 4.3 out of 5 stars 500 ratings"),
                                Map.entry("buttons", List.of(Map.of("selector_hint", "button[name='submit.addToCart']")))
                        )
                )
        ));

        List<Map<String, Object>> exported = cache.exportForProbe();
        assertEquals(1, exported.size());
        assertEquals("www.amazon.sg", exported.get(0).get("domain"));
        assertTrue(Double.parseDouble(String.valueOf(exported.get(0).get("quality_score"))) > 0);
        assertTrue(String.valueOf(exported.get(0).get("selectors")).contains("div.s-result-item"));
        assertFalse(String.valueOf(exported.get(0).get("selectors")).contains("li.nav-li"));
    }
}
