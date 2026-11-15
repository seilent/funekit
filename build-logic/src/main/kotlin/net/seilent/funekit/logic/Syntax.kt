package net.seilent.funekit.logic

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.cli.create
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgument
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger

class Finding(val file: String, val line: Int, val what: String) {
    override fun toString() = "$file:$line: $what"
}

class Allowance(val path: String, val site: String, val text: String, val reason: String)

class Fallback(val id: String, val signed: String, val what: String)

object Fallbacks {
    private val date = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")

    fun read(f: File): List<Fallback> {
        if (!f.isFile) return emptyList()
        val seen = HashSet<String>()
        return f.readLines().mapIndexedNotNull { i, line ->
            if (line.isBlank()) return@mapIndexedNotNull null
            val p = line.trim().split(Regex("\\s+"), limit = 3)
            if (p.size != 3) throw GenException("fallbacks: ${f.name}:${i + 1} needs id, signed and what")
            if (!date.matches(p[1]) && p[1] != "pending") throw GenException("fallbacks: ${f.name}:${i + 1} signed is a date or pending")
            if (!seen.add(p[0])) throw GenException("fallbacks: ${f.name}:${i + 1} repeats ${p[0]}")
            Fallback(p[0], p[1], p[2])
        }
    }
}

class Literals(val entries: List<Allowance>) {
    fun allowed(path: String): Set<Pair<String, String>> = entries.filter { it.path == path }.map { it.site to it.text }.toSet()

    companion object {
        private val cite = Regex("^[A-Za-z0-9_][A-Za-z0-9_./-]*:[0-9]+(-[0-9]+)?$")

        fun reasonError(reason: String, ids: Set<String>): String? {
            val w = reason.trim().split(Regex("\\s+"))
            return when (w[0]) {
                "fos" -> if (w.size < 2 || !cite.matches(w[1])) "fos needs <path>:<line>" else null
                "platform" -> if (w.size < 2) "platform needs an api" else null
                "fx" -> null
                "FB" -> when {
                    w.size < 2 -> "FB needs an id"
                    w[1] !in ids -> "FB ${w[1]} is not in fallbacks.txt"
                    else -> null
                }
                else -> "the reason starts with fos, platform, fx or FB"
            }
        }

        fun read(f: File, fallbacks: File = File(f.parentFile, "fallbacks.txt")): Literals {
            val ids = Fallbacks.read(fallbacks).map { it.id }.toSet()
            return Literals(f.readLines().mapIndexedNotNull { i, line ->
                if (line.isBlank()) return@mapIndexedNotNull null
                val p = line.trim().split(Regex("\\s+"), limit = 4)
                if (p.size != 4) throw GenException("literals: ${f.name}:${i + 1} needs file, site, text and reason")
                reasonError(p[3], ids)?.let { throw GenException("literals: ${f.name}:${i + 1} $it") }
                Allowance(p[0], p[1], p[2], p[3])
            })
        }
    }
}

@OptIn(K1Deprecation::class)
private fun psiFactory(disposable: Disposable): KtPsiFactory {
    val config = CompilerConfiguration.create()
    config.put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
    return KtPsiFactory(KotlinCoreEnvironment.createForProduction(disposable, config, EnvironmentConfigFiles.JVM_CONFIG_FILES).project)
}

class Syntax : AutoCloseable {
    private val disposable = Disposer.newDisposable()
    private val factory = psiFactory(disposable)

    override fun close() = Disposer.dispose(disposable)

    fun scan(
        path: String,
        text: String,
        allowed: Set<Pair<String, String>>,
        used: MutableSet<Pair<String, String>> = HashSet(),
    ): List<Finding> {
        val file = factory.createFile(File(path).name, text)
        val out = ArrayList<Finding>()
        fun add(e: PsiElement, what: String) {
            out += Finding(path, text.substring(0, e.textRange.startOffset).count { it == '\n' } + 1, what)
        }
        fun allow(e: PsiElement, site: String, value: String, what: String) {
            if ((site to value) in allowed) used += site to value else add(e, what)
        }
        file.accept(object : KtTreeVisitorVoid() {
            override fun visitImportDirective(d: KtImportDirective) {
                val fq = d.importedFqName?.asString() ?: ""
                val segs = fq.split('.')
                if (fq.startsWith("androidx.compose.material")) add(d, "Material import $fq")
                val named = segs.indices.any { i -> segs[i] == "Color" && i + 1 < segs.size && segs[i + 1].first().isUpperCase() }
                if (segs.indices.any { i -> segs[i] == "Spring" && (i + 1 == segs.size && d.isAllUnder || i + 1 < segs.size && segs[i + 1].first().isUpperCase()) })
                    add(d, "Spring import $fq")
                val star = d.isAllUnder && segs.last() == "Color"
                val alias = d.aliasName != null && segs.last() == "Color"
                if (named || star || alias) allow(d, "import", fq, "Color import $fq")
                super.visitImportDirective(d)
            }

            override fun visitConstantExpression(e: KtConstantExpression) {
                val t = e.node.elementType
                if ((t == KtNodeTypes.INTEGER_CONSTANT || t == KtNodeTypes.FLOAT_CONSTANT) && !unit(e.text)) {
                    val prop = PsiTreeUtil.getParentOfType(e, KtProperty::class.java)
                    val site = if (prop != null && prop.hasModifier(KtTokens.CONST_KEYWORD)) prop.name ?: "" else ""
                    allow(e, site, e.text, "numeric literal ${e.text}")
                }
                super.visitConstantExpression(e)
            }

            override fun visitQualifiedExpression(e: KtQualifiedExpression) {
                if (!header(e)) qualified(e)
                super.visitQualifiedExpression(e)
            }

            private fun qualified(e: KtQualifiedExpression) {
                when (val sel = e.selectorExpression) {
                    is KtNameReferenceExpression -> {
                        val n = sel.getReferencedName()
                        if (n == "dp" || n == "sp") add(e, ".$n")
                        if (colorReceiver(e.receiverExpression) && n.first().isUpperCase()) add(e, "Color.$n")
                        if (last(e.receiverExpression) == "Spring" && n.first().isUpperCase()) add(e, "Spring.$n")
                        if (last(e.receiverExpression) == "TextUnitType" && (n == "Sp" || n == "Em"))
                            allow(e, function(e), "TextUnitType.$n", "TextUnitType.$n")
                    }
                    is KtCallExpression -> {
                        val n = (sel.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
                        if (n != null && n in colorCalls && colorReceiver(e.receiverExpression)) add(e, "Color.$n(")
                    }
                    else -> {}
                }
            }

            override fun visitSimpleNameExpression(e: KtSimpleNameExpression) {
                val n = e.getReferencedName()
                if ((n in banned || easing.matches(n)) && !header(e)) add(e, n)
                super.visitSimpleNameExpression(e)
            }

            override fun visitCallExpression(e: KtCallExpression) {
                val n = (e.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
                if (n != null) call(e, n)
                super.visitCallExpression(e)
            }

            private fun listedColor(all: List<PsiElement>): Boolean {
                val r = all.singleOrNull() as? KtNameReferenceExpression ?: return false
                val p = PsiTreeUtil.findChildrenOfType(file, KtProperty::class.java)
                    .firstOrNull { it.name == r.getReferencedName() && it.hasModifier(KtTokens.CONST_KEYWORD) } ?: return false
                val v = p.initializer?.text ?: return false
                val named = PsiTreeUtil.findChildrenOfType(file, KtNamedDeclaration::class.java).count { it.name == r.getReferencedName() }
                return named == 1 && argb.matches(v) && (r.getReferencedName() to v) in allowed
            }

            private fun call(e: KtCallExpression, n: String) {
                val args = e.valueArguments
                val all = args.mapNotNull { it.getArgumentExpression() } + e.lambdaArguments
                when {
                    n == "Color" -> if (!mentionsTok(all, colorTok) && !listedColor(all)) add(e, "Color( without Tok")
                    n == "indication" -> add(e, "indication(")
                    n == "Dp" -> allow(e, function(e), "Dp(", "Dp(")
                    n == "tween" -> if (!mentionsTok(all) || arg(args, listOf("easing"), 2) == null)
                        add(e, "tween( without an easing and a spec from Tok")
                    n == "spring" -> if (!mentionsTok(all) || arg(args, listOf("dampingRatio"), 0) == null || arg(args, listOf("stiffness"), 1) == null)
                        add(e, "spring( without dampingRatio, stiffness and a spec from Tok")
                    n == "keyframes" -> if (!mentionsTok(all)) add(e, "keyframes( without a spec from Tok")
                    n in lazy -> if (arg(args, listOf("userScrollEnabled"), -1)?.getArgumentExpression()?.text != "false")
                        add(e, "$n without userScrollEnabled = false")
                    else -> {
                        val spec = when {
                            asState.matches(n) -> arg(args, listOf("animationSpec"), 1)
                            n in children -> arg(args, listOf("transitionSpec", "animationSpec"), -1)
                            n == "animateDecay" && received(e) -> arg(args, listOf("animationSpec"), 1)
                            n in specIndex -> arg(args, listOf("animationSpec"), specIndex.getValue(n))
                            else -> return
                        }
                        if (spec == null || !mentionsTok(listOfNotNull(spec.getArgumentExpression())))
                            add(e, "$n( without a spec from Tok")
                    }
                }
            }
        })
        return out
    }

    companion object {
        val banned = setOf(
            "animateItem", "animateScrollToItem", "clickable", "combinedClickable", "cameraDistance", "rotationX",
            "rotationY", "AnimatedVisibility", "AnimatedContent", "Crossfade", "animateContentSize", "Dialog", "Popup",
            "verticalScroll", "horizontalScroll", "scrollable", "draggable", "anchoredDraggable", "HorizontalPager",
            "VerticalPager", "selectable", "toggleable", "triStateToggleable", "FastOutSlowInEasing",
            "LinearOutSlowInEasing", "FastOutLinearInEasing", "LinearEasing", "toColorInt", "ColorUtils",
        )
        private val argb = Regex("^0x[0-9A-Fa-f]{8}L$")
        private val easing = Regex("^Ease([A-Z][A-Za-z]*)?$")
        private val lazy = setOf(
            "LazyColumn", "LazyRow", "LazyVerticalGrid", "LazyHorizontalGrid", "LazyVerticalStaggeredGrid",
            "LazyHorizontalStaggeredGrid",
        )
        private val asState = Regex("^animate[A-Za-z]*AsState$")
        private val children = setOf(
            "animateFloat", "animateValue", "animateInt", "animateDp", "animateColor", "animateOffset", "animateSize",
            "animateRect", "animateIntOffset", "animateIntSize",
        )
        private val specIndex = mapOf(
            "animateTo" to 1, "animate" to 3, "animateDecay" to 2, "animateScrollTo" to 1, "animateScrollBy" to 1,
            "animateScrollToPage" to 2,
        )
        private val colorCalls = setOf("hsv", "hsl", "parseColor", "valueOf", "argb", "rgb")
        private val tok = setOf("Tok", "TokBoard", "TokFit")
        private val colorTok = setOf("Tok", "TokBoard")

        private fun header(e: PsiElement) = PsiTreeUtil.getParentOfType(e, KtImportDirective::class.java) != null ||
            PsiTreeUtil.getParentOfType(e, KtPackageDirective::class.java) != null

        private fun last(r: KtExpression) = r.text.filterNot { it.isWhitespace() || it == '?' }.substringAfterLast('.')

        private fun function(e: PsiElement) = PsiTreeUtil.getParentOfType(e, KtNamedFunction::class.java)?.name ?: ""

        private fun received(e: KtCallExpression) = (e.parent as? KtQualifiedExpression)?.selectorExpression == e

        private fun colorReceiver(r: KtExpression): Boolean {
            val segs = r.text.filterNot { it.isWhitespace() || it == '?' }.split('.')
            return segs.last() == "Color" || (segs.size >= 2 && segs[segs.size - 2] == "Color" && segs.last() == "Companion")
        }

        private fun arg(args: List<KtValueArgument>, names: List<String>, index: Int): KtValueArgument? =
            args.firstOrNull { it.getArgumentName()?.asName?.asString() in names }
                ?: if (index >= 0) args.filter { it.getArgumentName() == null && it !is KtLambdaArgument }.getOrNull(index) else null

        private fun mentionsTok(parts: List<PsiElement>, names: Set<String> = tok) = parts.any { p ->
            (PsiTreeUtil.findChildrenOfType(p, KtNameReferenceExpression::class.java) + listOfNotNull(p as? KtNameReferenceExpression))
                .any { r -> r.getReferencedName() in names && (r.parent as? KtQualifiedExpression)?.receiverExpression == r }
        }

        fun unit(text: String): Boolean {
            val t = text.lowercase().replace("_", "")
            val v = when {
                t.startsWith("0x") -> BigDecimal(BigInteger(t.substring(2).trimEnd('l', 'u'), 16))
                t.startsWith("0b") -> BigDecimal(BigInteger(t.substring(2).trimEnd('l', 'u'), 2))
                else -> BigDecimal(t.trimEnd('l', 'u').removeSuffix("f"))
            }
            return v.compareTo(BigDecimal.ZERO) == 0 || v.compareTo(BigDecimal.ONE) == 0
        }

        fun scanFiles(
            root: File,
            files: List<File>,
            literals: Literals,
        ): List<Finding> {
            val known = files.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
            val stale = literals.entries.map { it.path }.filter { it !in known }.distinct()
            if (stale.isNotEmpty()) throw GenException("checkSyntax: kit/literals.txt names files outside the scan: $stale")
            return Syntax().use { s ->
                files.sortedBy { it.path }.flatMap { f ->
                    val rel = f.relativeTo(root).invariantSeparatorsPath
                    val used = HashSet<Pair<String, String>>()
                    val got = s.scan(rel, f.readText(), literals.allowed(rel), used)
                    val unused = literals.allowed(rel) - used
                    if (unused.isNotEmpty()) throw GenException("checkSyntax: kit/literals.txt entries for $rel match no site: $unused")
                    got
                }
            }
        }
    }
}
