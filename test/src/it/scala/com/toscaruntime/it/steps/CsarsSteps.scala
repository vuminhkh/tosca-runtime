package com.toscaruntime.it.steps

import java.net.URL
import java.nio.file.{Files, Path, Paths}

import com.toscaruntime.cli.command.CsarsCommand
import com.toscaruntime.compiler.Compiler
import com.toscaruntime.compiler.tosca.CompilationResult
import com.toscaruntime.it.TestConstant._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.MustMatchers
import sbt.IO

/**
  * All related steps to CSARS
  *
  * @author Minh Khang VU
  */
object CsarsSteps extends MustMatchers with LazyLogging {

  def downloadZipFileAndExtract(url: String, target: Path) = {
    val tempDownloadedFile = Files.createTempFile(tempPath, "csar", ".zip")
    IO.download(new URL(url), tempDownloadedFile.toFile)
    IO.unzip(tempDownloadedFile.toFile, target.toFile)
    Files.delete(tempDownloadedFile)
  }

  def installCsar(csar: Path) = {
    Compiler.install(csar, repositoryPath)
  }

  def showCompilationErrors(compilationResult: CompilationResult) = {
    compilationResult.errors.foreach {
      case (path, errors) => errors.foreach { error =>
        logger.error("At [{}][{}.{}] : {}", Paths.get(path).getFileName, error.startPosition.line.toString, error.startPosition.column.toString, error.error)
      }
    }
  }

  def assertNoCompilationErrorsDetected(compilationResult: CompilationResult) = {
    showCompilationErrors(compilationResult)
    compilationResult.isSuccessful must be(true)
  }

  def assertCompilationErrorsDetected(compilationResult: CompilationResult) = {
    showCompilationErrors(compilationResult)
    compilationResult.isSuccessful must be(false)
  }

  def listCsarWithName(csarName: String) = {
    CsarsCommand.listCsars(repositoryPath, Some(csarName))
  }

  def assertCsarFound(csarsFound: Seq[(Path, Path)], csarName: String, csarVersion: String) = {
    val csarWithVersionFound = csarsFound.filter {
      case (csar, version) => version.getFileName.toString == csarVersion && csar.getFileName.toString == csarName
    }
    csarWithVersionFound must have size 1
    csarWithVersionFound.head._1.getFileName.toString must be(csarName)
    csarWithVersionFound.head._2.getFileName.toString must be(csarVersion)
  }

  def assertCsarNotFound(csarsFound: Seq[(Path, Path)], csarName: String, csarVersion: String) = {
    val notFound = csarsFound.find {
      case (csar, version) => version.getFileName.toString == csarVersion && csar.getFileName.toString == csarName
    }
    notFound must be(empty)
  }

  def deleteCsar(csarName: String, csarVersion: String) = {
    CsarsCommand.deleteCsar(repositoryPath, csarName, csarVersion)
  }

  def installProvider(provider: String) = {
    val providerPackagePath = prepareTestDataPath.resolve(s"$provider-provider-types")
    Files.exists(providerPackagePath) must be(true)
    IO.copyDirectory(prepareTestDataPath.resolve(s"$provider-provider-types").toFile, repositoryPath.resolve(s"$provider-provider-types").toFile)
  }

  def installNormativeTypesAndProviders() = {
    downloadZipFileAndExtract("https://github.com/alien4cloud/tosca-normative-types/archive/master.zip", tempPath)
    assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("tosca-normative-types-master")))
    downloadZipFileAndExtract("https://github.com/alien4cloud/alien4cloud-extended-types/archive/master.zip", tempPath)
    assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("alien4cloud-extended-types-master").resolve("alien-base-types")))
    assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("alien4cloud-extended-types-master").resolve("alien-extended-storage-types")))
    installProvider(dockerProvider)
    installProvider(openstackProvider)
  }
}
