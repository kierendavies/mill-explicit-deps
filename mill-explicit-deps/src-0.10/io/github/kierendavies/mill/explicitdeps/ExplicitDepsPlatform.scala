package io.github.kierendavies.mill.explicitdeps

import mill.Agg
import mill.T
import mill.define.Target
import mill.define.Task
import mill.moduledefs.Cacher
import mill.scalalib.Dep
import mill.scalalib.api.ZincWorkerUtil

trait ExplicitDepsPlatform extends Cacher { this: ExplicitDepsModule =>

  // For convenient currying.
  def scalaVersionsAndPlatform: Task[(String, String, String)] = T.task {
    val scalaV = scalaVersion()
    val scalaBinV = ZincWorkerUtil.scalaBinaryVersion(scalaV)
    val platform = platformSuffix()
    (scalaBinV, scalaV, platform)
  }

  def transitiveMandatoryIvyDeps: Target[Agg[Dep]] = T {
    // The easiest way to get transitive dependencies is to resolve and
    // unresolve.
    unresolveDeps(resolveDeps(mandatoryIvyDeps))()
  }

  def ignoreUndeclaredIvyDeps: Task[Dep => Boolean] = T.task { dep: Dep =>
    val canonical = (DepC.apply _).tupled(scalaVersionsAndPlatform())
    val depC = canonical(dep)

    transitiveMandatoryIvyDeps().items.exists { mdep =>
      depC == canonical(mdep)
    }
  }

}
