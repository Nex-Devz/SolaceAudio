package com.solaceaudio.sources.deezer.crypto;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * FilterInputStream that automatically decrypts Deezer encrypted chunks on the fly.
 * Every 3rd 2048-byte chunk (chunkIndex % 3 == 0) is decrypted via Blowfish-CBC.
 */
public class DeezerBlowfishInputStream extends FilterInputStream {

    private final DeezerCipher cipher;
    private final byte[] chunkBuffer = new byte[DeezerCipher.CHUNK_SIZE];
    private int bufferOffset = 0;
    private int bufferLimit = 0;
    private long chunkIndex = 0;

    public DeezerBlowfishInputStream(InputStream in, String songId, String masterKey) {
        super(in);
        this.cipher = new DeezerCipher(songId, masterKey);
    }

    private boolean fillBuffer() throws IOException {
        int readBytes = 0;
        while (readBytes < DeezerCipher.CHUNK_SIZE) {
            int count = in.read(chunkBuffer, readBytes, DeezerCipher.CHUNK_SIZE - readBytes);
            if (count == -1) {
                break;
            }
            readBytes += count;
        }

        if (readBytes == 0) {
            return false;
        }

        if (chunkIndex % 3 == 0 && readBytes == DeezerCipher.CHUNK_SIZE) {
            cipher.decryptChunk(chunkBuffer, 0, readBytes);
        }

        chunkIndex++;
        bufferOffset = 0;
        bufferLimit = readBytes;
        return true;
    }

    @Override
    public int read() throws IOException {
        if (bufferOffset >= bufferLimit) {
            if (!fillBuffer()) {
                return -1;
            }
        }
        return chunkBuffer[bufferOffset++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (bufferOffset >= bufferLimit) {
            if (!fillBuffer()) {
                return -1;
            }
        }
        int available = bufferLimit - bufferOffset;
        int toRead = Math.min(len, available);
        System.arraycopy(chunkBuffer, bufferOffset, b, off, toRead);
        bufferOffset += toRead;
        return toRead;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = 0;
        while (skipped < n) {
            if (bufferOffset >= bufferLimit) {
                if (!fillBuffer()) {
                    break;
                }
            }
            int available = bufferLimit - bufferOffset;
            long step = Math.min(n - skipped, available);
            bufferOffset += (int) step;
            skipped += step;
        }
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return (bufferLimit - bufferOffset) + in.available();
    }
}
