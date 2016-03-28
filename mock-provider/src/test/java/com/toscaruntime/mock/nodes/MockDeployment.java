package com.toscaruntime.mock.nodes;

import java.util.HashMap;
import java.util.Map;

import com.toscaruntime.mock.relationships.MockHostedOnRelationship;
import com.toscaruntime.mock.relationships.MockRelationship;
import com.toscaruntime.util.PropertyUtil;

import tosca.relationships.AttachTo;
import tosca.relationships.Network;

public class MockDeployment extends com.toscaruntime.sdk.Deployment {

    @Override
    protected void addNodes() {
        Map<String, Object> properties_WebServer = new HashMap<>();
        properties_WebServer.put("interactive", "true");
        properties_WebServer.put("tag", "latest");
        properties_WebServer.put("image_id", "toscaruntime/ubuntu-trusty");

        Map<String, Map<String, Object>> capabilities_properties_WebServer = new HashMap<>();
        Map<String, Object> properties_WebServer_capability_scalable = new HashMap<>();
        properties_WebServer_capability_scalable.put("default_instances", "2");
        properties_WebServer_capability_scalable.put("max_instances", "3");
        capabilities_properties_WebServer.put("scalable", properties_WebServer_capability_scalable);
        addNode("WebServer", MockCompute.class, null, null, properties_WebServer, capabilities_properties_WebServer);

        Map<String, Object> properties_Java = new HashMap<>();
        properties_Java.put("java_url", "http://download.oracle.com/otn-pub/java/jdk/7u75-b13/jdk-7u75-linux-x64.tar.gz");
        properties_Java.put("java_home", "/opt/java");
        Map<String, Map<String, Object>> capabilities_properties_Java = new HashMap<>();
        addNode("Java", MockSoftware.class, "WebServer", "WebServer", properties_Java, capabilities_properties_Java);

        Map<String, Object> properties_Tomcat = new HashMap<>();
        properties_Tomcat.put("tomcat_home", "/opt/tomcat");
        properties_Tomcat.put("tomcat_port", "80");
        properties_Tomcat.put("tomcat_url", "http://mirrors.ircam.fr/pub/apache/tomcat/tomcat-8/v8.0.29/bin/apache-tomcat-8.0.29.tar.gz");
        Map<String, Map<String, Object>> capabilities_properties_Tomcat = new HashMap<>();
        addNode("Tomcat", MockSoftware.class, "WebServer", "WebServer", properties_Tomcat, capabilities_properties_Tomcat);

        Map<String, Object> properties_War = new HashMap<>();
        properties_War.put("context_path", "war");
        Map<String, Map<String, Object>> capabilities_properties_War = new HashMap<>();
        addNode("War", MockSoftware.class, "Tomcat", "Tomcat", properties_War, capabilities_properties_War);

        Map<String, Object> properties_LoadBalancerServer = new HashMap<>();
        properties_LoadBalancerServer.put("exposed_ports", PropertyUtil.toList("[{\"port\" : \"80\"}]"));
        properties_LoadBalancerServer.put("image_id", "toscaruntime/ubuntu-trusty");
        properties_LoadBalancerServer.put("tag", "latest");
        properties_LoadBalancerServer.put("interactive", "true");
        properties_LoadBalancerServer.put("port_mappings", PropertyUtil.toList("[{\"from\" : \"80\", \"to\" : \"51000\"}]"));
        Map<String, Map<String, Object>> capabilities_properties_LoadBalancerServer = new HashMap<>();
        Map<String, Object> properties_LoadBalancerServer_capability_scalable = new HashMap<>();
        properties_LoadBalancerServer_capability_scalable.put("max_instances", "2");
        capabilities_properties_LoadBalancerServer.put("scalable", properties_LoadBalancerServer_capability_scalable);
        addNode("LoadBalancerServer", MockCompute.class, null, null, properties_LoadBalancerServer, capabilities_properties_LoadBalancerServer);

        Map<String, Object> properties_ApacheLoadBalancer = new HashMap<>();
        properties_ApacheLoadBalancer.put("port", "80");
        Map<String, Map<String, Object>> capabilities_properties_ApacheLoadBalancer = new HashMap<>();
        addNode("ApacheLoadBalancer", MockSoftware.class, "LoadBalancerServer", "LoadBalancerServer", properties_ApacheLoadBalancer, capabilities_properties_ApacheLoadBalancer);

        Map<String, Object> properties_Internet = new HashMap<>();
        properties_Internet.put("ip_version", "4");
        properties_Internet.put("network_name", "tomcatNet");
        properties_Internet.put("cidr", "10.67.79.0/24");
        Map<String, Map<String, Object>> capabilities_properties_Internet = new HashMap<>();
        addNode("Internet", MockNetwork.class, null, null, properties_Internet, capabilities_properties_Internet);

        addNode("Volume", MockVolume.class, "LoadBalancerServer", null, new HashMap<>(), new HashMap<>());
    }

    @Override
    protected void postInitializeConfig() {
        this.config.setTopologyResourcePath(this.config.getArtifactsPath().resolve("apache-load-balancer-template-docker"));
    }

    @Override
    protected void addRelationships() {

        Map<String, Object> properties_rel_LoadBalancerServer_Internet = new HashMap<>();
        addRelationship("LoadBalancerServer", "Internet", properties_rel_LoadBalancerServer_Internet, Network.class);

        Map<String, Object> properties_rel_War_Tomcat = new HashMap<>();
        addRelationship("War", "Tomcat", properties_rel_War_Tomcat, MockHostedOnRelationship.class);

        Map<String, Object> properties_rel_War_ApacheLoadBalancer = new HashMap<>();
        addRelationship("War", "ApacheLoadBalancer", properties_rel_War_ApacheLoadBalancer, MockRelationship.class);

        Map<String, Object> properties_rel_ApacheLoadBalancer_LoadBalancerServer = new HashMap<>();
        addRelationship("ApacheLoadBalancer", "LoadBalancerServer", properties_rel_ApacheLoadBalancer_LoadBalancerServer, MockHostedOnRelationship.class);

        Map<String, Object> properties_rel_Java_WebServer = new HashMap<>();
        addRelationship("Java", "WebServer", properties_rel_Java_WebServer, MockHostedOnRelationship.class);

        Map<String, Object> properties_rel_WebServer_Internet = new HashMap<>();
        addRelationship("WebServer", "Internet", properties_rel_WebServer_Internet, Network.class);

        Map<String, Object> properties_rel_Tomcat_Java = new HashMap<>();
        addRelationship("Tomcat", "Java", properties_rel_Tomcat_Java, MockRelationship.class);

        Map<String, Object> properties_rel_Tomcat_WebServer = new HashMap<>();
        addRelationship("Tomcat", "WebServer", properties_rel_Tomcat_WebServer, MockHostedOnRelationship.class);

        addRelationship("Volume", "LoadBalancerServer", new HashMap<>(), AttachTo.class);
    }

    public Map<String, Object> getOutputs() {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("load_balancer_url", evaluateCompositeFunction("concat", "http://", evaluateFunction("get_attribute", "LoadBalancerServer", "public_ip_address"), ":", evaluateFunction("get_property", "LoadBalancerServer", "port_mappings[0].to")));
        return outputs;
    }
}