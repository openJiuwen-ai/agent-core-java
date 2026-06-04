package com.openjiuwen.agent_evolving.dataset;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CaseLoader and related utilities.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.dataset.test_case_loader}.</p>
 */
class CaseLoaderTest {

    @Test
    void shuffleCasesIsDeterministicForSameSeed() {
        List<Case> cases = makeCases(5);

        List<Case> first = CaseLoader.shuffleCases(cases, 42);
        List<Case> second = CaseLoader.shuffleCases(cases, 42);

        assertEquals(
                first.stream().map(Case::getCaseId).toList(),
                second.stream().map(Case::getCaseId).toList()
        );
        assertNotSame(first, second);
    }

    @Test
    void shuffleCasesMatchesPythonRandomOrder() {
        List<Case> shuffled = CaseLoader.shuffleCases(makeCases(10), 42);

        assertEquals(
                List.of(7, 3, 2, 8, 5, 6, 9, 4, 0, 1),
                shuffled.stream().map(caseData -> caseData.getInputs().get("id")).toList()
        );
    }

    @Test
    void splitCasesRespectsRatioAndKeepsOriginalUntouched() {
        CaseLoader loader = new CaseLoader(makeCases(10));

        CaseLoader[] split = loader.split(0.5, 0);

        assertEquals(5, split[0].size());
        assertEquals(5, split[1].size());
        assertEquals(10, loader.size());
    }

    @Test
    void loaderSplitMatchesPythonShuffleBeforeCut() {
        CaseLoader loader = new CaseLoader(makeCases(10));

        CaseLoader[] split = loader.split(0.5, 42);

        assertEquals(
                List.of(7, 3, 2, 8, 5),
                split[0].getCases().stream().map(caseData -> caseData.getInputs().get("id")).toList()
        );
        assertEquals(
                List.of(6, 9, 4, 0, 1),
                split[1].getCases().stream().map(caseData -> caseData.getInputs().get("id")).toList()
        );
    }

    @Test
    void splitCasesRejectsInvalidRatios() {
        CaseLoader loader = new CaseLoader(makeCases(2));

        assertThrows(IllegalArgumentException.class, () -> loader.split(-0.1, 0));
        assertThrows(IllegalArgumentException.class, () -> loader.split(1.1, 0));
        assertThrows(IllegalArgumentException.class, () -> CaseLoader.splitCases(makeCases(2), 1.1));
    }

    @Test
    void getCasesReturnsCopyAndEmptyLoaderWorks() {
        CaseLoader loader = new CaseLoader(List.of());

        assertTrue(loader.isEmpty());
        assertEquals(List.of(), loader.getCases());
        assertEquals(List.of(), CaseLoader.shuffleCases(null, 0));
    }

    @Test
    void splitCasesHandlesEmptyLists() {
        List<Case>[] split = CaseLoader.splitCases(List.of(), 0.5);

        assertEquals(List.of(), split[0]);
        assertEquals(List.of(), split[1]);
    }

    @Test
    void shuffleCasesReturnsNewList() {
        List<Case> cases = makeCases(5);

        List<Case> result = CaseLoader.shuffleCases(cases, 0);

        assertNotSame(cases, result);
        assertEquals(List.of(0, 1, 2, 3, 4),
                cases.stream().map(caseData -> caseData.getInputs().get("id")).toList());
    }

    @Test
    void shuffleCasesDifferentSeedsProduceDifferentOrders() {
        List<Case> cases = makeCases(10);

        List<Integer> first = CaseLoader.shuffleCases(cases, 1).stream().map(caseData -> caseData.getInputs().get("id")).map(Integer.class::cast).toList();
        List<Integer> second = CaseLoader.shuffleCases(cases, 2).stream().map(caseData -> caseData.getInputs().get("id")).map(Integer.class::cast).toList();

        assertNotEquals(first, second);
    }

    @Test
    void shuffleCasesEmptyListReturnsEmpty() {
        assertEquals(List.of(), CaseLoader.shuffleCases(List.of(), 0));
    }

    @Test
    void shuffleCasesSingleElement() {
        List<Case> result = CaseLoader.shuffleCases(makeCases(1), 0);

        assertEquals(1, result.size());
    }

    @Test
    void splitCasesZeroRatio() {
        List<Case>[] split = CaseLoader.splitCases(makeCases(10), 0.0);

        assertEquals(0, split[0].size());
        assertEquals(10, split[1].size());
    }

    @Test
    void splitCasesOneRatio() {
        List<Case>[] split = CaseLoader.splitCases(makeCases(10), 1.0);

        assertEquals(10, split[0].size());
        assertEquals(0, split[1].size());
    }

    @Test
    void splitCasesQuarterRatio() {
        List<Case>[] split = CaseLoader.splitCases(makeCases(20), 0.25);

        assertEquals(5, split[0].size());
        assertEquals(15, split[1].size());
    }

    @Test
    void splitCasesNegativeRatioRaises() {
        assertThrows(IllegalArgumentException.class, () -> CaseLoader.splitCases(makeCases(10), -0.1));
    }

    @Test
    void splitCasesRatioOverOneRaises() {
        assertThrows(IllegalArgumentException.class, () -> CaseLoader.splitCases(makeCases(10), 1.1));
    }

    @Test
    void caseLoaderCreation() {
        CaseLoader loader = new CaseLoader(makeCases(5));

        assertEquals(5, loader.size());
    }

    @Test
    void caseLoaderLength() {
        CaseLoader loader = new CaseLoader(makeCases(7));

        assertEquals(7, loader.size());
    }

    @Test
    void caseLoaderIteration() {
        CaseLoader loader = new CaseLoader(makeCases(3));

        List<Case> items = loader.getCases();

        assertEquals(3, items.size());
        assertEquals(0, items.getFirst().getInputs().get("id"));
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
        assertEquals(List.of(), loader.getCases());
    }

    @Test
    void splitMethodReturnsTwoLoaders() {
        CaseLoader loader = new CaseLoader(makeCases(10));

        CaseLoader[] split = loader.split(0.5, 42);

        assertEquals(5, split[0].size());
        assertEquals(5, split[1].size());
    }

    @Test
    void splitPreservesOriginal() {
        CaseLoader loader = new CaseLoader(makeCases(10));

        loader.split(0.5, 0);

        assertEquals(10, loader.size());
    }

    @Test
    void splitDifferentSeeds() {
        CaseLoader loader = new CaseLoader(makeCases(10));

        List<Integer> left1 = loader.split(0.5, 1)[0].getCases().stream()
                .map(caseData -> (Integer) caseData.getInputs().get("id"))
                .toList();
        List<Integer> left2 = loader.split(0.5, 2)[0].getCases().stream()
                .map(caseData -> (Integer) caseData.getInputs().get("id"))
                .toList();

        assertNotEquals(left1, left2);
    }

    @Test
    void splitEmptyLoader() {
        CaseLoader loader = new CaseLoader(List.of());

        CaseLoader[] split = loader.split(0.5, 0);

        assertEquals(0, split[0].size());
        assertEquals(0, split[1].size());
    }

    @Test
    void splitInvalidRatio() {
        CaseLoader loader = new CaseLoader(makeCases(5));

        assertThrows(IllegalArgumentException.class, () -> loader.split(1.5, 0));
    }

    @Test
    void splitZeroRatio() {
        CaseLoader loader = new CaseLoader(makeCases(5));

        CaseLoader[] split = loader.split(0.0, 0);

        assertEquals(0, split[0].size());
        assertEquals(5, split[1].size());
    }

    @Test
    void splitOneRatio() {
        CaseLoader loader = new CaseLoader(makeCases(5));

        CaseLoader[] split = loader.split(1.0, 0);

        assertEquals(5, split[0].size());
        assertEquals(0, split[1].size());
    }

    private static List<Case> makeCases(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new Case(Map.of("id", i), Map.of("answer", "ok"), "case_" + i))
                .toList();
    }
}
