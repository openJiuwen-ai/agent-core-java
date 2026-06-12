/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.Map;

/**
 * Mirrors Python's {@code session_tools} in
 * {@code openjiuwen/harness/prompts/sections/session_tools.py}.
 */
public final class SessionToolsSection {

    private static final String DEFAULT_LANGUAGE = "cn";
    private static final int PRIORITY = 85;

    private static final String SESSION_SYSTEM_PROMPT_CN = """
## 浼氳瘽宸ュ叿sessions_spawn 鐢ㄤ簬鍒涘缓涓存椂瀛愪唬鐞嗭紝鐙珛瀹屾垚澶嶆潅浠诲姟
璇存槑:
    - 閮ㄥ垎浼氳瘽鎴栦唬鐮佺被宸ュ叿杩斿洖涓嫢鍚?status 涓?pending锛堟垨绛変环瀛楁锛夛紝琛ㄧず璇锋眰宸插彈鐞嗭紝浠诲姟姝ｅ湪鍚庡彴鎵ц锛屽苟闈炲け璐ャ€?    - 姝ゆ椂涓嶅緱涓恒€屽偓淇冪粨鏋溿€嶆垨銆屼互涓烘湭鎵ц銆嶈€岃繛缁€侀噸澶嶅彂璧风浉鍚屾垨绛変环鐨?function_call锛堢浉鍚屽伐鍏枫€佺浉鍚屾剰鍥俱€佺浉鍚屽叧閿弬鏁帮級銆?浣跨敤鍦烘櫙:
    - 浠诲姟澶嶆潅銆佸姝ラ銆佸彲鐙珛鎵ц
    - 闇€瑕佸苟琛屽鐞嗐€佷笓娉ㄦ帹鐞嗐€佸ぇ閲忎笂涓嬫枃 / Token
    - 闇€瑕佹矙绠卞畨鍏ㄦ墽琛岋紙浠ｇ爜銆佹悳绱€佹牸寮忓寲锛?    - 鍙渶鏈€缁堣緭鍑猴紝涓嶅叧蹇冧腑闂磋繃绋?涓嶄娇鐢ㄥ満鏅?
    - 浠诲姟绠€鍗?    - 闇€瑕佹煡鐪嬩腑闂存楠?    - 鎷嗗垎鏃犳敹鐩娿€佷粎澧炲姞寤惰繜
浣跨敤鍘熷垯:
    - 鐙珛浠诲姟灏介噺骞惰鎵ц
    - 鐢ㄥ瓙浠ｇ悊闅旂澶嶆潅浠诲姟锛屾彁鍗囨晥鐜?    - 鑻ュ伐鍏疯繑鍥炰腑鍚?status 涓?pending锛氱敤绠€鐭嚜鐒惰瑷€璇存槑浠诲姟宸插湪鍚庡彴鎵ц锛岃鐢ㄦ埛绋嶅€欐垨绛夊緟绯荤粺鍚庣画鎺ㄩ€?涓嬩竴杞緭鍏ワ紱涓嶈鍫嗗彔澶氫綑宸ュ叿璋冪敤
    - 浠呭綋鐢ㄦ埛鏄庣‘瑕佹眰閲嶈瘯銆佸彉鏇村弬鏁版垨鍙栨秷鏃讹紝鍐嶅彂璧锋柊鐨?function_call
""";

    private static final String SESSION_SYSTEM_PROMPT_EN = """
## Session tools sessions_spawn is used to create temporary subagents
that handle isolated tasks.

When to use:
- Tasks that are complex, multi-step, and can be executed independently
- Scenarios requiring parallel processing, focused reasoning, or large context/token usage
- Tasks that require sandboxed execution (e.g., code execution, search, formatting)
- When only the final output is needed and intermediate steps are not required

When NOT to use:
- Tasks are simple
- Intermediate steps need to be observed
- Task decomposition provides no benefit and only adds latency

Usage Guidelines:
- Execute independent tasks in parallel whenever possible
- Use sub-agents to isolate complex tasks and improve efficiency
- If the tool response contains a status of pending: use brief, natural language to inform the user
that the task is being executed in the background
and ask them to wait for subsequent system notifications or the next round of input; do not stack redundant tool calls.
- Only initiate a new function call when the user explicitly requests a retry, changes parameters, or cancels the task.
""";

    private static final Map<String, String> SESSION_SYSTEM_PROMPT = Map.of(
            "cn", SESSION_SYSTEM_PROMPT_CN,
            "en", SESSION_SYSTEM_PROMPT_EN
    );

    private SessionToolsSection() {
    }

    public static String buildSessionToolsSystemPrompt(String language) {
        return SESSION_SYSTEM_PROMPT.getOrDefault(language, SESSION_SYSTEM_PROMPT_CN);
    }

    public static PromptSection buildSessionToolsSection(String language) {
        String resolvedLanguage = "en".equals(language) ? "en" : DEFAULT_LANGUAGE;
        return new PromptSection(
                SectionName.SESSION_TOOLS,
                Map.of(resolvedLanguage, buildSessionToolsSystemPrompt(resolvedLanguage)),
                PRIORITY
        );
    }

    public static PromptSection build(String language) {
        return buildSessionToolsSection(language);
    }

    public static PromptSection build() {
        return buildSessionToolsSection(DEFAULT_LANGUAGE);
    }
}
