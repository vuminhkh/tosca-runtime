







  

public class Deployment extends com.toscaruntime.sdk.Deployment{

public void initializeNodes() {
  
  


  java.util.Map<String, java.util.Map<String, Object>> capabilities_properties_WebServer = new java.util.HashMap<>();
  

java.util.Map<String, Object> properties_WebServer_capability_scalable = new java.util.HashMap<>();

  properties_WebServer_capability_scalable.put("max_instances", 
"3");

  properties_WebServer_capability_scalable.put("min_instances", 
"1");

  properties_WebServer_capability_scalable.put("default_instances", 
"1");

capabilities_properties_WebServer.put("scalable", properties_WebServer_capability_scalable);



initializeNode("WebServer", com.toscaruntime.mock.nodes.MockCompute.class, null, null, new java.util.HashMap<>(), capabilities_properties_WebServer);
  
    

  
java.util.Map<String, Object> properties_Java = new java.util.HashMap<>();

  properties_Java.put("java_url", 
"http://download.oracle.com/otn-pub/java/jdk/7u75-b13/jdk-7u75-linux-x64.tar.gz");

  properties_Java.put("java_home", 
"/opt/java");




initializeNode("Java", alien.nodes.Java.class, "WebServer", "WebServer", properties_Java, new java.util.HashMap<>());
  

  
    

  
java.util.Map<String, Object> properties_Tomcat = new java.util.HashMap<>();

  properties_Tomcat.put("tomcat_home", 
"/opt/tomcat");

  properties_Tomcat.put("tomcat_port", 
"80");

  properties_Tomcat.put("tomcat_url", 
"http://mirrors.ircam.fr/pub/apache/tomcat/tomcat-8/v8.0.32/bin/apache-tomcat-8.0.32.tar.gz");




initializeNode("Tomcat", alien.nodes.Tomcat.class, "WebServer", "WebServer", properties_Tomcat, new java.util.HashMap<>());
  
    

  
java.util.Map<String, Object> properties_War = new java.util.HashMap<>();

  properties_War.put("context_path", 
"war");




initializeNode("War", alien.nodes.War.class, "Tomcat", "Tomcat", properties_War, new java.util.HashMap<>());
  

  

  


  



initializeNode("LoadBalancerServer", com.toscaruntime.mock.nodes.MockCompute.class, null, null, new java.util.HashMap<>(), new java.util.HashMap<>());
  
    

  
java.util.Map<String, Object> properties_ApacheLoadBalancer = new java.util.HashMap<>();

  properties_ApacheLoadBalancer.put("port", 
"80");




initializeNode("ApacheLoadBalancer", alien.nodes.ApacheLoadBalancer.class, "LoadBalancerServer", "LoadBalancerServer", properties_ApacheLoadBalancer, new java.util.HashMap<>());
  

  


  

  
java.util.Map<String, Object> properties_Internet = new java.util.HashMap<>();

  properties_Internet.put("ip_version", 
"4");




initializeNode("Internet", com.toscaruntime.mock.nodes.MockNetwork.class, null, null, properties_Internet, new java.util.HashMap<>());
  



  
  
    setDependencies( "War",  "ApacheLoadBalancer"
    );
  

  
    setDependencies( "WebServer",  "Internet"
    );
  

  
    setDependencies( "LoadBalancerServer",  "Internet"
    );
  

  

  

  
    setDependencies( "Tomcat",  "Java"
    );
  

  

  }

public void postInitializeConfig() {
  this.config.setTopologyResourcePath(this.config.getArtifactsPath().resolve("apache-load-balancer-template-docker"));

}

  public void initializeRelationships() {

  
java.util.Map<String, Object> properties_rel_LoadBalancerServer_Internet = new java.util.HashMap<>();

  generateRelationships("LoadBalancerServer", "Internet", properties_rel_LoadBalancerServer_Internet, tosca.relationships.Network.class);

  
java.util.Map<String, Object> properties_rel_War_Tomcat = new java.util.HashMap<>();

  generateRelationships("War", "Tomcat", properties_rel_War_Tomcat, alien.relationships.WarHostedOnTomcat.class);

  
java.util.Map<String, Object> properties_rel_War_ApacheLoadBalancer = new java.util.HashMap<>();

  generateRelationships("War", "ApacheLoadBalancer", properties_rel_War_ApacheLoadBalancer, alien.relationships.WebApplicationConnectsToApacheLoadBalancer.class);

  
java.util.Map<String, Object> properties_rel_ApacheLoadBalancer_LoadBalancerServer = new java.util.HashMap<>();

  generateRelationships("ApacheLoadBalancer", "LoadBalancerServer", properties_rel_ApacheLoadBalancer_LoadBalancerServer, tosca.relationships.HostedOn.class);

  
java.util.Map<String, Object> properties_rel_Java_WebServer = new java.util.HashMap<>();

  generateRelationships("Java", "WebServer", properties_rel_Java_WebServer, tosca.relationships.HostedOn.class);

  
java.util.Map<String, Object> properties_rel_WebServer_Internet = new java.util.HashMap<>();

  generateRelationships("WebServer", "Internet", properties_rel_WebServer_Internet, tosca.relationships.Network.class);

  
java.util.Map<String, Object> properties_rel_Tomcat_Java = new java.util.HashMap<>();

  generateRelationships("Tomcat", "Java", properties_rel_Tomcat_Java, alien.relationships.RunOnJVM.class);

  
java.util.Map<String, Object> properties_rel_Tomcat_WebServer = new java.util.HashMap<>();

  generateRelationships("Tomcat", "WebServer", properties_rel_Tomcat_WebServer, tosca.relationships.HostedOn.class);

}

  public void initializeRelationshipInstances() {

  generateRelationshipInstances("LoadBalancerServer", "Internet", tosca.relationships.Network.class);

  generateRelationshipInstances("War", "Tomcat", alien.relationships.WarHostedOnTomcat.class);

  generateRelationshipInstances("War", "ApacheLoadBalancer", alien.relationships.WebApplicationConnectsToApacheLoadBalancer.class);

  generateRelationshipInstances("ApacheLoadBalancer", "LoadBalancerServer", tosca.relationships.HostedOn.class);

  generateRelationshipInstances("Java", "WebServer", tosca.relationships.HostedOn.class);

  generateRelationshipInstances("WebServer", "Internet", tosca.relationships.Network.class);

  generateRelationshipInstances("Tomcat", "Java", alien.relationships.RunOnJVM.class);

  generateRelationshipInstances("Tomcat", "WebServer", tosca.relationships.HostedOn.class);

}

  public void initializeInstances() {

  
  int WebServerInstancesCount = this.nodes.get("WebServer").getInstancesCount();
  for (int  WebServerIndex = 1;  WebServerIndex <= WebServerInstancesCount;  WebServerIndex++) {
  
com.toscaruntime.mock.nodes.MockCompute WebServer = new com.toscaruntime.mock.nodes.MockCompute ();
initializeInstance(WebServer, "WebServer", WebServerIndex, null, null);

  
  int JavaInstancesCount = this.nodes.get("Java").getInstancesCount();
  for (int  JavaIndex = 1;  JavaIndex <= JavaInstancesCount;  JavaIndex++) {
  
alien.nodes.Java Java = new alien.nodes.Java ();
initializeInstance(Java, "Java", JavaIndex, WebServer, WebServer);


  }


  
  int TomcatInstancesCount = this.nodes.get("Tomcat").getInstancesCount();
  for (int  TomcatIndex = 1;  TomcatIndex <= TomcatInstancesCount;  TomcatIndex++) {
  
alien.nodes.Tomcat Tomcat = new alien.nodes.Tomcat ();
initializeInstance(Tomcat, "Tomcat", TomcatIndex, WebServer, WebServer);

  
  int WarInstancesCount = this.nodes.get("War").getInstancesCount();
  for (int  WarIndex = 1;  WarIndex <= WarInstancesCount;  WarIndex++) {
  
alien.nodes.War War = new alien.nodes.War ();
initializeInstance(War, "War", WarIndex, Tomcat, Tomcat);


  }



  }



  }


  
  int LoadBalancerServerInstancesCount = this.nodes.get("LoadBalancerServer").getInstancesCount();
  for (int  LoadBalancerServerIndex = 1;  LoadBalancerServerIndex <= LoadBalancerServerInstancesCount;  LoadBalancerServerIndex++) {
  
com.toscaruntime.mock.nodes.MockCompute LoadBalancerServer = new com.toscaruntime.mock.nodes.MockCompute ();
initializeInstance(LoadBalancerServer, "LoadBalancerServer", LoadBalancerServerIndex, null, null);

  
  int ApacheLoadBalancerInstancesCount = this.nodes.get("ApacheLoadBalancer").getInstancesCount();
  for (int  ApacheLoadBalancerIndex = 1;  ApacheLoadBalancerIndex <= ApacheLoadBalancerInstancesCount;  ApacheLoadBalancerIndex++) {
  
alien.nodes.ApacheLoadBalancer ApacheLoadBalancer = new alien.nodes.ApacheLoadBalancer ();
initializeInstance(ApacheLoadBalancer, "ApacheLoadBalancer", ApacheLoadBalancerIndex, LoadBalancerServer, LoadBalancerServer);


  }



  }


  
  int InternetInstancesCount = this.nodes.get("Internet").getInstancesCount();
  for (int  InternetIndex = 1;  InternetIndex <= InternetInstancesCount;  InternetIndex++) {
  
com.toscaruntime.mock.nodes.MockNetwork Internet = new com.toscaruntime.mock.nodes.MockNetwork ();
initializeInstance(Internet, "Internet", InternetIndex, null, null);


  }


}


  public java.util.Map<String, Object> getOutputs() {
  java.util.Map<String, Object> outputs = new java.util.HashMap<>();
  
    outputs.put("load_balancer_url", 

evaluateFunction("get_attribute","ApacheLoadBalancer","load_balancer_url"));
  
  return outputs;
  }

}