package com.miniaturesoftwares.xpjobssuperviser.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Paces outbound API calls so background prefetching cannot starve the calls a
 * user is waiting on.
 *
 * The server allows 40 requests/second. Background work is deliberately capped
 * at [PERMITS_PER_SECOND] - half the budget - so foreground requests always
 * have room. Without this, opening Available Tasks fired one detail request
 * per task simultaneously, which both tripped the server limit and queued
 * ahead of everything else the app wanted to do.
 *
 * A plain sliding window rather than a token bucket: bursts are exactly what
 * caused the problem, so there is no allowance for them.
 */
object ApiRateLimiter {

    /** Half the server's 40/sec, leaving the rest for foreground calls. */
    const val PERMITS_PER_SECOND = 20

    private const val WINDOW_MS = 1_000L

    private val mutex = Mutex()
    /** Completion times of recent calls, oldest first. */
    private val recent = ArrayDeque<Long>()

    /**
     * Suspends until another request may be issued. Call immediately before
     * each network call on a background path.
     */
    suspend fun acquire() {
        while (true) {
            val waitMs = mutex.withLock {
                val now = System.currentTimeMillis()
                while (recent.isNotEmpty() && now - recent.first() >= WINDOW_MS) {
                    recent.removeFirst()
                }
                if (recent.size < PERMITS_PER_SECOND) {
                    recent.addLast(now)
                    0L
                } else {
                    // Wait until the oldest call falls out of the window.
                    WINDOW_MS - (now - recent.first())
                }
            }
            if (waitMs <= 0L) return
            delay(waitMs)
        }
    }
}
