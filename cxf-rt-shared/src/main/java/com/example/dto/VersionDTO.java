package com.example.dto;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class VersionDTO {
    private String implementationVersion;

    public String getImplementationVersion() {
        return implementationVersion;
    }

    public void setImplementationVersion(String implementationVersion) {
        this.implementationVersion = implementationVersion;
    }
}
