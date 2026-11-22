package io.github.leodias.httpie

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

class SpringRouteDeclaration : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(source: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        if (source?.language != HttpieLanguage) return null
        val project = source.project
        val doc = editor.document
        val row = doc.getLineNumber(offset)
        val start = doc.getLineStartOffset(row)
        val line = doc.charsSequence.subSequence(start, doc.getLineEndOffset(row))
        val match = requestLine.matchEntire(line) ?: return null
        val path = match.groups[2] ?: return null
        if (offset - start !in path.range || RequestEnvironment.variable.findAll(line).any { offset - start in it.range }) return null
        if (RequestLocations.at(doc.text, row)?.line != row || DumbService.isDumb(project)) return null
        val verb = match.groupValues[1]
        val segments = segments(path.value)
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        val best = linkedSetOf<PsiElement>()
        var bestScore = -1
        for (type in controllerTypes) {
            val annotation = facade.findClass(type, GlobalSearchScope.allScope(project)) ?: continue
            for (controller in AnnotatedElementsSearch.searchPsiClasses(annotation, scope).findAll()) {
                val base = controller.getAnnotation(mappingPackage + "RequestMapping")
                if (base != null && !acceptsVerb(base, verb)) continue
                val prefixes = base?.let(::paths) ?: listOf("")
                for (method in controller.methods) {
                    val mapping = method.annotations.firstOrNull { it.qualifiedName in mappings } ?: continue
                    if (!acceptsVerb(mapping, verb)) continue
                    for (suffix in paths(mapping)) for (prefix in prefixes) {
                        val pattern = segments(prefix) + segments(suffix)
                        if (pattern.size != segments.size) continue
                        if (pattern.indices.any { pattern[it] != segments[it] && !parameter.matches(pattern[it]) }) continue
                        val score = pattern.count { !parameter.matches(it) }
                        if (score > bestScore) { best.clear(); bestScore = score }
                        if (score == bestScore) best += method
                    }
                }
            }
        }
        return best.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    private fun acceptsVerb(annotation: PsiAnnotation, verb: String): Boolean {
        val mapped = mappings[annotation.qualifiedName]
        if (mapped != null) return mapped == verb
        val methods = annotation.findDeclaredAttributeValue("method") ?: return true
        val values = entries(methods)
        return values.isEmpty() || values.any { (it as? PsiReferenceExpression)?.referenceName == verb }
    }

    private fun paths(annotation: PsiAnnotation): List<String> {
        val value = annotation.findDeclaredAttributeValue("path") ?: annotation.findDeclaredAttributeValue("value")
            ?: return listOf("")
        val helper = JavaPsiFacade.getInstance(annotation.project).constantEvaluationHelper
        return entries(value).mapNotNull { helper.computeConstantExpression(it) as? String }
    }

    private fun entries(value: PsiAnnotationMemberValue) =
        (value as? PsiArrayInitializerMemberValue)?.initializers?.toList() ?: listOf(value)

    private fun segments(path: String) = path.trim('/').split('/').filter { it.isNotEmpty() }

    companion object {
        private const val mappingPackage = "org.springframework.web.bind.annotation."
        private val controllerTypes = listOf(mappingPackage + "RestController", "org.springframework.stereotype.Controller")
        private val mappings = listOf("Get", "Post", "Put", "Patch", "Delete").associate {
            mappingPackage + it + "Mapping" to it.uppercase()
        } + (mappingPackage + "RequestMapping" to null)
        private val parameter = Regex("""\{[\w-]+}""")
        private val requestLine = Regex("""\s*(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s+(?:https?://[^\s/?#]+|:\d+|\{\{\s*\w+\s*}})?(/[^\s?#]*)(?:[?#]\S*)?\s*""")
    }
}
