package com.toscaruntime.util;

public class DockerDaemonConfig {

    /**
     * Host in format tcp://0.0.0.0:2376
     */
    private String host;

    /**
     * O or 1 to indicate if ssl is enabled or no
     */
    private String tlsVerify;

    /**
     * Path to certificates and keys for SSL, used if ssl is enabled
     */
    private String certPath;

    public DockerDaemonConfig(String host, String tlsVerify, String certPath) {
        this.host = host;
        this.tlsVerify = tlsVerify;
        this.certPath = certPath;
    }

    public String getHost() {
        return host;
    }

    public String getTlsVerify() {
        return tlsVerify;
    }

    public String getCertPath() {
        return certPath;
    }
}
