// ==============================================================================
// thz-agent â€” Standalone AI Coding Agent para THZ-LANG
//
// Assistente de cÃ³digo autÃ´nomo que roda no terminal, similar a Claude Code /
// Cursor / Aider. Suporta modelos locais (llama.cpp) e APIs remotas.
// ==============================================================================

plugins {
    java
    application
    id("com.gradleup.shadow") version "9.6.1"
}

val repoRoot = rootProject.projectDir.resolve("../thz-lang/")
val versionFile = if (file("version.txt").exists()) file("version.txt") else repoRoot.resolve("version.txt")
val thzVersion = if (versionFile.exists()) versionFile.readText().trim() else "0.4.0"

group = "thz.lang"
version = thzVersion

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation("thz.lang:thz-core:$thzVersion")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    // Testes Automatizados
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("thz.lang.agent.ThzAgent")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

tasks.test {
    useJUnitPlatform()
    workingDir = rootProject.projectDir.resolve("../thz-lang/")
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.shadowJar {
    archiveBaseName.set("thz-agent")
    archiveClassifier.set("")
    archiveVersion.set(thzVersion)
}

tasks.register<JavaExec>("agent") {
    group = "application"
    description = "Executa o THZ-Agent"
    mainClass.set("thz.lang.agent.ThzAgent")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir.resolve("../thz-lang/")
    jvmArgs("-Dfile.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED")
    standardInput = System.`in`
}

