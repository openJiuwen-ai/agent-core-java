# GC 排查指南（事后诊断视角）

本文件补充 SKILL.md 的 GC 排查内容，**只关注事后诊断视角**（GC 已出问题怎么排查）。事前选型与配置（部署前选 GC、调参数）看 `../../performance-tuning/references/gc_tuning.md`。

**两个文件的边界**：
- 本文件：事后排查（Full GC 频繁、GC overhead limit、停顿过长）→ 定位 + 修复
- performance-tuning/gc_tuning.md：事前配置（部署前选 G1/ZGC、调堆）→ 预防

## GC 类型识别

排查第一步：识别当前是哪类 GC 问题。

| GC | 触发条件 | 回收区域 | 停顿时间 | 排查要点 |
|---|---|---|---|---|
| **Young GC / Minor GC** | Eden 满 | Eden + 一个 Survivor | 短（毫秒级） | 频繁 = 对象创建过快或 Eden 太小 |
| **Mixed GC** (G1) | 老年代占用触发 | 新生代 + 老年代部分 region | 中等 | 偶尔正常，频繁说明老年代增长快 |
| **Full GC** | 老年代满 / Metaspace 满 / `System.gc()` | 整个堆 + Metaspace | 长（秒级） | 频繁 Full GC 必有根因 |

**排查目标**：Full GC 越少越好。频繁 Full GC 说明老年代或 Metaspace 有问题。

## 对象进入老年代的途径（排查晋升问题）

排查"老年代增长过快"时看这 4 条途径：

1. **年龄到阈值**：默认 15 次 Young GC 后存活（`-XX:MaxTenuringThreshold`）
2. **大对象**：超过 `-XX:PretenureSizeThreshold` 直接进老年代
3. **Survivor 满了**：Survivor 空间不够时，存活对象直接晋升
4. **动态年龄计算**：JVM 自动判断某年龄对象太多，提前晋升

**排查思路**：如果 Young GC 频繁且 Full GC 也频繁，可能是对象过早晋升 —— 用 `jstat -gcnew <pid> 1000` 看 Survivor 占用，看是否对象没在新生代死掉就晋升了。

## 排查命令速查

```bash
# 看各代占用 + GC 次数（每秒刷新）
jstat -gcutil <pid> 1000

# 看上次 GC 原因
jstat -gccause <pid> 1000

# 看新生代详情（Eden/Survivor 占用、晋升年龄）
jstat -gcnew <pid> 1000

# 看 GC 概况
jcmd <pid> GC.heap_info

# 实时 GC 日志（不写文件）
jcmd <pid> VM.log output='file=/dev/stdout' what='gc*'
```

## 关键指标速查（jstat -gcutil）

用 `jstat -gcutil <pid> 1000` 连续观察，关注：

| 指标 | 健康值 | 异常值 | 异常对策 |
|---|---|---|---|
| O (老年代占用) | < 70% | > 80% 且不回落 | 扩容或查泄漏 |
| YGC 频率 | 几秒一次 | 每秒多次 | 减少对象创建 |
| FGC 频率 | 几小时一次 | 每分钟一次 | 查泄漏或扩容 |
| FGCT/GCT 比例 | < 20% | > 50% | Full GC 占比高，老年代有问题 |
| GCT/运行时间 | < 5% | > 10% | GC 整体开销大 |

**排查要点**：
- O 持续上升不回落 = 内存泄漏（见 SKILL.md OOM 排查）
- FGC 频率高但 O 不高 = 可能是 Metaspace 满 或 `System.gc()` 被调
- YGC 频率高但 FGC 少 = 对象创建过快，新生代不够

## 常见排查场景

### 场景 1：Young GC 频繁

**现象**：`jstat` 看 YGC 增长快，应用响应波动。

**排查**：
- `jstat -gcnew <pid> 1000` 看 Eden 占用，是否秒级满
- 看应用是否高频创建对象（ Profiling 分配火焰图）

**对策**：
- 减少对象创建（缓存、对象池、StringBuilder）
- 调大新生代（事前调优，看 performance-tuning/gc_tuning.md）
- Eden 太小会导致 Young GC 频繁但每次回收少

### 场景 2：Full GC 频繁

**现象**：FGC 增长快，应用长时间停顿。

**排查**（按根因定位）：
- `jstat -gcutil <pid> 1000` 看 O 是否持续上升不回落
- 老年代增长不回落 → 内存泄漏，拿 dump 分析（见 SKILL.md OOM 排查）
- 老年代增长后回落 → 堆不足
- Metaspace 满 → `jmap -clstats <pid>` 看 ClassLoader 是否持续增长（见 SKILL.md 类加载排查）
- 查代码是否有 `System.gc()` 调用

**对策**：
- 内存泄漏 → 拿 dump 找 GC Root 链，定位泄漏点
- 堆不足 → 调大 `-Xmx`
- Metaspace 满 → 调大 `-XX:MaxMetaspaceSize` 或修类加载泄漏
- `System.gc()` 被调 → 加 `-XX:+DisableExplicitGC`

### 场景 3：单次 GC 停顿过长

**现象**：GC 次数正常，但每次 FGCT 很大。

**排查**：
- 看 GC 日志确认是 Young GC 还是 Full GC 停顿长
- `jcmd <pid> GC.heap_info` 看堆大小和 region 配置

**对策**：
- G1 Young GC 停顿长 → Eden 太大，事前调 `MaxGCPauseMillis`（看 performance-tuning）
- G1 Full GC 停顿长 → 老年代 fragmentation，考虑换 ZGC
- 换 ZGC（JDK 17+ stable，事前配置看 performance-tuning/gc_tuning.md）

### 场景 4：大对象直接进老年代

**现象**：Full GC 频繁但老年代对象不多（大对象在老年代碎片化）。

**排查**：
- `jstat -gcnew <pid> 1000` 看 Survivor 是否频繁满
- Profiling 看是否有大数组/大字符串创建

**对策**：
- 找大数组/大字符串创建点，改用流式处理或分片
- 事前调 `-XX:PretenureSizeThreshold`（看 performance-tuning）

### 场景 5：GC overhead limit exceeded

**现象**：`OutOfMemoryError: GC overhead limit exceeded`。

**排查**：这是 GC 花太多时间回收太少内存的兜底机制。通常是内存泄漏或堆太小。

**对策**：
- 先拿 dump（`-XX:+HeapDumpOnOutOfMemoryError` 已开的话有 dump）
- MAT 分析找泄漏点
- 不是泄漏 → 堆太小，调大 `-Xmx`
- 临时关闭此检查：`-XX:-UseGCOverheadLimit`（治标不治本，不推荐）

## GC 日志分析

### JDK 17 日志格式

JDK 17 统一日志语法（`-Xlog:gc*`），日志示例：
```
[2023-12-01T10:00:00.000+0800] GC(0) Pause Young (Normal) (G1 Evacuation Pause)
[2023-12-01T10:00:00.050+0800] GC(0) 23M->10M(64M)(50.000ms) ...
```

**关键字段**：
- `GC(N)`：第 N 次 GC
- `Pause Young / Pause Full`：GC 类型
- `23M->10M(64M)`：堆前后占用（总大小）
- `(50.000ms)`：停顿时间

### 分析工具

- **GCViewer**（开源）：`https://github.com/chewiebug/GCViewer`
- **gceasy.io**（在线）：上传 GC 日志自动分析
- **JFR**：`jdk.GarbageCollection` 事件（看 performance-tuning/jmh_profiling.md）

## 参考入口

- **SKILL.md**：本 skill 主入口，按症状跳转（OOM/CPU 100%/高频 GC 等）
- **诊断命令详解**：`diagnostic_commands.md`（jstat/jmap/jcmd 输出字段含义）
- **排查案例**：`troubleshooting_cases.md`（4 个端到端案例）
- **事前 GC 配置**：`../../performance-tuning/references/gc_tuning.md`（部署前选 G1/ZGC、调堆、生产参数配置）
- **JVM 参数详解**：`../../performance-tuning/references/jvm_params.md`（JDK 17 默认参数表、各参数取值）
- 官方 GC 调优：`https://docs.oracle.com/en/java/javase/17/gctuning/`
- GC 日志分析：`https://gceasy.io/`
