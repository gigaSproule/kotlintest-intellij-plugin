package io.kotlintest.plugin.intellij.styles

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.name.FqName

object BehaviorSpecStyle : SpecStyle {

  override fun generateTest(specName: String, name: String): String {
    return "Given(\"$name\") { }"
  }

  override fun fqn() = FqName("io.kotlintest.specs.BehaviorSpec")

  override fun specStyleName(): String = "BehaviorSpec"

  // todo this could be optimized to not check for the other parts of the tree until the name is needed
  override fun isTestElement(element: PsiElement): Boolean = testPath(element) != null

  private val givens = listOf("given", "Given", "`given`", "`Given`")
  private val whens = listOf("when", "When", "`when`", "`When")
  private val thens = listOf("then", "Then", "`then`", "`Then`")

  private fun PsiElement.locateParentTestName(names: List<String>): String? {
    val param = this.matchFunction2WithStringAndLambda(names)
    return if (param == null && parent == null) null else param ?: parent.locateParentTestName(names)
  }

  private fun PsiElement.tryThenWithConfig(): String? {
    val then = extractStringArgForFunctionBeforeDotExpr(thens, listOf("config"))
    return if (then == null) null else {
      val `when` = locateParentTestName(whens)
      val given = locateParentTestName(givens)
      "Given: $given When: $`when` Then: $then"
    }
  }

  private fun PsiElement.tryThen(): String? {
    val then = matchFunction2WithStringAndLambda(thens)
    return if (then == null) null else {
      val `when` = locateParentTestName(whens)
      val given = locateParentTestName(givens)
      "Given: $given When: $`when` Then: $then"
    }
  }

  private fun PsiElement.tryWhen(): String? {
    val `when` = matchFunction2WithStringAndLambda(whens)
    return if (`when` == null) null else {
      val given = locateParentTestName(givens)
      "Given: $given When: $`when`"
    }
  }

  private fun PsiElement.tryGiven(): String? {
    val given = matchFunction2WithStringAndLambda(givens)
    return if (given == null) null else "Given: $given"
  }

  override fun testPath(element: PsiElement): String? {
    if (!element.isContainedInSpec())
      return null
    return element.tryThen() ?: element.tryThenWithConfig() ?: element.tryWhen() ?: element.tryGiven()
  }
}