# JVM 诊断命令详解

本文件补充 SKILL.md 命令速查里每条命令的输出字段含义和常用参数。用户问"某条命令输出怎么看"时按需读取。

## 进程概览

### `jps -lvm`

列出当前用户的所有 JVM 进程。

**输出示例**：
```
12345 com.example.Main -Xmx4g -Dspring.profiles.active=prod
23456 sun.tools.jps.Jps -lvm
```

**字段**：第一列是 PID，第二列是主类全名（`-l`），后面是传给 JVM 的参数（`-v`）和 main 方法参数（`-m`）。

### `jcmd <pid> VM.flags`

查看 JVM 实际生效的启动参数（含默认值）。

**用途**：确认 GC 收集器、堆大小、调优参数是否生效。加 `-all` 看所有参数含默认值。

## 堆分析

### `jmap -heap <pid>`

查看堆配置和各代占用。

**关键输出字段**：
```
Heap Configuration:
   MinHeapFreeRatio  = 40
   MaxHeapFreeRatio  = 70
   MaxHeapSize       = 4294967296 (4096.0MB)  # -Xmx
   NewSize           = 1048576000 (1000.0MB)  # 新生代初始
   MaxNewSize        = 1048576000 (1000.0MB)
   OldSize           = 3246436896 (3096.0MB)  # 老年代

Heap Usage:
PS Young Generation
   Eden Space:       capacity = 870318080 (830.0MB), used = 123456789
   From Space:       capacity = 104857600 (100.0MB), used = 0
   To   Space:       capacity = 104857600 (100.0MB), used = 0
PS Old Generation
   capacity = 3246436896 (3096.0MB), used = 2147483648 (2048.0MB)
```

**判断**：老年代 used 接近 capacity 时要扩容或排查泄漏。

### `jmap -histo:live <pid>`

按实例数排序的对象直方图。`:live` 触发一次 Full GC 后再统计，只看存活对象。

**输出示例**：
```
 num     #instances         #bytes  class name
   1:        1200000      102400000  [B  (byte 数组)
   2:         800000       25600000  java.lang.String
   3:         100000       12000000  com.example.UserDTO
```

**判断**：某个业务类实例数异常多 → 可能泄漏。注意 `[B`（byte 数组）、`[C`（char 数组）、`[Ljava.lang.Object;`（Object 数组）的 bytes 大，通常是被 String 或集合持有。

### `jmap -dump:format=b,file=heap.hprof <pid>`

导出完整 heap dump（hprof 格式），用 MAT 或 jvisualvm 打开分析。

**MAT 关键操作**：
- **Leak Suspects Report**：自动找泄漏点
- **Dominator Tree**：看哪些对象占内存最大
- **GC Root paths**：看对象为什么没被回收

## 线程分析

### `jstack <pid>`

打印所有线程的调用栈和锁状态。

**线程状态关键字**：
- `RUNNABLE`：正在执行或等待 CPU
- `BLOCKED`：等 synchronized 锁
- `WAITING`：`Object.wait()` / `LockSupport.park()` 无超时
- `TIMED_WAITING`：`sleep(ms)` / `wait(ms)` / `parkNanos(ns)`

**死锁检测**：jstack 自动检测 synchronized 死锁，输出末尾会有：
```
Found 1 deadlock.
====================
"Thread-1" deadlock waiting to lock <0x000000076b4f01e0>
    held by "Thread-2"
"Thread-2" deadlock waiting to lock <0x000000076b4f0230>
    held by "Thread-1"
```

**注意**：ReentrantLock 死锁 jstack 不自动检测，需要看栈是否卡在 `AbstractQueuedSynchronizer.acquire`。

### `top -Hp <pid>`

查看进程内各线程的 CPU 占用（Linux）。

**输出**：TID 是十进制，需要 `printf "%x\n" <tid>` 转十六进制，再用 `jstack <pid> | grep <十六进制> -A 30` 定位线程栈。

**macOS 替代**：`Activity Monitor` → 选中进程 → `Sample Process`，或用 `htop -p <pid>`。

## GC 分析

### `jstat -gcutil <pid> <interval>`

查看各代占用百分比 + GC 次数，`<interval>` 是刷新间隔（毫秒）。

**输出字段**：
```
  S0     S1     E      O      M     CCS    YGC    YGCT    FGC    FGCT     GCT
  0.00  92.15  45.30  78.50  95.20  91.40  124   3.456    8    2.891    6.347
```

| 列 | 含义 | 异常值 |
|---|---|---|
| S0/S1 | Survivor 0/1 占用 % | 一直在 0 或 100 可能不正常 |
| E | Eden 占用 % | 持续高 → Young GC 频繁 |
| O | 老年代占用 % | 持续增长不回落 = 泄漏 |
| M | Metaspace 占用 % | 持续增长 = 类加载泄漏 |
| CCS | 压缩类空间占用 % | 一般跟随 M |
| YGC/YGCT | Young GC 次数/总耗时 | 次数增长快 = 对象创建过快 |
| FGC/FGCT | Full GC 次数/总耗时 | 次数增长快 = 内存泄漏或堆不足 |
| GCT | GC 总耗时 | 占运行时间 > 5% 要排查 |

**用法**：连续观察多次（`jstat -gcutil <pid> 1000 10` 看十次），看 O 和 FGC 是否持续增长。

### `jstat -gccause <pid> <interval>`

比 `-gcutil` 多一列 `LGCC`（上次 GC 原因）和 `GCC`（当前 GC 原因）。

**常见原因**：
- `Allocation Failure`：Eden 满，触发 Young GC
- `System.gc()`：代码调了 `System.gc()`
- `Metadata GC Threshold`：Metaspace 不足
- `GCLocker Initiated GC`：JNI 临界区触发

## 类分析

### `jmap -clstats <pid>`

ClassLoader 统计，看每个 ClassLoader 加载了多少类、占用多少 bytes。

**判断**：如果同一个业务类被多个 ClassLoader 加载，或 ClassLoader 数量持续增长，是热部署/动态代理泄漏。

**关键字段**：
- `class_loader_instances`：ClassLoader 实例数
- `total_classes`：加载的类总数
- `parent_loader`：父 ClassLoader（看双亲委派链）

## 系统级

### `iostat -x 1`

磁盘 IO 详情，每秒刷新。看 `%util`（磁盘利用率）和 `await`（IO 等待时间）。`%util` 持续 > 80% 或 `await` > 20ms 说明磁盘瓶颈。

### `netstat -anp | grep <pid>`

看进程的网络连接。`ESTABLISHED` 数量异常多可能是连接泄漏（连接池没释放）。
