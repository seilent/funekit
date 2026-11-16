import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-test-fixtures`
    id("funekit.fence")
    id("funekit.noscrape")
}

group = "net.seilent.funekit"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    target.compilations.getByName("testFixtures").associateWith(target.compilations.getByName("main"))
}

tasks.named<KotlinCompile>("compileKotlin") { compilerOptions.moduleName.set("funekit-artist") }

dependencies {
    api(project(":core"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    systemProperty("http.proxyHost", "127.0.0.1")
    systemProperty("http.proxyPort", "9")
    systemProperty("https.proxyHost", "127.0.0.1")
    systemProperty("https.proxyPort", "9")
}
