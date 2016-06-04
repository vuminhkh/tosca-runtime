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

Tosca Runtime's aim is to become a basic development kit for Tosca as much as a JDK for Java.

Getting Started
============================

### Pre-requisite

* Install Java 8
* Install docker 1.10.x (the versions below or above 1.10.x might have compatibility issues)

  - For Linux user:
  
    ```bash
    # Uninstall old version if necessary
    curl -sSL https://get.docker.com/ | sudo sh
    # Add your user to docker group, not necessary but convenient, or else only root can use docker
    sudo usermod -aG docker your-user
    # log out and back in for this to take effect, perform a little test to make sure that you can connect to the daemon with your user
    docker ps -a
    # Verify that you have the good version
    docker info
    # configure docker to expose http end point (For ex: in /etc/default/docker or /etc/systemd/system/docker.service.d/docker.conf)
    echo "DOCKER_OPTS=\"-H tcp://0.0.0.0:2376 -H unix:///var/run/docker.sock\"" | sudo tee /etc/default/docker
    # On some recent Ubuntu versions or CentOS :
    # echo -e "[Service]\n""EnvironmentFile=-/etc/default/docker\n""ExecStart=\n""ExecStart=/usr/bin/docker daemon \$DOCKER_OPTS -H fd://\n" | sudo tee -a /etc/systemd/system/docker.service.d/docker.conf
    # restart docker
    sudo service docker restart
    ```
    Please take note that, for linux, there are some [kernel bugs](https://github.com/docker/docker/issues/18180) and compatibilities issues with old kernel version.
  - For Mac OS user: https://docs.docker.com/engine/installation/mac/
  
  - For Windows user: https://docs.docker.com/engine/installation/windows/
  
  Note that for Mac OS and Windows, if you've already had the docker toolbox installed but with older version, you can just upgrade your daemon's version
  
  ```bash
  docker-machine upgrade default
  ```

* Download [tosca-runtime](https://fastconnect.org/owncloud/public.php?service=files&t=b50fcd56e52724b25774e30a4c3ffbd7&download), uncompress

### Quick getting started

  ```bash
  # Let's say you are at pathToProjects
  # get some Alien samples
  git clone https://github.com/alien4cloud/alien4cloud-extended-types.git
  git clone https://github.com/alien4cloud/samples.git
  # get toscaruntime sources as it contains some samples for testing
  git clone https://github.com/vuminhkh/tosca-runtime.git
  # launch tosca runtime
  cd path_to_toscaruntime
  ./tosca-runtime.sh
  # From here inside tosca runtime shell
  # show pre-installed csars
  csars list
  # Install some types necessary to deploy a topology apache load balancer
  csars install pathToProjects/alien4cloud-extended-types/alien-base-types/
  csars install pathToProjects/samples/apache-load-balancer/
  csars install pathToProjects/samples/tomcat-war/
  csars install pathToProjects/samples/topology-load-balancer-tomcat/
  # First deployment may take some minutes as it pulls base image from toscaruntime docker hub, next deployments will be much more rapid
  # Create a deployment image
  deployments create aplb pathToProjects/tosca-runtime/test/src/it/resources/csars/docker/standalone/apache-lb/
  # Create agent to deploy
  agents create apache-lb
  ```
* Inside tosca runtime shell, perform `help` command to have more commands and options

### Build Tosca Runtime

* Install [Maven](https://maven.apache.org/install.html), [SBT](http://www.scala-sbt.org/0.13/docs/Setup.html)
* Clone and build docker-java (temporary for the moment as some modifications are not yet merged in the official release):
  
  ```bash
    git clone https://github.com/vuminhkh/docker-java.git
    cd docker-java/
    mvn clean install -DskipTests
  ```
* Clone and build tosca-runtime

  ```bash
    git clone https://github.com/vuminhkh/tosca-runtime.git
    cd tosca-runtime/
    sbt
    # Inside sbt CLI, build and package
    > dist
  ```

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
 
Basically, ToscaRuntime needs a docker daemon (can be swarm daemon) in order to manage deployment images and instantiate micro managers (agents), let’s call it the manager daemon.
Then the micro manager (agent) might target a particular IAAS (for ex Openstack), or a docker daemon (can be swarm), to instantiate application containers or VMs.
In some cases, the manager daemon and the target docker daemon for application containers can be the same. Below you have some examples of available configurations

![alt text](https://github.com/vuminhkh/tosca-runtime/raw/master/src/common/images/LocalhostOnly.jpg "Localhost only")
![alt text](https://github.com/vuminhkh/tosca-runtime/raw/master/src/common/images/SwarmCluster.jpg "Swarm Cluster")
![alt text](https://github.com/vuminhkh/tosca-runtime/raw/master/src/common/images/IAAS.jpg "IAAS")

The common workflow to use Tosca Runtime is:

* Create Deployment Image: 
From installed types and topology archives, Tosca Runtime allows you to create docker images that can be used to deploy your topology.
All necessary information are packaged into this image which make the deployment reproducible and can be shared with other people and other team.

  Usage:
  ```bash
  # To create the deployment from an installed archive
  deployments create my_deployment path_to_my_deployment
  # To list created deployments
  deployments list
  ```

* Create deployment agent (micro manager) from deployment images: you can create deployment agents which are docker container that handle the lifecycle of your application.
You can deploy/undeploy/scale your application thanks to the agents.

  ```bash
  # Create agent (docker container) from deployment image and run install workflow
  agents create myDeployment
  # Show logs of the deployment agent
  agents log myDeployment
  # Scale myNode to a new instance count of 5
  agents scale myDeployment myNode 2
  # Undeploy the application
  agents undeploy myDeployment
  # Delete the agent
  agents delete myDeployment
  ```
  
* Bootstrap: The localhost configuration is in general suitable only for developing and testing recipe.
You can bootstrap with Tosca Runtime a distant manager on a cloud provider (only Openstack is available for the moment).
The manager here is in fact a stateless proxy which dispatch the CLI's request to different deployments agents or to the docker daemon.
The bootstrap topology was only tested with Ubuntu Willy and kernel version 4.2.0-36-generic.

  ```bash
  # To bootstrap a single machine with a simple docker daemon and a proxy
  bootstrap --provider openstack
  # To bootstrap a swarm cluster
  bootstrap --provider openstack --target swarm
  # Note the output key 'public_daemon_url', this is the URL of the new docker daemon
  ```
* Target the new bootstrapped daemon: By default when you start to use Tosca Runtime CLI, it targets the local daemon of your machine.
After the bootstrap operation, you can point the CLI to the new daemon by doing:
 
  ```bash
  # To bootstrap a single machine with a simple docker daemon and a proxy
  use --url NEW_DAEMON_URL
  # To reset to the default local docker daemon configuration
  use-default
  ```
* After that you can begin to work with this bootstrapped daemon in all transparency as if you are working in local configuration.