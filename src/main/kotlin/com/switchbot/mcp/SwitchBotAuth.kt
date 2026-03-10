package com.switchbot.mcp

import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class AuthHeaders(
    val authorization: String,
    val t: String,
    val sign: String,
    val nonce: String,
)

fun generateAuthHeaders(token: String, secret: String): AuthHeaders {
    val t = System.currentTimeMillis().toString()
    val nonce = UUID.randomUUID().toString()
    val stringToSign = "$token$t$nonce"

    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val signBytes = mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
    val sign = Base64.getEncoder().encodeToString(signBytes).uppercase()

    return AuthHeaders(authorization = token, t = t, sign = sign, nonce = nonce)
}
