package org.jetbrains.plugins.scala.debugger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings

/**
  * @author Nikolay.Tropin
  */
object DebuggerTestUtil {

  def enableCompileServer(enable: Boolean): Unit = {
    val compileServerSettings = ScalaCompileServerSettings.getInstance()
    assert(compileServerSettings != null, "could not get instance of compileServerSettings. Was plugin artifact built before running test? ")
    compileServerSettings.COMPILE_SERVER_ENABLED = enable
    compileServerSettings.COMPILE_SERVER_SHUTDOWN_IDLE = true
    compileServerSettings.COMPILE_SERVER_SHUTDOWN_DELAY = 30
    val application = ApplicationManager.getApplication
    application match {
      case applicationEx: ApplicationEx => applicationEx.setSaveAllowed(true)
      case _ =>
    }
    application.saveSettings()
  }

  def forceJdk8ForBuildProcess(): Unit = {
    val jdk8 = SmartJDKLoader.getOrCreateJDK(JavaSdkVersion.JDK_1_8)
    if (jdk8.getHomeDirectory == null) {
      throw new RuntimeException(s"Failed to set up JDK, got: ${jdk8.toString}")
    }
    val jdkHome = jdk8.getHomeDirectory.getCanonicalPath
    Registry.get("compiler.process.jdk").setValue(jdkHome)
  }

 }
