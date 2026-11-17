package com.ch.chunkloader;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CHChunkLoader extends JavaPlugin implements CommandExecutor, TabCompleter {
    public static final String DEFAULT_TYPE = "default";

    private NamespacedKey key;
    private Lang lang;
    private final Map<UUID, Loader> loaders = new HashMap<>();
    private final Map<String, Integer> tickets = new HashMap<>();
    private File dataFile;

    public NamespacedKey key() {
        return key;
    }

    public Lang lang() {
        return lang;
    }

    // ------------------------------------------------------------ 청크로더 종류 (config.yml loaders)

    /** config.yml 에 등록된 청크로더 종류 이름들 */
    public Set<String> types() {
        ConfigurationSection sec = getConfig().getConfigurationSection("loaders");
        return sec == null ? new LinkedHashSet<>() : new LinkedHashSet<>(sec.getKeys(false));
    }

    public int maxRadius() {
        return Math.max(0, getConfig().getInt("max-radius", 8));
    }

    private int clamp(String type, int radius) {
        int max = maxRadius();
        if (radius > max) {
            getLogger().warning(lang.get("log-radius-clamped", "type", type, "radius", String.valueOf(radius), "max", String.valueOf(max)));
            return max;
        }
        return Math.max(0, radius);
    }

    /** 종류의 현재 radius. 종류가 없으면 fallback */
    public int radiusOf(String type, int fallback) {
        if (getConfig().isConfigurationSection("loaders." + type)) {
            return clamp(type, getConfig().getInt("loaders." + type + ".radius", 1));
        }
        return clamp(type, fallback);
    }

    public static String sizeText(int radius) {
        int n = radius * 2 + 1;
        return n + "x" + n;
    }

    /** 종류의 청크로더 아이템 (없으면 null) */
    public ItemStack typeItem(String type) {
        ItemStack it = getConfig().getItemStack("loaders." + type + ".item");
        return it == null ? null : it.clone();
    }

    /** 손에 든 아이템이 어느 종류의 청크로더 아이템인지 (아니면 null) */
    public String typeOfItem(ItemStack hand) {
        if (hand == null) return null;
        for (String t : types()) {
            ItemStack it = typeItem(t);
            if (it != null && hand.isSimilar(it)) return t;
        }
        return null;
    }

    public Map<UUID, Loader> loaders() {
        return loaders;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // 이전 버전(1.0) config 의 item -> loaders.default.item 으로 이전, 새 설정 항목 추가
        ItemStack legacyItem = getConfig().getItemStack("item");
        getConfig().options().copyDefaults(true);
        if (legacyItem != null && getConfig().getItemStack("loaders." + DEFAULT_TYPE + ".item") == null) {
            getConfig().set("loaders." + DEFAULT_TYPE + ".item", legacyItem);
        }
        getConfig().set("item", null);
        saveConfig();

        lang = new Lang(this);
        lang.load(getConfig().getString("language", "ko"));
        key = new NamespacedKey(this, "chunkloader");
        dataFile = new File(getDataFolder(), "loaders.yml");
        loadLoaders();
        applyAll();

        Bukkit.getPluginManager().registerEvents(new LoaderListener(this), this);
        if (getCommand("chunkloaderitem") != null) {
            getCommand("chunkloaderitem").setExecutor(this);
            getCommand("chunkloaderitem").setTabCompleter(this);
        }
        getLogger().info(lang.get("log-enabled", "count", String.valueOf(loaders.size()), "lang", lang.code()));
    }

    @Override
    public void onDisable() {
        saveLoaders();
    }

    // ------------------------------------------------------------ 명령어

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(lang.msg("player-only"));
            return true;
        }
        if (!p.hasPermission("chunkloader.admin")) {
            p.sendMessage(lang.msg("no-permission"));
            return true;
        }
        String type = args.length >= 1 ? args[0] : DEFAULT_TYPE;
        if (!types().contains(type)) {
            p.sendMessage(lang.msg("unknown-type", "type", type, "types", String.join(", ", types())));
            return true;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            p.sendMessage(lang.msg("hold-item"));
            return true;
        }
        ItemStack item = hand.clone();
        item.setAmount(1);
        getConfig().set("loaders." + type + ".item", item);
        saveConfig();
        p.sendMessage(lang.msg("item-set", "type", type, "size", sizeText(radiusOf(type, 1))));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String t : types()) {
                if (t.toLowerCase().startsWith(args[0].toLowerCase())) out.add(t);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ 청크 티켓 (3x3, 겹치면 카운트)

    private static String ticketKey(String world, int cx, int cz) {
        return world + ":" + cx + ":" + cz;
    }

    private void addTickets(Loader l, World w) {
        int cx = Math.floorDiv(l.x, 16);
        int cz = Math.floorDiv(l.z, 16);
        for (int dx = -l.radius; dx <= l.radius; dx++) {
            for (int dz = -l.radius; dz <= l.radius; dz++) {
                String k = ticketKey(l.world, cx + dx, cz + dz);
                int n = tickets.getOrDefault(k, 0);
                if (n == 0) w.addPluginChunkTicket(cx + dx, cz + dz, this);
                tickets.put(k, n + 1);
            }
        }
        l.active = true;
    }

    private void removeTickets(Loader l) {
        if (!l.active) return;
        World w = Bukkit.getWorld(l.world);
        int cx = Math.floorDiv(l.x, 16);
        int cz = Math.floorDiv(l.z, 16);
        for (int dx = -l.radius; dx <= l.radius; dx++) {
            for (int dz = -l.radius; dz <= l.radius; dz++) {
                String k = ticketKey(l.world, cx + dx, cz + dz);
                int n = tickets.getOrDefault(k, 0) - 1;
                if (n <= 0) {
                    tickets.remove(k);
                    if (w != null) w.removePluginChunkTicket(cx + dx, cz + dz, this);
                } else {
                    tickets.put(k, n);
                }
            }
        }
        l.active = false;
    }

    /** 월드가 로드되어 있는 청크로더에 티켓 적용 */
    public void applyAll() {
        for (Loader l : loaders.values()) {
            if (l.active) continue;
            World w = Bukkit.getWorld(l.world);
            if (w != null) addTickets(l, w);
        }
    }

    public void addLoader(Loader l) {
        loaders.put(l.standId, l);
        World w = Bukkit.getWorld(l.world);
        if (w != null) addTickets(l, w);
        saveLoaders();
    }

    public Loader removeLoader(UUID standId) {
        Loader l = loaders.remove(standId);
        if (l != null) removeTickets(l);
        saveLoaders();
        return l;
    }

    // ------------------------------------------------------------ 저장 / 불러오기

    private void loadLoaders() {
        if (!dataFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = y.getConfigurationSection("loaders");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(k);
            if (s == null) continue;
            try {
                UUID id = UUID.fromString(k);
                UUID owner = UUID.fromString(s.getString("owner", id.toString()));
                String type = s.getString("type", DEFAULT_TYPE);
                int radius = radiusOf(type, s.getInt("radius", 1));
                loaders.put(id, new Loader(id, s.getString("world", "world"),
                        s.getInt("x"), s.getInt("y"), s.getInt("z"), owner, type, radius));
            } catch (IllegalArgumentException ignored) {
                // 잘못된 항목 무시
            }
        }
    }

    public void saveLoaders() {
        YamlConfiguration y = new YamlConfiguration();
        for (Loader l : loaders.values()) {
            String b = "loaders." + l.standId;
            y.set(b + ".world", l.world);
            y.set(b + ".x", l.x);
            y.set(b + ".y", l.y);
            y.set(b + ".z", l.z);
            y.set(b + ".owner", l.owner.toString());
            y.set(b + ".type", l.type);
            y.set(b + ".radius", l.radius);
        }
        try {
            getDataFolder().mkdirs();
            y.save(dataFile);
        } catch (IOException e) {
            getLogger().severe(lang.get("log-save-failed", "error", String.valueOf(e)));
        }
    }
}
