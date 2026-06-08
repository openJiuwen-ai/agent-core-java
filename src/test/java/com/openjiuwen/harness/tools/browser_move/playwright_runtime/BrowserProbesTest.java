/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BrowserProbesTest {

    @Test
    void interactiveProbeContainsHighValueSelectorsAndParams() {
        String js = BrowserProbes.buildInteractiveProbeJs(25, true, "");

        assertTrue(js.contains("button"));
        assertTrue(js.contains("a[href]"));
        assertTrue(js.contains("[aria-label]"));
        assertTrue(js.contains("\"max_items\":25"));
        assertTrue(js.contains("\"viewport_only\":true"));
    }

    @Test
    void interactiveProbeClampsMaxItems() {
        String js = BrowserProbes.buildInteractiveProbeJs(999, true, "");

        assertTrue(js.contains("\"max_items\":100"));
    }

    @Test
    void cardProbeContainsExtractionTermsAndClampsMaxCards() {
        String js = BrowserProbes.buildCardProbeJs(999, true, true, "", List.of(), List.of());

        assertTrue(js.contains("\"max_cards\":50"));
        assertTrue(js.contains("recurring_signatures"));
        assertTrue(js.contains("price"));
        assertTrue(js.contains("rating"));
        assertTrue(js.contains("primary_link"));
        assertTrue(js.contains("buttons"));
    }

    @Test
    void cardProbeContainsExpectedSelectorAndNormalizationFragments() {
        String js = BrowserProbes.buildCardProbeJs(10, true, true, "", List.of(), List.of());

        assertTrue(js.contains("const normalize = (value, limit = 180)"));
        assertTrue(js.contains("normalizePriceValue"));
        assertTrue(js.contains("ratingClassValue"));
        assertTrue(js.contains("Five stars"));
        assertTrue(js.contains("Three stars"));
        assertTrue(js.contains("One star"));
        assertTrue(js.contains("depth < 8"));
        assertTrue(js.contains("nth-of-type"));
        assertTrue(js.contains("conflictsWithExisting"));
        assertTrue(js.contains("itemBetter"));
        assertTrue(js.contains("selected.splice"));
        assertTrue(js.contains("document.querySelectorAll(simple).length === 1"));
    }

    @Test
    void cardProbeEmbedsProfilesAndSelectorCacheRecords() {
        String js = BrowserProbes.buildCardProbeJs(
                10,
                true,
                true,
                "",
                List.of(Map.of(
                        "id", "test_shop",
                        "card_container_selectors", List.of(".product-card")
                )),
                List.of(Map.of(
                        "domain", "example.com",
                        "selectors", Map.of("card_container_selectors", List.of(".cached-card"))
                ))
        );

        assertTrue(js.contains("site_profiles"));
        assertTrue(js.contains("selector_cache_records"));
        assertTrue(js.contains(".product-card"));
        assertTrue(js.contains(".cached-card"));
        assertTrue(js.contains("profile_ids"));
        assertTrue(js.contains("cache_records_used"));
    }
}
