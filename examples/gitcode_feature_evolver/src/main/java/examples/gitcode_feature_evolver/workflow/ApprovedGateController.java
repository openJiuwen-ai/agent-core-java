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
    public synchronized ApprovedGateReceipt get() {
        GateIdentity identity = spec.currentIdentity();
        String fingerprint = fingerprint(identity);
        Optional<ApprovedGateReceipt> cached = store.findGateReceipt(spec.job().identity().id(),
                spec.stage(), identity.profile(), fingerprint);
        if (cached.isPresent()) {
            ApprovedGateReceipt receipt = cached.orElseThrow();
            store.recordGateCacheHit(receipt);
            return receipt;
        }
        ApprovedGateReceipt.Result result = Objects.requireNonNull(
                spec.evaluator().get(), "Gate result must not be null");
        ApprovedGateReceipt receipt = new ApprovedGateReceipt(spec.job().identity().id(),
                spec.stage(), new ApprovedGateReceipt.Identity(identity.profile(),
                fingerprint, selectorSummary(identity.selectors())), result,
                clock.millis());
        return cacheable(result.status()) ? store.recordGateReceipt(receipt) : receipt;
    }

    private String fingerprint(GateIdentity currentIdentity) {
        WorktreeState state = spec.worktree();
        FeatureGateFingerprint.GateIdentity identity = new FeatureGateFingerprint.GateIdentity(
                spec.stage().name(), currentIdentity.profile(), currentIdentity.selectors(),
                currentIdentity.imageDigest(), currentIdentity.sourceRevision());
        return FeatureGateFingerprint.compute(state.root(), state.head().get(),
                state.changedPaths().get(), identity);
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
                                Supplier<List<String>> changedPaths) {
        /** Validate state suppliers. */
        public WorktreeState {
            root = Objects.requireNonNull(root, "root must not be null")
                    .toAbsolutePath().normalize();
            head = Objects.requireNonNull(head, "head supplier must not be null");
            changedPaths = Objects.requireNonNull(
                    changedPaths, "changed paths supplier must not be null");
        }
    }

    /** Complete Controller-bound Gate specification. */
    public record GateSpec(FeatureJob job, FeatureStage stage, GateIdentity identity,
                           WorktreeState worktree,
                           Supplier<ApprovedGateReceipt.Result> evaluator,
                           Supplier<List<String>> currentSelectors) {
        /** Create a Gate whose selector identity is immutable. */
        public GateSpec(FeatureJob job, FeatureStage stage, GateIdentity identity,
                        WorktreeState worktree,
                        Supplier<ApprovedGateReceipt.Result> evaluator) {
            this(job, stage, identity, worktree, evaluator, identity::selectors);
        }

        /** Validate the immutable specification. */
        public GateSpec {
            job = Objects.requireNonNull(job, "job must not be null");
            stage = Objects.requireNonNull(stage, "stage must not be null");
            identity = Objects.requireNonNull(identity, "identity must not be null");
            worktree = Objects.requireNonNull(worktree, "worktree must not be null");
            evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
            currentSelectors = Objects.requireNonNull(
                    currentSelectors, "selector supplier must not be null");
        }

        private GateIdentity currentIdentity() {
            List<String> selectors = currentSelectors.get();
            return new GateIdentity(identity.profile(), selectors,
                    identity.imageDigest(), identity.sourceRevision());
        }
    }
}
