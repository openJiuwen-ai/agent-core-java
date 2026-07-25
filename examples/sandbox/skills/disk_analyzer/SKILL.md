---
description: Disk analyzer skill that runs shell commands in sandbox environment
---

# Disk Analyzer

Analyze disk usage on the remote sandbox environment:

1. Use `executeCmd` tool to run `df -h` to check disk space in the sandbox
2. Use `executeCmd` tool to run `ls -la /root` to list the home directory in the sandbox
3. Use `executeCmd` tool to run `cat /etc/os-release` to check the OS info in the sandbox
4. Summarize the disk and system information found

All commands will be executed in the sandbox environment automatically.
