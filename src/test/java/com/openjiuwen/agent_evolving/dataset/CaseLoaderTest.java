/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.dataset;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CaseLoader and related utilities.
 *
 * <p>Mirrors Python's {@code CaseLoader}, {@code shuffle_cases}, and {@code split_cases} in
 * {@code openjiuwen/agent_evolving/dataset/case_loader.py}.</p>
 */
class CaseLoaderTest {

    @Test
    void shuffleDeterministicWithSameSeed() {
        List<Case> cases = makeCases(5);

        List<Case> result1 = CaseLoader.shuffleCases(cases, 42);
        List<Case> result2 = CaseLoader.shuffleCases(cases, 42);

        assertEquals(ids(result1), ids(result2));
    }

    @Test
    void shuffleReturnsNewListAndPreservesOriginal() {
        List<Case> cases = makeCases(5);
        List<Case> original = new ArrayList<>(cases);

        List<Case> result = CaseLoader.shuffleCases(cases, 0);

        assertEquals(original, cases);
        assertNotSame(result, cases);
    }

    @Test
    void shuffleDifferentSeedsDifferentOrders() {
        List<Case> cases = makeCases(10);

        List<Case> result1 = CaseLoader.shuffleCases(cases, 1);
        List<Case> result2 = CaseLoader.shuffleCases(cases, 2);

        assertFalse(ids(result1).equals(ids(result2)));
    }

    @Test
    void shuffleEmptyList() {
        assertEquals(List.of(), CaseLoader.shuffleCases(List.of(), 0));
    }

    @Test
    void shuffleSingleElement() {
        List<Case> result = CaseLoader.shuffleCases(makeCases(1), 0);

        assertEquals(1, result.size());
    }

    @Test
    void shuffleMatchesPythonRandomSeed42Vector() {
        List<Case> result = CaseLoader.shuffleCases(makeCases(10), 42);

        assertEquals(List.of(7, 3, 2, 8, 5, 6, 9, 4, 0, 1), ids(result));
    }

    @Test
    void splitHalf() {
        CaseLoader.CaseListSplit split = CaseLoader.splitCases(makeCases(10), 0.5d);

        assertEquals(5, split.left().size());
        assertEquals(5, split.right().size());
    }

    @Test
    void splitZeroRatio() {
        CaseLoader.CaseListSplit split = CaseLoader.splitCases(makeCases(10), 0.0d);

        assertEquals(0, split.left().size());
        assertEquals(10, split.right().size());
    }

    @Test
    void splitOneRatio() {
        CaseLoader.CaseListSplit split = CaseLoader.splitCases(makeCases(10), 1.0d);

        assertEquals(10, split.left().size());
        assertEquals(0, split.right().size());
    }

    @Test
    void splitQuarter() {
        CaseLoader.CaseListSplit split = CaseLoader.splitCases(makeCases(20), 0.25d);

        assertEquals(5, split.left().size());
        assertEquals(15, split.right().size());
    }

    @Test
    void splitNegativeRatioRaises() {
        assertThrows(IllegalArgumentException.class, () -> CaseLoader.splitCases(makeCases(10), -0.1d));
    }

    @Test
    void splitRatioOverOneRaises() {
        assertThrows(IllegalArgumentException.class, () -> CaseLoader.splitCases(makeCases(10), 1.1d));
    }

    @Test
    void splitEmptyList() {
        CaseLoader.CaseListSplit split = CaseLoader.splitCases(List.of(), 0.5d);

        assertEquals(List.of(), split.left());
        assertEquals(List.of(), split.right());
    }

    @Test
    void loaderCreationAndLength() {
        CaseLoader loader = new CaseLoader(makeCases(5));

        assertEquals(5, loader.size());
    }

    @Test
    void loaderLength() {
        CaseLoader loader = new CaseLoader(makeCases(7));

        assertEquals(7, loader.size());
    }

    @Test
    void loaderIteration() {
        CaseLoader loader = new CaseLoader(makeCases(3));
        List<Case> items = new ArrayList<>();
        for (Case item : loader) {
            items.add(item);
        }

        assertEquals(3, items.size());
        assertEquals(0, items.get(0).getInputs().get("id"));
    }

    @Test
    void getCasesReturnsCopy() {
        List<Case> cases = makeCases(3);
        CaseLoader loader = new CaseLoader(cases);

        List<Case> retrieved = loader.getCases();

        assertEquals(3, retrieved.size());
        assertNotSame(cases, retrieved);
        assertEquals(cases, retrieved);
    }

    @Test
    void emptyLoader() {
        CaseLoader loader = new CaseLoader(List.of());

        assertEquals(0, loader.size());
        assertFalse(loader.iterator().hasNext());
    }

    @Test
    void loaderSplitMethod() {
        CaseLoader loader = new CaseLoader(makeCases(10));

        CaseLoader.CaseLoaderSplit split = loader.split(0.5d, 42);

        assertEquals(5, split.left().size());
        assertEquals(5, split.right().size());
    }

    @Test
    void loaderSplitPreservesOriginal() {
        CaseLoader loader = new CaseLoader(makeCases(10));

        loader.split(0.5d, 0);

        assertEquals(10, loader.size());
    }

    @Test
    void loaderSplitDifferentSeeds() {
        CaseLoader loader = new CaseLoader(makeCases(10));

        CaseLoader.CaseLoaderSplit split1 = loader.split(0.5d, 1);
        CaseLoader.CaseLoaderSplit split2 = loader.split(0.5d, 2);

        assertFalse(ids(split1.left().getCases()).equals(ids(split2.left().getCases())));
    }

    @Test
    void loaderSplitEmptyLoader() {
        CaseLoader loader = new CaseLoader(List.of());

        CaseLoader.CaseLoaderSplit split = loader.split(0.5d, 0);

        assertEquals(0, split.left().size());
        assertEquals(0, split.right().size());
    }

    @Test
    void loaderSplitInvalidRatio() {
        CaseLoader loader = new CaseLoader(makeCases(5));

        assertThrows(IllegalArgumentException.class, () -> loader.split(1.5d, 0));
    }

    @Test
    void loaderSplitZeroRatio() {
        CaseLoader loader = new CaseLoader(makeCases(5));

        CaseLoader.CaseLoaderSplit split = loader.split(0.0d, 0);

        assertEquals(0, split.left().size());
        assertEquals(5, split.right().size());
    }

    @Test
    void loaderSplitOneRatio() {
        CaseLoader loader = new CaseLoader(makeCases(5));

        CaseLoader.CaseLoaderSplit split = loader.split(1.0d, 0);

        assertEquals(5, split.left().size());
        assertEquals(0, split.right().size());
    }

    private static List<Case> makeCases(int count) {
        List<Case> cases = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cases.add(new Case(Map.of("id", i), Map.of("a", "b")));
        }
        return cases;
    }

    private static List<Integer> ids(List<Case> cases) {
        return cases.stream()
                .map(item -> (Integer) item.getInputs().get("id"))
                .toList();
    }
}
