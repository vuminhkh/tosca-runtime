package com.toscaruntime.consul;

import com.google.common.net.HostAndPort;
import com.orbitz.consul.Consul;
import com.orbitz.consul.cache.ConsulCache;
import com.orbitz.consul.cache.ServiceHealthCache;
import com.orbitz.consul.cache.ServiceHealthKey;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.model.health.HealthCheck;
import com.orbitz.consul.model.health.ServiceHealth;
import com.toscaruntime.consul.util.ConsulURLUtil;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.PluginHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tosca.constants.InstanceState;
import tosca.nodes.Compute;
import tosca.nodes.Root;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.toscaruntime.constant.ToscaInterfaceConstant.NODE_STANDARD_INTERFACE;

public class ConsulPluginHook implements PluginHook {

    private static final Logger log = LoggerFactory.getLogger(ConsulPluginHook.class);

    private List<Consul> consuls;

    private Iterator<Consul> roundRobinConsulIterator;

    private Deployment deployment;

    private final Map<String, ServiceHealthCache> healthCacheMap = new HashMap<>();

    private final Map<String, ConsulCache.Listener<ServiceHealthKey, ServiceHealth>> healthCacheListenersByIdMap = new HashMap<>();

    private Consul getConsulInstance() {
        // TODO The round robin is static and does not handle fail over of consul, make it fail safe
        if (!roundRobinConsulIterator.hasNext()) {
            roundRobinConsulIterator = consuls.iterator();
        }
        return roundRobinConsulIterator.next();
    }

    @Override
    public void postConstruct(Deployment deployment, Map<String, Map<String, Object>> pluginProperties, Map<String, Object> bootstrapContext) {
        this.deployment = deployment;
        List<String> consulAddresses = ConsulURLUtil.extractConsulURLs(bootstrapContext);
        // Perform connections to all consul nodes that were bootstrapped
        consuls = consulAddresses.stream().map(address -> Consul.builder().withHostAndPort(HostAndPort.fromString(address)).build()).collect(Collectors.toList());
        roundRobinConsulIterator = consuls.iterator();
        log.info("Consul raft leader {}", getConsulInstance().statusClient().getLeader());
        getConsulInstance().statusClient().getPeers().forEach(peer -> log.info("Consul raft peer {}", peer));
    }

    @Override
    public void preExecuteNodeOperation(Root node, String interfaceName, String operationName) {
        if (node instanceof Compute && "stop".equals(operationName) && NODE_STANDARD_INTERFACE.equals(interfaceName)) {
            // Before stopping a compute we peform de-registration on consul
            getConsulInstance().agentClient().deregister(this.deployment.getConfig().getDeploymentName() + "." + node.getId());
            String serviceId = this.deployment.getConfig().getDeploymentName() + "." + node.getId();
            String serviceName = this.deployment.getConfig().getDeploymentName() + "." + node.getName();
            synchronized (healthCacheMap) {
                ServiceHealthCache serviceHealthCache = healthCacheMap.get(serviceName);
                serviceHealthCache.removeListener(healthCacheListenersByIdMap.remove(serviceId));
                // TODO At some point stop service health cache
            }
        }
    }

    private void registerToConsul(Root node) throws Exception {
        // After starting a compute we perform registration on consul
        String ip = node.getAttributeAsString("ip_address");
        // TODO for the moment automatically check port 22 every 5 seconds, this should be configurable
        Registration.RegCheck regCheck = Registration.RegCheck.tcp(HostAndPort.fromParts(ip, 22).toString(), 5);
        String serviceId = this.deployment.getConfig().getDeploymentName() + "." + node.getId();
        String serviceName = this.deployment.getConfig().getDeploymentName() + "." + node.getName();
        Registration registration = ImmutableRegistration.builder().check(regCheck)
                .id(serviceId)
                .name(serviceName)
                .address(ip).build();
        Consul consul = getConsulInstance();
        consul.agentClient().register(registration);
        ServiceHealthCache serviceHealthCache;
        synchronized (healthCacheMap) {
            serviceHealthCache = healthCacheMap.get(serviceName);
            if (serviceHealthCache == null) {
                serviceHealthCache = ServiceHealthCache.newCache(consul.healthClient(), serviceName);
            }
            ConsulCache.Listener<ServiceHealthKey, ServiceHealth> listener = newValues -> {
                Optional<ServiceHealth> optionalComputeHealth = newValues.values().stream().filter(serviceHealth -> serviceHealth.getService().getId().equals(serviceId)).findFirst();
                if (optionalComputeHealth.isPresent()) {
                    ServiceHealth computeHealth = optionalComputeHealth.get();
                    List<HealthCheck> failedHealthChecks = computeHealth.getChecks().stream().filter(check -> !check.getStatus().equals("passing")).collect(Collectors.toList());
                    failedHealthChecks.forEach(failedHealthCheck -> log.info("Compute {} is {}, failed health check with output {} and note {}", node.getId(), failedHealthCheck.getStatus(), failedHealthCheck.getOutput(), failedHealthCheck.getNotes()));
                    node.setState(failedHealthChecks.isEmpty() ? InstanceState.STARTED : InstanceState.ERROR);
                }
            };
            serviceHealthCache.addListener(listener);
            healthCacheListenersByIdMap.put(serviceId, listener);
            if (!healthCacheMap.containsKey(serviceName)) {
                healthCacheMap.put(serviceName, serviceHealthCache);
                serviceHealthCache.start();
            }
        }
        node.setAttribute("service_id", serviceId);
        node.setAttribute("service_name", serviceName);
    }

    @Override
    public void postExecuteNodeOperation(Root node, String interfaceName, String operationName) throws Exception {
        if (node instanceof Compute && "start".equals(operationName) && NODE_STANDARD_INTERFACE.equals(interfaceName)) {
            registerToConsul(node);
        }
    }

    @Override
    public void preInitialLoad(Root node) {
    }

    @Override
    public void postInitialLoad(Root node) {
    }

    @Override
    public void preExecuteRelationshipOperation(tosca.relationships.Root relationship, String interfaceName, String operationName) {
    }

    @Override
    public void postExecuteRelationshipOperation(tosca.relationships.Root relationship, String interfaceName, String operationName) {
    }
}
