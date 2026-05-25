// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.storage;

import java.util.*;

/**
 * Trajectory store for RL training data.
 * <p>
 * Mirrors Python's {@code trajectory_store.py} from
 * {@code openjiuwen.agent_evolving.agent_rl.storage.trajectory_store}.
 */
public class TrajectoryStore {
    
    /**
     * Redis-based trajectory store.
     */
    public static class RedisTrajectoryStore {
        private final String redisUrl;
        
        public RedisTrajectoryStore(String redisUrl) {
            this.redisUrl = redisUrl;
        }
        
        /**
         * Save trajectory.
         * PLACEHOLDER: Requires Redis client integration.
         */
        public void saveTrajectory(String trajectoryId, Object trajectory) {
            throw new UnsupportedOperationException(
                "saveTrajectory requires Redis client integration. " +
                "Placeholder until Redis client is translated."
            );
        }
        
        /**
         * Load trajectory.
         * PLACEHOLDER: Requires Redis client integration.
         */
        public Object loadTrajectory(String trajectoryId) {
            throw new UnsupportedOperationException(
                "loadTrajectory requires Redis client integration. " +
                "Placeholder until Redis client is translated."
            );
        }
        
        /**
         * Get pending trajectories count.
         */
        public int getPendingCount() {
            // PLACEHOLDER
            return 0;
        }
    }
    
    /**
     * File-based trajectory store.
     * <p>
     * Implements basic file-based storage for trajectories using JSON serialization.
     */
    public static class FileTrajectoryStore {
        private final String basePath;
        private final java.io.File baseDir;
        
        public FileTrajectoryStore(String basePath) {
            this.basePath = basePath;
            this.baseDir = new java.io.File(basePath);
            // Ensure directory exists
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }
        }
        
        /**
         * Save trajectory to file.
         * <p>
         * Mirrors Python's trajectory serialization using JSON.
         *
         * @param trajectoryId Unique identifier for the trajectory
         * @param trajectory Trajectory data (will be serialized to JSON)
         */
        public void saveTrajectory(String trajectoryId, Object trajectory) {
            try {
                java.io.File file = new java.io.File(baseDir, trajectoryId + ".json");
                
                // Simple JSON serialization using toString
                // For production, use Jackson or Gson for proper JSON serialization
                StringBuilder json = new StringBuilder();
                json.append("{\n");
                json.append("  \"trajectory_id\": \"").append(trajectoryId).append("\",\n");
                json.append("  \"data\": ").append(objectToJson(trajectory)).append("\n");
                json.append("}");
                
                java.nio.file.Files.write(file.toPath(), json.toString().getBytes());
            } catch (Exception e) {
                throw new RuntimeException("Failed to save trajectory: " + trajectoryId, e);
            }
        }
        
        /**
         * Load trajectory from file.
         *
         * @param trajectoryId Unique identifier for the trajectory
         * @return Trajectory data (as Map)
         */
        public Object loadTrajectory(String trajectoryId) {
            try {
                java.io.File file = new java.io.File(baseDir, trajectoryId + ".json");
                if (!file.exists()) {
                    return null;
                }
                
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                // Simple parsing - for production use proper JSON parser
                return parseJsonToMap(content);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load trajectory: " + trajectoryId, e);
            }
        }
        
        /**
         * List all stored trajectory IDs.
         */
        public List<String> listTrajectoryIds() {
            List<String> ids = new ArrayList<>();
            if (baseDir.exists() && baseDir.isDirectory()) {
                for (java.io.File file : baseDir.listFiles()) {
                    if (file.getName().endsWith(".json")) {
                        ids.add(file.getName().replace(".json", ""));
                    }
                }
            }
            return ids;
        }
        
        /**
         * Delete a trajectory.
         */
        public boolean deleteTrajectory(String trajectoryId) {
            java.io.File file = new java.io.File(baseDir, trajectoryId + ".json");
            return file.delete();
        }
        
        /**
         * Simple object to JSON string conversion.
         * For production, use Jackson or Gson.
         */
        @SuppressWarnings("unchecked")
        private String objectToJson(Object obj) {
            if (obj == null) return "null";
            if (obj instanceof String) return "\"" + obj.toString().replace("\"", "\\\"") + "\"";
            if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
            if (obj instanceof List) {
                StringBuilder sb = new StringBuilder("[");
                List<?> list = (List<?>) obj;
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(objectToJson(list.get(i)));
                }
                sb.append("]");
                return sb.toString();
            }
            if (obj instanceof Map) {
                StringBuilder sb = new StringBuilder("{");
                Map<?, ?> map = (Map<?, ?>) obj;
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append("\"").append(entry.getKey()).append("\": ");
                    sb.append(objectToJson(entry.getValue()));
                    first = false;
                }
                sb.append("}");
                return sb.toString();
            }
            return "\"" + obj.toString().replace("\"", "\\\"") + "\"";
        }
        
        /**
         * Simple JSON to Map parsing.
         * For production, use Jackson or Gson.
         */
        private Map<String, Object> parseJsonToMap(String json) {
            Map<String, Object> result = new HashMap<>();
            // Very basic parsing - just return raw content wrapped
            result.put("raw", json);
            return result;
        }
    }
}