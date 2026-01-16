package org.rufftrigger.helmetme;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.entity.Player;
import org.bukkit.entity.Arrow;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class Main extends JavaPlugin implements Listener {

    private int helmetDamage;
    /**
     * Chance (0.0 - 1.0) that a helmet will be knocked off when the player is hit in the head.
     */
    private double helmetKnockOffChance;
    private List<Material> blockedHelmetMaterials;

    @Override
    public void onEnable() {
        try {
            // Save the default config.yml if it doesn't exist
            saveDefaultConfig();
            // Load configuration
            loadConfiguration();

            getServer().getPluginManager().registerEvents(this, this);
            getLogger().info("HelmetMe plugin has been enabled!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error enabling HelmetMe plugin", e);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("HelmetMe plugin has been disabled.");
    }

    private void loadConfiguration() {
        try {
            FileConfiguration config = getConfig();
            helmetDamage = config.getInt("helmetDamage", 1);

            // 0.20 = 20%
            helmetKnockOffChance = config.getDouble("helmetKnockOffChance", 0.20);
            if (helmetKnockOffChance < 0.0) helmetKnockOffChance = 0.0;
            if (helmetKnockOffChance > 1.0) helmetKnockOffChance = 1.0;

            blockedHelmetMaterials = new ArrayList<>();
            List<String> materialNames = config.getStringList("blockedHelmetMaterials");
            for (String materialName : materialNames) {
                Material material = Material.matchMaterial(materialName);
                if (material != null) {
                    blockedHelmetMaterials.add(material);
                } else {
                    getLogger().warning("Invalid material name in blockedHelmetMaterials: " + materialName);
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error loading configuration", e);
        }
    }

    private boolean isHelmetBlocked(ItemStack helmet) {
        if (helmet == null) {
            return false;
        }
        Material helmetMaterial = helmet.getType();
        return blockedHelmetMaterials.contains(helmetMaterial);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        try {
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

                        // New feature: 20% chance to knock the helmet off on a head hit.
                        // If it gets knocked off, we skip the "helmet blocks the arrow" logic because the helmet is gone.
                        if (tryKnockOffHelmet(player, arrow)) {
                            return;
                        }

                        if (isHelmetBlocked(helmet)) {
                            // Simulate shield block effects
                            simulateShieldBlock(player, arrow.getLocation());

                            // Reflect the arrow
                            reflectArrow(arrow, player);

                            // Damage the helmet
                            damageHelmet(player, helmet);
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error in onProjectileHit", e);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        try {
            if (event.getEntity() instanceof Player) {
                Player player = (Player) event.getEntity();

                if (event.getDamager() instanceof Arrow) {
                    Arrow arrow = (Arrow) event.getDamager();

                    // Check if the arrow hits the head area
                    if (arrow.getLocation().distance(player.getEyeLocation()) < 1.5) {
                        ItemStack helmet = player.getInventory().getHelmet();

                        // New feature: 20% chance to knock the helmet off on a head hit.
                        // If it gets knocked off, we let the damage proceed normally and skip the block/reflect logic.
                        if (tryKnockOffHelmet(player, arrow)) {
                            return;
                        }

                        if (isHelmetBlocked(helmet)) {
                            // Simulate shield block effects
                            simulateShieldBlock(player, arrow.getLocation());
                            event.setCancelled(true);

                            // Reflect the arrow
                            reflectArrow(arrow, arrow.getShooter()); // Pass the shooter of the arrow

                            // Damage the helmet
                            damageHelmet(player, helmet);
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error in onEntityDamageByEntity", e);
        }
    }

    private void simulateShieldBlock(Player player, Location location) {
        try {
            ItemStack helmet = player.getInventory().getHelmet();
            if (helmet == null) {
                return; // No helmet equipped, nothing to simulate
            }

            Material helmetMaterial = helmet.getType();
            if (helmetMaterial == null) {
                getLogger().warning("Helmet material is null for player: " + player.getName());
                return;
            }

            // Play Bell and shield block sound
            player.getWorld().playSound(location, Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
            player.getWorld().playSound(location, Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);

            // Display block particle effect
            if (helmetMaterial.isBlock()) {
                player.getWorld().spawnParticle(Particle.DUST, location, 10, 0.3, 0.3, 0.3, 0.1, helmetMaterial.createBlockData());
            } else {
                player.getWorld().spawnParticle(Particle.ASH, location, 10, 0.3, 0.3, 0.3);
            }

            // Send message to player
            player.sendMessage("Your helmet blocked the arrow!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error in simulateShieldBlock", e);
        }
    }

    private void reflectArrow(Arrow arrow, ProjectileSource shooter) {
        try {
            // Get the current velocity of the arrow
            Vector velocity = arrow.getVelocity();

            // Reflect the arrow by reversing its velocity
            Vector reflectedVelocity = velocity.multiply(-1);

            // Set the new velocity to the arrow
            arrow.setVelocity(reflectedVelocity);

            // Set the shooter of the arrow to avoid looping reflections
            arrow.setShooter(shooter);

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error in reflectArrow", e);
        }
    }

    private void damageHelmet(Player player, ItemStack helmet) {
        try {
            ItemMeta meta = helmet.getItemMeta(); // Corrected class name to ItemMeta
            if (meta instanceof Damageable) {
                Damageable damageable = (Damageable) meta;

                // Increase the damage by the configured amount
                damageable.setDamage(damageable.getDamage() + helmetDamage);

                // Check if the helmet is broken
                if (damageable.getDamage() >= helmet.getType().getMaxDurability()) {
                    player.getInventory().setHelmet(new ItemStack(Material.AIR)); // Remove the helmet if it's broken
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                    player.sendMessage("Your helmet has broken!");
                } else {
                    helmet.setItemMeta(meta); // Apply updated meta back to the ItemStack
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error in damageHelmet", e);
        }
    }

    /**
     * If the player is wearing a helmet, this gives it a chance to get knocked off and dropped.
     *
     * @return true if a helmet was knocked off, otherwise false.
     */
    private boolean tryKnockOffHelmet(Player player, Arrow arrow) {
        try {
            ItemStack helmet = player.getInventory().getHelmet();
            if (helmet == null || helmet.getType() == Material.AIR) {
                return false;
            }

            if (Math.random() > helmetKnockOffChance) {
                return false;
            }

            // Remove from head
            player.getInventory().setHelmet(new ItemStack(Material.AIR));

            // Drop the helmet with a "shot off" velocity
            Location dropLoc = player.getEyeLocation().clone().add(0, 0.2, 0);
            var item = player.getWorld().dropItemNaturally(dropLoc, helmet);

            Vector push;
            if (arrow != null) {
                // Push away from incoming arrow direction (arrow velocity points forward)
                Vector incoming = arrow.getVelocity();
                if (incoming.lengthSquared() > 0.0001) {
                    push = incoming.clone().normalize().multiply(0.6).setY(0.35);
                } else {
                    push = player.getLocation().getDirection().multiply(-0.4).setY(0.35);
                }
            } else {
                push = player.getLocation().getDirection().multiply(-0.4).setY(0.35);
            }
            item.setVelocity(push);

            // Feedback
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 0.6f);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getEyeLocation(), 8, 0.15, 0.15, 0.15, 0.02);
            player.sendMessage("Your helmet got shot off!");

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error in tryKnockOffHelmet", e);
            return false;
        }
    }
}
