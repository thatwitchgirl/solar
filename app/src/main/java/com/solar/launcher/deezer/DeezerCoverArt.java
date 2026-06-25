package com.solar.launcher.deezer;

/** Normalize Deezer album art to a high-resolution CDN URL. */
public final class DeezerCoverArt {

    private DeezerCoverArt() {}

    /** Upgrade API cover URLs and convert ALB_PICTURE MD5 hashes to 500×500 CDN art. */
    public static String bestCoverUrl(String coverOrHash) {
        if (coverOrHash == null) return "";
        String s = coverOrHash.trim();
        if (s.isEmpty()) return "";
        if (s.startsWith("http://") || s.startsWith("https://")) {
            return upgradeApiCoverUrl(s);
        }
        if (s.matches("(?i)[a-f0-9]{32}")) {
            return "https://e-cdns-images.dzcdn.net/images/cover/" + s.toLowerCase()
                    + "/500x500-000000-80-0-0.jpg";
        }
        return "";
    }

    /** Pick the largest cover field from a Deezer API album object. */
    public static String albumCoverFromJson(org.json.JSONObject album) {
        if (album == null) return "";
        String[] keys = {"cover_xl", "cover_big", "cover_medium", "cover", "cover_small"};
        for (String key : keys) {
            String url = album.optString(key, "").trim();
            if (!url.isEmpty()) return bestCoverUrl(url);
        }
        return "";
    }

    private static String upgradeApiCoverUrl(String url) {
        String u = url.replace("cover_small", "cover_xl")
                .replace("cover_medium", "cover_xl")
                .replace("cover_big", "cover_xl");
        u = u.replace("/100x100-", "/500x500-")
                .replace("/250x250-", "/500x500-")
                .replace("/500x500-", "/500x500-");
        return u;
    }

    private static void selfCheck() {
        String hash = "2e018122cb5698620212eccfcee10fb5";
        String fromHash = bestCoverUrl(hash);
        if (!fromHash.contains(hash)) throw new AssertionError("hash cover");
        String small = "https://cdn-images.dzcdn.net/images/cover/abc/100x100-000000-80-0-0.jpg";
        if (!bestCoverUrl(small).contains("500x500")) throw new AssertionError("upgrade small");
    }

    static {
        selfCheck();
    }
}
