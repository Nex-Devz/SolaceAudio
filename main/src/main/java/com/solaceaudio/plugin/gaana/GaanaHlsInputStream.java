package com.solaceaudio.plugin.gaana;

import com.fasterxml.jackson.databind.JsonNode;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class GaanaHlsInputStream extends InputStream {

    private static final Logger log = LoggerFactory.getLogger(GaanaHlsInputStream.class);
    private static final int SEGMENT_BUFFER_SIZE = 5;

    private final HttpInterface httpInterface;
    private final GaanaAudioTrack track;

    private final List<Segment> segments;
    private final BlockingQueue<SegmentData> segmentQueue;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicInteger currentVersion = new AtomicInteger(0);

    private byte[] currentBuffer;
    private int bufferPosition;
    private int currentSegmentIndex;
    private Thread downloadThread;

    public static class Segment {
        public final String url;
        public final long durationMs;

        public Segment(String url, long durationMs) {
            this.url = url;
            this.durationMs = durationMs;
        }
    }

    private static class SegmentData {
        final byte[] data;
        final int version;

        SegmentData(byte[] data, int version) {
            this.data = data;
            this.version = version;
        }
    }

    public GaanaHlsInputStream(HttpInterface httpInterface, JsonNode streamNode, long startTimeMs, GaanaAudioTrack track) {
        this.httpInterface = httpInterface;
        this.track = track;
        this.segmentQueue = new LinkedBlockingQueue<>(SEGMENT_BUFFER_SIZE);
        this.segments = new ArrayList<>();

        if (streamNode.has("segments")) {
            for (JsonNode segNode : streamNode.get("segments")) {
                segments.add(new Segment(segNode.get("url").asText(), segNode.get("durationMs").asLong()));
            }
        }

        this.currentSegmentIndex = 0;
        if (startTimeMs > 0) {
            skipToPosition(startTimeMs);
        }
        startDownloadThread();
    }

    private void skipToPosition(long positionMs) {
        long elapsed = 0;
        currentSegmentIndex = 0;

        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (elapsed + segment.durationMs <= positionMs) {
                elapsed += segment.durationMs;
                currentSegmentIndex = i + 1;
            } else {
                break;
            }
        }
    }

    public long getPosition() {
        long elapsed = 0;
        for (int i = 0; i < currentSegmentIndex && i < segments.size(); i++) {
            elapsed += segments.get(i).durationMs;
        }
        return elapsed;
    }

    private void startDownloadThread() {
        int version = currentVersion.get();
        downloadThread = new Thread(() -> downloadSegments(currentSegmentIndex, version), "Gaana-HLS");
        downloadThread.setDaemon(true);
        downloadThread.start();
    }

    private void downloadSegments(int startIndex, int version) {
        try {
            for (int i = startIndex; i < segments.size() && !stopped.get(); i++) {
                if (version != currentVersion.get()) return;

                Segment segment = segments.get(i);

                boolean success = false;
                int retries = 0;
                while (!success && retries < 5 && !stopped.get() && version == currentVersion.get()) {
                    try {
                        byte[] segmentData = fetchSegment(segment.url);
                        if (segmentData != null && version == currentVersion.get()) {
                            segmentQueue.put(new SegmentData(segmentData, version));
                            success = true;
                        }
                    } catch (Exception e) {
                        retries++;
                        log.warn("Segment {} fetch failed (attempt {}): {}", i, retries, e.getMessage());
                        if (retries < 5 && !stopped.get()) {
                            Thread.sleep(1000);
                        }
                    }
                }

                while (segmentQueue.size() >= SEGMENT_BUFFER_SIZE && !stopped.get() && version == currentVersion.get()) {
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private byte[] fetchSegment(String url) throws IOException {
        HttpGet request = new HttpGet(url);
        request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        request.setHeader("Referer", "https://gaana.com/");
        
        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException("Failed to fetch segment: " + response.getStatusLine().getStatusCode());
            }
            
            try (InputStream in = response.getEntity().getContent();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                return out.toByteArray();
            }
        }
    }

    @Override
    public int read() throws IOException {
        byte[] buffer = new byte[1];
        int bytesRead = read(buffer, 0, 1);
        return bytesRead == -1 ? -1 : (buffer[0] & 0xFF);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (stopped.get()) return -1;

        int totalRead = 0;
        while (totalRead < length && !stopped.get()) {
            if (currentBuffer != null && bufferPosition < currentBuffer.length) {
                int available = currentBuffer.length - bufferPosition;
                int toRead = Math.min(available, length - totalRead);
                System.arraycopy(currentBuffer, bufferPosition, buffer, offset + totalRead, toRead);

                bufferPosition += toRead;
                totalRead += toRead;
                continue;
            }

            try {
                SegmentData nextSegment = segmentQueue.poll(5000, TimeUnit.MILLISECONDS);
                if (nextSegment == null) {
                    if (currentSegmentIndex >= segments.size() || stopped.get()) {
                        return totalRead > 0 ? totalRead : -1;
                    }
                    continue;
                }

                if (nextSegment.version != currentVersion.get()) continue;

                currentBuffer = nextSegment.data;
                bufferPosition = 0;
                currentSegmentIndex++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }
        }

        return totalRead > 0 ? totalRead : -1;
    }

    @Override
    public int available() {
        return currentBuffer != null ? currentBuffer.length - bufferPosition : 0;
    }

    @Override
    public void close() throws IOException {
        stopped.set(true);
        segmentQueue.clear();
        if (downloadThread != null) {
            downloadThread.interrupt();
        }
        super.close();
    }
}

