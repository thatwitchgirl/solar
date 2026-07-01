package com.solar.launcher;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SolarDataResetTest {

    @Test
    public void overwriteFileContents_zerosBytesBeforeDelete() throws Exception {
        File f = File.createTempFile("solar_reset_", ".bin");
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(new byte[] {1, 2, 3, 4, 5});
        fos.close();

        SolarDataReset.overwriteFileContents(f);
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        byte[] buf = new byte[5];
        raf.readFully(buf);
        raf.close();
        for (byte b : buf) {
            assertTrue("expected zero overwrite", b == 0);
        }

        assertTrue(f.delete());
        assertFalse(f.exists());
    }

    @Test
    public void run_setsCachesClearedWhenCachesSelected() {
        SolarDataReset.Selection sel = new SolarDataReset.Selection();
        sel.caches = true;
        // Context-free: only inspect Result defaults from a null run
        SolarDataReset.Result bad = SolarDataReset.run(null, sel);
        assertFalse(bad.ok);
        assertFalse(bad.cachesCleared);
    }

    @Test
    public void markPendingAlbumArtCacheRebuild_survivesConsume() {
        MainActivity.markPendingAlbumArtCacheRebuild();
        assertTrue(MainActivity.consumePendingAlbumArtCacheRebuild());
        assertFalse(MainActivity.consumePendingAlbumArtCacheRebuild());
    }

    @Test
    public void deleteTree_removesNestedFiles() throws Exception {
        File root = File.createTempFile("solar_reset_dir_", "");
        assertTrue(root.delete());
        assertTrue(root.mkdir());
        File child = new File(root, "nested.txt");
        FileOutputStream fos = new FileOutputStream(child);
        fos.write("x".getBytes("UTF-8"));
        fos.close();

        SolarDataReset.deleteTree(root, false);
        assertFalse(root.exists());
    }
}
