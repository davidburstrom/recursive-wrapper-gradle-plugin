plugins {
    id("com.diffplug.spotless") version "7.0.4"
    id("com.github.ben-manes.versions") version "0.52.0"
    id("com.gradle.plugin-publish") version "1.3.1"
    id("io.github.davidburstrom.version-compatibility") version "0.5.0"
    id("net.ltgt.errorprone") version "4.2.0"
    id("org.gradle.signing")
}

val errorProneVersion = "2.38.0"
val googleJavaFormatVersion = "1.16.0"
val ktlintVersion = "1.6.0"
version = "0.1.0-SNAPSHOT"
group = "io.github.davidburstrom.gradle.recursive-wrapper"
val pluginId = "io.github.davidburstrom.recursive-wrapper"
val packageName = "io.github.davidburstrom.gradle.recursivewrapper"

repositories {
    mavenCentral()
}

configurations {
    register("dependencyUpdates")
}

dependencies {
    "dependencyUpdates"("com.pinterest.ktlint:ktlint-bom:$ktlintVersion")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.0")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("com.vdurmont:semver4j:3.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.0")
    errorprone("com.google.errorprone:error_prone_core:$errorProneVersion")
}

configure<JavaPluginExtension> {
    toolchain {
        // Could theoretically be version 8, but it's not compatible with
        // ErrorProne. Therefore, the JavaCompile release option is used.
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.named<JavaCompile>("compileJava").configure {
    options.release = 8
}

tasks.named<JavaCompile>("compileTestJava").configure {
    options.release = 11
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

tasks.withType<Test> {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(11)
    }
    maxHeapSize = "48m"
}

spotless {
    java {
        googleJavaFormat()
        licenseHeaderFile(rootProject.file("config/license-header.txt"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint(ktlintVersion).editorConfigOverride(mapOf("ktlint_standard_trailing-comma-on-call-site" to "disabled"))
    }
}

versionCompatibility {
    tests {
        dimensions {
            register("Gradle") {
                versions = listOf("6.0.1", "6.1.1", "6.2.2", "6.3", "6.4.1", "6.5.1", "6.6.1", "6.7.1", "6.8.3", "6.9.4", "7.0.2", "7.1.1", "7.2", "7.3.3", "7.4.2", "7.5.1", "7.6.3", "8.0.2", "8.1.1", "8.2.1", "8.3", "8.4", "8.5", "8.6", "8.7", "8.8", "8.9", "8.10.2", "8.11.1", "8.12.1", "8.13", "8.14.1")
                if (GradleVersion.current().version !in versions.get()) {
                    throw GradleException("Could not find ${gradle.gradleVersion} in the compatibility test versions")
                }
            }
        }
        eachTestTask {
            val (gradleVersion) = versions

            testTask.systemProperty("GRADLE_VERSION", gradleVersion)
            testTask.javaLauncher = javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(11)
            }
        }
    }
}

tasks.named("build").configure {
    dependsOn("testCompatibility")
}

gradlePlugin {
    website = "https://github.com/davidburstrom/recursive-wrapper-gradle-plugin"
    vcsUrl = "https://github.com/davidburstrom/recursive-wrapper-gradle-plugin"
    plugins.register("plugin") {
        id = pluginId
        implementationClass = "$packageName.RecursiveWrapperPlugin"
        displayName = "Recursive Wrapper Gradle Plugin"
        description = "Updates the Gradle wrapper for all included projects in a single invocation."
        tags = listOf("wrapper", "update", "composite", "recursive", "included", "project")
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
                "\n" +
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
