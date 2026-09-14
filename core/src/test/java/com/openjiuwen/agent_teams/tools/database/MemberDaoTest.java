/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.tools.TeamMember;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link MemberDao}.
 *
 * <p>Mirrors Python's {@code MemberDao} in
 * {@code openjiuwen/agent_teams/tools/database/member_dao.py}.</p>
 */
class MemberDaoTest {

    @Test
    void createMemberPersistsDefaultsAndRejectsDuplicate() throws Exception {
        MemberDao dao = newDao("member-create");

        boolean created = dao.createMember("alice", "team-a", "Alice", "{}", MemberStatus.UNSTARTED.value())
                .join();
        boolean duplicate = dao.createMember("alice", "team-a", "Alice", "{}", MemberStatus.UNSTARTED.value())
                .join();
        Optional<TeamMember> member = dao.getMember("alice", "team-a").join();

        assertThat(created).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(member).isPresent();
        assertThat(member.get().getRole()).isEqualTo(TeamRole.TEAMMATE.value());
        assertThat(member.get().getMode()).isEqualTo(MemberMode.BUILD_MODE.value());
        assertThat(member.get().getUpdatedAt()).isPositive();
    }

    @Test
    void humanAgentQueriesUsePersistedRole() throws Exception {
        MemberDao dao = newDao("member-human");

        dao.createMember(
                "human-1",
                "team-a",
                "Human",
                "{}",
                MemberStatus.READY.value(),
                TeamRole.HUMAN_AGENT.value(),
                null,
                null,
                MemberMode.BUILD_MODE.value(),
                null,
                null
        ).join();
        dao.createMember("bot-1", "team-a", "Bot", "{}", MemberStatus.READY.value()).join();
        dao.createMember(
                "human-2",
                "team-a",
                "Human 2",
                "{}",
                MemberStatus.READY.value(),
                TeamRole.HUMAN_AGENT.value(),
                null,
                null,
                MemberMode.PLAN_MODE.value(),
                null,
                null
        ).join();

        assertThat(dao.isHumanAgent("team-a", "human-1").join()).isTrue();
        assertThat(dao.isHumanAgent("team-a", "bot-1").join()).isFalse();
        assertThat(dao.listHumanAgentNames("team-a").join()).containsExactly("human-1", "human-2");
    }

    @Test
    void listMembersFiltersByStatusAndReportsMaxUpdatedAt() throws Exception {
        MemberDao dao = newDao("member-list");

        dao.createMember("alice", "team-a", "Alice", "{}", MemberStatus.READY.value()).join();
        dao.createMember("bob", "team-a", "Bob", "{}", MemberStatus.BUSY.value()).join();
        dao.createMember("mallory", "team-b", "Mallory", "{}", MemberStatus.READY.value()).join();

        List<TeamMember> allTeamA = dao.getTeamMembers("team-a").join();
        List<TeamMember> readyTeamA = dao.getTeamMembers("team-a", MemberStatus.READY.value()).join();

        assertThat(allTeamA).extracting(TeamMember::getMemberName).containsExactly("alice", "bob");
        assertThat(readyTeamA).extracting(TeamMember::getMemberName).containsExactly("alice");
        assertThat(dao.getMembersMaxUpdatedAt("team-a").join()).isPositive();
        assertThat(dao.getMembersMaxUpdatedAt("missing-team").join()).isZero();
    }

    @Test
    void updateMemberStatusValidatesTransitions() throws Exception {
        MemberDao dao = newDao("member-status");

        dao.createMember("alice", "team-a", "Alice", "{}", MemberStatus.UNSTARTED.value()).join();

        assertThat(dao.updateMemberStatus("alice", "team-a", MemberStatus.READY.value()).join()).isTrue();
        assertThat(dao.updateMemberStatus("alice", "team-a", MemberStatus.UNSTARTED.value()).join()).isFalse();
        assertThat(dao.updateMemberStatus("missing", "team-a", MemberStatus.READY.value()).join()).isFalse();
        assertThat(dao.getMember("alice", "team-a").join()).get().extracting(TeamMember::getStatus)
                .isEqualTo(MemberStatus.READY.value());
    }

    @Test
    void tryTransitionMemberStatusIsAtomicCompareAndSet() throws Exception {
        MemberDao dao = newDao("member-cas");

        dao.createMember("alice", "team-a", "Alice", "{}", MemberStatus.READY.value()).join();

        assertThat(dao.tryTransitionMemberStatus(
                "alice",
                "team-a",
                MemberStatus.READY,
                MemberStatus.BUSY
        ).join()).isTrue();
        assertThat(dao.tryTransitionMemberStatus(
                "alice",
                "team-a",
                MemberStatus.READY,
                MemberStatus.PAUSED
        ).join()).isFalse();
        assertThat(dao.getMember("alice", "team-a").join()).get().extracting(TeamMember::getStatus)
                .isEqualTo(MemberStatus.BUSY.value());
    }

    @Test
    void updateExecutionStatusValidatesTransitions() throws Exception {
        MemberDao dao = newDao("member-execution");

        dao.createMember(
                "alice",
                "team-a",
                "Alice",
                "{}",
                MemberStatus.READY.value(),
                TeamRole.TEAMMATE.value(),
                null,
                ExecutionStatus.IDLE.value(),
                MemberMode.BUILD_MODE.value(),
                null,
                null
        ).join();

        assertThat(dao.updateMemberExecutionStatus("alice", "team-a", ExecutionStatus.STARTING.value()).join())
                .isTrue();
        assertThat(dao.updateMemberExecutionStatus("alice", "team-a", ExecutionStatus.COMPLETED.value()).join())
                .isFalse();
        assertThat(dao.updateMemberExecutionStatus("missing", "team-a", ExecutionStatus.STARTING.value()).join())
                .isFalse();
        assertThat(dao.getMember("alice", "team-a").join()).get().extracting(TeamMember::getExecutionStatus)
                .isEqualTo(ExecutionStatus.STARTING.value());
    }

    private MemberDao newDao(String databaseName) throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");
        DatabaseEngine engine = new DatabaseEngine(DatabaseConfig.builder().build(), connection);
        engine.initialize().join();
        return new MemberDao(engine);
    }
}
