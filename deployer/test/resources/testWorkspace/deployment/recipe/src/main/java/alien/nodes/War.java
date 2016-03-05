
package alien.nodes;

public class War extends alien.nodes.LoadBalancedWebApplication {

public War() {
super();


  attributeDefinitions.put("application_url", () -> (

evaluateCompositeFunction("concat",

  "http://" ,

  
    
evaluateFunction("get_attribute","HOST","public_ip_address")
   ,

  ":" ,

  
    
evaluateFunction("get_property","HOST","tomcat_port")
   ,

  "/" ,

  
    
evaluateFunction("get_property","SELF","context_path")
   
)));

  attributeDefinitions.put("local_application_url", () -> (

evaluateCompositeFunction("concat",

  "http://" ,

  
    
evaluateFunction("get_attribute","HOST","ip_address")
   ,

  ":" ,

  
    
evaluateFunction("get_property","HOST","tomcat_port")
   ,

  "/" ,

  
    
evaluateFunction("get_property","SELF","context_path")
   
)));





  deploymentArtifacts.put("war_file", "tomcat-war-types/warFiles/helloWorld.war");


  
java.util.Map<String, com.toscaruntime.sdk.model.OperationInputDefinition> inputs_custom_update_war_file = new java.util.HashMap<>();

  inputs_custom_update_war_file.put("CONTEXT_PATH", () -> (

evaluateFunction("get_property","SELF","context_path")));

  inputs_custom_update_war_file.put("TOMCAT_HOME", () -> (

evaluateFunction("get_property","HOST","tomcat_home")));

  inputs_custom_update_war_file.put("TOMCAT_PORT", () -> (

evaluateFunction("get_property","HOST","tomcat_port")));

  operationInputs.put("custom_update_war_file", inputs_custom_update_war_file);


}


public void customUpdateWarFile () {
  
  operationOutputs.put("custom_update_war_file", executeOperation("custom_update_war_file", "tomcat-war-types/scripts/tomcat_install_war.sh"));
  
  }

}