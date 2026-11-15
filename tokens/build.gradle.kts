import net.seilent.funekit.logic.GenTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("funekit.gen")
}

group = "net.seilent.funekit"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    sourceSets.named("main") { kotlin.srcDir(tasks.named<GenTask>("gen").flatMap { it.out }) }
}

tasks.named<KotlinCompile>("compileKotlin") { compilerOptions.moduleName.set("funekit-tokens") }

tasks.named("check") { dependsOn(gradle.includedBuild("funekit-logic").task(":test")) }

val syncTest = tasks.register<Exec>("syncTest") {
    group = "verification"
    val scratch = layout.buildDirectory.dir("tmp/syncTest").get().asFile
    doFirst { scratch.mkdirs() }
    environment("TMPDIR", scratch.path)
    commandLine("bash", rootProject.file("tools/funeos-sync-test.sh").path)
}

tasks.named("check") { dependsOn(syncTest) }

dependencies {
    testImplementation("junit:junit:4.13.2")
}
