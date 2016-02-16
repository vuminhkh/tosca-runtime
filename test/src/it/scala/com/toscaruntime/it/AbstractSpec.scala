package com.toscaruntime.it

import java.nio.file.Files

import com.toscaruntime.cli.command.UseCommand
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.steps.{AgentsSteps, DeploymentsSteps}
import com.toscaruntime.util.FileUtil
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
        ignoring(classOf[Exception])(AgentsSteps.launchUninstallWorkflow(agent.head))
        ignoring(classOf[Exception])(AgentsSteps.deleteAgent(agent.head))
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
      UseCommand.switchConfiguration(Context.dockerConfig.getUrl, Context.dockerConfig.getCertPath, testDataPath)
    } catch {
      case e: Throwable => logger.warn(s"Could not properly clean test data at ${testDataPath.toAbsolutePath}", e)
    }
  }

}
