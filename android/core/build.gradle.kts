import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}

tasks.named<ProcessResources>("processTestResources") {
    from(rootProject.projectDir.resolve("../data/w124")) {
        include("state.json")
        rename { "seed-state.json" }
    }
    from(rootProject.projectDir.resolve("../data/w124")) {
        include("jobs.json", "tools.json", "inbox.json")
    }
}
