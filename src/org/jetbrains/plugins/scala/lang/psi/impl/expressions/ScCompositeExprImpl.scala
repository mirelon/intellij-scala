package org.jetbrains.plugins.scala.lang.psi.impl.expressions
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._

case class ScCompositeExprImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
    override def toString: String = "Composite expression"
}



