package com.ch.chunkloader;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LoaderListener implements Listener {
    private final CHChunkLoader plugin;

    public LoaderListener(CHChunkLoader plugin) {
        this.plugin = plugin;
    }

    /** 청크로더 GUI 홀더 */
    private static final class GuiHolder implements InventoryHolder {
        final UUID standId;
        Inventory inv;

        GuiHolder(UUID standId) {
            this.standId = standId;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private boolean isLoader(Entity e) {
        return e instanceof ArmorStand
                && e.getPersistentDataContainer().has(plugin.key(), PersistentDataType.STRING);
    }

    // ------------------------------------------------------------ 설치

    @EventHandler
    public void onPlace(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack loaderItem = plugin.loaderItem();
        ItemStack hand = e.getItem();
        if (loaderItem == null || hand == null || !hand.isSimilar(loaderItem)) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        Player p = e.getPlayer();
        if (!p.isSneaking() && clicked.getType().isInteractable()) return; // 상자 등은 평소대로 열기

        e.setCancelled(true);
        Block target = clicked.getRelative(e.getBlockFace());
        if (!target.getType().isAir() && !target.isPassable()) {
            p.sendMessage(CHChunkLoader.PREFIX + "§c여기에는 설치할 수 없습니다.");
            return;
        }
        Location loc = target.getLocation().add(0.5, 0, 0.5);
        loc.setYaw(0);
        loc.setPitch(0);

        ArmorStand stand = target.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setSilent(true);
            as.setBasePlate(false);
            as.setArms(false);
            as.setCanMove(false);
            as.setPersistent(true);
            as.getEquipment().setHelmet(new ItemStack(Material.BEACON));
            as.getPersistentDataContainer().set(plugin.key(), PersistentDataType.STRING, p.getUniqueId().toString());
        });

        if (p.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
        plugin.addLoader(new Loader(stand.getUniqueId(), target.getWorld().getName(),
                target.getX(), target.getY(), target.getZ(), p.getUniqueId()));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.2f);
        p.sendMessage(CHChunkLoader.PREFIX + "청크로더를 설치했습니다. §7(주변 3x3 청크가 계속 로드됩니다)");
    }

    // ------------------------------------------------------------ GUI

    @EventHandler
    public void onUse(PlayerInteractAtEntityEvent e) {
        if (!isLoader(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        GuiHolder h = new GuiHolder(e.getRightClicked().getUniqueId());
        Inventory inv = Bukkit.createInventory(h, 9, net.kyori.adventure.text.Component.text("청크로더"));
        h.inv = inv;
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(net.kyori.adventure.text.Component.text(" "));
        pane.setItemMeta(pm);
        for (int i = 0; i < 9; i++) inv.setItem(i, pane);

        ItemStack btn = new ItemStack(Material.BARRIER);
        ItemMeta bm = btn.getItemMeta();
        bm.displayName(legacy("§c청크로더 부수기"));
        bm.lore(List.of(legacy("§7클릭하면 청크로더를 철거하고"), legacy("§7아이템을 인벤토리로 돌려받습니다.")));
        btn.setItemMeta(bm);
        inv.setItem(4, btn);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.3f, 1f);
    }

    private static net.kyori.adventure.text.Component legacy(String s) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize(s)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof GuiHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getRawSlot() != 4) return;

        p.closeInventory();
        Loader l = plugin.loaders().get(h.standId);
        if (l != null && !p.getUniqueId().equals(l.owner) && !p.hasPermission("chunkloader.admin")) {
            p.sendMessage(CHChunkLoader.PREFIX + "§c설치한 사람만 철거할 수 있습니다.");
            return;
        }
        Entity stand = Bukkit.getEntity(h.standId);
        if (stand != null) stand.remove();
        if (l == null && stand == null) return; // 이미 철거됨
        plugin.removeLoader(h.standId);

        ItemStack back = plugin.loaderItem() == null ? null : plugin.loaderItem().clone();
        if (back != null) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(back);
            for (ItemStack rest : left.values()) p.getWorld().dropItem(p.getLocation(), rest);
        }
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.2f);
        p.sendMessage(CHChunkLoader.PREFIX + "청크로더를 철거하고 아이템을 돌려받았습니다.");
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof GuiHolder) e.setCancelled(true);
    }

    // ------------------------------------------------------------ 보호

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (isLoader(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void onManipulate(PlayerArmorStandManipulateEvent e) {
        if (isLoader(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        plugin.applyAll();
    }
}
