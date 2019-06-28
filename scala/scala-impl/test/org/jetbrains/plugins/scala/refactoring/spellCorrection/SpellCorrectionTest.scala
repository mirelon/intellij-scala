package org.jetbrains.plugins.scala.refactoring.spellCorrection

class SpellCorrectionTest extends SpellCorrectionTestBase {

  def test_correct_className(): Unit =
    doTest(s"class $NAME {}", "Testi")("Test")
}
