package com.craftworks.music.player

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NowPlayingOpenRequest {
    private val mutableRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val requests = mutableRequests.asSharedFlow()

    fun emit() {
        mutableRequests.tryEmit(Unit)
    }
}
