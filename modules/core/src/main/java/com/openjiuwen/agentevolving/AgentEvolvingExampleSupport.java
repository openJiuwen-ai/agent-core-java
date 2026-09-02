/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving;

import com.openjiuwen.agentevolving.dataset.Case;
import com.openjiuwen.agentevolving.dataset.CaseLoader;
import com.openjiuwen.agentevolving.evaluator.metrics.ExactMatchMetric;

import java.util.List;
import java.util.Map;

/**
 * Shared support for the Java RL / agent-evolving example baselines.
 * <p>
 * The current Java baseline exposes dataset/evaluator/trainer building
 * blocks, but not the Python online/offline RL launcher stack.
 * </p>
 * 
 * @since 0.1.7
 */
public final class AgentEvolvingExampleSupport {
    /**
     * AgentEvolvingExampleSupport.
     * 
     * @since 0.1.7
     */
    private AgentEvolvingExampleSupport() {
    }

    /**
     * calculatorCaseLoader.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static CaseLoader calculatorCaseLoader() {
        return new CaseLoader(List.of(Case.builder().caseId("calc-1")
                .inputs(Map.of("query", "2+2", "ground_truth", "4")).label(Map.of("answer", "4")).build()));
    }

    /**
     * nl2sqlCaseLoader.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static CaseLoader nl2sqlCaseLoader() {
        return new CaseLoader(List.of(Case.builder().caseId("sql-1")
                .inputs(Map.of("query", "Database: database/concert_singer\n\nQuestion: count all singers",
                        "ground_truth", "SELECT count(*) FROM singer"))
                .label(Map.of("answer", "SELECT count(*) FROM singer")).build()));
    }

    /**
     * exactMatchMetric.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static ExactMatchMetric exactMatchMetric() {
        return new ExactMatchMetric(true);
    }

    /**
     * describeCurrentJavaBaseline.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static String describeCurrentJavaBaseline() {
        return "Java baseline exposes dataset/evaluator/trainer building blocks; "
                + "Python online/offline RL launcher stack is not fully ported.";
    }
}
