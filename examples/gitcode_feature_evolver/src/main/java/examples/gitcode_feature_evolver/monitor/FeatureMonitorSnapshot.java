/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.monitor;

import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.job.FeatureAuditEvent;
import examples.gitcode_feature_evolver.job.ApprovedGateReceipt;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureJobStore;
import examples.gitcode_feature_evolver.job.FeatureStage;
import examples.gitcode_feature_evolver.polling.FeaturePollingStatusSnapshot;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Non-sensitive read model for the local Feature Evolver demonstration monitor.
 *
 * @param generatedAt snapshot generation time in epoch milliseconds
 * @param service non-sensitive service configuration
 * @param polling latest polling status, absent when polling is disabled
 * @param jobs recent repository-scoped Jobs
 * @param events recent repository-scoped controller events
 * @since 0.1.12
 */
public record FeatureMonitorSnapshot(long generatedAt, ServiceView service,
                                     PollingView polling, List<JobView> jobs,
                                     List<EventView> events) {
    private static final int JOB_LIMIT = 20;
    private static final int EVENT_LIMIT = 300;
    private static final Pattern PUBLICATION_PATTERN = Pattern.compile(
            "Commit ([0-9a-fA-F]{0,12}) published; PR (#[0-9]+|pending) is (draft|ready)");

    /** Freeze monitor collections. */
    public FeatureMonitorSnapshot {
        service = Objects.requireNonNull(service, "service must not be null");
        jobs = List.copyOf(jobs);
        events = List.copyOf(events);
    }

    /**
     * Capture one bounded repository-scoped view.
     *
     * @param config current service configuration
     * @param store repository-scoped durable store
     * @param pollingStatus current non-sensitive polling status
     * @return immutable monitor snapshot
     */
    public static FeatureMonitorSnapshot capture(FeatureEvolvingConfig config,
                                                 FeatureJobStore store,
                                                 FeaturePollingStatusSnapshot pollingStatus) {
        FeatureEvolvingConfig requiredConfig = Objects.requireNonNull(
                config, "config must not be null");
        FeatureJobStore requiredStore = Objects.requireNonNull(store, "store must not be null");
        long now = System.currentTimeMillis();
        ServiceView service = ServiceView.from(requiredConfig);
        PollingView polling = pollingStatus == null ? null : PollingView.from(pollingStatus);
        List<JobView> jobs = requiredStore.listRecentJobs(JOB_LIMIT).stream()
                .map(job -> JobView.from(job, now,
                        requiredStore.findLatestGateReceipt(job.identity().id()))).toList();
        List<EventView> events = requiredStore.listRecentAuditEvents(EVENT_LIMIT).stream()
                .map(FeatureMonitorSnapshot::eventView).toList();
        return new FeatureMonitorSnapshot(now, service, polling, jobs, events);
    }

    private static EventView eventView(FeatureAuditEvent event) {
        Optional<FeatureStage> stage = stage(event);
        String stageName = stage.map(FeatureStage::name).orElse("");
        String detail = safeDetail(event, stage);
        return new EventView(event.id(), event.jobId(), event.type(), stageName,
                detail, event.createdAt());
    }

    private static Optional<FeatureStage> stage(FeatureAuditEvent event) {
        String detail = event.detail();
        String candidate;
        if ("TRANSITION".equals(event.type())) {
            int separator = detail.indexOf(':');
            candidate = separator < 0 ? detail : detail.substring(0, separator);
        } else if ("WORKER".equals(event.type()) && detail.startsWith("Stage ")) {
            int separator = detail.indexOf(' ', 6);
            candidate = separator < 0 ? detail.substring(6) : detail.substring(6, separator);
        } else {
            return Optional.empty();
        }
        try {
            return Optional.of(FeatureStage.valueOf(candidate.strip()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String safeDetail(FeatureAuditEvent event, Optional<FeatureStage> stage) {
        return switch (event.type()) {
            case "ADMISSION" -> admissionDetail(event.detail());
            case "WORKER" -> stage.map(value -> "Worker 开始执行 " + stageLabel(value))
                    .orElse("Worker 已领取 Job");
            case "TRANSITION" -> stage.map(FeatureMonitorSnapshot::transitionDetail)
                    .orElse("工作流阶段已更新");
            case "PUBLISH" -> publicationDetail(event.detail(), "Feature");
            case "SYSTEM_TEST_PUBLISH" -> publicationDetail(event.detail(), "System-test");
            case "COMMAND" -> "已应用通过身份校验的流程控制命令";
            case "GATE" -> "Controller 已记录受控 Gate 结果";
            case "REPAIR" -> "ReAct 已收到结构化失败并进入自动修复";
            case "RETRY" -> "瞬时故障已分类并安排受控重试";
            case "PREFETCH" -> "隔离依赖缓存预取状态已更新";
            case "FAILURE" -> "Controller 已记录结构化失败";
            default -> "控制器记录了新的生命周期事件";
        };
    }

    private static String admissionDetail(String detail) {
        if ("Issue admitted via feature_issue_poll".equals(detail)) {
            return "Polling 扫描命中 Feature Issue，持久化 Job 已建立";
        }
        return "Webhook 触发 Feature Issue 准入，持久化 Job 已建立";
    }

    private static String publicationDetail(String detail, String kind) {
        Matcher matcher = PUBLICATION_PATTERN.matcher(detail);
        if (!matcher.matches()) {
            return kind + " 提交与 PR 绑定已由控制器更新";
        }
        String sha = matcher.group(1).isBlank() ? "待确认" : matcher.group(1);
        String state = "draft".equals(matcher.group(3)) ? "Draft" : "Ready";
        return kind + " 提交 " + sha + " 已发布，PR " + matcher.group(2)
                + " 为 " + state;
    }

    private static String transitionDetail(FeatureStage stage) {
        return switch (stage) {
            case ADMITTED -> "Issue 已准入，等待 Worker";
            case SPECIFY -> "工作树已准备，开始规格澄清";
            case REVIEW_R1 -> "规格产物已生成，进入 R1 独立评审";
            case CREATE_DRAFT_PR -> "R1 已通过，准备创建或对账 Draft PR";
            case DESIGN -> "进入技术设计阶段";
            case REVIEW_R2 -> "设计产物已生成，进入 R2 独立评审";
            case IMPLEMENT_RED -> "开始 RED，建立可复现的失败测试";
            case IMPLEMENT_GREEN -> "可信 RED 已捕获，开始最小实现";
            case IMPLEMENT_REFACTOR -> "GREEN 精确选定测试已通过，开始重构";
            case PUBLISH_TASK -> "重构后测试已通过，准备提交当前任务";
            case REVIEW_R3 -> "实现任务完成，进入 R3 独立评审";
            case SHIP -> "进入最终交付与精确选定测试复验";
            case READY_FOR_REVIEW -> "选定测试复验、提交和 PR 更新已完成";
            case SYSTEM_TEST -> "Feature PR 已合入，开始生成系统测试";
            case REVIEW_SYSTEM_TEST -> "系统测试已生成，进入独立评审";
            case PUBLISH_SYSTEM_TEST -> "系统测试评审通过，准备验证并发布测试 PR";
            case SYSTEM_TEST_READY_FOR_REVIEW -> "测试 PR 已建立，等待人工 Review/Merge";
            case PAUSED -> "流程已暂停";
            case RETRY_SCHEDULED -> "瞬时故障已分类，等待定时重试";
            case DEPENDENCY_PREFETCH -> "正在隔离环境中自动预取依赖";
            case BLOCKED_EXTERNAL -> "存在真实产品或环境阻塞";
            case CANCEL_REQUESTED -> "已收到取消请求";
            case CANCELLED -> "流程已取消";
            case MERGED -> "PR 已合并";
            case CLOSED -> "PR 已关闭";
            case FAILED_AUTOMATION -> "自动修复预算已耗尽";
            case FAILED_CONFIGURATION -> "服务配置或凭据错误";
            case FAILED_POLICY -> "不可变合同或路径策略违规";
            case FAILED_INTERNAL -> "控制器遇到未分类内部异常";
        };
    }

    private static String stageLabel(FeatureStage stage) {
        return stage.name().replace('_', ' ');
    }

    /** Non-sensitive service configuration shown in the monitor header. */
    public record ServiceView(String targetRepository, String publishRepository,
                              String baseBranch, String systemTestRepository,
                              String systemTestPublishRepository,
                              String systemTestBaseBranch, boolean systemTestEnabled,
                              String triggerMode, String triggerLabel,
                              String workflowMode, int pollIntervalMinutes,
                              boolean manualPollingEnabled) {
        private static ServiceView from(FeatureEvolvingConfig config) {
            return new ServiceView(config.coordinates().targetRepository(),
                    config.coordinates().publishRepository(), config.coordinates().baseBranch(),
                    config.systemTestCoordinates().targetRepository(),
                    config.systemTestCoordinates().publishRepository(),
                    config.systemTestCoordinates().baseBranch(), config.systemTestEnabled(),
                    config.triggerMode().name().toLowerCase(Locale.ROOT), config.triggerLabel(),
                    config.defaultWorkflowMode().name().toLowerCase(Locale.ROOT),
                    config.pollIntervalMinutes(), config.manualPollingEnabled());
        }
    }

    /** Latest non-sensitive polling state. */
    public record PollingView(String result, long lastAttemptAt, long lastSuccessAt,
                              String summary) {
        private static PollingView from(FeaturePollingStatusSnapshot snapshot) {
            return new PollingView(snapshot.result().name(), snapshot.lastAttemptAt(),
                    snapshot.lastSuccessAt(), snapshot.summary());
        }
    }

    /** Repository-scoped Job summary without prompts, model output, or raw failures. */
    public record JobView(String id, long issueIid, String issueTitle, String issueUrl,
                          String stage, String mode, boolean active, String branch,
                          String artifactRoot, Long pullRequestNumber, String pullRequestUrl,
                          String headSha, boolean draft, Long systemTestPullRequestNumber,
                          String systemTestPullRequestUrl, String systemTestHeadSha,
                          int primaryRepairRound, int diagnosticRepairRound,
                          int transientRetries, int dependencyPrefetchRounds,
                          long nextRetryAt, String retryStage, String failureCode,
                          String failureCategory,
                          String gateProfile, String gateFingerprint, String gateStatus,
                          boolean gateCached, long createdAt, long updatedAt) {
        private static JobView from(FeatureJob job, long now,
                                    Optional<ApprovedGateReceipt> gate) {
            ApprovedGateReceipt receipt = gate.orElse(null);
            return new JobView(job.identity().id(), job.identity().issue().iid(),
                    job.identity().issue().title(), job.identity().issue().url(),
                    job.progress().stage().name(),
                    job.progress().mode().name().toLowerCase(Locale.ROOT),
                    !job.lease().owner().isBlank() && job.lease().until() >= now,
                    job.identity().branch(), job.identity().artifactRoot(),
                    job.pullRequest().number(), job.pullRequest().url(),
                    job.pullRequest().headSha(), job.pullRequest().draft(),
                    job.systemTestPullRequest().number(), job.systemTestPullRequest().url(),
                    job.systemTestPullRequest().headSha(),
                    job.recovery().repairs().primary(), job.recovery().repairs().diagnostic(),
                    job.recovery().retries().transientRetries(),
                    job.recovery().retries().dependencyPrefetchRounds(),
                    job.recovery().nextRetryAt(),
                    job.recovery().retryStage() == null ? ""
                            : job.recovery().retryStage().name(),
                    job.recovery().lastFailureCode(),
                    job.recovery().lastFailureCategory() == null ? ""
                            : job.recovery().lastFailureCategory().name(),
                    receipt == null ? "" : receipt.identity().profile(),
                    receipt == null ? "" : receipt.identity().fingerprint(),
                    receipt == null ? "" : receipt.result().status().name(),
                    receipt != null && receipt.result().cached(),
                    job.record().createdAt(), job.record().updatedAt());
        }
    }

    /** Redacted, controller-owned timeline event. */
    public record EventView(long id, String jobId, String type, String stage,
                            String detail, long createdAt) {
    }
}
