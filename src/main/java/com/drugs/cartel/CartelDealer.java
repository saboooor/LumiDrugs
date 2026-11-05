package com.drugs.cartel;

import com.drugs.DrugsV2;
import com.drugs.config.MechanicsConfig;
import com.drugs.drug.DrugRegistry;
import com.drugs.items.DrugItemMetadata;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

/** Persistent player NPC buyers; all inventory and economy operations run on the server thread. */
public final class CartelDealer implements Listener, CommandExecutor, TabCompleter {
    private final DrugsV2 plugin;
    private CartelNpcs npcs;
    private FileConfiguration config;
    private CartelEnforcer enforcer;
    private final Set<UUID> sellingDrops = new HashSet<>();

    public CartelDealer(DrugsV2 plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "cartel.yml");
        if (!file.exists()) plugin.saveResource("cartel.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void setNpcs(CartelNpcs npcs) { this.npcs = npcs; }

    CartelNpcs npcs() { return npcs; }

    void interactEnforcer(Player player, Entity entity) {
        if (enforcer != null) enforcer.interact(player, entity);
    }

    public void setEnforcer(CartelEnforcer enforcer) { this.enforcer = enforcer; }

    FileConfiguration settings() { return config; }

    private boolean isDealer(Entity entity) {
        return npcs != null && npcs.find(entity, "buyer") != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reject")) {
            if (args.length != 1) {
                sender.sendMessage("Usage: /cartel reject");
            } else if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can reject their cartel demand.");
            } else if (enforcer != null) {
                enforcer.reject(player);
            } else {
                sender.sendMessage(ChatColor.GRAY + "You have no active cartel demand to reject.");
            }
            return true;
        }
        if (!sender.hasPermission("drugs.cartel.admin")) {
            sender.sendMessage(ChatColor.RED + "You cannot manage cartel dealers.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("enforcer")) {
            return enforcer != null && enforcer.onCommand(sender, args);
        }
        if (args.length != 1) return false;
        if (args[0].equalsIgnoreCase("reload")) {
            config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "cartel.yml"));
            if (enforcer != null) enforcer.reload();
            if (npcs != null) npcs.reload();
            sender.sendMessage(ChatColor.GREEN + "Cartel prices and encounters reloaded.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Run this command in game.");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (npcs == null || npcs.create(player.getLocation(), "buyer") == null) {
                    player.sendMessage(ChatColor.RED + "Could not create buyer. Install and enable Citizens for your server version.");
                    return true;
                }
                player.sendMessage(ChatColor.GREEN + "Cartel buyer created. Throw drugs near the buyer to sell.");
            }
            case "remove" -> {
                Entity target = player.getTargetEntity(5);
                if (target != null && isDealer(target)) {
                    npcs.find(target, "buyer").destroy();
                    player.sendMessage(ChatColor.GREEN + "Cartel buyer removed.");
                } else player.sendMessage(ChatColor.RED + "Look directly at a cartel buyer within five blocks.");
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("drugs.cartel.admin")) {
            return sender instanceof Player && args.length == 1 && "reject".startsWith(args[0].toLowerCase(Locale.ROOT))
                    ? List.of("reject") : List.of();
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("enforcer")) {
            List<String> options = switch (args.length) {
                case 2 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                case 3 -> new ArrayList<>(DrugRegistry.getRegisteredDrugNames());
                case 4 -> List.of("16", "32", "64");
                default -> List.of();
            };
            return options.stream().filter(s -> s.toLowerCase(Locale.ROOT)
                    .startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length != 1) return List.of();
        return List.of("create", "remove", "reload", "enforcer", "reject").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }

    void interact(Player player, Entity entity) {
        if (!isDealer(entity)) return;
        if (!player.hasPermission("drugs.cartel.sell")) {
            player.sendMessage(ChatColor.RED + "You cannot sell to cartel buyers.");
            return;
        }
        String name = config.getString("npc.name", "Pablo").trim();
        if (name.isEmpty()) name = "Pablo";
        player.sendMessage(ChatColor.GREEN + name + ": Throw the goods to me, within two blocks, and I'll pay you.");
        var offer = offer(entity);
        if (offer != null) {
            long minutes = Math.max(1, (offer.changesAt() - System.currentTimeMillis() + 59_999) / 60_000);
            player.sendMessage(ChatColor.GOLD + "Current preference: " + offer.drug().replace('_', ' ')
                    + (offer.strain() == null ? "" : " (" + offer.strain() + ")")
                    + " — " + String.format(Locale.ROOT, "%.2fx", offer.multiplier())
                    + " normal price. Changes in " + minutes + " minutes.");
        }
    }

    private DealerPreferences.Offer offer(Entity entity) {
        var buyer = npcs == null ? null : npcs.find(entity, "buyer");
        return buyer == null ? null : DealerPreferences.current(config, buyer.id(), System.currentTimeMillis());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (isDealer(event.getEntity())) event.setCancelled(true);
    }

    boolean acceptDrop(Player player, Item item) {
        if (npcs == null || !item.isValid() || !player.hasPermission("drugs.cartel.sell")) return false;
        for (Entity entity : item.getNearbyEntities(2, 2, 2)) {
            if (isDealer(entity) && entity instanceof LivingEntity living && living.hasLineOfSight(item)
                    && entity.getLocation().distanceSquared(item.getLocation()) <= 4) {
                Payments payments = payments();
                if (payments == null) {
                    player.sendMessage(ChatColor.RED + "The buyer cannot pay right now. Your items remain on the ground.");
                } else sellDropped(player, item, item.getItemStack().getAmount(), payments, offer(entity));
                return true;
            }
        }
        return false;
    }

    Payments payments() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Vault")) return null;
        return VaultPayments.find();
    }

    static double price(ItemStack item, FileConfiguration prices) {
        if (item == null || !item.hasItemMeta() || item.getAmount() <= 0) return 0;
        ItemMeta meta = item.getItemMeta();
        String drug = DrugItemMetadata.getDrugId(meta);
        if (drug == null || !DrugRegistry.isDrugRegistered(drug)) return 0;
        // Reject equipment, planting stock, and partially used cartridges.
        if (!drug.equals(DrugItemMetadata.getItemType(meta))) return 0;
        Integer durability = DrugItemMetadata.getCartDurability(meta);
        if (drug.equals("cart") && (durability == null || durability != MechanicsConfig.getCartDurabilityUses())) return 0;
        double value = prices.getDouble("prices." + drug, 0);
        String strain = DrugItemMetadata.getStrainId(meta);
        if (strain != null) value = prices.getDouble("strain-prices." + drug + "." + strain, value);
        return Double.isFinite(value) && value > 0 ? value : 0;
    }

    int sellDropped(Player player, Item item, int limit, Payments payments) {
        // Enforcer delivery contracts retain their agreed base prices.
        return sellDropped(player, item, limit, payments, null);
    }

    int sellDropped(Player player, Item item, int limit, Payments payments, DealerPreferences.Offer offer) {
        if (!item.isValid() || limit <= 0 || !sellingDrops.add(item.getUniqueId())) return 0;
        int pickupDelay = item.getPickupDelay();
        try {
            ItemStack original = item.getItemStack();
            double unit = price(original, config);
            if (unit > 0 && offer != null && offer.matches(original)) unit *= offer.multiplier();
            int count = Math.min(limit, original.getAmount());
            double total = unit * count;
            if (unit == 0 || count <= 0 || !Double.isFinite(total)) return 0;
            // This is synchronous on the server thread. Block pickup while the provider runs;
            // mutate the stack only after a successful deposit, leaving failures untouched.
            item.setPickupDelay(Integer.MAX_VALUE);
            boolean paid;
            try {
                paid = payments.deposit(player, total);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Cartel payment failed: " + exception.getMessage());
                paid = false;
            }
            if (!paid) {
                player.sendMessage(ChatColor.RED + "Payment failed. Your items remain on the ground.");
                return 0;
            }
            if (count == original.getAmount()) item.remove();
            else {
                ItemStack remaining = original.clone();
                remaining.setAmount(original.getAmount() - count);
                item.setItemStack(remaining);
            }
            String formatted;
            try { formatted = payments.format(total); }
            catch (RuntimeException exception) { formatted = Double.toString(total); }
            player.sendMessage(ChatColor.GREEN + "Sold " + count + " items for " + formatted + ".");
            return count;
        } finally {
            if (item.isValid()) item.setPickupDelay(pickupDelay);
            sellingDrops.remove(item.getUniqueId());
        }
    }

    interface Payments {
        boolean deposit(Player player, double amount);
        String format(double amount);
    }

    // Isolated so loading the dealer without Vault installed does not resolve Vault classes.
    private static final class VaultPayments implements Payments {
        private final net.milkbowl.vault.economy.Economy economy;
        private VaultPayments(net.milkbowl.vault.economy.Economy economy) { this.economy = economy; }
        static Payments find() {
            var registration = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (registration == null || !registration.getProvider().isEnabled()) return null;
            return new VaultPayments(registration.getProvider());
        }
        public boolean deposit(Player player, double amount) {
            return economy.depositPlayer(player, amount).transactionSuccess();
        }
        public String format(double amount) { return economy.format(amount); }
    }

}
