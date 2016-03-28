





public class Deployment extends com.toscaruntime.sdk.Deployment{

public void addNodes() {
  
  

  
java.util.Map<String, Object> properties_WebServer = new java.util.HashMap<>();

  properties_WebServer.put("interactive", 
"true");

  properties_WebServer.put("tag", 
"latest");

  properties_WebServer.put("image_id", 
"toscaruntime/ubuntu-trusty");



  java.util.Map<String, java.util.Map<String, Object>> capabilities_properties_WebServer = new java.util.HashMap<>();
  

java.util.Map<String, Object> properties_WebServer_capability_scalable = new java.util.HashMap<>();

  properties_WebServer_capability_scalable.put("max_instances", 
"3");

  properties_WebServer_capability_scalable.put("min_instances", 
"1");

  properties_WebServer_capability_scalable.put("default_instances", 
"1");

capabilities_properties_WebServer.put("scalable", properties_WebServer_capability_scalable);



addNode("WebServer", com.toscaruntime.mock.nodes.MockCompute.class, null, null, properties_WebServer, capabilities_properties_WebServer);
  
    

  
java.util.Map<String, Object> properties_Java = new java.util.HashMap<>();

  properties_Java.put("java_url", 
"http://download.oracle.com/otn-pub/java/jdk/7u75-b13/jdk-7u75-linux-x64.tar.gz");

  properties_Java.put("java_home", 
"/opt/java");




addNode("Java", alien.nodes.Java.class, "WebServer", "WebServer", properties_Java, new java.util.HashMap<>());
  

  
    

  
java.util.Map<String, Object> properties_Tomcat = new java.util.HashMap<>();

  properties_Tomcat.put("tomcat_home", 
"/opt/tomcat");

  properties_Tomcat.put("tomcat_port", 
"80");

  properties_Tomcat.put("tomcat_url", 
"http://mirrors.ircam.fr/pub/apache/tomcat/tomcat-8/v8.0.33/bin/apache-tomcat-8.0.33.tar.gz");




addNode("Tomcat", alien.nodes.Tomcat.class, "WebServer", "WebServer", properties_Tomcat, new java.util.HashMap<>());
  
    

  
java.util.Map<String, Object> properties_War = new java.util.HashMap<>();

  properties_War.put("context_path", 
"war");




addNode("War", alien.nodes.War.class, "Tomcat", "Tomcat", properties_War, new java.util.HashMap<>());
  

  

  


  

  
java.util.Map<String, Object> properties_LoadBalancerServer = new java.util.HashMap<>();

  properties_LoadBalancerServer.put("exposed_ports", 
com.toscaruntime.util.PropertyUtil.toList("[{\"port\" : \"80\"}]"));

  properties_LoadBalancerServer.put("image_id", 
"toscaruntime/ubuntu-trusty");

  properties_LoadBalancerServer.put("tag", 
"latest");

  properties_LoadBalancerServer.put("interactive", 
"true");

  properties_LoadBalancerServer.put("port_mappings", 
com.toscaruntime.util.PropertyUtil.toList("[{\"from\" : \"80\", \"to\" : \"51000\"}]"));




addNode("LoadBalancerServer", com.toscaruntime.mock.nodes.MockCompute.class, null, null, properties_LoadBalancerServer, new java.util.HashMap<>());
  
    

  
java.util.Map<String, Object> properties_ApacheLoadBalancer = new java.util.HashMap<>();

  properties_ApacheLoadBalancer.put("port", 
"80");




addNode("ApacheLoadBalancer", alien.nodes.ApacheLoadBalancer.class, "LoadBalancerServer", "LoadBalancerServer", properties_ApacheLoadBalancer, new java.util.HashMap<>());
  

  


  

  
java.util.Map<String, Object> properties_Internet = new java.util.HashMap<>();

  properties_Internet.put("ip_version", 
"4");

  properties_Internet.put("network_name", 
"tomcatNet");

  properties_Internet.put("cidr", 
"10.67.79.0/24");




addNode("Internet", com.toscaruntime.mock.nodes.MockNetwork.class, null, null, properties_Internet, new java.util.HashMap<>());
  


  }

public void postInitializeConfig() {
  this.config.setTopologyResourcePath(this.config.getArtifactsPath().resolve("apache-load-balancer-template-docker"));

}

  public void addRelationships() {

  
java.util.Map<String, Object> properties_rel_LoadBalancerServer_Internet = new java.util.HashMap<>();

  addRelationship("LoadBalancerServer", "Internet", properties_rel_LoadBalancerServer_Internet, tosca.relationships.Network.class);

  
java.util.Map<String, Object> properties_rel_War_Tomcat = new java.util.HashMap<>();

  addRelationship("War", "Tomcat", properties_rel_War_Tomcat, alien.relationships.WarHostedOnTomcat.class);

  
java.util.Map<String, Object> properties_rel_War_ApacheLoadBalancer = new java.util.HashMap<>();

  addRelationship("War", "ApacheLoadBalancer", properties_rel_War_ApacheLoadBalancer, alien.relationships.WebApplicationConnectsToApacheLoadBalancer.class);

  
java.util.Map<String, Object> properties_rel_ApacheLoadBalancer_LoadBalancerServer = new java.util.HashMap<>();

  addRelationship("ApacheLoadBalancer", "LoadBalancerServer", properties_rel_ApacheLoadBalancer_LoadBalancerServer, tosca.relationships.HostedOn.class);

  
java.util.Map<String, Object> properties_rel_Java_WebServer = new java.util.HashMap<>();

  addRelationship("Java", "WebServer", properties_rel_Java_WebServer, tosca.relationships.HostedOn.class);

  
java.util.Map<String, Object> properties_rel_WebServer_Internet = new java.util.HashMap<>();

  addRelationship("WebServer", "Internet", properties_rel_WebServer_Internet, tosca.relationships.Network.class);

  
java.util.Map<String, Object> properties_rel_Tomcat_Java = new java.util.HashMap<>();

  addRelationship("Tomcat", "Java", properties_rel_Tomcat_Java, alien.relationships.RunOnJVM.class);

  
java.util.Map<String, Object> properties_rel_Tomcat_WebServer = new java.util.HashMap<>();

  addRelationship("Tomcat", "WebServer", properties_rel_Tomcat_WebServer, tosca.relationships.HostedOn.class);

}


  public java.util.Map<String, Object> getOutputs() {
  java.util.Map<String, Object> outputs = new java.util.HashMap<>();
  
    outputs.put("load_balancer_url", 

evaluateCompositeFunction("concat",

  "http://" ,

  
    
evaluateFunction("get_attribute","LoadBalancerServer","public_ip_address")
   ,

  ":" ,

  
    
evaluateFunction("get_property","LoadBalancerServer","port_mappings[0].to")
   
));
  
  return outputs;
  }

}