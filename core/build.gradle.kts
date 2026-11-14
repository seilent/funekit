import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-test-fixtures`
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

tasks.named<KotlinCompile>("compileKotlin") { compilerOptions.moduleName.set("funekit-core") }

dependencies {
    testImplementation("junit:junit:4.13.2")
}
