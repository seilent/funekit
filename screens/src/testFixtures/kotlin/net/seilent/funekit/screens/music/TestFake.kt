package net.seilent.funekit.screens.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import net.seilent.funekit.fake.Fake
import net.seilent.funekit.fake.FakeSet

private const val LEASE_MS = 60_000L
private const val NANOS_PER_MS = 1_000_000L

fun testFake(
    set: FakeSet = FakeSet.Demo,
    scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
    services: Boolean = false,
    lyricsCarried: Boolean = false,
    quickplay: Boolean = true,
    clock: () -> Long = { scheduler.currentTime * NANOS_PER_MS },
): Fake = Fake(CoroutineScope(StandardTestDispatcher(scheduler)), clock, set, 0L, LEASE_MS, services = services, lyricsCarried = lyricsCarried, offersQuickplay = quickplay)
