package pl.proszczok.player.playback

/** Manual skipping wraps the queue even when automatic repeat is disabled. */
internal fun queueSkipIndex(current: Int, count: Int, adjacent: Int, forward: Boolean): Int? {
    if (count < 2 || current !in 0 until count) return null
    if (adjacent in 0 until count && adjacent != current) return adjacent
    return if (forward) (current + 1) % count else (current - 1 + count) % count
}
