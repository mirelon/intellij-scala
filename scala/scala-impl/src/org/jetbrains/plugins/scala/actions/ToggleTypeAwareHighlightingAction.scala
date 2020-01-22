package org.jetbrains.plugins.scala.actions

import com.intellij.openapi.actionSystem._
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.util.FileContentUtil
import org.jetbrains.plugins.scala.actions.ToggleTypeAwareHighlightingAction.toggleSettingAndRehighlight
import org.jetbrains.plugins.scala.extensions.{TraversableExt, invokeLater}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.JavaConverters._

/**
 * User: Alexander Podkhalyuzin
 * Date: 27.01.2010
 */

class ToggleTypeAwareHighlightingAction extends AnAction {
  def actionPerformed(e: AnActionEvent) {
    CommonDataKeys.PROJECT.getData(e.getDataContext) match {
      case project: Project =>
        toggleSettingAndRehighlight(project)
      case _ =>
    }
  }
}

object ToggleTypeAwareHighlightingAction {
  def toggleSettingAndRehighlight(project: Project): Unit = {
    val settings = ScalaProjectSettings.getInstance(project)
    settings.toggleTypeAwareHighlighting()
    invokeLater(ModalityState.NON_MODAL) {
      reparseActiveFiles(project)
    }
  }

  private def reparseActiveFiles(project: Project) {
    val openEditors = EditorFactory.getInstance().getAllEditors.toSeq.filterBy[EditorEx]
    val vFiles = openEditors.map(_.getVirtualFile).filterNot(_ == null)
    FileContentUtil.reparseFiles(project, vFiles.asJava, true)
  }
}