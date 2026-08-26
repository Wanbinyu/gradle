/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gradlebuild.identity

import gradlebuild.identity.extension.GradleModuleExtension
import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * The Gradle version to record in the metadata of a distribution jar — the manifest's
 * `Implementation-Version` and the Maven `pom.properties`.
 *
 * This is the *full* version, so that permanently published builds (milestones, RCs) stay
 * identifiable and the jar reports the same version it is published under. Nightly/snapshot
 * versions embed a per-build timestamp, which would make the metadata — and therefore the jar —
 * differ on every build, so that timestamp is replaced with `SNAPSHOT`, the Maven convention for
 * non-final versions. That keeps the distribution jars reproducible.
 *
 * This affects jar *contents* only. Jar file names keep the base version via `archiveVersion`.
 */
fun Project.reproducibleFullVersion(): Provider<String> {
    val identity = extensions.getByType(GradleModuleExtension::class.java).identity
    return identity.version.zip(identity.buildTimestamp.orElse("")) { version, timestamp ->
        if (timestamp.isEmpty()) version.version else version.version.replace(timestamp, "SNAPSHOT")
    }
}
