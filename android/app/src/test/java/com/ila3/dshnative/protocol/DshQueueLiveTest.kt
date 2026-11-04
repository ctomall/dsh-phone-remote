package com.ila3.dshnative.protocol

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.WebSocket
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Real queue regression. It creates a temporary Session, starts a long tool
 * turn, queues a second user message, then removes it through updateQueue.
 *
 *   DSH_PAIR_CODE=ABCD1234 DSH_QUEUE_TEST=1 ./gradlew testDebugUnitTest --tests '*DshQueueLiveTest*'
 */
class DshQueueLiveTest {
    private class Jar : CookieJar {
        private val values = mutableListOf<Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            values.removeAll { old -> cookies.any { it.name == old.name } }
            values.addAll(cookies)
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = values
    }

    @Test
    fun queueRemoveLive() = runBlocking {
        val code = System.getenv("DSH_PAIR_CODE") ?: return@runBlocking
        if (System.getenv("DSH_QUEUE_TEST") != "1") return@runBlocking
        val base = System.getenv("DSH_BASE") ?: "https://harness.ila3.net/phone/"
        val client = DshClient(base, Jar())
        var sessionId: String? = null
        var socket: WebSocket? = null
        try {
            client.pair(code)
            sessionId = client.createSession(null)
            println("DSH queue session=$sessionId")

            val turnStarted = CountDownLatch(1)
            val queued = CountDownLatch(1)
            val removed = CountDownLatch(1)
            val queuedId = AtomicReference<String?>(null)
            val sawInsert = AtomicBoolean(false)

            socket = client.openStream(
                "session/follow",
                JSONObject().put(
                    "request",
                    JSONObject()
                        .put("address", JSONObject().put("kind", "session").put("sessionId", sessionId))
                        .put("assistantStream", true)
                        .put("maxMessages", 100)
                        .put("turnWindow", JSONObject().put("minMessages", 10).put("minTurns", 1)),
                ),
                onValue = { value ->
                    if (value.optString("type") == "event") {
                        val event = value.optJSONObject("event")
                        if (event != null) {
                            when (event.optString("type")) {
                                "turn/start", "user/message" -> turnStarted.countDown()
                                "agent/inbox/spliced" -> {
                                    val data = event.optJSONObject("data")
                                    if (data != null && data.optString("target") == "next-turn") {
                                        val inserted = data.optJSONArray("inserted")
                                        if ((inserted?.length() ?: 0) > 0) {
                                            val id = inserted!!.optJSONObject(0)?.optString("id").orEmpty()
                                            if (id.isNotEmpty()) {
                                                queuedId.set(id)
                                                sawInsert.set(true)
                                                println("DSH queue inserted id=${id.take(8)}")
                                                queued.countDown()
                                            }
                                        }
                                        if (sawInsert.get() && data.optInt("removedCount", 0) > 0) {
                                            println("DSH queue observed removal")
                                            removed.countDown()
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                onFailure = { it.printStackTrace() },
            )

            client.prompt(sessionId, "请执行 bash 命令 sleep 40; echo done，然后只回复 DONE。不要调用其它工具。")
            assertTrue("turn did not start", turnStarted.await(60, TimeUnit.SECONDS))
            client.prompt(sessionId, "这是一条真实排队测试消息，请稍后回复 QUEUED-OK。")
            assertTrue("queue insert not observed", queued.await(60, TimeUnit.SECONDS))
            val itemId = queuedId.get()
            assertTrue("queue id missing", itemId != null)
            client.removeQueued(sessionId, itemId!!)
            assertTrue("queue removal not observed", removed.await(60, TimeUnit.SECONDS))
        } finally {
            runCatching { socket?.cancel() }
            sessionId?.let { id ->
                runCatching { client.cancelSession(id) }
                runCatching { client.archiveSession(id) }
            }
            runCatching { client.close() }
        }
    }
}
