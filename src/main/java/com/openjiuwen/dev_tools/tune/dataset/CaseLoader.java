/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.dataset;

import com.openjiuwen.dev_tools.tune.Case;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Case data loader for tuning.
 * <p>
 * Mirrors Python's {@code CaseLoader} in {@code openjiuwen.dev_tools.tune.dataset.case_loader}.
 * 
 * @since 0.1.7
 */
public class CaseLoader implements Iterable<Case> {
    private static final String CASE_ID_PREFIX = "case_";

    private final List<Case> cases;

    /**
     * Creates an empty CaseLoader.
     * 
     * @since 0.1.7
     */
    public CaseLoader() {
        this(List.of());
    }

    /**
     * Creates a CaseLoader with the given cases.
     * 
     * @param cases the list of cases to load
     * @since 0.1.7
     */
    public CaseLoader(List<Case> cases) {
        this.cases = new ArrayList<>(cases != null ? cases : List.of());
        assignCaseId();
    }

    /**
     * Returns the number of cases.
     * 
     * @return the number of cases
     * @since 0.1.7
     */
    public int size() {
        return cases.size();
    }

    /**
     * Returns the list of cases.
     * 
     * @return the list of cases
     * @since 0.1.7
     */
    public List<Case> getCases() {
        return Collections.unmodifiableList(cases);
    }

    /**
     * get_cases.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Case> get_cases() {
        return getCases();
    }

    /**
     * Shuffles the cases with the given random seed.
     * 
     * @param randomSeed the random seed
     * @since 0.1.7
     */
    public void shuffle(int randomSeed) {
        byte[] seedBytes = ByteBuffer.allocate(Integer.BYTES).putInt(randomSeed).array();
        Collections.shuffle(cases, new SecureRandom(seedBytes));
        assignCaseId();
    }

    /**
     * Shuffles the cases with a default random seed.
     * 
     * @since 0.1.7
     */
    public void shuffle() {
        shuffle(0);
    }

    /**
     * Splits the cases into two CaseLoaders with the given ratio.
     * 
     * @param ratio the split ratio (0.0 to 1.0)
     * @return a pair of CaseLoaders
     * @since 0.1.7
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

    /**
     * iterator.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<Case> iterator() {
        return cases.iterator();
    }

    /**
     * Returns the number of cases.
     * 
     * @return the number of cases
     * @since 0.1.7
     */
    public int length() {
        return cases.size();
    }

    /**
     * assignCaseId.
     * 
     * @since 0.1.7
     */
    private void assignCaseId() {
        for (int i = 0; i < cases.size(); i++) {
            cases.get(i).setCaseId(CASE_ID_PREFIX + i);
        }
    }

    /**
     * Result of a split operation.
     * 
     * @since 0.1.7
     */
    public static class SplitResult {
        private final CaseLoader first;
        private final CaseLoader second;

        /**
         * SplitResult.
         * 
         * @param first first
         * @param second second
         * @since 0.1.7
         */
        public SplitResult(CaseLoader first, CaseLoader second) {
            this.first = first;
            this.second = second;
        }

        /**
         * getFirst.
         * 
         * @return the result
         * @since 0.1.7
         */
        public CaseLoader getFirst() {
            return first;
        }

        /**
         * getSecond.
         * 
         * @return the result
         * @since 0.1.7
         */
        public CaseLoader getSecond() {
            return second;
        }
    }
}
