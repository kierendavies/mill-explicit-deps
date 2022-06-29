import $exec.plugins
import $exec.testinfo

import io.github.kierendavies.mill.explicitdeps.ExplicitDepsModule
import mill._
import mill.scalalib._

object explicit extends ScalaModule with ExplicitDepsModule {
  def scalaVersion = TestInfo.scalaVersion
  def millSourcePath = millOuterCtx.millSourcePath / "importFromCoreAndKernel"
  def ivyDeps = Agg(
    ivy"org.typelevel::cats-core:2.7.0",
    ivy"org.typelevel::cats-kernel:2.7.0",
  )
}

object undeclared extends ScalaModule with ExplicitDepsModule {
  def scalaVersion = TestInfo.scalaVersion
  def millSourcePath = millOuterCtx.millSourcePath / "importFromCoreAndKernel"
  def ivyDeps = Agg(
    ivy"org.typelevel::cats-core:2.7.0"
  )
}

object unimported extends ScalaModule with ExplicitDepsModule {
  def scalaVersion = TestInfo.scalaVersion
  def millSourcePath = millOuterCtx.millSourcePath / "importFromCore"
  def ivyDeps = Agg(
    ivy"org.typelevel::cats-core:2.7.0",
    ivy"org.typelevel::cats-kernel:2.7.0",
  )
}

object undeclaredIgnored extends ScalaModule with ExplicitDepsModule {
  def scalaVersion = TestInfo.scalaVersion
  def millSourcePath = millOuterCtx.millSourcePath / "importFromCoreAndKernel"
  def ivyDeps = Agg(
    ivy"org.typelevel::cats-core:2.7.0"
  )
  def ignoreUndeclaredIvyDeps = T.task { dep: Dep =>
    super.ignoreUndeclaredIvyDeps().apply(dep) || dep.dep.module.name.value == "cats-kernel"
  }
}

object unimportedIgnored extends ScalaModule with ExplicitDepsModule {
  def scalaVersion = TestInfo.scalaVersion
  def millSourcePath = millOuterCtx.millSourcePath / "importFromCore"
  def ivyDeps = Agg(
    ivy"org.typelevel::cats-core:2.7.0",
    ivy"org.typelevel::cats-kernel:2.7.0",
  )
  def ignoreUnimportedIvyDeps = T.task { dep: Dep =>
    super.ignoreUndeclaredIvyDeps().apply(dep) || dep.dep.module.name.value == "cats-kernel"
  }
}
