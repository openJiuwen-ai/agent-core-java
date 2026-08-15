'use strict';

const phases = [
    ['扫描', 'Polling'],
    ['建单', 'Job'],
    ['规格', 'Specify'],
    ['草稿 PR', 'Draft'],
    ['设计', 'Design'],
    ['失败测试', 'RED'],
    ['最小实现', 'GREEN'],
    ['重构提交', 'Publish'],
    ['终审', 'Review'],
    ['Feature PR', 'Ship'],
    ['系统测试', 'System Test'],
    ['Test PR', 'Test Delivery']
];

const phaseByStage = {
    ADMITTED: 1,
    SPECIFY: 2,
    REVIEW_R1: 2,
    CREATE_DRAFT_PR: 3,
    DESIGN: 4,
    REVIEW_R2: 4,
    IMPLEMENT_RED: 5,
    IMPLEMENT_GREEN: 6,
    IMPLEMENT_REFACTOR: 7,
    IMPLEMENT_REWORK: 7,
    PUBLISH_TASK: 7,
    REVIEW_R3: 8,
    SHIP: 9,
    READY_FOR_REVIEW: 9,
    SYSTEM_TEST: 10,
    REVIEW_SYSTEM_TEST: 10,
    PUBLISH_SYSTEM_TEST: 10,
    SYSTEM_TEST_READY_FOR_REVIEW: 11,
    PAUSED: 4,
    RETRY_SCHEDULED: 5,
    DEPENDENCY_PREFETCH: 5,
    BLOCKED_EXTERNAL: 11,
    CANCEL_REQUESTED: 11,
    CANCELLED: 11,
    MERGED: 11,
    CLOSED: 11,
    FAILED_AUTOMATION: 11,
    FAILED_CONFIGURATION: 11,
    FAILED_POLICY: 11,
    FAILED_INTERNAL: 11
};

let latest = null;
let selectedJobId = '';
let isManualPollPending = false;
let manualPollBaselineAttemptAt = 0;
let manualPollMessage = '';

function element(id) {
    return document.getElementById(id);
}

function setText(id, value) {
    element(id).textContent = value;
}

function formatTime(value, includeDate = false) {
    if (!value) return '--';
    const options = includeDate
        ? {timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false}
        : {timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false};
    return new Intl.DateTimeFormat('zh-CN', options).format(new Date(value));
}

function shortSha(value) {
    return value ? value.slice(0, 12) : '--';
}

function parseSummary(summary) {
    const values = {};
    String(summary || '').split(',').forEach(part => {
        const [key, value] = part.split('=');
        if (key && /^\d+$/.test(value || '')) values[key] = value;
    });
    return values;
}

function safeGitCodeLink(anchor, url, label) {
    anchor.removeAttribute('href');
    anchor.classList.add('disabled');
    anchor.textContent = label;
    try {
        const parsed = new URL(url);
        if (parsed.protocol === 'https:' && parsed.hostname === 'gitcode.com') {
            anchor.href = parsed.href;
            anchor.target = '_blank';
            anchor.rel = 'noopener noreferrer';
            anchor.classList.remove('disabled');
        }
    } catch (ignored) {
        // An absent pre-publication URL remains inert.
    }
}

function renderPipeline(job) {
    const track = element('pipeline-track');
    track.replaceChildren();
    const active = job ? (phaseByStage[job.stage] ?? 1) : 0;
    phases.forEach((phase, index) => {
        const step = document.createElement('div');
        step.className = 'pipeline-step';
        if (index < active || (job && index === 0)) step.classList.add('done');
        if (index === active) step.classList.add('active');

        const dot = document.createElement('span');
        dot.className = 'step-dot';
        const card = document.createElement('div');
        card.className = 'step-card';
        const sequence = document.createElement('span');
        sequence.className = 'step-index';
        sequence.textContent = String(index + 1).padStart(2, '0');
        const label = document.createElement('span');
        label.className = 'step-label';
        label.textContent = phase[0];
        card.append(sequence, label);
        step.append(dot, card);
        track.append(step);
    });
    setText('active-stage', job ? job.stage : 'POLLING');
}

function selectJob(id) {
    selectedJobId = id;
    render(latest);
}

function renderJobs(jobs) {
    const list = element('job-list');
    list.replaceChildren();
    setText('job-count', String(jobs.length));
    if (!jobs.length) {
        const empty = document.createElement('div');
        empty.className = 'empty-state';
        empty.textContent = '尚未发现符合条件的 Feature Issue';
        list.append(empty);
        return;
    }
    jobs.forEach(job => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'job-button';
        if (job.id === selectedJobId) button.classList.add('selected');
        button.addEventListener('click', () => selectJob(job.id));

        const number = document.createElement('span');
        number.className = 'issue-number';
        number.textContent = '#' + job.issueIid;
        const title = document.createElement('span');
        title.className = 'job-button-title';
        title.textContent = job.issueTitle;
        const stage = document.createElement('span');
        stage.className = 'job-button-stage';
        stage.textContent = job.stage;
        button.append(number, title, stage);
        list.append(button);
    });
}

function renderDetail(job) {
    if (!job) {
        setText('job-title', '等待任务');
        setText('job-active', 'IDLE');
        element('job-active').classList.add('muted');
        setText('job-stage', '--');
        setText('job-updated', '--');
        setText('job-branch', '--');
        setText('job-sha', '--');
        setText('system-test-sha', '--');
        setText('repair-rounds', '--');
        setText('retry-rounds', '--');
        setText('failure-code', '--');
        setText('gate-receipt', '--');
        setText('next-retry', '--');
        safeGitCodeLink(element('issue-link'), '', '--');
        safeGitCodeLink(element('pr-link'), '', '尚未创建');
        safeGitCodeLink(element('system-test-pr-link'), '', '尚未创建');
        return;
    }
    setText('job-title', job.issueTitle);
    setText('job-active', job.active ? 'WORKER ACTIVE' : 'DURABLE');
    element('job-active').classList.toggle('muted', !job.active);
    setText('job-stage', job.stage);
    setText('job-updated', formatTime(job.updatedAt, true));
    setText('job-branch', job.branch);
    setText('job-sha', shortSha(job.headSha));
    setText('system-test-sha', shortSha(job.systemTestHeadSha));
    setText('repair-rounds', 'primary=' + job.primaryRepairRound
        + ' / diagnostic=' + job.diagnosticRepairRound);
    setText('retry-rounds', 'retry=' + job.transientRetries
        + ' / prefetch=' + job.dependencyPrefetchRounds);
    setText('failure-code', job.failureCode
        ? job.failureCategory + ' / ' + job.failureCode : '--');
    const fingerprint = job.gateFingerprint ? job.gateFingerprint.slice(0, 12) : '--';
    setText('gate-receipt', job.gateProfile
        ? job.gateProfile + ' / ' + job.gateStatus + ' / ' + fingerprint
            + (job.gateCached ? ' / cached' : '') : '--');
    setText('next-retry', job.nextRetryAt
        ? formatTime(job.nextRetryAt, true) + (job.retryStage ? ' → ' + job.retryStage : '')
        : '--');
    safeGitCodeLink(element('issue-link'), job.issueUrl, '#' + job.issueIid + ' · 打开 GitCode Issue');
    const prLabel = job.pullRequestNumber
        ? 'PR #' + job.pullRequestNumber + (job.draft ? ' · Draft' : ' · Ready')
        : '尚未创建';
    safeGitCodeLink(element('pr-link'), job.pullRequestUrl, prLabel);
    const systemTestPrLabel = job.systemTestPullRequestNumber
        ? 'PR #' + job.systemTestPullRequestNumber + ' · Ready'
        : '尚未创建';
    safeGitCodeLink(element('system-test-pr-link'), job.systemTestPullRequestUrl,
        systemTestPrLabel);
}

function renderTimeline(events, job) {
    const timeline = element('timeline');
    timeline.replaceChildren();
    const filtered = job ? events.filter(event => event.jobId === job.id) : [];
    if (!filtered.length) {
        const empty = document.createElement('li');
        empty.className = 'empty-state';
        empty.textContent = 'Job 建立后将在这里显示执行轨迹';
        timeline.append(empty);
        return;
    }
    filtered.slice(0, 18).forEach(event => {
        const item = document.createElement('li');
        item.className = 'timeline-event';
        const mark = document.createElement('span');
        mark.className = 'event-mark';
        mark.textContent = event.type.slice(0, 1);
        const top = document.createElement('div');
        top.className = 'event-top';
        const type = document.createElement('span');
        type.className = 'event-type';
        type.textContent = event.stage || event.type;
        const time = document.createElement('time');
        time.className = 'event-time';
        time.textContent = formatTime(event.createdAt);
        const detail = document.createElement('p');
        detail.className = 'event-detail';
        detail.textContent = event.detail;
        top.append(type, time);
        item.append(mark, top, detail);
        timeline.append(item);
    });
}

function renderPolling(service, polling) {
    setText('target-repository', service.targetRepository);
    setText('base-branch', service.baseBranch);
    setText('system-test-repository', service.systemTestEnabled
        ? service.systemTestRepository : '已禁用');
    setText('system-test-publish-repository', service.systemTestEnabled
        ? service.systemTestPublishRepository : '--');
    setText('system-test-base-branch', service.systemTestEnabled
        ? service.systemTestBaseBranch : '--');
    setText('workflow-mode', service.workflowMode);
    setText('trigger-label', service.triggerLabel + ' label');
    const pollButton = element('poll-now');
    pollButton.hidden = !service.manualPollingEnabled;
    pollButton.disabled = isManualPollPending || !polling || polling.result === 'RUNNING';
    setText('poll-action-status', manualPollMessage);
    if (!polling) {
        setText('poll-result', 'Webhook 模式');
        setText('poll-time', '当前配置未启用轮询');
        return;
    }
    const labels = {
        NEVER_RUN: '等待首次扫描',
        RUNNING: '扫描进行中',
        SUCCESS: '最近扫描成功',
        FAILED: '扫描失败，等待重试'
    };
    setText('poll-result', labels[polling.result] || polling.result);
    const latestTime = polling.lastAttemptAt ? '上次扫描 ' + formatTime(polling.lastAttemptAt, true) : '启动后立即执行';
    setText('poll-time', latestTime + ' · 每 ' + service.pollIntervalMinutes + ' 分钟');
    const metrics = parseSummary(polling.summary);
    setText('metric-inspected', metrics.issues || '0');
    setText('metric-admitted', metrics.admitted || '0');
}

async function requestManualPoll() {
    if (isManualPollPending) return;
    isManualPollPending = true;
    manualPollBaselineAttemptAt = latest && latest.polling
        ? latest.polling.lastAttemptAt || 0 : 0;
    manualPollMessage = '正在提交…';
    element('poll-now').disabled = true;
    setText('poll-action-status', manualPollMessage);
    try {
        const response = await fetch('/admin/poll', {
            method: 'POST',
            cache: 'no-store',
            headers: {'X-Feature-Evolver-Admin': 'poll'}
        });
        if (response.status === 202) {
            manualPollMessage = '已接受，等待扫描完成…';
        } else if (response.status === 409) {
            manualPollMessage = '已有扫描进行中，等待完成…';
            if (latest && latest.polling && latest.polling.result === 'RUNNING') {
                manualPollBaselineAttemptAt = Math.max(0,
                    latest.polling.lastAttemptAt - 1);
            }
        } else {
            manualPollMessage = '请求失败 HTTP ' + response.status;
            isManualPollPending = false;
        }
    } catch (error) {
        manualPollMessage = '请求失败';
        isManualPollPending = false;
    } finally {
        setText('poll-action-status', manualPollMessage);
        if (latest) renderPolling(latest.service, latest.polling);
    }
}

function completeManualPoll(snapshot) {
    if (!isManualPollPending || !snapshot || !snapshot.polling) return;
    const polling = snapshot.polling;
    if (polling.lastAttemptAt <= manualPollBaselineAttemptAt
            || polling.result === 'RUNNING') return;
    isManualPollPending = false;
    const metrics = parseSummary(polling.summary);
    manualPollMessage = polling.result === 'SUCCESS'
        ? '扫描完成 · PR 对账 ' + (metrics.prs || '0')
        : '扫描失败，服务将按计划重试';
}

function render(snapshot) {
    if (!snapshot) return;
    completeManualPoll(snapshot);
    if (!selectedJobId || !snapshot.jobs.some(job => job.id === selectedJobId)) {
        selectedJobId = snapshot.jobs.length ? snapshot.jobs[0].id : '';
    }
    const job = snapshot.jobs.find(candidate => candidate.id === selectedJobId) || null;
    renderPolling(snapshot.service, snapshot.polling);
    renderPipeline(job);
    renderJobs(snapshot.jobs);
    renderDetail(job);
    renderTimeline(snapshot.events, job);
    setText('generated-at', '更新于 ' + formatTime(snapshot.generatedAt));
}

async function refresh() {
    try {
        const response = await fetch('/api/monitor', {cache: 'no-store'});
        if (!response.ok) throw new Error('HTTP ' + response.status);
        latest = await response.json();
        render(latest);
        element('connection-dot').className = 'connection-dot online';
        setText('connection-label', '实时监控');
    } catch (error) {
        element('connection-dot').className = 'connection-dot offline';
        setText('connection-label', '连接中断，自动重试');
    }
}

renderPipeline(null);
element('poll-now').addEventListener('click', requestManualPoll);
refresh();
window.setInterval(refresh, 2000);
