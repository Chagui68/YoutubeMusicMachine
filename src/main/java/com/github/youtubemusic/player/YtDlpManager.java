package com.github.youtubemusic.player;

import com.github.youtubemusic.main.YoutubeMusicMachineMain;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class YtDlpManager {

    private static Path binaryPath;
    private static volatile boolean downloading = false;
    private static Path cookiesPath;

    public static String resolve(String query) {
        YoutubeMusicMachineMain plugin = YoutubeMusicMachineMain.getInstance();
        try {
            ensureBinary(plugin);
            if (binaryPath == null || !Files.exists(binaryPath)) return null;

            cookiesPath = plugin.getDataFolder().toPath().resolve("cookies.txt");
            plugin.getLogger().info("Data folder: " + plugin.getDataFolder().toPath().toAbsolutePath().normalize());
            plugin.getLogger().info("cookies.txt path: " + cookiesPath.toAbsolutePath().normalize());
            plugin.getLogger().info("cookies.txt exists: " + (Files.exists(cookiesPath) && Files.isReadable(cookiesPath)));
            ensureCookies(plugin);

            String clean = stripSearchPrefix(query);
            String searchArg = clean.startsWith("http://") || clean.startsWith("https://")
                    ? clean
                    : "ytsearch1:" + clean;

            String[] clients = {
                    "android,include_dash_manifests=false",
                    "android_creator,include_dash_manifests=false",
                    "tv_embed,include_dash_manifests=false",
                    "android_music,include_dash_manifests=false",
                    "web,player_skip=webpage,include_dash_manifests=false"
            };

            for (String client : clients) {
                String result = tryResolve(plugin, searchArg, client);
                if (result != null) {
                    plugin.getLogger().info("yt-dlp resolved (" + query + ") using client: " + client);
                    return result;
                }
            }

            if (cookiesMissing()) {
                Path abs = cookiesPath.toAbsolutePath();
                plugin.getLogger().warning("=== YouTube requires authentication ===");
                plugin.getLogger().warning("cookies.txt NOT FOUND at: " + abs);
                plugin.getLogger().warning("Working dir: " + Path.of(".").toAbsolutePath().normalize());
                plugin.getLogger().warning("Parent dir exists: " + (cookiesPath.getParent() != null && Files.exists(cookiesPath.getParent())));
                plugin.getLogger().warning("1. Install 'cookies.txt' extension in your browser");
                plugin.getLogger().warning("2. Visit youtube.com and export cookies (Netscape format)");
                plugin.getLogger().warning("3. Upload the file to: " + abs);
                plugin.getLogger().warning("4. Run /playmusic again");
            } else {
                plugin.getLogger().warning("yt-dlp failed with cookies (" + cookiesPath.toAbsolutePath() + ", " + cookiesPath.toFile().length() + " bytes). Try exporting fresh cookies.");
            }

            plugin.getLogger().warning("yt-dlp: all clients failed for: " + query);
        } catch (Exception e) {
            plugin.getLogger().warning("yt-dlp error: " + e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "no message"));
        }
        return null;
    }

    private static boolean cookiesMissing() {
        return cookiesPath == null || !Files.exists(cookiesPath) || !Files.isReadable(cookiesPath);
    }

    private static String tryResolve(YoutubeMusicMachineMain plugin, String searchArg, String extractorArgs) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(binaryPath.toString());
            cmd.add("--no-playlist");
            cmd.add("-f");
            cmd.add("bestaudio[ext=m4a]/bestaudio");
            cmd.add("-g");
            cmd.add("--no-warnings");
            cmd.add("--geo-bypass");
            cmd.add("--extractor-args");
            cmd.add("youtube:" + extractorArgs);
            if (!cookiesMissing()) {
                cmd.add("--cookies");
                cmd.add(cookiesPath.toString());
            }
            cmd.add(searchArg);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            long start = System.currentTimeMillis();
            Process proc = pb.start();
            boolean finished = proc.waitFor(25, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            if (!finished) {
                proc.destroyForcibly();
                return null;
            }

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    line = line.trim();
                    if (line.startsWith("http://") || line.startsWith("https://")) {
                        return line;
                    }
                }
            }

            int exitCode = proc.exitValue();
            if (exitCode != 0 && output.length() > 0) {
                String out = output.toString().trim();
                if (out.contains("Sign in") || out.contains("bot") || out.contains("captcha")) {
                    return null;
                }
                plugin.getLogger().warning("yt-dlp exit=" + exitCode + " (" + elapsed + "ms) args=" + extractorArgs);
                plugin.getLogger().warning("yt-dlp output: " + out);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("yt-dlp tryResolve (" + extractorArgs + "): " + e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "no message"));
        }
        return null;
    }

    private static void ensureCookies(YoutubeMusicMachineMain plugin) {
        try {
            if (cookiesPath != null && Files.exists(cookiesPath) && cookiesPath.toFile().length() > 0) {
                plugin.getLogger().info("cookies.txt found (" + (cookiesPath.toFile().length() / 1024) + "KB)");
                return;
            }
        } catch (Exception e) {
            // ignore and continue
        }
        try (var in = plugin.getClass().getClassLoader().getResourceAsStream("cookies.txt")) {
            if (in != null) {
                plugin.getLogger().info("Extracting bundled cookies.txt from JAR...");
                Files.createDirectories(cookiesPath.getParent());
                Files.copy(in, cookiesPath, StandardCopyOption.REPLACE_EXISTING);
                long sz = Files.size(cookiesPath);
                if (sz > 100) {
                    plugin.getLogger().info("cookies.txt extracted (" + (sz / 1024) + "KB)");
                } else {
                    plugin.getLogger().warning("Bundled cookies.txt too small (" + sz + " bytes), ignoring");
                    Files.delete(cookiesPath);
                }
            } else {
                plugin.getLogger().info("cookies.txt not found at: " + cookiesPath);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error extracting cookies: " + e.getMessage());
        }
    }

    private static String stripSearchPrefix(String q) {
        for (String p : new String[]{"ytsearch:", "ytmsearch:", "scsearch:", "ytmusic:"}) {
            if (q.startsWith(p)) {
                String s = q.substring(p.length());
                return s.isEmpty() ? q : s;
            }
        }
        return q;
    }

    private static void ensureBinary(YoutubeMusicMachineMain plugin) {
        if (binaryPath != null && Files.exists(binaryPath)) return;
        if (downloading) return;
        downloading = true;

        try {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
            String suffix;
            String repoFile;

            if (os.contains("win")) {
                suffix = ".exe";
                repoFile = "yt-dlp.exe";
            } else if (os.contains("mac")) {
                suffix = "";
                repoFile = "yt-dlp_macos";
            } else {
                suffix = "";
                if (arch.contains("aarch64") || arch.contains("arm64")) {
                    repoFile = "yt-dlp_linux_aarch64";
                } else if (arch.contains("arm")) {
                    repoFile = "yt-dlp_linux_armv7l";
                } else {
                    repoFile = "yt-dlp_linux";
                }
            }

            Path dataDir = plugin.getDataFolder().toPath();
            Files.createDirectories(dataDir);
            Path targetPath = dataDir.resolve("yt-dlp" + suffix);

            if (Files.exists(targetPath)) {
                if (targetPath.toFile().setExecutable(true)) {
                    binaryPath = targetPath;
                    return;
                }
                Files.delete(targetPath);
            }

            String url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/" + repoFile;
            plugin.getLogger().info("Downloading yt-dlp from " + url);

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            Path tmp = Files.createTempFile("yt-dlp-", ".tmp");
            try {
                HttpResponse<Path> resp = client.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
                if (resp.statusCode() != 200) {
                    plugin.getLogger().warning("yt-dlp download HTTP " + resp.statusCode());
                    return;
                }
                Files.move(tmp, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }

            long size = Files.size(targetPath);
            if (size < 10_000_000) {
                plugin.getLogger().warning("yt-dlp too small (" + size + " bytes), likely corrupt");
                Files.delete(targetPath);
                binaryPath = null;
                return;
            }

            if (!suffix.equals(".exe")) {
                targetPath.toFile().setExecutable(true);
            }

            binaryPath = targetPath;
            plugin.getLogger().info("yt-dlp ready (" + (size / 1024 / 1024) + "MB) at " + targetPath);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to set up yt-dlp: " + e.getMessage());
            binaryPath = null;
        } finally {
            downloading = false;
        }
    }
}
