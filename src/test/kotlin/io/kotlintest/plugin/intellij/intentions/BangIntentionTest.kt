package io.kotlintest.plugin.intellij.intentions

import com.intellij.openapi.command.CommandProcessor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import io.kotlintest.matchers.collections.shouldBeEmpty
import io.kotlintest.shouldBe
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import java.nio.file.Paths

class BangIntentionTest : LightCodeInsightFixtureTestCase() {

  override fun getTestDataPath(): String {
    val path = Paths.get("./src/test/resources/").toAbsolutePath()
    return path.toString()
  }

  fun testIntention() {

    myFixture.configureByFile("/behaviorspec.kt")
    editor.moveCaret(366)

    val intention = myFixture.findSingleIntention("Add/Remove bang to test name")
    intention.familyName shouldBe "Add/Remove bang to test name"
    CommandProcessor.getInstance().runUndoTransparentAction {
      runWriteAction {
        intention.invoke(project, editor, file)
      }
    }
    file.findElementAt(366)?.text shouldBe "!another test"

    CommandProcessor.getInstance().runUndoTransparentAction {
      runWriteAction {
        intention.invoke(project, editor, file)
      }
    }
    file.findElementAt(366)?.text shouldBe "another test"
  }

  fun testIntentionShouldOnlyBeAvailableOnStrings() {

    myFixture.configureByFile("/behaviorspec.kt")
    editor.moveCaret(418)
    myFixture.filterAvailableIntentions("Add/Remove bang to test name").shouldBeEmpty()
  }
}