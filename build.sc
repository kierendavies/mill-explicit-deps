import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.6.1`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.3.0`
import $ivy.`io.github.davidgregory084::mill-tpolecat::0.3.2`

import de.tobiasroeser.mill.integrationtest._
import de.tobiasroeser.mill.vcs.version.VcsVersion
import io.github.davidgregory084.TpolecatModule
import mill._
import mill.scalalib._
import mill.scalalib.api.ZincWorkerUtil
import mill.scalalib.publish._
import mill.scalalib.scalafmt.ScalafmtModule

val SemVer = raw"(\d+)\.(\d+)\.(\d+)(-.*)?".r

def millPlatform(millVersion: String): String = millVersion match {
  case SemVer("0", minor, _, _) => s"0.$minor"
}

trait CrossConfig {
  def millVersion: String
  def zincVersion: String

  def scalaVersion: String = "2.13.10"
  def millTestVersions: Seq[String] = {
    val vs = millVersion match {
      case SemVer(major, minor, patch, _) =>
        (0 until patch.toInt).map(testPatch => s"$major.$minor.$testPatch")
    }
    vs :+ millVersion
  }
  def scalaTestVersions: Seq[String] = Seq("2.13.10", "3.2.1")
  def itestCrossMatrix: Seq[(String, String)] = for {
    m <- millTestVersions
    s <- scalaTestVersions
  } yield (m, s)
}

val crossConfigs = Seq(
  new CrossConfig {
    def millVersion = "0.10.11"
    def zincVersion = "1.8.0"
  }
).map(c => millPlatform(c.millVersion) -> c).toMap

val crossMillVersions = crossConfigs.keys.toSeq

object `mill-explicit-deps` extends Cross[MillExplicitDepsModule](crossMillVersions: _*)
class MillExplicitDepsModule(millPlatform: String)
    extends CrossScalaModule
    with PublishModule
    with ScalafmtModule
    with TpolecatModule {

  val crossConfig = crossConfigs(millPlatform)
  def crossScalaVersion = crossConfig.scalaVersion
  def artifactSuffix = s"_mill${millPlatform}" + super.artifactSuffix()

  override def sources = T.sources {
    Seq(
      PathRef(millSourcePath / s"src"),
      PathRef(millSourcePath / s"src-$millPlatform"),
    )
  }

  // It is arguably "cheating" not to declare all dependencies explicitly here,
  // but then we would have to deal with figuring out all the right dependency
  // versions for the given version of Mill.
  def ivyDeps = Agg(
    ivy"com.lihaoyi::mill-scalalib:${crossConfig.millVersion}",
    ivy"org.scala-sbt::zinc-persist:${crossConfig.zincVersion}",
  )

  def publishVersion = VcsVersion
    .vcsState()
    .format(
      untaggedSuffix = "-SNAPSHOT"
    )
  def pomSettings = PomSettings(
    description = "Mill plugin for enforcing explicit dependencies",
    organization = "io.github.kierendavies",
    url = "https://github.com/kierendavies/mill-explicit-deps",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github("kierendavies", "mill-explicit-deps"),
    developers = Seq(
      Developer("kierendavies", "Kieren Davies", "https://github.com/kierendavies")
    ),
  )

}

val itestCrossMatrix = crossConfigs.values.toSeq.flatMap(_.itestCrossMatrix)

object itest extends Cross[ItestCross](itestCrossMatrix: _*)
class ItestCross(_millTestVersion: String, scalaTestVersion: String) extends MillIntegrationTestModule {

  def millTestVersion = _millTestVersion
  def pluginsUnderTest = Seq(`mill-explicit-deps`(millPlatform(_millTestVersion)))
  def millSourcePath = millOuterCtx.millSourcePath

  def testInvocations = Seq(
    PathRef(millSourcePath) -> Seq(
      TestInvocation.Targets(Seq("explicit.checkExplicitDeps")),
      TestInvocation.Targets(Seq("undeclared.checkExplicitDeps"), 1),
      TestInvocation.Targets(Seq("unimported.checkExplicitDeps"), 1),
      TestInvocation.Targets(Seq("undeclaredIgnored.checkExplicitDeps")),
      TestInvocation.Targets(Seq("unimportedIgnored.checkExplicitDeps")),
    )
  )

  def generateSharedSources = T {
    os.write(
      T.dest / "testinfo.sc",
      s"""|object TestInfo {
          |  val scalaVersion = "$scalaTestVersion"
          |}
          |""".stripMargin,
    )
    PathRef(T.dest)
  }

  def perTestResources = T.sources {
    Seq(generateSharedSources())
  }
}
