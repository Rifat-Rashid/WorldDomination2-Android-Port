package com.example.worlddomination.game.assets;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

public final class CompressedFlashAssetReadTest {
    @Test public void svgzReadsCompressedStreamWithoutProbingBogusSidecar() throws Exception {
        try (FilesFixture fixture = new FilesFixture()) {
            String path = "original/runtime/shapes/123.svgz";
            fixture.put(path, gzip("shape"));
            assertEquals("shape", OriginalFlashAnimationAssets.readCompressedUtf8(path));
            assertEquals(List.of(path), fixture.requested);
            assertEquals(List.of(path), fixture.reads);
            assertTrue(fixture.probes.isEmpty());
        }
    }

    @Test public void gzMetadataStillPrefersUncompressedSidecar() throws Exception {
        try (FilesFixture fixture = new FilesFixture()) {
            String path = "original/runtime/scene.json.gz";
            String sidecar = "original/runtime/scene.json";
            fixture.put(path, gzip("compressed"));
            fixture.put(sidecar, "raw".getBytes(StandardCharsets.UTF_8));
            assertEquals("raw", OriginalFlashAnimationAssets.readCompressedUtf8(path));
            assertEquals(List.of(sidecar), fixture.probes);
            assertEquals(List.of(sidecar), fixture.reads);
            assertEquals(List.of(sidecar), fixture.requested);
        }
    }

    @Test public void gzMetadataFallsBackToCompressedStream() throws Exception {
        try (FilesFixture fixture = new FilesFixture()) {
            String path = "original/runtime/battle-root.json.gz";
            String sidecar = "original/runtime/battle-root.json";
            fixture.put(path, gzip("compressed"));
            assertEquals("compressed", OriginalFlashAnimationAssets.readCompressedUtf8(path));
            assertEquals(List.of(sidecar), fixture.probes);
            assertEquals(List.of(path), fixture.reads);
            assertEquals(List.of(sidecar, path), fixture.requested);
        }
    }

    @Test public void missingCompressedShapeStillFailsWithItsPath() {
        try (FilesFixture fixture = new FilesFixture()) {
            String path = "original/runtime/shapes/999999.svgz";
            try {
                OriginalFlashAnimationAssets.readCompressedUtf8(path);
                fail("Missing compressed shape must fail");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains(path));
            }
            assertEquals(List.of(path), fixture.requested);
            assertEquals(List.of(path), fixture.reads);
            assertTrue(fixture.probes.isEmpty());
        }
    }

    private static byte[] gzip(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream output = new GZIPOutputStream(bytes)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static final class FilesFixture implements AutoCloseable {
        final Files previous = Gdx.files;
        final Map<String, byte[]> contents = new HashMap<>();
        final List<String> requested = new ArrayList<>();
        final List<String> probes = new ArrayList<>();
        final List<String> reads = new ArrayList<>();

        FilesFixture() {
            Gdx.files = (Files) Proxy.newProxyInstance(Files.class.getClassLoader(),
                    new Class<?>[]{Files.class}, (proxy, method, args) -> {
                        if ("internal".equals(method.getName())) {
                            String path = (String) args[0];
                            requested.add(path);
                            return new TracedHandle(path, this);
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        void put(String path, byte[] data) { contents.put(path, data); }

        @Override public void close() { Gdx.files = previous; }
    }

    private static final class TracedHandle extends FileHandle {
        private final String tracedPath;
        private final FilesFixture fixture;

        TracedHandle(String path, FilesFixture fixture) {
            super(path);
            this.tracedPath = path;
            this.fixture = fixture;
        }

        @Override public boolean exists() {
            fixture.probes.add(tracedPath);
            return fixture.contents.containsKey(tracedPath);
        }

        @Override public InputStream read() {
            fixture.reads.add(tracedPath);
            byte[] data = fixture.contents.get(tracedPath);
            if (data == null) throw new IllegalStateException("Missing " + tracedPath);
            return new ByteArrayInputStream(data);
        }
    }
}
