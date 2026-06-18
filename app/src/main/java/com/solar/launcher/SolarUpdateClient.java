package com.solar.launcher;

import com.solar.launcher.net.SolarHttp;
import com.solar.launcher.net.TlsHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** GitHub releases for thatwitchgirl/solar — stable (v*) and nightly (nightly-*). */
public final class SolarUpdateClient {
    public static final String DEFAULT_REPO = "thatwitchgirl/solar";
    public static final String APK_ASSET = "app-release.apk";

    public static final class ReleaseInfo {
        public final String tag;
        public final String versionName;
        public final int versionCode;
        public final String apkUrl;
        public final boolean nightly;

        ReleaseInfo(String tag, String versionName, int versionCode, String apkUrl, boolean nightly) {
            this.tag = tag == null ? "" : tag;
            this.versionName = versionName == null ? "" : versionName;
            this.versionCode = versionCode;
            this.apkUrl = apkUrl == null ? "" : apkUrl;
            this.nightly = nightly;
        }

        public boolean isNewerThan(int localCode, String localName) {
            String local = localName == null ? "" : localName.trim();
            if (nightly) {
                if (local.startsWith("nightly-")) {
                    if (versionCode > 0 && localCode > 0) return versionCode > localCode;
                    return !tag.equals(local);
                }
                return true;
            }
            if (local.startsWith("nightly-")) return true;
            return compareSemver(local, versionName) < 0;
        }
    }

    private SolarUpdateClient() {}

    public static List<ReleaseInfo> fetchReleases(String repo, String token) throws IOException {
        if (repo == null || repo.trim().isEmpty()) repo = DEFAULT_REPO;
        String url = "https://api.github.com/repos/" + repo.trim() + "/releases?per_page=40";
        TlsHelper.ensureSecurityProvider();
        Request.Builder rb = new Request.Builder()
                .url(url)
                .header("User-Agent", "SolarLauncher/1.0")
                .header("Accept", "application/vnd.github+json");
        if (token != null && !token.trim().isEmpty()) {
            rb.header("Authorization", "Bearer " + token.trim());
        }
        OkHttpClient client = SolarHttp.longReadClient();
        Response resp = client.newCall(rb.build()).execute();
        try {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("GitHub releases HTTP " + resp.code());
            }
            JSONArray arr;
            try {
                arr = new JSONArray(new String(resp.body().bytes(), "UTF-8"));
            } catch (JSONException e) {
                throw new IOException("Invalid GitHub releases JSON", e);
            }
            List<ReleaseInfo> out = new ArrayList<ReleaseInfo>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject rel = arr.optJSONObject(i);
                if (rel == null) continue;
                if (rel.optBoolean("draft", false)) continue;
                String tag = rel.optString("tag_name", "").trim();
                if (tag.isEmpty()) continue;
                String apkUrl = findApkAsset(rel.optJSONArray("assets"));
                if (apkUrl.isEmpty()) continue;
                if (tag.startsWith("nightly-")) {
                    int code = parseNightlyCode(tag);
                    out.add(new ReleaseInfo(tag, tag, code, apkUrl, true));
                } else if (tag.startsWith("v")) {
                    String name = tag.substring(1);
                    out.add(new ReleaseInfo(tag, name, 0, apkUrl, false));
                }
            }
            return out;
        } finally {
            if (resp.body() != null) resp.body().close();
        }
    }

    public static ReleaseInfo latestStable(List<ReleaseInfo> releases) {
        ReleaseInfo best = null;
        for (ReleaseInfo r : releases) {
            if (r.nightly) continue;
            if (best == null || compareSemver(best.versionName, r.versionName) < 0) best = r;
        }
        return best;
    }

    public static ReleaseInfo latestNightly(List<ReleaseInfo> releases) {
        ReleaseInfo best = null;
        for (ReleaseInfo r : releases) {
            if (!r.nightly) continue;
            if (best == null || r.versionCode > best.versionCode) best = r;
        }
        return best;
    }

    static String findApkAsset(JSONArray assets) {
        if (assets == null) return "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            if (APK_ASSET.equals(a.optString("name", ""))) {
                return a.optString("browser_download_url", "").trim();
            }
        }
        return "";
    }

    static int parseNightlyCode(String tag) {
        if (tag == null || !tag.startsWith("nightly-")) return 0;
        try {
            return Integer.parseInt(tag.substring("nightly-".length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** ponytail: naive semver — enough for v0.2 style tags */
    static int compareSemver(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? parseIntPart(pa[i]) : 0;
            int vb = i < pb.length ? parseIntPart(pb[i]) : 0;
            if (va != vb) return va < vb ? -1 : 1;
        }
        return 0;
    }

    private static int parseIntPart(String s) {
        if (s == null || s.isEmpty()) return 0;
        StringBuilder n = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') n.append(c);
            else break;
        }
        if (n.length() == 0) return 0;
        try {
            return Integer.parseInt(n.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void selfCheck() {
        if (compareSemver("0.1", "0.2") >= 0) throw new AssertionError("0.1 < 0.2");
        if (compareSemver("0.2", "0.2") != 0) throw new AssertionError("0.2 eq");
        if (parseNightlyCode("nightly-42") != 42) throw new AssertionError("nightly code");
        ReleaseInfo r = new ReleaseInfo("v0.2", "0.2", 0, "https://x/a.apk", false);
        if (!r.isNewerThan(1, "0.1")) throw new AssertionError("stable newer");
        ReleaseInfo n = new ReleaseInfo("nightly-10", "nightly-10", 10, "https://x/a.apk", true);
        if (!n.isNewerThan(5, "nightly-5")) throw new AssertionError("nightly newer");
    }
}
