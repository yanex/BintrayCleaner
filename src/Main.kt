package org.jetbrains.neokotlin

import java.io.File
import java.util.*

private const val CONFIGURATION_FILE_NAME = "conf.properties"

enum class CliOption(val text: String) {
    DryRun("-n"),
    Automatic("-a")
}

class Configuration(private val map: Map<String, String>) {
    operator fun get(key: String): String {
        return map[key]?.takeIf { it.isNotEmpty() }
            ?: die("Error: define '$key' in '$CONFIGURATION_FILE_NAME'")
    }
}

fun readConfiguration(): Map<String, String> {
    val propertiesFile = File(CONFIGURATION_FILE_NAME).takeIf { it.exists() }
        ?: die("Error: '$CONFIGURATION_FILE_NAME' file does not exist")

    val properties = Properties()
    propertiesFile.inputStream().use { properties.load(it) }
    return properties.map { Pair(it.toString(), it.toString()) }.toMap()
}

fun main(args: Array<String>) {
    if ("-help" in args || "--help" in args) {
        println(printUsage())
        System.exit(0)
    }

    val options = args
        .mapNotNull { arg -> CliOption.values().firstOrNull { v -> v.text == arg } }
        .toSet()

    val config = Configuration(readConfiguration())

    clean(options, config)
}

fun die(message: String): Nothing {
    System.err.println(message)
    System.exit(1)
    throw IllegalStateException("System.exit() does not work")
}