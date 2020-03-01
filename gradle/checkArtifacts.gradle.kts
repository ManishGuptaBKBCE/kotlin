import java.io.InputStream
import java.util.jar.Manifest
import java.util.zip.ZipFile

val isTeamcityBuild = project.hasProperty("teamcity") || System.getenv("TEAMCITY_VERSION") != null

val distDir: String by rootProject.extra
val kotlinVersion: String by rootProject.extra

val checkDist = tasks.register("checkDistArtifacts") {
    doLast {
        fileTree(distDir).checkArtifacts()
    }
}

tasks.register("checkArtifacts") {
    dependsOn(checkDist)
}

fun FileTree.checkArtifacts() {
    filter { it.extension == "jar" }.forEach { jar ->
        val zip = ZipFile(jar)
        val artifact = jar.relativeTo(file(rootDir)).invariantSeparatorsPath

        if (isTeamcityBuild)
            testStarted(artifact)

        zip.checkCompilerVersion(kotlinVersion)?.let { reportProblem(artifact, it) }
        zip.checkManifest(kotlinVersion)?.let { reportProblem(artifact, it) }

        if (isTeamcityBuild)
            testFinished(artifact)
    }
}

fun ZipFile.checkManifest(version: String) = checkZipEntry("META-INF/MANIFEST.MF") { entryStream ->
    val implementationVersion = Manifest(entryStream).mainAttributes.getValue("Implementation-Version")
    "Manifest contains invalid 'Implementation-Version' value, expected: $version found: $implementationVersion".takeIf {
        implementationVersion?.let { it != version } ?: false
    }
}

fun ZipFile.checkCompilerVersion(version: String) = checkZipEntry("META-INF/compiler.version") {
    val artifactVersion = it.bufferedReader().readLine()
    "Invalid compiler.version content, expected: $version found: $artifactVersion"
        .takeIf { artifactVersion != version }
}

fun ZipFile.checkZipEntry(entryName: String, action: (entryStream: InputStream) -> String?): String? =
    getEntry(entryName)?.let { entry -> getInputStream(entry).use(action) }

fun reportProblem(artifact: String, message: String) {
    if (isTeamcityBuild)
        testFailed(artifact, "Artifact contains problems", message)
    else
        println("Artifact $artifact contains problems:\n$message")

}

fun escape(s: String): String {
    return s.replace("[\\|'\\[\\]]".toRegex(), "\\|$0").replace("\n".toRegex(), "|n").replace("\r".toRegex(), "|r")
}

fun testStarted(testName: String) {
    println("##teamcity[testStarted name='%s']".format(escape(testName)))
}

fun testFinished(testName: String) {
    println("##teamcity[testFinished name='%s']".format(escape(testName)))
}

fun testFailed(name: String, message: String, details: String) {
    println("##teamcity[testFailed name='%s' message='%s' details='%s']".format(escape(name), escape(message), escape(details)))
}