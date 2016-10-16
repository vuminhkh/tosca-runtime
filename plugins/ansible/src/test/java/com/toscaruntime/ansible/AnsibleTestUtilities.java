package com.toscaruntime.ansible;

import com.github.dockerjava.core.DockerClientConfig;
import com.toscaruntime.ansible.connection.AnsibleConnection;
import com.toscaruntime.ansible.connection.AnsibleDockerConnection;
import com.toscaruntime.ansible.connection.AnsibleSSHConnection;
import com.toscaruntime.artifact.Connection;
import com.toscaruntime.util.PropertyUtil;
import org.junit.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class AnsibleTestUtilities {

    static AnsibleDockerConnection createDockerConnection(Connection controlMachineConnection, Map<String, Object> containerProperties) throws IOException {
        // Upload the cert from the local machine to control machine
        String certPathOnControlMachine = "/tmp/cert";
        String certPathOnLocalMachine = PropertyUtil.getMandatoryPropertyAsString(containerProperties, DockerClientConfig.DOCKER_CERT_PATH);
        Path tempDirForCert = Files.createTempDirectory("cert");
        Files.walk(Paths.get(certPathOnLocalMachine)).filter(path -> path.getFileName().toString().endsWith(".pem")).forEach(path -> {
            try {
                Files.copy(path, tempDirForCert.resolve(path.getFileName()));
            } catch (IOException e) {
                Assert.fail(e.getMessage());
            }
        });
        controlMachineConnection.upload(tempDirForCert.toString(), certPathOnControlMachine);
        Map<String, Object> ansibleConnectionProperties = new HashMap<>(containerProperties);
        ansibleConnectionProperties.put(AnsibleConnection.CONTROL_MACHINE_CONNECTION, controlMachineConnection);
        ansibleConnectionProperties.put(DockerClientConfig.DOCKER_CERT_PATH, certPathOnControlMachine);
        AnsibleDockerConnection dockerConnection = new AnsibleDockerConnection();
        dockerConnection.initialize(ansibleConnectionProperties);
        return dockerConnection;
    }

    static AnsibleSSHConnection createSSHConnection(Connection controlMachineConnection, Map<String, Object> containerProperties) {
        Map<String, Object> ansibleConnectionProperties = new HashMap<>(containerProperties);
        ansibleConnectionProperties.put(AnsibleConnection.CONTROL_MACHINE_CONNECTION, controlMachineConnection);
        AnsibleSSHConnection sshConnection = new AnsibleSSHConnection();
        sshConnection.initialize(ansibleConnectionProperties);
        return sshConnection;
    }
}
