package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager.{getApplication => Application}
import com.intellij.openapi.command.CommandProcessor.{getInstance => CommandProcessor}
import com.intellij.openapi.compiler._
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiFile, PsiManager}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.processors.scalafmt.ScalaFmtPreFormatProcessor
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigManager
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ReformatOnCompileTask(project: Project) extends CompileTask {
  override def execute(context: CompileContext): Boolean = {
    val scalaSettings: ScalaCodeStyleSettings = CodeStyle.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings])
    if (scalaSettings.REFORMAT_ON_COMPILE) {
      ScalaFmtPreFormatProcessor.inFailSilentMode {
        reformatScopeFiles(context.getCompileScope, scalaSettings)
      }
    }
    true
  }

  private def reformatScopeFiles(compileScope: CompileScope, scalaSettings: ScalaCodeStyleSettings): Unit = for {
    virtualFile <- compileScope.getFiles(ScalaFileType.INSTANCE, true)
    psiFile = inReadAction(PsiManager.getInstance(project).findFile(virtualFile))
    if shouldFormatFile(psiFile, scalaSettings)
    psiFile <- psiFile.asOptionOf[ScalaFile].filterNot(_.isWorksheetFile)
  } {
    Application.invokeAndWait {
      CommandProcessor.runUndoTransparentAction {
        CodeStyleManager.getInstance(project).reformat(psiFile)
      }
    }
  }

  private def shouldFormatFile(file: PsiFile, scalaSettings: ScalaCodeStyleSettings): Boolean = {
    if (scalaSettings.USE_SCALAFMT_FORMATTER()) {
      ScalafmtDynamicConfigManager.isIncludedInProject(file)
    } else {
      true
    }
  }
}
