
package alien.relationships;

  public class WarHostedOnTomcat extends tosca.relationships.HostedOn {

public WarHostedOnTomcat() {
super();







  
java.util.Map<String, com.toscaruntime.sdk.model.OperationInputDefinition> inputs_post_configure_source = new java.util.HashMap<>();

  inputs_post_configure_source.put("CONTEXT_PATH", () -> (

evaluateFunction("get_property","SOURCE","context_path")));

  inputs_post_configure_source.put("TOMCAT_HOME", () -> (

evaluateFunction("get_property","TARGET","tomcat_home")));

  inputs_post_configure_source.put("TOMCAT_PORT", () -> (

evaluateFunction("get_property","TARGET","tomcat_port")));

  operationInputs.put("post_configure_source", inputs_post_configure_source);


}


public void postConfigureSource () {
  
    java.util.Map<String, String> outputs = executeOperation("post_configure_source", "tomcat-war-types/scripts/tomcat_install_war.sh");
    setOperationOutputs("Configure", "post_configure_source", outputs);
  
  }

}