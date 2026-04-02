package com.openjiuwen.agent_evolving.dataset;

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
    void splitCasesRespectsRatioAndKeepsOriginalUntouched() {
        CaseLoader loader = new CaseLoader(makeCases(10));

        CaseLoader[] split = loader.split(0.5, 0);

        assertEquals(5, split[0].size());
        assertEquals(5, split[1].size());
        assertEquals(10, loader.size());
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

    private static List<Case> makeCases(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new Case(Map.of("id", i), Map.of("answer", "ok"), "case_" + i))
                .toList();
    }
}
