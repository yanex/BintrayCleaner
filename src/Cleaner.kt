package org.jetbrains.neokotlin

import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.jetbrains.neokotlin.CliOption.*
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.SocketTimeoutException
import java.text.DateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

fun clean(options: Set<CliOption>, config: Configuration) {
    val repoOwner = config["bintray.owner"]
    val repoName = config["bintray.repo"]
    val packageName = config["bintray.package"]

    val credentials = Credentials.basic(config["bintray.user"], config["bintray.api.key"])

    val httpClient = OkHttpClient().newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl("https://bintray.com/api/v1/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val bintrayService = retrofit.create(BintrayService::class.java)

    val versions = bintrayService.getPackage(repoOwner, repoName, packageName)().versions
    println("Found ${versions.size} version(s)")

    val oneMonthFromNow = Date(System.currentTimeMillis() - 1000L * 3600 * 24 * 30) // 1 month

    val preserveCount = 15
    val devVersionsToPreserve = versions.asSequence()
        .filter { ArtifactType.parse(it) == ArtifactType.DEV }
        .take(preserveCount)
        .toSet()

    for (versionId in versions.reversed()) {
        val artifactType = ArtifactType.parse(versionId)
        if (artifactType != ArtifactType.DEV && artifactType != ArtifactType.EAP) {
            println("$versionId: skipping $artifactType publication")
            continue
        }

        if (versionId in devVersionsToPreserve) {
            println("$versionId: skipping, preserving last $preserveCount build(s)")
            continue
        }

        val version = bintrayService.getVersion(credentials, repoOwner, repoName, packageName, versionId)()
        val publishingDate = DateFormat.getDateInstance().format(version.created)

        if (version.created.after(oneMonthFromNow)) {
            println("$versionId: skipping, relatively new ($publishingDate)")
            continue
        }

        fun deleteCurrent() {
            if (DryRun in options) {
                println("ok (dry run).")
                return
            }

            lateinit var message: String

            try {
                lateinit var result: Response<Unit>
                val took = measureTimeMillis {
                    result = bintrayService.deleteVersion(
                        credentials, repoOwner, repoName, packageName, versionId
                    ).execute()
                } / 1000
                message = if (result.isSuccessful) "ok (took $took s)." else result.raw().message()
            } catch (e: SocketTimeoutException) {
                message = "timeout."
            }

            println(message)
        }

        if (Automatic in options) {
            print("$versionId: Deleting obsolete version ($publishingDate)... ")
            deleteCurrent()
        } else {
            print("$versionId: obsolete version ($publishingDate). Delete (y/n)? ")
            if ((readLine() ?: return).toLowerCase() == "y") {
                print("deleting... ")
                deleteCurrent()
            }
        }
    }
}

private enum class ArtifactType {
    EAP, DEV, OTHER;

    companion object {
        private val REGEX = "\\d+\\.\\d+(?:\\.\\d+)?(?:-[A-Za-z0-9]+)?(?:-([a-z]+)-\\d+)?".toRegex()

        fun parse(versionId: String): ArtifactType {
            val qualifier = REGEX.matchEntire(versionId)?.groupValues?.drop(1)?.firstOrNull() ?: return OTHER
            return when (qualifier) {
                "eap", "rc" -> EAP
                "dev" -> DEV
                else -> OTHER
            }
        }
    }
}

private operator fun <T> Call<T>.invoke(): T {
    val result = execute()
    if (!result.isSuccessful) {
        die(result.raw().message())
    }
    return result.body()!!
}