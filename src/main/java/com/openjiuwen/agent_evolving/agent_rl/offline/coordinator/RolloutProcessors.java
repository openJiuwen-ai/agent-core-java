/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rollout processors: classifier, validator, sampler utilities.
 * <p>
 * Mirrors Python's {@code RolloutClassifier}, {@code RolloutValidator},
 * {@code RolloutSampling}, and {@code ProcessorsRegistry} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.coordinator.processors}.
 */
public final class RolloutProcessors {

    private RolloutProcessors() {
    }

    /**
     * Classify rollout steps into positive and negative lists based on reward.
     *
     * @param mdpList rollouts with rewards
     * @return pair of positive and negative rollouts
     */
    public static RolloutPair classifyRollouts(List<RolloutWithReward> mdpList) {
        List<RolloutWithReward> posRollouts = new ArrayList<>();
        List<RolloutWithReward> negRollouts = new ArrayList<>();
        for (RolloutWithReward mdp : safeList(mdpList)) {
            if (mdp.getReward() != null && mdp.getReward() > 0.0d) {
                posRollouts.add(mdp);
            } else {
                negRollouts.add(mdp);
            }
        }
        return new RolloutPair(posRollouts, negRollouts);
    }

    /**
     * Compatibility wrapper returning `[positive, negative]`.
     *
     * @param mdpList rollouts with rewards
     * @return array of positive and negative rollout lists
     */
    @SuppressWarnings("unchecked")
    public static List<RolloutWithReward>[] defaultClassifyRollouts(List<RolloutWithReward> mdpList) {
        RolloutPair pair = classifyRollouts(mdpList);
        return new List[] {pair.positiveRollouts(), pair.negativeRollouts()};
    }

    /**
     * Stop when at least two positive samples exist and one achieves reward exactly 1.0,
     * unless negatives already outnumber positives.
     *
     * @param posRolloutList positive rollouts
     * @param negRolloutList negative rollouts
     * @return true if rollout collection should stop
     */
    public static boolean defaultValidateStop(
            List<RolloutWithReward> posRolloutList,
            List<RolloutWithReward> negRolloutList) {
        List<RolloutWithReward> positives = safeList(posRolloutList);
        List<RolloutWithReward> negatives = safeList(negRolloutList);
        if (negatives.size() > positives.size()) {
            return false;
        }
        if (positives.size() < 2) {
            return false;
        }
        for (RolloutWithReward rollout : positives) {
            if (rollout.getReward() != null && Double.compare(rollout.getReward(), 1.0d) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Balanced stopping rule: stop only when both positive and negative counts meet their targets.
     *
     * @param posRolloutList positive rollouts
     * @param negRolloutList negative rollouts
     * @param finalKeepPerPrompt target samples per prompt
     * @return true if both sides reached their target
     */
    public static boolean validateStopBalanced(
            List<RolloutWithReward> posRolloutList,
            List<RolloutWithReward> negRolloutList,
            int finalKeepPerPrompt) {
        int targetPos = finalKeepPerPrompt / 2;
        int targetNeg = finalKeepPerPrompt - targetPos;
        return safeList(posRolloutList).size() >= targetPos && safeList(negRolloutList).size() >= targetNeg;
    }

    /**
     * Balanced stopping rule with Python's default final_keep_per_prompt=8.
     *
     * @param posRolloutList positive rollouts
     * @param negRolloutList negative rollouts
     * @return true if both sides reached their default target
     */
    public static boolean validateStopBalanced(
            List<RolloutWithReward> posRolloutList,
            List<RolloutWithReward> negRolloutList) {
        return validateStopBalanced(posRolloutList, negRolloutList, 8);
    }

    /**
     * Return deep-copied dictionaries unchanged.
     *
     * @param posRolloutDict positive rollouts by UID
     * @param negRolloutDict negative rollouts by UID
     * @return copied positive and negative dictionaries
     */
    public static SamplingResult defaultSampling(
            Map<String, List<RolloutWithReward>> posRolloutDict,
            Map<String, List<RolloutWithReward>> negRolloutDict) {
        return new SamplingResult(copyRolloutMap(posRolloutDict), copyRolloutMap(negRolloutDict));
    }

    /**
     * Compatibility list sampler that combines the per-UID downsample result.
     *
     * @param posRolloutList positive rollouts
     * @param negRolloutList negative rollouts
     * @param maxSamples maximum samples to return
     * @return combined sampled list
     */
    public static List<RolloutWithReward> defaultSampling(
            List<RolloutWithReward> posRolloutList,
            List<RolloutWithReward> negRolloutList,
            int maxSamples) {
        RolloutPair pair = downsampleOneUid(posRolloutList, negRolloutList, maxSamples);
        List<RolloutWithReward> result = new ArrayList<>(pair.positiveRollouts());
        result.addAll(pair.negativeRollouts());
        return result;
    }

    /**
     * Downsample positive and negative samples for one UID toward a balanced target total.
     *
     * @param posList positive rollouts
     * @param negList negative rollouts
     * @param targetTotal target total samples
     * @return selected positive and negative samples
     */
    public static RolloutPair downsampleOneUid(
            List<RolloutWithReward> posList,
            List<RolloutWithReward> negList,
            int targetTotal) {
        List<RolloutWithReward> positives = safeList(posList);
        List<RolloutWithReward> negatives = safeList(negList);
        int targetPos = Math.min(targetTotal / 2, positives.size());
        int targetNeg = Math.min(targetTotal - targetPos, negatives.size());

        if (targetPos + targetNeg < targetTotal) {
            int remaining = targetTotal - (targetPos + targetNeg);
            int extraPosCap = positives.size() - targetPos;
            if (extraPosCap > 0) {
                int add = Math.min(extraPosCap, remaining);
                targetPos += add;
                remaining -= add;
            }
            if (remaining > 0) {
                int extraNegCap = negatives.size() - targetNeg;
                if (extraNegCap > 0) {
                    int add = Math.min(extraNegCap, remaining);
                    targetNeg += add;
                }
            }
        }

        return new RolloutPair(
                new ArrayList<>(positives.subList(0, targetPos)),
                new ArrayList<>(negatives.subList(0, targetNeg))
        );
    }

    /**
     * Balanced adaptive sampling for each UID.
     *
     * @param posRolloutDict positive rollouts by UID
     * @param negRolloutDict negative rollouts by UID
     * @param finalKeepPerPrompt target samples per prompt
     * @return sampled positive and negative dictionaries
     */
    public static SamplingResult samplingAda(
            Map<String, List<RolloutWithReward>> posRolloutDict,
            Map<String, List<RolloutWithReward>> negRolloutDict,
            int finalKeepPerPrompt) {
        Map<String, List<RolloutWithReward>> positives = safeMap(posRolloutDict);
        Map<String, List<RolloutWithReward>> negatives = safeMap(negRolloutDict);
        Map<String, List<RolloutWithReward>> outPos = new LinkedHashMap<>();
        Map<String, List<RolloutWithReward>> outNeg = new LinkedHashMap<>();

        Set<String> allUids = new LinkedHashSet<>();
        allUids.addAll(positives.keySet());
        allUids.addAll(negatives.keySet());
        for (String uid : allUids) {
            List<RolloutWithReward> posList = safeList(positives.get(uid));
            List<RolloutWithReward> negList = safeList(negatives.get(uid));
            if (posList.isEmpty() && negList.isEmpty()) {
                continue;
            }
            RolloutPair selected = downsampleOneUid(posList, negList, finalKeepPerPrompt);
            outPos.put(uid, selected.positiveRollouts());
            outNeg.put(uid, selected.negativeRollouts());
        }
        return new SamplingResult(outPos, outNeg);
    }

    /**
     * Balanced adaptive sampling with Python's default final_keep_per_prompt=8.
     *
     * @param posRolloutDict positive rollouts by UID
     * @param negRolloutDict negative rollouts by UID
     * @return sampled positive and negative dictionaries
     */
    public static SamplingResult samplingAda(
            Map<String, List<RolloutWithReward>> posRolloutDict,
            Map<String, List<RolloutWithReward>> negRolloutDict) {
        return samplingAda(posRolloutDict, negRolloutDict, 8);
    }

    /**
     * Registry for rollout classifiers, validators, and samplers.
     */
    public static final class ProcessorsRegistry {
        private final Map<String, ClassifierProcessor> classifiers = new LinkedHashMap<>();
        private final Map<String, ValidatorProcessor> validators = new LinkedHashMap<>();
        private final Map<String, SamplerProcessor> samplers = new LinkedHashMap<>();

        public ProcessorsRegistry() {
            loadPredefinedFunctions();
        }

        public ClassifierProcessor registerClassifier(String name, ClassifierProcessor processor) {
            classifiers.put(name, processor);
            return processor;
        }

        public ValidatorProcessor registerValidator(String name, ValidatorProcessor processor) {
            validators.put(name, processor);
            return processor;
        }

        public SamplerProcessor registerSampler(String name, SamplerProcessor processor) {
            samplers.put(name, processor);
            return processor;
        }

        public ClassifierProcessor getClassifier(String name) {
            ClassifierProcessor processor = classifiers.get(name);
            if (processor == null) {
                throw missingProcessor("classifier", name, classifiers.keySet());
            }
            return processor;
        }

        public ValidatorProcessor getValidator(String name) {
            ValidatorProcessor processor = validators.get(name);
            if (processor == null) {
                throw missingProcessor("validator", name, validators.keySet());
            }
            return processor;
        }

        public SamplerProcessor getSampler(String name) {
            SamplerProcessor processor = samplers.get(name);
            if (processor == null) {
                throw missingProcessor("sampler", name, samplers.keySet());
            }
            return processor;
        }

        public Map<String, ClassifierProcessor> classifiers() {
            return Map.copyOf(classifiers);
        }

        public Map<String, ValidatorProcessor> validators() {
            return Map.copyOf(validators);
        }

        public Map<String, SamplerProcessor> samplers() {
            return Map.copyOf(samplers);
        }

        private void loadPredefinedFunctions() {
            registerClassifier("default_classify_rollouts", RolloutProcessors::classifyRollouts);
            registerValidator("default_validate_stop", RolloutProcessors::defaultValidateStop);
            registerValidator("validate_stop_balanced",
                    (posRollouts, negRollouts) -> RolloutProcessors.validateStopBalanced(posRollouts, negRollouts));
            registerSampler("default_sampling", RolloutProcessors::defaultSampling);
            registerSampler("sampling_ada", RolloutProcessors::samplingAda);
        }

        private static IllegalArgumentException missingProcessor(String type, String name, Set<String> available) {
            return new IllegalArgumentException(
                    "Processor not found: type=" + type + ", name=" + name + ", available=" + available);
        }
    }

    @FunctionalInterface
    public interface ClassifierProcessor {
        RolloutPair apply(List<RolloutWithReward> rollouts);
    }

    @FunctionalInterface
    public interface ValidatorProcessor {
        boolean apply(List<RolloutWithReward> positiveRollouts, List<RolloutWithReward> negativeRollouts);
    }

    @FunctionalInterface
    public interface SamplerProcessor {
        SamplingResult apply(
                Map<String, List<RolloutWithReward>> positiveRollouts,
                Map<String, List<RolloutWithReward>> negativeRollouts);
    }

    public record RolloutPair(
            List<RolloutWithReward> positiveRollouts,
            List<RolloutWithReward> negativeRollouts) {
        public RolloutPair {
            positiveRollouts = List.copyOf(safeList(positiveRollouts));
            negativeRollouts = List.copyOf(safeList(negativeRollouts));
        }
    }

    public record SamplingResult(
            Map<String, List<RolloutWithReward>> positiveRollouts,
            Map<String, List<RolloutWithReward>> negativeRollouts) {
        public SamplingResult {
            positiveRollouts = immutableMapOfLists(positiveRollouts);
            negativeRollouts = immutableMapOfLists(negativeRollouts);
        }
    }

    private static Map<String, List<RolloutWithReward>> copyRolloutMap(
            Map<String, List<RolloutWithReward>> input) {
        Map<String, List<RolloutWithReward>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<RolloutWithReward>> entry : safeMap(input).entrySet()) {
            copy.put(entry.getKey(), copyRolloutList(entry.getValue()));
        }
        return copy;
    }

    private static List<RolloutWithReward> copyRolloutList(List<RolloutWithReward> input) {
        List<RolloutWithReward> copy = new ArrayList<>();
        for (RolloutWithReward rollout : safeList(input)) {
            copy.add(copyRollout(rollout));
        }
        return copy;
    }

    private static RolloutWithReward copyRollout(RolloutWithReward source) {
        RolloutWithReward copy = new RolloutWithReward();
        copy.setTurnId(source.getTurnId());
        copy.setTaskId(source.getTaskId());
        copy.setRolloutId(source.getRolloutId());
        copy.setInputPromptIds(source.getInputPromptIds());
        copy.setOutputResponseIds(source.getOutputResponseIds());
        copy.setReward(source.getReward());
        copy.setNTurns(source.getNTurns());
        copy.setLossMask(source.getLossMask());
        return copy;
    }

    private static Map<String, List<RolloutWithReward>> immutableMapOfLists(
            Map<String, List<RolloutWithReward>> input) {
        Map<String, List<RolloutWithReward>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<RolloutWithReward>> entry : safeMap(input).entrySet()) {
            result.put(entry.getKey(), List.copyOf(safeList(entry.getValue())));
        }
        return Map.copyOf(result);
    }

    private static List<RolloutWithReward> safeList(List<RolloutWithReward> list) {
        return list == null ? List.of() : list;
    }

    private static Map<String, List<RolloutWithReward>> safeMap(
            Map<String, List<RolloutWithReward>> map) {
        return map == null ? Map.of() : map;
    }
}
