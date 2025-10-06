import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.3.11"
    id("com.lovelysystems.gradle") version ("1.2.0")
}

lovely {
    gitProject()
    dockerProject("ghcr.io/lovelysystems/floatha")
    dockerFiles.from(tasks["distTar"].outputs)
}

repositories {
    jcenter()
}

application {
    mainClassName = "AppKt"
    applicationName = "floatha"
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("com.natpryce:konfig:1.6.10.0")
    compile("com.myjeeva.digitalocean:digitalocean-api-client:2.16")

    compile("org.amshove.kluent:kluent:1.45")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
