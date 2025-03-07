import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.changelog)
    alias(libs.plugins.qodana)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(17)
}

allprojects {
    apply {
        with(rootProject.libs.plugins) {
            listOf(kotlin, intelliJPlatform).map { it.get().pluginId }.forEach(::plugin)
        }
    }

    repositories {
        mavenCentral()

        intellijPlatform {
            defaultRepositories()
        }
    }

    dependencies {
        testImplementation(rootProject.libs.junit)

        implementation(rootProject.libs.ktor.server.websockets)
        implementation(rootProject.libs.ktor.server.core)
        implementation(rootProject.libs.ktor.server.netty)
        implementation(rootProject.libs.kotlin.serialization)
        implementation(rootProject.libs.ktor.client.core)
        implementation(rootProject.libs.ktor.client.cio)
        implementation(rootProject.libs.ktor.server.thymeleaf.jvm)
        implementation(rootProject.libs.ktor.server.status.pages)
        implementation(rootProject.libs.model.mapper)


        intellijPlatform {
            create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

            bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

            plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

            pluginVerifier()
            zipSigner()
            testFramework(TestFrameworkType.Platform)
        }
    }

    intellijPlatform {
        pluginConfiguration {
            version = providers.gradleProperty("pluginVersion")

            description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                with(it.lines()) {
                    if (!containsAll(listOf(start, end))) {
                        throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                    }
                    subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
                }
            }

            val changelog = rootProject.project.changelog // local variable for configuration cache compatibility
            changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
                with(changelog) {
                    renderItem(
                        (getOrNull(pluginVersion) ?: getUnreleased())
                            .withHeader(false)
                            .withEmptySections(false),
                        Changelog.OutputType.HTML,
                    )
                }
            }

            ideaVersion {
                sinceBuild = providers.gradleProperty("pluginSinceBuild")
                untilBuild = providers.gradleProperty("pluginUntilBuild")
            }
        }

        signing {
            certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
            privateKey = providers.environmentVariable("PRIVATE_KEY")
            password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
        }

        publishing {
            token = providers.environmentVariable("PUBLISH_TOKEN")
            channels = providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
        }

        pluginVerification {
            ides {
                recommended()
            }
        }
    }

    tasks {
        test {
            useJUnit()
        }
    }

    intellijPlatformTesting {
        runIde {
            register("runIdeForUiTests") {
                task {
                    jvmArgumentProviders += CommandLineArgumentProvider {
                        listOf(
                            "-Drobot-server.port=8082",
                            "-Dide.mac.message.dialogs.as.sheets=false",
                            "-Djb.privacy.policy.text=<!--999.999-->",
                            "-Djb.consents.confirmation.enabled=false",
                        )
                    }
                }

                plugins {
                    robotServerPlugin()
                }
            }
        }
    }
}
