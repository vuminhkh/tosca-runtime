package com.toscaruntime.cli.command

import java.nio.file.{Files, Paths}

import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.parser.Parsers
import com.toscaruntime.cli.util.{CompilationUtil, TabulatorUtil}
import com.toscaruntime.compiler.Compiler
import com.toscaruntime.util.FileUtil
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.collection.JavaConverters._
import scala.language.postfixOps

/**
  * Handle the CLI's compile and install command to compile and install csar in local repository
  *
  * @author Minh Khang VU
  */
object CsarsCommand {

  private val compileOpt = "compile"
  private val deleteOpt = "delete"
  private val installOpt = "install"
  private val listOpt = "list"
  private val infoOpt = "info"

  private lazy val csarsArgsParser = Space ~>
    ((token(listOpt) ~ ((Space ~> token(StringBasic)) ?)) |
      (token(compileOpt) ~ (Space ~> token(Parsers.filePathParser))) |
      (token(installOpt) ~ (Space ~> token(Parsers.filePathParser))) |
      (token(infoOpt) ~ (Space ~> (token(StringBasic) ~ (token(":") ~> token(StringBasic))))) |
      (token(deleteOpt) ~ (Space ~> (token(StringBasic) ~ (token(":") ~> token(StringBasic)))))) +

  private lazy val csarsHelp = Help("csars", ("csars", "Compile / install / get information of a csar in local repository, execute 'help csars' for more details"),
    """
      |csars [list <name filter> | [compile|install] <csar path> | [info|delete] <csar name>:<csar version>]
      |list   : show all csars installed in local repository
      |compile: compile a csar without installing it in local repository
      |install: compile a csar and install it in local repository
      |info   : show information about a csar installed in local repository
      |delete : delete a csar installed in local repository
    """.stripMargin
  )

  lazy val instance = Command("csars", csarsHelp)(_ => csarsArgsParser) { (state, args) =>
    val basedir = state.attributes.get(Attributes.basedirAttribute).get
    val repository = basedir.resolve("repository")
    args.head match {
      case ("list", filter: Option[String]) =>
        val headers = List("Name", "Version", "Last modified")
        val allCsars = FileUtil.listChildrenDirectories(repository, filter.map(List(_)).getOrElse(List.empty).toArray: _*).asScala.toSeq
        val allCsarsData = allCsars.flatMap {
          case csarAllVersions =>
            FileUtil.listChildrenDirectories(csarAllVersions).asScala.map { version =>
              List(csarAllVersions.getFileName.toString, version.getFileName.toString, Files.getLastModifiedTime(version).toString)
            }.toList
        }.toList
        if (allCsarsData.nonEmpty) {
          println(TabulatorUtil.format(headers :: allCsarsData))
        } else {
          println("No csar found")
        }
      case ("compile", csarPath: String) =>
        val result = Compiler.compile(Paths.get(csarPath), repository)
        if (result.isSuccessful) {
          println(s"[$csarPath] compiled successfully")
        } else {
          CompilationUtil.showErrors(result)
        }
      case ("install", csarPath: String) =>
        val result = Compiler.install(Paths.get(csarPath), repository)
        if (result.isSuccessful) {
          println(s"[$csarPath] installed successfully to [$repository]")
        } else {
          CompilationUtil.showErrors(result)
        }
      case ("delete", (csarName: String, csarVersion: String)) =>
        Compiler.resolveDependency(csarName, csarVersion, repository).foreach(FileUtil.delete)
        println(s"Deleted [$csarName:$csarVersion]")
      case ("info", (csarName: String, csarVersion: String)) =>
        val result = Compiler.compile(csarName, csarVersion, repository)
        if (result.isSuccessful) {
          println(s"Name: ${result.csar.csarName}")
          println(s"Version: ${result.csar.csarVersion}")
          println(s"Definitions [${result.csar.definitions.size}]:")
          result.csar.definitions.foreach { definitionEntry =>
            println(s"  - Content of [${definitionEntry._1}]:")
            definitionEntry._2.author.foreach { author =>
              println(s"    + Author: [${author.value}]")
            }
            definitionEntry._2.description.foreach { description =>
              println(s"    + Description: [${description.value}]")
            }
            definitionEntry._2.definitionVersion.foreach { definitionVersion =>
              println(s"    + Definition version: [${definitionVersion.value}]")
            }
            definitionEntry._2.imports.foreach { imports =>
              println(s"    + Dependencies: [${imports.map(_.value).mkString(", ")}]")
            }
            definitionEntry._2.nodeTypes.foreach { nodeTypes =>
              println(s"    + Node types: [${nodeTypes.size}]")
            }
            definitionEntry._2.relationshipTypes.foreach { relationshipTypes =>
              println(s"    + Relationship types: [${relationshipTypes.size}]")
            }
            definitionEntry._2.capabilityTypes.foreach { capabilityTypes =>
              println(s"    + Capability types: [${capabilityTypes.size}]")
            }
            definitionEntry._2.artifactTypes.foreach { artifactTypes =>
              println(s"    + Artifact types: [${artifactTypes.size}]")
            }
            definitionEntry._2.dataTypes.foreach { dataTypes =>
              println(s"    + Data types: [${dataTypes.size}]")
            }
            definitionEntry._2.groupTypes.foreach { groupTypes =>
              println(s"    + Group types: [${groupTypes.size}]")
            }
            definitionEntry._2.policyTypes.foreach { policyTypes =>
              println(s"    + Policy types: [${policyTypes.size}]")
            }
            definitionEntry._2.topologyTemplate.foreach { topologyTemplate =>
              println(s"    + Topology with [${topologyTemplate.nodeTemplates.size}] nodes")
            }
          }
        } else {
          println(s"Unexpected errors found in compiled csar [$csarName:$csarVersion]")
          CompilationUtil.showErrors(result)
        }
    }
    state
  }
}
