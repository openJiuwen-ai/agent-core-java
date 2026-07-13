---
name: jvm-troubleshoot
description: JVM 线上故障排查手册。在应用报 OOM、CPU 100%、GC 频繁、线程死锁、类加载泄漏、启动失败、响应卡顿、容器内 JVM 被 kill、模块系统反射失败时主动应用。涉及关键词：OOM、OutOfMemoryError、CPU 高、GC、Full GC、死锁、deadlock、jstack、jmap、jstat、JVM 调优、Metaspace、StackOverflow、OOMKilled、容器、cgroup、PID 1、JRE-only、InaccessibleObjectException、--add-opens、模块系统、JPMS。不适用于：编码规范问题（用 coding-standard）、agent team 装配（用 agent-team-guide）、非 JVM 故障、事前性能调优与 JVM 参数选型（用 performance-tuning）。
---

# JVM 线上故障排查手册

本 skill 按"症状 → 诊断流程 → 根因表 → 修复"的结构组织，覆盖 7 类高频 JVM 故障。所有命令均需先 `jps -lvm` 拿到 `<pid>`。

**与 performance-tuning 的边界**：
- jvm-troubleshoot（本 skill）：事后排查（OOM/CPU 100% 已发生）→ 按"症状 → 诊断 → 修复"
- performance-tuning：事前优化（部署前选 GC、调 JVM 参数、写高性能代码）→ 按"层次 → 选型 → 优化 → 验证"

## 故障分类速查

| 症状 | 跳转 |
|---|---|
| 应用报 `OutOfMemoryError` | [OOM 排查](#oom-排查) |
| CPU 占用 100% | [CPU 排查](#cpu-100-排查) |
| GC 频繁、应用卡顿 | [高频 GC 排查](#高频-gc-排查) |
| 响应慢、超时、无报错 | [性能排查](#性能排查) |
| 线程死锁、程序卡住不动 | [线程死锁排查](#线程死锁排查) |
| `ClassNotFoundException` / `NoClassDefFoundError` | [类加载排查](#类加载排查) |
| `InaccessibleObjectException` / 模块系统反射失败 | [模块系统排查](#模块系统排查) |
| JVM 启动失败、立即退出 | [启动失败排查](#启动失败排查) |
| 容器内 JVM 被 kill / `jps` 看不到 / JRE-only | [容器排查](#容器排查) |

## OOM 排查

OOM 类型与定位：

| OOM 类型 | 报错关键词 | 根因 | 排查 |
|---|---|---|---|
| **Java heap space** | `OutOfMemoryError: Java heap space` | 堆内存不足，对象未释放 | `jmap -dump` 拿 dump → MAT 分析大对象 → 查静态 Map/list 持续增长 |
| **GC overhead** | `GC overhead limit exceeded` | GC 花太多时间回收太少内存 | 同上，通常是内存泄漏或堆太小 |
| **Metaspace** | `OutOfMemoryError: Metaspace` | 类元数据太多（动态生成类、ClassLoader 泄漏） | 调 `-XX:MaxMetaspaceSize`；查动态代理/CGLIB；见 [类加载排查](#类加载排查) |
| **Direct buffer** | `OutOfMemoryError: Direct buffer memory` | NIO 直接内存未释放 | 查 `ByteBuffer.allocateDirect` 释放；调 `-XX:MaxDirectMemorySize` |
| **StackOverflow** | `StackOverflowError` | 递归太深或栈帧过大 | 查递归终止条件；调 `-Xss` 增大栈（治标不治本） |
| **unable to create thread** | `OutOfMemoryError: unable to create new native thread` | 线程数达 OS 上限 | `jstack` 看线程数；查线程池泄漏；调 `ulimit -u` |

**止血**：先 `-XX:+HeapDumpOnOutOfMemoryError` 拿 dump，再重启。**根治**：MAT 分析 dump 找 GC Root 链，定位泄漏点。

## CPU 100% 排查

**诊断流程**（4 步定位）：

1. `top` 找占 CPU 高的**进程** PID
2. `top -Hp <pid>` 找占 CPU 高的**线程** TID（十进制）
3. `printf "%x\n" <tid>` 转十六进制
4. `jstack <pid> | grep <十六进制 tid> -A 30` 看该线程在干什么

**常见根因**：

| 根因 | 表现 | 对策 |
|---|---|---|
| 死循环 / 无限递归 | jstack 栈帧重复或固定 | 修代码逻辑，加终止条件 |
| 正则回溯爆炸 | 栈在 `Pattern.matcher` | 简化正则，或用预编译 + 超时 |
| 大集合嵌套遍历 | 栈在 `for` / `Stream` | 改算法，降 O(n²) 到 O(n) |
| 序列化大对象 | 栈在 `ObjectOutputStream` | 限制对象大小，或流式处理 |
| GC 线程占满 | 多个 `GC task` 线程 CPU 高 | 实际是内存问题，见 [高频 GC 排查](#高频-gc-排查) |
| 加密哈希密集 | 栈在 `MessageDigest` | 用更快的算法，或异步执行 |

**注意**：如果 CPU 高但业务逻辑不复杂，先查是不是 GC 线程占满（看 GC 日志）。

## 高频 GC 排查

**诊断流程**：

1. 开 GC 日志：`-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=10M`（JDK9+）
2. 用 GCViewer 或 gceasy.io 分析日志
3. 看关键指标：
   - **Full GC 频率**：正常几小时一次，几分钟一次有问题
   - **GC 吞吐量**：GC 时间占比应 < 5%，> 10% 要排查
   - **老年代占用**：持续增长不回落 = 内存泄漏
4. `jstat -gcutil <pid> 1000` 看各代占用变化

**常见根因与对策**：

| 根因 | 对策 |
|---|---|
| 堆太小 | 调大 `-Xmx` |
| 对象创建过快 | 缓存、对象池、`StringBuilder` |
| 内存泄漏 | 拿 dump 找泄漏点（见 OOM 排查） |
| 大对象直接进老年代 | 调 `-XX:PretenureSizeThreshold` |
| Metaspace 不足 | 调 `-XX:MaxMetaspaceSize`（见 OOM Metaspace） |
| `System.gc()` 被调 | 加 `-XX:+DisableExplicitGC` 禁用 |

**GC 收集器选型与生产参数配置**：事前配置归 performance-tuning skill，见 `../performance-tuning/references/gc_tuning.md`。本 skill 只关注事后排查。

## 性能排查

响应慢但 CPU 不高、GC 正常时：

1. `jstack <pid> > thread.txt` 看线程状态分布
2. 统计 `BLOCKED` / `WAITING` 线程数：`grep "java.lang.Thread.State" thread.txt | sort | uniq -c`
3. 大量 `BLOCKED` → 锁竞争，见 [线程死锁排查](#线程死锁排查)
4. 大量 `WAITING` → 线程池空闲或等待外部资源（DB/HTTP）
5. 看是否有线程卡在 `SocketRead` → RPC 超时或对端慢
6. `jstat -gcutil <pid> 1000` 排除 GC 问题

## 线程死锁排查

**诊断**：`jstack <pid> | grep -A 20 "Found Java deadlock"` —— jstack 会自动检测 synchronized 死锁并打印。

**synchronized 死锁**：
- 表现：`jstack` 报 "Found 1 deadlock"，两个线程互相等对方的锁
- 根因：嵌套 `synchronized` 块，锁顺序不一致
- 修复：统一锁顺序，或用 `tryLock(timeout)`

**Lock 死锁（ReentrantLock）**：
- 表现：线程 `WAITING` 在 `AbstractQueuedSynchronizer`，但 jstack **不报 deadlock**（Lock 死锁不自动检测）
- 诊断：看 `jstack` 栈是否卡在 `lock()` 调用
- 修复：用 `tryLock(timeout, unit)` 替代 `lock()`，超时后释放并重试

**线程泄漏（假死锁）**：
- 表现：线程数持续增长，最终 `unable to create thread`
- 诊断：`jstack <pid> | grep "java.lang.Thread.State" | wc -l` 数线程总数
- 修复：查线程池是否泄漏（`new Thread` 未纳入池、`submit` 后没 `shutdown`）

## 类加载排查

`ClassNotFoundException` / `NoClassDefFoundError` / `LinkageError`：

1. `jmap -clstats <pid>` 看 ClassLoader 数量和加载的类数
2. ClassLoader 数量异常多 → ClassLoader 泄漏（常见于热部署、动态代理）
3. 类找不到 → 查 classpath：`jcmd <pid> VM.classloader_stats`
4. 同一类多版本冲突 → `jmap -clstats` 看是否有多个 ClassLoader 加载同类

**Metaspace 泄漏**：
- 现象：Full GC 后 Metaspace 不降，最终 OOM Metaspace
- 诊断：`jmap -clstats <pid>` 看 ClassLoader 是否持续增长
- 根因：动态代理（CGLIB/ByteBuddy）每次生成新类未缓存，或 Web 容器热部署未清理旧 ClassLoader
- 修复：缓存代理类、修复热部署泄漏、调 `-XX:MaxMetaspaceSize`

## 模块系统排查

JDK 9+ 模块系统（JPMS）特有故障。JDK 17 必踩。

### InaccessibleObjectException

**现象**：反射调用报 `java.lang.reflect.InaccessibleObjectException: Unable to make field accessible: module java.base does not "opens java.lang" to unnamed module`

**根因**：JDK 9+ 模块系统保护非 export 包，反射访问需显式打开。

**排查**：
1. 看报错哪行提到 "module X does not opens Y"
2. 对应模块加 `--add-opens`

**常见 `--add-opens` 速查**：

| 场景 | 报错模块 | 参数 |
|---|---|---|
| 反射访问 `String` 内部 | `java.base/java.lang` | `--add-opens java.base/java.lang=ALL-UNNAMED` |
| 反射访问集合内部 | `java.base/java.util` | `--add-opens java.base/java.util=ALL-UNNAMED` |
| Netty NIO 访问 | `java.base/java.nio` | `--add-opens java.base/java.nio=ALL-UNNAMED` |
| 反射访问 `Method` | `java.base/java.lang.reflect` | `--add-opens java.base/java.lang.reflect=ALL-UNNAMED` |
| Kryo / Gson 序列化 | `java.base/java.lang` 等 | 通常需多个 `--add-opens` |
| Spring / Hibernate 反射 | 多个 | 见框架文档 |

**配置位置**：
- 命令行：`java --add-opens ... -jar app.jar`
- MANIFEST.MF：`Add-Opens: java.base/java.lang java.base/java.util`
- K8s：`JAVA_OPTS` 环境变量

### `--add-opens` vs `--add-exports`

| 参数 | 用途 |
|---|---|
| `--add-opens` | 反射访问（运行时打开） |
| `--add-exports` | 编译时 + 运行时访问（非反射） |
| `--add-reads` | 让某模块读另一模块 |

**选**：反射场景用 `--add-opens`；直接 import 用 `--add-exports`。

### 模块冲突

**现象**：启动报 `Module resolution failed` 或 `module not found`。

**排查**：
- 看是否多个版本同一模块
- 检查 module-path（`--module-path`）vs classpath
- 用 `java --list-modules` 看已加载模块

### 模块系统诊断命令

```bash
# 看模块系统参数
jcmd <pid> VM.system_properties | grep jdk.module

# 看已加载模块
java --list-modules

# 启动调试
java --show-module-resolution -jar app.jar
```

## 容器排查

K8s / Docker 容器内 JVM 特有故障。详细排查看 `references/container_troubleshooting.md`。

**速查**：

| 症状 | 根因 | 对策 |
|---|---|---|
| Pod `OOMKilled` exit 137 | cgroup 内存 < JVM 用量 | 用 `MaxRAMPercentage` 替代固定 `-Xmx` |
| `jps` 看不到 JVM | 容器主进程是 PID 1 | 用 `jcmd 1 ...` |
| `jcmd` 命令找不到 | JRE-only 镜像 | sidecar 带 JDK / 用 jattach / 用 JFR |
| `Could not reserve enough space` | `-Xmx` 超 cgroup | 用 `MaxRAMPercentage=75.0` |
| `unable to create new native thread` | 容器 ulimit 收紧 | 调 `ulimit -u` 或减线程数 |
| JFR / dump 落盘丢 | 容器路径未挂载 | 挂 PVC 到 `HeapDumpPath` |

## 启动失败排查

JVM 启动立即退出，看报错关键词：

| 报错 | 根因 | 对策 |
|---|---|---|
| `Could not reserve enough space for object heap` | `-Xmx` 超过物理内存 | 调小 `-Xmx`，或查是否有其他进程占内存 |
| `Invalid initial heap size` | `-Xms` / `-Xmx` 参数格式错 | 检查参数单位（`4g` 不是 `4096`） |
| `Incompatible version` | 编译时 JDK 版本 > 运行时 | 用相同或更高 JDK 运行 |
| `Unsupported major.minor version` | class 文件版本不匹配 | 用 `javap -verbose X.class` 看 major version |
| `Unable to open jar` | jar 包损坏或路径错 | 检查 `-jar` 路径和 jar 完整性 |
| `Error: LinkageError` | 类冲突 | 查 classpath 重复类 |

## 命令速查

按场景分类的 JVM 诊断命令：

```bash
# === 进程概览 ===
jps -lvm                                      # 列 JVM 进程
jcmd <pid> VM.flags                           # 看 JVM 启动参数
jcmd <pid> VM.system_properties               # 看系统属性

# === 堆分析 ===
jmap -heap <pid>                              # 堆概况（各代占用）
jmap -histo:live <pid> | head -20             # 大对象直方图（前 20）
jmap -dump:format=b,file=heap.hprof <pid>     # 导出 heap dump
jmap -finalizer_info <pid>                    # 看 finalizer 队列

# === 线程分析 ===
jstack <pid> > thread.txt                     # 线程栈
jstack -l <pid>                               # 含锁信息的线程栈
top -Hp <pid>                                 # 线程级 CPU 占用

# === GC 分析 ===
jstat -gcutil <pid> 1000                      # 各代占用 + GC 次数（每秒刷新）
jstat -gccause <pid> 1000                     # 上次 GC 原因
jcmd <pid> GC.heap_info                       # 堆信息

# === 类分析 ===
jmap -clstats <pid>                           # ClassLoader 统计
jcmd <pid> VM.classloader_stats               # ClassLoader 状态
jmap -permstat <pid>                          # 持久代统计（JDK7）

# === 系统级 ===
top                                           # 进程 CPU/内存
iostat -x 1                                   # 磁盘 IO
netstat -anp | grep <pid>                     # 网络连接
```

## JVM 参数

排查时常开的监控参数：
```
-XX:+HeapDumpOnOutOfMemoryError               # OOM 时自动 dump（排查 OOM 必备）
-XX:HeapDumpPath=/var/log/dumps/              # dump 路径
-XX:ErrorFile=/var/log/hs_err_%p.log          # 致命错误日志
-Xlog:gc*:file=gc.log:time,uptime             # GC 日志
-XX:+PrintFlagsFinal                          # 打印所有 JVM 参数最终值
```

**事前调优参数（堆/GC 收集器/JIT 配置）**：归 performance-tuning skill，见 `../performance-tuning/references/jvm_params.md`。

## 使用方式

1. **按症状定位**：先看"故障分类速查"表，跳到对应小节。
2. **执行诊断流程**：每个小节有编号步骤，按顺序执行。
3. **查根因表**：诊断后按"常见根因"表找对策。
4. **命令详解**：看不懂命令输出时 Read `references/diagnostic_commands.md`。
5. **GC 排查**：调 GC 排查时 Read `references/gc_tuning_guide.md`（事后诊断视角）。事前选型与配置看 performance-tuning。
6. **容器排查**：K8s/Docker 容器内故障 Read `references/container_troubleshooting.md`。
7. **JFR 分析**：用 JFR 排查 Read `references/jfr_analysis.md`。
8. **排查案例**：需要端到端参照时 Read `references/troubleshooting_cases.md`。
9. **不确定不要编造**：JVM 行为以源码和官方文档为准，本 skill 不替代正式文档。

## 参考入口

- **命令详解**：`references/diagnostic_commands.md`（每条 jvm 命令的输出字段含义和示例）
- **GC 排查指南**：`references/gc_tuning_guide.md`（事后诊断视角的 GC 问题排查）
- **容器排查**：`references/container_troubleshooting.md`（K8s/Docker 容器内 JVM 故障：OOM killed / PID 1 / JRE-only）
- **JFR 事后分析**：`references/jfr_analysis.md`（JFR 事件速查、在线 streaming、JMC 火焰图）
- **排查案例**：`references/troubleshooting_cases.md`（4 个端到端案例：堆泄漏/CPU 100%/死锁/Metaspace 泄漏）
- **事前性能调优**：`../performance-tuning/SKILL.md`（JVM 参数选型、GC 收集器选型、JIT 调优、JMH 基准测试）
- 官方文档：`https://docs.oracle.com/en/java/javase/17/docs/specs/man/`（JDK 17 工具）
- GC 日志分析：`https://gceasy.io/`
- Heap dump 分析：MAT（`https://eclipse.org/mat/`）
- 项目内编码规范：`../coding-standard/SKILL.md`
