package ca.senseway.pathpaldemo

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

class WalkSignalSoundPlayer(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var activePlayers = mutableListOf<MediaPlayer>()

    // walk sign detected — play walk sound 2 times
    fun playWalk() = playChained("sounds/walk.mp3", 2)

    // wait/stop hand detected — play wait sound 3 times
    fun playWait() = playChained("sounds/wait.mp3", 3)

    private fun playChained(assetPath: String, times: Int) {
        stopAll() // cancel any in-progress sequence
        var remaining = times

        fun next() {
            if (remaining <= 0) return
            remaining--
            try {
                val mp = MediaPlayer()
                activePlayers.add(mp)
                val afd = context.assets.openFd(assetPath)
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                mp.prepare()
                mp.setOnCompletionListener {
                    activePlayers.remove(mp)
                    mp.release()
                    if (remaining > 0) handler.postDelayed(::next, 350L)
                }
                mp.start()
            } catch (_: Exception) {
                // asset missing or format unsupported — skip silently
            }
        }
        handler.post(::next)
    }

    fun stopAll() {
        activePlayers.forEach { runCatching { it.stop(); it.release() } }
        activePlayers.clear()
    }
}
