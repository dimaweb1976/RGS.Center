package com.rgs.center.camera;

import java.util.Arrays;

/** Байты текущего изображения для HTTP endpoint. */
public record CameraImagePayload(byte[] content, String contentType, long version) {

    public CameraImagePayload {
        content = Arrays.copyOf(content, content.length);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
