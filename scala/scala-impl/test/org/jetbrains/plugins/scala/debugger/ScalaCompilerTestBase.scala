package org.jetbrains.plugins.scala
package debugger

import java.io.File

import com.intellij.compiler.server.BuildManager
import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.compiler._
import com.intellij.openapi.projectRoots._
import com.intellij.openapi.roots._
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs._
import com.intellij.testFramework.{CompilerTester, EdtTestUtil, JavaModuleTestCase, PsiTestUtil, VfsTestUtil}
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, ScalaCompileServerSettings}
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase.ListCompilerMessageExt
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.project.{IncrementalityType, ProjectExt}
import org.junit.Assert._
import java.util.{List => JList}

import scala.concurrent.duration
import scala.collection.JavaConverters._
import scala.language.implicitConversions

/**
 * Nikolay.Tropin
 * 2/26/14
 */
abstract class ScalaCompilerTestBase extends JavaModuleTestCase with ScalaSdkOwner {

  private var compilerTester: CompilerTester = _

  override protected def setUp(): Unit = {
    super.setUp()

    // uncomment to enable debugging of compile server in tests
    //    BuildManager.getInstance().setBuildProcessDebuggingEnabled(true)
    //    com.intellij.openapi.util.registry.Registry.get("compiler.process.debug.port").setValue(5006)

    myProject.subscribeToModuleRootChanged(getTestRootDisposable) { _ =>
      BuildManager.getInstance.clearState(myProject)
    }

    addSrcRoot()
    compilerVmOptions.foreach(setCompilerVmOptions)
    DebuggerTestUtil.enableCompileServer(useCompileServer)
    DebuggerTestUtil.forceJdk8ForBuildProcess()
    setUpLibraries(getModule)
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = incrementalityType
    compilerTester = new CompilerTester(getModule)
    addOutRoot()
  }

  override protected def tearDown(): Unit = try {
    compilerTester.tearDown()
    ScalaCompilerTestBase.stopAndWait()
    EdtTestUtil.runInEdtAndWait { () =>
      disposeLibraries(getModule)
    }
  } finally {
    compilerTester = null
    EdtTestUtil.runInEdtAndWait { () =>
      ScalaCompilerTestBase.super.tearDown()
    }
  }

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeScalaReflect = true),
    HeavyJDKLoader(),
    SourcesLoader(getSourceRootDir.getCanonicalPath)
  ) ++ additionalLibraries

  override protected def getTestProjectJdk: Sdk = SmartJDKLoader.getOrCreateJDK()

  protected def additionalLibraries: Seq[LibraryLoader] = Seq.empty

  protected def incrementalityType: IncrementalityType = IncrementalityType.IDEA

  protected def compilerVmOptions: Option[String] = None

  protected def useCompileServer: Boolean = false

  protected def compiler: CompilerTester = compilerTester

  protected def getBaseDir: VirtualFile = {
    val baseDir = myProject.baseDir
    assertNotNull(baseDir)
    baseDir
  }

  protected def getSourceRootDir: VirtualFile = getBaseDir.findChild("src")

  protected def addFileToProjectSources(relativePath: String, text: String): VirtualFile = VfsTestUtil.createFile(
    getSourceRootDir,
    relativePath,
    StringUtil.convertLineSeparators(text)
  )

  private def getOrCreateChildDir(name: String) = {
    val file = new File(getBaseDir.getCanonicalPath, name)
    if (!file.exists()) file.mkdir()
    LocalFileSystem.getInstance.refreshAndFindFileByPath(file.getCanonicalPath)
  }

  private def addSrcRoot(): Unit = inWriteAction {
    val srcRoot = getOrCreateChildDir("src")
    PsiTestUtil.addSourceRoot(getModule, srcRoot, false)
  }


  private def addOutRoot(): Unit = inWriteAction {
    val outRoot = getOrCreateChildDir("out")
    CompilerProjectExtension.getInstance(getProject).setCompilerOutputUrl(outRoot.getUrl)
  }

  private def setCompilerVmOptions(options: String): Unit =
    if (useCompileServer) {
      ScalaCompileServerSettings.getInstance.COMPILE_SERVER_JVM_PARAMETERS = options
    } else {
      CompilerConfiguration.getInstance(getProject).setBuildProcessVMOptions(options)
    }

  protected implicit def listCompilerMessage2Ext(messages: JList[CompilerMessage]): ListCompilerMessageExt =
    new ListCompilerMessageExt(messages)
}

object ScalaCompilerTestBase {

  import duration.{Duration, DurationInt}

  def stopAndWait(timeout: Duration = 10.seconds): Unit = assertTrue(
    "Compile server process have not terminated after " + timeout,
    CompileServerLauncher.stopAndWaitTermination(timeout.toMillis)
  )

  implicit class ListCompilerMessageExt(val messages: JList[CompilerMessage])
    extends AnyVal {

    /**
     * Checks if not compilation errors
     */
    def assertNoErrors(): Unit =
      assertNoMessages(Set(CompilerMessageCategory.ERROR))

    /**
     * Checks if no compilation errors or warnings
     */
    def assertNoProblems(): Unit =
      assertNoMessages(Set(CompilerMessageCategory.ERROR, CompilerMessageCategory.WARNING))

    private def assertNoMessages(categories: Set[CompilerMessageCategory]): Unit = {
      val problems = messages.asScala.filter { message =>
        categories.contains(message.getCategory)
      }
      assertTrue(
        s"Compilation problems: ${problems.mkString(",")}",
        problems.isEmpty
      )
    }
  }
}