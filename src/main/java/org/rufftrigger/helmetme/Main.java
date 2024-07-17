package org.rufftrigger.helmetme;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.entity.Player;
import org.bukkit.entity.Arrow;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class Main extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("HelmetMe plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("HelmetMe plugin has been disabled.");
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow) {
            Arrow arrow = (Arrow) event.getEntity();

            if (event.getHitEntity() instanceof Player) {
                Player player = (Player) event.getHitEntity();

                // Calculate hit position relative to player
                double hitY = arrow.getLocation().getY() - player.getLocation().getY();

                // Approximate head hitbox height range
                double headStartY = 1.6; // Start of the head
                double headEndY = 2.0; // End of the head

                if (hitY >= headStartY && hitY <= headEndY) {
                    ItemStack helmet = player.getInventory().getHelmet();
                    if (helmet != null) {
                        // Simulate shield block effects
                        simulateShieldBlock(player, arrow.getLocation());

                        // Reflect the arrow
                        reflectArrow(arrow, player);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            if (event.getDamager() instanceof Arrow) {
                Arrow arrow = (Arrow) event.getDamager();

                // Check if the arrow hits the head area
                if (arrow.getLocation().distance(player.getEyeLocation()) < 1.5) {
                    ItemStack helmet = player.getInventory().getHelmet();
                    if (helmet != null) {
                        // Simulate shield block effects
                        simulateShieldBlock(player, arrow.getLocation());
                        event.setCancelled(true);

                        // Reflect the arrow
                        reflectArrow(arrow, player);
                    }
                }
            }
        }
    }

    private void simulateShieldBlock(Player player, Location location) {
        // Play shield block sound
        player.getWorld().playSound(location, Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);

        // Display block particle effect
        player.getWorld().spawnParticle(Particle.ASH, location, 10, 0.3, 0.3, 0.3, player.getInventory().getHelmet().getType().createBlockData());
        // Send message to player
        player.sendMessage("Your helmet blocked the arrow!");
    }

    private void reflectArrow(Arrow arrow, Player player) {
        // Get the current velocity of the arrow
        Vector velocity = arrow.getVelocity();

        // Reflect the arrow by reversing its velocity
        Vector reflectedVelocity = velocity.multiply(-1);

        // Set the new velocity to the arrow
        arrow.setVelocity(reflectedVelocity);

        // Set the shooter of the arrow to null to avoid looping reflections
        arrow.setShooter(null);
    }
}
