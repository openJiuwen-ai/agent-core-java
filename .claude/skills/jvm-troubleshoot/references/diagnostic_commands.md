# JVM Diagnostic Command Details

This file supplements the command quick reference in SKILL.md with output field meanings and common parameters for each command. Read as needed when a user asks "how to read the output of a specific command## Process Overview

### `jps -lvm`

Lists all JVM processes for the current user.

**Output example**:
```
12345 com.example.Main -Xmx4g -Dspring.profiles.active=prod
23456 sun.tools.jps.Jps -lvm
```

**Fields**: First column is PID, second column is the main class full name (`-l`), followed by JVM parameters (`-v`) and main method arguments (`-m`).

### `jcmd <pid> VM.flags`

View the JVM startup parameters actually in effect (including defaults).

**Usage**: Confirm whether GC collector, heap size, and tuning parameters are in effect. Add `-all` to see all parameters including defaults.

## Heap Analysis

### `jmap -heap <pid>`

View heap configuration and generation occupancy.

**Key output fields**:
```
Heap Configuration:
   MinHeapFreeRatio  = 40
   MaxHeapFreeRatio  = 70
   MaxHeapSize       = 4294967296 (4096.0MB)  # -Xmx
   NewSize           = 1048576000 (1000.0MB)  # Young generation initial
   MaxNewSize        = 1048576000 (1000.0MB)
   OldSize           = 3246436896 (3096.0MB)  # Old generation

Heap Usage:
PS Young Generation
   Eden Space:       capacity = 870318080 (830.0MB), used = 123456789
   From Space:       capacity = 104857600 (100.0MB), used = 0
   To   Space:       capacity = 104857600 (100.0MB), used = 0
PS Old Generation
   capacity = 3246436896 (3096.0MB), used = 2147483648 (2048.0MB)
```

**Assessment**: When old generation used approaches capacity, need to scale up or investigate leak.

### `jmap -histo:live <pid>`

Object histogram sorted by instance count. `:live` triggers a Full GC before counting, showing only live objects.

**Output example**:
```
 num     #instances         #bytes  class name
   1:        1200000      102400000  [B  (byte array)
   2:         800000       25600000  java.lang.String
   3:         100000       12000000  com.example.UserDTO
```

**Assessment**: An abnormally high instance count for a business class may indicate a leak. Note that `[B` (byte array), `[C` (char array), `[Ljava.lang.Object;` (Object array) tend to have large bytes values and are typically held by String or collections.

### `jmap -dump:format=b,file=heap.hprof <pid>`

Export a full heap dump (hprof format) for analysis with MAT or jvisualvm.

**Key MAT operations**:
- **Leak Suspects Report**: Automatically finds leak points
- **Dominator Tree**: See which objects occupy the most memory
- **GC Root paths**: See why an object was not garbage collected

## Thread Analysis

### `jstack <pid>`

Print call stacks and lock status for all threads.

**Thread state keywords**:
- `RUNNABLE`: Currently executing or waiting for CPU
- `BLOCKED`: Waiting for a synchronized lock
- `WAITING`: `Object.wait()` / `LockSupport.park()` without timeout
- `TIMED_WAITING`: `sleep(ms)` / `wait(ms)` / `parkNanos(ns)`

**Deadlock detection**: jstack automatically detects synchronized deadlocks; the end of the output will show:
```
Found 1 deadlock.
====================
"Thread-1" deadlock waiting to lock <0x000000076b4f01e0>
    held by "Thread-2"
"Thread-2" deadlock waiting to lock <0x000000076b4f0230>
    held by "Thread-1"
```

**Note**: ReentrantLock deadlocks are not automatically detected by jstack; you need to check if the stack is stuck in `AbstractQueuedSynchronizer.acquire`.

### `top -Hp <pid>`

View CPU usage per thread within a process (Linux).

**Output**: TID is in decimal; needs `printf "%x\n" <tid>` to convert to hexadecimal, then use `jstack <pid> | grep <hexadecimal> -A 30` to locate the thread stack.

**macOS alternative**: `Activity Monitor` -> select process -> `Sample Process`, or use `htop -p <pid>`.

## GC Analysis

### `jstat -gcutil <pid> <interval>`

View generation occupancy percentages + GC counts; `<interval>` is the refresh interval in milliseconds.

**Output fields**:
```
  S0     S1     E      O      M     CCS    YGC    YGCT    FGC    FGCT     GCT
  0.00  92.15  45.30  78.50  95.20  91.40  124   3.456    8    2.891    6.347
```

| Column | Meaning | Abnormal Value |
|---|---|---|
| S0/S1 | Survivor 0/1 occupancy % | Always 0 or 100 may be abnormal |
| E | Eden occupancy % | Persistently high -> frequent Young GC |
| O | Old generation occupancy % | Continuously growing without dropping = leak |
| M | Metaspace occupancy % | Continuously growing = class loading leak |
| CCS | Compressed class space occupancy % | Generally follows M |
| YGC/YGCT | Young GC count/total time | Count growing fast = object creation too fast |
| FGC/FGCT | Full GC count/total time | Count growing fast = memory leak or heap insufficient |
| GCT | GC total time | > 5% of runtime requires investigation |

**Usage**: Observe multiple times consecutively (`jstat -gcutil <pid> 1000 10` for 10 readings), check if O and FGC keep growing.

### `jstat -gccause <pid> <interval>`

Same as `-gcutil` plus an additional `LGCC` column (last GC cause) and `GCC` column (current GC cause).

**Common causes**:
- `Allocation Failure`: Eden full, triggers Young GC
- `System.gc()`: Code called `System.gc()`
- `Metadata GC Threshold`: Metaspace insufficient
- `GCLocker Initiated GC`: JNI critical section triggered

## Class Analysis

### `jmap -clstats <pid>`

ClassLoader statistics showing how many classes each ClassLoader has loaded and how many bytes they occupy.

**Assessment**: If the same business class is loaded by multiple ClassLoaders, or ClassLoader count keeps growing, it indicates a hot deployment/dynamic proxy leak.

**Key fields**:
- `class_loader_instances`: ClassLoader instance count
- `total_classes`: Total loaded classes
- `parent_loader`: Parent ClassLoader (view delegation chain)

## System Level

### `iostat -x 1`

Disk IO details, refreshed every second. Check `%util` (disk utilization) and `await` (IO wait time). `%util` persistently > 80% or `await` > 20ms indicates a disk bottleneck.

### `netstat -anp | grep <pid>`

View network connections for a process. Abnormally high `ESTABLISHED` count may indicate a connection leak (connection pool not released).
