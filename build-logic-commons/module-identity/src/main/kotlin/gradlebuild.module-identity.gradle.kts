/*
 * Copyright 2020 the original author or authors.
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

import gradlebuild.basics.buildFinalRelease
import gradlebuild.basics.buildMilestoneNumber
import gradlebuild.basics.buildRcNumber
import gradlebuild.basics.buildTimestamp
import gradlebuild.basics.buildVersionQualifier
import gradlebuild.basics.isPromotionBuild
import gradlebuild.basics.releasedVersionsFile
import gradlebuild.basics.repoRoot
import gradlebuild.identity.extension.GradleModuleExtension
import gradlebuild.identity.reproducibleFullVersion
import gradlebuild.identity.extension.ReleasedVersionsDetails
import java.util.Optional
import java.util.jar.Attributes

plugins {
    `java-base`
    id("org.gradle.pom-properties")
}

val gradleModule = extensions.create<GradleModuleExtension>(GradleModuleExtension.NAME).apply {
    published = false

    requiredRuntimes {
        client = false
        daemon = false
        worker = false
    }

    computedRuntimes {
        client = false
        daemon = false
        worker = false
    }

    // TODO: Most of these properties are the same across projects. We should
    // compute these at the settings-level instead of the project-level.
    identity {
        baseName = "gradle-$name"
        buildTimestamp = buildTimestamp()
        promotionBuild = isPromotionBuild

        val finalReleaseSuffix = buildFinalRelease.map { "" }
        val rcSuffix = buildRcNumber.map { "-rc-$it" }
        val milestoneSuffix = buildMilestoneNumber.map { "-milestone-$it" }
        val buildVersionQualifierSuffix = buildVersionQualifier.zip(buildTimestamp) { buildVersion, timestamp -> "-$buildVersion-$timestamp" }
        val buildTimestampSuffix = buildTimestamp.map { "-$it" }

        val specifiedSuffix = atMostOneOf(finalReleaseSuffix, rcSuffix, milestoneSuffix)
        val computedSuffix = specifiedSuffix
            .orElse(buildVersionQualifierSuffix)
            .orElse(buildTimestampSuffix)

        val baseVersion = trimmedContentsOfFile("version.txt")
        version = baseVersion.zip(computedSuffix) { base, suffix -> GradleVersion.version("$base$suffix") }
        snapshot = specifiedSuffix.map { false }.orElse(true)
        releasedVersions = version.map {
            ReleasedVersionsDetails(
                it.baseVersion,
                releasedVersionsFile()
            )
        }
    }
}

class LazyProjectVersion(private val version: Provider<String>) {
    override fun toString(): String = version.get()
}

group = "org.gradle"
version = LazyProjectVersion(gradleModule.identity.version.map { it.version })

// The version recorded inside the jar. The file name keeps the base version (`archiveVersion`
// below), but the metadata records the full version so a jar reports the version it is actually
// published under. See `reproducibleFullVersion`.
val jarMetadataVersion = reproducibleFullVersion()

tasks.withType<Jar>().configureEach {
    archiveBaseName = gradleModule.identity.baseName
    archiveVersion = gradleModule.identity.version.map { it.baseVersion.version }
    manifest.attributes(
        mapOf(
            // Product evidence. Kept as "Gradle" rather than the module name so it matches the
            // product of `cpe:2.3:a:gradle:gradle`, which is how CPE-based scanners key Gradle.
            Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
            Attributes.Name.IMPLEMENTATION_VERSION.toString() to jarMetadataVersion,
            // Vendor evidence, so a scanner reading only the manifest can still identify the
            // artifact. IMPLEMENTATION_VENDOR_ID carries the groupId, mirroring pom.properties.
            // Both follow the Maven Archiver convention (project.organization.name and
            // project.groupId), which is what these scanners are written against.
            Attributes.Name.IMPLEMENTATION_VENDOR.toString() to "Gradle Technologies",
            Attributes.Name.IMPLEMENTATION_VENDOR_ID.toString() to group.toString()
        )
    )
}

// The org.gradle.pom-properties plugin adds a Maven-style pom.properties to the standard `jar`.
// Override the coordinates to match how Gradle modules are published (gradle-<name>), using the
// same recorded version as the manifest. groupId defaults to project.group ("org.gradle").
pomProperties {
    artifactId = gradleModule.identity.baseName
    version = jarMetadataVersion
}

// The plugin only wires the standard `jar`. When the Shadow plugin is applied, the distribution
// ships the `shadowJar` output in its place, so it needs the same pom.properties.
pluginManager.withPlugin("com.gradleup.shadow") {
    tasks.named<Jar>("shadowJar") {
        from(tasks.named("generatePomProperties"))
    }
}

/**
 * Returns the trimmed contents of the file at the given [path] after
 * marking the file as a build logic input.
 */
fun Project.trimmedContentsOfFile(path: String): Provider<String> =
    providers.fileContents(repoRoot().file(path)).asText.map { it.trim() }


/**
 * Returns a new provider that takes its value from at most one
 * of the given providers. If no input provider is present, the output
 * provider will not be present. If more than one input provider
 * has a value specified, the resulting provider will throw an
 * exception when queried.
 */
fun <T: Any> atMostOneOf(vararg providers: Provider<T>): Provider<T> {
    return providers.map { provider ->
        provider.map {
            Optional.of(it)
        }.orElse(
            Optional.empty<T>()
        )
    }.reduce { acc, next ->
        acc.zip(next) { left, right ->
            when {
                left.isPresent -> {
                    require(!right.isPresent) {
                        "Expected at most one provider to be present"
                    }
                    left
                }
                else -> right
            }
        }
    }.map { it.orElse(null) }
}
