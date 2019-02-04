package org.jetbrains.neokotlin

fun printUsage(): String = """
    Bintray artifact cleaner.

    Usage:
    ------
    cleaner [-n] [-a]

    Options:
    --------
    -n | dry run
    -a | automatic (non-interactive) mode

    Configuration:
    --------------
    Set the 'bintray.user' and 'bintray.api.key' options in the 'conf.properties' file.
""".trimIndent()