package com.Lino.moneyMobs;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Random;

public class MoneyMobs extends JavaPlugin implements Listener {

    private Economy economy;
    private double minMoney;
    private double maxMoney;
    private Sound pickupSound;
    private float soundVolume;
    private float soundPitch;
    private String moneyFormat;
    private String pickupMessage;
    private boolean dropEnabled;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        loadConfiguration();

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MoneyMobs has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MoneyMobs has been disabled!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private void loadConfiguration() {
        FileConfiguration config = getConfig();
        minMoney = config.getDouble("money.min", 1.0);
        maxMoney = config.getDouble("money.max", 10.0);
        pickupSound = Sound.valueOf(config.getString("sound.type", "ENTITY_EXPERIENCE_ORB_PICKUP"));
        soundVolume = (float) config.getDouble("sound.volume", 1.0);
        soundPitch = (float) config.getDouble("sound.pitch", 1.0);
        moneyFormat = config.getString("format.money", "##.##");
        pickupMessage = config.getString("messages.pickup", "&aYou picked up &e$%money%&a!");
        dropEnabled = config.getBoolean("enabled", true);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!dropEnabled || event.getEntity().getKiller() == null) {
            return;
        }

        Player killer = event.getEntity().getKiller();
        if (!killer.hasPermission("moneymobs.earn")) {
            return;
        }

        Random random = new Random();
        double amount = minMoney + (maxMoney - minMoney) * random.nextDouble();
        amount = Double.parseDouble(new DecimalFormat(moneyFormat).format(amount).replace(',', '.'));

        ItemStack goldNugget = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = goldNugget.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "$" + new DecimalFormat(moneyFormat).format(amount));
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Money Drop"));
        meta.getPersistentDataContainer().set(
                getNamespacedKey("money_value"),
                PersistentDataType.DOUBLE,
                amount
        );
        goldNugget.setItemMeta(meta);

        Item droppedItem = event.getEntity().getWorld().dropItem(event.getEntity().getLocation(), goldNugget);
        droppedItem.setGlowing(true);
        droppedItem.setCustomName(ChatColor.GOLD + "$" + new DecimalFormat(moneyFormat).format(amount));
        droppedItem.setCustomNameVisible(true);
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        ItemStack item = event.getItem().getItemStack();

        if (item.getType() != Material.GOLD_NUGGET || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(getNamespacedKey("money_value"), PersistentDataType.DOUBLE)) {
            return;
        }

        double amount = meta.getPersistentDataContainer().get(
                getNamespacedKey("money_value"),
                PersistentDataType.DOUBLE
        );

        event.setCancelled(true);
        event.getItem().remove();

        economy.depositPlayer(player, amount);

        player.playSound(player.getLocation(), pickupSound, soundVolume, soundPitch);

        String message = ChatColor.translateAlternateColorCodes('&', pickupMessage)
                .replace("%money%", new DecimalFormat(moneyFormat).format(amount));
        player.sendMessage(message);
    }

    private org.bukkit.NamespacedKey getNamespacedKey(String key) {
        return new org.bukkit.NamespacedKey(this, key);
    }
}