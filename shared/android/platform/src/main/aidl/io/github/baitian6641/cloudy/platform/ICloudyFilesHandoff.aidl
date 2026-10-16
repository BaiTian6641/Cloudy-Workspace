package io.github.baitian6641.cloudy.platform;

// Explicit method IDs are permanent. Payloads are versioned, bounded UTF-8; no credential channel.
interface ICloudyFilesHandoff {
    byte[] discover(int protocolVersion, String afterResourceId) = 0;
    byte[] submit(in byte[] envelopeUtf8) = 1;
}
