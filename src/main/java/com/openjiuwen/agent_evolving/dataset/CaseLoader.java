// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.dataset;

import java.util.*;

/**
 * Container for Case list with iteration and split support.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.dataset.case_loader.CaseLoader}.
 */
public class CaseLoader implements Iterable<Case> {

    private final List<Case> cases;

    /**
     * Initialize with case list.
     *
     * @param cases List of Cases to wrap
     */
    public CaseLoader(List<Case> cases) {
        this.cases = cases != null ? new ArrayList<>(cases) : new ArrayList<>();
    }

    /**
     * Return number of cases.
     *
     * @return Number of cases
     */
    public int size() {
        return cases.size();
    }

    /**
     * Check if empty.
     *
     * @return True if no cases
     */
    public boolean isEmpty() {
        return cases.isEmpty();
    }

    @Override
    public Iterator<Case> iterator() {
        return getCases().iterator();
    }

    /**
     * Get copy of cases list.
     *
     * @return Copy of internal case list
     */
    public List<Case> getCases() {
        return new ArrayList<>(cases);
    }

    /**
     * Split samples into two parts by ratio.
     *
     * @param ratio Split ratio in [0.0, 1.0]
     * @param seed  Random seed for reproducible shuffle
     * @return Tuple of (first_half, second_half) CaseLoaders
     * @throws IllegalArgumentException if ratio is not in [0.0, 1.0]
     */
    public CaseLoader[] split(double ratio, int seed) {
        if (ratio < 0.0 || ratio > 1.0) {
            throw new IllegalArgumentException("ratio must be in [0.0, 1.0], got " + ratio);
        }

        List<Case> shuffled = shuffleCases(cases, seed);
        int cut = (int) (shuffled.size() * ratio);
        return new CaseLoader[]{
                new CaseLoader(shuffled.subList(0, cut)),
                new CaseLoader(shuffled.subList(cut, shuffled.size()))
        };
    }

    /**
     * Shuffle Case list with optional seed.
     *
     * @param cases Cases to shuffle
     * @param seed  Random seed for reproducibility
     * @return New shuffled list (original unchanged)
     */
    public static List<Case> shuffleCases(List<Case> cases, int seed) {
        List<Case> shuffled = new ArrayList<>(cases != null ? cases : Collections.emptyList());
        Random rnd = new Random(seed);
        Collections.shuffle(shuffled, rnd);
        return shuffled;
    }

    /**
     * Split Case list by ratio.
     *
     * @param cases Cases to split
     * @param ratio Split ratio in [0.0, 1.0]
     * @return Array of [first_half, second_half]
     * @throws IllegalArgumentException if ratio is not in [0.0, 1.0]
     */
    public static List<Case>[] splitCases(List<Case> cases, double ratio) {
        if (ratio < 0.0 || ratio > 1.0) {
            throw new IllegalArgumentException("ratio must be in [0.0, 1.0], got " + ratio);
        }
        List<Case> safeCases = cases != null ? cases : Collections.emptyList();
        int cut = (int) (safeCases.size() * ratio);
        @SuppressWarnings("unchecked")
        List<Case>[] result = new List[2];
        result[0] = new ArrayList<>(safeCases.subList(0, cut));
        result[1] = new ArrayList<>(safeCases.subList(cut, safeCases.size()));
        return result;
    }
}
