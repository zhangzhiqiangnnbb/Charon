package com.Charon.service.port;

public interface VideoEncoder {
    /**
     * Executes the video encoding process.
     * @param request The encoding parameters
     * @return The process output log
     * @throws Exception if encoding fails
     */
    String encode(VideoEncodingRequest request) throws Exception;

    /**
     * Returns true if this encoder supports the given mode.
     */
    boolean supports(String mode);
}
