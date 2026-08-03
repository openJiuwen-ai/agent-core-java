package com.openjiuwen.agent_evolving.evaluator;

import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.CaseLoader;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.evaluator.metrics.Metric;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code BaseEvaluator}, {@code DefaultEvaluator}, {@code _agg_score},
 * and {@code MetricEvaluator} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator.py}.
 *
 * <p>Mirrors Python's test coverage in
 * {@code tests/unit_tests/agent_evolving/evaluator/test_evaluator.py}.</p>
 */
class EvaluatorTest {

    @Test
    void batchEvaluateRejectsLengthMismatchWithPythonStatusCode() {
        RecordingEvaluator evaluator = new RecordingEvaluator();

        assertThatThrownBy(() -> evaluator.batchEvaluate(List.of(caseValue("q1")), List.of(), 1))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOLCHAIN_EVALUATOR_EXECUTION_ERROR))
                .hasMessageContaining("length of cases: 1 dose not equal with length of predicts: 0 ");
    }

    @Test
    @Disabled
    void batchEvaluateAcceptsCaseLoaderAndPreservesInputOrder() {
        RecordingEvaluator evaluator = new RecordingEvaluator();
        Case first = caseValue("q1");
        Case second = caseValue("q2");

        List<EvaluatedCase> result = evaluator.batchEvaluate(
                new CaseLoader(List.of(first, second)),
                List.of(answer("a1"), answer("a2")),
                2
        );

        assertThat(result).extracting(EvaluatedCase::getReason).containsExactly("q1", "q2");
        assertThat(evaluator.seenQuestions).containsExactly("q1", "q2");
    }

    @Test
    void defaultEvaluatorParsesPassingJsonResultFromModel() {
        List<List<BaseMessage>> capturedMessages = new ArrayList<>();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            capturedMessages.add(messages);
            return CompletableFuture.completedFuture(new AssistantMessage("""
                    ```json
                    {"result": " TRUE ", "reason": "same answer"}
                    ```
                    """));
        });
        DefaultEvaluator evaluator = new DefaultEvaluator(model, "prefer exact factual consistency");

        EvaluatedCase result = evaluator.evaluate(caseValue("capital?"), answer("Paris"));

        assertThat(result.getScore()).isEqualTo(1.0d);
        assertThat(result.getReason()).isEqualTo("same answer");
        assertThat(capturedMessages).hasSize(1);
        assertThat(capturedMessages.get(0).get(0).getContentAsString())
                .contains("prefer exact factual consistency")
                .contains("capital?")
                .contains("Paris");
    }

    @Test
    void defaultEvaluatorRetriesWhenInitialResponseCannotBeParsed() {
        AtomicInteger calls = new AtomicInteger();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            if (calls.getAndIncrement() == 0) {
                return CompletableFuture.completedFuture(new AssistantMessage("not json"));
            }
            return CompletableFuture.completedFuture(new AssistantMessage("""
                    ```json
                    {"result": false, "reason": "mismatch"}
                    ```
                    """));
        });
        DefaultEvaluator evaluator = new DefaultEvaluator(model, "");

        EvaluatedCase result = evaluator.evaluate(caseValue("question"), answer("wrong"));

        assertThat(calls).hasValue(2);
        assertThat(result.getScore()).isZero();
        assertThat(result.getReason()).isEqualTo("mismatch");
    }

    @Test
    void defaultEvaluatorReturnsModelErrorReasonWhenInvokeFails() {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            CompletableFuture<AssistantMessage> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("llm down"));
            return failed;
        });
        DefaultEvaluator evaluator = new DefaultEvaluator(model, "");

        EvaluatedCase result = evaluator.evaluate(caseValue("question"), answer("answer"));

        assertThat(result.getScore()).isZero();
        assertThat(result.getReason()).isEqualTo("Failed to evaluate case due to model error");
    }

    @Test
    void metricEvaluatorAggregatesScalarAndDictMetricOutputs() {
        Metric scalarMetric = metric("scalar", (prediction, label, kwargs) -> "0.5");
        Map<String, Object> dictResult = new LinkedHashMap<>();
        dictResult.put("dict_score", 1);
        dictResult.put("bad_score", "not-a-number");
        Metric dictMetric = metric("dict", (prediction, label, kwargs) -> dictResult);
        MetricEvaluator evaluator = new MetricEvaluator(List.of(scalarMetric, dictMetric), "mean");

        EvaluatedCase result = evaluator.evaluate(caseValue("question"), answer("answer"));

        assertThat(result.getScore()).isEqualTo(0.5d);
        assertThat(result.getPerMetric()).containsEntry("scalar", 0.5d)
                .containsEntry("dict_score", 1.0d)
                .containsEntry("bad_score", 0.0d);
    }

    @Test
    void metricEvaluatorSupportsFirstAndFallbackMeanAggregation() {
        assertThat(EvaluatorScoreAggregator.aggregateScore(List.of(0.25d, 1.0d), "first"))
                .isEqualTo(0.25d);
        assertThat(EvaluatorScoreAggregator.aggregateScore(List.of(0.25d, 0.75d), "unknown"))
                .isEqualTo(0.5d);
        assertThat(EvaluatorScoreAggregator.aggregateScore(List.of(), "mean")).isZero();
    }

    @ParameterizedTest
    @MethodSource("aggregateCases")
    void aggregateScoreMatchesPythonCases(List<Double> values, String aggregate, double expected) {
        assertThat(EvaluatorScoreAggregator.aggregateScore(values, aggregate)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "0.0,0.0",
            "1.0,1.0",
            "0.5,0.5",
            "0.999999,0.999999",
            "0.000001,0.000001",
            "999.0,999.0",
            "-5.0,-5.0",
            "0.25,0.25"
    })
    void metricEvaluatorKeepsAssignedBoundaryScores(double metricValue, double expected) {
        MetricEvaluator evaluator = new MetricEvaluator(metric("boundary", (prediction, label, kwargs) -> metricValue));

        EvaluatedCase result = evaluator.evaluate(caseValue("question"), answer("answer"));

        assertThat(result.getScore()).isEqualTo(expected);
        assertThat(result.getPerMetric()).containsEntry("boundary", expected);
    }

    @ParameterizedTest
    @CsvSource({
            "true,1.0",
            "false,0.0",
            " TRUE ,1.0",
            " false ,0.0",
            "yes,0.0",
            "no,0.0"
    })
    void defaultEvaluatorScoresStringResultVariants(String resultValue, double expectedScore) {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("""
                        ```json
                        {"result": "%s", "reason": "checked"}
                        ```
                        """.formatted(resultValue))));
        DefaultEvaluator evaluator = new DefaultEvaluator(model, "");

        EvaluatedCase result = evaluator.evaluate(caseValue("question"), answer("answer"));

        assertThat(result.getScore()).isCloseTo(expectedScore, within(1.0e-12d));
        assertThat(result.getReason()).isEqualTo("checked");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 21, 100})
    void batchEvaluateRejectsInvalidParallelValues(int numParallel) {
        RecordingEvaluator evaluator = new RecordingEvaluator();

        assertThatThrownBy(() -> evaluator.batchEvaluate(
                List.of(caseValue("q1")),
                List.of(answer("a1")),
                numParallel))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR));
    }

    @ParameterizedTest
    @MethodSource("safeConversionCases")
    void metricEvaluatorConvertsSafeValues(Object metricValue, double expected) {
        MetricEvaluator evaluator = new MetricEvaluator(metric("convert", (prediction, label, kwargs) -> metricValue));

        EvaluatedCase result = evaluator.evaluate(caseValue("question"), answer("answer"));

        assertThat(result.getScore()).isEqualTo(expected);
        assertThat(result.getPerMetric()).containsEntry("convert", expected);
    }

    @ParameterizedTest
    @MethodSource("predictionShapes")
    void metricEvaluatorAcceptsPredictionShapes(Map<String, Object> predict) {
        MetricEvaluator evaluator = new MetricEvaluator(metric("shape", (prediction, label, kwargs) -> {
            assertThat(kwargs).containsKeys("question", "case");
            assertThat(prediction).isSameAs(predict);
            return 0.8d;
        }));

        EvaluatedCase result = evaluator.evaluate(caseValue("question"), predict);

        assertThat(result.getScore()).isEqualTo(0.8d);
    }

    @ParameterizedTest
    @MethodSource("metricAggregationCases")
    void metricEvaluatorAggregatesMetricCombinations(List<Metric> metrics, String aggregate, double expectedScore) {
        MetricEvaluator evaluator = new MetricEvaluator(metrics, aggregate);

        EvaluatedCase result = evaluator.evaluate(caseValue("question"), answer("answer"));

        assertThat(result.getScore()).isCloseTo(expectedScore, within(1.0e-12d));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5})
    void batchEvaluateProcessesAllCases(int size) {
        RecordingEvaluator evaluator = new RecordingEvaluator();
        List<Case> cases = IntStream.range(0, size)
                .mapToObj(index -> caseValue("q" + index))
                .toList();
        List<Map<String, Object>> predicts = IntStream.range(0, size)
                .mapToObj(index -> answer("a" + index))
                .toList();

        List<EvaluatedCase> result = evaluator.batchEvaluate(cases, predicts, Math.min(size, 2));

        assertThat(result).hasSize(size);
        assertThat(result).extracting(EvaluatedCase::getReason).containsExactlyElementsOf(
                IntStream.range(0, size).mapToObj(index -> "q" + index).toList());
        assertThat(evaluator.seenQuestions).containsExactlyInAnyOrderElementsOf(
                IntStream.range(0, size).mapToObj(index -> "q" + index).toList());
    }

    @ParameterizedTest
    @MethodSource("dictMetricCases")
    void metricEvaluatorFlattensDictMetricOutputs(Map<String, Object> metricOutput, double expectedScore) {
        MetricEvaluator evaluator = new MetricEvaluator(metric("dict", (prediction, label, kwargs) -> metricOutput));

        EvaluatedCase result = evaluator.evaluate(caseValue("question"), answer("answer"));

        assertThat(result.getScore()).isCloseTo(expectedScore, within(1.0e-12d));
        assertThat(result.getPerMetric()).containsKeys(metricOutput.keySet().toArray(String[]::new));
    }

    private static Stream<Arguments> aggregateCases() {
        return Stream.of(
                Arguments.of(List.of(0.5d), "mean", 0.5d),
                Arguments.of(List.of(0.5d, 1.0d, 0.0d), "mean", 0.5d),
                Arguments.of(List.<Double>of(), "mean", 0.0d),
                Arguments.of(List.of(0.2d, 0.8d, 0.5d), "first", 0.2d),
                Arguments.of(List.<Double>of(), "first", 0.0d),
                Arguments.of(List.of(1.0d, 2.0d, 3.0d), "mean", 2.0d),
                Arguments.of(List.of(1.0d, 2.0d, 3.0d), "invalid", 2.0d)
        );
    }

    private static Stream<Arguments> safeConversionCases() {
        return Stream.of(
                Arguments.of("0.75", 0.75d),
                Arguments.of(0.75d, 0.75d),
                Arguments.of(1, 1.0d),
                Arguments.of(0L, 0.0d),
                Arguments.of(true, 1.0d),
                Arguments.of(false, 0.0d),
                Arguments.of("invalid_string_not_a_number", 0.0d),
                Arguments.of("", 0.0d)
        );
    }

    private static Stream<Arguments> predictionShapes() {
        return Stream.of(
                Arguments.of(answer("pred")),
                Arguments.of(new LinkedHashMap<String, Object>()),
                Arguments.of(new LinkedHashMap<>(Map.of("output", "special chars !@#$%^&*()"))),
                Arguments.of(new LinkedHashMap<>(Map.of("output", "a".repeat(128)))),
                Arguments.of(new LinkedHashMap<>(Map.of("nested", Map.of("deep", true)))),
                Arguments.of(new LinkedHashMap<>(Map.of("number", 42)))
        );
    }

    private static Stream<Arguments> metricAggregationCases() {
        return Stream.of(
                Arguments.of(List.of(metric("m1", (prediction, label, kwargs) -> 0.6d),
                        metric("m2", (prediction, label, kwargs) -> 0.8d)), "mean", 0.7d),
                Arguments.of(List.of(metric("m1", (prediction, label, kwargs) -> 0.9d)), "first", 0.9d),
                Arguments.of(List.<Metric>of(), "mean", 0.0d),
                Arguments.of(List.of(metric("m1", (prediction, label, kwargs) -> 0.25d),
                        metric("m2", (prediction, label, kwargs) -> 0.75d)), "invalid", 0.5d),
                Arguments.of(List.of(metric("m1", (prediction, label, kwargs) -> "0.2"),
                        metric("m2", (prediction, label, kwargs) -> "0.4")), "mean", 0.3d),
                Arguments.of(List.of(metric("m1", (prediction, label, kwargs) -> 1.0d),
                        metric("m2", (prediction, label, kwargs) -> "bad")), "mean", 0.5d)
        );
    }

    private static Stream<Arguments> dictMetricCases() {
        return Stream.of(
                Arguments.of(linkedMap("score_a", 1.0d, "score_b", 0.5d), 0.75d),
                Arguments.of(linkedMap("only", 0.25d), 0.25d),
                Arguments.of(linkedMap("string", "0.75"), 0.75d),
                Arguments.of(linkedMap("bad", "not-a-number"), 0.0d),
                Arguments.of(linkedMap("truthy", true, "falsey", false), 0.5d)
        );
    }

    private static Case caseValue(String query) {
        return new Case(new LinkedHashMap<>(Map.of("query", query)), new LinkedHashMap<>(Map.of("answer", "expected")));
    }

    private static Map<String, Object> answer(String value) {
        return new LinkedHashMap<>(Map.of("answer", value));
    }

    private static Map<String, Object> linkedMap(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> data = linkedMap(firstKey, firstValue);
        data.put(secondKey, secondValue);
        return data;
    }

    private static Map<String, Object> linkedMap(String firstKey, Object firstValue) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(firstKey, firstValue);
        return data;
    }

    private static Metric metric(String name, MetricComputer computer) {
        return new Metric() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Object compute(Object prediction, Object label, Map<String, Object> kwargs) {
                return computer.compute(prediction, label, kwargs);
            }
        };
    }

    @FunctionalInterface
    private interface MetricComputer {
        Object compute(Object prediction, Object label, Map<String, Object> kwargs);
    }

    private static final class RecordingEvaluator extends BaseEvaluator {
        private final List<String> seenQuestions = new ArrayList<>();

        @Override
        public EvaluatedCase evaluate(Case caseValue, Map<String, Object> predict) {
            String question = String.valueOf(caseValue.getInputs().get("query"));
            synchronized (seenQuestions) {
                seenQuestions.add(question);
            }
            return new EvaluatedCase(caseValue, predict, 1.0d, question, null);
        }
    }
}
