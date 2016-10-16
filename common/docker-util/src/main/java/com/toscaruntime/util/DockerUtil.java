package com.toscaruntime.util;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.netty.DockerCmdExecFactoryImpl;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

public class DockerUtil {

    private static final DockerDaemonConfig DEFAULT_DOCKER_CONF_FOR_LINUX = getDefaultDockerConfigForLinux();

    private static final DockerDaemonConfig DEFAULT_DOCKER_CONF_FOR_MAC_WINDOWS = new DockerDaemonConfig(
            "tcp://192.168.99.100:2376",
            "1",
            Paths.get(System.getProperty("user.home")).resolve(".docker").resolve("machine").resolve("machines").resolve("default").toString()
    );

    private static NetworkInterface guessMostRelevantInterface() throws SocketException {
        Enumeration<NetworkInterface> networkInterfaceEnumeration = NetworkInterface.getNetworkInterfaces();
        if (!networkInterfaceEnumeration.hasMoreElements()) {
            // No network interface !!
            return null;
        }
        while (networkInterfaceEnumeration.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaceEnumeration.nextElement();
            // Docker will create virtual interface on the machine, does not take it
            if (!networkInterface.getName().startsWith("docker")) {
                // Ethernet interface is priority
                if (networkInterface.getName().equals("eth") || networkInterface.getName().startsWith("en")) {
                    return networkInterface;
                }
            }
        }
        // Return a random one if none is found
        return NetworkInterface.getNetworkInterfaces().nextElement();
    }

    private static DockerDaemonConfig getDefaultDockerConfigForLinux() {
        String defaultHostForLinux = "unix:///var/run/docker.sock";
        try {
            NetworkInterface networkInterface = guessMostRelevantInterface();
            if (networkInterface == null) {
                return new DockerDaemonConfig(defaultHostForLinux, "0", null);
            }
            Enumeration<InetAddress> address = networkInterface.getInetAddresses();
            while (address.hasMoreElements()) {
                InetAddress currentAddress = address.nextElement();
                if (currentAddress instanceof Inet4Address && !currentAddress.isLoopbackAddress()) {
                    return new DockerDaemonConfig("tcp://" + currentAddress.getHostAddress() + ":2376", "0", null);
                }
            }
            return new DockerDaemonConfig(defaultHostForLinux, "0", null);
        } catch (SocketException ignored) {
            return new DockerDaemonConfig(defaultHostForLinux, "0", null);
        }
    }

    public static DockerClient buildDockerClient(Map<String, String> providerProperties) {
        Properties properties = new Properties();
        properties.put(DockerClientConfig.API_VERSION, "1.22");
        properties.putAll(providerProperties);
        DockerClientConfig config = new DockerClientConfig.DockerClientConfigBuilder().withProperties(properties).build();
        DockerCmdExecFactoryImpl execFactory = new DockerCmdExecFactoryImpl();
        return DockerClientBuilder.getInstance(config).withDockerCmdExecFactory(execFactory).build();
    }

    public static DockerDaemonConfig getDefaultDockerDaemonConfig() throws MalformedURLException {
        String dockerHostFromEnv = System.getenv(DockerClientConfig.DOCKER_HOST);
        if (StringUtils.isNotBlank(dockerHostFromEnv)) {
            return new DockerDaemonConfig(dockerHostFromEnv, System.getenv(DockerClientConfig.DOCKER_TLS_VERIFY), System.getenv(DockerClientConfig.DOCKER_CERT_PATH));
        } else {
            String osName = System.getProperty("os.name");
            if (osName.startsWith("Windows") || osName.startsWith("Mac")) {
                return DEFAULT_DOCKER_CONF_FOR_MAC_WINDOWS;
            } else {
                return DEFAULT_DOCKER_CONF_FOR_LINUX;
            }
        }
    }

    public static String getDockerDaemonIP(Map<String, String> providerProperties) throws UnknownHostException {
        String dockerHost = providerProperties.get(DockerClientConfig.DOCKER_HOST);
        if (StringUtils.isBlank(dockerHost)) {
            return "127.0.0.1";
        } else {
            return InetAddress.getByName(getDockerHostName(dockerHost)).getHostAddress();
        }
    }

    public static DockerClient buildDockerClient(DockerDaemonConfig config) {
        Map<String, String> providerProperties = Maps.newHashMap();
        providerProperties.put(DockerClientConfig.DOCKER_HOST, config.getHost());
        if (StringUtils.isNotBlank(config.getCertPath())) {
            providerProperties.put(DockerClientConfig.DOCKER_CERT_PATH, config.getCertPath());
        }
        if (StringUtils.isNotBlank(config.getTlsVerify())) {
            providerProperties.put(DockerClientConfig.DOCKER_TLS_VERIFY, config.getTlsVerify());
        }
        return buildDockerClient(providerProperties);
    }

    public static String getDockerHostName(Map<String, String> properties) {
        return properties.get(DockerClientConfig.DOCKER_HOST);
    }

    public static String getDockerCertPath(Map<String, String> properties) {
        return properties.get(DockerClientConfig.DOCKER_CERT_PATH);
    }

    public static String getDockerTlsVerify(Map<String, String> properties) {
        return properties.get(DockerClientConfig.DOCKER_TLS_VERIFY);
    }

    public static DockerDaemonConfig getDockerDaemonConfig(Map<String, String> properties) {
        return new DockerDaemonConfig(getDockerHostName(properties), getDockerTlsVerify(properties), getDockerCertPath(properties));
    }

    public static void showLog(DockerClient dockerClient, String containerId, boolean follow, int numberOfLines, LogContainerResultCallback logCallback) {
        dockerClient.logContainerCmd(containerId).withStdOut(true).withStdErr(true).withFollowStream(follow).withTail(numberOfLines).exec(logCallback);
    }

    /**
     * Retrieve the docker host from the URL
     *
     * @param url url of docker
     * @return the ip address of the docker host
     */
    public static String getDockerHostName(String url) {
        try {
            URI parsed = new URI(url);
            return parsed.getHost();
        } catch (URISyntaxException e) {
            return "127.0.0.1";
        }
    }

    public static String normalizeResourceName(String name) {
        return name.replaceAll("[^\\p{L}\\p{Nd}]+", "");
    }
}
