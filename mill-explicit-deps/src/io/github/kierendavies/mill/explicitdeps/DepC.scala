package io.github.kierendavies.mill.explicitdeps

import mill.scalalib.Dep

object DepC {

  private[explicitdeps] type Type = (String, String)

  // Equality on coursier.Dependency is by reference. We need to construct some
  // sort of well-behaved canonical representation.
  private[explicitdeps] def apply(
      binaryVersion: String,
      fullVersion: String,
      platformSuffix: String,
  )(dep: Dep): Type = {
    val module = dep
      .toDependency(
        binaryVersion,
        fullVersion,
        platformSuffix,
      )
      .module

    (module.organization.value, module.name.value)
  }

}
