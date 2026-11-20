package com.example.worlddomination.platform.persistence;

import android.system.ErrnoException;
import android.system.Os;
import com.example.worlddomination.game.persistence.GameSaveStore;
import java.io.File;
import java.io.IOException;

/** App-private, same-filesystem POSIX replacement, available since Android API 21. */
public final class AndroidGameSaveStore {
    private AndroidGameSaveStore() { }

    public static GameSaveStore create(File filesDirectory) {
        return new GameSaveStore(new File(filesDirectory, "manual-game.wd2"),
                (temporary, destination) -> {
                    try {
                        Os.rename(temporary.getAbsolutePath(), destination.getAbsolutePath());
                    } catch (ErrnoException error) {
                        throw new IOException("Unable to replace the saved game", error);
                    }
                });
    }
}
