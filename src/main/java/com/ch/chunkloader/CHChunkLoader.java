package com.ch.chunkloader;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CHChunkLoader extends JavaPlugin implements CommandExecutor {
    public static final String PREFIX = "§6[청크로더]§f ";

    private NamespacedKey key;
    private ItemStack loaderItem;
    private final Map<UUID, Loader> loaders = new HashMap<>();
    private final Map<String, Integer> tickets = new HashMap<>();
    private File dataFile;

    public NamespacedKey key() {
        return key;
    }

    public ItemStack loaderItem() {
        return loaderItem;
    }

    public Map<UUID, Loader> loaders() {
        return loaders;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        key = new NamespacedKey(this, "chunkloader");
        dataFile = new File(getDataFolder(), "loaders.yml");
        loaderItem = getConfig().getItemStack("item");
        loadLoaders();
        applyAll();

        Bukkit.getPluginManager().registerEvents(new LoaderListener(this), this);
        if (getCommand("손에든아이템청크로더설정") != null) {
            getCommand("손에든아이템청크로더설정").setExecutor(this);
        }
        getLogger().info("CH-ChunkLoader 활성화 (청크로더 " + loaders.size() + "개)");
    }

    @Override
    public void onDisable() {
        saveLoaders();
    }

    // ------------------------------------------------------------ 명령어

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(PREFIX + "플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (!p.hasPermission("chunkloader.admin")) {
            p.sendMessage(PREFIX + "§c권한이 없습니다.");
            return true;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            p.sendMessage(PREFIX + "§c청크로더로 쓸 아이템을 손에 들어주세요.");
            return true;
        }
        loaderItem = hand.clone();
        loaderItem.setAmount(1);
        getConfig().set("item", loaderItem);
        saveConfig();
        p.sendMessage(PREFIX + "손에 든 아이템을 §e청크로더 아이템§f으로 설정했습니다. 블록에 우클릭하면 설치됩니다.");
        return true;
    }

    // ------------------------------------------------------------ 청크 티켓 (3x3, 겹치면 카운트)

    private static String ticketKey(String world, int cx, int cz) {
        return world + ":" + cx + ":" + cz;
    }

    private void addTickets(Loader l, World w) {
        int cx = Math.floorDiv(l.x, 16);
        int cz = Math.floorDiv(l.z, 16);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
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
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
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
                loaders.put(id, new Loader(id, s.getString("world", "world"),
                        s.getInt("x"), s.getInt("y"), s.getInt("z"), owner));
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
        }
        try {
            getDataFolder().mkdirs();
            y.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("loaders.yml 저장 실패: " + e);
        }
    }
}
