package com.sofasports.tv

/** Reads JSON fixtures from src/test/resources/fixtures (the same files the debug fixture mode serves). */
object Fixtures {
    fun read(path: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$path")) { "missing fixture $path" }.readText()
}
