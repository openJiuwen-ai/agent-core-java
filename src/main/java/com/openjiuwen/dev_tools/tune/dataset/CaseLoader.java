  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.dev_tools.tune.dataset;

import com.openjiuwen.dev_tools.tune.Case;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Case data loader for tuning.
 *
 * <p>Mirrors Python's {@code CaseLoader} in {@code openjiuwen.dev_tools.tune.dataset.case_loader}.
 */
public class CaseLoader implements Iterable<Case> {

    private static final String CASE_ID_PREFIX = "case_";

    private final List<Case> cases;

    /**
     * Creates an empty CaseLoader.
     */
    public CaseLoader() {
        this(List.of());
    }

    /**
     * Creates a CaseLoader with the given cases.
     *
     * @param cases the list of cases to load
     */
    public CaseLoader(List<Case> cases) {
        this.cases = new ArrayList<>(cases != null ? cases : List.of());
        assignCaseId();
    }

    /**
     * Returns the number of cases.
     *
     * @return the number of cases
     */
    public int size() {
        return cases.size();
    }

    /**
     * Returns the list of cases.
     *
     * @return the list of cases
     */
    public List<Case> getCases() {
        return Collections.unmodifiableList(cases);
    }

    public List<Case> get_cases() {
        return getCases();
    }

    /**
     * Shuffles the cases with the given random seed.
     *
     * @param randomSeed the random seed
     */
    public void shuffle(int randomSeed) {
        Collections.shuffle(cases, new Random(randomSeed));
        assignCaseId();
    }

    /**
     * Shuffles the cases with a default random seed.
     */
    public void shuffle() {
        shuffle(0);
    }

    /**
     * Splits the cases into two CaseLoaders with the given ratio.
     *
     * @param ratio the split ratio (0.0 to 1.0)
     * @return a pair of CaseLoaders
     */
    public SplitResult split(double ratio) {
        if (ratio < 0.0 || ratio > 1.0) {
            ratio = 0.5;
        }
        
        List<Case> shuffled = new ArrayList<>(cases);
        Collections.shuffle(shuffled);
        
        int cut = (int) (cases.size() * ratio);
        List<Case> first = new ArrayList<>(shuffled.subList(0, cut));
        List<Case> second = new ArrayList<>(shuffled.subList(cut, shuffled.size()));
        
        return new SplitResult(new CaseLoader(first), new CaseLoader(second));
    }

    @Override
    public Iterator<Case> iterator() {
        return cases.iterator();
    }

    /**
     * Returns the number of cases.
     *
     * @return the number of cases
     */
    public int length() {
        return cases.size();
    }

    private void assignCaseId() {
        for (int i = 0; i < cases.size(); i++) {
            cases.get(i).setCaseId(CASE_ID_PREFIX + i);
        }
    }

    /**
     * Result of a split operation.
     */
    public static class SplitResult {
        private final CaseLoader first;
        private final CaseLoader second;

        public SplitResult(CaseLoader first, CaseLoader second) {
            this.first = first;
            this.second = second;
        }

        public CaseLoader getFirst() {
            return first;
        }

        public CaseLoader getSecond() {
            return second;
        }
    }
}