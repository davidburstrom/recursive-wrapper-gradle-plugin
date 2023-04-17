# Recursive Wrapper Gradle Plugin

When using [Gradle composite builds](https://docs.gradle.org/current/userguide/composite_builds.html),
each included build is an independent project and needs its own Gradle wrapper. Keeping these in sync
and up to date can be tedious.

What if it was possible to run `./gradlew :wrapper --gradle-version=8.1.0-rc-3` in the root project
and update the wrappers in all included projects at the same time?

Simply apply the following plugin in the root project `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.davidburstrom.recursive-wrapper") version "0.1.0-beta-1"
}
```

and the plugin will deal with the rest when the `:wrapper` task is invoked, propagating
relevant values, and even boostrapping the wrapper files if necessary.

For a Groovy `build.gradle` the equivalent is:

```groovy
plugins {
    id 'io.github.davidburstrom.recursive-wrapper' version '0.1.0-beta-1'
}
```

All standard `:wrapper` arguments are supported, e.g. `--gradle-distribution-sha256-sum`, etc.

In case the root project is using Gradle dependency verification, it is either necessary to add the
plugin verification information in the included builds, or to execute the wrapper task with
`--dependency-verification=lenient`.

The plugin has been tested on all Gradle versions from 6.0.1 to 8.1.0-rc-3.

## Releases

* Unreleased
  * Support temporarily disabling dependency verification in included builds.
* 0.1.0-beta-2
  * Support bootstrapping Gradle Wrapper files in included builds if they are missing.
* 0.1.0-beta-1
  * The initial release.

## License

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Copyright 2023 David Burström.

# Acknowledgements

* [Nelson Osacky](mailto:nelson@osacky.com) - for invaluable Gradle insights
* [Remco Mokveld](https://github.com/remcomokveld) - for bringing the problem to my attention in the first place
