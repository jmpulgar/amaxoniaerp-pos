package com.amaxoniaerp.core.security

import java.security.MessageDigest

fun md5Hash(value: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
