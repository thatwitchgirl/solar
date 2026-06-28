package com.solar.launcher.theme;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Set;

public class ThemeDownloaderTest {
    @Test
    public void selfCheck() throws Exception {
        ThemeDownloader.selfCheck();
    }

    @Test
    public void subfolderOutputPath() throws Exception {
        File themeDir = new File(System.getProperty("java.io.tmpdir"), "solar-theme-subfolder-test");
        deleteDir(themeDir);
        themeDir.mkdirs();
        try {
            File out = ThemeDownloader.resolveAssetOutputFile(themeDir, "ACNH",
                    "ACNH/all other images/folder view/foldericon.png");
            if (out == null) throw new AssertionError("null output");
            String path = out.getAbsolutePath().replace('\\', '/');
            if (!path.contains("/all other images/folder view/foldericon.png")) {
                throw new AssertionError("bad subfolder path: " + path);
            }
        } finally {
            deleteDir(themeDir);
        }
    }

    @Test
    public void eddieVariantPlanLocalNames() throws Exception {
        URL url = new URL("http://themes.innioasis.app/Stranger%20Things/Variants/Eddie/config.json");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        InputStream in = conn.getInputStream();
        byte[] buf = new byte[8192];
        int n, total = 0;
        byte[] all = new byte[65536];
        while ((n = in.read(buf)) != -1 && total + n <= all.length) {
            System.arraycopy(buf, 0, all, total, n);
            total += n;
        }
        in.close();
        JSONObject config = new JSONObject(new String(all, 0, total, "UTF-8"));
        String catalog = "Stranger Things/Variants/Eddie";
        Set<String> assets = ThemeDownloader.collectAssetPaths(config, catalog);
        assets.remove(catalog + "/config.json");
        Map<String, String> mapped = ThemeDownloader.planLocalNames(assets);
        if (mapped.isEmpty()) throw new AssertionError("no mapped assets");
        if (mapped.size() != assets.size()) throw new AssertionError("map size mismatch");
    }

    private static void deleteDir(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteDir(k);
        }
        f.delete();
    }
}
