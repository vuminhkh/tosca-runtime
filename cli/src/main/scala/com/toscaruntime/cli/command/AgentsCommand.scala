package com.toscaruntime.cli.command

import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.util.DeployUtil
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.io.StdIn
import scala.language.postfixOps

/**
  * Command to handle list, delete, show information of an agent
  *
  * @author Minh Khang VU
  */
object AgentsCommand {

  val commandName = "agents"

  private val listOpt = "list"

  private val logOpt = "log"

  private val startOpt = "start"

  private val stopOpt = "stop"

  private val deleteOpt = "delete"

  private val deployOpt = "deploy"

  private val undeployOpt = "undeploy"

  private val scaleOpt = "scale"

  private val infoOpt = "info"

  private val nodeNameOpt = "-n"

  private val instancesCountOpt = "-c"

  private val nodesInfoOpt = "nodes"

  private val outputsInfoOpt = "outputs"

  private val relationshipsInfoOpt = "relationships"

  private val nodeInfoOpt = "node"

  private val relationshipInfoOpt = "relationship"

  private val instanceInfoOpt = "instance"

  private val relationshipInstanceInfoOpt = "relationshipInstance"

  private lazy val scaleArgsParser = Space ~> ((token(nodeNameOpt) ~ (Space ~> token(StringBasic))) | (token(instancesCountOpt) ~ (Space ~> token(IntBasic)))) +

  private lazy val infoNodeArgsParser = token(nodeInfoOpt) ~ (Space ~> token(StringBasic))

  private lazy val relationshipArgsParser = token(relationshipInfoOpt) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val infoInstanceArgsParser = token(instanceInfoOpt) ~ (Space ~> token(StringBasic))

  private lazy val relationshipInstanceArgsParser = token(relationshipInstanceInfoOpt) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val infoExtraArgsParser = (Space ~> (token(nodesInfoOpt) | token(relationshipsInfoOpt) | token(outputsInfoOpt) | infoNodeArgsParser | infoInstanceArgsParser | relationshipArgsParser | relationshipInstanceArgsParser)) ?

  private lazy val agentsArgsParser = Space ~>
    (token(listOpt) |
      (token(startOpt) ~ (Space ~> token(StringBasic))) |
      (token(logOpt) ~ (Space ~> token(StringBasic))) |
      (token(stopOpt) ~ (Space ~> token(StringBasic))) |
      (token(deleteOpt) ~ (Space ~> token(StringBasic))) |
      (token(deployOpt) ~ (Space ~> token(StringBasic))) |
      (token(scaleOpt) ~ (Space ~> token(StringBasic)) ~ scaleArgsParser) |
      (token(undeployOpt) ~ (Space ~> token(StringBasic))) |
      (token(infoOpt) ~ (Space ~> token(StringBasic) ~ infoExtraArgsParser))) +

  private lazy val agentsActionsHelp = Help(commandName, (commandName, s"List, stop or delete agent, execute 'help $commandName' for more details"),
    s"""
       |$commandName [$listOpt| [$startOpt|$stopOpt|$deleteOpt|$deployOpt|$undeployOpt|$scaleOpt|$infoOpt|$logOpt] <deployment id> [other options]
       |$listOpt     : list all agents
       |$logOpt      : show the agent's log
       |$infoOpt     : show the agent's deployment details
       |  $infoOpt $outputsInfoOpt: show only outputs details
       |  $infoOpt $nodesInfoOpt: show only nodes details
       |  $infoOpt $relationshipsInfoOpt: show only relationships details
       |  $infoOpt $nodeInfoOpt <node id>: show node details
       |  $infoOpt $instanceInfoOpt <instance id>: show instance details
       |  $infoOpt $relationshipInfoOpt <source> <target>: show relationship node details
       |  $infoOpt $relationshipInstanceInfoOpt <source instance> <target instance>: show relationship instance details
       |$startOpt    : start agent, agent will begin to manage deployment
       |$stopOpt     : stop agent, agent will not manage deployment anymore
       |$deployOpt   : launch default deployment workflow
       |$undeployOpt : launch default un-deployment workflow
       |$scaleOpt    : launch default scale workflow on the given node
       |  $scaleOpt <deployment id> $nodeNameOpt <node name> $instancesCountOpt <instances count>
       |$deleteOpt   : delete agent
    """.stripMargin
  )

  lazy val instance = Command("agents", agentsActionsHelp)(_ => agentsArgsParser) { (state, args) =>
    // TODO multiple agents to manage a deployment ... For the moment one agent per deployment
    val client = state.attributes.get(Attributes.clientAttribute).get
    var fail = false
    args.head match {
      case "list" =>
        DeployUtil.listDeploymentAgents(client)
      case ("info", (deploymentId: String, extraArgs: Option[Any])) =>
        extraArgs match {
          case Some("nodes") => DeployUtil.printNodesDetails(client, deploymentId)
          case Some("relationships") => DeployUtil.printRelationshipsDetails(client, deploymentId)
          case Some("outputs") => DeployUtil.printOutputsDetails(client, deploymentId)
          case Some(("node", nodeId: String)) => DeployUtil.printNodeDetails(client, deploymentId, nodeId)
          case Some((("relationship", source: String), target: String)) => DeployUtil.printRelationshipDetails(client, deploymentId, source, target)
          case Some(("instance", instanceId: String)) => DeployUtil.printInstanceDetails(client, deploymentId, instanceId)
          case Some((("relationshipInstance", source: String), target: String)) => DeployUtil.printRelationshipInstanceDetails(client, deploymentId, source, target)
          case _ => DeployUtil.printDetails(client, deploymentId)
        }
      case (("scale", deploymentId: String), scaleOpts: Seq[(String, _)]) =>
        val scaleArgs = scaleOpts.toMap
        val nodeName = scaleArgs.get(nodeNameOpt)
        val newInstancesCount = scaleArgs.get(instancesCountOpt)
        if (nodeName.isEmpty) {
          println("Node name is mandatory for scale workflow")
          fail = true
        } else if (newInstancesCount.isEmpty) {
          println("Instances count is mandatory for scale workflow")
          fail = true
        } else {
          client.scale(deploymentId, nodeName.get.asInstanceOf[String], newInstancesCount.get.asInstanceOf[Int])
          println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
        }
      case ("deploy", deploymentId: String) =>
        client.deploy(deploymentId)
        println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
      case ("log", deploymentId: String) =>
        println("***Press enter to stop following logs***")
        val logCallback = client.tailLog(deploymentId, System.out)
        try {
          StdIn.readLine()
        } finally {
          logCallback.close()
        }
      case ("undeploy", deploymentId: String) =>
        client.undeploy(deploymentId)
        println(s"Undeployed $deploymentId")
        println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
      case ("start", deploymentId: String) =>
        client.startDeploymentAgent(deploymentId)
        println(s"Started $deploymentId")
      case ("stop", deploymentId: String) =>
        client.stopDeploymentAgent(deploymentId)
        println(s"Stopped $deploymentId")
      case ("delete", deploymentId: String) =>
        client.deleteDeploymentAgent(deploymentId)
        println(s"Deleted $deploymentId")
    }
    state
  }
}
