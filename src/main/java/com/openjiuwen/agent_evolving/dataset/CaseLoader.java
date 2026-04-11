/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

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
        new PythonRandom(seed).shuffle(shuffled);
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

    /**
     * Mirrors Python's random.Random(seed).shuffle for deterministic cross-language parity.
     */
    private static final class PythonRandom {
        private static final int N = 624;
        private static final int M = 397;
        private static final int MATRIX_A = 0x9908b0df;
        private static final int UPPER_MASK = 0x80000000;
        private static final int LOWER_MASK = 0x7fffffff;

        private final int[] mt = new int[N];
        private int index = N;

        private PythonRandom(int seed) {
            initByArray(seedWords(seed));
        }

        private static int[] seedWords(int seed) {
            long normalized = Math.abs((long) seed);
            return new int[]{(int) normalized};
        }

        private void initGenRand(int seed) {
            mt[0] = seed;
            for (int i = 1; i < N; i++) {
                long previous = Integer.toUnsignedLong(mt[i - 1]);
                long mixed = previous ^ (previous >>> 30);
                mt[i] = (int) ((1812433253L * mixed + i) & 0xffffffffL);
            }
            index = N;
        }

        private void initByArray(int[] initKey) {
            initGenRand(19650218);
            int i = 1;
            int j = 0;
            int keyLength = initKey.length;
            int loops = Math.max(N, keyLength);

            for (int count = 0; count < loops; count++) {
                long previous = Integer.toUnsignedLong(mt[i - 1]);
                long mixed = previous ^ (previous >>> 30);
                long current = Integer.toUnsignedLong(mt[i]);
                long key = Integer.toUnsignedLong(initKey[j]);
                mt[i] = (int) ((((current ^ (mixed * 1664525L)) + key + j)) & 0xffffffffL);
                i++;
                j++;
                if (i >= N) {
                    mt[0] = mt[N - 1];
                    i = 1;
                }
                if (j >= keyLength) {
                    j = 0;
                }
            }

            for (int count = 0; count < N - 1; count++) {
                long previous = Integer.toUnsignedLong(mt[i - 1]);
                long mixed = previous ^ (previous >>> 30);
                long current = Integer.toUnsignedLong(mt[i]);
                mt[i] = (int) ((((current ^ (mixed * 1566083941L)) - i)) & 0xffffffffL);
                i++;
                if (i >= N) {
                    mt[0] = mt[N - 1];
                    i = 1;
                }
            }
            mt[0] = 0x80000000;
        }

        private int nextInt32() {
            if (index >= N) {
                twist();
            }

            int y = mt[index++];
            y ^= y >>> 11;
            y ^= (y << 7) & 0x9d2c5680;
            y ^= (y << 15) & 0xefc60000;
            y ^= y >>> 18;
            return y;
        }

        private void twist() {
            for (int kk = 0; kk < N - M; kk++) {
                long y = (Integer.toUnsignedLong(mt[kk]) & UPPER_MASK)
                        | (Integer.toUnsignedLong(mt[kk + 1]) & LOWER_MASK);
                mt[kk] = mt[kk + M] ^ (int) (y >>> 1) ^ ((y & 1L) == 0L ? 0 : MATRIX_A);
            }
            for (int kk = N - M; kk < N - 1; kk++) {
                long y = (Integer.toUnsignedLong(mt[kk]) & UPPER_MASK)
                        | (Integer.toUnsignedLong(mt[kk + 1]) & LOWER_MASK);
                mt[kk] = mt[kk + (M - N)] ^ (int) (y >>> 1) ^ ((y & 1L) == 0L ? 0 : MATRIX_A);
            }

            long y = (Integer.toUnsignedLong(mt[N - 1]) & UPPER_MASK)
                    | (Integer.toUnsignedLong(mt[0]) & LOWER_MASK);
            mt[N - 1] = mt[M - 1] ^ (int) (y >>> 1) ^ ((y & 1L) == 0L ? 0 : MATRIX_A);
            index = 0;
        }

        private long getRandBits(int bits) {
            if (bits <= 0) {
                return 0L;
            }

            int wordCount = (bits + 31) / 32;
            long value = 0L;
            int accumulatedBits = 0;

            for (int i = 0; i < wordCount; i++) {
                long word = Integer.toUnsignedLong(nextInt32());
                int take = 32;
                if (i == wordCount - 1 && bits % 32 != 0) {
                    take = bits % 32;
                    word >>>= (32 - take);
                }
                value |= word << accumulatedBits;
                accumulatedBits += take;
            }
            return value;
        }

        private int randBelow(int boundExclusive) {
            int bits = 32 - Integer.numberOfLeadingZeros(boundExclusive);
            long candidate = getRandBits(bits);
            while (candidate >= boundExclusive) {
                candidate = getRandBits(bits);
            }
            return (int) candidate;
        }

        private <T> void shuffle(List<T> values) {
            for (int i = values.size() - 1; i > 0; i--) {
                int j = randBelow(i + 1);
                Collections.swap(values, i, j);
            }
        }
    }
}
