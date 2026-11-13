package com.chaosmagic.mind;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ChaosMindController extends JavaPlugin implements Listener {
    private final Map<UUID, Session> sessions = new HashMap<>();
    private record Session(UUID mobId, Location home, GameMode oldMode, boolean oldAi) {}

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        getLogger().info("Chaos Mind test controller enabled");
    }
    @Override public void onDisable() {
        for (UUID id : new java.util.ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) release(p);
            else sessions.remove(id);
        }
    }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (command.getName().equalsIgnoreCase("mindrelease")) {
            release(p); return true;
        }
        if (sessions.containsKey(p.getUniqueId())) {
            p.sendMessage("§cAlready possessing. Use /mindrelease."); return true;
        }
        // This test only targets mobs (not players) and requires clear line of sight.
        Entity entity = p.getTargetEntity(30);
        if (!(entity instanceof Mob mob)) {
            p.sendMessage("§cLook directly at a mob within 30 blocks."); return true;
        }
        Session session = new Session(mob.getUniqueId(), p.getLocation().clone(), p.getGameMode(), mob.hasAI());
        sessions.put(p.getUniqueId(), session);
        mob.setAI(false);
        p.setGameMode(GameMode.SPECTATOR);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (sessions.containsKey(p.getUniqueId()) && p.isOnline() && mob.isValid()) {
                p.setSpectatorTarget(mob);
                p.sendMessage("§4Chaos Mind §cpossessing! WASD to move, Space to jump, Shift to strike. /mindrelease to exit.");
            }
        }, 2L);
        return true;
    }
    private void tick() {
        for (var entry : new java.util.ArrayList<>(sessions.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Session session = entry.getValue();
            Entity e = Bukkit.getEntity(session.mobId());
            if (player == null || !player.isOnline()) continue;
            if (!(e instanceof Mob mob) || !mob.isValid() || mob.isDead()) {
                release(player); continue;
            }
            Input in = player.getCurrentInput();
            // Use mob yaw while spectating. The camera may be locked to mob facing.
            double yaw = Math.toRadians(mob.getLocation().getYaw());
            Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
            Vector right = new Vector(-forward.getZ(), 0, forward.getX());
            Vector move = new Vector(0, 0, 0);
            if (in.isForward()) move.add(forward);
            if (in.isBackward()) move.subtract(forward);
            if (in.isRight()) move.add(right);
            if (in.isLeft()) move.subtract(right);
            if (move.lengthSquared() > 0) move.normalize().multiply(in.isSprint() ? 0.38 : 0.24);
            double y = mob.getVelocity().getY();
            if (in.isJump() && mob.isOnGround()) y = 0.43;
            move.setY(y);
            mob.setVelocity(move);
            if (Bukkit.getCurrentTick() % 4 == 0) {
                mob.getWorld().spawnParticle(Particle.DUST, mob.getEyeLocation(), 3,
                    0.22, 0.2, 0.22, new Particle.DustOptions(org.bukkit.Color.fromRGB(190, 10, 40), 0.9f));
            }
            // Sneak as an attack trigger because spectator left-click events may not fire.
            if (in.isSneak() && Bukkit.getCurrentTick() % 12 == 0) {
                for (Entity nearby : mob.getNearbyEntities(2.3, 1.7, 2.3)) {
                    if (nearby instanceof LivingEntity victim && !nearby.equals(player) && !nearby.equals(mob)) {
                        victim.damage(3.0, player);
                        break;
                    }
                }
            }
        }
    }
    private void release(Player p) {
        Session session = sessions.remove(p.getUniqueId());
        if (session == null) { p.sendMessage("§cNot possessing a mob."); return; }
        Entity entity = Bukkit.getEntity(session.mobId());
        if (entity instanceof Mob mob && mob.isValid()) mob.setAI(session.oldAi());
        p.setSpectatorTarget(null);
        p.setGameMode(session.oldMode());
        p.teleport(session.home());
        p.sendMessage("§cReturned to your own mind.");
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { release(e.getPlayer()); }
    @EventHandler public void onDeath(EntityDeathEvent e) {
        for (var entry : new java.util.ArrayList<>(sessions.entrySet())) {
            if (entry.getValue().mobId().equals(e.getEntity().getUniqueId())) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null) release(p);
            }
        }
    }
}
