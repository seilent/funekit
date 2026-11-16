plugins {
    `kotlin-dsl`
}

group = "net.seilent.funekit"

gradlePlugin {
    plugins {
        create("gen") {
            id = "funekit.gen"
            implementationClass = "net.seilent.funekit.logic.GenPlugin"
        }
        create("syntax") {
            id = "funekit.syntax"
            implementationClass = "net.seilent.funekit.logic.SyntaxPlugin"
        }
        create("fence") {
            id = "funekit.fence"
            implementationClass = "net.seilent.funekit.logic.FencePlugin"
        }
        create("pin") {
            id = "funekit.pin"
            implementationClass = "net.seilent.funekit.logic.PinPlugin"
        }
        create("noscrape") {
            id = "funekit.noscrape"
            implementationClass = "net.seilent.funekit.logic.NoScrapePlugin"
        }
    }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
    testImplementation("junit:junit:4.13.2")
}
