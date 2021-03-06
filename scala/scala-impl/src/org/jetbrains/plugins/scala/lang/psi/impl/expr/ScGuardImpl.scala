package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
  * @author Alexander Podkhalyuzin
  */

class ScGuardImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScGuard with ScEnumeratorImpl {

  override def toString: String = "Guard"

  def expr: Option[ScExpression] = findChild(classOf[ScExpression])

  override def enumeratorToken: Option[PsiElement] =
    Option(findFirstChildByType(ScalaTokenTypes.kIF))
}