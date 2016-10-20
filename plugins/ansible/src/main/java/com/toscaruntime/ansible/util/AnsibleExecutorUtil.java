package com.toscaruntime.ansible.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class AnsibleExecutorUtil {

    public static Path createArtifactWrapper(String artifactPath, String tempDirPath, Path userDataDir, String executionToken) throws IOException {
        Path tempArtifactWrapper = Files.createTempFile("ansible", "wrapper.yml");
        try (PrintWriter commandWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(tempArtifactWrapper))))) {
            commandWriter.println("- include: " + userDataDir.resolve(artifactPath).toString());
            commandWriter.println("- hosts: all");
            commandWriter.println("  tags: [ always ]");
            commandWriter.println("  tasks:");
            commandWriter.println("    - local_action: copy content=\"{{ hostvars[inventory_hostname] }}\" dest=" + userDataDir.resolve(tempDirPath).resolve(executionToken).toString());
        }
        return tempArtifactWrapper;
    }
}
