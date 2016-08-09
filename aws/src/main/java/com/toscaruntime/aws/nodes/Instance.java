package com.toscaruntime.aws.nodes;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.toscaruntime.aws.util.FailSafeConfigUtil;
import com.toscaruntime.exception.deployment.execution.InvalidOperationExecutionException;
import com.toscaruntime.util.FailSafeUtil;
import com.toscaruntime.nodes.SSHCompute;
import com.toscaruntime.util.SynchronizationUtil;
import org.apache.commons.lang.StringUtils;
import org.jclouds.aws.ec2.options.AWSRunInstancesOptions;
import org.jclouds.ec2.domain.ElasticIPAddress;
import org.jclouds.ec2.domain.InstanceState;
import org.jclouds.ec2.domain.Reservation;
import org.jclouds.ec2.domain.RunningInstance;
import org.jclouds.ec2.features.ElasticIPAddressVPCApi;
import org.jclouds.ec2.features.InstanceApi;
import org.jclouds.ec2.features.TagApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Instance extends SSHCompute {

    private static final Logger log = LoggerFactory.getLogger(Instance.class);

    private InstanceApi instanceApi;

    private ElasticIPAddressVPCApi elasticIPAddressApi;

    private TagApi tagApi;

    private RunningInstance instance;

    private boolean associateElasticIP;

    public void setInstanceApi(InstanceApi instanceApi) {
        this.instanceApi = instanceApi;
    }

    public void setElasticIPAddressApi(ElasticIPAddressVPCApi elasticIPAddressApi) {
        this.elasticIPAddressApi = elasticIPAddressApi;
    }

    public void setTagApi(TagApi tagApi) {
        this.tagApi = tagApi;
    }

    public void setAssociateElasticIP(boolean associateElasticIP) {
        this.associateElasticIP = associateElasticIP;
    }

    @Override
    public void initialLoad() {
        super.initialLoad();
        this.instance = this.instanceApi.describeInstancesInRegion(null, getAttributeAsString("provider_resource_id")).iterator().next().iterator().next();
    }

    @Override
    public Map<String, String> execute(String nodeId, String operationArtifactPath, Map<String, Object> inputs, Map<String, String> deploymentArtifacts) {
        return executeBySSH(nodeId, operationArtifactPath, inputs, deploymentArtifacts);
    }

    @Override
    public void create() {
        super.create();
        String instanceType = getMandatoryPropertyAsString("instance_type");
        List<String> securityGroups = (List<String>) getProperty("security_groups");
        String keyName = getMandatoryPropertyAsString("key_name");
        String userData = getPropertyAsString("user_data");
        String availabilityZone = getPropertyAsString("availability_zone");
        String imageId = getMandatoryPropertyAsString("image_id");
        String subnetId = getPropertyAsString("subnet_id");
        AWSRunInstancesOptions runInstancesOptions = AWSRunInstancesOptions.Builder.asType(instanceType)
                .withSecurityGroups(securityGroups)
                .withKeyName(keyName);
        if (StringUtils.isNotBlank(userData)) {
            runInstancesOptions.withUserData(userData.getBytes(Charsets.UTF_8));
        }
        if (StringUtils.isNotBlank(subnetId)) {
            runInstancesOptions.withSubnetId(subnetId);
        }
        int retryNumber = getAWSOperationRetry();
        long coolDownPeriod = getAWSWaitBetweenOperationRetry();
        FailSafeUtil.doActionWithRetry(() -> {
            instance = instanceApi.runInstancesInRegion(null, availabilityZone, imageId, 1, 1, runInstancesOptions).iterator().next();
        }, "Create compute " + getId(), retryNumber, coolDownPeriod, TimeUnit.SECONDS);
        FailSafeUtil.doActionWithRetry(() -> {
            tagApi.applyToResources(ImmutableMap.of("Name", config.getDeploymentName() + "_" + getId()), ImmutableSet.of(instance.getId()));
        }, "Create name tag for compute " + getId(), retryNumber, coolDownPeriod, TimeUnit.SECONDS);
    }

    @Override
    public void start() {
        super.start();
        int retryNumber = getAWSOperationRetry();
        long coolDownPeriod = getAWSWaitBetweenOperationRetry();
        if (this.instance == null) {
            throw new InvalidOperationExecutionException("Compute [" + getId() + "] : Must create the server before starting it");
        }
        SynchronizationUtil.waitUntilPredicateIsSatisfied(() -> {
            boolean isUp = InstanceState.RUNNING.equals(instance.getInstanceState());
            if (!isUp) {
                log.info("Compute [{}] : Waiting for server [{}] to be up, current state [{}]", getId(), instance.getId(), instance.getInstanceState());
                instance = instanceApi.describeInstancesInRegion(null, instance.getId()).iterator().next().iterator().next();
                return false;
            } else {
                return true;
            }
        }, retryNumber, coolDownPeriod, TimeUnit.SECONDS);
        this.ipAddress = instance.getPrivateIpAddress();
        if (associateElasticIP) {
            String configuredElasticIP = getPropertyAsString("elastic_ip_allocation_id");
            if (StringUtils.isBlank(configuredElasticIP)) {
                ElasticIPAddress elasticIPAddress = elasticIPAddressApi.allocateAddressInRegion(null);
                configuredElasticIP = elasticIPAddress.getPublicIp();
                setAttribute("elastic_ip_allocation_id", elasticIPAddress.getAllocationId());
            }
            elasticIPAddressApi.associateAddressInRegion(null, configuredElasticIP, this.instance.getId());
            setAttribute("public_ip_address", configuredElasticIP);
        } else {
            setAttribute("public_ip_address", instance.getIpAddress());
        }
        setAttribute("ip_address", this.ipAddress);
        setAttribute("provider_resource_id", instance.getId());
        setAttribute("provider_resource_name", instance.getId());

        initSshExecutor(getIpAddressForSSHSession());
    }

    @Override
    public void stop() {
        super.stop();
        if (this.instance == null) {
            log.warn("Compute [{}] : Server has not been started yet", getId());
            return;
        }
        // Stop the compute
        int retryNumber = getAWSOperationRetry();
        long coolDownPeriod = getAWSWaitBetweenOperationRetry();
        destroySshExecutor();
        this.instanceApi.stopInstancesInRegion(null, false, this.instance.getId());
        SynchronizationUtil.waitUntilPredicateIsSatisfied(() -> {
            boolean isShutOff = InstanceState.STOPPED.equals(instance.getInstanceState());
            if (!isShutOff) {
                log.info("Compute [{}]: Waiting for server [{}] to be shutdown, current state [{}]", getId(), instance.getId(), instance.getInstanceState());
                instance = instanceApi.describeInstancesInRegion(null, instance.getId()).iterator().next().iterator().next();
                return false;
            } else {
                return true;
            }
        }, retryNumber, coolDownPeriod, TimeUnit.SECONDS);
        log.info("Compute [{}] : Stopped server with id [{}]", getId(), this.instance.getId());
    }

    @Override
    public void delete() {
        super.delete();
        int retryNumber = getAWSOperationRetry();
        long coolDownPeriod = getAWSWaitBetweenOperationRetry();
        if (this.instance == null) {
            log.warn("Compute [{}] : Server has not been started yet", getId());
            return;
        }
        String elasticIPAllocationId = getAttributeAsString("elastic_ip_allocation_id");
        if (StringUtils.isNotBlank(elasticIPAllocationId)) {
            try {
                elasticIPAddressApi.releaseAddressInRegion(null, elasticIPAllocationId);
            } catch (Exception e) {
                log.error("Could not release elastic ip with allocation id " + elasticIPAllocationId, e);
            }
        }
        this.instanceApi.terminateInstancesInRegion(null, this.instance.getId());
        SynchronizationUtil.waitUntilPredicateIsSatisfied(() -> {
            Set<? extends Reservation<? extends RunningInstance>> allReservations = instanceApi.describeInstancesInRegion(null, instance.getId());
            boolean isDeleted;
            if (allReservations.isEmpty()) {
                isDeleted = true;
            } else {
                Reservation<? extends RunningInstance> reservation = allReservations.iterator().next();
                isDeleted = reservation.isEmpty() || reservation.iterator().next().getInstanceState().equals(InstanceState.TERMINATED);
            }
            if (!isDeleted) {
                log.info("Compute [{}] : Waiting for server [{}] to be deleted, current state [{}]", getId(), instance.getId(), instance.getInstanceState());
            }
            return isDeleted;
        }, retryNumber, coolDownPeriod, TimeUnit.SECONDS);
        log.info("Compute [{}] : Deleted server with id [{}]", getId(), instance.getId());
        instance = null;
        this.removeAttribute("ip_address");
        this.removeAttribute("provider_resource_id");
        this.removeAttribute("provider_resource_name");
        this.removeAttribute("public_ip_address");
    }


    private int getAWSOperationRetry() {
        return FailSafeConfigUtil.getAWSOperationRetry(getProperties());
    }

    private long getAWSWaitBetweenOperationRetry() {
        return FailSafeConfigUtil.getAWSkWaitBetweenOperationRetry(getProperties());
    }
}
