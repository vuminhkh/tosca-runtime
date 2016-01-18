package com.toscaruntime.util;

public class DockerDaemonConfig {

    private String url;

    private String certPath;

    public DockerDaemonConfig(String url, String certPath) {
        this.url = url;
        this.certPath = certPath;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCertPath() {
        return certPath;
    }

    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }
}
