package com.toscaruntime.mock.relationships;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tosca.relationships.Root;

public class MockRelationship extends Root {

    private static final Logger log = LoggerFactory.getLogger(MockRelationship.class);

    @Override
    public void preConfigureSource() {
        log.info("preConfigureSource From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.preConfigureSource();
    }

    @Override
    public void preConfigureTarget() {
        log.info("preConfigureTarget From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.preConfigureTarget();
    }

    @Override
    public void postConfigureSource() {
        log.info("postConfigureSource From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.postConfigureSource();
    }

    @Override
    public void postConfigureTarget() {
        log.info("postConfigureTarget From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.postConfigureTarget();
    }

    @Override
    public void addTarget() {
        log.info("addTarget From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.addTarget();
    }

    @Override
    public void addSource() {
        log.info("addSource From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.addSource();
    }

    @Override
    public void removeSource() {
        log.info("removeSource From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.removeSource();
    }

    @Override
    public void removeTarget() {
        log.info("removeTarget From {} to {} state {}", getSource().getId(), getTarget().getId(), getState());
        super.removeTarget();
    }
}
