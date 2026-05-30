package com.github.youtubemusic.player;

import com.github.youtubemusic.main.YoutubeMusicMachineMain;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

public class MusicAPIManager {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final List<String> INVIDIOUS_INSTANCES = List.of(
            "https://inv.nadeko.net",
            "https://yewtu.be",
            "https://vid.puffyan.us",
            "https://inv.tux.pizza",
            "https://invidious.snopyta.org",
            "https://iv.melmac.space",
            "https://invidious.privacyredirect.com",
            "https://invidious.nerdvpn.de"
    );

    private static final List<String> PIPED_INSTANCES = List.of(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.smnz.de",
            "https://pipedapi-us.projectsegfau.lt",
            "https://pipedapi.moomoo.me",
            "https://pipedapi.adminforge.de",
            "https://pipedapi.leptons.xyz"
    );

    private static final List<String> PHANTOMJSEARCH_INSTANCES = List.of(
            "https://search.sapti.me",
            "https://search.disnerd.eu.org"
    );

    public static String resolve(String query) {
        if (query == null || query.isEmpty()) return null;
        query = stripSearchPrefix(query);

        YoutubeMusicMachineMain plugin = YoutubeMusicMachineMain.getInstance();
        Logger log = plugin.getLogger();
        String apiUrl = plugin.getConfig().getString("api.url", "");

        if (!apiUrl.isEmpty()) {
            log.info("Trying API bridge: " + apiUrl);
            String result = callApiBridge(apiUrl, query, log);
            if (result != null) {
                log.info("API bridge success: " + result.substring(0, Math.min(60, result.length())) + "...");
                return result;
            }
            log.warning("API bridge failed, trying Piped...");
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            log.info("Trying Piped API (attempt " + (attempt + 1) + ")...");
            String result = tryPiped(query);
            if (result != null) return result;

            log.info("Trying Invidious API (attempt " + (attempt + 1) + ")...");
            result = tryInvidious(query);
            if (result != null) return result;

            if (attempt == 0) {
                log.info("Piped fallback (PhantomJS) (attempt 1)...");
                result = tryPipedFallback(query);
                if (result != null) return result;
            }
        }

        log.warning("All APIs failed for: " + query);
        return null;
    }

    private static String callApiBridge(String baseUrl, String query, Logger log) {
        try {
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            boolean isUrl = query.startsWith("http://") || query.startsWith("https://");
            String endpoint = isUrl
                    ? "/url?url=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    : "/buscar?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(25))
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warning("API bridge returned HTTP " + resp.statusCode());
                return null;
            }

            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            String url = json.get("url_directa") != null ? json.get("url_directa").getAsString() : null;
            if (url != null && !url.isEmpty()) {
                String title = json.get("titulo") != null ? json.get("titulo").getAsString() : "";
                if (!title.isEmpty()) {
                    log.info("API bridge resolved: \"" + title + "\"");
                }
                return url;
            }
        } catch (java.net.http.HttpTimeoutException e) {
            log.warning("API bridge timeout: " + e.getMessage());
        } catch (java.net.ConnectException e) {
            log.warning("API bridge connection refused: " + e.getMessage());
        } catch (Exception e) {
            log.warning("API bridge error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return null;
    }

    private static String tryPiped(String query) {
        boolean isSearch = !query.startsWith("http://") && !query.startsWith("https://");

        for (String instance : PIPED_INSTANCES) {
            try {
                String videoId;

                if (isSearch) {
                    String json = httpGet(instance + "/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&filter=videos");
                    if (json == null) continue;

                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    JsonArray items = obj.getAsJsonArray("items");
                    if (items == null || items.size() == 0) continue;

                    JsonObject first = items.get(0).getAsJsonObject();
                    String url = first.get("url") != null ? first.get("url").getAsString() : null;
                    if (url == null) continue;

                    if (url.startsWith("/watch?v=")) {
                        videoId = url.substring(9);
                    } else {
                        videoId = extractVideoId(url);
                    }
                    int idx = videoId != null ? videoId.indexOf('&') : -1;
                    if (idx != -1) videoId = videoId.substring(0, idx);
                } else {
                    videoId = extractVideoId(query);
                }

                if (videoId == null || videoId.length() != 11) continue;

                String audioUrl = getPipedAudioUrl(instance, videoId);
                if (audioUrl != null) return audioUrl;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String tryPipedFallback(String query) {
        if (query.startsWith("http://") || query.startsWith("https://")) return null;

        for (String instance : PHANTOMJSEARCH_INSTANCES) {
            try {
                String json = httpGet(instance + "/api/v1/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
                if (json == null) continue;

                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                JsonArray results = obj.getAsJsonArray("results");
                if (results == null || results.size() == 0) continue;

                String videoUrl = results.get(0).getAsJsonObject().get("url") != null
                        ? results.get(0).getAsJsonObject().get("url").getAsString() : null;
                if (videoUrl == null) continue;

                String resolved = tryPiped(videoUrl);
                if (resolved != null) return resolved;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String getPipedAudioUrl(String instance, String videoId) {
        String json = httpGet(instance + "/streams/" + videoId);
        if (json == null) return null;

        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("error")) return null;

            JsonArray audioStreams = obj.getAsJsonArray("audioStreams");
            if (audioStreams == null || audioStreams.size() == 0) {
                JsonArray videoStreams = obj.getAsJsonArray("videoStreams");
                if (videoStreams != null && videoStreams.size() > 0) {
                    JsonObject best = null;
                    int bestQuality = 0;
                    for (int i = 0; i < videoStreams.size(); i++) {
                        try {
                            JsonObject vs = videoStreams.get(i).getAsJsonObject();
                            if (vs.has("quality") && !vs.get("quality").getAsString().contains("dash")) {
                                int quality = vs.get("quality").getAsInt();
                                if (quality > bestQuality) {
                                    bestQuality = quality;
                                    best = vs;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    if (best != null && best.has("url")) return best.get("url").getAsString();
                }
                return null;
            }

            JsonObject best = null;
            int bestBitrate = 0;
            for (int i = 0; i < audioStreams.size(); i++) {
                JsonObject as = audioStreams.get(i).getAsJsonObject();
                try {
                    int bitrate = as.get("bitrate").getAsInt();
                    if (bitrate > bestBitrate) {
                        bestBitrate = bitrate;
                        best = as;
                    }
                } catch (Exception ignored) {}
            }
            return best != null ? best.get("url").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String tryInvidious(String query) {
        boolean isSearch = !query.startsWith("http://") && !query.startsWith("https://");

        for (String instance : INVIDIOUS_INSTANCES) {
            try {
                String videoId;

                if (isSearch) {
                    String json = httpGet(instance + "/api/v1/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&type=video&page=1");
                    if (json == null || json.trim().equals("[]")) continue;

                    JsonArray results = JsonParser.parseString(json).getAsJsonArray();
                    if (results.size() == 0) continue;

                    videoId = results.get(0).getAsJsonObject().get("videoId") != null
                            ? results.get(0).getAsJsonObject().get("videoId").getAsString() : null;
                    if (videoId == null) continue;
                } else {
                    videoId = extractVideoId(query);
                    if (videoId == null) continue;
                }

                String json = httpGet(instance + "/api/v1/videos/" + videoId);
                if (json == null) continue;

                JsonObject info = JsonParser.parseString(json).getAsJsonObject();
                JsonArray formats = info.getAsJsonArray("adaptiveFormats");
                if (formats == null) continue;

                for (int i = 0; i < formats.size(); i++) {
                    JsonObject fmt = formats.get(i).getAsJsonObject();
                    if (fmt.get("type") == null) continue;
                    String type = fmt.get("type").getAsString();
                    if (type.startsWith("audio/")) {
                        String url = fmt.get("url") != null ? fmt.get("url").getAsString() : null;
                        if (url != null && !url.isEmpty()) return url.replace("\\u0026", "&");
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String httpGet(String urlStr) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripSearchPrefix(String query) {
        for (String prefix : new String[]{"ytsearch:", "ytmsearch:", "scsearch:", "ytmusic:"}) {
            if (query.startsWith(prefix)) {
                String stripped = query.substring(prefix.length());
                return stripped.isEmpty() ? query : stripped;
            }
        }
        return query;
    }

    private static String extractVideoId(String url) {
        if (url == null) return null;
        String[] patterns = {"v=", "youtu.be/", "embed/", "shorts/"};
        for (String p : patterns) {
            int idx = url.indexOf(p);
            if (idx != -1) {
                int start = idx + p.length();
                int end = url.indexOf("&", start);
                if (end == -1) end = url.indexOf("?", start);
                if (end == -1) end = url.length();
                String id = url.substring(start, end);
                if (id.length() == 11) return id;
            }
        }
        if (url.length() == 11 && url.matches("[\\w-]+")) return url;
        return null;
    }
}
