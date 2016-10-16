package com.toscaruntime.mock.nodes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tosca.nodes.Compute;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class MockCompute extends Compute {

    private static final Logger log = LoggerFactory.getLogger(MockCompute.class);

    private static final AtomicInteger COUNT = new AtomicInteger(0);

    @Override
    public void uploadRecipe() {
        log.info("Upload recipe");
    }

    @Override
    public void create() {
        log.info("create {} state {}", getId(), getState());
        super.create();
    }

    @Override
    public void configure() {
        log.info("configure {} state {}", getId(), getState());
        super.configure();
    }

    @Override
    public void start() {
        log.info("start {} state {}", getId(), getState());
        super.start();
        setAttribute("ip_address", "192.168.1." + COUNT.incrementAndGet());
        setAttribute("public_ip_address", "129.185.67." + COUNT.incrementAndGet());
    }

    @Override
    public void stop() {
        log.info("stop {} state {}", getId(), getState());
        super.stop();
    }

    @Override
    public void delete() {
        log.info("delete {} state {}", getId(), getState());
        super.delete();
    }

    @Override
    public Map<String, Object> execute(String nodeId, String operation, String operationArtifactPath, String artifactType, Map<String, Object> inputs, Map<String, String> deploymentArtifacts) {
        log.info("{} execute for {} operation {} with inputs {}", getId(), nodeId, operationArtifactPath, inputs);
        Map<String, Object> randomOutputs = new HashMap<>();
        randomOutputs.put("output1", UUID.randomUUID().toString());
        randomOutputs.put("output2", UUID.randomUUID().toString());
        return randomOutputs;
    }

}
