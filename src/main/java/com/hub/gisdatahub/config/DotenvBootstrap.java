package com.hub.gisdatahub.config;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * .env 위치 탐색 및 적용. DevTools {@code RestartLauncher} 경로에서는
 * {@link org.springframework.boot.env.EnvironmentPostProcessor}만으로는 값이 비는 경우가 있어
 * {@code main}에서도 {@link #applySystemProperties()}를 호출합니다.
 */
public final class DotenvBootstrap {

    private DotenvBootstrap() {
    }

    public static Path resolveDotenvDirectory(Class<?> anchor) {
        Path fromUser = walkForEnvFile(Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize());
        if (fromUser != null) {
            return fromUser;
        }
        Path fromCode = walkFromCodeSource(anchor);
        if (fromCode != null) {
            return fromCode;
        }
        return Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }

    private static Path walkForEnvFile(Path start) {
        Path p = start;
        for (int i = 0; i < 8; i++) {
            if (Files.isRegularFile(p.resolve(".env"))) {
                return p;
            }
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) {
                break;
            }
            p = parent;
        }
        return null;
    }

    private static Path walkFromCodeSource(Class<?> anchor) {
        try {
            URL url = anchor.getProtectionDomain().getCodeSource().getLocation();
            if (url == null) {
                return null;
            }
            Path path = Paths.get(url.toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                path = path.getParent();
            }
            for (int i = 0; i < 16; i++) {
                if (path != null && Files.isRegularFile(path.resolve(".env"))) {
                    return path;
                }
                if (path == null) {
                    break;
                }
                Path parent = path.getParent();
                if (parent == null || parent.equals(path)) {
                    break;
                }
                path = parent;
            }
        } catch (URISyntaxException ignored) {
            // fall through
        }
        return null;
    }

    /**
     * Spring 기동 전에 호출 — {@code application.yml}의 {@code ${JWT_SECRET}} 등이 시스템 프로퍼티로 해석됩니다.
     */
    public static void applySystemProperties(Class<?> anchor) {
        Path dir = resolveDotenvDirectory(anchor);
        Dotenv dotenv = Dotenv.configure()
                .directory(dir.toString())
                .ignoreIfMissing()
                .load();
        dotenv.entries().forEach(e -> {
            String key = e.getKey();
            String value = e.getValue();
            if (value == null || key == null) {
                return;
            }
            if (System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, value);
            }
        });
    }

    public static Map<String, Object> loadPropertyMap(Class<?> anchor) {
        Path dir = resolveDotenvDirectory(anchor);
        Dotenv dotenv = Dotenv.configure()
                .directory(dir.toString())
                .ignoreIfMissing()
                .load();
        Map<String, Object> map = new HashMap<>();
        dotenv.entries().forEach(e -> {
            if (e.getValue() != null && e.getKey() != null) {
                map.put(e.getKey(), e.getValue());
            }
        });
        return map;
    }
}
