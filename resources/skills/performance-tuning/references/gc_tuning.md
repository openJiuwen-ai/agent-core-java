# GC 调优指南（预防式，JDK 17 baseline）

本文件补充 SKILL.md 的 GC 调优内容，**以 JDK 17 为基线**，重点在**事前配置**（怎么选 GC、调参数避免出问题）。事后排查（GC 已经频繁了怎么诊断）看 `../../jvm-troubleshoot/references/gc_tuning_guide.md`。

**两个 skill 的边界**：
- jvm-troubleshoot/gc_tuning_guide：事后排查（Full GC 频繁、GC overhead limit）→ 定位 + 修复
- 本文件：事前配置（部署前选 GC、调参数）→ 预防

## 堆内存分代回顾

```
┌─────────────────────────────────────────────────┐
│                        堆 (Heap)                │
├──────────────────────────┬──────────────────────┤
│       新生代 (Young)      │   老年代 (Old)        │
│  ┌──────┬──────┬──────┐  │                      │
│  │ Eden │ S0   │ S1   │  │                      │
│  └──────┴──────┴──────┘  │                      │
└──────────────────────────┴──────────────────────┘
              ┌──────────────────┐
              │ Metaspace (非堆)  │  类元数据
              └──────────────────┘
```

**调优核心思路**：
- 让对象尽量在新生代被回收（朝生夕死）
- 避免过早晋升到老年代
- 减少 Full GC（Full GC = STW 长停顿）

## JDK 17 GC 收集器现状

JDK 17 stable 的收集器：

| 收集器 | 状态 | 算法 | 停顿 | 适用 |
|---|---|---|---|---|
| **G1** | 默认 | 分区 + 标记整理 | 短可控（200ms 目标） | 通用、中等堆 |
| **ZGC** | stable（JDK 15+） | 染色指针 + 读屏障 | 亚毫秒 | 大堆、低延迟 |
| **Shenandoah** | stable（JDK 15+） | Brooks pointer + 并发整理 | 亚毫秒 | 大堆、低延迟 |
| **Parallel** | stable | 多线程并行 | 中 | 吞吐量优先 |
| **Serial** | stable | 单线程 | 长 | 客户端、小应用 |
| **CMS** | **已移除**（JDK 14） | - | - | 升级到 G1 |

**JDK 17 关键点**：
- ZGC 是**非分代版**（generational ZGC 在 JDK 21+ 才 stable）。非分代版吞吐量受影响，超大堆场景考虑升级 JDK 21
- Shenandoah 在 OpenJDK 构建 stable；Oracle JDK 不含 Shenandoah
- G1 仍是默认，多数场景够用

## GC 收集器选型（事前决策）

### 按场景选

| 场景 | 堆大小 | 停顿要求 | 推荐收集器 | 关键参数 |
|---|---|---|---|---|
| 微服务 / 默认 | < 4GB | 200ms 内 | G1 | `-XX:MaxGCPauseMillis=200` |
| 中型应用 | 4-8GB | 200ms 内 | G1 | `-XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=8m` |
| 大型低延迟 | 8-32GB | 50ms 内 | ZGC | `-XX:+UseZGC -XX:ZAllocationSpikeTolerance=2` |
| 超大堆 | 32GB-16TB | 亚毫秒 | ZGC | `-XX:+UseZGC` |
| 高吞吐批处理 | 任意 | 无停顿要求 | Parallel | `-XX:+UseParallelGC -XX:ParallelGCThreads=N` |
| 低停顿 + 并发标记 | 中型 | 100ms 内 | Shenandoah（OpenJDK） | `-XX:+UseShenandoahGC` |

### 选型决策树

1. JDK 17 + 堆 ≥ 8GB + 低延迟 → **ZGC**
2. JDK 17 + 堆 < 8GB → **G1**（默认）
3. 吞吐量第一（离线计算） → **Parallel**
4. 堆 > 32GB + 对吞吐敏感 → 升级 JDK 21 用分代 ZGC

**不要做**：
- ❌ 堆 < 4GB 用 ZGC → 没收益，反而慢
- ❌ 没测就换收集器 → 可能更慢
- ❌ 调 `-XX:MaxGCPauseMillis=1` → 多数情况达不到，反而频繁 GC

## G1 调优

G1 是 JDK 17 默认收集器，多数场景不需要手动调。

### G1 工作原理

- 堆划分为 region（1MB-32MB，默认按堆大小自适应）
- 新生代 = 一组 region（动态调整）
- 老年代 = 一组 region
- 大对象 = humongous region（对象 > region 50%）
- Mixed GC：选择性回收老年代 region

### G1 关键参数

| 参数 | 默认 | 调优建议 |
|---|---|---|
| `-XX:MaxGCPauseMillis` | 200 | 实际 SLA 停顿 × 0.8 |
| `-XX:G1HeapRegionSize` | 自适应 | 显式设 8m-32m |
| `-XX:InitiatingHeapOccupancyPercent` | 45 | 老年代占用率触发并发标记，调低触发早 |
| `-XX:G1NewSizePercent` | 5 | 新生代下限 |
| `-XX:G1MaxNewSizePercent` | 60 | 新生代上限 |
| `-XX:G1MixedGCCountTarget` | 8 | Mixed GC 分多少次完成 |
| `-XX:G1MixedGCLiveThresholdPercent` | 85 | region 活对象占比 > 此值不回收 |

### G1 调优场景

**场景 1：Full GC 频繁**

原因：老年代增长太快，Mixed GC 来不及回收。

调优：
- 调低 `-XX:InitiatingHeapOccupancyPercent=35`（提前触发并发标记）
- 调高 `-XX:G1MixedGCCountTarget=16`（Mixed GC 分更多次）
- 调高 `-XX:G1MixedGCLiveThresholdPercent=90`（更多 region 可回收）

**场景 2：Young GC 停顿长**

原因：Eden 太大，单次回收久。

调优：
- 调低 `-XX:MaxGCPauseMillis=100`
- 调小 `-XX:G1MaxNewSizePercent=40`

**场景 3：humongous 分配频繁**

原因：大对象多，占 humongous region，Mixed GC 才回收。

调优：
- 调大 `-XX:G1HeapRegionSize=16m` 或 `32m`（减少 humongous）
- 应用层：减少大对象分配，或对象池化

## ZGC 调优（JDK 17 非分代版）

JDK 15+ production-ready。JDK 17 是**非分代版**，停顿不随堆增长（亚毫秒级），适合大堆。

### ZGC 工作原理

- 染色指针（多视图映射）
- 读屏障（应用线程并发标记）
- 几乎全程并发，STW 只有几次短停顿（初始标记、再标记、再分配）

### JDK 17 vs JDK 21+ ZGC

| 维度 | JDK 17（非分代） | JDK 21+（分代） |
|---|---|---|
| 算法 | 整堆并发 | 分代 + 并发 |
| 吞吐量 | 略低（扫描整堆） | 更高（只扫新生代） |
| 停顿 | 亚毫秒 | 亚毫秒 |
| 堆大小 | 任意 | 任意 |

**建议**：堆 > 32GB + 对吞吐敏感 → 升级 JDK 21 用分代 ZGC。否则 JDK 17 非分代 ZGC 够用。

### ZGC 关键参数

| 参数 | 默认 | 说明 |
|---|---|---|
| `-XX:ZAllocationSpikeTolerance` | 2 | 分配尖峰容忍度 |
| `-XX:ConcGCThreads` | ParallelGCThreads / 4 | 并发 GC 线程数 |
| `-XX:ZUncommitDelay` | 300 | 未提交内存回收延迟（秒） |

### ZGC 调优场景

**并发标记占 CPU**：
- 调高 `-XX:ConcGCThreads`（增加并发 GC 线程数）
- 但不能超过物理核数一半，否则应用 CPU 受挤压

**分配尖峰导致 STW**：
- 调高 `-XX:ZAllocationSpikeTolerance=3`

## Parallel GC 调优

吞吐量优先的收集器。离线计算常用。

### Parallel 关键参数

| 参数 | 默认 | 说明 |
|---|---|---|
| `-XX:ParallelGCThreads` | CPU 核 5/8 | GC 线程数 |
| `-XX:MaxGCPauseMillis` | 无上限 | 目标停顿（不保证） |
| `-XX:GCTimeRatio` | 99 | GC 时间占比 = 1/(1+N) → 1% |
| `-XX:UseParallelOldGC` | JDK 17 默认 | 老年代并行收集 |

### Parallel 调优场景

**吞吐量优先**：
- 不设 `-XX:MaxGCPauseMillis`（让 GC 自由发挥）
- 调大新生代：`-XX:NewRatio=1`（新生代 = 老年代一半）

**降低 GC 频率**：
- 调大堆
- 调大新生代
- 调大 `-XX:GCTimeRatio=99`（GC 时间占比允许 1%）

## 通用 GC 调优原则

### 原则 1：先调应用，再调 GC

GC 调优收益有限。先把应用层优化好（算法、数据结构、分配热点），再调 GC 参数。

### 原则 2：堆不是越大越好

- 大堆 → 单次 GC 时间长（非并发收集器）
- 大堆 → 超过 32GB 关闭指针压缩
- 32GB+ 堆用 ZGC / Shenandoah（并发，停顿不随堆增长）

### 原则 3：避免过早晋升

对象在新生代回收比进老年代好。

- 调大新生代（`-XX:NewRatio=1`）
- 调大 Survivor（`-XX:SurvivorRatio=6`）
- 调大晋升年龄（`-XX:MaxTenuringThreshold=15`）

### 原则 4：监控 GC 日志

GC 日志必开（JDK 17 统一日志语法）：

```
-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=10M
```

定期用 GCViewer / gceasy.io 分析。

### 原则 5：测试验证

GC 参数改动需测试：
- 用 JFR / 容器负载测试跑实际流量
- 对比 GC 日志的吞吐量、停顿、Full GC 频率
- 不达目标不收工

## GC 调优场景速查

| 场景 | 调优 |
|---|---|
| Full GC 频繁 | 调大堆 / 调低 IHOP / 修内存泄漏 |
| Young GC 频繁 | 调大新生代 |
| Young GC 停顿长 | 调小新生代 / 调小 MaxGCPauseMillis |
| GC 吞吐量 > 10% | 调大堆 / 换 GC / 修应用 |
| Metaspace 满 | 调 `-XX:MaxMetaspaceSize` |
| 直接内存满 | 调 `-XX:MaxDirectMemorySize` |

## 生产环境推荐配置

### 微服务（JDK 17，容器）

```
-XX:+UseContainerSupport
-XX:InitialRAMPercentage=50.0
-XX:MaxRAMPercentage=75.0
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/dumps/
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=10,filesize=10M
-XX:+AlwaysPreTouch
-XX:+DisableExplicitGC
```

### 大型低延迟（JDK 17，物理机）

```
-Xms32g -Xmx32g
-XX:+UseZGC                          # JDK 17 非分代 ZGC
-XX:ZAllocationSpikeTolerance=2
-XX:ConcGCThreads=8
-XX:MetaspaceSize=512m
-XX:MaxMetaspaceSize=1g
-XX:ReservedCodeCacheSize=512m
-XX:+HeapDumpOnOutOfMemoryError
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=20,filesize=50M
```

### 高吞吐批处理（JDK 17）

```
-Xms16g -Xmx16g
-XX:+UseParallelGC
-XX:ParallelGCThreads=16
-XX:NewRatio=1
-XX:+AlwaysPreTouch
```

## GC 日志分析（JDK 17 统一语法）

### 关键指标

- **GC 吞吐量**：应用时间 / 总时间（应 > 95%）
- **平均停顿**：所有 GC 停顿平均
- **最大停顿**：单次最长停顿
- **Full GC 频率**：正常几小时一次，几分钟一次有问题
- **老年代占用**：稳定后应能回落，不回落 = 内存泄漏

### 工具

- **GCViewer**（开源）：`https://github.com/chewiebug/GCViewer`
- **gceasy.io**（在线）：上传 GC 日志自动分析
- **JFR**：`jdk.GarbageCollection` 事件

### JDK 17 日志语法

```
-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=10M
```

- `gc*`：所有 GC 相关标签
- `time,uptime`：日志格式（时间戳 + 启动后秒数）
- `filecount=10,filesize=10M`：滚动日志，10 文件 × 10MB

**在线查看 GC 日志 / 实时诊断命令**：归 jvm-troubleshoot skill，见 `../../jvm-troubleshoot/references/diagnostic_commands.md` 和 `gc_tuning_guide.md`（事后排查视角）。

## 参考文档

- 项目内事后排查：`../../jvm-troubleshoot/references/gc_tuning_guide.md`
- 项目内诊断命令：`../../jvm-troubleshoot/references/diagnostic_commands.md`
- Oracle JDK 17 GC 调优：`https://docs.oracle.com/en/java/javase/17/gctuning/`
- G1 调优：`https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-garbage-collector.html`
- ZGC：`https://wiki.openjdk.org/display/zgc`
- ZGC 分代设计（JEP 439）：`https://openjdk.org/jeps/439`
- Shenandoah：`https://wiki.openjdk.org/display/shenandoah`
- GC 日志分析：`https://gceasy.io/`
