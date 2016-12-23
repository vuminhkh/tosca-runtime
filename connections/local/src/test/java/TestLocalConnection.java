import com.toscaruntime.artifact.AbstractOutputHandler;
import com.toscaruntime.artifact.SimpleCommandOutputHandler;
import com.toscaruntime.connection.LocalConnection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;

@RunWith(JUnit4.class)
public class TestLocalConnection {

    @Test
    public void testEnvVar() {
        LocalConnection localConnection = new LocalConnection();
        localConnection.initialize(new HashMap<>());
        StringBuffer buffer = new StringBuffer();
        localConnection.executeCommand("echo $HOME", new AbstractOutputHandler() {
            @Override
            protected void onData(String source, String line) {
                buffer.append(line);
            }
        });
        Assert.assertEquals(System.getenv("HOME"), buffer.toString());
    }
}
