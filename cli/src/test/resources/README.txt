Getting started:

1/ Install Alien CSARS:

csars install /Users/vuminhkh/Projects/samples/apache/
csars install /Users/vuminhkh/Projects/samples/mysql/
csars install /Users/vuminhkh/Projects/samples/php/
csars install /Users/vuminhkh/Projects/samples/wordpress/
csars install /Users/vuminhkh/Projects/samples/topology-wordpress/

2/ Install specific docker topology:

csars install /Users/vuminhkh/Projects/tosca-runtime/compiler/src/test/resources/csars/topologyWordpressDocker/

3/ List installed csars:

csars list

4/ Create deployment:

deployments create wordpress -c wordpress-template-docker:*

5/Run deployment:

deployments run wordpress

6/See logs:

agents log wordpress

7/Check deployment status:

agents info wordpress

8/ Apache load balancer samples:

csars install /Users/vuminhkh/Projects/alien4cloud-extended-types/alien-base-types-1.0-SNAPSHOT/
csars install /Users/vuminhkh/Projects/alien4cloud-extended-types/alien-extended-storage-types-1.0-SNAPSHOT/
csars install /Users/vuminhkh/Projects/samples/apache-load-balancer/
csars install /Users/vuminhkh/Projects/samples/tomcat-war/
csars install /Users/vuminhkh/Projects/samples/topology-load-balancer-tomcat/
csars install /Users/vuminhkh/Projects/tosca-runtime/compiler/src/test/resources/csars/topologyApacheLoadBalancerDocker/
deployments create tomcat -c apache-load-balancer-template-docker:*
deployments run tomcat
agents log tomcat

To clean up:

agents undeploy tomcat
agents delete tomcat
deployments delete tomcat

9/ Demo lifecycle:
csars install /Users/vuminhkh/Projects/samples/apache/
csars install /Users/vuminhkh/Projects/samples/php/
csars install /Users/vuminhkh/Projects/samples/demo-lifecycle/
csars install /Users/vuminhkh/Projects/tosca-runtime/compiler/src/test/resources/csars/topologyDemoLifeCycleDocker
deployments create demo-lifecycle -c demo-lifecyle-template-docker:*
deployments run demo-lifecycle
agents log demo-lifecycle

10/ Install Alien