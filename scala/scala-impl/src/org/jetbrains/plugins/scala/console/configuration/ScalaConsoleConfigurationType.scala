package org.jetbrains.plugins.scala.console.configuration

import com.intellij.execution.configurations.{ConfigurationFactory, ConfigurationType}
import com.intellij.openapi.project.DumbAware
import javax.swing.Icon
import org.jetbrains.plugins.scala.icons.Icons

class ScalaConsoleConfigurationType extends ConfigurationType with DumbAware {
  private val confFactory = new ScalaConsoleRunConfigurationFactory(this)
  // to migrate configurations with the old id
  private val confFactoryLegacy = new ScalaConsoleRunConfigurationFactory(this) {
    override def getId: String = "Scala Console"
  }

  def getIcon: Icon = Icons.SCALA_CONSOLE

  def getDisplayName: String = "Scala REPL"

  def getConfigurationTypeDescription: String = "Scala REPL run configurations"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory, confFactoryLegacy)

  def getId: String = "ScalaScriptConsoleRunConfiguration"
}