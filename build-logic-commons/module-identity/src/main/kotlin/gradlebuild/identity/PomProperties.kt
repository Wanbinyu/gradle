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
import gradlebuild.identity.tasks.GeneratePomProperties
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/**
 * Registers a [GeneratePomProperties] task producing a Maven-style `pom.properties`
 * for a distribution jar of this module. The `groupId` is the project group
 * (`org.gradle`) and the `version` is the module base version — the same coordinates
 * the module is published under. The caller wires the generated resource into the
 * relevant jar.
 *
 * @param taskName the name of the generator task; also used for its output directory
 * @param artifactId the Maven artifact id, e.g. the jar's `archiveBaseName`
 */
fun Project.registerPomPropertiesTask(
    taskName: String,
    artifactId: Provider<String>
): TaskProvider<GeneratePomProperties> {
    // Captured eagerly: module-identity sets the group before this is called, so the
    // value is a plain String and stays configuration-cache friendly.
    val moduleGroupId = group.toString()
    val identity = extensions.getByType(GradleModuleExtension::class.java).identity
    // Use the full version so permanently published builds (milestones, RCs) remain
    // identifiable. Nightly/snapshot versions embed a per-build timestamp, which would
    // break reproducibility, so that timestamp is replaced with "SNAPSHOT" (the Maven
    // convention for non-final versions). This affects only the pom.properties content,
    // not the jar file name.
    val moduleVersion = identity.version.zip(identity.buildTimestamp.orElse("")) { version, timestamp ->
        if (timestamp.isEmpty()) version.version else version.version.replace(timestamp, "SNAPSHOT")
    }
    return tasks.register(taskName, GeneratePomProperties::class.java) {
        this.groupId.set(moduleGroupId)
        this.artifactId.set(artifactId)
        this.version.set(moduleVersion)
        destinationDirectory.set(layout.buildDirectory.dir("generated-resources/$taskName"))
    }
}
