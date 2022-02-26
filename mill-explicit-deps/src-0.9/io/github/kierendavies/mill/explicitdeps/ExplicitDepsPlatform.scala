package io.github.kierendavies.mill.explicitdeps

import mill.Agg
import mill.T
import mill.define.Target
import mill.define.Task
import mill.moduledefs.Cacher
import mill.scalalib.Dep
import mill.scalalib.api.Util

private[explicitdeps] trait ExplicitDepsPlatform extends Cacher { this: ExplicitDepsModule =>

  // For convenient currying.
  private[explicitdeps] def scalaVersionsAndPlatform: Task[(String, String, String)] = T.task {
    val scalaV = scalaVersion()
    val scalaBinV = Util.scalaBinaryVersion(scalaV)
    val platform = platformSuffix()
    (scalaBinV, scalaV, platform)
  }

  def transitiveScalaLibraryIvyDeps: Target[Agg[Dep]] = T {
    // The easiest way to get transitive dependencies is to resolve and
    // unresolve.
    unresolveDeps(resolveDeps(scalaLibraryIvyDeps))()
  }

  def ignoreUndeclaredIvyDeps: Task[Dep => Boolean] = T.task { dep: Dep =>
    val canonical = (DepC.apply _).tupled(scalaVersionsAndPlatform())
    val depC = canonical(dep)

    transitiveScalaLibraryIvyDeps().items.exists { mdep =>
      depC == canonical(mdep)
    }
  }

}
