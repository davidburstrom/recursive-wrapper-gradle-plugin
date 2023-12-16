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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.vdurmont.semver4j.Semver;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
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
    SettingsFileWriter.create(projectDir).write();

    bootstrapWrappers(projectDir);

    writeProjectBuildScriptWithPluginsBlock(projectDir);

    getGradleRunner(projectDir)
        .withArguments(getArguments(RecursiveWrapperPlugin.TESTING_PROPERTY_ARG))
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
    SettingsFileWriter.create(projectDir).setPostamble("includeBuild(\"subproject\")").write();
    Path subprojectDir = IncludedProjectWriter.create(projectDir, "subproject").write();

    bootstrapWrappers(projectDir);

    writeProjectBuildScriptWithPluginsBlock(projectDir);

    getGradleRunner(projectDir)
        .withArguments(getArguments(RecursiveWrapperPlugin.TESTING_PROPERTY_ARG))
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
    SettingsFileWriter.create(projectDir)
        .setPostamble("includeBuild(\"dir/subproject\")\n")
        .write();

    Path subprojectDir = IncludedProjectWriter.create(projectDir, "dir/subproject").write();

    bootstrapWrappers(projectDir);

    writeProjectBuildScriptWithPluginsBlock(projectDir);

    getGradleRunner(projectDir)
        .withArguments(getArguments(RecursiveWrapperPlugin.TESTING_PROPERTY_ARG))
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
    SettingsFileWriter.create(projectDir).setPostamble("includeBuild(\"subproject\")").write();
    Path subprojectDir =
        IncludedProjectWriter.create(projectDir, "subproject")
            .setSettingsFileCreator(
                SettingsFileWriter.create().setPostamble("includeBuild(\"subsubproject\")"))
            .write();
    Path subsubprojectDir = IncludedProjectWriter.create(subprojectDir, "subsubproject").write();

    bootstrapWrappers(projectDir);
    writeProjectBuildScriptWithPluginsBlock(projectDir);

    getGradleRunner(projectDir)
        .withArguments(
            getArguments("--distribution-type=all", RecursiveWrapperPlugin.TESTING_PROPERTY_ARG))
        .build();

    assertWrapperPropertyEquals(
        subsubprojectDir,
        "distributionUrl",
        "https://services.gradle.org/distributions/gradle-"
            + System.getProperty("GRADLE_VERSION")
            + "-all.zip");
  }

  @Test
  void updatesIncludedBuildFromPluginManagement(@TempDir Path projectDir) throws IOException {
    SettingsFileWriter.create(projectDir)
        .setPreamble("pluginManagement { includeBuild(\"subproject\") }\n")
        .write();
    Path subprojectDir = IncludedProjectWriter.create(projectDir, "subproject").write();

    bootstrapWrappers(projectDir);
    writeProjectBuildScriptWithPluginsBlock(projectDir);

    getGradleRunner(projectDir)
        .withArguments(
            getArguments("--distribution-type=all", RecursiveWrapperPlugin.TESTING_PROPERTY_ARG))
        .build();

    assertWrapperPropertyEquals(
        subprojectDir,
        "distributionUrl",
        "https://services.gradle.org/distributions/gradle-"
            + System.getProperty("GRADLE_VERSION")
            + "-all.zip");
  }

  @Test
  void setsExplicitProperties(@TempDir Path projectDir) throws IOException {
    SettingsFileWriter.create(projectDir).setPostamble("includeBuild(\"subproject\")").write();

    Path subprojectDir = IncludedProjectWriter.create(projectDir, "subproject").write();

    bootstrapWrappers(projectDir);

    writeProjectBuildScriptWithPluginsBlock(projectDir);

    getGradleRunner(projectDir)
        .withArguments(
            getArguments(
                "--gradle-distribution-url=https://services.gradle.org/distributions/gradle-8.0.2-bin.zip",
                "--gradle-distribution-sha256-sum=ff7bf6a86f09b9b2c40bb8f48b25fc19cf2b2664fd1d220cd7ab833ec758d0d7",
                RecursiveWrapperPlugin.TESTING_PROPERTY_ARG))
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
    SettingsFileWriter.create(projectDir).setPostamble("includeBuild(\"subproject\")").write();
    Path subprojectDir = IncludedProjectWriter.create(projectDir, "subproject").write();

    bootstrapWrappers(projectDir);
    writeProjectBuildScriptWithPluginsBlock(projectDir);
    writeProjectBuildScriptWithPluginsBlock(subprojectDir);

    getGradleRunner(projectDir)
        .withArguments(
            getArguments("--distribution-type=all", RecursiveWrapperPlugin.TESTING_PROPERTY_ARG))
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
    SettingsFileWriter.create(projectDir)
        .setPostamble("includeBuild(\"dir1/subproject\"); includeBuild(\"dir2/subproject\")")
        .write();

    IncludedProjectWriter.create(projectDir, "dir1/subproject").write();
    IncludedProjectWriter.create(projectDir, "dir2/subproject").write();

    writeProjectBuildScriptWithPluginsBlock(projectDir);

    BuildResult buildResult =
        getGradleRunner(projectDir).withArguments(getArguments()).buildAndFail();

    assertThat(buildResult.getOutput()).contains("which is the same");
  }

  @Test
  void failsIfTestingSystemPropertyIsNotSuppliedInArguments(@TempDir Path projectDir)
      throws IOException {
    assumeGradleSupportsDependencyVerification();
    SettingsFileWriter.create(projectDir).setPostamble("includeBuild(\"subproject\")").write();
    IncludedProjectWriter.create(projectDir, "subproject").write();

    bootstrapWrappers(projectDir);
    writeProjectBuildScriptWithPluginsBlock(projectDir);

    BuildResult buildResult =
        getGradleRunner(projectDir)
            .withArguments(getArguments("--distribution-type=all"))
            .buildAndFail();

    assertThat(buildResult.getOutput())
        .contains(Constants.GROUP + ":" + Constants.ID + ":" + Constants.VERSION);
  }

  private static final String BARE_MINIMUM_DEPENDENCY_VERIFICATION =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<verification-metadata>\n"
          + "   <configuration>\n"
          + "      <verify-metadata>true</verify-metadata>\n"
          + "      <verify-signatures>true</verify-signatures>\n"
          + "    </configuration>\n"
          + "</verification-metadata>";

  private static final String BUILDSCRIPT_FAKE_DEPENDENCY =
      "buildscript {\n"
          + "    repositories {\n"
          + "        maven {\n"
          + "            url = uri(file(\"repo\"))\n"
          + "            metadataSources {\n"
          + "                artifact()\n"
          + "            }\n"
          + "        }\n"
          + "    }\n"
          + "    dependencies {\n"
          + "        classpath(\"a:b:c\")\n"
          + "    }\n"
          + "}";

  @Test
  void passesIfSubprojectRequiresDependencyVerificationAndExplicitlyDisabled(
      @TempDir Path projectDir) throws IOException {
    assumeGradleSupportsDependencyVerification();
    SettingsFileWriter.create(projectDir).setPostamble("includeBuild(\"subproject\")").write();
    Path subprojectDir =
        IncludedProjectWriter.create(projectDir, "subproject")
            .setSettingsFileCreator(
                SettingsFileWriter.create().addBuildscriptBlockSnippet(BUILDSCRIPT_FAKE_DEPENDENCY))
            .write();
    writeVerificationMetadata(subprojectDir);
    writeFakeMavenRepo(subprojectDir);

    bootstrapWrappers(projectDir);
    writeProjectBuildScriptWithPluginsBlock(projectDir);

    BuildResult buildResult =
        getGradleRunner(projectDir)
            .withArguments(
                getArguments(
                    "--dependency-verification=lenient",
                    RecursiveWrapperPlugin.TESTING_PROPERTY_ARG))
            .build();

    assertThat(buildResult.getOutput()).contains("failed verification");
  }

  @Test
  void failsIfSubprojectRequiresDependencyVerification(@TempDir Path projectDir)
      throws IOException {
    assumeGradleSupportsDependencyVerification();
    SettingsFileWriter.create(projectDir).setPostamble("includeBuild(\"subproject\")").write();
    Path subprojectDir =
        IncludedProjectWriter.create(projectDir, "subproject")
            .setSettingsFileCreator(
                SettingsFileWriter.create().addBuildscriptBlockSnippet(BUILDSCRIPT_FAKE_DEPENDENCY))
            .write();
    writeVerificationMetadata(subprojectDir);
    writeFakeMavenRepo(subprojectDir);

    bootstrapWrappers(projectDir);
    writeProjectBuildScriptWithPluginsBlock(projectDir);

    BuildResult buildResult =
        getGradleRunner(projectDir)
            .withArguments(getArguments(RecursiveWrapperPlugin.TESTING_PROPERTY_ARG))
            .buildAndFail();

    assertThat(buildResult.getOutput()).contains("failed verification");
  }

  private static void assumeGradleSupportsDependencyVerification() {
    assumeTrue(
        new Semver(System.getProperty("GRADLE_VERSION"), Semver.SemverType.LOOSE)
            .isGreaterThanOrEqualTo(new Semver("6.2.0", Semver.SemverType.LOOSE)));
  }

  private static void writeVerificationMetadata(final Path subprojectDir) throws IOException {
    Files.createDirectories(subprojectDir.resolve("gradle"));
    Files.write(
        subprojectDir.resolve("gradle/verification-metadata.xml"),
        BARE_MINIMUM_DEPENDENCY_VERIFICATION.getBytes(Charset.defaultCharset()));
  }

  private static void writeFakeMavenRepo(final Path subprojectDir) throws IOException {
    Path artifactDirectory = subprojectDir.resolve("repo/a/b/c/");
    Files.createDirectories(artifactDirectory);
    Files.createFile(artifactDirectory.resolve("b-c.jar"));
  }

  /** Invokes Gradle in order to write the wrapper infrastructure in the given directory. */
  private static void bootstrapWrappers(final Path directory) {
    getGradleRunner(directory)
        .withArguments(getArguments())
        .withProjectDir(directory.toFile())
        .build();
  }

  private static GradleRunner getGradleRunner(@Nonnull Path projectDir) {
    return GradleRunner.create()
        .forwardOutput()
        .withPluginClasspath()
        .withProjectDir(projectDir.toFile())
        .withGradleVersion(System.getProperty("GRADLE_VERSION"));
  }

  private static List<String> getArguments(String... arguments) {
    return Stream.concat(Stream.of(":wrapper", "--stacktrace"), Arrays.stream(arguments))
        .collect(Collectors.toList());
  }

  @Nonnull
  private static String getSettingsScriptWithPluginClasspath(
      final List<String> buildscriptBlockSnippets,
      @Nonnull final String settingsPreamble,
      @Nonnull final String settingsPostamble) {
    StringBuilder sb = new StringBuilder();
    sb.append(settingsPreamble);
    sb.append("buildscript {\n");
    for (final String buildscriptBlockSnippet : buildscriptBlockSnippets) {
      sb.append(buildscriptBlockSnippet);
    }
    sb.append("}\n");
    sb.append(settingsPostamble);
    return sb.toString();
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
    assertThat(value).isEqualTo(properties.getProperty(key));
  }

  private static class SettingsFileWriter {
    private Path projectDir;
    private String settingsPreamble = "";
    private String settingsPostamble = "";

    private final List<String> buildscriptBlockSnippets = new ArrayList<>();

    static SettingsFileWriter create() {
      return new SettingsFileWriter();
    }

    static SettingsFileWriter create(Path projectDir) {
      return new SettingsFileWriter().setProjectDir(projectDir);
    }

    private SettingsFileWriter() {
      StringBuilder sb = new StringBuilder();
      sb.append("  dependencies {\n");
      String collect =
          PLUGIN_CLASSPATH.stream()
              .map(it -> "\"" + it.toString() + "\"")
              .collect(Collectors.joining(", "));
      sb.append("    classpath(files(").append(collect).append("))\n");
      sb.append("  }\n");
      addBuildscriptBlockSnippet(sb.toString());
    }

    private SettingsFileWriter setProjectDir(final Path projectDir) {
      this.projectDir = projectDir;
      return this;
    }

    private SettingsFileWriter setPreamble(String settingsPreamble) {
      this.settingsPreamble = settingsPreamble;
      return this;
    }

    private SettingsFileWriter setPostamble(String settingsPostamble) {
      this.settingsPostamble = settingsPostamble;
      return this;
    }

    private SettingsFileWriter addBuildscriptBlockSnippet(String snippet) {
      buildscriptBlockSnippets.add(snippet);
      return this;
    }

    private void write() throws IOException {
      Files.write(
          projectDir.resolve("gradle.properties"), "org.gradle.jvmargs=-Xmx128m\n".getBytes(UTF_8));
      Files.write(
          projectDir.resolve("settings.gradle.kts"),
          getSettingsScriptWithPluginClasspath(
                  buildscriptBlockSnippets, settingsPreamble, settingsPostamble)
              .getBytes(UTF_8));
    }
  }

  private static class IncludedProjectWriter {
    private final Path parentProjectDir;
    private final String name;
    private SettingsFileWriter settingsFileWriter = SettingsFileWriter.create();

    private IncludedProjectWriter(Path parentProjectDir, String name) {
      this.parentProjectDir = parentProjectDir;
      this.name = name;
    }

    static IncludedProjectWriter create(Path parentProjectDir, String name) {
      return new IncludedProjectWriter(parentProjectDir, name);
    }

    private IncludedProjectWriter setSettingsFileCreator(
        final SettingsFileWriter settingsFileWriter) {
      this.settingsFileWriter = settingsFileWriter;
      return this;
    }

    private Path write() throws IOException {
      Path includedProjectDir = parentProjectDir.resolve(name);
      Files.createDirectories(includedProjectDir);
      settingsFileWriter.setProjectDir(includedProjectDir).write();
      return includedProjectDir;
    }
  }
}
