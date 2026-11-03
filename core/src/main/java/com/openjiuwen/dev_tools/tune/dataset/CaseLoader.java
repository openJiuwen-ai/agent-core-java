/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.dataset;

import com.openjiuwen.dev_tools.tune.Case;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Case data loader for tuning.
 *
 * <p>Mirrors Python's {@code CaseLoader} in
 * {@code openjiuwen/dev_tools/tune/dataset/case_loader.py}.</p>
 */
public class CaseLoader implements Iterable<Case> {

    private static final Logger LOGGER = Logger.getLogger(CaseLoader.class.getName());
    private static final String CASE_ID_PREFIX = "case_";

    private final List<Case> cases;

    public CaseLoader() {
        this(List.of());
    }

    public CaseLoader(List<Case> cases) {
        this.cases = new ArrayList<>(cases == null ? List.of() : cases);
        assignCaseId();
    }

    public int size() {
        return cases.size();
    }

    public int length() {
        return cases.size();
    }

    public List<Case> getCases() {
        return Collections.unmodifiableList(cases);
    }

    public List<Case> get_cases() {
        return getCases();
    }

    public void shuffle() {
        shuffle(0);
    }

    public void shuffle(int randomSeed) {
        shuffleInPlace(cases, randomSeed);
        assignCaseId();
    }

    public SplitResult split() {
        return split(0.5d);
    }

    public SplitResult split(double ratio) {
        double actualRatio = ratio;
        if (actualRatio < 0.0d || actualRatio > 1.0d) {
            LOGGER.severe("ratio must be between 0.0 and 1.0, got " + ratio + ", using default 0.5");
            actualRatio = 0.5d;
        }
        List<Case> shuffledCases = deepCopyCases(cases);
        Collections.shuffle(shuffledCases);
        int cut = (int) (cases.size() * actualRatio);
        return new SplitResult(
                new CaseLoader(new ArrayList<>(shuffledCases.subList(0, cut))),
                new CaseLoader(new ArrayList<>(shuffledCases.subList(cut, shuffledCases.size())))
        );
    }

    @Override
    public Iterator<Case> iterator() {
        return cases.iterator();
    }

    private void assignCaseId() {
        for (int index = 0; index < cases.size(); index++) {
            cases.get(index).setCaseId(CASE_ID_PREFIX + index);
        }
    }

    private static List<Case> deepCopyCases(List<Case> source) {
        List<Case> copied = new ArrayList<>(source.size());
        for (Case item : source) {
            copied.add(copyCase(item));
        }
        return copied;
    }

    private static Case copyCase(Case source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> inputs = new LinkedHashMap<>(source.getInputs());
        Map<String, Object> label = new LinkedHashMap<>(source.getLabel());
        return new Case(inputs, label, source.getTools(), source.getCaseId());
    }

    private static void shuffleInPlace(List<Case> target, long seed) {
        PythonRandom random = new PythonRandom(seed);
        for (int index = target.size() - 1; index > 0; index--) {
            int swapIndex = random.randBelow(index + 1);
            Collections.swap(target, index, swapIndex);
        }
    }

    /**
     * Result of a Python tuple-like split operation.
     */
    public record SplitResult(CaseLoader first, CaseLoader second) {
        public CaseLoader getFirst() {
            return first;
        }

        public CaseLoader getSecond() {
            return second;
        }
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
