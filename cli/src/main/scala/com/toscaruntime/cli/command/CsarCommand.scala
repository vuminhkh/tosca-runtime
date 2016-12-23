package com.toscaruntime.cli.command

import java.nio.file.{Files, Path, Paths}

import com.toscaruntime.cli.Args._
import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.parser.Parsers
import com.toscaruntime.cli.util.{CompilationUtil, TabulatorUtil}
import com.toscaruntime.compiler.Compiler
import com.toscaruntime.util.{FileUtil, PathUtil}
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.collection.JavaConverters._
import scala.language.postfixOps

/**
  * Handle the CLI's compile and install command to compile and install csar in local repository
  *
  * @author Minh Khang VU
  */
object CsarCommand {

  private val commandName = "csar"

  private val compileCmd = "compile"

  private val deleteCmd = "delete"

  private val installCmd = "install"

  private val listCmd = "list"

  private val infoCmd = "info"

  private lazy val listCmdParser = token(listCmd) ~ ((Space ~> token(StringBasic)) ?)

  private lazy val compileCmdParser = token(compileCmd) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val installCmdParser = token(installCmd) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val infoCmdParser = token(infoCmd) ~ (Space ~> (token(StringBasic) ~ (token(":") ~> token(StringBasic))))

  private lazy val deleteCmdParser = token(deleteCmd) ~ (Space ~> (token(StringBasic) ~ (token(":") ~> token(StringBasic))))

  private lazy val csarsCmdParser = Space ~>
    (listCmdParser |
      compileCmdParser |
      installCmdParser |
      infoCmdParser |
      deleteCmdParser)

  private lazy val csarsHelp = Help(commandName, (commandName, s"Compile / install / delete / get information of a csar in local repository, execute 'help $commandName' for more details"),
    f"""
       |$commandName <sub command> [ARGS]
       |
       |Sub commands:
       |
       |  $listCmd%-30s show all csars installed in local repository
       |  $synopsisToken%-30s $listCmd <name filter>
       |
       |  $compileCmd%-30s compile a csar without installing it in local repository
       |  $synopsisToken%-30s $compileCmd <csar path>
       |
       |  $installCmd%-30s compile a csar and install it in local repository
       |  $synopsisToken%-30s $installCmd <csar path>
       |
       |  $infoCmd%-30s show information about a csar installed in local repository
       |  $synopsisToken%-30s $infoCmd <csar name>:<csar version>
       |
       |  $deleteCmd%-30s delete a csar installed in local repository
       |  $synopsisToken%-30s $deleteCmd <csar name>:<csar version>
    """.stripMargin)

  def listCsars(repository: Path, filter: Option[String]) = {
    val allCsars = FileUtil.listChildrenDirectories(repository, filter.map(List(_)).getOrElse(List.empty).toArray: _*).asScala.toSeq
    allCsars.flatMap(csarAllVersions => FileUtil.listChildrenDirectories(csarAllVersions).asScala.map((csarAllVersions, _)))
  }

  private def printCsarsList(repository: Path, filter: Option[String]) = {
    val headers = List("Name", "Version", "Last modified")
    val allCsarsData = listCsars(repository, filter).map {
      case (csar, version) => List(csar.getFileName.toString, version.getFileName.toString, Files.getLastModifiedTime(version).toString)
    }.toList
    if (allCsarsData.nonEmpty) {
      println(TabulatorUtil.format(headers :: allCsarsData))
    } else {
      println("No csar found")
    }
  }

  def deleteCsar(repository: Path, csarName: String, csarVersion: String) = {
    Compiler.getCsarPath(csarName, csarVersion, repository).foreach(FileUtil.delete)
  }

  lazy val instance = Command(commandName, csarsHelp)(_ => csarsCmdParser) { (state, args) =>
    val basedir = state.attributes.get(Attributes.basedirAttribute).get
    val repository = basedir.resolve("repository")
    args match {
      case (`listCmd`, filter: Option[String]) => printCsarsList(repository, filter)
      case (`compileCmd`, csarPath: String) =>
        val result = PathUtil.openAsDirectory(Paths.get(csarPath), realPath => Compiler.compile(realPath, repository))
        if (result.isSuccessful) {
          println(s"[$csarPath] compiled successfully")
        } else {
          CompilationUtil.showErrors(result)
        }
      case (`installCmd`, csarPath: String) =>
        val result = PathUtil.openAsDirectory(Paths.get(csarPath), realPath => Compiler.install(realPath, repository))
        if (result.isSuccessful) {
          println(s"[$csarPath] installed successfully to [$repository]")
        } else {
          CompilationUtil.showErrors(result)
        }
      case (`deleteCmd`, (csarName: String, csarVersion: String)) =>
        deleteCsar(repository, csarName, csarVersion)
        println(s"Deleted [$csarName:$csarVersion]")
      case (`infoCmd`, (csarName: String, csarVersion: String)) =>
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
              println(s"    + Topology with [${topologyTemplate.nodeTemplates.map(_.size).getOrElse(0)}] nodes")
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
