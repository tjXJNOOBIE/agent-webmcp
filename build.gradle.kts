import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

plugins {
    application
    java
}

group = "org.tavall.agentwebmcp"
version = "0.1.0-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.18.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")
    implementation("org.tavall:tavall-logging") {
        version { branch = "main" }
    }

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

application {
    mainClass.set("org.tavall.agentwebmcp.AgentWebMcpApplication")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxParallelForks = 1
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val npmCi = tasks.register<Exec>("npmCi") {
    inputs.files("package.json", "package-lock.json")
    outputs.dir("node_modules")
    commandLine("npm", "ci")
}

val e2eInstallBrowser = tasks.register<Exec>("e2eInstallBrowser") {
    dependsOn(npmCi)
    commandLine("npx", "playwright", "install", "chromium")
}

val e2e = tasks.register<Exec>("e2e") {
    group = "verification"
    description = "Runs browser E2E coverage against the installed Agent WebMCP runtime."
    dependsOn(tasks.named("installDist"), e2eInstallBrowser)
    environment("AGENT_WEBMCP_DIST", layout.buildDirectory.dir("install/agent-webmcp").get().asFile.absolutePath)
    commandLine("npm", "run", "test:e2e")
}

tasks.named("check") {
    dependsOn(e2e)
}
