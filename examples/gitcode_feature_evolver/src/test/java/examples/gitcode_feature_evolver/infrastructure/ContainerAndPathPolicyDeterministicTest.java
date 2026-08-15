/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.infrastructure;

import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.FeatureWorkflowMode;
import examples.gitcode_feature_evolver.agent.FeaturePathPolicy;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureStage;
import examples.gitcode_issue_evolver.TriggerMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** Deterministic fixed-command container and permanent-path-policy checks. */
public final class ContainerAndPathPolicyDeterministicTest {
    private ContainerAndPathPolicyDeterministicTest() {
    }

    /** Run all local container and path checks. */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("feature-container-");
        FeatureEvolvingConfig config = config(root, TriggerMode.POLLING, "");
        writePom(root.resolve("worktree"), "0.1.14.post1");
        require(config.readinessErrors().isEmpty(),
                "polling-only configuration unexpectedly required a Webhook secret");
        FeatureEvolvingConfig webhook = config(root, TriggerMode.WEBHOOK, "");
        require(webhook.readinessErrors().stream().anyMatch(
                        error -> error.contains("webhookSecret")),
                "Webhook mode did not require its independent HMAC secret");
        Deque<RootlessContainerGateRunner.Execution> results = new ArrayDeque<>();
        List<List<String>> commands = new ArrayList<>();
        RootlessContainerGateRunner runner = new RootlessContainerGateRunner(config,
                (command, directory, timeout) -> {
                    commands.add(List.copyOf(command));
                    return results.removeFirst();
                });

        results.add(new RootlessContainerGateRunner.Execution(0, "true\n", false));
        results.add(new RootlessContainerGateRunner.Execution(0, "", false));
        require(runner.readinessErrors().isEmpty(), "valid rootless runtime was rejected");

        testFeatureProfiles(root, runner, results, commands);
        testSystemTestContainer(root, runner, results, commands);
        testMavenVersionResolution(root);
        testDependencyPrefetch(config, root);

        testTargetRepositoryFetch(config);
        testDeploymentCacheProbe();
        testRetrySnapshotRestoration(config, root);
        testPathPolicy();
        System.out.println("ContainerAndPathPolicyDeterministicTest: PASS");
    }

    private static void testDependencyPrefetch(FeatureEvolvingConfig config, Path root)
            throws Exception {
        Files.writeString(config.containerMavenCache().resolve("shared-marker"), "trusted\n");
        List<List<String>> commands = new ArrayList<>();
        DependencyPrefetcher prefetcher = new DependencyPrefetcher(config,
                (command, directory, timeout) -> {
                    commands.add(List.copyOf(command));
                    return new DependencyPrefetcher.Execution(0, "prefetch passed");
                });
        FeatureJob job = featureJob();
        DependencyPrefetcher.Result result = prefetcher.prefetchFeature(
                job, root.resolve("worktree"), List.of("src/main/java/Feature.java"));
        require(result.passed() && commands.size() == 1,
                "credential-free dependency prefetch did not run");
        List<String> command = commands.get(0);
        require(command.contains("--network=slirp4netns")
                        && command.contains("--http-proxy=false")
                        && command.contains("--pull=never")
                        && command.stream().anyMatch(value -> value.startsWith(
                        "--tmpfs=/native-tmp:"))
                        && command.contains(
                        "--tmpfs=/native-tmp:rw,exec,nosuid,nodev,size=64m")
                        && command.contains("-DskipTests")
                        && command.contains("dependency:go-offline")
                        && !command.contains("test-compile"),
                "dependency prefetch did not use the fixed no-test network profile");
        require(command.stream().noneMatch(value -> value.toLowerCase(Locale.ROOT)
                        .contains("token"))
                        && command.stream().noneMatch(value -> value.contains(
                        config.containerMavenCache().toString() + ":/m2")),
                "prefetch container received credentials or the shared cache mount");
        require(command.stream().anyMatch(value -> value.startsWith(
                        "--env=JAVA_TOOL_OPTIONS=-Duser.home=/tmp")
                        && value.contains("-Djansi.tmpdir=/native-tmp")
                        && value.contains("-Dorg.sqlite.tmpdir=/native-tmp"))
                        && command.stream().noneMatch(value -> value.contains(
                        "-Djava.io.tmpdir=/native-tmp")),
                "prefetch exposed general Maven temporary files on an executable mount");
        require(Files.readString(config.containerMavenCache().resolve("shared-marker"))
                        .equals("trusted\n")
                        && Files.isRegularFile(prefetcher.cacheFor(job).resolve("shared-marker")),
                "prefetch modified or failed to clone the shared cache");
        testSystemTestPrefetch(root, prefetcher, job, commands);
        testPrefetchFailureClassification(config, root);
        DependencyPrefetcher.Result policy = prefetcher.prefetchFeature(
                job, root.resolve("worktree"), List.of("pom.xml"));
        require(policy.status() == DependencyPrefetcher.Status.POLICY_VIOLATION
                        && commands.size() == 2,
                "modified Maven build contract reached the networked prefetch container");
    }

    private static void testPrefetchFailureClassification(
            FeatureEvolvingConfig config, Path root) {
        DependencyPrefetcher compilation = new DependencyPrefetcher(config,
                (command, directory, timeout) -> new DependencyPrefetcher.Execution(
                        1, "COMPILATION ERROR from maven-compiler-plugin"));
        DependencyPrefetcher.Result invalid = compilation.prefetchFeature(
                featureJob(), root.resolve("worktree"), List.of());
        require(invalid.status() == DependencyPrefetcher.Status.BUILD_CONTRACT_INVALID,
                "deterministic prefetch build failure was treated as transient infrastructure");

        DependencyPrefetcher interrupted = new DependencyPrefetcher(config,
                (command, directory, timeout) -> new DependencyPrefetcher.Execution(
                        130, "Dependency prefetch was interrupted"));
        DependencyPrefetcher.Result transientFailure = interrupted.prefetchFeature(
                featureJob(), root.resolve("worktree"), List.of());
        require(transientFailure.status() == DependencyPrefetcher.Status.TRANSIENT,
                "interrupted prefetch was treated as a deterministic build failure");

        DependencyPrefetcher network = new DependencyPrefetcher(config,
                (command, directory, timeout) -> new DependencyPrefetcher.Execution(
                        1, "Could not transfer artifact: Read timed out"));
        DependencyPrefetcher.Result networkFailure = network.prefetchFeature(
                featureJob(), root.resolve("worktree"), List.of());
        require(networkFailure.status() == DependencyPrefetcher.Status.TRANSIENT,
                "repository timeout was treated as a deterministic build failure");
    }

    private static void testSystemTestPrefetch(
            Path root, DependencyPrefetcher prefetcher, FeatureJob job,
            List<List<String>> commands) {
        DependencyPrefetcher.Result systemTest = prefetcher.prefetchSystemTest(
                job, root.resolve("worktree"), root.resolve("system-tests"),
                List.of("src/test/java/example/FeatureSystemTest.java"));
        require(systemTest.passed() && commands.size() == 2,
                "system-test dependency prefetch did not run");
        List<String> systemCommand = commands.get(1);
        String systemScript = systemCommand.get(systemCommand.size() - 1);
        require(systemCommand.stream().anyMatch(value -> value.endsWith(":/source:ro,Z"))
                        && systemCommand.contains(
                        "--tmpfs=/source/target:rw,noexec,nosuid,nodev,size=2048m")
                        && systemCommand.contains("--env=FEATURE_SOURCE_VERSION=0.1.14.post1")
                        && systemCommand.contains("--env=SUREFIRE_VERSION=3.2.3")
                        && systemCommand.contains(
                        "--env=JUNIT_PLATFORM_LAUNCHER_VERSION=1.10.1")
                        && systemCommand.stream().anyMatch(value -> value.endsWith(
                        ":/tests:ro,Z"))
                        && systemCommand.contains(
                        "--tmpfs=/tests/target:rw,noexec,nosuid,nodev,size=2048m")
                        && !systemScript.contains("help:evaluate")
                        && !systemScript.contains("test-compile")
                        && systemScript.contains("dependency:get")
                        && systemScript.contains(
                        "org.apache.maven.surefire:surefire-junit-platform:")
                        && systemScript.contains(
                        "org.junit.platform:junit-platform-launcher:")
                        && systemScript.contains(
                        "[feature-evolver:prefetch-postcondition-missing]")
                        && systemScript.contains("test -s /m2/"),
                "system-test prefetch did not preserve the frozen-source contract");
    }

    private static FeatureJob featureJob() {
        FeatureJob.IssueReference issue = new FeatureJob.IssueReference(
                7L, "Prefetch", "https://gitcode.com/example/issues/7");
        FeatureJob.Identity identity = new FeatureJob.Identity(
                "12345678-1234-1234-1234-123456789012", "example/repo", issue,
                "feature-evolving/issue-7-prefetch", "features/7-prefetch");
        FeatureJob.Progress progress = new FeatureJob.Progress(
                FeatureStage.DEPENDENCY_PREFETCH, FeatureStage.IMPLEMENT_GREEN,
                FeatureWorkflowMode.UNATTENDED, 0, 0);
        FeatureJob.RecordMetadata metadata = new FeatureJob.RecordMetadata(
                0L, "", Instant.now().toEpochMilli(), Instant.now().toEpochMilli());
        return new FeatureJob(identity, progress, new FeatureJob.PullRequests(
                FeatureJob.PullRequest.empty(), FeatureJob.PullRequest.empty()),
                FeatureJob.Recovery.empty(), new FeatureJob.Lease("", 0L), metadata);
    }

    private static void testRetrySnapshotRestoration(
            FeatureEvolvingConfig config, Path root) throws Exception {
        Path repository = root.resolve("retry-snapshot");
        Files.createDirectories(repository);
        runGit(repository, "init");
        Path plan = repository.resolve("features/7-feature/plan.md");
        Path trackedTest = repository.resolve("src/test/java/example/ExistingTest.java");
        Files.createDirectories(plan.getParent());
        Files.createDirectories(trackedTest.getParent());
        Files.writeString(plan, "approved plan\n");
        Files.writeString(trackedTest, "class ExistingTest {}\n");
        runGit(repository, "add", ".");
        runGit(repository, "-c", "user.name=Feature Test", "-c",
                "user.email=feature-test@example.com", "commit", "-m", "baseline");

        Files.writeString(plan, "untrusted retry plan\n");
        Files.writeString(trackedTest, "class ExistingTest { int retry; }\n");
        Path newTest = repository.resolve("src/test/java/example/NewFeatureTest.java");
        Files.writeString(newTest, "class NewFeatureTest {}\n");
        Path outside = repository.resolve("src/main/java/example/Feature.java");
        Files.createDirectories(outside.getParent());
        Files.writeString(outside, "class Feature {}\n");

        FeatureGitPublisher publisher = new FeatureGitPublisher(config);
        List<String> scopes = List.of("features/7-feature/", "src/test/java/example/");
        FeatureGitPublisher.RestoreResult rejected = publisher.restoreRetrySnapshot(
                repository, scopes);
        require(!rejected.success() && !rejected.retryable() && Files.exists(newTest),
                "retry restoration discarded an out-of-scope forensic Worktree");

        Files.delete(outside);
        FeatureGitPublisher.RestoreResult restored = publisher.restoreRetrySnapshot(
                repository, scopes);
        require(restored.success() && restored.restoredFiles().size() == 3,
                "bounded retry snapshot was not restored");
        require(Files.readString(plan).equals("approved plan\n")
                        && Files.readString(trackedTest).equals("class ExistingTest {}\n")
                        && !Files.exists(newTest),
                "retry restoration did not return to the committed stage snapshot");
        require(runGit(repository, "status", "--porcelain").isBlank(),
                "retry restoration left a dirty Worktree");

        Files.writeString(plan, "policy violation\n");
        Files.writeString(outside, "class Feature {}\n");
        FeatureGitPublisher.RestoreResult policyRestored =
                publisher.restorePolicySnapshot(repository, false);
        require(policyRestored.success() && !Files.exists(outside)
                        && Files.readString(plan).equals("approved plan\n")
                        && runGit(repository, "status", "--porcelain").isBlank(),
                "policy restoration did not reset the complete owned Worktree snapshot");
    }

    private static String runGit(Path repository, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).directory(repository.toFile())
                .redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).strip();
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException("Deterministic Git command failed: " + output);
        }
        return output;
    }

    private static void testFeatureProfiles(
            Path root, RootlessContainerGateRunner runner,
            Deque<RootlessContainerGateRunner.Execution> results,
            List<List<String>> commands) {
        results.add(new RootlessContainerGateRunner.Execution(1,
                "Tests run: 1, Failures: 1, Errors: 0, Skipped: 0", false));
        ContainerGateResult red = runner.run(RootlessContainerGateRunner.Profile.RED,
                root.resolve("worktree"), List.of("example.FeatureTest"));
        require(red.expectedRed(), "trustworthy test assertion failure was not accepted as RED");
        List<String> redCommand = commands.get(2);
        testContainerCommandPolicy(redCommand);
        require(redCommand.contains("-Dtest=example.FeatureTest")
                        && redCommand.contains("-Dsurefire.failIfNoSpecifiedTests=true"),
                "RED did not use the controller-supplied exact selector");

        results.add(new RootlessContainerGateRunner.Execution(1,
                "Could not resolve dependencies; cannot access central in offline mode", false));
        ContainerGateResult dependency = runner.run(
                RootlessContainerGateRunner.Profile.BASELINE, root.resolve("worktree"));
        require(dependency.outcome() == ContainerGateResult.Outcome.DEPENDENCY_MISSING,
                "offline dependency miss was not routed to prefetch");
        List<String> baselineCommand = commands.get(3);
        require(baselineCommand.stream().anyMatch(value -> value.contains(
                        "ConstrainConfigValidationTest")) && !baselineCommand.contains("verify"),
                "baseline profile was not fixed to the sandbox-compatible probe");

        results.add(new RootlessContainerGateRunner.Execution(1,
                "pthread_create failed (EAGAIN): unable to create native thread", false));
        ContainerGateResult resource = runner.run(RootlessContainerGateRunner.Profile.TARGETED,
                root.resolve("worktree"), List.of("example.FeatureTest"));
        require(resource.outcome() == ContainerGateResult.Outcome.INFRASTRUCTURE_FAILED,
                "native-thread exhaustion was misclassified as a test failure");
        expectInvalidSelector(() -> runner.run(RootlessContainerGateRunner.Profile.TARGETED,
                root.resolve("worktree"), List.of("FeatureTest#method")));
    }

    private static void testSystemTestContainer(
            Path root, RootlessContainerGateRunner runner,
            Deque<RootlessContainerGateRunner.Execution> results,
            List<List<String>> commands) throws Exception {
        Path tests = root.resolve("system-tests");
        writeSystemTestPom(tests);
        results.add(new RootlessContainerGateRunner.Execution(0, "selected tests passed", false));
        ContainerGateResult selected = runner.runSystemTest(
                RootlessContainerGateRunner.SystemTestProfile.SELECTED,
                root.resolve("worktree"), tests,
                List.of("com.openjiuwen.test.FeatureSystemTest"));
        require(selected.passed(), "post-merge selected-test profile failed");
        require(Files.isDirectory(root.resolve("worktree/target"))
                        && !Files.isSymbolicLink(root.resolve("worktree/target"))
                        && Files.getPosixFilePermissions(root.resolve("worktree/target"))
                        .contains(PosixFilePermission.OTHERS_WRITE),
                "Controller did not prepare the isolated target mountpoint");
        List<String> command = commands.get(commands.size() - 1);
        require(command.contains("--network=none"),
                "system-test container unexpectedly had network access");
        require(command.stream().anyMatch(value -> value.endsWith(":/m2:O")),
                "system-test Maven cache did not use a disposable overlay");
        require(command.stream().anyMatch(value -> value.contains("dst=/source/.git"))
                        && command.stream().anyMatch(value -> value.contains("dst=/tests/.git")),
                "system-test Git control files were not masked");
        require(command.stream().anyMatch(value -> value.endsWith(":/source:ro,Z"))
                        && command.stream().anyMatch(value -> value.endsWith(":/tests:ro,Z"))
                        && command.contains(
                        "--tmpfs=/source/target:rw,noexec,nosuid,nodev,size=2048m")
                        && command.contains(
                        "--tmpfs=/tests/target:rw,noexec,nosuid,nodev,size=2048m"),
                "frozen source was not mounted read-only with isolated build output");
        String selectedPomMount = command.stream().filter(value -> value.endsWith(
                ":/tests/pom.xml:ro,Z")).findFirst().orElseThrow();
        Path selectedPom = Path.of(selectedPomMount.substring("--volume=".length(),
                selectedPomMount.indexOf(":/tests/pom.xml:ro,Z")));
        String selectedPomText = Files.readString(selectedPom);
        require(selectedPomText.contains(
                        "com/openjiuwen/test/FeatureSystemTest.java")
                        && !selectedPomText.contains("**/*.java")
                        && Files.readString(tests.resolve("pom.xml")).contains("**/*.java"),
                "system-test Gate did not isolate approved compiler roots");
        require(command.contains("--env=FEATURE_SOURCE_VERSION=0.1.14.post1"),
                "system-test command did not use the Controller-resolved source version");
        String script = command.get(command.size() - 1);
        require(script.contains("-Dmaven.test.skip=true")
                        && script.contains("-Dtest=\"com.openjiuwen.test.FeatureSystemTest\" test"),
                "system-test command did not install source without tests then run exact selectors");
        require(!script.contains(" verify") && !script.contains("help:evaluate")
                        && script.contains("[feature-evolver:step=source-install]")
                        && script.contains("[feature-evolver:step=system-test]"),
                "system-test command widened tests or hid its fixed phase boundaries");

        testSystemTestFailureClassification(root, tests, runner, results);
    }

    private static void testSystemTestFailureClassification(
            Path root, Path tests, RootlessContainerGateRunner runner,
            Deque<RootlessContainerGateRunner.Execution> results) {
        results.add(new RootlessContainerGateRunner.Execution(1,
                "[feature-evolver:step=source-install]\n"
                        + "Plugin org.apache.maven.plugins:maven-install-plugin has not been "
                        + "downloaded from it before", false));
        ContainerGateResult dependency = runner.runSystemTest(
                RootlessContainerGateRunner.SystemTestProfile.SELECTED,
                root.resolve("worktree"), tests,
                List.of("com.openjiuwen.test.FeatureSystemTest"));
        require(dependency.outcome() == ContainerGateResult.Outcome.DEPENDENCY_MISSING,
                "offline system-test plugin miss was not routed to prefetch");

        results.add(new RootlessContainerGateRunner.Execution(1,
                "[feature-evolver:step=source-install]\n"
                        + "[feature-evolver:step=system-test]\nCompilation error", false));
        ContainerGateResult compilation = runner.runSystemTest(
                RootlessContainerGateRunner.SystemTestProfile.SELECTED,
                root.resolve("worktree"), tests,
                List.of("com.openjiuwen.test.FeatureSystemTest"));
        require(compilation.outcome()
                        == ContainerGateResult.Outcome.TEST_COMPILATION_FAILED,
                "system-test compilation failure was not classified for Agent repair");

        results.add(new RootlessContainerGateRunner.Execution(1,
                "Picked up JAVA_TOOL_OPTIONS: bounded", false));
        ContainerGateResult unobservable = runner.runSystemTest(
                RootlessContainerGateRunner.SystemTestProfile.SELECTED,
                root.resolve("worktree"), tests,
                List.of("com.openjiuwen.test.FeatureSystemTest"));
        require(unobservable.outcome()
                        == ContainerGateResult.Outcome.UNOBSERVABLE_FAILURE,
                "evidence-free failure was incorrectly assigned to Agent repair");

        results.add(new RootlessContainerGateRunner.Execution(130,
                "Rootless container process interrupted", false));
        ContainerGateResult interrupted = runner.runSystemTest(
                RootlessContainerGateRunner.SystemTestProfile.SELECTED,
                root.resolve("worktree"), tests,
                List.of("com.openjiuwen.test.FeatureSystemTest"));
        require(interrupted.outcome()
                        == ContainerGateResult.Outcome.INFRASTRUCTURE_FAILED,
                "interrupted system-test container was treated as a deterministic failure");
    }

    private static void testMavenVersionResolution(Path root) throws Exception {
        Path source = root.resolve("version-source");
        Files.createDirectories(source);
        writePom(source, "${revision}");
        Path pom = source.resolve("pom.xml");
        String content = Files.readString(pom).replace("</project>",
                "<properties><revision>0.2.0-rc1</revision></properties></project>");
        Files.writeString(pom, content);
        require("0.2.0-rc1".equals(MavenProjectVersionResolver.resolve(source)),
                "Maven project version property was not resolved");

        Files.writeString(pom, "<!DOCTYPE project [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>"
                + "<project><version>&xxe;</version></project>");
        try {
            MavenProjectVersionResolver.resolve(source);
            throw new IllegalStateException("unsafe Maven XML was accepted");
        } catch (MavenProjectVersionResolver.ProjectVersionException expected) {
            require(expected.getMessage().contains("cannot be parsed safely"),
                    "unsafe Maven XML failed for an unexpected reason");
        }
    }

    private static void writePom(Path worktree, String version) throws Exception {
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion>"
                + "<groupId>example</groupId><artifactId>feature</artifactId><version>"
                + version + "</version></project>");
    }

    private static void writeSystemTestPom(Path worktree) throws Exception {
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion>"
                + "<groupId>example</groupId><artifactId>system-tests</artifactId>"
                + "<version>1</version><properties><surefire.version>3.2.3</surefire.version>"
                + "<junit.version>5.10.1</junit.version></properties><dependencies><dependency>"
                + "<groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId>"
                + "<version>${junit.version}</version></dependency></dependencies>"
                + "<build><plugins><plugin>"
                + "<groupId>org.apache.maven.plugins</groupId>"
                + "<artifactId>maven-compiler-plugin</artifactId>"
                + "<configuration><release>17</release>"
                + "<testIncludes><testInclude>**/*.java</testInclude></testIncludes>"
                + "</configuration>"
                + "</plugin><plugin><groupId>org.apache.maven.plugins</groupId>"
                + "<artifactId>maven-surefire-plugin</artifactId>"
                + "<version>${surefire.version}</version>"
                + "</plugin></plugins></build></project>");
    }

    private static void testTargetRepositoryFetch(FeatureEvolvingConfig source) {
        FeatureEvolvingConfig config = FeatureEvolvingConfig.builder()
                .localRepository(source.localRepository())
                .worktreeRoot(source.worktreeRoot())
                .targetRepository("antonjli/agent-core-java-bot")
                .publishRepository("antonjli/agent-core-java-bot")
                .baseBranch("730")
                .build();
        FeatureWorktreeManager manager = new FeatureWorktreeManager(config);
        List<String> command = manager.targetFetchCommand();
        require(command.contains("https://gitcode.com/antonjli/agent-core-java-bot.git"),
                "feature baseline did not use the configured target repository");
        require(!command.contains("origin"),
                "feature baseline remained coupled to the deployment repository origin");
        require(command.contains("+refs/heads/730:refs/remotes/feature-target/730"),
                "feature baseline did not use its isolated target ref");
        require("refs/remotes/feature-target/730".equals(manager.targetBaseReference()),
                "feature Worktree base reference was not deterministic");

        FeatureEvolvingConfig systemTestConfig = FeatureEvolvingConfig.builder()
                .localRepository(source.localRepository())
                .worktreeRoot(source.worktreeRoot())
                .systemTestRepository("openJiuwen/jiuwen-test")
                .systemTestPublishRepository("antonjli/jiuwen-test-bot")
                .systemTestBaseBranch("agent_core_java")
                .build();
        SystemTestWorktreeManager systemTests = new SystemTestWorktreeManager(systemTestConfig);
        List<String> testFetch = systemTests.targetFetchCommand();
        require(testFetch.contains("https://gitcode.com/openJiuwen/jiuwen-test.git")
                        && !testFetch.contains("https://gitcode.com/antonjli/jiuwen-test-bot.git"),
                "system-test baseline did not fetch from the target repository");
        require(systemTestConfig.systemTestCoordinates().publishRepository().equals(
                        "antonjli/jiuwen-test-bot"),
                "system-test publication fork was not preserved");
    }

    private static void testDeploymentCacheProbe() throws Exception {
        Path helper = Path.of("examples/gitcode_feature_evolver/deploy/sbin/"
                + "run-feature-evolver-test");
        String script = Files.readString(helper);
        String wrapper = Files.readString(Path.of(
                "examples/gitcode_feature_evolver/deploy/libexec/podman"));
        String probe = "maven_cache_probe_arguments";
        int declaration = script.indexOf(probe);
        int onlineRun = script.indexOf(probe, declaration + probe.length());
        int offlineRun = script.indexOf(probe, onlineRun + probe.length());
        require(declaration >= 0 && onlineRun > declaration && offlineRun > onlineRun,
                "deployment gate did not run the Maven cache probe online and offline");
        require(script.contains("ConstrainConfigValidationTest")
                        && script.contains("-Dsurefire.failIfNoSpecifiedTests=true")
                        && script.contains("-Duser.home=/tmp")
                        && script.contains("maven_source_install_arguments")
                        && script.contains("--tmpfs=/workspace/target:")
                        && script.contains("$offline_worktree:/workspace:ro,Z"),
                "deployment gate cache probe was not fixed to a real deterministic JUnit test");
        require(wrapper.contains("JAVA_TOOL_OPTIONS=-Duser.home=/tmp"),
                "root-owned Podman launcher did not confine the JVM home to tmpfs");
        require(Files.isRegularFile(Path.of("src/test/java/com/openjiuwen/core/application/"
                        + "schema/ConstrainConfigValidationTest.java")),
                "deployment gate cache probe test is unavailable");
    }

    private static void testContainerCommandPolicy(List<String> redCommand) {
        require(redCommand.contains("--network=none"), "container networking was not disabled");
        require(redCommand.contains("--read-only=true"), "container root filesystem was not read-only");
        require(redCommand.contains("--http-proxy=false"), "host proxy settings could enter the container");
        require(redCommand.contains("--cap-drop=ALL"), "container capabilities were not dropped");
        require(redCommand.contains("--tmpfs=/tmp:rw,noexec,nosuid,nodev,size=256m"),
                "general temporary storage was executable");
        require(redCommand.contains("--tmpfs=/native-tmp:rw,exec,nosuid,nodev,size=64m"),
                "bounded native-library temporary storage was unavailable");
        require(redCommand.stream().anyMatch(value -> value.startsWith("--env=JAVA_TOOL_OPTIONS=")
                        && value.contains("-Duser.home=/tmp")
                        && value.contains("-Djansi.tmpdir=/native-tmp")
                        && value.contains("-Dorg.sqlite.tmpdir=/native-tmp")),
                "JVM caches and native libraries were not confined to temporary storage");
        require(redCommand.stream().noneMatch(value -> value.contains("-Djava.io.tmpdir=")),
                "general JVM temporary storage was redirected to an executable mount");
        require(redCommand.contains("--env=HOME=/tmp")
                        && redCommand.contains("--env=MAVEN_CONFIG=/tmp/.m2"),
                "container home or Maven configuration escaped temporary storage");
        require(redCommand.stream().anyMatch(value -> value.contains("dst=/workspace/.git")),
                "the Worktree Git control file was not masked");
        require(redCommand.stream().anyMatch(value -> value.startsWith("--user=1000:1000")),
                "non-root container user was not fixed");
        require(redCommand.contains("-o"), "Maven was not forced into offline mode");
        require(redCommand.stream().anyMatch(value -> value.endsWith(":/m2:ro,Z")),
                "shared Maven cache was not mounted read-only");
        require(redCommand.stream().noneMatch(
                value -> value.toLowerCase(Locale.ROOT).contains("token")),
                "container command exposed a credential-bearing argument");
    }

    private static void expectInvalidSelector(Runnable action) {
        try {
            action.run();
            throw new IllegalStateException("an unsafe Maven test selector was accepted");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("exact Java class names"),
                    "unsafe selector failed for an unexpected reason");
        }
    }

    private static FeatureEvolvingConfig config(Path root, TriggerMode mode,
                                                 String webhookSecret) throws Exception {
        Files.createDirectories(root.resolve("worktree"));
        Files.createDirectories(root.resolve("m2"));
        Files.createDirectories(root.resolve("data"));
        Path repository = root.resolve("repo");
        Path featureSkill = repository.resolve("resources/skills/gitcode-feature-devflow");
        Path codingSkill = repository.resolve("resources/skills/coding-standard");
        Files.createDirectories(repository.resolve(".git"));
        Files.createDirectories(featureSkill);
        Files.createDirectories(codingSkill);
        Files.writeString(featureSkill.resolve("SKILL.md"), "feature");
        Files.writeString(codingSkill.resolve("SKILL.md"), "coding");
        return FeatureEvolvingConfig.builder()
                .dataDir(root.resolve("data"))
                .worktreeRoot(root.resolve("worktree-root"))
                .localRepository(repository)
                .featureSkill(featureSkill)
                .codingStandardSkill(codingSkill)
                .gitCodeToken("deterministic-feature-bot-token")
                .webhookSecret(webhookSecret)
                .triggerMode(mode)
                .approverLogins(List.of("approver"))
                .assignees(List.of("reviewer"))
                .containerRuntime("podman")
                .containerImage("maven:test@sha256:" + "a".repeat(64))
                .containerMavenCache(root.resolve("m2"))
                .containerUser("1000:1000")
                .containerLimits(new FeatureEvolvingConfig.ContainerLimits(1024, "1.5", 128))
                .modelProvider("deterministic")
                .modelName("deterministic")
                .modelApiBase("http://127.0.0.1/model")
                .modelApiKey("deterministic-model-key")
                .build();
    }

    private static void testPathPolicy() {
        List<String> scopes = FeaturePathPolicy.normalizeScopes(List.of(
                "features/77-demo/", "src/main/java/example/Feature.java",
                "src/test/java/example/"));
        require(FeaturePathPolicy.isAllowedWrite("features/77-demo/spec.md", scopes),
                "artifact directory scope was not honored");
        require(FeaturePathPolicy.isAllowedWrite("src/main/java/example/Feature.java", scopes),
                "exact R2-approved file was not honored");
        require(!FeaturePathPolicy.isAllowedWrite("src/main/java/example/Other.java", scopes),
                "unapproved adjacent source file was writable");
        require(!FeaturePathPolicy.isAllowedWrite("resources/skills/coding-standard/SKILL.md", scopes),
                "trusted Skill path bypassed the permanent denylist");
        require(FeaturePathPolicy.isDeniedWrite("pom.xml"),
                "Maven lifecycle configuration was not permanently denied");
        require(FeaturePathPolicy.isDeniedWrite(".mvn"),
                "Maven lifecycle directory root was not permanently denied");
        require(FeaturePathPolicy.isDeniedWrite("module/.mvn/extensions.xml"),
                "nested Maven lifecycle directory was not permanently denied");
        require(FeaturePathPolicy.isDeniedWrite(".github/workflows/release.yml"),
                "CI workflow path was not permanently denied");
        require(FeaturePathPolicy.isDeniedWrite(
                        "module/resources/skills/local/SKILL.md"),
                "nested trusted Skill path was not permanently denied");
        require(FeaturePathPolicy.isSensitiveRead("config/.env"),
                "credential file was not denied for reads");
        require(FeaturePathPolicy.isSensitiveRead("module/.git/config"),
                "nested Git control path was not denied for reads");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
