package io.github.kierendavies.mill.explicitdeps

import scala.xml.XML

import coursier.core.ModuleName
import coursier.core.Project
import coursier.core.compatibility.xmlFromElem
import coursier.ivy.IvyXml
import coursier.maven.Pom
import coursier.util.Xml
import mill.Agg
import mill.PathRef
import mill.T
import mill.api.Result
import mill.define.Command
import mill.define.Target
import mill.scalalib.CrossVersion
import mill.scalalib.Dep
import mill.scalalib.ScalaModule
import sbt.internal.inc.Analysis
import sbt.internal.inc.FileAnalysisStore
import xsbti.VirtualFileRef

trait ExplicitDepsModule extends ScalaModule with ExplicitDepsPlatform {

  def declaredIvyDeps: Target[Agg[Dep]] = T {
    ivyDeps() ++ compileIvyDeps()
  }

  // It's called resolved but it's actually the primary source which we will
  // unresolve later.
  def resolvedImportedIvyDeps: Target[Agg[PathRef]] = T {
    val analysisFile = compile().analysisFile
    T.log.debug(s"Reading imported dependencies from analysis file ${analysisFile}")

    val analysis =
      FileAnalysisStore
        .binary(
          analysisFile.toIO
        )
        .unsafeGet()
        .getAnalysis()
        .asInstanceOf[Analysis]
    T.log.debug(analysis.toString)

    Agg.from(
      analysis.relations.allLibraryDeps
        .filter(ref => ref.id().endsWith(".jar"))
        .map { ref: VirtualFileRef =>
          PathRef(os.Path(ref.id()), quick = true)
        }
    )
  }

  // Unresolve resolvedImportedIvyDeps.
  def importedIvyDeps: Target[Agg[Dep]] = T {
    val pomToDepF = pomToDep.tupled(scalaVersionsAndPlatform())
    val ivyXmlToDepF = ivyXmlToDep.tupled(scalaVersionsAndPlatform())

    def logFound(path: os.Path) = T.log.debug(s"Found module descriptor $path")

    val (errs: Seq[(PathRef, Seq[os.Path])], deps: Seq[Dep]) =
      resolvedImportedIvyDeps().indexed.partitionMap { jar =>
        lazy val pom = pomPath(jar.path)
        lazy val ivyXmlLocal = ivyXmlLocalPath(jar.path)
        lazy val ivyXmlCache = ivyXmlCachePath(jar.path)

        if (os.exists(pom)) {
          logFound(pom)
          Right(pomToDepF(pom))
        } else if (os.exists(ivyXmlLocal)) {
          logFound(ivyXmlLocal)
          Right(ivyXmlToDepF(ivyXmlLocal))
        } else if (os.exists(ivyXmlCache)) {
          logFound(ivyXmlCache)
          Right(ivyXmlToDepF(ivyXmlCache))
        } else {
          Left(jar -> Seq(pom, ivyXmlLocal, ivyXmlCache))
        }
      }

    if (errs.nonEmpty) {
      val header =
        s"""|
            |Cound not find module descriptors for ${errs.size} JARs:
            |--------------------------------------------
            |""".stripMargin

      val errMsgs = errs.map { case (jar, projPaths) =>
        s"  ${jar.path}\n" +
          projPaths.map(p => s"    not found: $p").mkString("\n")
      }

      val msg = header + errMsgs.mkString("\n") + "\n"
      Result.Failure(msg)
    } else {
      Result.Success(Agg.from(deps))
    }
  }

  def undeclaredIvyDeps: Target[Agg[Dep]] = T {
    val canonical = (DepC.apply _).tupled(scalaVersionsAndPlatform())

    val declaredC = declaredIvyDeps().map(canonical)

    importedIvyDeps().filter { dep =>
      if (ignoreUndeclaredIvyDeps().apply(dep)) {
        T.log.debug(s"Ignoring undeclared dependency ${ivyDepDecl(dep)}")
        false
      } else !declaredC.contains(canonical(dep))
    }
  }

  def unimportedIvyDeps: Target[Agg[Dep]] = T {
    val canonical = (DepC.apply _).tupled(scalaVersionsAndPlatform())

    val importedC = importedIvyDeps().map(canonical)

    declaredIvyDeps().filter { dep =>
      !importedC.contains(canonical(dep))
    }
  }

  def checkExplicitDeps(): Command[Unit] = T.command {
    def logError(deps: Seq[Dep], header: String): Unit =
      if (deps.nonEmpty) {
        val depMsgs = deps.map { dep =>
          // Four leading spaces, and trailing comma, because that is the most
          // common formatting of entries in ivyDeps.
          s"    ${ivyDepDecl(dep)},"
        }.sorted
        T.log.error(header + "\n" + depMsgs.mkString("\n") + "\n")
      }

    val undeclared = undeclaredIvyDeps().indexed
    val unimported = unimportedIvyDeps().indexed

    if (undeclared.nonEmpty || unimported.nonEmpty) {
      logError(
        undeclared,
        "Found undeclared dependencies: (add these to ivyDeps)",
      )

      logError(
        unimported,
        "Found unimported dependencies: (remove these from ivyDeps)",
      )

      Result.Failure(s"Found ${undeclared.size} undeclared dependencies, ${unimported.size} unimported dependencies")
    } else {
      T.log.info("All dependencies are explicit")
      Result.Success(())
    }
  }

  private def pomPath(jar: os.Path): os.Path =
    jar / os.up / s"${jar.baseName}.pom"

  private def ivyXmlLocalPath(jar: os.Path): os.Path =
    jar / os.up / os.up / "ivys" / "ivy.xml"

  private def ivyXmlCachePath(jar: os.Path): os.Path = {
    val version = jar.baseName.split('-').last
    jar / os.up / os.up / s"ivy-$version.xml"
  }

  private def moduleXmlToDep(
      readProject: Xml.Node => Either[String, Project]
  )(
      binaryVersion: String,
      fullVersion: String,
      platformVersion: String,
  )(
      xmlPath: os.Path
  ): Dep = {
    val binarySuffix = "_" + binaryVersion
    val fullSuffix = "_" + fullVersion

    val xml = XML.loadFile(xmlPath.toIO)
    readProject(xmlFromElem(xml)) match {
      case Left(msg) =>
        throw new Exception(s"Failed to read metadata: $msg ($xmlPath)")
      case Right(proj) =>
        // TODO
        val _ = platformVersion
        // Parse cross version suffixes
        // val (moduleName, cross) = proj.module.name.value match {
        //   case s"${pref}" => (???, ???)
        // }
        val (moduleName, cross) =
          if (proj.module.name.value.endsWith(binarySuffix))
            (
              ModuleName(proj.module.name.value.stripSuffix(binarySuffix)),
              CrossVersion.Binary(false),
            )
          else if (proj.module.name.value.endsWith(fullSuffix))
            (
              ModuleName(proj.module.name.value.stripSuffix(fullSuffix)),
              CrossVersion.Full(false),
            )
          else
            (proj.module.name, CrossVersion.empty(false))

        Dep(
          coursier.Dependency(
            proj.module.withName(moduleName),
            // proj.module,
            proj.version,
          ),
          cross,
          // CrossVersion.empty(false),
          false,
        )
    }
  }

  private val pomToDep = (moduleXmlToDep _)(Pom.project)
  private val ivyXmlToDep = (moduleXmlToDep _)(IvyXml.project)

  private def ivyDepDecl(dep: Dep): String = {
    val sep1 = dep.cross match {
      case _: CrossVersion.Constant => ":"
      case _: CrossVersion.Binary => "::"
      case _: CrossVersion.Full => ":::"
    }
    val sep2 = if (dep.cross.platformed) "::" else ":"

    // TODO attributes?

    "ivy\"" +
      dep.dep.module.organization.value +
      sep1 +
      dep.dep.module.name.value +
      sep2 +
      dep.dep.version +
      "\""
  }

}
