# JFR 事后分析指南

本文件补充 SKILL.md，专讲 **JDK Flight Recorder（JFR）事后分析**。JDK 17 JFR 开销 < 1%，是事后排查主力工具，替代传统 jstack/jmap 老路。用户报故障需用 JFR 排查、或想看 JFR 事件定位根因时按需读取。

## JFR vs 传统工具

| 维度 | jstack / jmap | JFR |
|---|---|---|
| 开销 | 中（每次执行影响应用） | 极低（< 1%，可常开） |
| 数据 | 单时刻快照 | 持续时间窗口事件 |
| 在线 streaming | 不支持 | 支持（JDK 14+） |
| 火焰图 | 需额外处理 | 内建 |
| 适合 | 单次诊断 | 长跑监控 + 事后分析 |
| 容器友好 | 需 JDK + SYS_PTRACE | 启动时配即用 |

**结论**：JDK 17 优先 JFR，jstack/jmap 作辅助。

## JFR 事件分类

JFR 事件按类别：

| 类别 | 代表事件 | 用途 |
|---|---|---|
| **GC** | `jdk.GarbageCollection` | 排查 GC 频繁 / 停顿 |
| **内存** | `jdk.ObjectAllocationSample` / `jdk.GCHeapSummary` | 排查内存分配热点 |
| **锁** | `jdk.JavaMonitorWait` / `jdk.JavaMonitorEnter` | 排查锁竞争 / 死锁 |
| **线程** | `jdk.ThreadStart` / `jdk.ThreadPark` | 排查线程泄漏 / 阻塞 |
| **CPU** | `jdk.ExecutionSample` | 火焰图源数据 |
| **类加载** | `jdk.ClassLoad` / `jdk.ClassUnload` | 排查 ClassLoader 泄漏 |
| **IO** | `jdk.FileRead` / `jdk.SocketRead` | 排查 IO 阻塞 |
| **异常** | `jdk.JavaExceptionThrow` | 排查异常风暴 |
| **JVM** | `jdk.JVMInformation` / `jdk.OSInformation` | 环境 |

## 故障别看哪些事件

### OOM 前看哪些事件

OOM 发生前启动 JFR，事后分析：

| 事件 | 看什么 |
|---|---|
| `jdk.ObjectAllocationSample` | 谁在分配大量对象（找热点） |
| `jdk.GCHeapSummary` | 堆增长曲线（什么时候开始涨） |
| `jdk.GarbageCollection` | GC 频率 + 回收率（回收不掉 = 泄漏） |
| `jdk.JavaExceptionThrow` | 是否大量 OOM 异常前兆 |

**分析步骤**：
1. JMC 打开 `.jfr` 文件
2. 看 GC Heap 趋势 → 什么时候开始不回落
3. 看 Object Allocation Sample → 哪个类分配最多
4. 看时间线关联 → 哪段业务流量引起

### CPU 100% 看哪些事件

| 事件 | 看什么 |
|---|---|
| `jdk.ExecutionSample` | 火焰图，哪个方法占 CPU 最多 |
| `jdk.CPULoad` | CPU 负载趋势 |
| `jdk.ThreadCPULoad` | 哪个线程占 CPU |

**分析步骤**：
1. JMC 火焰图找最宽方法
2. 看是否 GC 线程占 CPU（`GC Thread` 占多 → 实际是 GC 问题）
3. 看是否业务线程死循环（栈固定）

### 死锁前看哪些事件

| 事件 | 看什么 |
|---|---|
| `jdk.JavaMonitorEnter` | 锁等待时长 + 哪把锁 |
| `jdk.JavaMonitorWait` | `wait()` 等待时长 |
| `jdk.ThreadPark` | 线程 park 阻塞 |
| `jdk.ExecutionSample` | 线程栈（看卡在哪） |

**注意**：JFR 不自动检测死锁。死锁看 `jcmd <pid> Thread.print`，看是否 `Found Java deadlock`。

**JFR 看死锁前兆**：
1. 看 `JavaMonitorEnter` 等待时长分布 → P99 等待长 = 锁竞争激烈
2. 看时间线 → 哪段开始等待激增
3. 关联 `ExecutionSample` → 线程在什么方法等锁

### 高频 GC 看哪些事件

| 事件 | 看什么 |
|---|---|
| `jdk.GarbageCollection` | GC 次数 + 耗时 + cause |
| `jdk.GCHeapSummary` | 堆前后占用 + pause 时间 |
| `jdk.G1HeapRegionInformation`（G1） | region 占用 |
| `jdk.ObjectAllocationSample` | 分配热点 |

**分析步骤**：
1. 看 GC 频率 + cause（`Allocation Rate` / `System.gc()` / `Metaspace`）
2. 看 Young GC vs Full GC 比例
3. 看 GC 后老年代是否回落（不回落 = 泄漏）
4. 关联分配采样 → 找分配热点

### 类加载泄漏看哪些事件

| 事件 | 看什么 |
|---|---|
| `jdk.ClassLoad` | 加载的类数 + ClassLoader |
| `jdk.ClassLoaderStatistics` | ClassLoader 数量 + 占用 |

**分析步骤**：
1. 看 ClassLoader 数量趋势
2. 看哪类 ClassLoader 持续增长
3. 关联 `ClassLoad` 事件看加载什么类

## JFR 启动与采集

### 启动时配置常开

生产推荐：启动时常开 JFR，事后随时有数据。

```
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile
-XX:FlightRecorderOptions=stackdepth=64
```

**参数说明**：
- `maxage=1h`：滚动，1 小时数据
- `maxsize=100M`：文件上限
- `settings=profile`：高采样率（vs `default` 更省资源）
- `stackdepth=64`：栈深度（默认 64，复杂应用调 128）

### 临时采集 30 秒

事后排查常用：

```bash
# 启动 30 秒采集
jcmd <pid> JFR.start duration=30s filename=/tmp/recording.jfr

# 容器内（PID 1）
jcmd 1 JFR.start duration=30s filename=/tmp/recording.jfr
```

### 持续后台采集

```bash
# 后台启动，持续到停止
jcmd <pid> JFR.start filename=/tmp/cont.jfr maxage=1h maxsize=100M settings=profile

# 看正在跑的 recording
jcmd <pid> JFR.check

# 停止并 dump 到文件
jcmd <pid> JFR.stop name=1 filename=/tmp/final.jfr
```

### 在线 streaming（JDK 14+）

无需落盘，应用内消费 JFR 事件：

```java
import jdk.jfr.consumer.EventStream;

try (EventStream stream = EventStream.openRepository()) {
    // 实时监控 GC
    stream.onEvent("jdk.GarbageCollection", e -> {
        System.out.println("GC: " + e.getDuration("duration") + "ms");
        if (e.getDuration("duration").toMillis() > 100) {
            alertService.notify("Long GC detected!");
        }
    });

    // 实时监控锁等待
    stream.onEvent("jdk.JavaMonitorEnter", e -> {
        if (e.getDuration("duration").toMillis() > 50) {
            log.warn("Long lock wait: " + e.getDuration("duration"));
        }
    });

    stream.startAsync();
    Thread.sleep(Long.MAX_VALUE);
}
```

**应用场景**：
- 应用内嵌实时监控告警
- APM 厂商集成
- 不依赖外部工具

## JMC（JDK Mission Control）分析

官方 GUI 工具，看 JFR 文件。

### 安装

下载：`https://github.com/openjdk/jmc`

### 主要视图

1. **概览**：CPU/内存/GC/线程 概况
2. **GC 配置 / 暂停**：GC 频率、停顿时间分布
3. **内存**：堆占用趋势、分配热点
4. **线程**：线程状态分布、锁等待
5. **代码**：方法 CPU 采样
6. **I/O**：文件 / 网络 IO
7. **系统**：JVM 参数、环境

### JMC 火焰图

JDK 14+ JMC 支持火焰图：
1. 打开 `.jfr` 文件
2. 选 "Method Profile" / "Memory" 视图
3. 点火焰图按钮

**读图**：
- 横轴：调用栈展开
- 纵轴：栈深度
- 宽度：CPU/内存时间占比
- 找最宽的"平顶" → 优化目标

## 命令行 JFR 分析

不装 JMC 也能看 JFR 内容：

```bash
# 看所有事件（默认前 10）
jfr print recording.jfr

# 看特定事件
jfr print --events jdk.GarbageCollection recording.jfr

# 看特定事件 + JSON 格式
jfr print --events jdk.JavaMonitorEnter --json recording.jfr

# 看汇总统计
jfr summary recording.jfr

# 按类别
jfr print --categories "GC" recording.jfr
```

## JFR vs async-profiler

| 维度 | JFR | async-profiler |
|---|---|---|
| 来源 | Oracle / OpenJDK 官方 | 社区开源 |
| 集成 | JVM 内建 | 外部 agent |
| 火焰图 | JMC 内建 / `jfr print` | 直接生成 HTML |
| 事件类型 | 多（GC/锁/IO/类加载...） | 少（CPU/Alloc/Lock） |
| 在线 streaming | 支持（JDK 14+） | 不支持 |
| 生产常开 | 推荐 | 可以 |
| 易用性 | GUI（JMC）+ CLI | CLI + 火焰图 |

**选**：
- 全局排查、长跑监控 → JFR
- 快速火焰图、找 CPU/Alloc 热点 → async-profiler
- 生产环境 → JFR 启动时常开 + async-profiler 临时采样

## 实战分析流程

### 流程 1：OOM 事后分析

1. **启动时常开 JFR**：`-XX:StartFlightRecording=...`
2. **OOM 触发**：JVM 不退出（`HeapDumpOnOutOfMemoryError` 拿 dump）
3. **事后看 JFR**：
   - `jfr print --events jdk.GCHeapSummary recording.jfr` 看堆增长曲线
   - `jfr print --events jdk.ObjectAllocationSample recording.jfr` 看分配热点
   - JMC 打开看时间线关联
4. **拿 dump 用 MAT 分析**：找 GC Root 链定位泄漏点
5. **结合 JFR + dump**：dump 看泄漏点，JFR 看什么时候开始泄漏

### 流程 2：CPU 100% 排查

1. **启动 30 秒 JFR**：`jcmd <pid> JFR.start duration=30s filename=/tmp/cpu.jfr`
2. **JMC 火焰图**：找最宽方法
3. **看是否 GC 占 CPU**：`jfr print --events jdk.GarbageCollection recording.jfr`
4. **看线程 CPU 分布**：`jdk.ThreadCPULoad` 事件
5. **结合 jstack**：`jcmd <pid> Thread.print` 看死循环栈

### 流程 3：死锁 / 锁竞争排查

1. **启动 30 秒 JFR**
2. **看锁等待**：`jfr print --events jdk.JavaMonitorEnter recording.jfr`
3. **按等待时长排序**：找最长的锁等待
4. **关联线程栈**：`jdk.ExecutionSample` 看线程在什么方法等锁
5. **结合 jstack**：`jcmd <pid> Thread.print` 看是否 `Found Java deadlock`

### 流程 4：高频 GC 排查

1. **启动 30 秒 JFR**
2. **看 GC 事件**：`jfr print --events jdk.GarbageCollection recording.jfr`
3. **看 cause 字段**：是 `Allocation Rate` 还是 `System.gc()` 还是 `Metaspace`
4. **看堆前后占用**：`jdk.GCHeapSummary` 看老年代是否回落
5. **看分配热点**：`jdk.ObjectAllocationSample` 找谁在分配
6. **决策**：
   - 老年代不回落 → 内存泄漏，拿 dump
   - 分配过快 → 优化代码减少分配
   - `System.gc()` → 加 `-XX:+DisableExplicitGC`

## JFR 配置模板

### 临时排查（30 秒）

```
-XX:StartFlightRecording=duration=30s,filename=/tmp/r.jfr,settings=profile
```

### 生产常开

```
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=1h,maxsize=100M,settings=profile,disk=true
-XX:FlightRecorderOptions=stackdepth=64
```

### 低开销监控

```
-XX:StartFlightRecording=filename=/var/log/jfr/app.jfr,maxage=24h,maxsize=500M,settings=default
-XX:FlightRecorderOptions=stackdepth=32
```

`settings=default` 比 `profile` 采样率低，开销更小，适合长期监控。

## 参考入口

- **SKILL.md**：本 skill 主入口，按症状跳转
- **诊断命令详解**：`diagnostic_commands.md`（jcmd/jstack/jmap 输出字段）
- **GC 排查**：`gc_tuning_guide.md`（事后诊断视角）
- **容器排查**：`container_troubleshooting.md`（容器内 JFR 落盘配置）
- **事前 JMH + JFR**：`../../performance-tuning/references/jmh_profiling.md`（JMH 基准 + JFR 生产常开）
- 官方 JFR 文档：`https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html#starting-a-recording-on-a-running-java-application`
- JMC 下载：`https://github.com/openjdk/jmc`
- JFR 在线 streaming：`https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/consumer/EventStream.html`
