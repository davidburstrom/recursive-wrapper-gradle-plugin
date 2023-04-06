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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "It just copies files if necessary, which means it's I/O bound")
public class WrapperBootstrapTask extends DefaultTask {

  public File rootProjectDir;
  public File includedBuildDir;

  @TaskAction
  public void run() throws IOException {
    Path rootProjectPath = rootProjectDir.toPath();
    Path includedBuildPath = includedBuildDir.toPath();
    Path gradlew = Paths.get("gradlew");
    Path gradlewBat = Paths.get("gradlew.bat");
    Path gradleWrapperJar = Paths.get("gradle-wrapper.jar");
    Path gradleWrapperProperties = Paths.get("gradle-wrapper.properties");
    Path gradleWrapperDir = Paths.get("gradle/wrapper");

    Path targetGradlew = includedBuildPath.resolve(gradlew);
    if (!Files.exists(targetGradlew)) {
      Files.copy(rootProjectPath.resolve(gradlew), targetGradlew);
    }

    Path targetGradlewBat = includedBuildPath.resolve(gradlewBat);
    if (!Files.exists(targetGradlewBat)) {
      Files.copy(rootProjectPath.resolve(gradlewBat), targetGradlewBat);
    }

    Path targetGradleWrapperDir = includedBuildPath.resolve(gradleWrapperDir);
    if (!Files.exists(targetGradleWrapperDir)) {
      Files.createDirectories(targetGradleWrapperDir);
    }

    Path targetGradleWrapperJar = targetGradleWrapperDir.resolve(gradleWrapperJar);
    if (!Files.exists(targetGradleWrapperJar)) {
      Files.copy(
          rootProjectPath.resolve(gradleWrapperDir).resolve(gradleWrapperJar),
          targetGradleWrapperJar);
    }

    Path targetGradleWrapperProperties = targetGradleWrapperDir.resolve(gradleWrapperProperties);
    if (!Files.exists(targetGradleWrapperProperties)) {
      Files.copy(
          rootProjectPath.resolve(gradleWrapperDir).resolve(gradleWrapperProperties),
          targetGradleWrapperProperties);
    }
  }
}
