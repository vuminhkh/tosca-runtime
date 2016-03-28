
package alien.nodes;

public class Java extends tosca.nodes.SoftwareComponent {

public Java() {
super();


  attributeDefinitions.put("java_version", () -> (

evaluateFunction("get_operation_output","SELF","Standard","create","JAVA_VERSION")));

  attributeDefinitions.put("java_message", () -> (

evaluateCompositeFunction("concat",

  "Java help: " ,

  
    
evaluateFunction("get_operation_output","SELF","Standard","create","JAVA_HELP")
   
)));






  
java.util.Map<String, com.toscaruntime.sdk.model.OperationInputDefinition> inputs_create = new java.util.HashMap<>();

  inputs_create.put("JAVA_URL", () -> (

evaluateFunction("get_property","SELF","java_url")));

  inputs_create.put("JAVA_HOME", () -> (

evaluateFunction("get_property","SELF","java_home")));

  operationInputs.put("create", inputs_create);


}


public void create () {
  
    java.util.Map<String, String> outputs = executeOperation("create", "tomcat-war-types/scripts/java_install.sh");
    setOperationOutputs("Standard", "create", outputs);
  
  }

}