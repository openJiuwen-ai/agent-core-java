/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.workflow;

import examples.gitcode_feature_evolver.job.ApprovedGateReceipt;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureJobStore;
import examples.gitcode_feature_evolver.job.FeatureStage;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runs and caches one immutable Controller-approved stage gate.
 *
 * @since 0.1.12
 */
public final class ApprovedGateController implements Supplier<ApprovedGateReceipt> {
    private final FeatureJobStore store;
    private final GateSpec spec;
    private final Clock clock;
    private final Object gateLock = new Object();

    /** Create a production approved gate. */
    public ApprovedGateController(FeatureJobStore store, GateSpec spec) {
        this(store, spec, Clock.systemUTC());
    }

    ApprovedGateController(FeatureJobStore store, GateSpec spec, Clock clock) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.spec = Objects.requireNonNull(spec, "spec must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** Serialize concurrent model/final validation calls for this stage session. */
    @Override
    public ApprovedGateReceipt get() {
        synchronized (gateLock) {
            return getLocked();
        }
    }

    private ApprovedGateReceipt getLocked() {
        GateIdentity identity = spec.currentIdentity();
        Optional<ApprovedGateReceipt.Result> rejection = Objects.requireNonNull(
                spec.evaluation().precondition().get(),
                "Gate precondition result must not be null");
        if (rejection.isPresent()) {
            String fingerprint = fingerprint(identity, spec.worktree().preconditionPaths());
            return receipt(identity, fingerprint, rejection.orElseThrow());
        }
        String fingerprint = fingerprint(identity, spec.worktree().changedPaths());
        Optional<ApprovedGateReceipt> cached = store.findGateReceipt(spec.job().identity().id(),
                spec.stage(), identity.profile(), fingerprint);
        if (cached.isPresent()) {
            ApprovedGateReceipt receipt = cached.orElseThrow();
            store.recordGateCacheHit(receipt);
            return receipt;
        }
        ApprovedGateReceipt.Result result = Objects.requireNonNull(
                spec.evaluation().evaluator().get(), "Gate result must not be null");
        ApprovedGateReceipt receipt = receipt(identity, fingerprint, result);
        return cacheable(result.status()) ? store.recordGateReceipt(receipt) : receipt;
    }

    private ApprovedGateReceipt receipt(GateIdentity identity, String fingerprint,
                                        ApprovedGateReceipt.Result result) {
        return new ApprovedGateReceipt(spec.job().identity().id(), spec.stage(),
                new ApprovedGateReceipt.Identity(identity.profile(), fingerprint,
                        selectorSummary(identity.selectors())), result, clock.millis());
    }

    private String fingerprint(GateIdentity currentIdentity,
                               Supplier<List<String>> pathSupplier) {
        WorktreeState state = spec.worktree();
        FeatureGateFingerprint.GateIdentity identity = new FeatureGateFingerprint.GateIdentity(
                spec.stage().name(), currentIdentity.profile(), currentIdentity.selectors(),
                currentIdentity.imageDigest(), currentIdentity.sourceRevision());
        return FeatureGateFingerprint.compute(state.root(), state.head().get(),
                pathSupplier.get(), identity);
    }

    private static boolean cacheable(ApprovedGateReceipt.Status status) {
        return status == ApprovedGateReceipt.Status.PASSED
                || status == ApprovedGateReceipt.Status.FAILED;
    }

    private static String selectorSummary(List<String> selectors) {
        String value = String.join(",", selectors);
        return value.substring(0, Math.min(value.length(), 2_000));
    }

    /** Immutable stage/profile/selector identity. */
    public record GateIdentity(String profile, List<String> selectors,
                               String imageDigest, String sourceRevision) {
        /** Normalize identity. */
        public GateIdentity {
            profile = Objects.requireNonNull(profile, "profile must not be null");
            selectors = selectors == null ? List.of() : List.copyOf(selectors);
            imageDigest = imageDigest == null ? "" : imageDigest;
            sourceRevision = sourceRevision == null ? "" : sourceRevision;
        }
    }

    /** Worktree suppliers evaluated immediately before every Gate call. */
    public record WorktreeState(Path root, Supplier<String> head,
                                Supplier<List<String>> changedPaths,
                                Supplier<List<String>> preconditionPaths) {
        /** Use the execution fingerprint paths for precondition failures too. */
        public WorktreeState(Path root, Supplier<String> head,
                             Supplier<List<String>> changedPaths) {
            this(root, head, changedPaths, changedPaths);
        }

        /** Validate state suppliers. */
        public WorktreeState {
            root = Objects.requireNonNull(root, "root must not be null")
                    .toAbsolutePath().normalize();
            head = Objects.requireNonNull(head, "head supplier must not be null");
            changedPaths = Objects.requireNonNull(
                    changedPaths, "changed paths supplier must not be null");
            preconditionPaths = Objects.requireNonNull(
                    preconditionPaths, "precondition paths supplier must not be null");
        }
    }

    /** Complete Controller-bound Gate specification. */
    public record GateSpec(FeatureJob job, FeatureStage stage, GateIdentity identity,
                           WorktreeState worktree, GateEvaluation evaluation) {
        /** Create a Gate whose selector identity is immutable. */
        public GateSpec(FeatureJob job, FeatureStage stage, GateIdentity identity,
                        WorktreeState worktree,
                        Supplier<ApprovedGateReceipt.Result> evaluator) {
            this(job, stage, identity, worktree,
                    GateEvaluation.withoutPrecondition(evaluator, identity::selectors));
        }

        /** Validate the immutable specification. */
        public GateSpec {
            job = Objects.requireNonNull(job, "job must not be null");
            stage = Objects.requireNonNull(stage, "stage must not be null");
            identity = Objects.requireNonNull(identity, "identity must not be null");
            worktree = Objects.requireNonNull(worktree, "worktree must not be null");
            evaluation = Objects.requireNonNull(
                    evaluation, "Gate evaluation must not be null");
        }

        private GateIdentity currentIdentity() {
            List<String> selectors = evaluation.currentSelectors().get();
            return new GateIdentity(identity.profile(), selectors,
                    identity.imageDigest(), identity.sourceRevision());
        }
    }

    /** Static precondition, expensive evaluation, and current selector bindings. */
    public record GateEvaluation(
            Supplier<Optional<ApprovedGateReceipt.Result>> precondition,
            Supplier<ApprovedGateReceipt.Result> evaluator,
            Supplier<List<String>> currentSelectors) {
        /** Validate Gate callbacks. */
        public GateEvaluation {
            precondition = Objects.requireNonNull(
                    precondition, "Gate precondition must not be null");
            evaluator = Objects.requireNonNull(evaluator, "Gate evaluator must not be null");
            currentSelectors = Objects.requireNonNull(
                    currentSelectors, "selector supplier must not be null");
        }

        private static GateEvaluation withoutPrecondition(
                Supplier<ApprovedGateReceipt.Result> evaluator,
                Supplier<List<String>> currentSelectors) {
            return new GateEvaluation(Optional::empty, evaluator, currentSelectors);
        }
    }
}
