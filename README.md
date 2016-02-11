Tosca Runtime: Development and deployment platform for Tosca
============================
[Tosca](https://www.oasis-open.org/committees/tosca/faq.php) from developer's point of view is a cloud deployment orchestration language.
The language is fairly complex with a lots of feature. Its notion of type, template and instance have many similarity with object oriented language like Java.
The motivation behind this project is to create a light weight runtime for this new language, which must contain:
* A compiler, which from a tosca recipe, generates deployment code.
* A runtime which is capable of deploying generated code by the compiler (deployment orchestrator).

Advantage of Tosca Runtime:
* Agent-less, not any process is spawn on the target VM, so much more rapid, artifact is executed directly from the CLI on the target VM (or docker ...), script's log can be followed by the end user
* It can work manager-less (only a docker daemon is required), to deploy on docker or even to deploy on a distant IAAS (from the moment when the target VMs are reachable)
* Each deployment is self-contained, a deployment in failure will never impact others, possibility to stop, move the deployment manager agents to other machine, 
restart in all transparency without the deployed applications even know about it.

Tosca Runtime's aim is to become a basic development kit for Tosca as much as a JDK for Java. It focus on the aspects simplicity, light weight, rapidity to develop Tosca recipe, to test and to deploy.

Architecture
============================
Basically, Tosca Runtime is a set of command line tools that make development/deployment of Tosca recipe quicker.
Tosca Runtime for the moment target Docker and OpenStack as IaaS Provider.

Those are main components and features:

### Compiler

Tosca Runtime compiles and generates deployable code for your archive through 3 steps:
  
* Syntax analyzer: from tosca code, parse and create syntax tree, after this step, the tosca recipe has valid syntax.
* Semantic analyzer: from the syntax tree, perform semantic analysis to detect incoherence in the syntax tree 
(for example: derived from a type which do not exist, property value do not follow its type and constraint etc ...).
* Code generator: generate code to deploy the tosca recipe.
  
Usage:

```bash
csars install PATH_TO_YOUR_ARCHIVE
```

Errors detected in the recipe are shown with line and column number, which helps recipe developers to quickly fix them.

### Dependency management

Tosca runtime for the moment propose a basic dependency management mechanism based on CSAR's (Cloud service archive) name and version.
CSARs are installed in the local repository and can be reused to compile others CSARs that depends on them.
The `csars install` command not only compiles but also installs the given csar to the local repository.

### Deployment Orchestrator
 
Tosca Runtime uses Docker to handle deployment lifecycle, the only pre-requisite is to have a running docker daemon.

![alt text](https://github.com/vuminhkh/tosca-runtime/raw/master/src/common/images/ManagerLessArchitecture.png "Managerless architecture")

In this managerless Tosca Runtime configuration, no manager bootstrap is required to begin to use Tosca Runtime CLI. 
The deployment can event target a distant IAAS if each created VM has a public ip and is reachable. 

The common workflow to use Tosca Runtime is:

* Create Deployment Image: 
From installed types and topology archives, Tosca Runtime allows you to create docker images that can be used to deploy your topology.
All necessary information are packaged into this image which make the deployment reproducible and can be shared with other people and other team.

  Usage:
  ```bash
  # To create the deployment from an installed archive
  deployments create myDeployment -c myTopologyArchive:*
  # To list created deployments
  deployments list
  ```

* Create deployment agent from deployment images: you can create deployment agents which are docker container that handle the lifecycle of your application.
You can deploy/undeploy/scale your application thanks to the agents.

  ```bash
  # Create agent (docker container) from deployment image and run install workflow
  deployments run myDeployment
  # Show logs of the deployment agent
  agents log myDeployment
  # Scale myNode to a new instance count of 5
  agents scale myDeployment -n myNode -c 5
  # Undeploy the application
  agents undeploy myDeployment
  # Delete the agent
  agents delete myDeployment
  ```
  
* Bootstrap: The managerless configuration is in general suitable only for developing and testing recipe.
You can bootstrap with Tosca Runtime a distant manager on a cloud provider (only Openstack is available for the moment).
The manager here is in fact a stateless proxy which dispatch the CLI's request to different deployments agents or to the docker daemon.

  ```bash
  # To bootstrap a single machine with a simple docker daemon and a proxy
  bootstrap -p openstack
  # To bootstrap a swarm cluster
  bootstrap -p openstack -t swarm
  ```
* Target the new bootstrapped daemon: By default when you start to use Tosca Runtime CLI, it targets the local daemon of your machine.
After the bootstrap operation, you can point the CLI to the new daemon by doing:
 
  ```bash
  # To bootstrap a single machine with a simple docker daemon and a proxy
  use -u NEW_DAEMON_URL
  # To reset to the default local docker daemon configuration
  use-default
  ```
* After that you can begin to work with this bootstrapped daemon in tout transparency as if you are working in local configuration.