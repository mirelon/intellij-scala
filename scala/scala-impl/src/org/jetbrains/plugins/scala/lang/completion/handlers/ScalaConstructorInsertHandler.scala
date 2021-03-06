package org.jetbrains.plugins.scala.lang.completion.handlers

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.{PsiClass, PsiDocumentManager}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createReferenceFromText
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaConstructorInsertHandler extends InsertHandler[LookupElement] {

  def handleInsert(context: InsertionContext, element: LookupElement) {
    val editor = context.getEditor
    val document = editor.getDocument
    if (context.getCompletionChar == '(') {
      context.setAddCompletionChar(false)
    } else if (context.getCompletionChar == '[') {
      context.setAddCompletionChar(false)
    }
    val startOffset = context.getStartOffset
    val lookupStringLength = element.getLookupString.length
    var endOffset = startOffset + lookupStringLength

    element match {
      case ScalaLookupItem(item, namedElement) =>
        namedElement match {
          case _: ScObject =>
            if (context.getCompletionChar != '.') {
              document.insertString(endOffset, ".")
              endOffset += 1
              editor.getCaretModel.moveToOffset(endOffset)
              ScalaLookupItem.scheduleAutoPopup(context)
            }
          case clazz: PsiClass =>
            val isRenamed = item.isRenamed.isDefined
            var hasNonEmptyParams = false
            clazz match {
              case c: ScClass =>
                c.constructor match {
                  case Some(constr) if constr.parameters.nonEmpty => hasNonEmptyParams = true
                  case _ =>
                }
                c.secondaryConstructors.foreach(fun => if (fun.parameters.nonEmpty) hasNonEmptyParams = true)
              case _ =>
                clazz.getConstructors.foreach(meth => if (meth.getParameterList.getParametersCount > 0) hasNonEmptyParams = true)
            }
            if (context.getCompletionChar == '(') hasNonEmptyParams = true
            if (item.typeParametersProblem) {
              document.insertString(endOffset, "[]")
              endOffset += 2
              editor.getCaretModel.moveToOffset(endOffset - 1)
            } else if (item.typeParameters.nonEmpty) {
              val str = item.typeParameters.map(_.canonicalText).mkString("[", ", ", "]")
              document.insertString(endOffset, str)
              endOffset += str.length()
              editor.getCaretModel.moveToOffset(endOffset)
            }
            if (hasNonEmptyParams) {
              document.insertString(endOffset, "()")
              endOffset += 2
              if (!item.typeParametersProblem)
                editor.getCaretModel.moveToOffset(endOffset - 1)
            }

            if (clazz.isInterface || clazz.isInstanceOf[ScTrait] ||
              clazz.hasModifierPropertyScala("abstract")) {
              document.insertString(endOffset, " {}")
              endOffset += 3
              if (!item.typeParametersProblem)
                editor.getCaretModel.moveToOffset(endOffset - 1)
            }
            PsiDocumentManager.getInstance(context.getProject).commitDocument(document)
            val file = context.getFile
            val element = file.findElementAt(endOffset - 1)
            element.parentOfType(classOf[ScNewTemplateDefinition]).foreach { newT =>
              val maybeRef = newT.extendsBlock.templateParents.toSeq.flatMap(_.typeElements) match {
                case Seq(ScSimpleTypeElement.unwrapped(reference)) => Some(reference)
                case _ => None
              }

              maybeRef match {
                case Some(value) if !isRenamed =>
                  val referenceElement = if (item.prefixCompletion) {
                    val newRefText = clazz.qualifiedName.split('.').takeRight(2).mkString(".")
                    val newRef = createReferenceFromText(newRefText)(clazz.getManager)
                    value.replace(newRef).asInstanceOf[ScStableCodeReference]
                  } else value

                  referenceElement.bindToElement(clazz)
                case _ =>
              }

              ScalaPsiUtil.adjustTypes(newT)
            }

            if ((clazz.isInterface || clazz.isInstanceOf[ScTrait] ||
              clazz.hasModifierPropertyScala("abstract")) && !item.typeParametersProblem) {
              context.setLaterRunnable(new Runnable {
                def run() {
                  val file = context.getFile
                  val element = file.findElementAt(editor.getCaretModel.getOffset)
                  if (element == null) return

                  element.getParent match {
                    case (_: ScTemplateBody) childOf ((_: ScExtendsBlock) childOf (newTemplateDef: ScNewTemplateDefinition)) =>
                      val members = ScalaOIUtil.getMembersToImplement(newTemplateDef)

                      ScalaApplicationSettings.getInstance().SPECIFY_RETURN_TYPE_EXPLICITLY =
                        ScalaApplicationSettings.ReturnTypeLevel.BY_CODE_STYLE

                      ScalaOIUtil.runAction(
                        members.toSeq,
                        isImplement = true,
                        newTemplateDef
                      )(
                        newTemplateDef.getProject,
                        editor
                      )
                    case _ =>
                  }
                }
              })
            }
        }
      case _ =>
    }
  }
}
