package io.kotlintest.plugin.intellij.styles

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

fun PsiElement.isContainedInSpec(): Boolean {
  val enclosingClass = getParentOfType<KtClassOrObject>(true) ?: return false
  return enclosingClass.isAnySpecSubclass()
}

/**
 * Returns true if this element is contained within a class that is a Spec class.
 */
fun PsiElement.isContainedInSpec(fqn: FqName): Boolean {
  val enclosingClass = getParentOfType<KtClassOrObject>(true) ?: return false
  return enclosingClass.isSpecSubclass(fqn)
}

/**
 * Returns the [KtClassOrObject] for a [LeafPsiElement] that is a [KtKeywordToken] with the
 * token value "class" or "object"
 */
fun LeafPsiElement.enclosingClassOrObjectForClassOrObjectToken(): KtClassOrObject? {
  if (elementType is KtKeywordToken && (text == "class" || text == "object")) {
    val maybeKtClassOrObject = parent
    if (maybeKtClassOrObject is KtClassOrObject) {
      return maybeKtClassOrObject
    }
  }
  return null
}

/**
 * Returns true if this [KtClassOrObject] is a subclass of any Spec.
 * This function will recursively check all superclasses.
 */
fun KtClassOrObject.isAnySpecSubclass(): Boolean {
  val superClass = getSuperClass()
      ?: return SpecStyle.specs.any { it.fqn().shortName().asString() == getSuperClassSimpleName() }
  val fqn = superClass.getKotlinFqName()
  return if (SpecStyle.specs.any { it.fqn() == fqn }) true else superClass.isAnySpecSubclass()
}

/**
 * Returns true if this [KtClassOrObject] is a subclass of a specific Spec.
 * This function will recursively check all superclasses.
 */
fun KtClassOrObject.isSpecSubclass(style: SpecStyle) = isSpecSubclass(style.fqn())
fun KtClassOrObject.isSpecSubclass(fqn: FqName): Boolean {
  val superClass = getSuperClass() ?: return getSuperClassSimpleName() == fqn.shortName().asString()
  return if (superClass.getKotlinFqName() == fqn) true else superClass.isSpecSubclass(fqn)
}

fun KtClassOrObject.getSuperClassSimpleName(): String? {
  for (entry in superTypeListEntries) {
    if (entry is KtSuperTypeCallEntry) {
      val name = entry.typeAsUserType?.referencedName
      if (name != null) return name
    }
  }
  return null
}

/**
 * Returns the superclass parent of this [KtClassOrObject].
 * If this class or object only implements interfaces then this function will
 * return null.
 */
fun KtClassOrObject.getSuperClass(): KtClassOrObject? {
  for (entry in superTypeListEntries) {
    if (entry is KtSuperTypeCallEntry) {

      val ref = entry.calleeExpression
          .constructorReferenceExpression
          ?.mainReference
          ?.resolve()

      if (ref is KtClassOrObject) return ref
      if (ref is KtPrimaryConstructor) return ref.getContainingClassOrObject()
    }
  }
  return null
}

/**
 * Returns the [FqName] if this [LeafPsiElement] has type [KtKeywordToken] with the lexeme 'class'
 * and the [KtClass] definition is a subclass of the given spec style.
 */

fun PsiElement.enclosingClass(): KtClass? = getParentOfType(true)

/**
 * Matches blocks of the form:
 *
 * functionName("some string").config(..)
 *
 * @return the string argument of the invoked function
 *
 * @param lefts one or more acceptable names for the left hand side reference
 */
fun PsiElement.extractStringArgForFunctionWithConfig(lefts: List<String>): String? =
    extractStringArgForFunctionBeforeDotExpr(lefts, listOf("config"))

/**
 * Matches blocks of the form:
 *
 * functionName("some string").<ident>
 *
 * Eg, can be used to match: should("this is a test").config { }
 *
 * @return the string argument of the invoked function
 *
 * @param lefts one or more acceptable names for the left hand side reference
 * @param rights one or more acceptable names for the right hand side reference
 */
fun PsiElement.extractStringArgForFunctionBeforeDotExpr(lefts: List<String>, rights: List<String>): String? {
  if (parent is KtLiteralStringTemplateEntry) {
    val maybeTemplateExpr = parent.parent
    if (maybeTemplateExpr is KtStringTemplateExpression) {
      val maybeValueArg = maybeTemplateExpr.parent
      if (maybeValueArg is KtValueArgument) {
        val maybeValueArgList = maybeValueArg.parent
        if (maybeValueArgList is KtValueArgumentList) {
          val maybeCallExpr = maybeValueArgList.parent
          if (maybeCallExpr is KtCallExpression) {
            val maybeDotExpr = maybeCallExpr.parent
            if (maybeDotExpr is KtDotQualifiedExpression) {
              if (maybeDotExpr.children.size == 2
                  && maybeDotExpr.children[0].isCallExprWithName(lefts)
                  && maybeDotExpr.children[1].isCallExprWithName(rights)) {
                return parent.text
              }
            }
          }
        }
      }
    }
  }
  return null
}

fun PsiElement.extractLiteralForStringExtensionFunction(funcnames: List<String>): String? {
  if (parent is KtLiteralStringTemplateEntry) {
    val maybeTemplateExpr = parent.parent
    if (maybeTemplateExpr is KtStringTemplateExpression) {
      val maybeDotExpr = maybeTemplateExpr.parent
      if (maybeDotExpr is KtDotQualifiedExpression) {
        if (maybeDotExpr.children.size == 2
            && maybeDotExpr.children[1].isCallExprWithName(funcnames)) {
          return parent.text
        }
      }
    }
  }
  return null
}

fun PsiElement.isCallExprWithName(names: List<String>): Boolean =
    this is KtCallExpression
        && children.isNotEmpty()
        && children[0] is KtReferenceExpression
        && names.contains(children[0].text)

fun PsiElement.isNameReference(names: List<String>): Boolean = this is KtNameReferenceExpression && names.contains(text)

fun PsiElement.isOperation(names: List<String>): Boolean = this is KtOperationReferenceExpression && names.contains(text)

/**
 * Matches blocks of the form:
 *
 * functionName("some string") { }
 *
 * Eg, can be used to match: given("this is a test") { }
 *
 * @return the string argument of the invoked function
 *
 * @param names one or more function names to search for
 */
fun PsiElement.matchFunction2WithStringAndLambda(names: List<String>): String? {
  return when (val p = parent) {
    is KtStringTemplateEntry -> p.extractStringForFunction2WithStringAndLambda(names)
    is KtCallExpression -> p.extractStringForFunction2WithStringAndLambda(names)
    else -> null
  }
}

fun KtStringTemplateEntry.extractStringForFunction2WithStringAndLambda(names: List<String>): String? {
  if (parent is KtStringTemplateExpression) {
    val maybeValueArg = parent.parent
    if (maybeValueArg is KtValueArgument) {
      val maybeValueArgList = maybeValueArg.parent
      if (maybeValueArgList is KtValueArgumentList) {
        val maybeCallExpr = maybeValueArgList.parent
        if (maybeCallExpr is KtCallExpression) {
          if (maybeCallExpr.children.size == 3
              && maybeCallExpr.children[0].isNameReference(names)
              && maybeCallExpr.children[2] is KtLambdaArgument) {
            return text
          }
        }
      }
    }
  }
  return null
}

fun KtCallExpression.extractStringForFunction2WithStringAndLambda(names: List<String>): String? {
  if (children.size == 3
      && children[0].isNameReference(names)
      && children[1].isSingleStringTemplateArg()
      && children[2] is KtLambdaArgument) {
    return children[1] // KtValueArgumentList
        .children[0] // KtValueArgument
        .children[0] // KtStringTemplateExpression
        .children[0] // KtStringTemplateEntry
        .text
  }
  return null
}

fun PsiElement.isSingleStringTemplateArg(): Boolean =
    this is KtValueArgumentList
        && children.size == 1
        && children[0] is KtValueArgument
        && children[0].children.size == 1
        && children[0].children[0] is KtStringTemplateExpression


/**
 * Matches blocks of the form:
 *
 * "string" infixFunctionName { }
 *
 * Eg, can be used to match: "this test" should { }
 *
 * @return the LHS operand
 *
 * @param names one or more function names to search for
 */
fun PsiElement.matchInfixFunctionWithStringAndLambaArg(names: List<String>): String? =
    when (val p = parent) {
      is KtStringTemplateEntry -> p.extractLhsForInfixFunction(names)
      is KtBinaryExpression -> p.extractLhsForInfixFunction(names)
      else -> null
    }

fun KtStringTemplateEntry.extractLhsForInfixFunction(names: List<String>): String? {
  if (parent is KtStringTemplateExpression) {
    val maybeBinaryExpr = parent.parent
    if (maybeBinaryExpr is KtBinaryExpression) {
      if (maybeBinaryExpr.children.size == 3
          && maybeBinaryExpr.children[1].isOperation(names)
          && maybeBinaryExpr.children[2] is KtLambdaExpression) {
        return text
      }
    }
  }
  return null
}

fun KtBinaryExpression.extractLhsForInfixFunction(names: List<String>): String? {
  if (children.size == 3
      && children[0] is KtStringTemplateExpression
      && children[1].isOperation(names)
      && children[2] is KtLambdaExpression) {
    val template = children[0]
    if (template.children.isNotEmpty() && template.children[0] is KtStringTemplateEntry) {
      return template.children[0].text
    }
  }
  return null
}

/**
 * Matches blocks of the form:
 *
 * "string" { }
 *
 * @return the LHS operand
 */
fun PsiElement.matchStringInvoke(): String? =
    when (val p = parent) {
      is KtStringTemplateEntry -> p.extractLhsForStringInvoke()
      is KtCallExpression -> p.extractLhsForStringInvoke()
      else -> null
    }

fun KtStringTemplateEntry.extractLhsForStringInvoke(): String? {
  if (parent is KtStringTemplateExpression) {
    val maybeCallExpr = parent.parent
    if (maybeCallExpr is KtCallExpression) {
      if (maybeCallExpr.children.size == 2
          && maybeCallExpr.children[1] is KtLambdaArgument) {
        return text
      }
    }
  }
  return null
}

fun KtCallExpression.extractLhsForStringInvoke(): String? {
  if (children.size == 2
      && children[0] is KtStringTemplateExpression
      && children[1] is KtLambdaArgument) {
    return children[0] // KtStringTemplateExpression
        .children[0] // KtStringTemplateEntry
        .text
  }
  return null
}

fun buildSuggestedName(specName: String?, testName: String?): String? {
  return when {
    specName == null || specName.isBlank() -> null
    testName == null || testName.isBlank() -> specName.split('.').last()
    else -> {
      val simpleName = specName.split('.').last()
      "$simpleName: $testName"
    }
  }
}