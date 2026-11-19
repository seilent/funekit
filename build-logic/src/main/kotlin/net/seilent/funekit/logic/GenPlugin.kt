package net.seilent.funekit.logic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

private fun <T> gen(what: () -> T): T = try {
    what()
} catch (e: GenException) {
    throw GradleException(e.message ?: "gen failed")
}

@CacheableTask
abstract class GenTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val root: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fit: RegularFileProperty

    @get:OutputDirectory
    abstract val out: DirectoryProperty

    @TaskAction
    fun run() {
        val dir = root.get().asFile
        val f = fit.get().asFile
        val files = gen {
            mapOf(
                "Tok.kt" to Gen.tokKt(dir),
                "TokBoard.kt" to Gen.tokBoardKt(dir, f),
                "TokFit.kt" to Gen.tokFitKt(dir, f),
                "Glyphs.kt" to Gen.glyphsKt(dir),
                "Moves.kt" to Moves.movesKt(dir),
            )
        }
        val o = File(out.get().asFile, Gen.pkg.replace('.', '/'))
        out.get().asFile.deleteRecursively()
        o.mkdirs()
        for ((name, text) in files) File(o, name).writeText(text)
    }
}

@CacheableTask
abstract class CheckSourceTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val root: DirectoryProperty

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun run() {
        gen { Gen.checkSource(root.get().asFile, name) }
        stamp.get().asFile.writeText(Gen.setHash(root.get().asFile) + "\n")
    }
}

@CacheableTask
abstract class CheckTokTokTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val root: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generated: DirectoryProperty

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun run() {
        val tok = File(generated.get().asFile, Gen.pkg.replace('.', '/') + "/Tok.kt")
        if (!tok.isFile) throw GradleException("checkTokTok: ${tok.name} was not generated")
        gen { Gen.checkTok(root.get().asFile, tok.readText()) }
        stamp.get().asFile.writeText("ok\n")
    }
}

@CacheableTask
abstract class CheckTokFitTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val root: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fit: RegularFileProperty

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun run() {
        gen { Gen.checkFit(root.get().asFile, fit.get().asFile) }
        stamp.get().asFile.writeText("ok\n")
    }
}

interface SyntaxParams : WorkParameters {
    val root: DirectoryProperty
    val files: ConfigurableFileCollection
    val literals: RegularFileProperty
    val fallbacks: RegularFileProperty
}

abstract class SyntaxWork : WorkAction<SyntaxParams> {
    override fun execute() {
        val root = parameters.root.get().asFile
        val found = gen {
            val literals = Literals.read(parameters.literals.get().asFile, parameters.fallbacks.get().asFile)
            Syntax.scanFiles(root, parameters.files.files.toList(), literals)
        }
        if (found.isNotEmpty()) throw GradleException("checkSyntax:\n" + found.joinToString("\n"))
    }
}

@CacheableTask
abstract class CheckSyntaxTask : DefaultTask() {
    @get:Inject
    abstract val workers: WorkerExecutor

    @get:Input
    abstract val required: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val literals: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fallbacks: RegularFileProperty

    @get:Classpath
    abstract val psi: ConfigurableFileCollection

    @get:Internal
    abstract val root: DirectoryProperty

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun run() {
        val repo = root.get().asFile
        val missing = required.get().filter { !File(repo, it).exists() }
        if (missing.isNotEmpty()) throw GradleException("checkSyntax: required scan roots are missing: $missing")
        val queue = workers.classLoaderIsolation { classpath.from(psi) }
        queue.submit(SyntaxWork::class.java) {
            root.set(this@CheckSyntaxTask.root)
            files.from(sources.asFileTree.matching { include("**/*.kt") })
            literals.set(this@CheckSyntaxTask.literals)
            fallbacks.set(this@CheckSyntaxTask.fallbacks)
        }
        queue.await()
        stamp.get().asFile.writeText("ok\n")
    }
}

@CacheableTask
abstract class CheckKitImportsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fixtures: ConfigurableFileCollection

    @get:Input
    abstract val allowed: ListProperty<String>

    @get:Input
    abstract val denied: ListProperty<String>

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun run() {
        val files = sources.asFileTree.matching { include("**/*.kt") }.files.toList()
        val fixed = fixtures.asFileTree.matching { include("**/*.kt") }.files.toList()
        val hits = Fence.scan(files, allowed.get(), denied.get()) + Fence.scan(fixed, allowed.get() + Fence.fixtureAllowed, denied.get())
        if (hits.isNotEmpty()) throw GradleException("checkKitImports:\n" + hits.joinToString("\n"))
        stamp.get().asFile.writeText("ok\n")
    }
}

@CacheableTask
abstract class CheckKitDepsTask : DefaultTask() {
    @get:Input
    abstract val owner: Property<String>

    @get:Input
    abstract val projects: MapProperty<String, Set<String>>

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun run() {
        val bad = projects.get().filterValues { ":fake" in it }.keys.sorted()
        if (bad.isNotEmpty()) throw GradleException(bad.joinToString("\n") { "checkKitDeps: ${owner.get()} $it has project :fake" })
        stamp.get().asFile.writeText("ok\n")
    }
}

@DisableCachingByDefault(because = "it hashes the kit tree on every run")
abstract class CheckKitPinTask : DefaultTask() {
    @get:Inject
    abstract val exec: ExecOperations

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val tree: ConfigurableFileCollection

    @get:Internal
    abstract val kit: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pin: RegularFileProperty

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun run() {
        val dir = kit.get().asFile
        val script = File(dir, "tools/kitset.sh")
        if (!script.isFile) throw GradleException("checkKitPin: ${dir.name}/tools/kitset.sh is missing, run git submodule update --init")
        val want = gen { KitPin.read(pin.get().asFile) }
        val out = ByteArrayOutputStream()
        exec.exec {
            commandLine("bash", script.path, "--set", dir.path)
            standardOutput = out
        }
        val got = out.toString().trim()
        if (got != want.set) throw GradleException("checkKitPin: ${dir.name}/ hashes to $got, ${pin.get().asFile.name} pins ${want.set} (commit ${want.commit})")
        stamp.get().asFile.writeText("$got\n")
    }
}

abstract class FunekitSyntax {
    abstract val roots: ListProperty<String>
    abstract val optional: ListProperty<String>
    abstract val literals: RegularFileProperty
    abstract val fallbacks: RegularFileProperty
    abstract val repo: DirectoryProperty
}

abstract class FunekitPin {
    abstract val kit: DirectoryProperty
    abstract val pin: RegularFileProperty
}

class GenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val root = project.layout.projectDirectory.dir("funeos")
        val fit = project.layout.projectDirectory.file("fit.txt")
        val checks = project.layout.buildDirectory.dir("checks")
        val gen = project.tasks.register("gen", GenTask::class.java) {
            group = "funekit"
            this.root.set(root)
            this.fit.set(fit)
            out.set(project.layout.buildDirectory.dir("generated/funeos/kotlin"))
        }
        val checkTokPin = project.tasks.register("checkTokPin", CheckSourceTask::class.java) {
            group = "verification"
            this.root.set(root)
            stamp.set(checks.map { it.file("checkTokPin") })
        }
        val checkTokTok = project.tasks.register("checkTokTok", CheckTokTokTask::class.java) {
            group = "verification"
            this.root.set(root)
            generated.set(gen.flatMap { it.out })
            stamp.set(checks.map { it.file("checkTokTok") })
        }
        val checkTokFit = project.tasks.register("checkTokFit", CheckTokFitTask::class.java) {
            group = "verification"
            this.root.set(root)
            this.fit.set(fit)
            stamp.set(checks.map { it.file("checkTokFit") })
        }
        project.tasks.matching { it.name == "check" }.configureEach {
            dependsOn(checkTokPin, checkTokTok, checkTokFit)
        }
    }
}

class SyntaxPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext = project.extensions.create("funekitSyntax", FunekitSyntax::class.java)
        ext.optional.convention(emptyList())
        val psi = project.configurations.create("funePsi") {
            isCanBeConsumed = false
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.LIBRARY))
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements::class.java, LibraryElements.JAR))
                attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling::class.java, Bundling.EXTERNAL))
                attribute(
                    TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                    project.objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM),
                )
            }
        }
        val checkSyntax = project.tasks.register("checkSyntax", CheckSyntaxTask::class.java) {
            group = "verification"
            required.set(ext.roots)
            sources.from(ext.repo.zip(ext.roots.zip(ext.optional) { a, b -> a + b }) { r, paths -> paths.map { r.file(it).asFile } })
            literals.set(ext.literals)
            fallbacks.set(ext.fallbacks)
            this.psi.from(psi)
            this.root.set(ext.repo)
            stamp.set(project.layout.buildDirectory.file("checks/checkSyntax"))
        }
        project.tasks.matching { it.name == "check" }.configureEach { dependsOn(checkSyntax) }
    }
}

class PinPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext = project.extensions.create("funekitPin", FunekitPin::class.java)
        ext.kit.convention(project.layout.projectDirectory.dir("kit"))
        ext.pin.convention(project.layout.projectDirectory.file("kit.pin"))
        val checkKitPin = project.tasks.register("checkKitPin", CheckKitPinTask::class.java) {
            group = "verification"
            kit.set(ext.kit)
            tree.from(ext.kit.map { d -> d.asFileTree.matching { exclude(KitPin.excludes) } })
            pin.set(ext.pin)
            stamp.set(project.layout.buildDirectory.file("checks/checkKitPin"))
        }
        project.allprojects {
            tasks.matching { it.name == "check" || it.name.startsWith("compile") }.configureEach { dependsOn(checkKitPin) }
        }
    }
}

class FencePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val checkKitImports = project.tasks.register("checkKitImports", CheckKitImportsTask::class.java) {
            group = "verification"
            sources.from(project.fileTree("src") { exclude("test*/**", "androidTest*/**") })
            if (project.path == ":api") fixtures.from(project.layout.projectDirectory.dir("src/testFixtures"))
            allowed.set(Fence.allowed)
            denied.set(Fence.deniedIn(project.path))
            stamp.set(project.layout.buildDirectory.file("checks/checkKitImports"))
        }
        val path = project.path
        val runtimes = project.provider {
            listOf("debugRuntimeClasspath", "releaseRuntimeClasspath").filter { project.configurations.findByName(it) != null }.ifEmpty { listOf("runtimeClasspath") }
        }
        val checkKitDeps = project.tasks.register("checkKitDeps", CheckKitDepsTask::class.java) {
            group = "verification"
            owner.set(path)
            projects.set(runtimes.map { names ->
                names.associateWith { c ->
                    project.configurations.getByName(c).incoming.resolutionResult.allComponents
                        .mapNotNull { (it.id as? ProjectComponentIdentifier)?.projectPath }.filter { it != path }.toSet()
                }
            })
            stamp.set(project.layout.buildDirectory.file("checks/checkKitDeps"))
        }
        project.tasks.matching { it.name == "check" }.configureEach { dependsOn(checkKitImports, checkKitDeps) }
    }
}
