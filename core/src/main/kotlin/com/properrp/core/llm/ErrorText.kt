package com.properrp.core.llm

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** A failure as a person reads it: what happened and what to do, then the technical chain for reports. */
object ErrorText {
    /** What kind of failure it is, so the app can say it in its own language. */
    enum class Kind { NO_NETWORK, CUT_OFF, TIMEOUT, TLS, BAD_KEY, FORBIDDEN, NO_CREDIT, RATE_LIMITED, OVERLOADED, SERVER, DECLINED, UNUSABLE, NO_KEY, OTHER }

    fun kind(e: Throwable): Kind {
        val chain = generateSequence(e) { it.cause }.take(6).toList()
        val all = chain.joinToString(" ") { it.message.orEmpty() }
        return when {
            chain.any { it is UnknownHostException } -> Kind.NO_NETWORK
            "cut off" in all -> Kind.CUT_OFF
            chain.any { it is SocketTimeoutException } || "timed out" in all || "timeout" in all.lowercase() -> Kind.TIMEOUT
            chain.any { it is SSLException } -> Kind.TLS
            "API error 401" in all -> Kind.BAD_KEY
            "API error 403" in all -> Kind.FORBIDDEN
            "credit balance" in all.lowercase() -> Kind.NO_CREDIT
            "rate limited" in all -> Kind.RATE_LIMITED
            "server error 529" in all || "overloaded" in all.lowercase() -> Kind.OVERLOADED
            "server error" in all -> Kind.SERVER
            "declined" in all -> Kind.DECLINED
            "no usable reply" in all -> Kind.UNUSABLE
            "Add your Claude API key" in all -> Kind.NO_KEY
            else -> Kind.OTHER
        }
    }

    /** The technical chain, for reports. */
    fun details(e: Throwable): String = generateSequence(e) { it.cause }.take(6).toList()
        .joinToString("\n← ") { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }

    /** In English: what happened and what to do, then the technical chain (the CLI and tests). */
    fun explain(e: Throwable): String = HEADLINES.getValue(kind(e)) + "\n\n" + details(e)

    val HEADLINES = mapOf(
        Kind.NO_NETWORK to "Can't reach Claude: the phone couldn't look up api.anthropic.com. Check the internet connection (and that " +
            "this app may use mobile data), then try again.",
        Kind.CUT_OFF to "The connection dropped while Claude was writing. Try again.",
        Kind.TIMEOUT to "The connection timed out. On a weak signal this happens; try again.",
        Kind.TLS to "The secure connection to Claude failed. A VPN, captive Wi-Fi page or filtering DNS can cause this.",
        Kind.BAD_KEY to "Claude rejected the API key. Paste a new one in Settings → Claude API key.",
        Kind.FORBIDDEN to "Claude refused the request (403): the key may lack access, or the API isn't available from this network.",
        Kind.NO_CREDIT to "Your Anthropic account is out of credit. Add credit in the Anthropic Console, then try again.",
        Kind.RATE_LIMITED to "Too many requests at once. Wait a minute and try again.",
        Kind.OVERLOADED to "Claude is overloaded right now. Try again in a minute.",
        Kind.SERVER to "Claude had a server error. Try again.",
        Kind.DECLINED to "The model declined to write this passage. Try again, or reroll.",
        Kind.UNUSABLE to "Claude's answer came back unusable three times. Try again.",
        Kind.NO_KEY to "Add your Claude API key first (Settings → Claude API key).",
        Kind.OTHER to "Something went wrong.",
    )
}
