
package alien.nodes;

public class ApacheLoadBalancer extends alien.nodes.LoadBalancer {

public ApacheLoadBalancer() {
super();


  attributeDefinitions.put("load_balancer_url", () -> (

evaluateCompositeFunction("concat",

  "http://" ,

  
    
evaluateFunction("get_attribute","HOST","public_ip_address")
   ,

  ":" ,

  
    
evaluateFunction("get_property","SELF","port")
   ,

  "/" 
)));






  
java.util.Map<String, com.toscaruntime.sdk.model.OperationInputDefinition> inputs_create = new java.util.HashMap<>();

  inputs_create.put("PORT", () -> (

evaluateFunction("get_property","SELF","port")));

  operationInputs.put("create", inputs_create);


  
java.util.Map<String, com.toscaruntime.sdk.model.OperationInputDefinition> inputs_start = new java.util.HashMap<>();

  operationInputs.put("start", inputs_start);


  
java.util.Map<String, com.toscaruntime.sdk.model.OperationInputDefinition> inputs_stop = new java.util.HashMap<>();

  operationInputs.put("stop", inputs_stop);


}


public void create () {
  
    java.util.Map<String, String> outputs = executeOperation("create", "apache-load-balancer-type/scripts/install_apache_load_balancer.sh");
    setOperationOutputs("Standard", "create", outputs);
  
  }

public void start () {
  
    java.util.Map<String, String> outputs = executeOperation("start", "apache-load-balancer-type/scripts/start_apache_load_balancer.sh");
    setOperationOutputs("Standard", "start", outputs);
  
  }

public void stop () {
  
    java.util.Map<String, String> outputs = executeOperation("stop", "apache-load-balancer-type/scripts/stop_apache_load_balancer.sh");
    setOperationOutputs("Standard", "stop", outputs);
  
  }

}