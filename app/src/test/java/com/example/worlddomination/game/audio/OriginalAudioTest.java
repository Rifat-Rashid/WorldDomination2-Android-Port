package com.example.worlddomination.game.audio;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class OriginalAudioTest {
    @Test
    public void everyMappedCueIsAnOriginalGzipWrappedMp3Asset() throws Exception {
        for (OriginalAudio.Cue cue : OriginalAudio.Cue.values()) {
            assertTrue(OriginalAudio.internalPath(cue).endsWith(".mp3.gz"));
            File asset = locateAsset(OriginalAudio.internalPath(cue));
            assertTrue("Missing original audio asset: " + cue.name(), asset.isFile());
            try (InputStream gzip = new GZIPInputStream(new FileInputStream(asset))) {
                int first = gzip.read();
                int second = gzip.read();
                boolean mpegSync = first == 0xff && (second & 0xe0) == 0xe0;
                boolean id3Header = first == 'I' && second == 'D';
                assertTrue("Audio asset is not an MP3 stream: " + cue.name(),
                        mpegSync || id3Header);
            }
            assertTrue(OriginalAudio.cachePath(cue).endsWith(".mp3"));
            assertTrue("Cache paths should be safe for Android filesystems",
                    !OriginalAudio.cachePath(cue).contains(" "));
        }
    }

    @Test
    public void themeUsesTheRecoveredWorldDominationTrack() {
        assertEquals("original/runtime/audio/2_WDTheme.wav.mp3.gz",
                OriginalAudio.internalPath(OriginalAudio.Cue.THEME));
    }

    private static File locateAsset(String relativePath) {
        File directory = new File(System.getProperty("user.dir"));
        while (directory != null) {
            File appAsset = new File(directory, "app/src/main/assets/" + relativePath);
            if (appAsset.isFile()) {
                return appAsset;
            }
            File moduleAsset = new File(directory, "src/main/assets/" + relativePath);
            if (moduleAsset.isFile()) {
                return moduleAsset;
            }
            directory = directory.getParentFile();
        }
        return new File(System.getProperty("user.dir"), relativePath);
    }
}
