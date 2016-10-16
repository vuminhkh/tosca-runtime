package com.toscaruntime.ansible.connection;

import com.github.dockerjava.core.DockerClientConfig;
import com.toscaruntime.util.DockerDaemonConfig;
import com.toscaruntime.util.DockerUtil;
import com.toscaruntime.util.PropertyUtil;
import org.apache.commons.lang.StringUtils;

import java.util.Map;

public class AnsibleDockerConnection extends AnsibleConnection {

    private DockerDaemonConfig dockerDaemonConfig;

    @Override
    public void initialize(Map<String, Object> properties) {
        super.initialize(properties);
        if (StringUtils.isBlank(getConnectionType())) {
            setConnectionType("docker");
        }
        this.dockerDaemonConfig = DockerUtil.getDockerDaemonConfig(PropertyUtil.flatten(properties));
    }

    @Override
    protected String appendConnectionInfo(String ansibleCommand) {
        ansibleCommand = exportVariable(ansibleCommand, DockerClientConfig.DOCKER_HOST, this.dockerDaemonConfig.getHost());
        ansibleCommand = exportVariable(ansibleCommand, DockerClientConfig.DOCKER_CERT_PATH, this.dockerDaemonConfig.getCertPath());
        ansibleCommand = exportVariable(ansibleCommand, DockerClientConfig.DOCKER_TLS_VERIFY, this.dockerDaemonConfig.getTlsVerify());
        ansibleCommand = super.appendConnectionInfo(ansibleCommand);
        return ansibleCommand;
    }
}
