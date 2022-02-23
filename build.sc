import $ivy.`io.github.davidgregory084::mill-tpolecat::0.3.0`

import io.github.davidgregory084.TpolecatModule
import mill._
import mill.scalalib._
import mill.scalalib.api.ZincWorkerUtil
import mill.scalalib.publish._
import mill.scalalib.scalafmt.ScalafmtModule

case class Versions(
    mill: String,
    zinc: String,
)

object Versions {
  val forMill = Map(
    "0.9.12" -> Versions(
      mill = "0.9.12",
      zinc = "1.5.5",
    ),
    "0.10.0" -> Versions(
      mill = "0.10.0",
      zinc = "1.5.9",
    ),
  )

  val millCross = forMill.keys.toSeq.sorted
}

object `mill-explicit-deps` extends Cross[MillExplicitDepsModule](Versions.millCross: _*)
class MillExplicitDepsModule(millVersion: String)
    extends CrossScalaModule
    with PublishModule
    with ScalafmtModule
    with TpolecatModule {

  def crossScalaVersion = "2.13.8"
  def millBinaryVersion = ZincWorkerUtil.scalaNativeBinaryVersion(millVersion)
  def artifactSuffix = s"_mill${millBinaryVersion}" + super.artifactSuffix()

  override def sources = T.sources {
    PathRef(millSourcePath / s"src") +:
      ZincWorkerUtil.matchingVersions(millVersion).map { v =>
        PathRef(millSourcePath / s"src-$v")
      }
  }

  // It is arguably "cheating" not to declare all dependencies explicitly here,
  // but then we would have to deal with figuring out all the right dependency
  // versions for the given version of Mill.
  val v = Versions.forMill(millVersion)
  def ivyDeps = Agg(
    ivy"com.lihaoyi::mill-scalalib:${v.mill}",
    ivy"org.scala-sbt::zinc-persist:${v.zinc}",
  )

  def publishVersion = "0.1.0"
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
