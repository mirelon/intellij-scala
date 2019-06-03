package org.jetbrains.plugins.scala.lang.psi.controlFlow

import org.jetbrains.plugins.scala.dfa.DfConcreteLambdaRef
import org.jetbrains.plugins.scala.lang.psi.controlFlow.cfg.JumpingInstruction

class ControlFlowGraph private (val instructions: Array[cfg.Instruction], val lambdas: Seq[DfConcreteLambdaRef]) {
  assert(instructions.length > 0)

  def instructionCount: Int = instructions.length
  def instructionAt(index: Int): cfg.Instruction = instructions(index)

  def entryInstruction: cfg.Instruction = instructions.head

  def asmText(lineNumbers: Boolean = true, labels: Boolean = true, indentation: Boolean = true): String = {
    if (instructions.isEmpty) {
      return "<empty-cfg>"
    }

    val numLength = (instructions.length - 1).toString.length

    val builder = new StringBuilder

    for ((instr, idx) <- instructions.zipWithIndex) {
      if (idx > 0)
        builder.append("\n")

      if (labels && instr.labels.nonEmpty) {
        for ((label, idx) <- instr.labels.zipWithIndex) {
          if (idx > 0)
            builder.append(" ")
          builder.append(label)
        }
        builder.append(":\n")
      }

      if (lineNumbers) {
        val line = idx + 1
        builder.append(line + 1)
        builder.append(": ")
      }

      if (indentation && !lineNumbers) {
        builder.append("  ")
      }

      builder.append(instr.asmString)
    }

    for (lambda <- lambdas) {
      builder.append("\n\n# ")
      builder.append(lambda)
      builder.append("\n")
      builder.append(lambda.cfg.asmText(lineNumbers, labels, indentation))
    }

    builder.toString()
  }

  override def toString: String = asmText()
}

object ControlFlowGraph {
  private[controlFlow] def apply(instructions: Array[cfg.Instruction]): ControlFlowGraph = {
    val lambdas =
      instructions.flatMap(_.sourceEntities.collect { case lambda: DfConcreteLambdaRef => lambda })

    val graph = new ControlFlowGraph(instructions, lambdas)

    val jumps = instructions
      .collect { case ji: JumpingInstruction => ji }

    val labelsOfInstr = jumps
      .map(_.targetLabel)
      .groupBy(_.targetIndex)
      .mapValues(_.toSet)
      .withDefaultValue(Set.empty)


    for ((instr, idx) <- instructions.zipWithIndex) {
      cfg.Instruction.finalizeInstruction(instr, graph, labelsOfInstr(idx))
    }

    graph
  }
}