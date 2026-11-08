package dev.lackluster.mihelper.app.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FreePPS 模块（`/data/adb/modules/FreePPS`）的开关代理。
 *
 * 这个模块本体是常驻的守护进程（Rust 二进制，由 `service.sh` 拉起），App 侧只能做两件事：
 * 1. 读 `FreePPS/free`：`1` = 锁定公版 PPS，`0` = 暂停；
 * 2. 执行模块自带的 `action.sh`：它把 `free` 在 0/1 之间翻转，守护进程会自己跟上。
 *
 * `/data/adb` 对普通应用不可读，所以全部走 `su`。拿不到 root 或模块没装时统一返回 `null`。
 */
object FreePps {

    private const val MODULE_DIR = "/data/adb/modules/FreePPS"
    private const val FREE_FILE = "$MODULE_DIR/free"
    private const val ACTION_SH = "$MODULE_DIR/action.sh"

    /** 切换状态，阻塞 IO 在 [Dispatchers.IO] 上。null = 失败。 */
    suspend fun setLocked(locked: Boolean): Boolean? =
        withContext(Dispatchers.IO) { setLockedBlocking(locked) }

    /** 当前是否锁定。null = 未安装模块 / 没给 root / 读取失败。 */
    suspend fun readLocked(): Boolean? = withContext(Dispatchers.IO) { readLockedBlocking() }

    /** 同 [readLocked]，但会阻塞当前线程 —— 磁贴那边不在主线程调。 */
    fun readLockedBlocking(): Boolean? = runCatching {
        val result = SystemCommander.exec(
            command = "if [ -f $ACTION_SH ]; then cat $FREE_FILE 2>/dev/null | tr -d '[:space:]'; else echo __missing__; fi",
            silent = true,
        )
        when (result.out.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }.getOrNull()

    /**
     * 把状态设成 [locked]，返回改完之后**实际**的状态；null 表示没成功。
     *
     * `action.sh` 是无脑翻转的，所以只在当前值和目标不一致时才调它 —— 多调一次就反回去了。
     */
    fun setLockedBlocking(locked: Boolean): Boolean? {
        val current = readLockedBlocking() ?: return null
        if (current == locked) return current
        // 不用管 action.sh 的输出：它的 printf 也可能失败（比如 free 文件只读），
        // 所以一律以「再读一次的结果」为准。
        runCatching { SystemCommander.exec(command = "sh $ACTION_SH", silent = true) }
            .getOrNull() ?: return null
        return readLockedBlocking()
    }
}
