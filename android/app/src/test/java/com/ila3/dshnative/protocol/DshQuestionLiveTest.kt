package com.ila3.dshnative.protocol

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opt-in live check for ask_user_question projection + answer API. */
class DshQuestionLiveTest {
    private class Jar : CookieJar {
        private val values = mutableListOf<Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            values.removeAll { old -> cookies.any { it.name == old.name } }
            values.addAll(cookies)
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = values
    }

    @Test
    fun askAndAnswer() = runBlocking {
        val code = System.getenv("DSH_PAIR_CODE") ?: return@runBlocking
        if (System.getenv("DSH_QUESTION_TEST") != "1") return@runBlocking
        val base = System.getenv("DSH_BASE") ?: "https://harness.ila3.net/phone/"
        val client = DshClient(base, Jar())
        var sessionId: String? = null
        try {
            client.pair(code)
            sessionId = client.createSession(null)
            client.prompt(
                sessionId,
                "请只调用 ask_user_question 工具问我一个问题：是否继续？选项为「继续」和「停止」。不要调用其它工具。",
            )
            var pending = emptyList<DshClient.PendingQuestion>()
            for (attempt in 0 until 40) {
                pending = client.pendingQuestions(sessionId)
                if (pending.isNotEmpty()) break
                delay(1000)
            }
            assertTrue("no pending user question", pending.isNotEmpty())
            val question = pending.first()
            val first = question.questions.first()
            println("DSH question call=${question.callId.take(8)} q=${first.question} options=${first.options.map { it.label }}")
            val selected = first.options.firstOrNull()?.label ?: "继续"
            val answers = JSONArray().put(
                JSONObject().put("id", first.id).put("selected", JSONArray().put(selected)),
            )
            client.answerQuestions(sessionId, question.callId, answers)
            println("DSH question answered=$selected")
        } finally {
            sessionId?.let {
                runCatching { client.cancelSession(it) }
                runCatching { client.archiveSession(it) }
            }
            runCatching { client.close() }
        }
    }
}
