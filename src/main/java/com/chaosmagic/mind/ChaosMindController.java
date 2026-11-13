
package com.chaosmagic.mind;

import org.bukkit.Bukkit;
import org.bukkit.Color;
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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ChaosMindController extends JavaPlugin implements Listener {

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> debugging = new HashSet<>();

    private record Session(
        UUID mobId,
        Location home,
        GameMode oldMode,
        boolean oldAi
    ) {}

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        getLogger().info("Chaos Mind Controller 0.2 enabled");
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                release(player);
            }
        }
        sessions.clear();
        debugging.clear();
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("mindrelease")) {
            release(player);
            return true;
        }

        UUID id = player.getUniqueId();

        // DEBUG MODE
        if (args.length > 0 && args[0].equalsIgnoreCase("debug")) {
            if (!sessions.containsKey(id)) {
                player.sendMessage("§cPossess a mob first.");
                return true;
            }

            if (debugging.remove(id)) {
                player.sendMessage("§cInput debugging disabled.");
            } else {
                debugging.add(id);
                player.sendMessage("§aInput debugging enabled!");
                player.sendMessage("§7Press WASD and Space.");
            }
            return true;
        }

        // FORCED VELOCITY TEST
        if (args.length > 0 && args[0].equalsIgnoreCase("push")) {
            Session session = sessions.get(id);

            if (session == null) {
                player.sendMessage("§cPossess a mob first.");
                return true;
            }

            Entity entity = Bukkit.getEntity(session.mobId());

            if (entity instanceof Mob mob && mob.isValid()) {
                mob.setVelocity(new Vector(0, 1.2, 0));
                player.sendMessage("§aFORCED PUSH EXECUTED!");
                player.sendMessage("§7The mob should launch upward.");
            } else {
                player.sendMessage("§cPossessed mob not found.");
            }

            return true;
        }

        if (sessions.containsKey(id)) {
            player.sendMessage("§cAlready possessing.");
            return true;
        }

        Entity target = player.getTargetEntity(30);

        if (!(target instanceof Mob mob)) {
            player.sendMessage("§cLook at a mob within 30 blocks.");
            return true;
        }

        // Prevent two players controlling the same mob
        for (Session existing : sessions.values()) {
            if (existing.mobId().equals(mob.getUniqueId())) {
                player.sendMessage("§cThat mind is already occupied.");
                return true;
            }
        }

        Session session = new Session(
            mob.getUniqueId(),
            player.getLocation().clone(),
            player.getGameMode(),
            mob.hasAI()
        );

        sessions.put(id, session);

        mob.setAI(false);
        mob.setVelocity(new Vector(0, 0, 0));

        player.setGameMode(GameMode.SPECTATOR);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (sessions.containsKey(id)
                    && player.isOnline()
                    && mob.isValid()) {

                player.setSpectatorTarget(mob);

                player.sendMessage("§4Chaos Mind §cactivated!");
                player.sendMessage("§7WASD: Move | Space: Jump");
                player.sendMessage("§7/mindpossess debug");
                player.sendMessage("§7/mindpossess push");
                player.sendMessage("§7/mindrelease");
            }
        }, 2L);

        return true;
    }

    private void tick() {
        for (var entry : new ArrayList<>(sessions.entrySet())) {

            Player player = Bukkit.getPlayer(entry.getKey());
            Session session = entry.getValue();

            if (player == null || !player.isOnline()) {
                continue;
            }

            Entity entity = Bukkit.getEntity(session.mobId());

            if (!(entity instanceof Mob mob)
                    || !mob.isValid()
                    || mob.isDead()) {
                release(player);
                continue;
            }

            Input input = player.getCurrentInput();

            // DEBUG: DISPLAY ACTUAL JAVA INPUT
            if (debugging.contains(player.getUniqueId())) {
                if (Bukkit.getCurrentTick() % 10 == 0) {

                    String status =
                        "§cW:" + input.isForward()
                        + " A:" + input.isLeft()
                        + " S:" + input.isBackward()
                        + " D:" + input.isRight()
                        + " J:" + input.isJump()
                        + " AI:" + mob.hasAI();

                    player.sendActionBar(status);
                }
            }

            // MOVEMENT
            double yaw = Math.toRadians(mob.getLocation().getYaw());

            Vector forward = new Vector(
                -Math.sin(yaw), 0, Math.cos(yaw)
            );

            Vector right = new Vector(
                -forward.getZ(), 0, forward.getX()
            );

            Vector movement = new Vector(0, 0, 0);

            if (input.isForward()) movement.add(forward);
            if (input.isBackward()) movement.subtract(forward);
            if (input.isRight()) movement.add(right);
            if (input.isLeft()) movement.subtract(right);

            if (movement.lengthSquared() > 0) {
                movement.normalize().multiply(
                    input.isSprint() ? 0.38 : 0.24
                );
            }

            double vertical = mob.getVelocity().getY();

            if (input.isJump() && mob.isOnGround()) {
                vertical = 0.43;
            }

            movement.setY(vertical);
            mob.setVelocity(movement);

            // CRIMSON POSSESSION PARTICLES
            if (Bukkit.getCurrentTick() % 4 == 0) {
                mob.getWorld().spawnParticle(
                    Particle.DUST,
                    mob.getEyeLocation(),
                    3,
                    0.22, 0.2, 0.22,
                    new Particle.DustOptions(
                        Color.fromRGB(190, 10, 40),
                        0.9f
                    )
                );
            }

            // SNEAK ATTACK
            if (input.isSneak()
                    && Bukkit.getCurrentTick() % 12 == 0) {

                for (Entity nearby : mob.getNearbyEntities(
                        2.3, 1.7, 2.3)) {

                    if (nearby instanceof LivingEntity victim
                            && !nearby.equals(player)
                            && !nearby.equals(mob)) {

                        victim.damage(3.0, player);
                        break;
                    }
                }
            }
        }
    }

    private void release(Player player) {
        UUID id = player.getUniqueId();

        Session session = sessions.remove(id);
        debugging.remove(id);

        if (session == null) {
            player.sendMessage("§cNot possessing a mob.");
            return;
        }

        Entity entity = Bukkit.getEntity(session.mobId());

        if (entity instanceof Mob mob && mob.isValid()) {
            mob.setAI(session.oldAi());
        }

        player.setSpectatorTarget(null);
        player.setGameMode(session.oldMode());
        player.teleport(session.home());

        player.sendMessage("§cReturned to your own mind.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) {
            release(event.getPlayer());
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        for (var entry : new ArrayList<>(sessions.entrySet())) {
            if (entry.getValue().mobId()
                    .equals(event.getEntity().getUniqueId())) {

                Player player = Bukkit.getPlayer(entry.getKey());

                if (player != null) {
                    release(player);
                }
            }
        }
    }
}
