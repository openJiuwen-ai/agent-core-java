/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Site profiles and selector cache helpers for compact browser probes.
 *
 * <p>Mirrors Python's
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/site_profiles.py}.
 */
public final class BrowserSiteProfiles {

    private static final List<Map<String, Object>> BUILTIN_SITE_PROFILES = List.of(
            Map.of(
                    "id", "books_to_scrape",
                    "domains", List.of("books.toscrape.com"),
                    "route_patterns", List.of("^/$", "^/catalogue/"),
                    "card_container_selectors", List.of("article.product_pod", "ol.row > li > article.product_pod"),
                    "title_selectors", List.of("h3 a[title]", "h3 a", "a[title]", "img[alt]"),
                    "price_selectors", List.of(".price_color", "[class*='price' i]"),
                    "rating_selectors", List.of("p.star-rating", "[class*='star-rating' i]", "[class*='rating' i]"),
                    "availability_selectors", List.of(".availability", "[class*='availability' i]", "[class*='stock' i]"),
                    "primary_link_selectors", List.of("h3 a[href]", "a[href][title]", "a[href]"),
                    "button_selectors", List.of("form button", "button", "[role='button']", "input[type='submit']")
            )
    );

    private BrowserSiteProfiles() {
    }

    public static List<Map<String, Object>> builtinSiteProfiles() {
        return BUILTIN_SITE_PROFILES.stream().map(BrowserSiteProfiles::deepCopy).toList();
    }

    public static String normalizeRouteSignature(String url) {
        URI uri = URI.create(url == null || url.isBlank() ? "https://example.invalid/" : url);
        String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
        path = path.replaceAll("\\d+", "*").replaceAll("/+", "/");
        if (!"/".equals(path) && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isBlank() ? "/" : path;
    }

    public static String domainFromUrl(String url) {
        URI uri = URI.create(url == null || url.isBlank() ? "https://example.invalid/" : url);
        return uri.getHost() == null ? "" : uri.getHost();
    }

    public static Path defaultCachePath() {
        String raw = System.getenv("OPENJIUWEN_BROWSER_SELECTOR_CACHE");
        if (raw != null && !raw.isBlank()) {
            return Path.of(raw).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".openjiuwen", "browser_selector_cache.json")
                .toAbsolutePath()
                .normalize();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nestedMap) {
                copy.put(entry.getKey(), deepCopy((Map<String, Object>) nestedMap));
            } else if (value instanceof List<?> list) {
                copy.put(entry.getKey(), list.stream().map(item -> {
                    if (item instanceof Map<?, ?> map) {
                        return deepCopy((Map<String, Object>) map);
                    }
                    return item;
                }).toList());
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }
}
