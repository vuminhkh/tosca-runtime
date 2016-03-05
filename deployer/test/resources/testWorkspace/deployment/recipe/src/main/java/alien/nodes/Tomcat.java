
package alien.nodes;

public class Tomcat extends tosca.nodes.WebServer {

public Tomcat() {
super();


  attributeDefinitions.put("server_url", () -> (

evaluateCompositeFunction("concat",

  "http://" ,

  
    
evaluateFunction("get_attribute","HOST","public_ip_address")
   ,

  ":" ,

  
    
evaluateFunction("get_property","SELF","tomcat_port")
   
)));






  
java.util.Map<String, com.toscaruntime.sdk.model.OperationInputDefinition> inputs_create = new java.util.HashMap<>();

  inputs_create.put("TOMCAT_HOME", () -> (

evaluateFunction("get_property","SELF","tomcat_home")));

  inputs_create.put("TOMCAT_PORT", () -> (

evaluateFunction("get_property","SELF","tomcat_port")));

  inputs_create.put("TOMCAT_URL", () -> (

evaluateFunction("get_property","SELF","tomcat_url")));

  operationInputs.put("create", inputs_create);


  
java.util.Map<String, com.toscaruntime.sdk.model.OperationInputDefinition> inputs_start = new java.util.HashMap<>();

  inputs_start.put("TOMCAT_HOME", () -> (

evaluateFunction("get_property","SELF","tomcat_home")));

  inputs_start.put("TOMCAT_PORT", () -> (

evaluateFunction("get_property","SELF","tomcat_port")));

  operationInputs.put("start", inputs_start);


  
java.util.Map<String, com.toscaruntime.sdk.model.OperationInputDefinition> inputs_stop = new java.util.HashMap<>();

  inputs_stop.put("TOMCAT_HOME", () -> (

evaluateFunction("get_property","SELF","tomcat_home")));

  operationInputs.put("stop", inputs_stop);


}


public void create () {
  
  operationOutputs.put("create", executeOperation("create", "tomcat-war-types/scripts/tomcat_install.sh"));
  
  }

public void start () {
  
  operationOutputs.put("start", executeOperation("start", "tomcat-war-types/scripts/tomcat_start.sh"));
  
  }

public void stop () {
  
  operationOutputs.put("stop", executeOperation("stop", "tomcat-war-types/scripts/tomcat_stop.sh"));
  
  }

}