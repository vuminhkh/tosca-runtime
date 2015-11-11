package com.toscaruntime.tosca.runtime

import java.nio.file._
import com.toscaruntime.sdk.Deployment
import com.toscaruntime.constant.CompilerConstant
import com.toscaruntime.sdk.{Deployment, DeploymentPostConstructor}

import scala.collection.JavaConverters._

/**
 * Deploy generated code
 */
object Deployer {

  /**
   * Deploy the given recipe from the given recipe folder and the given input file and given provider configuration file
   *
   * @param deploymentRecipeFolder recipe's path
   * @param inputFile deployment input file
   * @param providerProperties provider's configuration
   * @return the created deployment
   */
  def createDeployment(deploymentRecipeFolder: Path, inputFile: Option[Path], providerProperties: Map[String, String], bootstrap: Boolean): Deployment = {
    val inputs = DeployerUtil.loadInputs(inputFile)
    createDeployment(deploymentRecipeFolder, inputs, providerProperties, bootstrap)
  }

  /**
   * Deploy the given recipe from the given recipe folder and the given input properties and given provider properties
   *
   * @param deploymentRecipeFolder recipe's path
   * @param inputs deployment input
   * @param providerProperties provider's properties
   * @return the created deployment
   */
  def createDeployment(deploymentRecipeFolder: Path, inputs: Map[String, AnyRef], providerProperties: Map[String, String], bootstrap: Boolean): Deployment = {
    val compiledClasses = DeployerUtil.compileJavaRecipe(
      List(
        deploymentRecipeFolder.resolve(CompilerConstant.TYPES_FOLDER),
        deploymentRecipeFolder.resolve(CompilerConstant.DEPLOYMENT_FOLDER)
      ), deploymentRecipeFolder.resolve(CompilerConstant.LIB_FOLDER))
    val classLoader = compiledClasses._2
    val loadedClasses = compiledClasses._1
    val currentClassLoader = Thread.currentThread().getContextClassLoader
    Thread.currentThread().setContextClassLoader(classLoader)
    val deployment = classLoader.loadClass("Deployment").newInstance().asInstanceOf[Deployment]
    deployment.initializeDeployment(deploymentRecipeFolder, inputs.asJava, bootstrap)
    val deploymentPostConstructors = DeployerUtil.findImplementations(loadedClasses, classLoader, classOf[DeploymentPostConstructor])
    deploymentPostConstructors.foreach(_.newInstance().asInstanceOf[DeploymentPostConstructor].postConstruct(deployment, providerProperties.asJava))
    Thread.currentThread().setContextClassLoader(currentClassLoader)
    deployment
  }
}
