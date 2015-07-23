import java.util.Map;

import tosca.nodes.Root;

import com.google.common.collect.Maps;

public class Java extends Root {

    @Override
    public void create() {
        Map<String, String> env = Maps.newHashMap();
        env.put("JAVA_HOME", "/opt/java");
        env.put("JAVA_URL", "http://download.oracle.com/otn-pub/java/jdk/7u75-b13/jdk-7u75-linux-x64.tar.gz");
        executeOperation("java_install.sh", env);
    }
}
