package com.openjiuwen.agentevolving.dataset;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

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

        CaseLoader.CaseLoaderSplit split = loader.split(0.5, 0);

        assertEquals(5, split.left().size());
        assertEquals(5, split.right().size());
        assertEquals(10, loader.size());
    }

    @Test
    void loaderSplitMatchesPythonShuffleBeforeCut() {
        CaseLoader loader = new CaseLoader(makeCases(10));

        CaseLoader.CaseLoaderSplit split = loader.split(0.5, 42);

        assertEquals(
                List.of(7, 3, 2, 8, 5),
                split.left().getCases().stream().map(caseData -> caseData.getInputs().get("id")).toList()
        );
        assertEquals(
                List.of(6, 9, 4, 0, 1),
                split.right().getCases().stream().map(caseData -> caseData.getInputs().get("id")).toList()
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

        assertTrue(loader.getCases().isEmpty());
        assertEquals(List.of(), loader.getCases());
    }

    @Test
    void shuffleCasesRejectsNullCases() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> CaseLoader.shuffleCases(null, 0));

        assertEquals("cases", exception.getMessage());
    }

    @Test
    void splitCasesHandlesEmptyLists() {
        CaseLoader.CaseListSplit split = CaseLoader.splitCases(List.of(), 0.5);

        assertEquals(List.of(), split.left());
        assertEquals(List.of(), split.right());
    }

    private static List<Case> makeCases(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new Case(Map.of("id", i), Map.of("answer", "ok"), "case_" + i))
                .toList();
    }
}
