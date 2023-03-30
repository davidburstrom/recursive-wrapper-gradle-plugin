/*
 * Copyright 2023 David Burström
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.davidburstrom.gradle.recursivewrapper;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/*
 * ProjectBuilder from Gradle testfixtures does not have support for included builds,
 * therefore the GradleRunner is required. This makes it impossible to get mutation test coverage.
 */
class RecursiveWrapperPluginTest {

  private static final List<? extends File> PLUGIN_CLASSPATH =
      GradleRunner.create().withPluginClasspath().getPluginClasspath();

  @Test
  void updatesWithNoIncludedBuild(@TempDir Path projectDir) throws IOException {
    writeSettingsScript(projectDir, "");

    bootstrapWrappers(projectDir);

    writeProjectBuildScriptWithPluginsBlock(projectDir);

    getGradleRunner(projectDir)
        .withArguments(":wrapper", "--stacktrace", RecursiveWrapperPlugin.TESTING_PROPERTY_ARG)
        .build();

    assertWrapperPropertyEquals(
        projectDir,
        "distributionUrl",
        "https://services.gradle.org/distributions/gradle-"
            + System.getProperty("GRADLE_VERSION")
            + "-bin.zip");
  }

  @Test
  void updatesOneIncludedBuild(@TempDir Path projectDir) throws IOException {
    writeSettingsScript(projectDir, "includeBuild(\"subproject\")");
    Path subprojectDir = createIncludedProject(projectDir, "subproject", "");

    bootstrapWrappers(projectDir, subprojectDir);

    writeProjectBuildScriptWithPluginsBlock(projectDir);

    getGradleRunner(projectDir)
        .withArguments(":wrapper", "--stacktrace", RecursiveWrapperPlugin.TESTING_PROPERTY_ARG)
        .build();

    assertWrapperPropertyEquals(
        subprojectDir,
        "distributionUrl",
        "https://services.gradle.org/distributions/gradle-"
            + System.getProperty("GRADLE_VERSION")
            + "-bin.zip");
  }

  @Test
  void updatesOneIncludedBuildInDistalDirectory(@TempDir Path projectDir) throws IOException {
    writeSettingsScript(projectDir, "includeBuild(\"dir/subproject\")\n");

    Path subprojectDir = createIncludedProject(projectDir, "dir/subproject", "");

    bootstrapWrappers(projectDir, subprojectDir);

    writeProjectBuildScriptWithPluginsBlock(projectDir);

    getGradleRunner(projectDir)
        .withArguments(":wrapper", "--stacktrace", RecursiveWrapperPlugin.TESTING_PROPERTY_ARG)
        .build();

    assertWrapperPropertyEquals(
        projectDir,
        "distributionUrl",
        "https://services.gradle.org/distributions/gradle-"
            + System.getProperty("GRADLE_VERSION")
            + "-bin.zip");

    assertWrapperPropertyEquals(
        subprojectDir,
        "distributionUrl",
        "https://services.gradle.org/distributions/gradle-"
            + System.getProperty("GRADLE_VERSION")
            + "-bin.zip");
  }

  @Test
  void updatesTransitivelyIncludedBuilds(@TempDir Path projectDir) throws IOException {
    writeSettingsScript(projectDir, "includeBuild(\"subproject\")");
    Path subprojectDir =
        createIncludedProject(projectDir, "subproject", "includeBuild(\"subsubproject\")");
    Path subsubprojectDir = createIncludedProject(subprojectDir, "subsubproject", "");

    bootstrapWrappers(projectDir, subprojectDir, subsubprojectDir);
    writeProjectBuildScriptWithPluginsBlock(projectDir);

    getGradleRunner(projectDir)
        .withArguments(
            ":wrapper",
            "--distribution-type=all",
            "--stacktrace",
            RecursiveWrapperPlugin.TESTING_PROPERTY_ARG)
        .build();

    assertWrapperPropertyEquals(
        subsubprojectDir,
        "distributionUrl",
        "https://services.gradle.org/distributions/gradle-"
            + System.getProperty("GRADLE_VERSION")
            + "-all.zip");
  }

  @Test
  void setsExplicitProperties(@TempDir Path projectDir) throws IOException {
    writeSettingsScript(projectDir, "includeBuild(\"subproject\")");

    Path subprojectDir = createIncludedProject(projectDir, "subproject", "");

    bootstrapWrappers(projectDir, subprojectDir);

    writeProjectBuildScriptWithPluginsBlock(projectDir);

    getGradleRunner(projectDir)
        .withArguments(
            ":wrapper",
            "--gradle-distribution-url=https://services.gradle.org/distributions/gradle-8.0.2-bin.zip",
            "--gradle-distribution-sha256-sum=ff7bf6a86f09b9b2c40bb8f48b25fc19cf2b2664fd1d220cd7ab833ec758d0d7",
            "--stacktrace",
            RecursiveWrapperPlugin.TESTING_PROPERTY_ARG)
        .build();

    assertWrapperPropertyEquals(
        projectDir,
        "distributionUrl",
        "https://services.gradle.org/distributions/gradle-8.0.2-bin.zip");
    assertWrapperPropertyEquals(
        projectDir,
        "distributionSha256Sum",
        "ff7bf6a86f09b9b2c40bb8f48b25fc19cf2b2664fd1d220cd7ab833ec758d0d7");
    assertWrapperPropertyEquals(
        subprojectDir,
        "distributionUrl",
        "https://services.gradle.org/distributions/gradle-8.0.2-bin.zip");
    assertWrapperPropertyEquals(
        subprojectDir,
        "distributionSha256Sum",
        "ff7bf6a86f09b9b2c40bb8f48b25fc19cf2b2664fd1d220cd7ab833ec758d0d7");
  }

  @Test
  void includedBuildCanHavePluginAppliedWithoutConflict(@TempDir Path projectDir)
      throws IOException {
    writeSettingsScript(projectDir, "includeBuild(\"subproject\")");
    Path subprojectDir = createIncludedProject(projectDir, "subproject", "");

    bootstrapWrappers(projectDir, subprojectDir);
    writeProjectBuildScriptWithPluginsBlock(projectDir);
    writeProjectBuildScriptWithPluginsBlock(subprojectDir);

    getGradleRunner(projectDir)
        .withArguments(
            ":wrapper",
            "--distribution-type=all",
            "--stacktrace",
            RecursiveWrapperPlugin.TESTING_PROPERTY_ARG)
        .build();

    assertWrapperPropertyEquals(
        subprojectDir,
        "distributionUrl",
        "https://services.gradle.org/distributions/gradle-"
            + System.getProperty("GRADLE_VERSION")
            + "-all.zip");
  }

  @Test
  void cannotIncludeProjectsWithSameLeafName(@TempDir Path projectDir) throws IOException {
    writeSettingsScript(
        projectDir, "includeBuild(\"dir1/subproject\"); includeBuild(\"dir2/subproject\")");

    createIncludedProject(projectDir, "dir1/subproject", "");
    createIncludedProject(projectDir, "dir2/subproject", "");

    writeProjectBuildScriptWithPluginsBlock(projectDir);

    BuildResult buildResult =
        getGradleRunner(projectDir).withArguments(":wrapper", "--stacktrace").buildAndFail();

    assertTrue(buildResult.getOutput().contains("which is the same"));
  }

  @Test
  void failsIfTestingSystemPropertyIsNotSuppliedInArguments(@TempDir Path projectDir)
      throws IOException {
    writeSettingsScript(projectDir, "includeBuild(\"subproject\")");
    Path subprojectDir = createIncludedProject(projectDir, "subproject", "");

    bootstrapWrappers(projectDir, subprojectDir);
    writeProjectBuildScriptWithPluginsBlock(projectDir);

    BuildResult buildResult =
        getGradleRunner(projectDir)
            .withArguments(":wrapper", "--distribution-type=all", "--stacktrace")
            .buildAndFail();

    assertTrue(
        buildResult
            .getOutput()
            .contains(Constants.GROUP + ":" + Constants.ID + ":" + Constants.VERSION));
  }

  /**
   * Invokes Gradle in order to write the wrapper infrastructure in the given directory, as well as
   * any other given directories.
   */
  private static void bootstrapWrappers(final Path directory, Path... otherDirectories)
      throws IOException {
    getGradleRunner(directory)
        .withArguments(":wrapper", "--stacktrace")
        .withProjectDir(directory.toFile())
        .build();
    for (final Path otherDirectory : otherDirectories) {
      copyWrapper(directory, otherDirectory);
    }
  }

  private static GradleRunner getGradleRunner(@Nonnull Path projectDir) {
    return GradleRunner.create()
        .forwardOutput()
        .withPluginClasspath()
        .withProjectDir(projectDir.toFile())
        .withGradleVersion(System.getProperty("GRADLE_VERSION"));
  }

  @NotNull
  private static Path createIncludedProject(
      final Path projectDir, final String relativePath, final String extraSettings)
      throws IOException {
    Path includedProjectDir = projectDir.resolve(relativePath);
    Files.createDirectories(includedProjectDir);
    writeSettingsScript(includedProjectDir, extraSettings);
    return includedProjectDir;
  }

  private static void writeSettingsScript(final Path projectDir, final String extraSettings)
      throws IOException {
    Files.write(
        projectDir.resolve("settings.gradle.kts"),
        getSettingsScriptWithPluginClasspath(extraSettings).getBytes(UTF_8));
  }

  @Nonnull
  private static String getSettingsScriptWithPluginClasspath(
      @Nonnull final String extraSettingsScript) {
    StringBuilder sb = new StringBuilder();
    sb.append("buildscript {\n");
    sb.append("  dependencies {\n");
    String collect =
        PLUGIN_CLASSPATH.stream()
            .map(it -> "\"" + it.toString() + "\"")
            .collect(Collectors.joining(", "));
    sb.append("    classpath(files(").append(collect).append("))\n");
    sb.append("  }\n");
    sb.append("}\n");
    sb.append(extraSettingsScript);
    return sb.toString();
  }

  private static void copyWrapper(@Nonnull final Path sourceDir, @Nonnull final Path destinationDir)
      throws IOException {
    copyWithDirs(sourceDir, destinationDir, Paths.get("gradlew"));
    copyWithDirs(sourceDir, destinationDir, Paths.get("gradlew.bat"));
    copyWithDirs(sourceDir, destinationDir, Paths.get("gradle/wrapper/gradle-wrapper.jar"));
    copyWithDirs(sourceDir, destinationDir, Paths.get("gradle/wrapper/gradle-wrapper.properties"));
  }

  private static void copyWithDirs(
      @Nonnull final Path sourceDir, @Nonnull final Path destinationDir, @Nonnull final Path file)
      throws IOException {
    Path destinationFile = destinationDir.resolve(file);
    if (!Files.exists(destinationFile.getParent())) {
      Files.createDirectories(destinationFile.getParent());
    }
    Files.copy(sourceDir.resolve(file), destinationFile);
  }

  private static void writeProjectBuildScriptWithPluginsBlock(@Nonnull final Path projectDir)
      throws IOException {
    Files.write(
        projectDir.resolve("build.gradle.kts"),
        ("plugins { id(\"" + Constants.PLUGIN_ID + "\") }").getBytes(UTF_8));
  }

  private void assertWrapperPropertyEquals(
      @Nonnull final Path projectDir, @Nonnull final String key, @Nonnull final String value)
      throws IOException {
    Properties properties = new Properties();
    properties.load(
        Files.newBufferedReader(projectDir.resolve("gradle/wrapper/gradle-wrapper.properties")));
    assertEquals(value, properties.getProperty(key));
  }
}
