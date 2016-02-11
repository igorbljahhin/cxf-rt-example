package com.example.rt;

import com.example.dto.VersionDTO;

public class VersionResourceImpl implements VersionResource {

    @Override
    public VersionDTO getCurrentVersion() {
        final VersionDTO payload = new VersionDTO();
        payload.setImplementationVersion("0.0.1");

        return payload;
    }
}
