
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
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ChaosMindController extends JavaPlugin implements Listener {

    // Movement settings
    private static final double WALK_SPEED = 0.34;
    private static final double SPRINT_SPEED = 0.48;
    private static final double JUMP_POWER = 0.43;
    private static final double GRAVITY = 0.08;
    private static final double TERMINAL_SPEED = -1.5;

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> debugging = new HashSet<>();

    private static final class Session {
        UUID mobId;
        Location home;
        GameMode oldMode;
        boolean oldAi;

        double verticalSpeed = 0;
        boolean jumpHeld = false;
        int attackCooldown = 0;
        int pushTicks = 0;

        Session(Player player, Mob mob) {
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
        getLogger().info("Chaos Mind Controller 0.4 enabled");
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

        UUID id = player.getUniqueId();

        if (command.getName().equalsIgnoreCase("mindrelease")) {
            release(player);
            return true;
        }

        Session session = sessions.get(id);

        if (args.length > 0) {
            String action = args[0].toLowerCase();

            if (action.equals("debug")) {
                if (session == null) {
                    player.sendMessage("§cPossess a mob first.");
                    return true;
                }

                if (debugging.add(id)) {
                    player.sendMessage("§aChaos Mind debug enabled.");
                } else {
                    debugging.remove(id);
                    player.sendMessage("§cChaos Mind debug disabled.");
                }
                return true;
            }

            if (action.equals("push")) {
                if (session == null) {
                    player.sendMessage("§cPossess a mob first.");
                    return true;
                }

                session.verticalSpeed = 0.8;
                session.pushTicks = 10;
                player.sendMessage("§aTelekinetic upward push!");
                return true;
            }

            if (action.equals("step")) {
                if (session == null) {
                    player.sendMessage("§cPossess a mob first.");
                    return true;
                }

                Entity entity = Bukkit.getEntity(session.mobId);

                if (entity instanceof Mob mob && mob.isValid()) {
                    Location destination = mob.getLocation().add(0, 2, 0);

                    if (canOccupy(mob, destination)) {
                        mob.teleport(destination);
                        session.verticalSpeed = 0;
                        player.sendMessage("§aStep test succeeded.");
                    } else {
                        player.sendMessage("§cNot enough space.");
                    }
                }
                return true;
            }
        }

        if (session != null) {
            player.sendMessage("§cAlready possessing a mob.");
            return true;
        }

        Entity target = player.getTargetEntity(30);

        if (!(target instanceof Mob mob)) {
            player.sendMessage("§cLook at a mob within 30 blocks.");
            return true;
        }

        for (Session existing : sessions.values()) {
            if (existing.mobId.equals(mob.getUniqueId())) {
                player.sendMessage("§cThat mob is already possessed.");
                return true;
            }
        }

        Session newSession = new Session(player, mob);
        sessions.put(id, newSession);

        mob.setAI(false);
        mob.setGravity(false);
        mob.setVelocity(new Vector(0, 0, 0));

        player.setGameMode(GameMode.SPECTATOR);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (sessions.get(id) == newSession
                    && player.isOnline()
                    && mob.isValid()) {

                player.setSpectatorTarget(mob);

                player.sendMessage("§4Chaos Mind 0.4 §cactivated!");
                player.sendMessage("§7WASD: Move | Sprint: Faster");
                player.sendMessage("§7Space: Jump | Shift: Attack");
                player.sendMessage("§7/mindrelease to exit.");
            }
        }, 2L);

        return true;
    }

    // Checks the mob's collision box at a proposed location.
    private boolean canOccupy(Mob mob, Location destination) {
        if (destination.getWorld() == null) return false;

        BoundingBox box = mob.getBoundingBox();
        Location current = mob.getLocation();

        double dx = destination.getX() - current.getX();
        double dy = destination.getY() - current.getY();
        double dz = destination.getZ() - current.getZ();

        BoundingBox moved = box.clone().shift(dx, dy, dz);

        return !destination.getWorld().hasCollisions(mob, moved);
    }

    // Detects solid ground directly underneath the mob.
    private boolean isGrounded(Mob mob) {
        Location below = mob.getLocation().clone();
        below.subtract(0, 0.06, 0);

        return !canOccupy(mob, below);
    }

    private void tick() {
        for (var entry : new ArrayList<>(sessions.entrySet())) {

            Player player = Bukkit.getPlayer(entry.getKey());
            Session session = entry.getValue();

            if (player == null || !player.isOnline()) {
                continue;
            }

            Entity entity = Bukkit.getEntity(session.mobId);

            if (!(entity instanceof Mob mob)
                    || !mob.isValid()
                    || mob.isDead()) {
                release(player);
                continue;
            }

            if (mob.hasAI()) mob.setAI(false);
            if (mob.hasGravity()) mob.setGravity(false);

            Input input = player.getCurrentInput();

            if (session.attackCooldown > 0) {
                session.attackCooldown--;
            }

            boolean grounded = isGrounded(mob);

            // ==================================
            // HORIZONTAL MOVEMENT
            // ==================================

            double yaw = Math.toRadians(mob.getLocation().getYaw());

            Vector forward = new Vector(
                -Math.sin(yaw), 0, Math.cos(yaw)
            );

            Vector right = new Vector(
                -forward.getZ(), 0, forward.getX()
            );

            Vector horizontal = new Vector(0, 0, 0);

            if (input.isForward()) horizontal.add(forward);
            if (input.isBackward()) horizontal.subtract(forward);
            if (input.isRight()) horizontal.add(right);
            if (input.isLeft()) horizontal.subtract(right);

            if (horizontal.lengthSquared() > 0) {
                horizontal.normalize();

                double speed = input.isSprint()
                    ? SPRINT_SPEED
                    : WALK_SPEED;

                horizontal.multiply(speed);
            }

            // ==================================
            // JUMPING AND GRAVITY
            // ==================================

            boolean jumping = input.isJump();

            // Jump only on a new Space press.
            if (jumping && !session.jumpHeld && grounded) {
                session.verticalSpeed = JUMP_POWER;
                grounded = false;
            }

            session.jumpHeld = jumping;

            if (session.pushTicks > 0) {
                session.pushTicks--;
            }

            if (grounded && session.verticalSpeed <= 0) {
                session.verticalSpeed = 0;
            } else {
                session.verticalSpeed -= GRAVITY;

                if (session.verticalSpeed < TERMINAL_SPEED) {
                    session.verticalSpeed = TERMINAL_SPEED;
                }
            }

            // ==================================
            // COLLISION-AWARE MOVEMENT
            // ==================================

            Location current = mob.getLocation();
            Location destination = current.clone();

            // Move separately along X and Z to allow wall sliding.
            Location xMove = destination.clone();
            xMove.add(horizontal.getX(), 0, 0);

            if (canOccupy(mob, xMove)) {
                destination = xMove;
            }

            Location zMove = destination.clone();
            zMove.add(0, 0, horizontal.getZ());

            if (canOccupy(mob, zMove)) {
                destination = zMove;
            }

            // Vertical movement in small increments prevents
            // passing through floors during fast falls.
            double remaining = session.verticalSpeed;
            boolean verticalCollision = false;

            while (Math.abs(remaining) > 0.0001) {
                double step = Math.max(
                    -0.2,
                    Math.min(0.2, remaining)
                );

                Location verticalMove = destination.clone();
                verticalMove.add(0, step, 0);

                if (canOccupy(mob, verticalMove)) {
                    destination = verticalMove;
                } else {
                    verticalCollision = true;
                    break;
                }

                remaining -= step;
            }

            if (verticalCollision) {
                session.verticalSpeed = 0;
            }

            // Only teleport when a position change is needed.
            if (destination.distanceSquared(current) > 0.000001) {
                mob.teleport(destination);
            }

            // ==================================
            // ATTACK
            // ==================================

            if (input.isSneak() && session.attackCooldown == 0) {
                session.attackCooldown = 12;

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

            // ==================================
            // VISUAL EFFECTS
            // ==================================

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

            // ==================================
            // DEBUG DISPLAY
            // ==================================

            if (debugging.contains(player.getUniqueId())
                    && Bukkit.getCurrentTick() % 10 == 0) {

                player.sendActionBar(
                    "§cW:" + input.isForward()
                    + " J:" + input.isJump()
                    + " Ground:" + isGrounded(mob)
                    + " Y:" + String.format("%.2f", session.verticalSpeed)
                    + " AI:" + mob.hasAI()
                );
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

        Entity entity = Bukkit.getEntity(session.mobId);

        if (entity instanceof Mob mob && mob.isValid()) {
            mob.setAI(session.oldAi);
            mob.setGravity(true);
            mob.setVelocity(new Vector(0, 0, 0));
        }

        player.setSpectatorTarget(null);
        player.setGameMode(session.oldMode);
        player.teleport(session.home);

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
            if (entry.getValue().mobId.equals(
                    event.getEntity().getUniqueId())) {

                Player player = Bukkit.getPlayer(entry.getKey());

                if (player != null) {
                    release(player);
                }
            }
        }
    }
}
