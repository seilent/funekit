import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("funekit.syntax")
    id("funekit.fence")
}

group = "net.seilent.funekit"

android {
    namespace = "net.seilent.funekit.screens"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2g"
                it.systemProperty("http.proxyHost", "127.0.0.1")
                it.systemProperty("http.proxyPort", "9")
                it.systemProperty("https.proxyHost", "127.0.0.1")
                it.systemProperty("https.proxyPort", "9")
            }
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    if (!name.contains("Test")) compilerOptions.moduleName.set("funekit-screens")
}

funekitSyntax {
    repo.set(layout.projectDirectory.dir(".."))
    roots.set(listOf("screens/src/main/kotlin"))
    literals.set(layout.projectDirectory.file("literals.txt"))
    fallbacks.set(layout.projectDirectory.file("fallbacks.txt"))
}

dependencies {
    api(project(":api"))
    api(project(":kit"))
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core:1.18.0")
    implementation("androidx.window:window:1.5.1")
    add("funePsi", "org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
    testImplementation(project(":fake"))
    testImplementation(project(":sheet"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
