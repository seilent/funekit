package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

class AgentTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val time = FakeTime()

    private fun agent(app: String) = Agent("$app/1.2 (https://example.org/$app)", "$app/1.2 (art@example.org)", "player-$app", app, "1.2")

    @Test
    fun aRequestCarriesTheHostsUserAgent() {
        for (app in listOf("hosta", "hostb")) {
            val f = Fixtures()
            val a = agent(app)
            val api = Api(f, a.userAgent, RateLimiter(0) { time.now }, time.sleep)
            f.queued["https://example.org/get"] = ArrayDeque(listOf(Response(200, ByteArray(1))))
            f.queued["POST https://example.org/post k=v"] = ArrayDeque(listOf(Response(200, ByteArray(1))))
            f.queued["https://example.org/down"] = ArrayDeque(listOf(Response(200, ByteArray(1))))
            api.get("https://example.org/get")
            api.post("https://example.org/post", mapOf("k" to "v"), emptyMap())
            api.download("https://example.org/down", 16) { _, _ -> false }
            assertEquals(listOf<String?>(a.userAgent, a.userAgent, a.userAgent), f.agents)
        }
    }

    @Test
    fun aListenBrainzBodyCarriesTheHostsPlayerAndClient() {
        for (app in listOf("hosta", "hostb")) {
            val f = Fixtures()
            val a = agent(app)
            ListenBrainz(f, a.userAgent, RateLimiter(0) { time.now }, time.sleep, ListenBrainz.BASE, a).submit("tok", listOf(Listen(1_800_000_000, "A", "T")))
            val body = f.calls.single { it.startsWith("POST ") }
            assertTrue(body, body.contains("\"media_player\":\"player-$app\",\"submission_client\":\"$app\",\"submission_client_version\":\"1.2\""))
            assertEquals(listOf<String?>(a.userAgent), f.agents)
        }
    }

    @Test
    fun aScrobblerSendsTheHostsAgentToLastFmAndListenBrainz() {
        for (app in listOf("hosta", "hostb")) {
            val f = Fixtures()
            val a = agent(app)
            val accounts = Accounts(true, "k".repeat(32), "s3cret", "me", "sk1", true, "tok", "me")
            Scrobbler(tmp.newFolder(), f, { accounts }, AppPair("a".repeat(32), "appsecret"), a, { 1_800_000_000_000L }, time.sleep, { time.now })
                .nowPlaying(Listen(1_800_000_000, "A", "T"))
            val posts = f.calls.filter { it.startsWith("POST ") }
            assertEquals(2, posts.size)
            assertEquals(listOf<String?>(a.userAgent, a.userAgent), f.agents)
            assertTrue(posts.toString(), posts.any { "\"media_player\":\"player-$app\",\"submission_client\":\"$app\"" in it })
        }
    }

    @Test
    fun lrclibSendsTheHostsAgent() {
        for (app in listOf("hosta", "hostb")) {
            val f = Fixtures()
            val a = agent(app)
            Lrclib(tmp.newFolder(), f, a, { true }, { 0L }, time.sleep, { time.now }, Random(1)).get(Lrclib.Query("t", "a", null, 200_000))
            assertTrue(f.agents.isNotEmpty())
            assertTrue(f.agents.toString(), f.agents.all { it == a.userAgent })
        }
    }

    @Test
    fun autoEqIndexSendsTheHostsAgent() {
        for (app in listOf("hosta", "hostb")) {
            val f = Fixtures()
            val a = agent(app)
            AutoEqIndex(tmp.newFolder(), f, a, clock = { time.now }, sleep = time.sleep, mono = { time.now }).profile(AutoEqIndex.Entry("Name", "oratory1990/over-ear/Name"))
            assertEquals(listOf<String?>(a.userAgent), f.agents)
        }
    }
}
