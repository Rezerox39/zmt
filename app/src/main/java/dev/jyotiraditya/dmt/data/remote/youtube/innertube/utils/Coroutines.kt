package dev.jyotiraditya.dmt.data.remote.youtube.innertube.utils

inline fun <T> runCatchingCancellable(block: () -> T) =
    runCatching(block).takeIf { it.exceptionOrNull() !is CancellationException }

private typealias CancellationException = kotlin.coroutines.cancellation.CancellationException
