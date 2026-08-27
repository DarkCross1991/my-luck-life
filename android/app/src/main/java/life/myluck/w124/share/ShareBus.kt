package life.myluck.w124.share

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ShareBus {
    private val _incoming = MutableStateFlow<IncomingShare?>(null)
    val incoming: StateFlow<IncomingShare?> = _incoming.asStateFlow()

    fun offer(share: IncomingShare) {
        _incoming.value = share
    }

    fun consume() {
        _incoming.value = null
    }
}
