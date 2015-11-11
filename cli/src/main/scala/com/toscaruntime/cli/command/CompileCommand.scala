package com.toscaruntime.cli.command

import java.nio.file.{Files, Paths}

import com.toscaruntime.cli.parser._
import com.toscaruntime.cli.Args
import com.toscaruntime.cli.parser.Parsers
import com.toscaruntime.cli.{Attributes, Args}
import com.toscaruntime.compiler.Compiler
import com.toscaruntime.util.FileUtil
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

/**
 * Handle the CLI's compile command which from a topology and all of its dependencies produce a deployable package
 *
 * @author Minh Khang VU
 */
object CompileCommand {

  private val topologyOpt = "-t"
  private val csarPathOpt = "-cp"
  private val outputOpt = "-o"

  private lazy val compileArgsParser = Space ~> (topologyPathArg | csarPathArg | Args.providerArg | outputPathArg) +
  private lazy val topologyPathArg = token(topologyOpt) ~ (Space ~> token(Parsers.filePathParser))
  private lazy val csarPathArg = token(csarPathOpt) ~ (Space ~> (token(Parsers.filePathParser) ~ ((token(":") ~> token(Parsers.filePathParser)) *)))
  // Add more providers options when you add provider to tosca-runtime here
  private lazy val outputPathArg = token(outputOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val compileHelp = Help("compile", ("compile", "Compile a topology to produce a deployable archive, execute 'help compile' for more details"),
    """
      |compile -t <topology path> [-cp <csar path>] -p <provider name> -o <output path>
      |-t   : path to topology archive
      |-cp  : csars path separated by ':', a csar should be preceded by all of its dependencies on the csars path
      |-p   : name of the provider
      |-o   : path to output of compiled deployable archive
    """.stripMargin
  )

  lazy val instance = Command("compile", compileHelp)(_ => compileArgsParser) { (state, args) =>
    val argsMap = args.toMap
    val topology = argsMap.get(topologyOpt)
    var fail = false
    if (topology.isEmpty) {
      println("-t is mandatory")
      fail = true
    }
    val csarPath = argsMap.get(csarPathOpt)
    val provider = argsMap.get(Args.providerOpt)
    if (provider.isEmpty) {
      println("-p is mandatory")
      fail = true
    }
    val output = argsMap.get(outputOpt)
    if (output.isEmpty) {
      println("-o is mandatory")
      fail = true
    }
    if (fail) {
      state.fail
    } else {
      val csarPathsForCompilation = csarPath.map { cp =>
        val cpr = cp.asInstanceOf[(String, Seq[String])]
        Paths.get(cpr._1) :: cpr._2.map(Paths.get(_)).toList
      }.getOrElse(List.empty)
      val basedir = state.attributes.get(Attributes.basedirAttribute).get
      val outputPath = Paths.get(output.get.asInstanceOf[String])
      if (Files.isRegularFile(outputPath)) {
        Files.delete(outputPath)
      } else if (Files.isDirectory(outputPath)) {
        FileUtil.delete(outputPath)
      }
      val compilationSuccessful = Compiler.compile(
        Paths.get(topology.get.asInstanceOf[String]),
        csarPathsForCompilation,
        basedir.resolve("providers").resolve(provider.get.asInstanceOf[String]),
        basedir.resolve("sdk"),
        outputPath
      )
      if (compilationSuccessful) {
        println("Compiled successfully and produced deployable recipe at <" + output.get.asInstanceOf[String] + ">")
        state
      } else {
        state.fail
      }
    }
  }
}
