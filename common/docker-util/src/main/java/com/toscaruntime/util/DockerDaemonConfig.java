package com.toscaruntime.util;

import java.util.Collections;
import java.util.Map;

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

    private Map<String, String> extraProperties;

    public DockerDaemonConfig(String host, String tlsVerify, String certPath) {
        this(host, tlsVerify, certPath, Collections.emptyMap());
    }

    public DockerDaemonConfig(String host, String tlsVerify, String certPath, Map<String, String> extraProperties) {
        this.host = host;
        this.tlsVerify = tlsVerify;
        this.certPath = certPath;
        this.extraProperties = extraProperties;
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

    public Map<String, String> getExtraProperties() {
        return extraProperties;
    }

    @Override
    public String toString() {
        return "DockerDaemonConfig{" +
                "host='" + host + '\'' +
                ", tlsVerify='" + tlsVerify + '\'' +
                ", certPath='" + certPath + '\'' +
                ", extraProperties=" + extraProperties +
                '}';
    }
}
