package com.toscaruntime.runtime

import java.net.{URL, URLClassLoader}
import java.nio.file._
import java.util

import com.toscaruntime.constant.CompilerConstant
import com.toscaruntime.deployment.DeploymentPersister
import com.toscaruntime.exception.deployment.creation.ProviderHookNotFoundException
import com.toscaruntime.sdk._
import com.toscaruntime.util.{JavaScalaConversionUtil, ScalaFileUtil}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

/**
  * Deploy generated code
  */
object Deployer extends LazyLogging {

  /**
    * Deploy the given recipe from the given recipe folder and the given input file and given provider configuration file
    *
    * @param deploymentName           deployment's name
    * @param deploymentAssemblyFolder recipe's path
    * @param inputFile                deployment input file
    * @param bootstrapContextFile     bootstrap context file
    * @param providerConfigurations   provider's properties, provider name to properties map
    * @param pluginConfigurations     plugin's properties, plugin name to properties map
    * @param bootstrap                bootstrap mode enabled (in bootstrap mode, cli will use public ip to connect to VMs)
    * @param contextClassloader       the classloader that will be used to be the parent of the deployment
    * @param deploymentPersister      the dao to persist the deployment
    * @return the created deployment
    */
  def createDeployment(deploymentName: String,
                       deploymentAssemblyFolder: Path,
                       inputFile: Option[Path],
                       providerConfigurations: List[ProviderConfiguration],
                       pluginConfigurations: List[PluginConfiguration],
                       bootstrapContextFile: Option[Path],
                       bootstrap: Boolean,
                       contextClassloader: ClassLoader,
                       deploymentPersister: DeploymentPersister): Deployment = {
    val inputs = DeployerUtil.loadInputs(inputFile)
    val bootstrapContext = DeployerUtil.loadInputs(bootstrapContextFile)
    createDeployment(deploymentName, deploymentAssemblyFolder, inputs, providerConfigurations, pluginConfigurations, bootstrapContext, bootstrap, contextClassloader, deploymentPersister)
  }

  private def loadLibrary(lib: Path, parentClassLoader: ClassLoader) = {
    val names = ScalaFileUtil.listJavaClassNames(lib.resolve(CompilerConstant.SOURCE_FOLDER))
    val classPath = ListBuffer[URL]()
    val classesFolder = lib.resolve(CompilerConstant.TARGET_FOLDER)
    if (Files.isDirectory(classesFolder)) {
      classPath += classesFolder.toUri.toURL
    }
    val libFolder = lib.resolve(CompilerConstant.LIB_FOLDER)
    if (Files.isDirectory(libFolder)) {
      classPath ++= ScalaFileUtil.listFiles(libFolder).map(jar => jar.toUri.toURL)
    }
    val classLoader = URLClassLoader.newInstance(classPath.toArray, parentClassLoader)
    logger.info(s"Load library [$lib] with classpath [${classLoader.getURLs.mkString(",")}]")
    val classes = names.map(name => (name, classLoader.loadClass(name))).toMap[String, Class[_]]
    (classes, classLoader)
  }

  /**
    * Deploy the given recipe from the given recipe folder and the given input properties and given provider properties
    *
    * @param deploymentName           deployment's name
    * @param deploymentAssemblyFolder recipe's path
    * @param inputs                   deployment input
    * @param providerConfigurations   provider's properties, provider name to properties map
    * @param pluginConfigurations     plugin's properties, plugin name to properties map
    * @param bootstrapContext         bootstrap's context
    * @param bootstrap                bootstrap mode enabled (in bootstrap mode, cli will use public ip to connect to VMs)
    * @param contextClassloader       the classloader that will be used to be the parent of the deployment
    * @param deploymentPersister      the dao to persist the deployment
    * @return the created deployment
    */
  def createDeployment(deploymentName: String,
                       deploymentAssemblyFolder: Path,
                       inputs: Map[String, Any],
                       providerConfigurations: List[ProviderConfiguration],
                       pluginConfigurations: List[PluginConfiguration],
                       bootstrapContext: Map[String, Any],
                       bootstrap: Boolean,
                       contextClassloader: ClassLoader,
                       deploymentPersister: DeploymentPersister): Deployment = {

    val loadedDeployment = loadLibrary(deploymentAssemblyFolder.resolve(CompilerConstant.ASSEMBLY_RECIPE_FOLDER), contextClassloader)
    val deployment = loadedDeployment._2.loadClass("Deployment").newInstance().asInstanceOf[Deployment]

    val allProvidersPaths = ScalaFileUtil.list(deploymentAssemblyFolder.resolve(CompilerConstant.ASSEMBLY_PROVIDER_FOLDER))
    if (allProvidersPaths.isEmpty) {
      throw new ProviderHookNotFoundException("No provider is found on the classpath to initialize the deployment")
    }

    val allLoadedProviders = allProvidersPaths.map { provider =>
      (provider.getFileName.toString, loadLibrary(provider, contextClassloader))
    }.toMap
    val allProviders = allLoadedProviders.map { case (providerName, loadedProvider) =>
      val providerHookClasses = DeployerUtil.findImplementations(loadedProvider._1.values, classOf[ProviderHook])
      if (providerHookClasses.isEmpty) {
        throw new ProviderHookNotFoundException(s"""No provider hook is found on the classpath for $providerName to initialize the deployment""")
      } else {
        val allProviderTargetConfigurations = providerConfigurations.find(_.name == providerName).map(_.targets.map(target => (target.name, target.configuration)).toMap).map(_.asJava).getOrElse(new util.HashMap[String, util.Map[String, AnyRef]]())
        new Provider(allProviderTargetConfigurations, providerHookClasses.map(_.newInstance().asInstanceOf[ProviderHook]).toList.asJava)
      }
    }.toList

    val allPluginsPaths = ScalaFileUtil.list(deploymentAssemblyFolder.resolve(CompilerConstant.ASSEMBLY_PLUGIN_FOLDER))
    val allLoadedPlugins = allPluginsPaths.map { plugin =>
      (plugin.getFileName.toString, loadLibrary(plugin, contextClassloader))
    }.toMap
    val allPlugins = allLoadedPlugins.map { case (pluginName, loadedPlugin) =>
      val pluginHookClasses = DeployerUtil.findImplementations(loadedPlugin._1.values, classOf[PluginHook])
      val allPluginTargetConfigurations = pluginConfigurations.find(_.name == pluginName).map(_.targets.map(target => (target.name, target.configuration)).toMap).map(_.asJava).getOrElse(new util.HashMap[String, util.Map[String, AnyRef]]())
      new Plugin(allPluginTargetConfigurations, pluginHookClasses.map(_.newInstance().asInstanceOf[PluginHook]).toList.asJava)
    }.toList

    val typeRegistry = new SimpleTypeRegistry((allLoadedPlugins.values.flatMap(_._1) ++ allLoadedProviders.values.flatMap(_._1) ++ loadedDeployment._1).toMap.asJava)
    deployment.initializeConfig(deploymentName,
      deploymentAssemblyFolder,
      JavaScalaConversionUtil.toJavaMap(inputs),
      typeRegistry,
      JavaScalaConversionUtil.toJavaMap(bootstrapContext),
      allProviders.asJava,
      allPlugins.asJava,
      deploymentPersister,
      bootstrap
    )
    deployment
  }
}
