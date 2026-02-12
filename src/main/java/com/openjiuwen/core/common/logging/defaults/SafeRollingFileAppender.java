/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.defaults;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 安全日志文件轮转处理器
 * 
 * <p>扩展 Logback RollingFileAppender，提供：
 * <ul>
 *   <li>安全文件权限设置（POSIX 系统下设置 0640/0440 权限）</li>
 *   <li>支持日志文件名模式（{name}, {ext}, {pid}, {timestamp} 等占位符）</li>
 *   <li>自动日志目录创建（安全目录权限 0750）</li>
 *   <li>备份文件权限管理（只读 0440）</li>
 * </ul>
 * 
 * <p>对应 Python: default/default_impl.py::SafeRotatingFileHandler
 */
public class SafeRollingFileAppender extends RollingFileAppender<ILoggingEvent> {
    
    private static final boolean IS_POSIX = isPosixFileSystem();
    
    /** 日志文件权限: owner rw, group r (0640) */
    private static final Set<PosixFilePermission> LOG_FILE_PERMISSIONS = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.GROUP_READ
    );
    
    /** 备份文件权限: owner r, group r (0440) */
    private static final Set<PosixFilePermission> BACKUP_FILE_PERMISSIONS = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.GROUP_READ
    );
    
    /** 目录权限: owner rwx, group rx (0750) */
    private static final Set<PosixFilePermission> DIR_PERMISSIONS = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE,
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.GROUP_EXECUTE
    );
    
    private String logFilePattern;
    private String backupFilePattern;
    
    /**
     * 创建安全日志文件轮转处理器
     */
    public SafeRollingFileAppender() {
        super();
    }
    
    /**
     * 设置日志文件名模式
     * 
     * <p>支持的占位符：
     * <ul>
     *   <li>{name} - 文件名（不含扩展名）</li>
     *   <li>{ext} - 文件扩展名</li>
     *   <li>{pid} - 进程ID</li>
     *   <li>{timestamp} - 时间戳 (yyyyMMddHHmmss)</li>
     *   <li>{date} - 日期 (yyyyMMdd)</li>
     *   <li>{time} - 时间 (HHmmss)</li>
     *   <li>{datetime} - 日期时间 (yyyy-MM-dd_HH-mm-ss)</li>
     * </ul>
     * 
     * @param logFilePattern 文件名模式
     */
    public void setLogFilePattern(String logFilePattern) {
        this.logFilePattern = logFilePattern;
    }
    
    public String getLogFilePattern() {
        return logFilePattern;
    }
    
    /**
     * 设置备份文件名模式
     * 
     * @param backupFilePattern 备份文件名模式，支持 {baseFilename} 和 {index} 占位符
     */
    public void setBackupFilePattern(String backupFilePattern) {
        this.backupFilePattern = backupFilePattern;
    }
    
    public String getBackupFilePattern() {
        return backupFilePattern;
    }
    
    @Override
    public void start() {
        // Apply filename pattern if set
        if (logFilePattern != null && !logFilePattern.isEmpty()) {
            String formattedFile = formatFilename(getFile(), logFilePattern);
            setFile(formattedFile);
        }
        
        // Ensure log directory exists with secure permissions
        ensureDirectoryExists(getFile());
        
        super.start();
        
        // Set secure file permissions on the log file
        setSecureFilePermissions(getFile(), LOG_FILE_PERMISSIONS);
    }
    
    @Override
    public void rollover() {
        super.rollover();
        
        // Set permissions on new log file
        setSecureFilePermissions(getFile(), LOG_FILE_PERMISSIONS);
        
        // Set read-only permissions on backup files
        setBackupFilePermissions();
    }
    
    /**
     * 根据模式格式化文件名
     * 
     * @param baseFilename 基础文件名
     * @param pattern 格式化模式
     * @return 格式化后的文件名
     */
    String formatFilename(String baseFilename, String pattern) {
        File file = new File(baseFilename);
        String dirPath = file.getParent();
        String fileName = file.getName();
        
        String namePart;
        String ext;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0) {
            namePart = fileName.substring(0, dotIndex);
            ext = "." + fileName.substring(dotIndex + 1);
        } else {
            namePart = fileName;
            ext = "";
        }
        
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        
        String formattedName = pattern
            .replace("{name}", namePart)
            .replace("{ext}", ext)
            .replace("{pid}", String.valueOf(ProcessHandle.current().pid()))
            .replace("{timestamp}", now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
            .replace("{date}", now.format(DateTimeFormatter.ofPattern("yyyyMMdd")))
            .replace("{time}", now.format(DateTimeFormatter.ofPattern("HHmmss")))
            .replace("{datetime}", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));
        
        // If pattern doesn't have {ext} and original file has extension, append extension
        if (!pattern.contains("{ext}") && !ext.isEmpty() && !formattedName.endsWith(ext)) {
            formattedName = formattedName + ext;
        }
        
        if (dirPath != null) {
            return dirPath + File.separator + formattedName;
        }
        return formattedName;
    }
    
    /**
     * 确保日志目录存在，并设置安全权限
     */
    private void ensureDirectoryExists(String filePath) {
        if (filePath == null) {
            return;
        }
        File dir = new File(filePath).getParentFile();
        if (dir != null && !dir.exists()) {
            boolean created = dir.mkdirs();
            if (created && IS_POSIX) {
                try {
                    Files.setPosixFilePermissions(dir.toPath(), DIR_PERMISSIONS);
                } catch (IOException e) {
                    addWarn("Failed to set directory permissions on " + dir + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 设置文件的安全权限（仅 POSIX 系统）
     */
    private void setSecureFilePermissions(String filePath, Set<PosixFilePermission> permissions) {
        if (!IS_POSIX || filePath == null) {
            return;
        }
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.setPosixFilePermissions(path, permissions);
            }
        } catch (IOException e) {
            throw new JiuWenBaseException(
                StatusCode.COMMON_LOG_EXECUTION_RUNTIME_ERROR.getCode(),
                StatusCode.COMMON_LOG_EXECUTION_RUNTIME_ERROR.getMessage()
                    .replace("{error_msg}", "failed to set file permissions: " + e.getMessage())
            );
        }
    }
    
    /**
     * 设置备份文件的只读权限
     * 
     * <p>在 POSIX 系统下，将备份文件设置为 0440（owner 和 group 只读）。
     */
    private void setBackupFilePermissions() {
        if (!IS_POSIX || getFile() == null) {
            return;
        }
        
        File dir = new File(getFile()).getParentFile();
        String baseName = new File(getFile()).getName();
        
        if (dir != null && dir.exists()) {
            File[] backupFiles = dir.listFiles((d, name) ->
                name.startsWith(baseName) && !name.equals(baseName));
            if (backupFiles != null) {
                for (File backupFile : backupFiles) {
                    try {
                        Files.setPosixFilePermissions(backupFile.toPath(), BACKUP_FILE_PERMISSIONS);
                    } catch (IOException e) {
                        throw new JiuWenBaseException(
                            StatusCode.COMMON_LOG_EXECUTION_RUNTIME_ERROR.getCode(),
                            StatusCode.COMMON_LOG_EXECUTION_RUNTIME_ERROR.getMessage()
                                .replace("{error_msg}", "failed to set backup file permissions: " + e.getMessage())
                        );
                    }
                }
            }
        }
    }
    
    /**
     * 检测文件系统是否支持 POSIX 权限
     */
    private static boolean isPosixFileSystem() {
        try {
            return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        } catch (Exception e) {
            return false;
        }
    }
}

