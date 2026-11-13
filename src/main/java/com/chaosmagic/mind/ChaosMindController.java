
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

    private static final class Session {
        UUID mobId;
        Location home;
        GameMode oldMode;
        boolean oldAi;
        int pushTicks = 0;
        int jumpCooldown = 0;
        int attackCooldown = 0;

        Session(Mob mob, Player player) {
            mobId = mob.getUniqueId();
            home = player.getLocation().clone();
            oldMode = player.getGameMode();
            oldAi = mob.hasAI();
        }
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        getLogger().info("Chaos Mind Controller 0.3 enabled");
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) release(p);
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
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        UUID id = p.getUniqueId();

        if (command.getName().equalsIgnoreCase("mindrelease")) {
            release(p);
            return true;
        }

        Session s = sessions.get(id);

        if (args.length > 0) {
            String action = args[0].toLowerCase();

            if (action.equals("debug")) {
                if (s == null) {
                    p.sendMessage("§cPossess a mob first.");
                    return true;
                }

                if (!debugging.add(id)) {
                    debugging.remove(id);
                    p.sendMessage("§cDebug disabled.");
                } else {
                    p.sendMessage("§aDebug enabled.");
                }
                return true;
            }

            if (action.equals("push")) {
                if (s == null) {
                    p.sendMessage("§cPossess a mob first.");
                    return true;
                }

                Entity e = Bukkit.getEntity(s.mobId);

                if (e instanceof Mob mob && mob.isValid()) {
                    s.pushTicks = 25;
                    mob.setVelocity(new Vector(0, 1.2, 0));
                    p.sendMessage("§aPush applied for testing.");
                }
                return true;
            }

            if (action.equals("step")) {
                if (s == null) {
                    p.sendMessage("§cPossess a mob first.");
                    return true;
                }

                Entity e = Bukkit.getEntity(s.mobId);

                if (e instanceof Mob mob && mob.isValid()) {
                    Location destination = mob.getLocation().clone();
                    destination.add(0, 2, 0);

                    if (isSafeSpace(destination, mob)) {
                        boolean success = mob.teleport(destination);
                        p.sendMessage(
                            success
                                ? "§aTeleport step succeeded."
                                : "§cTeleport step failed."
                        );
                    } else {
                        p.sendMessage("§cNo clear space above mob.");
                    }
                }
                return true;
            }
        }

        if (s != null) {
            p.sendMessage("§cAlready possessing. /mindrelease to exit.");
            return true;
        }

        Entity target = p.getTargetEntity(30);

        if (!(target instanceof Mob mob)) {
            p.sendMessage("§cLook directly at a mob within 30 blocks.");
            return true;
        }

        for (Session existing : sessions.values()) {
            if (existing.mobId.equals(mob.getUniqueId())) {
                p.sendMessage("§cThat mob is already possessed.");
                return true;
            }
        }

        Session session = new Session(mob, p);
        sessions.put(id, session);

        mob.setAI(false);
        mob.setVelocity(new Vector(0, 0, 0));

        p.setGameMode(GameMode.SPECTATOR);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (sessions.get(id) == session
                    && p.isOnline()
                    && mob.isValid()) {

                p.setSpectatorTarget(mob);

                p.sendMessage("§4Chaos Mind 0.3 §cactivated!");
                p.sendMessage("§7WASD: Move | Space: Jump | Shift: Attack");
                p.sendMessage("§7/mindpossess debug");
                p.sendMessage("§7/mindpossess push");
                p.sendMessage("§7/mindpossess step");
                p.sendMessage("§7/mindrelease");
            }
        }, 2L);

        return true;
    }

    private boolean isSafeSpace(Location loc, Mob mob) {
        if (!loc.getBlock().isPassable()) return false;

        // Check head space for taller mobs.
        if (mob.getHeight() > 1.0) {
            Location head = loc.clone().add(0, 1, 0);
            if (!head.getBlock().isPassable()) return false;
        }

        return true;
    }

    private void tick() {
        for (var entry : new ArrayList<>(sessions.entrySet())) {

            Player player = Bukkit.getPlayer(entry.getKey());
            Session s = entry.getValue();

            if (player == null || !player.isOnline()) continue;

            Entity entity = Bukkit.getEntity(s.mobId);

            if (!(entity instanceof Mob mob)
                    || !mob.isValid()
                    || mob.isDead()) {
                release(player);
                continue;
            }

            // Keep the mob's AI disabled during possession.
            if (mob.hasAI()) mob.setAI(false);

            Input input = player.getCurrentInput();

            if (s.jumpCooldown > 0) s.jumpCooldown--;
            if (s.attackCooldown > 0) s.attackCooldown--;

            if (debugging.contains(player.getUniqueId())
                    && Bukkit.getCurrentTick() % 10 == 0) {

                player.sendActionBar(
                    "§cW:" + input.isForward()
                    + " A:" + input.isLeft()
                    + " S:" + input.isBackward()
                    + " D:" + input.isRight()
                    + " J:" + input.isJump()
                    + " AI:" + mob.hasAI()
                    + " P:" + s.pushTicks
                );
            }

            // Let the forced velocity test run without interference.
            if (s.pushTicks > 0) {
                s.pushTicks--;
                continue;
            }

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

            // Horizontal movement by controlled repositioning.
            if (movement.lengthSquared() > 0) {
                double speed = input.isSprint() ? 0.28 : 0.18;

                movement.normalize().multiply(speed);

                Location destination = mob.getLocation().clone();
                destination.add(movement);

                if (isSafeSpace(destination, mob)) {
                    mob.teleport(destination);
                }
            }

            // Jump using velocity, without immediately overwriting it.
            if (input.isJump()
                    && mob.isOnGround()
                    && s.jumpCooldown == 0) {

                Vector velocity = mob.getVelocity();
                velocity.setY(0.48);
                mob.setVelocity(velocity);

                s.jumpCooldown = 12;
            }

            // Shift attack (spectator left-click is unreliable).
            if (input.isSneak() && s.attackCooldown == 0) {
                s.attackCooldown = 12;

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
        }
    }

    private void release(Player p) {
        UUID id = p.getUniqueId();
        Session s = sessions.remove(id);
        debugging.remove(id);

        if (s == null) {
            p.sendMessage("§cNot possessing a mob.");
            return;
        }

        Entity entity = Bukkit.getEntity(s.mobId);

        if (entity instanceof Mob mob && mob.isValid()) {
            mob.setAI(s.oldAi);
        }

        p.setSpectatorTarget(null);
        p.setGameMode(s.oldMode);
        p.teleport(s.home);

        p.sendMessage("§cReturned to your own mind.");
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
            if (entry.getValue().mobId.equals(
                    event.getEntity().getUniqueId())) {

                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null) release(p);
            }
        }
    }
}
