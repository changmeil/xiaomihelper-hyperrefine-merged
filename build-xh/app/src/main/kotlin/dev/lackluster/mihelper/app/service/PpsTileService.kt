package dev.lackluster.mihelper.app.service

import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.lackluster.mihelper.R
import dev.lackluster.mihelper.app.utils.FreePps
import java.util.concurrent.Executors

/**
 * 控制中心磁贴：切换 FreePPS 的 PPS 锁定状态。
 *
 * 磁贴和主界面那个开关是同一个数据源（模块的 `free` 文件），谁改了另一方下次
 * `onStartListening` / 打开界面时都能对上。
 *
 * 磁贴回调都在主线程，而 `su` 是阻塞的，所以全部丢到单线程池里跑，回主线程再 `updateTile()`。
 * 点下去先把磁贴置成 `STATE_UNAVAILABLE`，避免连点两次把状态翻回来。
 */
class PpsTileService : TileService() {

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pps-tile")
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        worker.execute {
            val locked = FreePps.readLockedBlocking()
            mainHandler.post { applyState(locked) }
        }
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        val target = tile.state != Tile.STATE_ACTIVE
        tile.state = Tile.STATE_UNAVAILABLE
        tile.updateTile()
        worker.execute {
            val locked = FreePps.setLockedBlocking(target)
            mainHandler.post { applyState(locked) }
        }
    }

    private fun applyState(locked: Boolean?) {
        val tile = qsTile ?: return
        tile.state = when (locked) {
            true -> Tile.STATE_ACTIVE
            false -> Tile.STATE_INACTIVE
            null -> Tile.STATE_UNAVAILABLE
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_pps_tile)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (locked) {
                true -> getString(R.string.tile_pps_state_locked)
                false -> getString(R.string.tile_pps_state_paused)
                null -> getString(R.string.tile_pps_state_missing)
            }
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
