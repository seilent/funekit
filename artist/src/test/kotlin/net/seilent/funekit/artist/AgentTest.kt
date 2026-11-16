package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentTest {
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
}
