
package alien.relationships;

public class WebApplicationConnectsToApacheLoadBalancer extends alien.relationships.WebApplicationConnectsToLoadBalancer {

    public WebApplicationConnectsToApacheLoadBalancer() {
        super();


        java.util.Map<String, com.toscaruntime.sdk.model.OperationInputDefinition> inputs_add_source = new java.util.HashMap<>();

        inputs_add_source.put("WEB_APPLICATION_URL", () -> (

                evaluateFunction("get_attribute", "SOURCE", "local_application_url")));

        operationInputs.put("add_source", inputs_add_source);


        java.util.Map<String, com.toscaruntime.sdk.model.OperationInputDefinition> inputs_remove_source = new java.util.HashMap<>();

        inputs_remove_source.put("WEB_APPLICATION_URL", () -> (

                evaluateFunction("get_attribute", "SOURCE", "local_application_url")));

        operationInputs.put("remove_source", inputs_remove_source);


    }


    public void addSource() {

        operationOutputs.put("add_source", executeOperation("add_source", "apache-load-balancer-type/scripts/add_web_app_to_load_balancer.sh"));
        setAttribute("att1", "test_att1");
        setAttribute("att2", "test_att2");
    }

    public void removeSource() {

        operationOutputs.put("remove_source", executeOperation("remove_source", "apache-load-balancer-type/scripts/remove_web_app_from_load_balancer.sh"));

    }

}