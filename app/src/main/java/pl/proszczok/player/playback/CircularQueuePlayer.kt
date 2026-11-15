package pl.proszczok.player.playback

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Exposes real skip commands to Media3, legacy controllers and headset buttons. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class CircularQueuePlayer(private val delegate: Player) : ForwardingSimpleBasePlayer(delegate) {
    override fun getState(): State {
        val state = super.getState()
        if (delegate.mediaItemCount < 2 || !delegate.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) return state
        return state.buildUpon().setAvailableCommands(
            state.availableCommands.buildUpon().addAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            ).build(),
        ).build()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val forward = when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> true
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> false
            else -> return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
        val target = queueSkipIndex(
            delegate.currentMediaItemIndex,
            delegate.mediaItemCount,
            if (forward) delegate.nextMediaItemIndex else delegate.previousMediaItemIndex,
            forward,
        )
        if (target != null) delegate.seekTo(target, 0L)
        return Futures.immediateVoidFuture()
    }
}
