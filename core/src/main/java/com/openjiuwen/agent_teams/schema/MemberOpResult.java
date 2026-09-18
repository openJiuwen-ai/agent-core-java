/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import java.util.Objects;

/**
 * Outcome of a team-member mutation with the failure reason preserved.
 *
 * <p>Mirrors Python's {@code MemberOpResult} in
 * {@code openjiuwen/agent_teams/schema/team.py}.</p>
 */
public final class MemberOpResult {

    private final boolean ok;
    private final String reason;

    public MemberOpResult(boolean ok, String reason) {
        this.ok = ok;
        this.reason = reason == null ? "" : reason;
    }

    public static MemberOpResult success() {
        return new MemberOpResult(true, "");
    }

    public static MemberOpResult fail(String reason) {
        return new MemberOpResult(false, reason);
    }

    public boolean isOk() {
        return ok;
    }

    public String getReason() {
        return reason;
    }

    public boolean asBoolean() {
        return ok;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MemberOpResult that)) {
            return false;
        }
        return ok == that.ok && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ok, reason);
    }
}
