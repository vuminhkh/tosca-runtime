import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.core.DockerClientBuilder;
import com.mkv.tosca.docker.nodes.Container;

@Configuration
public class SampleJavaProgram {

    @Bean
    public Container compute() {
        Container container = new Container();
        container.setName("compute");
        container.setDockerClient(dockerClient());
        container.setImageId("tosca");
        container.setRecipeLocalPath("/Users/mkv/tosca-docker/src/test/resources");
        return container;
    }

    @Bean
    public Java java() {
        Java java = new Java();
        java.setHostContainer(compute());
        java.setName("java");
        return java;
    }

    @Bean
    public DockerClient dockerClient() {
        DockerClient dockerClient = DockerClientBuilder.getInstance("https://192.168.59.103:2376").build();
        return dockerClient;
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.register(SampleJavaProgram.class);
        applicationContext.refresh();
//        DockerClient dockerClient = (DockerClient) applicationContext.getBean("dockerClient");
//        ExecCreateCmdResponse response = dockerClient.execCreateCmd("f8beae1675e8ddb8fc7f5c954b50e423ce64f1233bf4343c545c171affde62b9").withTty()
//                .withAttachStdin().withAttachStdout()
//                .withCmd("/bin/sh", "-c", "\"export JAVA_HOME=/opt/java && export JAVA_URL=http://download.oracle.com/otn-pub/java/jdk/7u75-b13/jdk-7u75-linux-x64.tar.gz && chmod +x /var/recip\n" +
//                        "e/java_install.sh && /var/recipe/java_install.sh\"").exec();
//        InputStream startResponse = dockerClient.execStartCmd("f8beae1675e8ddb8fc7f5c954b50e423ce64f1233bf4343c545c171affde62b9").withExecId(response.getId())
//                .withTty().exec();
//        System.out.println(IOUtils.toString(startResponse, "UTF-8"));
         Container compute = (Container) applicationContext.getBean("compute");
         compute.create();
         compute.start();
         Java java = (Java) applicationContext.getBean("java");
         Thread.sleep(5000L);
         java.create();
    }
}
