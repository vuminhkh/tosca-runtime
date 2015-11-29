import com.google.common.collect.Maps;
import com.toscaruntime.util.SSHExecutor;

/**
 * @author Minh Khang VU
 */
public class Test {

    public static void main(String[] args) throws Exception {
        SSHExecutor executor = new SSHExecutor("ubuntu", "128.136.179.244", 22, "common/ssh-util/src/test/resources/toscaruntime.pem");
        executor.init();
        executor.executeCommand("ps -ef", Maps.newHashMap());
    }
}
