import java.util.Map;

import com.google.common.collect.Maps;
import com.toscaruntime.util.SSHExecutor;

/**
 * @author Minh Khang VU
 */
public class Test {

    public static void main(String[] args) throws Exception {
        SSHExecutor executor = new SSHExecutor("ubuntu", "128.136.179.138", 22, "common/ssh-util/src/test/resources/toscaruntime.pem");
        executor.init();
        Map<String, String> envVars = executor.executeCommand("test", "export TOTO=TOTO_VALUE", Maps.newHashMap());
        for (Map.Entry<String, String> envVarEntry : envVars.entrySet()) {
            System.out.println(envVarEntry.getKey() + " = " + envVarEntry.getValue());
        }
    }
}
