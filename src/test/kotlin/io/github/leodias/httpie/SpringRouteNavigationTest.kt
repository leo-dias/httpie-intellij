package io.github.leodias.httpie

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SpringRouteNavigationTest : BasePlatformTestCase() {
    fun testNavigateByPathAndVerb() {
        val pkg = "org.springframework.web.bind.annotation"
        for (name in listOf("RestController", "RequestMapping", "GetMapping", "PostMapping")) {
            myFixture.addFileToProject("${pkg.replace('.', '/')}/$name.java",
                "package $pkg; public @interface $name { String[] value() default {}; }")
        }
        myFixture.addFileToProject("demo/MyController.java", """
            package demo;
            import $pkg.*;
            @RestController @RequestMapping("/")
            public class MyController {
                @GetMapping("/{key}") public void getKey() {}
                @GetMapping public void root() {}
                @GetMapping("/fixed") public void fixed() {}
                @PostMapping("/fixed") public void postFixed() {}
            }
        """.trimIndent())
        myFixture.addFileToProject("demo/Other.java", """
            package demo;
            import $pkg.*;
            @RestController @RequestMapping("/api")
            public class Other {
                @GetMapping("/{id}") public void byId() {}
                @GetMapping("/{name}") public void byName() {}
            }
        """.trimIndent())
        fun targets(request: String): Set<String> {
            myFixture.configureByText("routes.httpie", request)
            return GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)
                .filterIsInstance<PsiMethod>().map { it.name }.toSet()
        }
        assertEquals(setOf("getKey"), targets("GET :8080/<caret>my-api"))
        assertEquals(setOf("root"), targets("GET :8080<caret>/"))
        assertEquals(setOf("fixed"), targets("GET {{url}}/<caret>fixed?q=123"))
        assertEquals(setOf("postFixed"), targets("POST https://localhost/<caret>fixed"))
        assertEmpty(targets("POST :8080/<caret>my-api"))
        assertEquals(setOf("byId", "byName"), targets("GET :8080/api/<caret>abc"))
        assertEmpty(targets("GET :8080/fixed?q=<caret>abc"))
    }
}
