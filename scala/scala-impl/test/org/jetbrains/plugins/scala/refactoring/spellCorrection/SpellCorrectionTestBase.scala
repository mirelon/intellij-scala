package org.jetbrains.plugins.scala.refactoring.spellCorrection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{Lookup, LookupManager}
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.spellchecker.quickfixes.{ChangeTo, RenameTo}
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.{findCaretOffset, normalize}
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, ScalaLightPlatformCodeInsightTestCaseAdapter}
import org.jetbrains.plugins.scala.codeInspection.ScalaHighlightsTestBase.{checkOffset, highlightedRange}
import org.jetbrains.plugins.scala.codeInspection.{ForceInspectionSeverity, ScalaHighlightsTestBase, ScalaInspectionTestBase, ScalaQuickFixTestBase}

import collection.JavaConverters._

class SpellCorrectionTestBase extends ScalaLightCodeInsightFixtureTestAdapter {
  val NAME = "/*NAME*/"

  def doTest(context: String, originalWord: String, fileExt: String = "scala")(expectedWords: String*): Unit = {
    myFixture.enableInspections(classOf[SpellCheckingInspection])

    val original = context.replace(NAME, originalWord.head + CARET + originalWord.tail)
    val expected = context.replace(NAME, expectedWords.head)

    getFixture.configureByText("dummy." + fileExt, original)
    val fix = myFixture.findSingleIntention(RenameTo.FIX_NAME);
    myFixture.launchAction(fix)
    selectAndCheckLookupElements(expectedWords)
    myFixture.checkResult(expected)
  }

  private def selectAndCheckLookupElements(expectedWords: Seq[String]): Unit = {
    val elements = myFixture.getLookupElements
    val lookup = LookupManager.getInstance(getProject).getActiveLookup

    expectedWords.foreach{ expectedWord =>
      assert(elements.exists(_.getLookupString == expectedWord), s"Expected '$expectedWord' but only found: ${elements.map(_.getLookupString).mkString(", ")}")
    }

    lookup.setCurrentItem(elements.find(_.getLookupString == expectedWords.head).get)

    lookup.asInstanceOf[LookupImpl].finishLookup(Lookup.NORMAL_SELECT_CHAR)
  }
}
