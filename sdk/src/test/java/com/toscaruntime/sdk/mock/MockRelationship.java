package com.toscaruntime.sdk.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tosca.relationships.Root;

public class MockRelationship extends Root {

    private static final Logger log = LoggerFactory.getLogger(MockRelationship.class);

    @Override
    public void preConfigureSource() {
        log.info("Before preConfigureSource From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.preConfigureSource();
    }

    @Override
    public void preConfigureTarget() {
        log.info("Before preConfigureTarget From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.preConfigureTarget();
    }

    @Override
    public void postConfigureSource() {
        log.info("Before postConfigureSource From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.postConfigureSource();
    }

    @Override
    public void postConfigureTarget() {
        log.info("Before postConfigureTarget From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.postConfigureTarget();
    }

    @Override
    public void addTarget() {
        log.info("Before addTarget From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.addTarget();
    }

    @Override
    public void addSource() {
        log.info("Before addSource From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.addSource();
    }

    @Override
    public void removeSource() {
        log.info("Before removeSource From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.removeSource();
    }

    @Override
    public void removeTarget() {
        log.info("Before removeTarget From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.removeTarget();
    }
}
