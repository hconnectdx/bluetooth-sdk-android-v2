package kr.co.hconnect.polihealth_sdk_android_v2.utils

// 바이트 배열을 16진수 문자열로 변환하는 헬퍼 함수
fun ByteArray.toHexString(): String =
    joinToString(separator = " ") { byte -> "%02x".format(byte) }

fun Byte.toHexString(): String = "%02x".format(this)