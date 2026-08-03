/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.dataset;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Container for Case list with iteration and split support.
 *
 * <p>Mirrors Python's {@code CaseLoader}, {@code shuffle_cases}, and {@code split_cases} in
 * {@code openjiuwen/agent_evolving/dataset/case_loader.py}.</p>
 */
public class CaseLoader implements Iterable<Case> {

    private final List<Case> cases;

    public CaseLoader(List<Case> cases) {
        this.cases = Objects.requireNonNull(cases, "cases");
    }

    public int size() {
        return cases.size();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<Case> iterator() {
        return cases.iterator();
    }

    public List<Case> getCases() {
        return new ArrayList<>(cases);
    }

    public CaseLoaderSplit split(double ratio) {
        return split(ratio, 0);
    }

    public CaseLoaderSplit split(double ratio, long seed) {
        validateRatio(ratio);
        List<Case> shuffled = shuffleCases(cases, seed);
        int cut = (int) (shuffled.size() * ratio);
        return new CaseLoaderSplit(
                new CaseLoader(new ArrayList<>(shuffled.subList(0, cut))),
                new CaseLoader(new ArrayList<>(shuffled.subList(cut, shuffled.size())))
        );
    }

    public static List<Case> shuffleCases(List<Case> cases) {
        return shuffleCases(cases, 0);
    }

    public static List<Case> shuffleCases(List<Case> cases, long seed) {
        List<Case> shuffled = new ArrayList<>(Objects.requireNonNull(cases, "cases"));
        PythonRandom random = new PythonRandom(seed);
        for (int index = shuffled.size() - 1; index > 0; index--) {
            int swapIndex = random.randBelow(index + 1);
            Collections.swap(shuffled, index, swapIndex);
        }
        return shuffled;
    }

    public static CaseListSplit splitCases(List<Case> cases, double ratio) {
        validateRatio(ratio);
        List<Case> source = Objects.requireNonNull(cases, "cases");
        int cut = (int) (source.size() * ratio);
        return new CaseListSplit(
                new ArrayList<>(source.subList(0, cut)),
                new ArrayList<>(source.subList(cut, source.size()))
        );
    }

    private static void validateRatio(double ratio) {
        if (ratio < 0.0d || ratio > 1.0d) {
            throw new IllegalArgumentException("ratio must be in [0.0, 1.0], got " + ratio);
        }
    }

    public record CaseListSplit(List<Case> left, List<Case> right) {
    }

    public record CaseLoaderSplit(CaseLoader left, CaseLoader right) {
    }

    private static final class PythonRandom {

        private static final int N = 624;
        private static final int M = 397;
        private static final int MATRIX_A = 0x9908B0DF;
        private static final int UPPER_MASK = 0x80000000;
        private static final int LOWER_MASK = 0x7FFFFFFF;
        private static final long UINT_MASK = 0xFFFF_FFFFL;

        private final int[] state = new int[N];
        private int index = N;

        private PythonRandom(long seed) {
            initByArray(seedToKey(seed));
        }

        private int randBelow(int bound) {
            int bitLength = Integer.SIZE - Integer.numberOfLeadingZeros(bound);
            int value;
            do {
                value = getRandBits(bitLength);
            } while (value >= bound);
            return value;
        }

        private int getRandBits(int bits) {
            if (bits <= 0) {
                return 0;
            }
            return (int) (nextUInt32() >>> (32 - bits));
        }

        private long nextUInt32() {
            if (index >= N) {
                twist();
            }
            int y = state[index++];
            y ^= y >>> 11;
            y ^= (y << 7) & 0x9D2C5680;
            y ^= (y << 15) & 0xEFC60000;
            y ^= y >>> 18;
            return y & UINT_MASK;
        }

        private void initGenrand(long seed) {
            state[0] = (int) (seed & UINT_MASK);
            for (int i = 1; i < N; i++) {
                long previous = state[i - 1] & UINT_MASK;
                state[i] = (int) ((1812433253L * (previous ^ (previous >>> 30)) + i) & UINT_MASK);
            }
            index = N;
        }

        private void initByArray(int[] key) {
            initGenrand(19650218L);
            int i = 1;
            int j = 0;
            int k = Math.max(N, key.length);
            for (; k > 0; k--) {
                long previous = state[i - 1] & UINT_MASK;
                long mixed = (state[i] & UINT_MASK) ^ ((previous ^ (previous >>> 30)) * 1664525L);
                state[i] = (int) ((mixed + (key[j] & UINT_MASK) + j) & UINT_MASK);
                i++;
                j++;
                if (i >= N) {
                    state[0] = state[N - 1];
                    i = 1;
                }
                if (j >= key.length) {
                    j = 0;
                }
            }
            for (k = N - 1; k > 0; k--) {
                long previous = state[i - 1] & UINT_MASK;
                long mixed = (state[i] & UINT_MASK) ^ ((previous ^ (previous >>> 30)) * 1566083941L);
                state[i] = (int) ((mixed - i) & UINT_MASK);
                i++;
                if (i >= N) {
                    state[0] = state[N - 1];
                    i = 1;
                }
            }
            state[0] = UPPER_MASK;
        }

        private void twist() {
            int[] mag01 = {0, MATRIX_A};
            int kk = 0;
            for (; kk < N - M; kk++) {
                int y = (state[kk] & UPPER_MASK) | (state[kk + 1] & LOWER_MASK);
                state[kk] = state[kk + M] ^ (y >>> 1) ^ mag01[y & 1];
            }
            for (; kk < N - 1; kk++) {
                int y = (state[kk] & UPPER_MASK) | (state[kk + 1] & LOWER_MASK);
                state[kk] = state[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 1];
            }
            int y = (state[N - 1] & UPPER_MASK) | (state[0] & LOWER_MASK);
            state[N - 1] = state[M - 1] ^ (y >>> 1) ^ mag01[y & 1];
            index = 0;
        }

        private static int[] seedToKey(long seed) {
            BigInteger value = BigInteger.valueOf(seed);
            if (value.signum() < 0) {
                value = value.negate();
            }
            if (value.signum() == 0) {
                return new int[] {0};
            }
            List<Integer> words = new ArrayList<>();
            BigInteger mask = BigInteger.valueOf(UINT_MASK);
            while (value.signum() > 0) {
                words.add(value.and(mask).intValue());
                value = value.shiftRight(32);
            }
            int[] result = new int[words.size()];
            for (int i = 0; i < words.size(); i++) {
                result[i] = words.get(i);
            }
            return result;
        }
    }
}
