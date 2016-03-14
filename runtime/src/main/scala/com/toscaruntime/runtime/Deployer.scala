package com.toscaruntime.runtime

import java.nio.file._

import com.toscaruntime.constant.CompilerConstant
import com.toscaruntime.deployment.DeploymentPersister
import com.toscaruntime.exception.deployment.creation.{MultipleProviderHooksFoundException, ProviderHookNotFoundException}
import com.toscaruntime.sdk.{Deployment, ProviderHook}
import com.toscaruntime.util.JavaScalaConversionUtil

import scala.collection.JavaConverters._

/**
  * Deploy generated code
  */
object Deployer {

  /**
    * Deploy the given recipe from the given recipe folder and the given input file and given provider configuration file
    *
    * @param deploymentName         deployment's name
    * @param deploymentRecipeFolder recipe's path
    * @param inputFile              deployment input file
    * @param bootstrapContextFile   bootstrap context file
    * @param providerProperties     provider's configuration
    * @param bootstrap              bootstrap mode enabled (in bootstrap mode, cli will use public ip to connect to VMs)
    * @param contextClassloader     the classloader that will be used to be the parent of the deployment
    * @param deploymentPersister    the dao to persist the deployment
    * @return the created deployment
    */
  def createDeployment(deploymentName: String,
                       deploymentRecipeFolder: Path,
                       inputFile: Option[Path],
                       providerProperties: Map[String, String],
                       bootstrapContextFile: Option[Path],
                       bootstrap: Boolean,
                       contextClassloader: ClassLoader,
                       deploymentPersister: DeploymentPersister): Deployment = {
    val inputs = DeployerUtil.loadInputs(inputFile)
    val bootstrapContext = DeployerUtil.loadInputs(bootstrapContextFile)
    createDeployment(deploymentName, deploymentRecipeFolder, inputs, providerProperties, bootstrapContext, bootstrap, contextClassloader, deploymentPersister)
  }

  /**
    * Deploy the given recipe from the given recipe folder and the given input properties and given provider properties
    *
    * @param deploymentName         deployment's name
    * @param deploymentRecipeFolder recipe's path
    * @param inputs                 deployment input
    * @param providerProperties     provider's properties
    * @param bootstrapContext       bootstrap's context
    * @param bootstrap              bootstrap mode enabled (in bootstrap mode, cli will use public ip to connect to VMs)
    * @param contextClassloader     the classloader that will be used to be the parent of the deployment
    * @param deploymentPersister    the dao to persist the deployment
    * @return the created deployment
    */
  def createDeployment(deploymentName: String,
                       deploymentRecipeFolder: Path,
                       inputs: Map[String, Any],
                       providerProperties: Map[String, String],
                       bootstrapContext: Map[String, Any],
                       bootstrap: Boolean,
                       contextClassloader: ClassLoader,
                       deploymentPersister: DeploymentPersister): Deployment = {
    val compiledClasses = DeployerUtil.compileJavaRecipe(
      List(
        deploymentRecipeFolder.resolve(CompilerConstant.TYPES_FOLDER),
        deploymentRecipeFolder.resolve(CompilerConstant.DEPLOYMENT_FOLDER)
      ), deploymentRecipeFolder.resolve(CompilerConstant.LIB_FOLDER), contextClassloader)
    val deploymentClassLoader = compiledClasses._2
    val loadedClasses = compiledClasses._1
    val currentClassLoader = Thread.currentThread().getContextClassLoader
    Thread.currentThread().setContextClassLoader(deploymentClassLoader)
    val deployment = deploymentClassLoader.loadClass("Deployment").newInstance().asInstanceOf[Deployment]
    val providerHookClasses = DeployerUtil.findImplementations(loadedClasses, deploymentClassLoader, classOf[ProviderHook])
    if (providerHookClasses.isEmpty) {
      throw new ProviderHookNotFoundException("No provider hook is found on the classpath to initialize the deployment")
    }
    if (providerHookClasses.size > 1) {
      throw new MultipleProviderHooksFoundException(s"More than one provider hooks exist on the classpath ${providerHookClasses.map(_.getName).mkString(", ")}")
    }
    val providerHookClass = providerHookClasses.head
    val providerHookInstance = providerHookClass.newInstance().asInstanceOf[ProviderHook]
    deployment.initializeConfig(deploymentName,
      deploymentRecipeFolder,
      JavaScalaConversionUtil.toJavaMap(inputs),
      providerProperties.asJava,
      JavaScalaConversionUtil.toJavaMap(bootstrapContext),
      providerHookInstance,
      deploymentPersister,
      bootstrap
    )
    Thread.currentThread().setContextClassLoader(currentClassLoader)
    deployment
  }
}
