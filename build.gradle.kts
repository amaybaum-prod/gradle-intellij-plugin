// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)
fun Jar.patchManifest() = manifest { attributes("Version" to project.version) }

plugins {
    `kotlin-dsl`
    `maven-publish`
    kotlin("jvm") version "1.8.10"
    kotlin("plugin.serialization") version "1.8.10"
    id("org.jetbrains.kotlin.plugin.sam.with.receiver") version "1.8.10"
    id("com.gradle.plugin-publish") version "1.1.0"
    id("org.jetbrains.changelog") version "2.0.0"
    id("org.jetbrains.dokka") version "1.8.10"
//    id("org.barfuin.gradle.taskinfo") version "2.0.0" // TODO: use whenever it supports Gradle 7.6
}

version = when (properties("snapshot").get().toBoolean()) {
    true -> properties("snapshotVersion").map { "$it-SNAPSHOT"}
    false -> properties("version")
}.get()
group = properties("group").get()
description = properties("description").get()

repositories {
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
    maven("https://plugins.gradle.org/m2")
    mavenCentral()
}

val additionalPluginClasspath: Configuration by configurations.creating

dependencies {
    implementation("org.jetbrains:annotations:24.0.1")
    implementation("org.jetbrains.intellij.plugins:structure-base:3.251") {
        exclude("org.jetbrains.kotlin")
    }
    implementation("org.jetbrains.intellij.plugins:structure-intellij:3.251") {
        exclude("org.jetbrains.kotlin")
    }
    implementation("org.jetbrains.intellij:plugin-repository-rest-client:2.0.30") {
        exclude("org.jetbrains.kotlin")
        exclude("org.slf4j")
    }

    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    implementation("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")
    implementation("com.googlecode.plist:dd-plist:1.26")

    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")
    additionalPluginClasspath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")

    api("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:1.1.7")
    api("com.squareup.retrofit2:retrofit:2.9.0")

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

kotlin {
    jvmToolchain(11)
}

@Suppress("UnstableApiUsage")
gradlePlugin {
    website.set(properties("website"))
    vcsUrl.set(properties("vcsUrl"))

    plugins.create("intellijPlugin") {
        id = properties("pluginId").get()
        displayName = properties("pluginDisplayName").get()
        implementationClass = properties("pluginImplementationClass").get()
        description = project.description
        tags.set(properties("tags").map { it.split(',') })
    }
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
            apiVersion = "1.4"
            languageVersion = "1.4"
        }
    }

    wrapper {
        gradleVersion = properties("gradleVersion").get()
        distributionUrl = "https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-$gradleVersion-all.zip"
    }

    pluginUnderTestMetadata {
        pluginClasspath.from(additionalPluginClasspath)
    }

    test {
        val testGradleHomePath = properties("testGradleUserHome").getOrElse("$buildDir/testGradleHome")
        doFirst {
            File(testGradleHomePath).mkdir()
        }
        systemProperties["test.gradle.home"] = testGradleHomePath
        systemProperties["test.gradle.scan"] = project.gradle.startParameter.isBuildScan
        systemProperties["test.kotlin.version"] = properties("kotlinVersion").get()
        systemProperties["test.gradle.default"] = properties("gradleVersion").get()
        systemProperties["test.gradle.version"] = properties("testGradleVersion").get()
        systemProperties["test.gradle.arguments"] = properties("testGradleArguments").get()
        systemProperties["test.intellij.version"] = properties("testIntelliJVersion").get()
        systemProperties["test.markdownPlugin.version"] = properties("testMarkdownPluginVersion").get()
        systemProperties["plugins.repository"] = properties("pluginsRepository").get()
        outputs.dir(testGradleHomePath)

// Verbose tests output used for debugging tasks:
//        testLogging {
//            outputs.upToDateWhen { false }
//            showStandardStreams = true
//        }
    }

    jar {
        patchManifest()
    }

    validatePlugins {
        enableStricterValidation.set(true)
    }
}

val dokkaHtml by tasks.getting(DokkaTask::class)
val javadocJar by tasks.registering(Jar::class) {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.outputDirectory)
    patchManifest()
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
    patchManifest()
}

artifacts {
    archives(javadocJar)
    archives(sourcesJar)
}

publishing {
    repositories {
        maven {
            name = "snapshot"
            url = uri(properties("snapshotUrl").get())
            credentials {
                username = properties("ossrhUsername").get()
                password = properties("ossrhPassword").get()
            }
        }
    }
    publications {
        create<MavenPublication>("snapshot") {
            groupId = properties("group").get()
            artifactId = properties("artifactId").get()
            version = version.toString()

            from(components["java"])

            pom {
                name.set(properties("pluginDisplayName"))
                description.set(project.description)
                url.set(properties("website"))

                packaging = "jar"

                scm {
                    connection.set(properties("scmUrl"))
                    developerConnection.set(properties("scmUrl"))
                    url.set(properties("vcsUrl"))
                }

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("zolotov")
                        name.set("Alexander Zolotov")
                        email.set("zolotov@jetbrains.com")
                    }
                    developer {
                        id.set("hsz")
                        name.set("Jakub Chrzanowski")
                        email.set("jakub.chrzanowski@jetbrains.com")
                    }
                }
            }
        }
    }
}

changelog {
    unreleasedTerm.set("next")
    groups.empty()
    repositoryUrl.set("https://github.com/JetBrains/gradle-intellij-plugin")
}
