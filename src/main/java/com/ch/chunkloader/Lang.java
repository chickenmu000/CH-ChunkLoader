package com.ch.chunkloader;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * lang.yml 메시지 로더.
 * 조회 순서 : 서버의 lang.yml(선택 언어) -> 내장 lang.yml(선택 언어) -> en -> ko -> 키 이름
 * (lang.yml 에 키가 빠져 있어도 내장 기본값으로 대체됩니다)
 */
public class Lang {
    private final JavaPlugin plugin;
    private YamlConfiguration user = new YamlConfiguration();
    private YamlConfiguration bundled = new YamlConfiguration();
    private String code = "ko";

    public Lang(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String code() {
        return code;
    }

    public void load(String requested) {
        File f = new File(plugin.getDataFolder(), "lang.yml");
        if (!f.exists()) plugin.saveResource("lang.yml", false);
        user = YamlConfiguration.loadConfiguration(f);
        try (InputStream in = plugin.getResource("lang.yml")) {
            if (in != null) {
                bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("bundled lang.yml read failed: " + e);
        }

        String c = requested == null ? "ko" : requested.trim().toLowerCase(Locale.ROOT);
        int cut = c.indexOf('_');
        if (cut < 0) cut = c.indexOf('-');
        if (cut > 0) c = c.substring(0, cut); // en_US -> en, zh-CN -> zh
        if (!user.isConfigurationSection(c) && !bundled.isConfigurationSection(c)) {
            plugin.getLogger().warning("Unknown language '" + requested + "', using 'en'.");
            c = "en";
        }
        code = c;
    }

    private String raw(String key) {
        for (String c : new String[]{code, "en", "ko"}) {
            String v = user.getString(c + "." + key);
            if (v != null) return v;
            v = bundled.getString(c + "." + key);
            if (v != null) return v;
        }
        return key;
    }

    /** 메시지 (& 색코드 변환, {이름} 자리표시자 치환). kv = "이름", "값", "이름", "값" ... */
    public String get(String key, String... kv) {
        String s = raw(key);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** 접두사가 붙은 메시지 */
    public String msg(String key, String... kv) {
        return get("prefix") + get(key, kv);
    }

    public List<String> list(String key, String... kv) {
        List<String> src = null;
        for (String c : new String[]{code, "en", "ko"}) {
            if (user.isList(c + "." + key)) {
                src = user.getStringList(c + "." + key);
                break;
            }
            if (bundled.isList(c + "." + key)) {
                src = bundled.getStringList(c + "." + key);
                break;
            }
        }
        List<String> out = new ArrayList<>();
        if (src == null) return out;
        for (String line : src) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                line = line.replace("{" + kv[i] + "}", kv[i + 1]);
            }
            out.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return out;
    }
}
