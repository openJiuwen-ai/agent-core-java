/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.store.BaseDbStore;

import java.util.concurrent.CompletableFuture;

/**
 * Table creation utilities for message entities.
 * Corresponds to Python: manage/mem_model/message.py - create_tables function
 */
public class MessageTables {

    private static final LoggerProtocol logger = Loggers.MEMORY;

    private MessageTables() {
        // Utility class
    }

    /**
     * Create tables for UserMessage and ScopeUserMapping.
     * Corresponds to Python: create_tables(db_store: BaseDbStore)
     *
     * @param dbStore the database store
     * @return CompletableFuture that completes when tables are created
     */
    public static CompletableFuture<Void> createTables(BaseDbStore dbStore) {
        return CompletableFuture.runAsync(() -> {
            try {
                // In Python, this uses SQLAlchemy's metadata.create_all
                // In Java, the actual table creation depends on the database implementation
                // This is a placeholder that would work with the actual BaseDbStore implementation
                
                logger.debug("Creating tables: {}, {}", 
                    UserMessage.getTableName(), 
                    ScopeUserMapping.getTableName());
                
                // The actual implementation would use the dbStore to create tables
                // For now, this serves as a placeholder for the async table creation
                
            } catch (Exception e) {
                logger.error("Failed to create tables", e);
                throw new RuntimeException("Failed to create tables", e);
            }
        });
    }

    /**
     * Get the table name for UserMessage.
     *
     * @return table name
     */
    public static String getUserMessageTableName() {
        return UserMessage.getTableName();
    }

    /**
     * Get the table name for ScopeUserMapping.
     *
     * @return table name
     */
    public static String getScopeUserMappingTableName() {
        return ScopeUserMapping.getTableName();
    }
}

