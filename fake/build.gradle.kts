import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    id("funekit.fence")
}

group = "net.seilent.funekit"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

tasks.named<KotlinCompile>("compileKotlin") { compilerOptions.moduleName.set("funekit-fake") }

dependencies {
    api(project(":api"))
    implementation(project(":core"))
    testImplementation(testFixtures(project(":api")))
}
