package com.toscaruntime.artifact;

public class Artifact {

    private String path;

    private String type;

    public Artifact(String path, String type) {
        this.path = path;
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public String getType() {
        return type;
    }
}
