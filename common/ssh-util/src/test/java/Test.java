import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.toscaruntime.util.SSHExecutor;

/**
 * @author Minh Khang VU
 */
public class Test {

    public static void main(String[] args) throws Exception {
        ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.execute(() -> {
            try {
                execute("129.185.67.81");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        executorService.execute(() -> {
            try {
                execute("129.185.67.78");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        executorService.shutdown();
    }

    public static void execute(String ip) throws Exception {
        SSHExecutor executor = new SSHExecutor("ubuntu", ip, 22, "/Users/vuminhkh/Projects/tosca-runtime/cli/src/main/resources/bootstrap/openstack/swarm/archive/toscaruntime.pem");
        executor.init();
        Map<String, String> envVars = executor.executeScript("test_" + ip, "/Users/vuminhkh/Projects/tosca-runtime/common/ssh-util/src/test/resources/test.sh", new HashMap<>());
        for (Map.Entry<String, String> envVarEntry : envVars.entrySet()) {
            System.out.println(envVarEntry.getKey() + " = " + envVarEntry.getValue());
        }
    }
}
