package net.seilent.funekit.api

sealed interface EngineEvent {
    data class Started(val track: Long) : EngineEvent
    data class Ended(val track: Long) : EngineEvent
    data class Failed(val track: Long, val message: String) : EngineEvent
    data object OutputLost : EngineEvent
    data object RouteTimeout : EngineEvent
    data class FormatChanged(val input: Format, val output: Format) : EngineEvent
    data class RouteChanged(val route: Output, val resampled: Boolean) : EngineEvent
    data class Xruns(val count: Long) : EngineEvent
    data object QueueChanged : EngineEvent
    data class Scan(val progress: ScanProgress) : EngineEvent
}
