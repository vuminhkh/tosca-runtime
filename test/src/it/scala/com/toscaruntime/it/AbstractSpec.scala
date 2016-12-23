package com.toscaruntime.it

import java.nio.file.Files

import com.toscaruntime.cli.command.DaemonCommand
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.steps.{AgentsSteps, CsarsSteps, DeploymentsSteps}
import com.toscaruntime.util.{FileUtil, ScalaFileUtil}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfter, FeatureSpec, GivenWhenThen}

import scala.util.control.Exception.ignoring

/**
  * Base configuration for all specs
  *
  * @author Minh Khang VU
  */
class AbstractSpec extends FeatureSpec with GivenWhenThen with LazyLogging with BeforeAndAfter {

  before {
    try {
      logger.info("Cleaning agents on the local docker daemon")
      AgentsSteps.listAgents().foreach { agent =>
        logger.info(s"Cleaning agent [${agent.head}]")
        ignoring(classOf[Exception])(AgentsSteps.launchUndeployment(agent.head))
        logger.info(s"Cleaned agent [${agent.head}]")
      }
      logger.info("Cleaning deployment images on the local docker daemon")
      DeploymentsSteps.listDeploymentImages().foreach { image =>
        logger.info(s"Cleaning image [${image.head}]")
        ignoring(classOf[Exception])(DeploymentsSteps.deleteDeploymentImage(image.head))
        logger.info(s"Cleaned image [${image.head}]")
      }
      logger.info(s"Cleaning test data at ${testDataPath.toAbsolutePath}")
      FileUtil.delete(testDataPath)
      Files.createDirectories(repositoryPath)
      Files.createDirectories(tempPath)
      Files.createDirectories(assemblyPath)
      logger.info(s"Cleaned test data ${testDataPath.toAbsolutePath}")
      ScalaFileUtil.copyRecursive(prepareTestDataPath, testDataPath)
      CsarsSteps.downloadZipFileAndExtract("https://github.com/vuminhkh/alien4cloud-extended-types/archive/master.zip", tempPath)
      CsarsSteps.assertNoCompilationErrorsDetected(CsarsSteps.installCsar(tempPath.resolve("alien4cloud-extended-types-master").resolve("alien-base-types")))
      CsarsSteps.assertNoCompilationErrorsDetected(CsarsSteps.installCsar(tempPath.resolve("alien4cloud-extended-types-master").resolve("alien-extended-storage-types")))
      DaemonCommand.switchConfiguration(Context.dockerConfig, testConfigPath)
    } catch {
      case e: Throwable => logger.warn(s"Could not properly clean test data at ${testDataPath.toAbsolutePath}", e)
    }
  }

}
