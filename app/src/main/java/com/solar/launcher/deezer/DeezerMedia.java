package com.solar.launcher.deezer;

import com.solar.launcher.net.TlsHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Resolve signed CDN URL from media.deezer.com. */
public final class DeezerMedia {
    private static final String GET_URL = "https://media.deezer.com/v1/get_url";

    private final DeezerClient client;

    public DeezerMedia(DeezerClient client) {
        this.client = client;
    }

    public String resolveUrl(String trackToken) throws IOException {
        if (trackToken == null || trackToken.isEmpty()) {
            throw new IOException("No track token");
        }
        JSONObject format = new JSONObject();
        try {
            format.put("cipher", "BF_CBC_STRIPE");
            format.put("format", client.soundFormat());
            JSONArray formats = new JSONArray();
            formats.put(format);
            JSONObject media = new JSONObject();
            media.put("type", "FULL");
            media.put("formats", formats);
            JSONArray mediaArr = new JSONArray();
            mediaArr.put(media);
            JSONArray tokens = new JSONArray();
            tokens.put(trackToken);
            JSONObject body = new JSONObject();
            body.put("license_token", client.licenseToken());
            body.put("media", mediaArr);
            body.put("track_tokens", tokens);

            TlsHelper.ensureSecurityProvider();
            MediaType jsonType = MediaType.parse("application/json; charset=utf-8");
            Request req = new Request.Builder()
                    .url(GET_URL)
                    .post(RequestBody.create(jsonType, body.toString()))
                    .header("User-Agent", DeezerClient.USER_AGENT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .build();
            OkHttpClient http = TlsHelper.client();
            Response resp = http.newCall(req).execute();
            try {
                if (!resp.isSuccessful() || resp.body() == null) {
                    throw new IOException("HTTP " + resp.code());
                }
                String bodyText = resp.body().string();
                JSONObject root = new JSONObject(bodyText);
                JSONArray data = root.optJSONArray("data");
                if (data == null || data.length() == 0) {
                    throw new IOException(mediaApiError(root, "No download URL from Deezer"));
                }
                JSONObject item = data.getJSONObject(0);
                if (item.has("errors")) {
                    JSONArray errs = item.getJSONArray("errors");
                    throw new IOException(mediaApiError(errs, "API error"));
                }
                JSONArray mediaOut = item.optJSONArray("media");
                if (mediaOut == null || mediaOut.length() == 0) {
                    throw new IOException("No media in Deezer response");
                }
                JSONArray sources = mediaOut.getJSONObject(0).optJSONArray("sources");
                if (sources == null || sources.length() == 0) {
                    throw new IOException("No stream source for this track");
                }
                return sources.getJSONObject(0).getString("url");
            } finally {
                if (resp.body() != null) resp.body().close();
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    /** Pull first API error message from a response or errors array. */
    private static String mediaApiError(JSONObject root, String fallback) {
        if (root == null) return fallback;
        JSONArray errs = root.optJSONArray("errors");
        if (errs != null && errs.length() > 0) {
            return errs.optJSONObject(0).optString("message", fallback);
        }
        return fallback;
    }

    private static String mediaApiError(JSONArray errs, String fallback) {
        if (errs != null && errs.length() > 0) {
            return errs.optJSONObject(0).optString("message", fallback);
        }
        return fallback;
    }
}
