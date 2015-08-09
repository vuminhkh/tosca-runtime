import java.io.IOException;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.dockerjava.api.DockerClient;
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
        Container compute = (Container) applicationContext.getBean("compute");
        compute.create();
        compute.start();
        Java java = (Java) applicationContext.getBean("java");
        Thread.sleep(5000L);
        java.create();
    }
}
