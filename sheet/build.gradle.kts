import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    id("funekit.fence")
}

group = "net.seilent.funekit"

android {
    namespace = "net.seilent.funekit.sheet"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<KotlinCompile>().configureEach {
    if (!name.contains("Test")) compilerOptions.moduleName.set("funekit-sheet")
}

dependencies {
    api(project(":kit"))
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
}
