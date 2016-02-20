import java.util.HashMap;
import java.util.Map;

import com.toscaruntime.util.SSHJExecutor;

/**
 * @author Minh Khang VU
 */
public class Test {

    public static void main(String[] args) throws Exception {
        try (SSHJExecutor sshjExecutor = new SSHJExecutor("centos", "129.185.67.118", 22, "/Users/vuminhkh/Projects/tosca-runtime/cli/src/main/resources/bootstrap/openstack/swarm/archive/toscaruntime.pem")) {
            sshjExecutor.initialize();
            sshjExecutor.upload("/Users/vuminhkh/Projects/tosca-runtime/common/ssh-util/src/test/resources", "myData/toto");
            Map<String, String> envVars = sshjExecutor.executeArtifact("test_129.185.67.107", "myData/toto/test.sh", new HashMap<>());
            for (Map.Entry<String, String> envVarEntry : envVars.entrySet()) {
                System.out.println(envVarEntry.getKey() + " = " + envVarEntry.getValue());
            }
        }
    }
//
//    public static void execute(String ip) throws Exception {
//        SSHExecutor executor = new SSHExecutor("ubuntu", ip, 22, "/Users/vuminhkh/Projects/tosca-runtime/cli/src/main/resources/bootstrap/openstack/swarm/archive/toscaruntime.pem");
//        executor.initialize();
//        Map<String, String> envVars = executor.executeArtifact("test_" + ip, "/Users/vuminhkh/Projects/tosca-runtime/common/ssh-util/src/test/resources/test.sh", new HashMap<>());
//        for (Map.Entry<String, String> envVarEntry : envVars.entrySet()) {
//            System.out.println(envVarEntry.getKey() + " = " + envVarEntry.getValue());
//        }
//    }
//
//    public static void upload(String ip, String localPath, String remotePath) throws Exception {
//        SSHExecutor executor = new SSHExecutor("centos", ip, 22, "/Users/vuminhkh/Projects/tosca-runtime/cli/src/main/resources/bootstrap/openstack/swarm/archive/toscaruntime.pem");
//        executor.initialize();
//        executor.upload(localPath, remotePath);
//    }
}
