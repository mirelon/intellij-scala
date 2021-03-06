package org.jetbrains.plugins.scala.util.runners

import java.lang.annotation.Annotation

import junit.extensions.TestDecorator
import junit.framework
import junit.framework.{Test, TestCase, TestSuite}
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_10, Scala_2_11, Scala_2_12, Scala_2_13}
import org.junit.experimental.categories.Category
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.{Describable, Description}

import scala.annotation.tailrec
import scala.collection.JavaConverters.{collectionAsScalaIterableConverter, enumerationAsScalaIteratorConverter}

class MultipleScalaVersionsRunner(myTest: Test, klass: Class[_]) extends JUnit38ClassRunner(myTest) {

  def this(klass: Class[_]) =
    this(MultipleScalaVersionsRunner.testSuite(klass.asSubclass(classOf[TestCase])), klass)

  override def getDescription: Description = {
    val description = MultipleScalaVersionsRunner.makeDescription(klass, myTest)
    //debugLog(description)
    description
  }
}

private object MultipleScalaVersionsRunner {

  private val DefaultRunScalaVersionsToRun = Seq(
    Scala_2_10,
    Scala_2_11,
    Scala_2_12,
    Scala_2_13,
  )

  private case class ScalaVersionTestSuite(name: String) extends TestSuite(name) {
    def this() = this(null: String)
    def this(version: ScalaVersion) = this(sanitize(s"(scala ${version.minor})"))
  }

  def testSuite(klass: Class[_ <: TestCase]): TestSuite = {
    assert(classOf[ScalaSdkOwner].isAssignableFrom(klass))

    val suite = new TestSuite
    suite.setName(klass.getName)

    val classVersions = scalaVersionsToRun(klass)
    assert(classVersions.nonEmpty, "at least one scala version should be specified")

    val allTestCases: Seq[(TestCase, ScalaVersion)] = new ScalaVersionAwareTestsCollector(klass, classVersions).collectTests()

    val childTests = childTestsByVersion(allTestCases)
    // val childTests = childTestsByName(allTests)
    childTests.foreach { childTest =>
      suite.addTest(childTest)
    }

    suite

  }

  private def childTestsByName(testsCases: Seq[(TestCase, ScalaVersion)]): Seq[Test] = {
    val nameToTests: Map[String, Seq[(TestCase, ScalaVersion)]] = testsCases.groupBy(_._1.getName)

    for {
      (testName, tests: Seq[(TestCase, ScalaVersion)]) <- nameToTests.toSeq.sortBy(_._1)
    } yield {
      if (tests.size == 1) tests.head._1
      else {
        val suite = new framework.TestSuite()
        suite.setName(testName)
        tests.sortBy(_._2).foreach { case (t, version) =>
          t.setName(testName + "." + sanitize(version.minor))
          suite.addTest(t)
        }
        suite
      }
    }
  }

  private def childTestsByVersion(testsCases: Seq[(TestCase, ScalaVersion)]): Seq[Test] = {
    val versionToTests: Map[ScalaVersion, Seq[Test]] = testsCases.groupBy(_._2).mapValues(_.map(_._1))

    if (versionToTests.size == 1) versionToTests.head._2
    else {
      for {
        (version, tests) <- versionToTests.toSeq.sortBy(_._1)
        if tests.nonEmpty
      } yield {
        val suite = new ScalaVersionTestSuite(version)
        tests.foreach(suite.addTest)
        suite
      }
    }
  }

  private def scalaVersionsToRun(klass: Class[_ <: TestCase]): Seq[ScalaVersion] = {
    val annotation = findAnnotation(klass, classOf[RunWithScalaVersions])
    annotation
      .map(_.value.map(_.toProductionVersion).toSeq)
      .getOrElse(DefaultRunScalaVersionsToRun)
  }

  private def findAnnotation[T <: Annotation](klass: Class[_], annotationClass: Class[T]): Option[T] = {
    @tailrec
    def inner(c: Class[_]): Annotation = c.getAnnotation(annotationClass) match {
      case null =>
        c.getSuperclass match {
          case null => null
          case parent => inner(parent)
        }
      case annotation => annotation
    }

    Option(inner(klass).asInstanceOf[T])
  }

  private def debugLog(d: Description, deep: Int = 0): Unit = {
    val annotations = d.getAnnotations.asScala.map(_.annotationType.getName).mkString(",")
    val details = s"${d.getMethodName}, ${d.getClassName}, ${d.getTestClass}, $annotations"
    val prefix = "##" + "    " * deep
    System.out.println(s"$prefix ${d.toString} ($details)")
    d.getChildren.forEach(debugLog(_, deep + 1))
  }

  // Copied from JUnit38ClassRunner, added "Category" annotation propagation for ScalaVersionTestSuite
  private def makeDescription(klass: Class[_], test: Test): Description = test match {
    case ts: TestSuite =>
      val name = Option(ts.getName).getOrElse(createSuiteDescriptionName(ts))
      val annotations = ts match {
        case _: ScalaVersionTestSuite => findAnnotation(klass, classOf[Category]).toSeq
        case _                        => Seq()
      }
      val description = Description.createSuiteDescription(name, annotations: _*)
      ts.tests.asScala.foreach { childTest =>
        // compiler fails on TeamCity without this case, no idea why
        //noinspection ScalaRedundantCast
        val childDescription = makeDescription(klass, childTest.asInstanceOf[Test])
        description.addChild(childDescription)
      }
      description
    case tc: TestCase             => Description.createTestDescription(tc.getClass, tc.getName)
    case adapter: Describable     => adapter.getDescription
    case decorator: TestDecorator => makeDescription(klass, decorator.getTest)
    case _                        => Description.createSuiteDescription(test.getClass)
  }

  private def createSuiteDescriptionName(ts: TestSuite): String = {
    val count = ts.countTestCases
    val example = if (count == 0) "" else " [example: %s]".format(ts.testAt(0))
    "TestSuite with %s tests%s".format(count, example)
  }

  // dot is treated as a package separator by IntelliJ which causes broken rendering in tests tree
  private def sanitize(testName: String): String = testName.replace(".", "_")
}

