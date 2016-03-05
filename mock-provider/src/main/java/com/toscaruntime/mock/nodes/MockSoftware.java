package com.toscaruntime.mock.nodes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tosca.nodes.Root;

public class MockSoftware extends Root {

    private static final Logger log = LoggerFactory.getLogger(MockSoftware.class);

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
}
