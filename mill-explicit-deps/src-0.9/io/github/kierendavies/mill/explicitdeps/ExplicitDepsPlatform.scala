package io.github.kierendavies.mill.explicitdeps

import mill.T
import mill.define.Task
import mill.scalalib.Dep
import mill.scalalib.ScalaModule
import mill.scalalib.api.Util

trait ExplicitDepsPlatform { this: ScalaModule =>

  // For convenient currying.
  private[explicitdeps] def scalaVersionsAndPlatform: Task[(String, String, String)] = T.task {
    val scalaV = scalaVersion()
    val scalaBinV = Util.scalaBinaryVersion(scalaV)
    val platform = platformSuffix()
    (scalaBinV, scalaV, platform)
  }

  def ignoreUndeclaredIvyDeps: Task[Dep => Boolean] = T.task { dep: Dep =>
    val canonical = (DepC.apply _).tupled(scalaVersionsAndPlatform())
    val depC = canonical(dep)

    scalaLibraryIvyDeps().items.exists { mdep =>
      depC == canonical(mdep)
    }
  }

}
