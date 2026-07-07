/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.SessionStateAccess;
import com.openjiuwen.core.workflow.condition.Condition;
import com.openjiuwen.core.workflow.BranchRouter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code tests/unit_tests/core/component/test_branch_comp.py}.
 */
public class BranchComponentPythonParityTest {

    @Test
    void subWorkflowWithBranchRoutesRepeatedInvocationsToSelectedTarget() {
        TestSession session = sessionWith("start.d", List.of(1, 2, 3));
        BranchRouter router = new BranchRouter();
        router.addBranch("len(${start.d}) > 2", "a");
        router.addBranch("len(${start.d}) < 2", "b");
        router.setSession(session);

        List<List<String>> routed = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            routed.add(router.route());
        }

        assertThat(routed).containsOnly(List.of("a"));
    }

    @Test
    void addBranchRejectsInvalidConditionAndTargets() {
        BranchComponent branch = new BranchComponent();

        assertBaseError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                () -> branch.addBranch((String) null, "a", ""));
        assertBaseError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                () -> branch.addBranch("sss", "", ""));
        assertBaseError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                () -> branch.addBranch("sss", (String) null, ""));
        assertBaseError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                () -> branch.addBranch("sss", Arrays.asList("", "xxx"), ""));
        assertBaseError(StatusCode.COMPONENT_BRANCH_PARAM_INVALID,
                () -> branch.addBranch("sss", Arrays.asList("xxx", null), ""));
    }

    @Test
    void expressionIsEmptyMatchesPythonCases() {
        assertThat(routeWithInput("is_empty(${start.input})", null)).containsExactly("print_inputs");
        assertThat(routeWithInput("is_empty(${start.input})", List.of())).containsExactly("print_inputs");
        assertThat(routeWithInput("is_empty(${start.input})", "")).containsExactly("print_inputs");
        assertThat(routeWithInput("is_empty(${start.input})", Map.of())).containsExactly("print_inputs");
        assertBaseError(StatusCode.EXPRESSION_EVAL_ERROR,
                () -> routeWithInput("is_empty(${start.input})", 0));
        assertBaseError(StatusCode.EXPRESSION_EVAL_ERROR,
                () -> routeWithInput("is_not_empty(${start.input})", 1.2D));
        assertThat(routeWithInput("is_empty(${start.input}[0])", Arrays.asList(null, "y")))
                .containsExactly("print_inputs");
        assertThat(routeWithInput("is_empty(${start.input}['x'])", linkedMap("x", null)))
                .containsExactly("print_inputs");
        assertThat(routeWithInput("is_empty(${start.input}['x'][0])", linkedMap("x", Arrays.asList((Object) null))))
                .containsExactly("print_inputs");
    }

    @Test
    void expressionIsNotEmptyMatchesPythonCases() {
        assertThat(routeWithInput("is_not_empty(${start.input})", "x")).containsExactly("print_inputs");
        assertThat(routeWithInput("is_not_empty(${start.input})", Map.of("a", "a")))
                .containsExactly("print_inputs");
        assertThat(routeWithInput("is_not_empty(${start.input})", List.of("a")))
                .containsExactly("print_inputs");
        assertThat(routeWithInput("is_not_empty(${start.input})", List.of(1, 2)))
                .containsExactly("print_inputs");
        assertBaseError(StatusCode.COMPONENT_BRANCH_EXECUTION_ERROR,
                () -> routeWithInput("is_not_empty(${start.input})", null));
        assertBaseError(StatusCode.EXPRESSION_EVAL_ERROR,
                () -> routeWithInput("is_not_empty(${start.input})", 1.2D));
        assertThat(routeWithInput("is_not_empty(${start.input}[0])", List.of("x", "y")))
                .containsExactly("print_inputs");
        assertThat(routeWithInput("is_not_empty(${start.input}['x'])", Map.of("x", "x")))
                .containsExactly("print_inputs");
        assertThat(routeWithInput("is_not_empty(${start.input}['x'][0])", Map.of("x", List.of("x"))))
                .containsExactly("print_inputs");
    }

    @Test
    void expressionLengthMatchesPythonCases() {
        assertBaseError(StatusCode.EXPRESSION_EVAL_ERROR,
                () -> routeWithInput("length(${start.input}) == 0", 0));
        assertThat(routeWithInput("length(${start.input}) == 0", Map.of())).containsExactly("print_inputs");
        assertThat(routeWithInput("length(${start.input}) == 0", List.of())).containsExactly("print_inputs");
        assertThat(routeWithInput("length(${start.input}) == 0", "")).containsExactly("print_inputs");
        assertThat(routeWithInput("length(${start.input}) == 0", new Object[0])).containsExactly("print_inputs");
    }

    @Test
    void expressionArithmeticAndNestedPathMatchesPythonCases() {
        TestSession session = new TestSession();
        session.state.values.put("start.input1", "test");
        session.state.values.put("start.input2", true);
        session.state.values.put("start.input3", List.of(11, "arr", Map.of("k", "v"), List.of(1, 2, 3)));
        session.state.values.put("start.input4", Map.of("k1", 12.2D, "k3", Map.of("k", "v")));

        BranchComponent branch = new BranchComponent();
        branch.addBranch("( length(${start.input1}) < ${start.input4.k1} ) && "
                + "( ${start.input3[0]} % 2 == 1 ) && "
                + "( ${start.input3[2].k} == ${start.input4.k3.k} ) && "
                + "( ${start.input2} && len(${start.input3[3]}) > 2 )", List.of("print_inputs", "add_ten"));
        branch.invoke(Map.of(), session, null);

        assertThat(branch.router().route()).containsExactly("print_inputs", "add_ten");
    }

    @Test
    void sdkConditionSubclassIsAcceptedLikePythonCondition() {
        TestSession session = new TestSession();
        BranchComponent branch = new BranchComponent();
        branch.addBranch(new Condition() {
            @Override
            public Object doInvoke(Object inputs, BaseSession session) {
                return true;
            }
        }, List.of("next"));
        branch.invoke(Map.of(), session, null);

        assertThat(branch.router().route()).containsExactly("next");
    }

    private static List<String> routeWithInput(String expression, Object value) {
        TestSession session = sessionWith("start.input", value);
        BranchComponent branch = new BranchComponent();
        branch.addBranch(expression, List.of("print_inputs"));
        branch.invoke(Map.of(), session, null);
        return branch.router().route();
    }

    private static TestSession sessionWith(String key, Object value) {
        TestSession session = new TestSession();
        session.state.values.put(key, value);
        return session;
    }

    private static Map<String, Object> linkedMap(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    private static void assertBaseError(StatusCode statusCode, Runnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(statusCode);
    }

    public static final class TestSession extends BaseSession {
        private final TestState state = new TestState();

        @Override
        public SessionStateAccess state() {
            return state;
        }

        @Override
        public String sessionId() {
            return "branch-component-python-parity";
        }
    }

    public static final class TestState implements SessionStateAccess {
        private final Map<String, Object> values = new LinkedHashMap<>();

        @Override
        public Object get(Object key) {
            return values.get(String.valueOf(key));
        }

        public Object get(String key) {
            return values.get(key);
        }

        @Override
        public void update(Map<String, Object> data) {
            values.putAll(data);
        }

        @Override
        public Object getGlobal(Object path) {
            return values.get(String.valueOf(path));
        }

        public Object getGlobal(String path) {
            return values.get(path);
        }
    }
}
