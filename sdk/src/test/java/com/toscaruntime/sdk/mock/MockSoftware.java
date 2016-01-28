package com.toscaruntime.sdk.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tosca.nodes.Root;

public class MockSoftware extends Root {

    private static final Logger log = LoggerFactory.getLogger(MockSoftware.class);

    @Override
    public void create() {
        log.info("Before create {} state {}", getId(), getState());
        super.create();
    }

    @Override
    public void configure() {
        log.info("Before configure {} state {}", getId(), getState());
        super.configure();
    }

    @Override
    public void start() {
        log.info("Before start {} state {}", getId(), getState());
        super.start();
    }

    @Override
    public void stop() {
        log.info("Before stop {} state {}", getId(), getState());
        super.stop();
    }

    @Override
    public void delete() {
        log.info("Before delete {} state {}", getId(), getState());
        super.delete();
    }
}