/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code BashMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/bash.py}.
 */
public final class BashMetadataProvider implements ToolMetadataProvider {

    private static final String DESCRIPTION_CN = """
            鎵ц Shell 鍛戒护骞惰繑鍥炶緭鍑恒€?

            宸ヤ綔鐩綍鍦ㄥ懡浠や箣闂翠繚鎸佷笉鍙橈紝浣?Shell 鐘舵€侊紙鍙橀噺銆佸嚱鏁般€乤lias锛変笉淇濈暀銆?
            Shell 鐜浠庣敤鎴风殑 profile锛坆ash 鎴?zsh锛夊垵濮嬪寲銆?

            Windows 娉ㄦ剰锛歚cmd`/PowerShell 鑷甫 `mkdir` **涓嶆敮鎸?`-p`**锛屼笉瑕佸湪 cmd/PowerShell 涓娇鐢?`mkdir -p`銆?
            鍙湁杩愯鐜淇℃伅鏄剧ず Git Bash 鎴栭潪 WSL stub 鐨?PATH bash 鍙敤锛屽苟涓斿疄闄呬娇鐢?bash/Git Bash 鏃讹紝POSIX `mkdir -p` 鎵嶉€傜敤銆?
            鍚﹀垯搴斾娇鐢?PowerShell `New-Item ... -Force` 鎴?cmd 閫愮骇 `mkdir`銆?

            閲嶈锛氶伩鍏嶄娇鐢ㄦ湰宸ュ叿鎵ц `find`銆乣grep`銆乣cat`銆乣head`銆乣tail`銆?
            `sed`銆乣awk` 鎴?`echo` 鍛戒护锛岄櫎闈炴槑纭寚绀烘垨纭涓撶敤宸ュ叿鏃犳硶瀹屾垚浠诲姟銆?
            璇蜂娇鐢ㄥ搴旂殑涓撶敤宸ュ叿锛屼互鑾峰緱鏇村ソ鐨勪綋楠岋細

             - 鏂囦欢鎼滅储锛氫娇鐢?glob 宸ュ叿锛堜笉瑕佺敤 find 鎴?ls锛?
             - 鍐呭鎼滅储锛氫娇鐢?grep 宸ュ叿锛堜笉瑕佺敤 grep 鎴?rg 鍛戒护锛?
             - 璇诲彇鏂囦欢锛氫娇鐢?read_file 宸ュ叿锛堜笉瑕佺敤 cat/head/tail锛?
             - 缂栬緫鏂囦欢锛氫娇鐢?edit_file 宸ュ叿锛堜笉瑕佺敤 sed/awk锛?
             - 鍐欏叆鏂囦欢锛氫娇鐢?write_file 宸ュ叿锛堜笉瑕佺敤 echo > 鎴?cat <<EOF锛?
             - 杈撳嚭鏂囨湰锛氱洿鎺ヨ緭鍑猴紙涓嶈鐢?echo/printf锛?
            铏界劧 bash 宸ュ叿涔熻兘鍋氬埌锛屼絾涓撶敤宸ュ叿鎻愪緵鏇村ソ鐨勭敤鎴蜂綋楠岋紝骞朵笖鏇存柟渚垮鏌ュ拰鎺堟潈銆?

            # 浣跨敤璇存槑
             - 鍒涘缓鏂囦欢鎴栫洰褰曞墠锛屽厛鐢ㄦ湰宸ュ叿鎵ц `ls` 纭鐖剁洰褰曞瓨鍦ㄤ笖浣嶇疆姝ｇ‘
             - 璺緞鍖呭惈绌烘牸鏃跺繀椤荤敤鍙屽紩鍙锋嫭璧凤紙渚嬪 `cd "path with spaces/file.txt"`锛?
             - 灏介噺浣跨敤缁濆璺緞缁存寔褰撳墠宸ヤ綔鐩綍锛岄伩鍏嶄娇鐢?`cd`锛岄櫎闈炵敤鎴锋槑纭姹?
             - 鍙€氳繃 timeout 鍙傛暟鎸囧畾瓒呮椂锛堢锛夛紝榛樿 300 绉掞紝涓婇檺 3600 绉?
             - 鍙皢 run_in_background 璁句负 true 鏉ュ悗鍙拌繍琛屽懡浠ゃ€備粎鍦ㄤ笉闇€瑕佺珛鍗宠幏鍙栫粨鏋滄椂浣跨敤锛屽懡浠ゅ畬鎴愬悗浼氭敹鍒伴€氱煡锛屾棤闇€鍦ㄥ懡浠ゆ湯灏惧姞 `&`
             - 鍙戝嚭澶氭潯鍛戒护鏃讹細鐙珛鍛戒护鍦ㄥ悓涓€娑堟伅涓娆″苟琛岃皟鐢ㄦ湰宸ュ叿锛涗緷璧栧懡浠ゅ湪鍗曟璋冪敤涓敤 `&&` 涓茶仈锛涗粎鍦ㄤ笉鍏冲績鍓嶅簭鍛戒护鏄惁澶辫触鏃朵娇鐢?`;`
             - Git 鍛戒护瑙勮寖锛氫紭鍏堝垱寤烘柊鎻愪氦鑰岄潪淇敼宸叉湁鎻愪氦锛涙墽琛?`git reset --hard`銆乬it push --force`銆乬it checkout --` 绛夌牬鍧忔€ф搷浣滃墠鍏堣€冭檻鏇村畨鍏ㄦ浛浠ｆ柟妗堬紱闄ら潪鐢ㄦ埛鏄庣‘瑕佹眰锛屼笉瑕佽烦杩?hooks 鎴栫粫杩囩鍚?
             - 鐢ㄦ埛鍒嗕韩浠ｇ爜浠?URL 璁╀綘銆岀湅鐪嬭繖涓粨搴撱€嶆垨銆屽垎鏋愪竴涓嬨€嶆椂锛岄閫夊姩浣滄槸 `git clone <url> <鏈湴璺緞>`锛涘厠闅嗗畬鍐嶇敤 read_file/grep/glob 绛変笓鐢ㄥ伐鍏疯婧愮爜
             - 閬垮厤涓嶅繀瑕佺殑 `sleep` 鍛戒护锛氶暱浠诲姟浼樺厛鐢?`run_in_background: true`锛岄渶瑕?sleep 鏃朵繚鎸?1-5 绉掔煭绛夊緟
            """.stripTrailing();

    private static final String DESCRIPTION_EN = """
            Executes a given bash command and returns its output.

            The working directory persists between commands, but shell state (variables, functions, aliases) does not. The shell environment is initialized from the user's profile (bash or zsh).

            Windows note: `cmd`/PowerShell `mkdir` **does not support `-p`**; do not use `mkdir -p` in cmd/PowerShell. POSIX `mkdir -p` is appropriate only when the runtime environment information shows Git Bash or a non-WSL-stub PATH bash is available and you are actually using bash/Git Bash. Otherwise, use PowerShell `New-Item ... -Force` or create each level with cmd `mkdir`.

            IMPORTANT: Avoid using this tool to run `find`, `grep`, `cat`, `head`, `tail`, `sed`, `awk`, or `echo` commands, unless explicitly instructed or after you have verified that a dedicated tool cannot accomplish your task. Instead, use the appropriate dedicated tool as this will provide a much better experience for the user:

             - File search: Use glob tool (NOT find or ls)
             - Content search: Use grep tool (NOT grep or rg)
             - Read files: Use read_file tool (NOT cat/head/tail)
             - Edit files: Use edit_file tool (NOT sed/awk)
             - Write files: Use write_file tool (NOT echo >/cat <<EOF)
             - Communication: Output text directly (NOT echo/printf)
            While the bash tool can do similar things, it is better to use the built-in tools as they provide a better user experience and make it easier to review tool calls and give permission.

            # Instructions
             - If your command will create new directories or files, first use this tool to run `ls` to verify the parent directory exists and is the correct location.
             - Always quote file paths that contain spaces with double quotes in your command (e.g., cd "path with spaces/file.txt").
             - Try to maintain your current working directory throughout the session by using absolute paths and avoiding usage of `cd`. You may use `cd` if the user explicitly requests it.
             - You may specify an optional timeout in seconds (up to 3600s / 60 minutes). By default, your command will timeout after 300s.
             - You can use the `run_in_background` parameter to run the command in the background. Only use this if you don't need the result immediately and are OK being notified when the command completes later. You do not need to use '&' at the end of the command when using this parameter.
             - When issuing multiple commands:
               - If the commands are independent and can run in parallel, make multiple bash tool calls in a single message. Example: if you need to run "git status" and "git diff", send a single message with two bash tool calls in parallel.
               - If the commands depend on each other and must run sequentially, use a single bash call with '&&' to chain them together.
               - Use ';' only when you need to run commands sequentially but don't care if earlier commands fail.
               - DO NOT use newlines to separate commands (newlines are ok in quoted strings).
             - For git commands:
               - Prefer to create a new commit rather than amending an existing commit.
               - Before running destructive operations (e.g., git reset --hard, git push --force, git checkout --), consider whether there is a safer alternative that achieves the same goal. Only use destructive operations when they are truly the best approach.
               - Never skip hooks (--no-verify) or bypass signing (--no-gpg-sign) unless the user has explicitly asked for it. If a hook fails, investigate and fix the underlying issue.
               - When a user shares a repo URL and asks you to 'look at' or 'analyze' it, the natural first step is `git clone <url> <local_path>`; after cloning, use read_file/grep/glob on the working tree - it gives you far more than the rendered repository page would.
             - Avoid unnecessary `sleep` commands:
               - Do not sleep between commands that can run immediately -- just run them.
               - If your command is long running and you would like to be notified when it finishes -- use `run_in_background: true`. No sleep needed.
               - Do not retry failing commands in a sleep loop -- diagnose the root cause.
               - If waiting for a background task you started with `run_in_background: true`, you will be notified when it completes -- do not poll.
               - If you must poll an external process, use a check command (e.g. `gh run view`) rather than sleeping first.
               - If you must sleep, keep the duration short (1-5 seconds) to avoid blocking the user.
            """.stripTrailing();

    private static final String DESCRIPTION_PARAM_CN = """
            鐢ㄧ畝娲佺殑涓诲姩璇€佹弿杩拌鍛戒护鐨勪綔鐢ㄣ€?
            涓嶈鍦ㄦ弿杩颁腑浣跨敤 "澶嶆潅" 鎴? "椋庨櫓" 绛夎瘝鈥斺€旂洿鎺ユ弿杩板畠鍋氫粈涔堛€?

            瀵逛簬绠€鍗曞懡浠わ紙git銆乶pm銆佸父鐢?CLI 宸ュ叿锛夛紝淇濇寔绠€鐭紙5-10 涓瓧锛夛細
            - ls 鈫? "鍒楀嚭褰撳墠鐩綍鏂囦欢"
            - git status 鈫? "鏄剧ず宸ヤ綔鍖虹姸鎬?"
            - npm install 鈫? "瀹夎椤圭洰渚濊禆"

            瀵逛簬涓嶆槗涓€鐪肩湅鎳傜殑鍛戒护锛堢閬撳懡浠ゃ€佸喎闂ㄥ弬鏁扮瓑锛夛紝琛ュ厖瓒冲涓婁笅鏂囪鏄庡叾鐢ㄩ€旓細
            - find . -name "*.tmp" -exec rm {} \\; 鈫? "閫掑綊鏌ユ壘骞跺垹闄ゆ墍鏈?.tmp 鏂囦欢"
            - git reset --hard origin/main 鈫? "涓㈠純鎵€鏈夋湰鍦版洿鏀癸紝涓庤繙绋?main 瀵归綈"
            - curl -s url | jq '.data[]' 鈫? "浠?URL 鑾峰彇 JSON 骞舵彁鍙?data 鏁扮粍鍏冪礌"
            """.stripTrailing();

    private static final String DESCRIPTION_PARAM_EN = """
            Clear, concise description of what this command does in active voice. Never use words like "complex" or "risk" in the description - just describe what it does.

            For simple commands (git, npm, standard CLI tools), keep it brief (5-10 words):
            - ls -> "List files in current directory"
            - git status -> "Show working tree status"
            - npm install -> "Install package dependencies"

            For commands that are harder to parse at a glance (piped commands, obscure flags, etc.), add enough context to clarify what it does:
            - find . -name "*.tmp" -exec rm {} \\; -> "Find and delete all .tmp files recursively"
            - git reset --hard origin/main -> "Discard all local changes and match remote main"
            - curl -s url | jq '.data[]' -> "Fetch JSON from URL and extract data array elements"
            """.stripTrailing();

    @Override
    public String getName() {
        return "bash";
    }

    @Override
    public String getDescription(String language) {
        return "en".equals(language) ? DESCRIPTION_EN : DESCRIPTION_CN;
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        String lang = "en".equals(language) ? "en" : "cn";

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("command", property("string", "en".equals(lang) ? "The command to execute" : "瑕佹墽琛岀殑鍛戒护"));
        properties.put("timeout", integerProperty("en".equals(lang)
                ? "Optional timeout in seconds, default 300, max 3600. For long-running tasks, it is recommended to increase this value to avoid premature termination"
                : "鍙€夎秴鏃舵椂闂达紙绉掞級锛岄粯璁?300锛屼笂闄?3600銆傚浜庨暱鏃堕棿杩愯鐨勪换鍔★紝寤鸿閫傚綋澧炲ぇ璇ュ€间互閬垮厤浠诲姟琚彁鍓嶄腑鏂?"));
        properties.put("description", property("string", "en".equals(lang) ? DESCRIPTION_PARAM_EN : DESCRIPTION_PARAM_CN));
        properties.put("run_in_background", booleanProperty("en".equals(lang)
                ? "Set to true to run this command in the background. Only use this if you don't need the result immediately and are OK being notified when the command completes later"
                : "璁句负 true 浠ュ悗鍙拌繍琛屽懡浠ゃ€備粎鍦ㄤ笉闇€瑕佺珛鍗宠幏鍙栫粨鏋滄椂浣跨敤锛屽懡浠ゅ畬鎴愬悗浼氭敹鍒伴€氱煡"));
        properties.put("workdir", property("string", "en".equals(lang)
                ? "Working directory (relative or absolute path), defaults to workspace root; cannot escape workspace sandbox"
                : "鎵ц鐩綍锛堢浉瀵规垨缁濆璺緞锛夛紝榛樿涓哄伐浣滃尯鏍圭洰褰曪紱涓嶈兘瓒婂嚭宸ヤ綔鍖烘矙绠?"));
        properties.put("max_output_chars", integerProperty("en".equals(lang)
                ? "Max output characters; 0 (default) means no limit; non-zero values are capped at 20000 to prevent oversized output from flooding context"
                : "鏈€澶ц緭鍑哄瓧绗︽暟锛? 琛ㄧず涓嶉檺鍒讹紙榛樿锛夛紱闈為浂鏃朵笂闄?20000锛岄槻姝㈣秴澶ц緭鍑烘拺鐖嗕笂涓嬫枃"));

        Map<String, Object> shellType = property("string", "en".equals(lang)
                ? "Shell to use: auto/cmd/powershell/bash/sh, default auto. cmd/PowerShell do not support `mkdir -p`; use auto/bash/sh for POSIX syntax only when the environment information shows Git Bash or a non-WSL-stub PATH bash is available."
                : "鎸囧畾 Shell 绫诲瀷锛屽彲閫夊€硷細auto/cmd/powershell/bash/sh锛岄粯璁?auto銆俢md/PowerShell 涓嶆敮鎸?`mkdir -p`锛屽彧鏈夌幆澧冧俊鎭樉绀?Git Bash 鎴栭潪 WSL stub 鐨?PATH bash 鍙敤鏃讹紝鎵嶅 POSIX 璇硶浣跨敤 auto/bash/sh銆?");
        shellType.put("enum", List.of("auto", "cmd", "powershell", "bash", "sh"));
        properties.put("shell_type", shellType);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("command"));
        return schema;
    }

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> integerProperty(String description) {
        return property("integer", description);
    }

    private static Map<String, Object> booleanProperty(String description) {
        return property("boolean", description);
    }
}
