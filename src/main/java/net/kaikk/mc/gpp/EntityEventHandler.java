/*
    GriefPreventionPlus Server Plugin for Minecraft
    Copyright (C) 2015 Antonino Kai Pocorobba
    (forked from GriefPrevention by Ryan Hamshire)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.kaikk.mc.gpp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

//handles events related to entities
@SuppressWarnings("deprecation")
class EntityEventHandler implements Listener {
	// convenience reference for the singleton datastore
	private final DataStore dataStore;

	public EntityEventHandler(DataStore dataStore) {
		this.dataStore = dataStore;
	}

	// don't allow endermen to change blocks
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onEntityChangeBlock(EntityChangeBlockEvent event) {
		if (!GriefPreventionPlus.getInstance().config.endermenMoveBlocks && (event.getEntityType() == EntityType.ENDERMAN)) {
			event.setCancelled(true);
		}

		else if (!GriefPreventionPlus.getInstance().config.silverfishBreakBlocks && (event.getEntityType() == EntityType.SILVERFISH)) {
			event.setCancelled(true);
		}

		// don't allow the wither to break blocks, when the wither is
		// determined, too expensive to constantly check for claimed blocks
		else if ((event.getEntityType() == EntityType.WITHER) && !GriefPreventionPlus.getInstance().config.disabledWorlds.contains(event.getBlock().getWorld().getName())) {
			event.setCancelled(true);
		}

		// don't allow players to trample crops
		else if ((event.getTo() == Material.DIRT) && (event.getBlock().getType() == Material.SOIL)) {
			event.setCancelled(true);
		}

		// sand cannon fix - when the falling block doesn't fall straight down,
		// take additional anti-grief steps
		else if (event.getEntityType() == EntityType.FALLING_BLOCK) {
			final FallingBlock entity = (FallingBlock) event.getEntity();
			final Block block = event.getBlock();

			// if changing a block TO air, this is when the falling block
			// formed. note its original location
			if (event.getTo() == Material.AIR) {
				entity.setMetadata("GP_FALLINGBLOCK", new FixedMetadataValue(GriefPreventionPlus.getInstance(), block.getLocation()));
			}
			// otherwise, the falling block is forming a block. compare new
			// location to original source
			else {
				final List<MetadataValue> values = entity.getMetadata("GP_FALLINGBLOCK");

				// if we're not sure where this entity came from (maybe another
				// plugin didn't follow the standard?), allow the block to form
				if (values.size() < 1) {
					return;
				}

				final Location originalLocation = (Location) (values.get(0).value());
				final Location newLocation = block.getLocation();

				// if did not fall straight down
				if ((originalLocation.getBlockX() != newLocation.getBlockX()) || (originalLocation.getBlockZ() != newLocation.getBlockZ())) {
					// in creative mode worlds, never form the block
					if (GriefPreventionPlus.getInstance().config.creativeRulesWorlds.contains(newLocation.getWorld().getName())) {
						event.setCancelled(true);
						return;
					}

					// in other worlds, if landing in land claim, only allow if
					// source was also in the land claim
					final Claim claim = this.dataStore.getClaimAt(newLocation, false);
					if ((claim != null) && !claim.contains(originalLocation, false, false)) {
						// when not allowed, drop as item instead of forming a
						// block
						event.setCancelled(true);
						final ItemStack itemStack = new ItemStack(entity.getMaterial(), 1, entity.getBlockData());
						final Item item = block.getWorld().dropItem(entity.getLocation(), itemStack);
						item.setVelocity(new Vector());
					}
				}
			}
		}
	}

	// when an entity is damaged
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onEntityDamage(EntityDamageEvent event) {
		// monsters are never protected
		if (event.getEntity() instanceof Monster) {
			return;
		}

		// protect pets from environmental damage types which could be easily
		// caused by griefers
		if ((event.getEntity() instanceof Tameable) && !GriefPreventionPlus.getInstance().config.pvp_enabledWorlds.contains(event.getEntity().getWorld().getUID())) {
			final Tameable tameable = (Tameable) event.getEntity();
			if (tameable.isTamed()) {
				final DamageCause cause = event.getCause();
				if ((cause != null) && ((cause == DamageCause.ENTITY_EXPLOSION) || (cause == DamageCause.FALLING_BLOCK) || (cause == DamageCause.FIRE) || (cause == DamageCause.FIRE_TICK) || (cause == DamageCause.LAVA) || (cause == DamageCause.SUFFOCATION))) {
					event.setCancelled(true);
					return;
				}
			}
		}

		// the rest is only interested in entities damaging entities (ignoring
		// environmental damage)
		if (!(event instanceof EntityDamageByEntityEvent)) {
			return;
		}

		final EntityDamageByEntityEvent subEvent = (EntityDamageByEntityEvent) event;

		// determine which player is attacking, if any
		Player attacker = null;
		Projectile arrow = null;
		final Entity damageSource = subEvent.getDamager();

		if (damageSource != null) {
			if (damageSource instanceof Player) {
				attacker = (Player) damageSource;
			} else if (damageSource instanceof Projectile) {
				arrow = (Projectile) damageSource;
				if (arrow.getShooter() instanceof Player) {
					attacker = (Player) arrow.getShooter();
				}
			}
		}

		// if the attacker is a player and defender is a player (pvp combat)
		if ((attacker != null) && (event.getEntity() instanceof Player) && GriefPreventionPlus.getInstance().config.pvp_enabledWorlds.contains(attacker.getWorld().getUID())) {
			// FEATURE: prevent pvp in the first minute after spawn, and prevent
			// pvp when one or both players have no inventory

			// doesn't apply when the attacker has the no pvp immunity
			// permission
			// this rule is here to allow server owners to have a world with no
			// spawn camp protection by assigning permissions based on the
			// player's world
			if (attacker.hasPermission("griefprevention.nopvpimmunity")) {
				return;
			}

			final Player defender = (Player) (event.getEntity());

			if (attacker != defender) {
				final PlayerData defenderData = this.dataStore.getPlayerData(((Player) event.getEntity()).getUniqueId());
				final PlayerData attackerData = this.dataStore.getPlayerData(attacker.getUniqueId());

				// FEATURE: prevent players from engaging in PvP combat inside
				// land claims (when it's disabled)
				if (GriefPreventionPlus.getInstance().config.pvp_noCombatInPlayerLandClaims || GriefPreventionPlus.getInstance().config.pvp_noCombatInAdminLandClaims) {
					final Claim attackerClaim = this.dataStore.getClaimAt(attacker.getLocation(), false, attackerData.lastClaim);
					if ((attackerClaim != null) && ((attackerClaim.isAdminClaim() && (attackerClaim.getParent() == null) && GriefPreventionPlus.getInstance().config.pvp_noCombatInAdminLandClaims) || (attackerClaim.isAdminClaim() && (attackerClaim.getParent() != null) && GriefPreventionPlus.getInstance().config.pvp_noCombatInAdminSubdivisions) || (!attackerClaim.isAdminClaim() && GriefPreventionPlus.getInstance().config.pvp_noCombatInPlayerLandClaims))) {
						event.setCancelled(true);
						GriefPreventionPlus.sendMessage(attacker, TextMode.Err, Messages.CantFightWhileImmune);
						return;
					}

					final Claim defenderClaim = this.dataStore.getClaimAt(defender.getLocation(), false, defenderData.lastClaim);
					if ((defenderClaim != null) && ((defenderClaim.isAdminClaim() && (defenderClaim.getParent() == null) && GriefPreventionPlus.getInstance().config.pvp_noCombatInAdminLandClaims) || (defenderClaim.isAdminClaim() && (defenderClaim.getParent() != null) && GriefPreventionPlus.getInstance().config.pvp_noCombatInAdminSubdivisions) || (!defenderClaim.isAdminClaim() && GriefPreventionPlus.getInstance().config.pvp_noCombatInPlayerLandClaims))) {
						event.setCancelled(true);
						GriefPreventionPlus.sendMessage(attacker, TextMode.Err, Messages.PlayerInPvPSafeZone);
						return;
					}
				}
			}
		}

		// FEATURE: protect claimed animals, boats, minecarts, and items inside
		// item frames
		// NOTE: animals can be lead with wheat, vehicles can be pushed around.
		// so unless precautions are taken by the owner, a resourceful thief
		// might find ways to steal anyway

		// if theft protection is enabled
		if (event instanceof EntityDamageByEntityEvent) {
			// don't track in worlds where claims are not enabled
			if (!GriefPreventionPlus.getInstance().claimsEnabledForWorld(event.getEntity().getWorld())) {
				return;
			}

			// if the damaged entity is a claimed item frame or armor stand, the
			// damager needs to be a player with container trust in the claim
			if ((subEvent.getEntityType() == EntityType.ITEM_FRAME) || (subEvent.getEntityType() == EntityType.VILLAGER)) {
				// decide whether it's claimed
				Claim cachedClaim = null;
				PlayerData playerData = null;
				if (attacker != null) {
					playerData = this.dataStore.getPlayerData(attacker.getUniqueId());
					cachedClaim = playerData.lastClaim;
				}

				final Claim claim = this.dataStore.getClaimAt(event.getEntity().getLocation(), false, cachedClaim);

				// if it's claimed
				if (claim != null) {
					// if attacker isn't a player, cancel
					if (attacker == null) {
						// exception case
						if ((event.getEntity() instanceof Villager) && (damageSource != null) && (damageSource instanceof Monster)) {
							return;
						}

						event.setCancelled(true);
						return;
					}

					// otherwise player must have container trust in the claim
					final String failureReason = claim.canBuild(attacker, Material.AIR);
					if (failureReason != null) {
						event.setCancelled(true);
						GriefPreventionPlus.sendMessage(attacker, TextMode.Err, failureReason);
						return;
					}
				}
			}

			// if the entity is an non-monster creature (remember monsters
			// disqualified above), or a vehicle
			if (((subEvent.getEntity() instanceof Creature) && GriefPreventionPlus.getInstance().config.claims_protectCreatures)) {
				// if entity is tameable and has an owner, apply special rules
				if ((subEvent.getEntity() instanceof Tameable) && !GriefPreventionPlus.getInstance().config.pvp_enabledWorlds.contains(subEvent.getEntity().getWorld().getUID())) {
					final Tameable tameable = (Tameable) subEvent.getEntity();
					if (tameable.isTamed() && (tameable.getOwner() != null)) {
						// limit attacks by players to owners and admins in
						// ignore claims mode
						if (attacker != null) {
							final UUID ownerID = tameable.getOwner().getUniqueId();

							// if the player interacting is the owner, always
							// allow
							if (attacker.getUniqueId().equals(ownerID)) {
								return;
							}

							// allow for admin override
							final PlayerData attackerData = this.dataStore.getPlayerData(attacker.getUniqueId());
							if (attackerData.ignoreClaims) {
								return;
							}

							// otherwise disallow in non-pvp worlds
							if (!GriefPreventionPlus.getInstance().config.pvp_enabledWorlds.contains(subEvent.getEntity().getLocation().getWorld().getUID())) {
								final OfflinePlayer owner = GriefPreventionPlus.getInstance().getServer().getOfflinePlayer(ownerID);
								String ownerName = owner.getName();
								if (ownerName == null) {
									ownerName = "someone";
								}
								String message = GriefPreventionPlus.getInstance().getDataStore().getMessage(Messages.NoDamageClaimedEntity, ownerName);
								if (attacker.hasPermission("griefprevention.ignoreclaims")) {
									message += "  " + GriefPreventionPlus.getInstance().getDataStore().getMessage(Messages.IgnoreClaimsAdvertisement);
								}
								GriefPreventionPlus.sendMessage(attacker, TextMode.Err, message);
								event.setCancelled(true);
								return;
							}
						}
					}
				}

				Claim cachedClaim = null;
				PlayerData playerData = null;

				// if not a player or an explosive, allow
				if ((attacker == null) && (damageSource != null) && !(damageSource instanceof Projectile) && (damageSource.getType() != EntityType.CREEPER) && !(damageSource instanceof Explosive)) {
					return;
				}

				if (attacker != null) {
					playerData = this.dataStore.getPlayerData(attacker.getUniqueId());
					cachedClaim = playerData.lastClaim;
				}

				final Claim claim = this.dataStore.getClaimAt(event.getEntity().getLocation(), false, cachedClaim);

				// if it's claimed
				if (claim != null) {
					// if damaged by anything other than a player (exception
					// villagers injured by zombies in admin claims), cancel the
					// event
					// why exception? so admins can set up a village which can't
					// be CHANGED by players, but must be "protected" by
					// players.
					if (attacker == null) {
						// exception case
						if ((event.getEntity() instanceof Villager) && (damageSource != null) && (damageSource instanceof Monster)) {
							return;
						}

						// all other cases
						else {
							event.setCancelled(true);
							if ((damageSource != null) && (damageSource instanceof Projectile)) {
								damageSource.remove();
							}
						}
					}

					// otherwise the player damaging the entity must have
					// permission
					else {
						final String noContainersReason = claim.canOpenContainers(attacker);
						if (noContainersReason != null) {
							event.setCancelled(true);

							// kill the arrow to avoid infinite bounce between
							// crowded together animals
							if (arrow != null) {
								arrow.remove();
							}

							String message = GriefPreventionPlus.getInstance().getDataStore().getMessage(Messages.NoDamageClaimedEntity, claim.getOwnerName());
							if (attacker.hasPermission("griefprevention.ignoreclaims")) {
								message += "  " + GriefPreventionPlus.getInstance().getDataStore().getMessage(Messages.IgnoreClaimsAdvertisement);
							}
							GriefPreventionPlus.sendMessage(attacker, TextMode.Err, message);
							event.setCancelled(true);
						}
					}
				}
			}
		}
	}

	// when an entity explodes...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onEntityExplode(EntityExplodeEvent explodeEvent) {
		final List<Block> blocks = explodeEvent.blockList();
		final Location location = explodeEvent.getLocation();

		// FEATURE: explosions don't destroy blocks when they explode near or
		// above sea level in standard worlds
		final boolean isCreeper = ((explodeEvent.getEntity() != null) && (explodeEvent.getEntity() instanceof Creeper));

		// exception for some land claims in survival worlds, see notes below
		Claim originationClaim = null;
		if (!GriefPreventionPlus.getInstance().creativeRulesApply(location.getWorld())) {
			originationClaim = GriefPreventionPlus.getInstance().getDataStore().getClaimAt(location, false);
		}

		if ((location.getWorld().getEnvironment() == Environment.NORMAL) && GriefPreventionPlus.getInstance().claimsEnabledForWorld(location.getWorld()) && ((isCreeper && GriefPreventionPlus.getInstance().config.blockSurfaceCreeperExplosions) || (!isCreeper && GriefPreventionPlus.getInstance().config.blockSurfaceOtherExplosions))) {
			for (int i = 0; i < blocks.size(); i++) {
				final Block block = blocks.get(i);
				if (GriefPreventionPlus.getInstance().config.mods_explodableIds.contains(new MaterialInfo(block.getType(), block.getData(), null))) {
					continue;
				}

				// in survival worlds, if claim explosions are enabled for the
				// source claim, allow non-creeper explosions to destroy blocks
				// in and under that claim even above sea level.
				if (!isCreeper && (originationClaim != null) && originationClaim.areExplosivesAllowed() && originationClaim.contains(block.getLocation(), true, false)) {
					continue;
				}

				if (block.getLocation().getBlockY() > (GriefPreventionPlus.getInstance().getSeaLevel(location.getWorld()) - 7)) {
					blocks.remove(i--);
				}
			}
		}

		// special rule for creative worlds: explosions don't destroy anything
		if (GriefPreventionPlus.getInstance().creativeRulesApply(explodeEvent.getLocation().getWorld())) {
			for (int i = 0; i < blocks.size(); i++) {
				final Block block = blocks.get(i);
				if (GriefPreventionPlus.getInstance().config.mods_explodableIds.contains(new MaterialInfo(block.getType(), block.getData(), null))) {
					continue;
				}

				blocks.remove(i--);
			}
		}

		// FEATURE: explosions don't damage claimed blocks
		Claim claim = null;
		for (int i = 0; i < blocks.size(); i++) // for each destroyed block
		{
			final Block block = blocks.get(i);
			if (block.getType() == Material.AIR) {
				continue; // if it's air, we don't care
			}

			if (GriefPreventionPlus.getInstance().config.mods_explodableIds.contains(new MaterialInfo(block.getType(), block.getData(), null))) {
				continue;
			}

			claim = this.dataStore.getClaimAt(block.getLocation(), false);
			// if the block is claimed, remove it from the list of destroyed
			// blocks
			if ((claim != null) && !claim.areExplosivesAllowed() && GriefPreventionPlus.getInstance().config.blockClaimExplosions) {
				blocks.remove(i--);
			}
		}
	}

	// don't allow entities to trample crops
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onEntityInteract(EntityInteractEvent event) {
		final Material material = event.getBlock().getType();
		if (material == Material.SOIL) {
			if (!GriefPreventionPlus.getInstance().config.creaturesTrampleCrops) {
				event.setCancelled(true);
			} else {
				final Entity rider = event.getEntity().getPassenger();
				if ((rider != null) && (rider.getType() == EntityType.PLAYER)) {
					event.setCancelled(true);
				}
			}
		}
	}

	// when an entity picks up an item
	@EventHandler(priority = EventPriority.LOWEST)
	public void onEntityPickup(EntityChangeBlockEvent event) {
		// FEATURE: endermen don't steal claimed blocks

		// if its an enderman
		if (event.getEntity() instanceof Enderman) {
			// and the block is claimed
			if (this.dataStore.getClaimAt(event.getBlock().getLocation(), false) != null) {
				// he doesn't get to steal it
				event.setCancelled(true);
			}
		}
	}

	// when a creature spawns...
	@EventHandler(priority = EventPriority.LOWEST)
	public void onEntitySpawn(CreatureSpawnEvent event) {
		// these rules apply only to creative worlds
		if (!GriefPreventionPlus.getInstance().creativeRulesApply(event.getLocation().getWorld())) {
			return;
		}

		// chicken eggs and breeding could potentially make a mess in the
		// wilderness, once griefers get involved
		final SpawnReason reason = event.getSpawnReason();
		if ((reason != SpawnReason.SPAWNER_EGG) && (reason != SpawnReason.BUILD_IRONGOLEM) && (reason != SpawnReason.BUILD_SNOWMAN) && (GriefPreventionPlus.isBukkit18 && MC18Helper.isArmorStatue(event.getEntityType()))) {
			event.setCancelled(true);
			return;
		}

		// otherwise, just apply the limit on total entities per claim (and no
		// spawning in the wilderness!)
		final Claim claim = this.dataStore.getClaimAt(event.getLocation(), false);
		if ((claim == null) || (claim.allowMoreEntities() != null)) {
			event.setCancelled(true);
			return;
		}
	}

	// when an experience bottle explodes...
	@EventHandler(priority = EventPriority.LOWEST)
	public void onExpBottle(ExpBottleEvent event) {
		// if in a creative world, cancel the event (don't drop exp on the
		// ground)
		if (GriefPreventionPlus.getInstance().creativeRulesApply(event.getEntity().getWorld())) {
			event.setExperience(0);
		}
	}

	// when a painting is broken
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onHangingBreak(HangingBreakEvent event) {
		// don't track in worlds where claims are not enabled
		if (!GriefPreventionPlus.getInstance().claimsEnabledForWorld(event.getEntity().getWorld())) {
			return;
		}

		// FEATURE: claimed paintings are protected from breakage

		// explosions don't destroy hangings
		if (event.getCause() == RemoveCause.EXPLOSION) {
			event.setCancelled(true);
			return;
		}

		// only allow players to break paintings, not anything else (like water
		// and explosions)
		if (!(event instanceof HangingBreakByEntityEvent)) {
			event.setCancelled(true);
			return;
		}

		final HangingBreakByEntityEvent entityEvent = (HangingBreakByEntityEvent) event;

		// who is removing it?
		final Entity remover = entityEvent.getRemover();

		// again, making sure the breaker is a player
		if (!(remover instanceof Player)) {
			event.setCancelled(true);
			return;
		}

		// if the player doesn't have build permission, don't allow the breakage
		final Player playerRemover = (Player) entityEvent.getRemover();
		final String noBuildReason = GriefPreventionPlus.getInstance().allowBuild(playerRemover, event.getEntity().getLocation(), Material.AIR);
		if (noBuildReason != null) {
			event.setCancelled(true);
			GriefPreventionPlus.sendMessage(playerRemover, TextMode.Err, noBuildReason);
		}
	}

	// when an item spawns...
	@EventHandler(priority = EventPriority.LOWEST)
	public void onItemSpawn(ItemSpawnEvent event) {
		// if in a creative world, cancel the event (don't drop items on the
		// ground)
		if (GriefPreventionPlus.getInstance().creativeRulesApply(event.getLocation().getWorld())) {
			event.setCancelled(true);
		}

		// if item is on watch list, apply protection
		final ArrayList<PendingItemProtection> watchList = GriefPreventionPlus.getInstance().pendingItemWatchList;
		final Item newItem = event.getEntity();
		Long now = null;
		for (int i = 0; i < watchList.size(); i++) {
			final PendingItemProtection pendingProtection = watchList.get(i);
			// ignore and remove any expired pending protections
			if (now == null) {
				now = System.currentTimeMillis();
			}
			if (pendingProtection.expirationTimestamp < now) {
				watchList.remove(i--);
				continue;
			}
			// skip if item stack doesn't match
			if ((pendingProtection.itemStack.getAmount() != newItem.getItemStack().getAmount()) || (pendingProtection.itemStack.getType() != newItem.getItemStack().getType())) {
				continue;
			}

			// skip if new item location isn't near the expected spawn area
			final Location spawn = event.getLocation();
			final Location expected = pendingProtection.location;
			if (!spawn.getWorld().equals(expected.getWorld()) || (spawn.getX() < (expected.getX() - 5)) || (spawn.getX() > (expected.getX() + 5)) || (spawn.getZ() < (expected.getZ() - 5)) || (spawn.getZ() > (expected.getZ() + 5)) || (spawn.getY() < (expected.getY() - 15)) || (spawn.getY() > (expected.getY() + 3))) {
				continue;
			}

			// otherwise, mark item with protection information
			newItem.setMetadata("GP_ITEMOWNER", new FixedMetadataValue(GriefPreventionPlus.getInstance(), pendingProtection.owner));

			// and remove pending protection data
			watchList.remove(i);
			break;
		}
	}

	// when a painting is placed...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onPaintingPlace(HangingPlaceEvent event) {
		// don't track in worlds where claims are not enabled
		if (!GriefPreventionPlus.getInstance().claimsEnabledForWorld(event.getBlock().getWorld())) {
			return;
		}

		// FEATURE: similar to above, placing a painting requires build
		// permission in the claim

		// if the player doesn't have permission, don't allow the placement
		final String noBuildReason = GriefPreventionPlus.getInstance().allowBuild(event.getPlayer(), event.getEntity().getLocation(), Material.PAINTING);
		if (noBuildReason != null) {
			event.setCancelled(true);
			GriefPreventionPlus.sendMessage(event.getPlayer(), TextMode.Err, noBuildReason);
			return;
		}

		// otherwise, apply entity-count limitations for creative worlds
		else if (GriefPreventionPlus.getInstance().creativeRulesApply(event.getEntity().getWorld())) {
			final PlayerData playerData = this.dataStore.getPlayerData(event.getPlayer().getUniqueId());
			final Claim claim = this.dataStore.getClaimAt(event.getBlock().getLocation(), false, playerData.lastClaim);
			if (claim == null) {
				return;
			}

			final String noEntitiesReason = claim.allowMoreEntities();
			if (noEntitiesReason != null) {
				GriefPreventionPlus.sendMessage(event.getPlayer(), TextMode.Err, noEntitiesReason);
				event.setCancelled(true);
				return;
			}
		}
	}

	// when a splash potion effects one or more entities...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void onPotionSplash(PotionSplashEvent event) {
		final ThrownPotion potion = event.getPotion();

		// ignore potions not thrown by players
		final ProjectileSource projectileSource = potion.getShooter();
		if ((projectileSource == null) || !(projectileSource instanceof Player)) {
			return;
		}
		final Player thrower = (Player) projectileSource;

		final Collection<PotionEffect> effects = potion.getEffects();
		for (final PotionEffect effect : effects) {
			final PotionEffectType effectType = effect.getType();

			// restrict jump potions on claimed animals (griefers could use this
			// to steal animals over fences)
			if (effectType.getName().equals("JUMP") || effectType.getName().equals("POISON")) {
				for (final LivingEntity effected : event.getAffectedEntities()) {
					Claim cachedClaim = null;
					if (effected instanceof Animals) {
						final Claim claim = this.dataStore.getClaimAt(effected.getLocation(), false, cachedClaim);
						if (claim != null) {
							cachedClaim = claim;
							if (claim.canOpenContainers(thrower) != null) {
								event.setCancelled(true);
								GriefPreventionPlus.sendMessage(thrower, TextMode.Err, Messages.NoDamageClaimedEntity, claim.getOwnerName());
								return;
							}
						}
					}
				}
			}

			// otherwise, no restrictions for positive effects
			if (positiveEffects.contains(effectType)) {
				continue;
			}

			for (final LivingEntity effected : event.getAffectedEntities()) {
				// always impact the thrower
				if (effected == thrower) {
					continue;
				}

				// always impact non players
				if (!(effected instanceof Player)) {
					continue;
				} else if (GriefPreventionPlus.getInstance().config.pvp_noCombatInPlayerLandClaims || GriefPreventionPlus.getInstance().config.pvp_noCombatInAdminLandClaims) {
					final Player effectedPlayer = (Player) effected;
					final PlayerData defenderData = this.dataStore.getPlayerData(effectedPlayer.getUniqueId());
					final PlayerData attackerData = this.dataStore.getPlayerData(thrower.getUniqueId());
					final Claim attackerClaim = this.dataStore.getClaimAt(thrower.getLocation(), false, attackerData.lastClaim);
					if ((attackerClaim != null) && ((attackerClaim.isAdminClaim() && (attackerClaim.getParent() == null) && GriefPreventionPlus.getInstance().config.pvp_noCombatInAdminLandClaims) || (attackerClaim.isAdminClaim() && (attackerClaim.getParent() != null) && GriefPreventionPlus.getInstance().config.pvp_noCombatInAdminSubdivisions) || (!attackerClaim.isAdminClaim() && GriefPreventionPlus.getInstance().config.pvp_noCombatInPlayerLandClaims))) {
						event.setIntensity(effected, 0);
						GriefPreventionPlus.sendMessage(thrower, TextMode.Err, Messages.CantFightWhileImmune);
						continue;
					}

					final Claim defenderClaim = this.dataStore.getClaimAt(effectedPlayer.getLocation(), false, defenderData.lastClaim);
					if ((defenderClaim != null) && ((defenderClaim.isAdminClaim() && (defenderClaim.getParent() == null) && GriefPreventionPlus.getInstance().config.pvp_noCombatInAdminLandClaims) || (defenderClaim.isAdminClaim() && (defenderClaim.getParent() != null) && GriefPreventionPlus.getInstance().config.pvp_noCombatInAdminSubdivisions) || (!defenderClaim.isAdminClaim() && GriefPreventionPlus.getInstance().config.pvp_noCombatInPlayerLandClaims))) {
						event.setIntensity(effected, 0);
						GriefPreventionPlus.sendMessage(thrower, TextMode.Err, Messages.PlayerInPvPSafeZone);
						continue;
					}
				}
			}
		}
	}

	// when a vehicle is damaged
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onVehicleDamage(VehicleDamageEvent event) {
		// all of this is anti theft code
		if (!GriefPreventionPlus.getInstance().config.claims_preventTheft) {
			return;
		}

		// input validation
		if (event.getVehicle() == null) {
			return;
		}

		// don't track in worlds where claims are not enabled
		if (!GriefPreventionPlus.getInstance().claimsEnabledForWorld(event.getVehicle().getWorld())) {
			return;
		}

		// determine which player is attacking, if any
		Player attacker = null;
		final Entity damageSource = event.getAttacker();
		EntityType damageSourceType = null;

		// if damage source is null or a creeper, don't allow the damage when
		// the vehicle is in a land claim
		if (damageSource != null) {
			damageSourceType = damageSource.getType();

			if (damageSource.getType() == EntityType.PLAYER) {
				attacker = (Player) damageSource;
			} else if (damageSource instanceof Projectile) {
				final Projectile arrow = (Projectile) damageSource;
				if (arrow.getShooter() instanceof Player) {
					attacker = (Player) arrow.getShooter();
				}
			}
		}

		// if not a player and not an explosion, always allow
		if ((attacker == null) && (damageSourceType != EntityType.CREEPER) && (damageSourceType != EntityType.PRIMED_TNT)) {
			return;
		}

		// NOTE: vehicles can be pushed around.
		// so unless precautions are taken by the owner, a resourceful thief
		// might find ways to steal anyway
		Claim cachedClaim = null;
		PlayerData playerData = null;

		if (attacker != null) {
			playerData = this.dataStore.getPlayerData(attacker.getUniqueId());
			cachedClaim = playerData.lastClaim;
		}

		final Claim claim = this.dataStore.getClaimAt(event.getVehicle().getLocation(), false, cachedClaim);

		// if it's claimed
		if (claim != null) {
			// if damaged by anything other than a player, cancel the event
			if (attacker == null) {
				event.setCancelled(true);
			}

			// otherwise the player damaging the entity must have permission
			else {
				final String noContainersReason = claim.canOpenContainers(attacker);
				if (noContainersReason != null) {
					event.setCancelled(true);
					String message = GriefPreventionPlus.getInstance().getDataStore().getMessage(Messages.NoDamageClaimedEntity, claim.getOwnerName());
					if (attacker.hasPermission("griefprevention.ignoreclaims")) {
						message += "  " + GriefPreventionPlus.getInstance().getDataStore().getMessage(Messages.IgnoreClaimsAdvertisement);
					}
					GriefPreventionPlus.sendMessage(attacker, TextMode.Err, message);
					event.setCancelled(true);
				}
			}
		}
	}

	// don't allow zombies to break down doors
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onZombieBreakDoor(EntityBreakDoorEvent event) {
		if (!GriefPreventionPlus.getInstance().config.zombiesBreakDoors) {
			event.setCancelled(true);
		}
	}

	private static final HashSet<PotionEffectType> positiveEffects = new HashSet<PotionEffectType>(Arrays.asList(PotionEffectType.ABSORPTION, PotionEffectType.DAMAGE_RESISTANCE, PotionEffectType.FAST_DIGGING, PotionEffectType.FIRE_RESISTANCE, PotionEffectType.HEAL, PotionEffectType.HEALTH_BOOST, PotionEffectType.INCREASE_DAMAGE, PotionEffectType.INVISIBILITY, PotionEffectType.JUMP, PotionEffectType.NIGHT_VISION, PotionEffectType.REGENERATION, PotionEffectType.SATURATION, PotionEffectType.SPEED, PotionEffectType.WATER_BREATHING));
}
