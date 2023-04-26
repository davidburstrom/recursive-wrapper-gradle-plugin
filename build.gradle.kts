plugins {
    id("com.diffplug.spotless") version "6.18.0"
    id("com.github.ben-manes.versions") version "0.46.0"
    id("com.gradle.plugin-publish") version "1.2.0"
    id("io.github.davidburstrom.version-compatibility") version "0.5.0"
    id("net.ltgt.errorprone") version "3.1.0"
    id("org.gradle.signing")
}

val errorProneVersion = "2.18.0"
val googleJavaFormatVersion = "1.16.0"
version = "0.1.0-SNAPSHOT"
group = "io.github.davidburstrom.gradle.recursive-wrapper"
val pluginId = "io.github.davidburstrom.recursive-wrapper"
val packageName = "io.github.davidburstrom.gradle.recursivewrapper"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testImplementation("com.google.truth:truth:1.1.3")
    testImplementation("com.vdurmont:semver4j:3.1.0")
    errorprone("com.google.errorprone:error_prone_core:$errorProneVersion")
}

tasks.named<JavaCompile>("compileJava").configure {
    options.release.set(8)
}

tasks.named<JavaCompile>("compileTestJava").configure {
    options.release.set(11)
}

tasks.withType(JavaCompile::class).configureEach {
    options.compilerArgs.add("-Werror")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    systemProperty("GRADLE_VERSION", GradleVersion.current().version)
}

tasks.withType<Test>() {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(11))
        }
    )
}

spotless {
    java {
        googleJavaFormat(googleJavaFormatVersion)
        licenseHeaderFile(rootProject.file("config/license-header.txt"))
    }
}

versionCompatibility {
    tests {
        dimensions {
            register("Gradle") {
                versions.set(
                    listOf("6.0.1", "6.1.1", "6.2.2", "6.3", "6.4.1", "6.5.1", "6.6.1", "6.7.1", "6.8.3", "6.9.2", "7.0.2", "7.1.1", "7.2", "7.3.3", "7.4.2", "7.5.1", "7.6.1", "8.0.2", "8.1.1")
                )
                if (GradleVersion.current().version !in versions.get()) {
                    throw GradleException("Could not find ${gradle.gradleVersion} in the compatibility test versions")
                }
            }
        }
        eachTestTask {
            val (gradleVersion) = versions

            testTask.systemProperty("GRADLE_VERSION", gradleVersion)
            testTask.javaLauncher.set(
                javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(11))
                }
            )
        }
    }
}

tasks.named("build").configure {
    dependsOn("testCompatibility")
}

gradlePlugin {
    website.set("https://github.com/davidburstrom/recursive-wrapper-gradle-plugin")
    vcsUrl.set("https://github.com/davidburstrom/recursive-wrapper-gradle-plugin")
    plugins.register("plugin") {
        id = pluginId
        implementationClass = "$packageName.RecursiveWrapperPlugin"
        displayName = "Recursive Wrapper Gradle Plugin"
        description = "Updates the Gradle wrapper for all included projects in a single invocation."
        tags.set(listOf("wrapper", "update", "composite", "recursive", "included", "project"))
    }
}


val constantsGenerator by tasks.registering {
    val constantsDir = project.layout.buildDirectory.dir("generated/sources/constants/java")
    val outputFile =
        constantsDir.get().file("${packageName.replace(".", "/")}/Constants.java").asFile
    val licenseFile = rootProject.file("config/license-header.txt")
    inputs.file(licenseFile)
    inputs.property("version", version)
    inputs.property("pluginId", pluginId)
    inputs.property("group", project.group)
    inputs.property("id", project.name)
    inputs.property("packageName", packageName)
    outputs.dir(constantsDir)
    doLast {
        outputFile.parentFile.mkdirs()
        val properties = inputs.properties
        val pluginId by properties
        val group by properties
        val id by properties
        val version by properties
        val packageName by properties
        outputFile.writeText(
            licenseFile.readText() +
                    "package $packageName;\n" +
                    "/** Holds the current build version. */\n" +
                    "public final class Constants {\n" +
                    "  private Constants() {}\n" +
                    "\n" +
                    "  public static final String PLUGIN_ID = \"$pluginId\";\n" +
                    "  public static final String GROUP = \"$group\";\n" +
                    "  public static final String ID = \"$id\";\n" +
                    "  public static final String VERSION = \"$version\";\n" +
                    "}\n"
        )
    }
}

sourceSets.main {
    java.srcDir(constantsGenerator)
}

