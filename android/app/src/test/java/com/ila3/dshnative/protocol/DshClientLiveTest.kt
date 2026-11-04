package com.ila3.dshnative.protocol

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Opt-in live protocol test. It runs only when DSH_PAIR_CODE is supplied, so CI
 * and regular builds stay offline and side-effect free:
 *
 *   DSH_PAIR_CODE=ABCD1234 ./gradlew testDebugUnitTest --tests '*DshClientLiveTest*'
 */
class DshClientLiveTest {
    private class Jar : CookieJar {
        private val values = mutableListOf<Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            values.removeAll { old -> cookies.any { it.name == old.name } }
            values.addAll(cookies)
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = values
    }

    @Test
    fun pairListAndFollow() = runBlocking {
        val code = System.getenv("DSH_PAIR_CODE") ?: return@runBlocking
        val base = System.getenv("DSH_BASE") ?: "https://harness.ila3.net/phone/"
        val client = DshClient(base, Jar())
        client.pair(code)
        val sessions = client.sessions()
        println("DSH native protocol sessions=${sessions.size}")
        assertTrue("expected at least one session", sessions.isNotEmpty())

        val session = sessions.firstOrNull { !it.blank } ?: sessions.first()
        val snapshot = CountDownLatch(1)
        val address = JSONObject().put("kind", "session").put("sessionId", session.id)
        val request = JSONObject()
            .put("address", address)
            .put("assistantStream", true)
            .put("maxMessages", 100)
            .put("turnWindow", JSONObject().put("minMessages", 10).put("minTurns", 1))
        val socket = client.openStream(
            "session/follow",
            JSONObject().put("request", request),
            onValue = { value ->
                if (value.optString("type") == "snapshot") {
                    val records = value.optJSONArray("records")
                    val items = ChatParser.parseRecords(records)
                    println(
                        "DSH native protocol snapshot records=${records?.length() ?: 0} " +
                            "items=${items.size} users=${items.count { it is ChatItem.User }} " +
                            "tools=${items.count { it is ChatItem.Tool }}",
                    )
                    if (session.blank.not()) {
                        assertTrue("expected at least one user message", items.any { it is ChatItem.User })
                    }
                    snapshot.countDown()
                }
            },
            onFailure = { it.printStackTrace() },
        )
        assertTrue("expected session/follow snapshot", snapshot.await(20, TimeUnit.SECONDS))
        socket.cancel()
        val files = client.listFiles(session.id, ".")
        println("DSH native protocol files=${files.size}")
        files.take(10).forEach { println("DSH native protocol file type=${it.type} name=${it.name} size=${it.size}") }
        runCatching { client.readFileText(session.id, "dsh-native-android/README.md") }
            .onSuccess { println("DSH native protocol file preview chars=${it.length}") }
            .onFailure { println("DSH native protocol file preview failed: ${it.message}") }

        val upload = client.uploadFile(session.id, "native upload probe".toByteArray(), "native-upload.txt")
        println("DSH native protocol upload receipt=${upload.receiptId.take(8)} bytes=${upload.bytes}")

        val terminals = client.listTerminals(session.id)
        val terminal = terminals.firstOrNull() ?: client.createTerminal(session.id)
        val termSnapshot = CountDownLatch(1)
        val term = client.attachTerminal(
            session.id,
            terminal.id,
            onFrame = { frame ->
                if (frame.optString("type") == "snapshot") {
                    println("DSH native protocol terminal screen=${frame.optString("screen").length}")
                    termSnapshot.countDown()
                }
            },
            onFailure = { it.printStackTrace() },
        )
        assertTrue("expected terminal snapshot", termSnapshot.await(20, TimeUnit.SECONDS))
        term.cancel()
        client.closeTerminal(session.id, terminal.id)

        val catalog = client.modelCatalog()
        println("DSH native protocol model groups=${catalog.groups.size} default=${catalog.default?.provider}/${catalog.default?.model}")
        val selected = client.currentModel(session.id)
        println("DSH native protocol current model=${selected?.provider}/${selected?.model}")
        catalog.default?.let {
            client.selectModel(session.id, it)
            println("DSH native protocol model select ok=${it.provider}/${it.model}")
        }

        if (System.getenv("DSH_PROMPT_TEST") == "1") {
            val newSession = client.createSession(null)
            val reply = CountDownLatch(1)
            val follow = client.openStream(
                "session/follow",
                JSONObject().put(
                    "request",
                    JSONObject()
                        .put("address", JSONObject().put("kind", "session").put("sessionId", newSession))
                        .put("assistantStream", true)
                        .put("maxMessages", 50)
                        .put("turnWindow", JSONObject().put("minMessages", 5).put("minTurns", 1)),
                ),
                onValue = { value ->
                    if (value.optString("type") == "event") {
                        val type = value.optJSONObject("event")?.optString("type")
                        println("DSH native protocol prompt event=$type")
                        if (type == "assistant/message") reply.countDown()
                    }
                },
                onFailure = { it.printStackTrace() },
            )
            client.prompt(newSession, "只回复 OK，不要使用任何工具。")
            assertTrue("expected assistant message", reply.await(90, TimeUnit.SECONDS))
            follow.cancel()
        }
        client.close()
    }
}
