package com.monaysmp.monaykits;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class MonayKits extends JavaPlugin implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private final Map<UUID, Long> claims = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadClaims();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("MonayKits enabled. Five default kits are ready to configure.");
    }

    @Override
    public void onDisable() {
        saveClaims();
    }

    private void loadClaims() {
        ConfigurationSection section = data.getConfigurationSection("claims");
        if (section == null) return;
        for (String uuid : section.getKeys(false)) {
            try {
                claims.put(UUID.fromString(uuid), section.getLong(uuid));
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Skipping invalid UUID in data.yml: " + uuid);
            }
        }
    }

    private void saveClaims() {
        if (data == null) return;
        for (Map.Entry<UUID, Long> entry : claims.entrySet()) {
            data.set("claims." + entry.getKey(), entry.getValue());
        }
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            getLogger().severe("Could not save data.yml: " + exception.getMessage());
        }
    }

    private String color(String input) {
        return input == null ? "" : input.replace('&', '§');
    }

    private String prefix() {
        return color(getConfig().getString("settings.prefix", "&bMonayKits &8» "));
    }

    private List<String> kitKeys() {
        return List.of("starter", "og", "comedian", "booster", "monayplus");
    }

    private String displayName(String key) {
        return color(getConfig().getString("kits." + key + ".display-name", "&f" + key));
    }

    private boolean hasKit(String key) {
        return getConfig().isConfigurationSection("kits." + key);
    }

    private boolean bedrock(Player player) {
        if (!getConfig().getBoolean("settings.bedrock-fallback", true)) return false;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            return (boolean) apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, player.getUniqueId());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private void openMenu(Player player) {
        if (bedrock(player)) {
            openBedrockMenu(player);
        } else {
            try {
                openDialog(player);
            } catch (Throwable exception) {
                getLogger().warning("Native dialog unavailable for " + player.getName() + "; using inventory fallback: " + exception.getMessage());
                openBedrockMenu(player);
            }
        }
    }

    private void openDialog(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        for (String key : kitKeys()) {
            if (!hasKit(key)) continue;
            buttons.add(ActionButton.builder(Component.text("Claim " + plain(displayName(key))))
                    .tooltip(Component.text("Claim this kit if you have permission and its cooldown has ended."))
                    .action(DialogAction.customClick(Key.key("monaykits:claim_" + key), null))
                    .build());
            buttons.add(ActionButton.builder(Component.text("Preview " + plain(displayName(key))))
                    .tooltip(Component.text("View the contents of this kit."))
                    .action(DialogAction.customClick(Key.key("monaykits:preview_" + key), null))
                    .build());
        }
        buttons.add(ActionButton.builder(Component.text("Back / Close"))
                .action(DialogAction.customClick(Key.key("monaykits:close"), null)).build());
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("MONAYSMP | KITS"))
                        .body(List.of(DialogBody.plainMessage(Component.text("Choose a kit to claim or preview. Kit access and cooldowns are checked when claiming."))))
                        .build())
                .type(DialogType.multiAction(buttons)));
        player.showDialog(dialog);
    }

    private String plain(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "");
    }

    @EventHandler
    public void onDialogClick(PlayerCustomClickEvent event) {
        String namespace = event.getIdentifier().namespace();
        String action = event.getIdentifier().value();
        if (!namespace.equals("monaykits")) return;
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;
        Player player = connection.getPlayer();
        if (action.equals("close")) return;
        if (action.startsWith("claim_")) {
            claim(player, action.substring("claim_".length()));
        } else if (action.startsWith("preview_")) {
            preview(player, action.substring("preview_".length()));
        }
    }

    private void openBedrockMenu(Player player) {
        KitsHolder holder = new KitsHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, color(getConfig().getString("settings.title", "&b&lMONAYSMP &8» &fKits")));
        holder.inventory = inventory;
        int[] claimSlots = {10, 19, 28, 37, 46};
        int[] previewSlots = {12, 21, 30, 39, 48};
        List<String> keys = kitKeys();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (!hasKit(key)) continue;
            ItemStack claimItem = button(Material.LIME_DYE, "&a&lCLAIM", displayName(key), "&7Click to claim this kit");
            ItemStack previewItem = button(Material.BOOK, "&b&lPREVIEW", displayName(key), "&7View kit contents");
            inventory.setItem(claimSlots[i], claimItem);
            inventory.setItem(previewSlots[i], previewItem);
            holder.actions.put(claimSlots[i], "claim_" + key);
            holder.actions.put(previewSlots[i], "preview_" + key);
        }
        inventory.setItem(49, button(Material.BARRIER, "&cBack", "&7Close this menu"));
        holder.actions.put(49, "close");
        player.openInventory(inventory);
    }

    private ItemStack button(Material material, String... lines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (lines.length > 0) meta.displayName(LEGACY.deserialize(color(lines[0])));
        if (lines.length > 1) {
            List<Component> lore = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) lore.add(LEGACY.deserialize(color(lines[i])));
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof KitsHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String action = holder.actions.get(event.getRawSlot());
        if (action == null) return;
        player.closeInventory();
        if (action.equals("close")) return;
        if (action.startsWith("claim_")) claim(player, action.substring(6));
        if (action.startsWith("preview_")) preview(player, action.substring(8));
    }

    private void claim(Player player, String key) {
        if (!hasKit(key)) {
            player.sendMessage(prefix() + color("&cThat kit does not exist."));
            return;
        }
        String permission = getConfig().getString("kits." + key + ".permission", "monaykits.kit." + key);
        if (!player.hasPermission(permission) && !player.hasPermission("monaykits.admin")) {
            player.sendMessage(prefix() + color("&cYou don't have permission to claim ") + displayName(key) + color("&c."));
            return;
        }
        long cooldown = Math.max(0, getConfig().getLong("kits." + key + ".cooldown-seconds", 86400));
        String claimKey = player.getUniqueId() + "." + key;
        long last = data.getLong("last-claims." + claimKey, 0L);
        long remaining = cooldown - (System.currentTimeMillis() / 1000L - last);
        if (remaining > 0 && !player.hasPermission("monaykits.admin")) {
            player.sendMessage(prefix() + color("&cYou can claim this kit again in &e" + formatDuration(remaining) + "&c."));
            return;
        }
        List<?> configured = getConfig().getList("kits." + key + ".items", Collections.emptyList());
        int given = 0;
        for (Object object : configured) {
            if (!(object instanceof ItemStack stack) || stack.getType().isAir()) continue;
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack.clone());
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            given++;
        }
        List<?> armor = getConfig().getList("kits." + key + ".armor", Collections.emptyList());
        for (int i = 0; i < Math.min(armor.size(), 4); i++) {
            Object object = armor.get(i);
            if (object instanceof ItemStack stack && !stack.getType().isAir()) {
                ItemStack current = player.getInventory().getArmorContents()[i];
                if (current == null || current.getType().isAir()) {
                    ItemStack[] contents = player.getInventory().getArmorContents();
                    contents[i] = stack.clone();
                    player.getInventory().setArmorContents(contents);
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), stack.clone());
                }
            }
        }
        if (given == 0 && armor.isEmpty()) {
            player.sendMessage(prefix() + color("&eThis kit has no items configured yet. Ask staff to set it up."));
            return;
        }
        data.set("last-claims." + claimKey, System.currentTimeMillis() / 1000L);
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            getLogger().warning("Could not save claim cooldown: " + exception.getMessage());
        }
        player.sendMessage(prefix() + color("&aYou claimed ") + displayName(key) + color("&a!"));
    }

    private void preview(Player player, String key) {
        if (!hasKit(key)) {
            player.sendMessage(prefix() + color("&cThat kit does not exist."));
            return;
        }
        List<String> names = new ArrayList<>();
        for (Object object : getConfig().getList("kits." + key + ".items", Collections.emptyList())) {
            if (object instanceof ItemStack stack && !stack.getType().isAir()) {
                names.add(stack.getAmount() + "x " + stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
            }
        }
        for (Object object : getConfig().getList("kits." + key + ".armor", Collections.emptyList())) {
            if (object instanceof ItemStack stack && !stack.getType().isAir()) {
                names.add("Armor: " + stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
            }
        }
        player.sendMessage(prefix() + color("&fPreview: ") + displayName(key));
        if (names.isEmpty()) {
            player.sendMessage(color("&7No items configured yet."));
        } else {
            names.forEach(name -> player.sendMessage(color("&8 • &f" + name)));
        }
        player.sendMessage(color("&7Use &b/monaykits &7to return to the kit menu."));
    }

    private String formatDuration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + secs + "s";
        return secs + "s";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players can open the kit menu in-game.");
                return true;
            }
            if (!player.hasPermission("monaykits.use")) {
                player.sendMessage(prefix() + color("&cYou don't have permission."));
                return true;
            }
            openMenu(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("monaykits.admin")) {
                sender.sendMessage(prefix() + color("&cNo permission."));
                return true;
            }
            reloadConfig();
            sender.sendMessage(prefix() + color("&aConfiguration reloaded."));
            return true;
        }
        if (sub.equals("set")) {
            if (!(sender instanceof Player player) || !player.hasPermission("monaykits.admin")) {
                sender.sendMessage(prefix() + color("&cOnly admins in-game can set kits."));
                return true;
            }
            if (args.length < 2 || !kitKeys().contains(args[1].toLowerCase(Locale.ROOT))) {
                player.sendMessage(prefix() + color("&cUsage: /monaykits set <starter|og|comedian|booster|monayplus>"));
                return true;
            }
            String key = args[1].toLowerCase(Locale.ROOT);
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack stack : player.getInventory().getStorageContents()) {
                if (stack != null && !stack.getType().isAir()) items.add(stack.clone());
            }
            List<ItemStack> armor = new ArrayList<>();
            for (ItemStack stack : player.getInventory().getArmorContents()) {
                armor.add(stack == null ? new ItemStack(Material.AIR) : stack.clone());
            }
            getConfig().set("kits." + key + ".items", items);
            getConfig().set("kits." + key + ".armor", armor);
            saveConfig();
            player.sendMessage(prefix() + color("&aSaved your inventory and armor to ") + displayName(key) + color("&a."));
            return true;
        }
        if (sub.equals("claim") || sub.equals("preview")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used in-game.");
                return true;
            }
            if (args.length < 2 || !kitKeys().contains(args[1].toLowerCase(Locale.ROOT))) {
                player.sendMessage(prefix() + color("&cUsage: /monaykits " + sub + " <kit>"));
                return true;
            }
            String key = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("claim")) claim(player, key); else preview(player, key);
            return true;
        }
        sender.sendMessage(prefix() + color("&f/monaykits &7- open menu"));
        sender.sendMessage(color("&f/monaykits set <kit> &7- save your inventory as a kit"));
        sender.sendMessage(color("&f/monaykits claim <kit> &7- claim a kit"));
        sender.sendMessage(color("&f/monaykits preview <kit> &7- preview kit contents"));
        sender.sendMessage(color("&f/monaykits reload &7- reload config"));
        return true;
    }

    private static final class KitsHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> actions = new HashMap<>();
        @Override public Inventory getInventory() { return inventory; }
    }
}
