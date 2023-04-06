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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.wrapper.Wrapper;

public class RecursiveWrapperPlugin implements Plugin<Project> {

  static final String TESTING_PROPERTY =
      RecursiveWrapperPlugin.class.getPackage().getName() + ".testing";
  static final String TESTING_PROPERTY_ARG = "-D" + TESTING_PROPERTY;

  @Override
  public void apply(@Nonnull final Project project) {
    if (project.getParent() != null) {
      throw new GradleException("This plugin can only be applied on the root project");
    }

    Gradle gradle = project.getGradle();
    TaskContainer tasks = project.getTasks();

    /*
     * Ideally this should be a GradleBuild instead. However,
     * as of Gradle 8.0.2, it's not possible to run a GradleBuild on
     * a project that includes other projects, which means that
     * recursive wrapper updates break.
     */
    List<TaskProvider<Exec>> includedBuildWrapperExecs =
        gradle.getIncludedBuilds().stream()
            .map(
                includedBuild -> {
                  TaskProvider<WrapperBootstrapTask> bootstrapWrapper =
                      tasks.register(
                          "bootstrapWrapper" + includedBuild.getName(),
                          WrapperBootstrapTask.class,
                          task -> {
                            task.rootProjectDir = project.getProjectDir();
                            task.includedBuildDir = includedBuild.getProjectDir();
                          });

                  return tasks.register(
                      "wrapper" + includedBuild.getName(),
                      Exec.class,
                      task -> {
                        task.setWorkingDir(includedBuild.getProjectDir());
                        task.dependsOn(bootstrapWrapper);
                      });
                })
            .collect(Collectors.toList());

    tasks
        .named("wrapper")
        .configure(
            wrapper -> {
              wrapper.dependsOn(includedBuildWrapperExecs);
            });

    gradle
        .getTaskGraph()
        .whenReady(
            taskExecutionGraph -> {
              /*
               * If the wrapper task was not part of the requested build, there is
               * no need to do anything else.
               */
              if (!taskExecutionGraph.hasTask(":wrapper")) {
                return;
              }

              List<String> commandLine = new ArrayList<>();

              if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
                commandLine.add(".\\gradlew.bat");
              } else {
                commandLine.add("./gradlew");
              }

              commandLine.add(":wrapper");

              /*
               * Let's steal properties from the configured wrapper task.
               * This makes it possible to parameterize the root wrapper task invocation
               * and have the values propagate into the included projects.
               */
              Wrapper wrapper = tasks.named("wrapper", Wrapper.class).get();

              String distributionUrl = wrapper.getDistributionUrl();
              if (distributionUrl != null) {
                commandLine.add("--gradle-distribution-url=" + distributionUrl);
              } else {
                commandLine.add("--gradle-version=" + wrapper.getGradleVersion());
                commandLine.add("--distribution-type=" + wrapper.getDistributionType());
              }

              String distributionSha256Sum = wrapper.getDistributionSha256Sum();
              if (distributionSha256Sum != null) {
                commandLine.add("--gradle-distribution-sha256-sum=" + distributionSha256Sum);
              }

              try {
                Property<Integer> networkTimeout = wrapper.getNetworkTimeout();
                if (networkTimeout.isPresent()) {
                  commandLine.add("--network-timeout=" + networkTimeout.get());
                }
              } catch (NoSuchMethodError ignore) {
                // This property was added in Gradle 7.6
              }

              commandLine.add("--no-daemon");

              /*
               * Write the init script that injects the plugin automatically into
               * the included builds.
               */
              try {
                Path initScriptFile =
                    Files.createTempFile("init-recursive-wrapper-", ".gradle.kts");

                Runtime.getRuntime()
                    .addShutdownHook(
                        new Thread(
                            () -> {
                              try {
                                Files.delete(initScriptFile);
                              } catch (IOException ignore) {
                                // Not much else we can do at shutdown.
                              }
                            }));

                String initScript = formatInitScript();
                Files.write(initScriptFile, initScript.getBytes(StandardCharsets.UTF_8));

                commandLine.add("--init-script");
                commandLine.add(initScriptFile.toString());
              } catch (IOException e) {
                throw new RuntimeException("Unable to write the init script", e);
              }

              /* As a convenience, any spawned builds will inherit the stacktrace setting. */
              if (gradle.getStartParameter().getShowStacktrace() == ShowStacktrace.ALWAYS) {
                commandLine.add("--stacktrace");
              }

              /*
               * Any spawned builds will have to know if tests are being run, in
               * order to support recursive wrapper updates.
               */
              if (System.getProperty(TESTING_PROPERTY) != null) {
                commandLine.add(TESTING_PROPERTY_ARG);
              }

              includedBuildWrapperExecs.forEach(
                  includedBuildWrapper ->
                      includedBuildWrapper.configure(
                          gradleBuild -> gradleBuild.setCommandLine(commandLine)));
            });
  }

  @Nonnull
  private static String formatInitScript() {
    String dependencyAddition;
    if (System.getProperty(TESTING_PROPERTY) == null) {
      dependencyAddition =
          String.format(
              "            buildscript.dependencies.add(\"classpath\", \"%s:%s:%s\")\n",
              Constants.GROUP, Constants.ID, Constants.VERSION);
    } else {
      dependencyAddition = "";
    }
    return String.format(
        "apply<RecursiveWrapperInitPlugin>()\n"
            + "\n"
            + "class RecursiveWrapperInitPlugin : Plugin<Gradle> {\n"
            + "    override fun apply(gradle: Gradle) {\n"
            + "        gradle.rootProject {\n"
            + "%s"
            + "            buildscript.repositories.gradlePluginPortal()\n"
            + "\n"
            + "            afterEvaluate {\n"
            + "                apply(plugin = \"%s\")\n"
            + "            }\n"
            + "        }\n"
            + "    }\n"
            + "}",
        dependencyAddition, Constants.PLUGIN_ID);
  }
}
