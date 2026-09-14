/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.openjiuwen.agent_teams.messager.MessagerPeerConfig;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything an external agent needs to attach to a running team.
 *
 * <p>Mirrors Python's {@code TeamJoinDescriptor} in
 * {@code openjiuwen/agent_teams/external/descriptor.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class TeamJoinDescriptor {

    public static final String TEAM_JOIN_ENV = "OPENJIUWEN_TEAM_JOIN";

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> STRING_OBJECT_MAP =
            new TypeReference<>() {
            };

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("team_name")
    private String teamName;

    @JsonProperty("member_name")
    private String memberName;

    @JsonProperty("role")
    private String role = "teammate";

    @JsonProperty("language")
    private String language = "cn";

    @JsonProperty("db_config")
    private Map<String, Object> dbConfig = defaultDbConfig();

    @JsonProperty("transport_config")
    private MessagerTransportConfig transportConfig = new MessagerTransportConfig();

    public TeamJoinDescriptor() {
    }

    public TeamJoinDescriptor(
            String sessionId,
            String teamName,
            String memberName,
            String role,
            String language,
            Map<String, Object> dbConfig,
            MessagerTransportConfig transportConfig
    ) {
        this.sessionId = sessionId;
        this.teamName = teamName;
        this.memberName = memberName;
        setRole(role);
        setLanguage(language);
        setDbConfig(dbConfig);
        setTransportConfig(transportConfig);
    }

    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to encode team join descriptor", exception);
        }
    }

    public Map<String, String> toEnv() {
        return Map.of(TEAM_JOIN_ENV, toJson());
    }

    public static TeamJoinDescriptor fromJson(String raw) {
        try {
            TeamJoinDescriptor descriptor = OBJECT_MAPPER.readValue(raw, TeamJoinDescriptor.class);
            descriptor.applyDefaults();
            descriptor.validateRequired();
            return descriptor;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            ErrorHelper.raiseError(
                    StatusCode.AGENT_TEAM_CONFIG_INVALID,
                    null,
                    null,
                    exception,
                    Map.of("reason", "malformed team join descriptor: " + exception.getMessage())
            );
            throw new IllegalStateException("unreachable");
        }
    }

    public static TeamJoinDescriptor fromEnv() {
        return fromEnv(null);
    }

    public static TeamJoinDescriptor fromEnv(Map<String, String> env) {
        Map<String, String> source = env == null ? System.getenv() : env;
        String raw = source.get(TEAM_JOIN_ENV);
        if (raw == null || raw.isBlank()) {
            ErrorHelper.raiseError(
                    StatusCode.AGENT_TEAM_CONFIG_INVALID,
                    null,
                    null,
                    null,
                    Map.of("reason", "environment variable " + TEAM_JOIN_ENV + " is not set; cannot join team")
            );
        }
        return fromJson(raw);
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role == null || role.isBlank() ? "teammate" : role;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language == null || language.isBlank() ? "cn" : language;
    }

    public Map<String, Object> getDbConfig() {
        return new LinkedHashMap<>(dbConfig);
    }

    public void setDbConfig(Map<String, Object> dbConfig) {
        this.dbConfig = dbConfig == null ? defaultDbConfig() : new LinkedHashMap<>(dbConfig);
    }

    public MessagerTransportConfig getTransportConfig() {
        return copyTransport(transportConfig);
    }

    public void setTransportConfig(MessagerTransportConfig transportConfig) {
        this.transportConfig = transportConfig == null ? new MessagerTransportConfig() : copyTransport(transportConfig);
    }

    private void applyDefaults() {
        setRole(role);
        setLanguage(language);
        setDbConfig(dbConfig);
        setTransportConfig(transportConfig);
    }

    private void validateRequired() {
        List<String> missing = new ArrayList<>();
        if (sessionId == null) {
            missing.add("session_id");
        }
        if (teamName == null) {
            missing.add("team_name");
        }
        if (memberName == null) {
            missing.add("member_name");
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("missing required field(s): " + String.join(", ", missing));
        }
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    private static Map<String, Object> defaultDbConfig() {
        return OBJECT_MAPPER.convertValue(new DatabaseConfig(), STRING_OBJECT_MAP);
    }

    private static MessagerTransportConfig copyTransport(MessagerTransportConfig source) {
        MessagerTransportConfig copy = new MessagerTransportConfig();
        copy.setBackend(source.getBackend());
        copy.setTeamName(source.getTeamName());
        copy.setNodeId(source.getNodeId());
        copy.setDirectAddr(source.getDirectAddr());
        copy.setPubsubPublishAddr(source.getPubsubPublishAddr());
        copy.setPubsubSubscribeAddr(source.getPubsubSubscribeAddr());
        copy.setListenAddrs(source.getListenAddrs());
        copy.setBootstrapPeers(source.getBootstrapPeers());
        copy.setKnownPeers(source.getKnownPeers());
        copy.setRequestTimeout(source.getRequestTimeout());
        copy.setMetadata(source.getMetadata());
        return copy;
    }
}
