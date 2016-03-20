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
    protected void initializeNodes() {
        Map<String, Object> properties_WebServer = new HashMap<>();
        properties_WebServer.put("interactive", "true");
        properties_WebServer.put("tag", "latest");
        properties_WebServer.put("image_id", "toscaruntime/ubuntu-trusty");

        Map<String, Map<String, Object>> capabilities_properties_WebServer = new HashMap<>();
        Map<String, Object> properties_WebServer_capability_scalable = new HashMap<>();
        properties_WebServer_capability_scalable.put("default_instances", "2");
        properties_WebServer_capability_scalable.put("max_instances", "3");
        capabilities_properties_WebServer.put("scalable", properties_WebServer_capability_scalable);
        initializeNode("WebServer", MockCompute.class, null, null, properties_WebServer, capabilities_properties_WebServer);

        Map<String, Object> properties_Java = new HashMap<>();
        properties_Java.put("java_url", "http://download.oracle.com/otn-pub/java/jdk/7u75-b13/jdk-7u75-linux-x64.tar.gz");
        properties_Java.put("java_home", "/opt/java");
        Map<String, Map<String, Object>> capabilities_properties_Java = new HashMap<>();
        initializeNode("Java", MockSoftware.class, "WebServer", "WebServer", properties_Java, capabilities_properties_Java);

        Map<String, Object> properties_Tomcat = new HashMap<>();
        properties_Tomcat.put("tomcat_home", "/opt/tomcat");
        properties_Tomcat.put("tomcat_port", "80");
        properties_Tomcat.put("tomcat_url", "http://mirrors.ircam.fr/pub/apache/tomcat/tomcat-8/v8.0.29/bin/apache-tomcat-8.0.29.tar.gz");
        Map<String, Map<String, Object>> capabilities_properties_Tomcat = new HashMap<>();
        initializeNode("Tomcat", MockSoftware.class, "WebServer", "WebServer", properties_Tomcat, capabilities_properties_Tomcat);

        Map<String, Object> properties_War = new HashMap<>();
        properties_War.put("context_path", "war");
        Map<String, Map<String, Object>> capabilities_properties_War = new HashMap<>();
        initializeNode("War", MockSoftware.class, "Tomcat", "Tomcat", properties_War, capabilities_properties_War);

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
        initializeNode("LoadBalancerServer", MockCompute.class, null, null, properties_LoadBalancerServer, capabilities_properties_LoadBalancerServer);

        Map<String, Object> properties_ApacheLoadBalancer = new HashMap<>();
        properties_ApacheLoadBalancer.put("port", "80");
        Map<String, Map<String, Object>> capabilities_properties_ApacheLoadBalancer = new HashMap<>();
        initializeNode("ApacheLoadBalancer", MockSoftware.class, "LoadBalancerServer", "LoadBalancerServer", properties_ApacheLoadBalancer, capabilities_properties_ApacheLoadBalancer);

        Map<String, Object> properties_Internet = new HashMap<>();
        properties_Internet.put("ip_version", "4");
        properties_Internet.put("network_name", "tomcatNet");
        properties_Internet.put("cidr", "10.67.79.0/24");
        Map<String, Map<String, Object>> capabilities_properties_Internet = new HashMap<>();
        initializeNode("Internet", MockNetwork.class, null, null, properties_Internet, capabilities_properties_Internet);

        initializeNode("Volume", MockVolume.class, "LoadBalancerServer", null, new HashMap<>(), new HashMap<>());

        setDependencies("War", "ApacheLoadBalancer");
        setDependencies("WebServer", "Internet");
        setDependencies("LoadBalancerServer", "Internet");
        setDependencies("Tomcat", "Java");
    }

    @Override
    protected void postInitializeConfig() {
        this.config.setTopologyResourcePath(this.config.getArtifactsPath().resolve("apache-load-balancer-template-docker"));
    }

    @Override
    protected void initializeRelationships() {

        Map<String, Object> properties_rel_LoadBalancerServer_Internet = new HashMap<>();
        generateRelationships("LoadBalancerServer", "Internet", properties_rel_LoadBalancerServer_Internet, Network.class);

        Map<String, Object> properties_rel_War_Tomcat = new HashMap<>();
        generateRelationships("War", "Tomcat", properties_rel_War_Tomcat, MockHostedOnRelationship.class);

        Map<String, Object> properties_rel_War_ApacheLoadBalancer = new HashMap<>();
        generateRelationships("War", "ApacheLoadBalancer", properties_rel_War_ApacheLoadBalancer, MockRelationship.class);

        Map<String, Object> properties_rel_ApacheLoadBalancer_LoadBalancerServer = new HashMap<>();
        generateRelationships("ApacheLoadBalancer", "LoadBalancerServer", properties_rel_ApacheLoadBalancer_LoadBalancerServer, MockHostedOnRelationship.class);

        Map<String, Object> properties_rel_Java_WebServer = new HashMap<>();
        generateRelationships("Java", "WebServer", properties_rel_Java_WebServer, MockHostedOnRelationship.class);

        Map<String, Object> properties_rel_WebServer_Internet = new HashMap<>();
        generateRelationships("WebServer", "Internet", properties_rel_WebServer_Internet, Network.class);

        Map<String, Object> properties_rel_Tomcat_Java = new HashMap<>();
        generateRelationships("Tomcat", "Java", properties_rel_Tomcat_Java, MockRelationship.class);

        Map<String, Object> properties_rel_Tomcat_WebServer = new HashMap<>();
        generateRelationships("Tomcat", "WebServer", properties_rel_Tomcat_WebServer, MockHostedOnRelationship.class);

        generateRelationships("Volume", "LoadBalancerServer", new HashMap<>(), AttachTo.class);
    }

    @Override
    protected void initializeRelationshipInstances() {
        generateRelationshipInstances("LoadBalancerServer", "Internet", Network.class);
        generateRelationshipInstances("War", "Tomcat", MockHostedOnRelationship.class);
        generateRelationshipInstances("War", "ApacheLoadBalancer", MockRelationship.class);
        generateRelationshipInstances("ApacheLoadBalancer", "LoadBalancerServer", MockHostedOnRelationship.class);
        generateRelationshipInstances("Java", "WebServer", MockHostedOnRelationship.class);
        generateRelationshipInstances("WebServer", "Internet", Network.class);
        generateRelationshipInstances("Tomcat", "Java", MockRelationship.class);
        generateRelationshipInstances("Tomcat", "WebServer", MockHostedOnRelationship.class);
        generateRelationshipInstances("Volume", "LoadBalancerServer", AttachTo.class);
    }

    @Override
    protected void initializeInstances() {
        int WebServerInstancesCount = this.nodes.get("WebServer").getInstancesCount();
        for (int WebServerIndex = 1; WebServerIndex <= WebServerInstancesCount; WebServerIndex++) {
            MockCompute WebServer = new MockCompute();
            initializeInstance(WebServer, "WebServer", WebServerIndex, null, null);

            int JavaInstancesCount = this.nodes.get("Java").getInstancesCount();
            for (int JavaIndex = 1; JavaIndex <= JavaInstancesCount; JavaIndex++) {
                MockSoftware Java = new MockSoftware();
                initializeInstance(Java, "Java", JavaIndex, WebServer, WebServer);
            }

            int TomcatInstancesCount = this.nodes.get("Tomcat").getInstancesCount();
            for (int TomcatIndex = 1; TomcatIndex <= TomcatInstancesCount; TomcatIndex++) {
                MockSoftware Tomcat = new MockSoftware();
                initializeInstance(Tomcat, "Tomcat", TomcatIndex, WebServer, WebServer);

                int WarInstancesCount = this.nodes.get("War").getInstancesCount();
                for (int WarIndex = 1; WarIndex <= WarInstancesCount; WarIndex++) {
                    MockSoftware War = new MockSoftware();
                    initializeInstance(War, "War", WarIndex, Tomcat, Tomcat);
                }
            }
        }

        int LoadBalancerServerInstancesCount = this.nodes.get("LoadBalancerServer").getInstancesCount();
        for (int LoadBalancerServerIndex = 1; LoadBalancerServerIndex <= LoadBalancerServerInstancesCount; LoadBalancerServerIndex++) {
            MockCompute LoadBalancerServer = new MockCompute();
            initializeInstance(LoadBalancerServer, "LoadBalancerServer", LoadBalancerServerIndex, null, null);

            int ApacheLoadBalancerInstancesCount = this.nodes.get("ApacheLoadBalancer").getInstancesCount();
            for (int ApacheLoadBalancerIndex = 1; ApacheLoadBalancerIndex <= ApacheLoadBalancerInstancesCount; ApacheLoadBalancerIndex++) {
                MockSoftware ApacheLoadBalancer = new MockSoftware();
                initializeInstance(ApacheLoadBalancer, "ApacheLoadBalancer", ApacheLoadBalancerIndex, LoadBalancerServer, LoadBalancerServer);
            }

            int VolumeCount = this.nodes.get("Volume").getInstancesCount();
            for (int VolumeIndex = 1; VolumeIndex <= VolumeCount; VolumeIndex++) {
                MockVolume Volume = new MockVolume();
                initializeInstance(Volume, "Volume", VolumeIndex, LoadBalancerServer, null);
            }
        }

        int InternetInstancesCount = this.nodes.get("Internet").getInstancesCount();
        for (int InternetIndex = 1; InternetIndex <= InternetInstancesCount; InternetIndex++) {
            MockNetwork Internet = new MockNetwork();
            initializeInstance(Internet, "Internet", InternetIndex, null, null);
        }
    }

    public Map<String, Object> getOutputs() {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("load_balancer_url", evaluateCompositeFunction("concat", "http://", evaluateFunction("get_attribute", "LoadBalancerServer", "public_ip_address"), ":", evaluateFunction("get_property", "LoadBalancerServer", "port_mappings[0].to")));
        return outputs;
    }
}