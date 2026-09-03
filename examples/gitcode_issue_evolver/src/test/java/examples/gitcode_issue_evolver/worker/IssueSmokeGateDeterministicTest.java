/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.worker;

import examples.gitcode_issue_evolver.AutoEvolvingConfig;
import examples.gitcode_issue_evolver.infrastructure.CIGateResult;
import examples.gitcode_issue_evolver.infrastructure.VerificationFailureType;
import examples.gitcode_issue_evolver.job.EvolutionJobState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic contract checks for the Issue JiuwenTestJava smoke Gate. */
public final class IssueSmokeGateDeterministicTest {
    private static final String SMOKE_SELECTOR =
            "com.openjiuwen.test.cases.workflow_drawable.WorkflowDraw001Test";

    private IssueSmokeGateDeterministicTest() {
    }

    /**
     * Run deterministic smoke Gate checks without invoking Maven.
     *
     * @param args ignored command-line arguments
     * @throws Exception when temporary test fixtures cannot be prepared
     */
    public static void main(String[] args) throws Exception {
        testFixedSmokeCommands();
        testFailureClassification();
        testFingerprintAndStateContract();
        System.out.println("IssueSmokeGateDeterministicTest: PASS");
    }

    private static void testFixedSmokeCommands() throws Exception {
        Fixture fixture = fixture();
        AtomicReference<List<List<String>>> captured = new AtomicReference<>();
        IssueSmokeTestRunner runner = new IssueSmokeTestRunner(fixture.config(),
                (workspace, commands, timeout) -> {
                    captured.set(commands);
                    return result(true, "smoke passed", VerificationFailureType.NONE);
                });
        IssueSmokeTestRunner.Result result = runner.run(fixture.source());
        require(result.status() == IssueSmokeTestRunner.Status.PASSED,
                "fixed smoke selection did not pass");
        List<List<String>> commands = captured.get();
        require(commands.size() == 2
                        && commands.get(0).contains("-Dmaven.test.skip=true")
                        && commands.get(0).contains("install"),
                "source installation widened into source tests");
        List<String> smoke = commands.get(1);
        require(smoke.contains("-Dtest=" + SMOKE_SELECTOR)
                        && smoke.contains("-Dagent-core-java.version=0.1.14.post1")
                        && !smoke.contains("-Dgroups=smoke")
                        && !smoke.contains("verify"),
                "smoke command was not bound to the exact approved selector");
    }

    private static void testFailureClassification() throws Exception {
        Fixture fixture = fixture();
        IssueSmokeTestRunner deterministic = new IssueSmokeTestRunner(fixture.config(),
                (workspace, commands, timeout) -> result(false, "assertion failed",
                        VerificationFailureType.CHECK_FAILED));
        require(deterministic.run(fixture.source()).status() == IssueSmokeTestRunner.Status.FAILED,
                "smoke assertion failure was not assigned to Agent repair");

        IssueSmokeTestRunner dependency = new IssueSmokeTestRunner(fixture.config(),
                (workspace, commands, timeout) -> result(false,
                        "Could not resolve dependencies", VerificationFailureType.CHECK_FAILED));
        require(dependency.run(fixture.source()).status() == IssueSmokeTestRunner.Status.TRANSIENT,
                "smoke dependency failure was not assigned to infrastructure retry");
    }

    private static void testFingerprintAndStateContract() throws Exception {
        Fixture fixture = fixture();
        IssueSmokeTestRunner first = new IssueSmokeTestRunner(fixture.config(),
                IssueSmokeGateDeterministicTest::passed);
        Files.writeString(fixture.tests().resolve(
                        "src/test/java/com/openjiuwen/test/Smoke.java"),
                "class Smoke { int value; }", StandardCharsets.UTF_8);
        IssueSmokeTestRunner second = new IssueSmokeTestRunner(fixture.config(),
                IssueSmokeGateDeterministicTest::passed);
        require(!first.fingerprint().equals(second.fingerprint()),
                "smoke repository changes did not invalidate the Gate fingerprint");
        require(EvolutionJobState.VERIFYING.canTransitionTo(EvolutionJobState.SMOKE_TESTING)
                        && EvolutionJobState.SMOKE_TESTING.canTransitionTo(EvolutionJobState.COMMITTED),
                "smoke Gate was not represented in the durable state machine");
    }

    private static CIGateResult passed(Path workspace, List<List<String>> commands,
                                       Duration timeout) {
        return result(true, "smoke passed", VerificationFailureType.NONE);
    }

    private static CIGateResult result(boolean isPassed, String output,
                                       VerificationFailureType failureType) {
        return CIGateResult.builder()
                .isPassed(isPassed)
                .gateOutputs(List.of(output))
                .errors(isPassed ? "" : output)
                .failureType(failureType)
                .build();
    }

    private static Fixture fixture() throws Exception {
        Path root = Files.createTempDirectory("issue-smoke-gate-");
        Path source = root.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.openjiuwen</groupId>
                  <artifactId>agent-core-java</artifactId>
                  <version>0.1.14.post1</version>
                </project>
                """, StandardCharsets.UTF_8);
        Path tests = root.resolve("jiuwen-test-java");
        Path testSource = tests.resolve("src/test/java/com/openjiuwen/test/Smoke.java");
        Files.createDirectories(testSource.getParent());
        Files.writeString(tests.resolve("pom.xml"), "<project/>", StandardCharsets.UTF_8);
        Files.writeString(testSource, "class Smoke {}", StandardCharsets.UTF_8);
        AutoEvolvingConfig config = AutoEvolvingConfig.builder()
                .smokeTestEnabled(true)
                .smokeTestRepository(tests)
                .smokeTestSelectors(List.of(SMOKE_SELECTOR))
                .smokeTestTimeoutMinutes(30)
                .build();
        return new Fixture(source, tests, config);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Fixture(Path source, Path tests, AutoEvolvingConfig config) {
    }
}
