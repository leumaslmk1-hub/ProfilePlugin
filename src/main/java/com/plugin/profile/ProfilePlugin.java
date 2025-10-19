package com.plugin.profile.profileplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfilePlugin extends JavaPlugin implements Listener {
    
    private Map<UUID, PlayerProfile> profiles = new HashMap<>();
    private File profilesFolder;
    
    @Override
    public void onEnable() {
        // Créer le dossier de profils
        profilesFolder = new File(getDataFolder(), "profiles");
        if (!profilesFolder.exists()) {
            profilesFolder.mkdirs();
        }
        
        // Enregistrer les événements
        getServer().getPluginManager().registerEvents(this, this);
        
        // Charger les profils des joueurs en ligne
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadProfile(player);
        }
        
        getLogger().info("ProfilePlugin activé avec succès!");
    }
    
    @Override
    public void onDisable() {
        // Sauvegarder tous les profils
        for (UUID uuid : profiles.keySet()) {
            saveProfile(uuid);
        }
        getLogger().info("ProfilePlugin désactivé!");
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        loadProfile(player);
        
        PlayerProfile profile = profiles.get(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Bienvenue " + player.getName() + "!");
        player.sendMessage(ChatColor.YELLOW + "Rôle: " + profile.getRole());
        player.sendMessage(ChatColor.GRAY + "Temps de jeu: " + formatTime(profile.getPlaytime()));
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Mettre à jour le temps de jeu
        PlayerProfile profile = profiles.get(uuid);
        if (profile != null) {
            profile.updatePlaytime();
            saveProfile(uuid);
        }
        
        profiles.remove(uuid);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("profile")) {
            if (args.length == 0) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    showProfile(player, player);
                    return true;
                }
            } else if (args.length == 1) {
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Joueur introuvable!");
                    return true;
                }
                if (sender instanceof Player) {
                    showProfile((Player) sender, target);
                } else {
                    showProfile(null, target);
                }
                return true;
            }
        }
        
        if (command.getName().equalsIgnoreCase("setadmin")) {
            if (!sender.hasPermission("profile.admin")) {
                sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission!");
                return true;
            }
            
            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /setadmin <joueur>");
                return true;
            }
            
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Joueur introuvable!");
                return true;
            }
            
            PlayerProfile profile = profiles.get(target.getUniqueId());
            profile.setRole("ADMIN");
            saveProfile(target.getUniqueId());
            
            sender.sendMessage(ChatColor.GREEN + target.getName() + " est maintenant ADMIN!");
            target.sendMessage(ChatColor.GOLD + "Vous êtes maintenant ADMIN!");
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("removadmin")) {
            if (!sender.hasPermission("profile.admin")) {
                sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission!");
                return true;
            }
            
            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /removadmin <joueur>");
                return true;
            }
            
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Joueur introuvable!");
                return true;
            }
            
            PlayerProfile profile = profiles.get(target.getUniqueId());
            profile.setRole("JOUEUR");
            saveProfile(target.getUniqueId());
            
            sender.sendMessage(ChatColor.GREEN + target.getName() + " est maintenant JOUEUR!");
            target.sendMessage(ChatColor.YELLOW + "Vous êtes maintenant JOUEUR!");
            return true;
        }
        
        return false;
    }
    
    private void showProfile(Player viewer, Player target) {
        PlayerProfile profile = profiles.get(target.getUniqueId());
        if (profile == null) {
            if (viewer != null) {
                viewer.sendMessage(ChatColor.RED + "Profil introuvable!");
            }
            return;
        }
        
        String[] messages = {
            ChatColor.GOLD + "═══════════════════════════",
            ChatColor.AQUA + "Profil de " + ChatColor.WHITE + target.getName(),
            ChatColor.YELLOW + "Rôle: " + ChatColor.WHITE + profile.getRole(),
            ChatColor.YELLOW + "Temps de jeu: " + ChatColor.WHITE + formatTime(profile.getPlaytime()),
            ChatColor.YELLOW + "Première connexion: " + ChatColor.WHITE + profile.getFirstJoin(),
            ChatColor.GOLD + "═══════════════════════════"
        };
        
        if (viewer != null) {
            viewer.sendMessage(messages);
        } else {
            for (String msg : messages) {
                Bukkit.getConsoleSender().sendMessage(msg);
            }
        }
    }
    
    private void loadProfile(Player player) {
        UUID uuid = player.getUniqueId();
        File profileFile = new File(profilesFolder, uuid.toString() + ".yml");
        
        if (!profileFile.exists()) {
            // Créer un nouveau profil
            PlayerProfile profile = new PlayerProfile(player.getName(), player.hasPermission("profile.admin") ? "ADMIN" : "JOUEUR");
            profiles.put(uuid, profile);
            saveProfile(uuid);
        } else {
            // Charger le profil existant
            FileConfiguration config = YamlConfiguration.loadConfiguration(profileFile);
            PlayerProfile profile = new PlayerProfile(
                config.getString("name"),
                config.getString("role"),
                config.getLong("playtime"),
                config.getString("firstJoin")
            );
            profile.setJoinTime(System.currentTimeMillis());
            profiles.put(uuid, profile);
        }
    }
    
    private void saveProfile(UUID uuid) {
        PlayerProfile profile = profiles.get(uuid);
        if (profile == null) return;
        
        File profileFile = new File(profilesFolder, uuid.toString() + ".yml");
        FileConfiguration config = new YamlConfiguration();
        
        config.set("name", profile.getName());
        config.set("role", profile.getRole());
        config.set("playtime", profile.getPlaytime());
        config.set("firstJoin", profile.getFirstJoin());
        
        try {
            config.save(profileFile);
        } catch (IOException e) {
            getLogger().severe("Impossible de sauvegarder le profil de " + profile.getName());
            e.printStackTrace();
        }
    }
    
    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }
    
    // Classe interne pour les profils
    private class PlayerProfile {
        private String name;
        private String role;
        private long playtime;
        private String firstJoin;
        private long joinTime;
        
        public PlayerProfile(String name, String role) {
            this.name = name;
            this.role = role;
            this.playtime = 0;
            this.firstJoin = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date());
            this.joinTime = System.currentTimeMillis();
        }
        
        public PlayerProfile(String name, String role, long playtime, String firstJoin) {
            this.name = name;
            this.role = role;
            this.playtime = playtime;
            this.firstJoin = firstJoin;
        }
        
        public void updatePlaytime() {
            if (joinTime > 0) {
                long sessionTime = (System.currentTimeMillis() - joinTime) / 1000;
                playtime += sessionTime;
            }
        }
        
        public String getName() { return name; }
        public String getRole() { return role; }
        public long getPlaytime() { return playtime; }
        public String getFirstJoin() { return firstJoin; }
        
        public void setRole(String role) { this.role = role; }
        public void setJoinTime(long time) { this.joinTime = time; }
    }

}
