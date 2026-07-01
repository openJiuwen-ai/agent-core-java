/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small JSON selector cache for repeated browser probe discoveries.
 *
 * <p>Mirrors Python's {@code BrowserSelectorCache} in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/site_profiles.py}.
 */
public final class BrowserSelectorCache {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> CHROME_SELECTOR_FRAGMENTS = List.of(
            "#nav", "nav-", "navbar", "breadcrumb", "header", "footer", "menu", "sidebar"
    );
    private static final Set<String> CHROME_TITLES = Set.of(
            "fresh & fast", "sell", "best sellers", "customer service", "today's deals",
            "new releases", "help", "login", "sign in"
    );

    private final Path path;

    public BrowserSelectorCache(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    public Path getPath() {
        return path;
    }

    public List<Map<String, Object>> exportForProbe() {
        return exportForProbe(100);
    }

    public List<Map<String, Object>> exportForProbe(int maxRecords) {
        Map<String, Object> data = load();
        Object rawRecords = data.get("records");
        if (!(rawRecords instanceof List<?> records)) {
            return List.of();
        }
        return records.stream()
                .filter(Map.class::isInstance)
                .map(item -> cast((Map<?, ?>) item))
                .sorted(Comparator
                        .comparingDouble((Map<String, Object> item) -> doubleValue(item.get("quality_score")))
                        .thenComparingInt(item -> intValue(item.get("success_count")))
                        .thenComparingInt(item -> -intValue(item.get("failure_count")))
                        .thenComparingDouble(item -> doubleValue(item.get("last_success_at")))
                        .reversed())
                .limit(maxRecords)
                .map(BrowserSelectorCache::deepCopy)
                .toList();
    }

    public void recordCardProbeResult(Map<String, Object> result) {
        if (result == null || !Boolean.TRUE.equals(result.get("ok"))) {
            return;
        }
        String url = stringValue(result.get("url"));
        String domain = BrowserSiteProfiles.domainFromUrl(url);
        if (domain.isBlank()) {
            return;
        }
        String routeSignature = BrowserSiteProfiles.normalizeRouteSignature(url);
        Object rawCards = result.get("cards");
        if (!(rawCards instanceof List<?> cards) || cards.isEmpty()) {
            return;
        }

        List<Map<String, Object>> cacheableCards = cards.stream()
                .filter(Map.class::isInstance)
                .map(item -> cast((Map<?, ?>) item))
                .filter(this::isCacheableCard)
                .toList();
        if (cacheableCards.size() < 2) {
            return;
        }

        Map<String, List<String>> selectors = new LinkedHashMap<>();
        selectors.put("card_container_selectors", new ArrayList<>());
        selectors.put("title_selectors", new ArrayList<>());
        selectors.put("price_selectors", new ArrayList<>());
        selectors.put("rating_selectors", new ArrayList<>());
        selectors.put("availability_selectors", new ArrayList<>());
        selectors.put("primary_link_selectors", new ArrayList<>());
        selectors.put("button_selectors", new ArrayList<>());

        for (Map<String, Object> card : cacheableCards.stream().limit(10).toList()) {
            selectors.get("card_container_selectors").addAll(generalizeSelector(stringValue(card.get("selector_hint"))));
            selectors.get("title_selectors").addAll(generalizeSelector(stringValue(card.get("title_selector_hint"))));
            selectors.get("price_selectors").addAll(generalizeSelector(stringValue(card.get("price_selector_hint"))));
            selectors.get("rating_selectors").addAll(generalizeSelector(stringValue(card.get("rating_selector_hint"))));
            selectors.get("availability_selectors").addAll(generalizeSelector(stringValue(card.get("availability_selector_hint"))));
            selectors.get("primary_link_selectors").addAll(generalizeSelector(stringValue(card.get("primary_link_selector_hint"))));
            Object rawButtons = card.get("buttons");
            if (rawButtons instanceof List<?> buttons) {
                for (Object button : buttons.stream().limit(4).toList()) {
                    if (button instanceof Map<?, ?> buttonMap) {
                        selectors.get("button_selectors").addAll(generalizeSelector(stringValue(buttonMap.get("selector_hint"))));
                    }
                }
            }
        }

        Map<String, List<String>> compactSelectors = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : selectors.entrySet()) {
            List<String> unique = unique(entry.getValue(), 20);
            if (!unique.isEmpty()) {
                compactSelectors.put(entry.getKey(), unique);
            }
        }
        if (compactSelectors.isEmpty()) {
            return;
        }

        List<Integer> qualityScores = cacheableCards.stream().map(this::cardQualityScore).toList();
        double qualityScore = Math.min(
                1.0,
                qualityScores.stream().mapToInt(Integer::intValue).average().orElse(0.0) / 100.0
        );

        Map<String, Object> data = load();
        List<Map<String, Object>> records = new ArrayList<>();
        Object rawRecords = data.get("records");
        if (rawRecords instanceof List<?> recordList) {
            for (Object item : recordList) {
                if (item instanceof Map<?, ?> recordMap) {
                    records.add(cast(recordMap));
                }
            }
        }

        Map<String, Object> existing = records.stream()
                .filter(record -> domain.equals(record.get("domain"))
                        && routeSignature.equals(record.get("route_signature"))
                        && "card_probe".equals(record.get("kind")))
                .findFirst()
                .orElse(null);

        double now = System.currentTimeMillis() / 1000.0;
        if (existing == null) {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("domain", domain);
            created.put("route_signature", routeSignature);
            created.put("kind", "card_probe");
            created.put("selectors", compactSelectors);
            created.put("success_count", 1);
            created.put("failure_count", 0);
            created.put("last_success_at", now);
            created.put("quality_score", qualityScore);
            created.put("sample_card_count", cacheableCards.size());
            records.add(created);
        } else {
            Map<String, List<String>> mergedSelectors = new LinkedHashMap<>();
            Object rawSelectorMap = existing.get("selectors");
            if (rawSelectorMap instanceof Map<?, ?> selectorMap) {
                for (Map.Entry<?, ?> entry : selectorMap.entrySet()) {
                    if (entry.getKey() instanceof String key && entry.getValue() instanceof List<?> values) {
                        mergedSelectors.put(key, values.stream().map(BrowserSelectorCache::stringValue).toList());
                    }
                }
            }
            for (Map.Entry<String, List<String>> entry : compactSelectors.entrySet()) {
                List<String> merged = new ArrayList<>(mergedSelectors.getOrDefault(entry.getKey(), List.of()));
                merged.addAll(entry.getValue());
                mergedSelectors.put(entry.getKey(), unique(merged, 20));
            }
            existing.put("selectors", mergedSelectors);
            existing.put("success_count", intValue(existing.get("success_count")) + 1);
            existing.put("last_success_at", now);
            existing.put("quality_score", Math.max(doubleValue(existing.get("quality_score")), qualityScore));
            existing.put("sample_card_count", Math.max(intValue(existing.get("sample_card_count")), cacheableCards.size()));
        }

        List<Map<String, Object>> trimmed = records.size() <= 200
                ? records
                : records.subList(records.size() - 200, records.size());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", 1);
        payload.put("records", trimmed);
        save(payload);
    }

    private Map<String, Object> load() {
        if (!Files.exists(path)) {
            return new LinkedHashMap<>(Map.of("version", 1, "records", new ArrayList<>()));
        }
        try {
            Map<String, Object> raw = OBJECT_MAPPER.readValue(path.toFile(), new TypeReference<>() {
            });
            if (raw == null) {
                return new LinkedHashMap<>(Map.of("version", 1, "records", new ArrayList<>()));
            }
            raw.putIfAbsent("version", 1);
            raw.computeIfAbsent("records", unused -> new ArrayList<>());
            return raw;
        } catch (IOException ex) {
            return new LinkedHashMap<>(Map.of("version", 1, "records", new ArrayList<>()));
        }
    }

    private void save(Map<String, Object> data) {
        try {
            Files.createDirectories(path.getParent());
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), data);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save selector cache", ex);
        }
    }

    private boolean isCacheableCard(Map<String, Object> card) {
        int score = cardQualityScore(card);
        if (score >= 42) {
            return true;
        }
        String preview = stringValue(card.get("text_preview"));
        boolean hasLink = !stringValue(card.get("primary_link")).isBlank();
        boolean hasButtons = card.get("buttons") instanceof List<?> buttons && !buttons.isEmpty();
        return score >= 30 && preview.length() >= 80 && (hasLink || hasButtons);
    }

    private int cardQualityScore(Map<String, Object> card) {
        if (looksLikePageChrome(card)) {
            return 0;
        }
        int score = 0;
        String title = stringValue(card.get("title"));
        String preview = stringValue(card.get("text_preview"));
        Object buttons = card.get("buttons");
        if (title.length() >= 8) {
            score += 20;
        }
        if (preview.length() >= 60) {
            score += 15;
        }
        if (!stringValue(card.get("primary_link")).isBlank()) {
            score += 12;
        }
        if (!stringValue(card.get("price")).isBlank()) {
            score += 18;
        }
        if (!stringValue(card.get("rating")).isBlank()) {
            score += 14;
        }
        if (!stringValue(card.get("review_count")).isBlank()) {
            score += 10;
        }
        if (!stringValue(card.get("availability")).isBlank()) {
            score += 8;
        }
        if (Boolean.TRUE.equals(card.get("has_image"))) {
            score += 12;
        }
        if (buttons instanceof List<?> list && !list.isEmpty()) {
            score += 8;
        }
        return score;
    }

    private boolean looksLikePageChrome(Map<String, Object> card) {
        String selector = stringValue(card.get("selector_hint")).toLowerCase();
        String title = stringValue(card.get("title")).toLowerCase();
        String preview = stringValue(card.get("text_preview")).toLowerCase();
        return CHROME_SELECTOR_FRAGMENTS.stream().anyMatch(selector::contains)
                || CHROME_TITLES.contains(title)
                || CHROME_TITLES.contains(preview);
    }

    private List<String> generalizeSelector(String selector) {
        String value = selector == null ? "" : selector.trim();
        if (value.isEmpty()) {
            return List.of();
        }
        String noNth = value.replaceAll(":nth-of-type\\(\\d+\\)", "");
        List<String> parts = List.of(noNth.split(">")).stream().map(String::trim).filter(part -> !part.isEmpty()).toList();
        List<String> variants = new ArrayList<>();
        if (!parts.isEmpty()) {
            variants.add(parts.get(parts.size() - 1));
        }
        if (parts.size() >= 2) {
            variants.add(String.join(" > ", parts.subList(parts.size() - 2, parts.size())));
        }
        if (parts.size() >= 3) {
            variants.add(String.join(" > ", parts.subList(parts.size() - 3, parts.size())));
        }
        if (!noNth.isBlank()) {
            variants.add(noNth);
        }
        return unique(variants, 4).stream().filter(item -> !selectorTooBroad(item)).toList();
    }

    private boolean selectorTooBroad(String selector) {
        String value = stringValue(selector).toLowerCase();
        if (value.isBlank()) {
            return true;
        }
        if (Set.of("div", "li", "section", "article", "a", "span", "button").contains(value)) {
            return true;
        }
        return CHROME_SELECTOR_FRAGMENTS.stream().anyMatch(value::contains);
    }

    private static List<String> unique(List<String> items, int limit) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String item : items) {
            String value = stringValue(item);
            if (value.isBlank() || seen.contains(value)) {
                continue;
            }
            seen.add(value);
            result.add(value);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> rawMap) {
        Map<String, Object> casted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key) {
                if (entry.getValue() instanceof Map<?, ?> nestedMap) {
                    casted.put(key, cast(nestedMap));
                } else if (entry.getValue() instanceof List<?> list) {
                    casted.put(key, list.stream().map(item -> {
                        if (item instanceof Map<?, ?> map) {
                            return cast(map);
                        }
                        return item;
                    }).toList());
                } else {
                    casted.put(key, entry.getValue());
                }
            }
        }
        return casted;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        return cast(source);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intValue(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static double doubleValue(Object value) {
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException ex) {
            return 0.0;
        }
    }
}
