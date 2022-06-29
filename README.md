# mill-explicit-deps

A [Mill](https://com-lihaoyi.github.io/mill) plugin for enforcing explicit dependencies. Inspired by [sbt-explicit-dependencies](https://github.com/cb372/sbt-explicit-dependencies).

It allows you to check that `ivyDeps` and `ivyCompileDeps` accurately reflect the direct dependencies of your source code.

## Quickstart

Import the plugin in your `build.sc`:
```scala
import $ivy.`io.github.kierendavies::mill-explicit-deps::0.2.0`
import io.github.kierendavies.mill.explicitdeps.ExplicitDepsModule
```

Use the mixin:
```scala
object foo extends ScalaModule with ExplicitDepsModule {
    // ...
}
```

Then you can run the command `mill -k __.checkExplicitDeps`.
It will fail if it finds any transitive dependencies which are either
- imported by a source file but not declared (in `ivyDeps` or `compileIvyDeps`), or
- declared but not imported by any source files.

## Compatibility

This plugin requires Mill >=0.9.5 for Scala 2 modules and Mill >=0.9.7 for Scala 3 modules.

It has been tested with Scala 2.13.8, 3.1.3, and 3.2.0-RC1.
