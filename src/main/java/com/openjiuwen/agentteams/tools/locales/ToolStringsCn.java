/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.locales;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chinese tool description strings.
 * Mirrors Python tools/locales/cn.py.
 */
final class ToolStringsCn {
    static final Map<String, String> STRINGS;

    static {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("build_team.display_name", "团队展示名称");
        s.put("build_team.team_desc", "团队目标和描述");
        s.put("build_team.leader_display_name", "Leader的展示名称");
        s.put("build_team.leader_desc", "Leader的描述");
        s.put("build_team.enable_hitt", "是否启用人类成员(HITT)功能");
        s.put("spawn_member.member_name", "成员名称");
        s.put("spawn_member.display_name", "成员展示名称");
        s.put("spawn_member.desc", "成员描述");
        s.put("spawn_member.prompt", "成员系统提示词");
        s.put("spawn_member.model_name", "使用的模型名称");
        s.put("shutdown_member.member_name", "要关闭的成员名称");
        s.put("shutdown_member.force", "是否强制关闭");
        s.put("approve_plan.member_name", "提交计划待审批的成员名称");
        s.put("approve_plan.approved", "是否批准计划（批准: true, 拒绝: false）");
        s.put("approve_plan.feedback", "审批反馈（如拒绝，请说明原因）");
        s.put("approve_tool.member_name", "待审批工具调用的成员名称");
        s.put("approve_tool.tool_call_id", "待审批的工具调用ID");
        s.put("approve_tool.approved", "是否批准工具调用（批准: true, 拒绝: false）");
        s.put("approve_tool.feedback", "审批反馈（如拒绝，请说明原因）");
        s.put("approve_tool.auto_confirm", "是否后续自动批准同一工具调用");
        s.put("create_task.tasks", "任务列表（JSON数组）");
        s.put("create_task.tasks.id", "任务的唯一ID");
        s.put("create_task.tasks.title", "任务标题");
        s.put("create_task.tasks.content", "任务详细描述");
        s.put("create_task.tasks.depends_on", "依赖的其他任务ID列表");
        s.put("create_task.tasks.depended_by", "被其他任务依赖");
        s.put("view_task.action", "操作类型（list_all/list_pending/list_claimed/query_detail）");
        s.put("view_task.task_id", "任务ID（query_detail时必填）");
        s.put("view_task.status", "按状态过滤任务列表");
        s.put("update_task.task_id", "要更新的任务ID");
        s.put("update_task.status", "任务新状态（cancelled/completed等）");
        s.put("update_task.title", "任务新标题");
        s.put("update_task.content", "任务新描述内容");
        s.put("update_task.assignee", "指派给的成员名称");
        s.put("update_task.add_blocked_by", "新增依赖的任务ID");
        s.put("update_task.human_agent_error", "人类成员的任务不能取消或指派");
        s.put("claim_task.task_id", "要认领的任务ID");
        s.put("claim_task.status", "任务当前状态");
        s.put("send_message.to", "消息接收者的成员名称");
        s.put("send_message.content", "消息内容");
        s.put("send_message.summary", "消息摘要（可选）");
        s.put("enter_worktree.name", "工作树名称/标签（字母数字加分隔符，长度<=64）");
        s.put("exit_worktree.action", "keep（保留工作树供后续使用）或 remove（删除工作树及分支）");
        s.put("exit_worktree.discard_changes", "仅action=remove时：true=丢弃未提交更改强制删除");
        s.put("workspace_meta.action", "操作类型（list/review/stats等）");
        s.put("workspace_meta.path", "文件/目录的相对路径");
        STRINGS = Collections.unmodifiableMap(s);
    }

    /**
     * ToolStringsCn.
     * 
     * @since 0.1.7
     */
    private ToolStringsCn() {
    }
}
