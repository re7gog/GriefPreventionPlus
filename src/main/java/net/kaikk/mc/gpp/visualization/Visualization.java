/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2015 Ryan Hamshire

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

package net.kaikk.mc.gpp.visualization;

import java.util.ArrayList;

import net.kaikk.mc.gpp.Claim;
import net.kaikk.mc.gpp.GriefPreventionPlus;
import net.kaikk.mc.gpp.PlayerData;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

//represents a visualization sent to a player
//FEATURE: to show players visually where claim boundaries are, we send them fake block change packets
//the result is that those players see new blocks, but the world hasn't been changed.  other players can't see the new blocks, either.
@SuppressWarnings("deprecation")
public class Visualization {
	public ArrayList<VisualizationElement> elements = new ArrayList<VisualizationElement>();

	// adds a claim's visualization to the current visualization
	// handy for combining several visualizations together, as when
	// visualization a top level claim with several subdivisions inside
	// locality is a performance consideration. only create visualization blocks
	// for around 100 blocks of the locality
	private void addClaimElements(Claim claim, int height, VisualizationType visualizationType, Location locality) {
		final Location smallXsmallZ = claim.getLesserBoundaryCorner();
		final Location bigXbigZ = claim.getGreaterBoundaryCorner();
		final World world = smallXsmallZ.getWorld();
		final boolean waterIsTransparent = locality.getBlock().getType() == Material.STATIONARY_WATER;

		final int smallx = smallXsmallZ.getBlockX();
		final int smallz = smallXsmallZ.getBlockZ();
		final int bigx = bigXbigZ.getBlockX();
		final int bigz = bigXbigZ.getBlockZ();

		Material cornerMaterial;
		Material accentMaterial;

		final ArrayList<VisualizationElement> newElements = new ArrayList<VisualizationElement>();

		if (visualizationType == VisualizationType.Claim) {
			cornerMaterial = Material.GLOWSTONE;
			accentMaterial = Material.GOLD_BLOCK;
		}

		else if (visualizationType == VisualizationType.AdminClaim) {
			cornerMaterial = Material.GLOWSTONE;
			accentMaterial = Material.PUMPKIN;
		}

		else if (visualizationType == VisualizationType.Subdivision) {
			cornerMaterial = Material.IRON_BLOCK;
			accentMaterial = Material.WOOL;
		}

		else if (visualizationType == VisualizationType.RestoreNature) {
			cornerMaterial = Material.DIAMOND_BLOCK;
			accentMaterial = Material.DIAMOND_BLOCK;
		}

		else {
			cornerMaterial = Material.GLOWING_REDSTONE_ORE;
			accentMaterial = Material.NETHERRACK;
		}

		// initialize visualization elements without Y values and real data
		// that will be added later for only the visualization elements within
		// visualization range

		// locality
		final int minx = locality.getBlockX() - 100;
		final int minz = locality.getBlockZ() - 100;
		final int maxx = locality.getBlockX() + 100;
		final int maxz = locality.getBlockZ() + 100;

		final int STEP = 10;

		// top line
		for (int x = smallx + STEP; x < (bigx - (STEP / 2)); x += STEP) {
			if ((x > minx) && (x < maxx)) {
				newElements.add(new VisualizationElement(new Location(world, x, 0, bigz), accentMaterial, (byte) 0, Material.AIR, (byte) 0));
			}
		}

		// bottom line
		for (int x = smallx + STEP; x < (bigx - (STEP / 2)); x += STEP) {
			if ((x > minx) && (x < maxx)) {
				newElements.add(new VisualizationElement(new Location(world, x, 0, smallz), accentMaterial, (byte) 0, Material.AIR, (byte) 0));
			}
		}

		// left line
		for (int z = smallz + STEP; z < (bigz - (STEP / 2)); z += STEP) {
			if ((z > minz) && (z < maxz)) {
				newElements.add(new VisualizationElement(new Location(world, smallx, 0, z), accentMaterial, (byte) 0, Material.AIR, (byte) 0));
			}
		}

		// right line
		for (int z = smallz + STEP; z < (bigz - (STEP / 2)); z += STEP) {
			if ((z > minz) && (z < maxz)) {
				newElements.add(new VisualizationElement(new Location(world, bigx, 0, z), accentMaterial, (byte) 0, Material.AIR, (byte) 0));
			}
		}

		// bottom left corner leaves
		newElements.add(new VisualizationElement(new Location(world, smallx + 1, 0, smallz), accentMaterial, (byte) 0, Material.AIR, (byte) 0));
		newElements.add(new VisualizationElement(new Location(world, smallx, 0, smallz + 1), accentMaterial, (byte) 0, Material.AIR, (byte) 0));

		// bottom right corner leaves
		newElements.add(new VisualizationElement(new Location(world, bigx - 1, 0, smallz), accentMaterial, (byte) 0, Material.AIR, (byte) 0));
		newElements.add(new VisualizationElement(new Location(world, bigx, 0, smallz + 1), accentMaterial, (byte) 0, Material.AIR, (byte) 0));

		// top right corner leaves
		newElements.add(new VisualizationElement(new Location(world, bigx - 1, 0, bigz), accentMaterial, (byte) 0, Material.AIR, (byte) 0));
		newElements.add(new VisualizationElement(new Location(world, bigx, 0, bigz - 1), accentMaterial, (byte) 0, Material.AIR, (byte) 0));

		// top left corner leaves
		newElements.add(new VisualizationElement(new Location(world, smallx + 1, 0, bigz), accentMaterial, (byte) 0, Material.AIR, (byte) 0));
		newElements.add(new VisualizationElement(new Location(world, smallx, 0, bigz - 1), accentMaterial, (byte) 0, Material.AIR, (byte) 0));

		// corners
		newElements.add(new VisualizationElement(new Location(world, smallx, 0, smallz), cornerMaterial, (byte) 0, Material.AIR, (byte) 0));
		newElements.add(new VisualizationElement(new Location(world, bigx, 0, smallz), cornerMaterial, (byte) 0, Material.AIR, (byte) 0));
		newElements.add(new VisualizationElement(new Location(world, bigx, 0, bigz), cornerMaterial, (byte) 0, Material.AIR, (byte) 0));
		newElements.add(new VisualizationElement(new Location(world, smallx, 0, bigz), cornerMaterial, (byte) 0, Material.AIR, (byte) 0));

		// remove any out of range elements (the corners may be out of range)
		this.removeElementsOutOfRange(newElements, minx, minz, maxx, maxz);

		// remove any elements outside the claim
		for (int i = 0; i < newElements.size(); i++) {
			final VisualizationElement element = newElements.get(i);
			if (!claim.contains(element.location, true, false)) {
				newElements.remove(i--);
			}
		}

		// set Y values and real block information for any remaining
		// visualization blocks
		for (final VisualizationElement element : newElements) {
			final Location tempLocation = element.location;
			element.location = getVisibleLocation(tempLocation.getWorld(), tempLocation.getBlockX(), height, tempLocation.getBlockZ(), waterIsTransparent);
			element.realMaterial = element.location.getBlock().getType();
			element.realData = element.location.getBlock().getData();
		}

		this.elements.addAll(newElements);
	}

	// removes any elements which are out of visualization range
	private void removeElementsOutOfRange(ArrayList<VisualizationElement> elements, int minx, int minz, int maxx, int maxz) {
		for (int i = 0; i < elements.size(); i++) {
			final Location location = elements.get(i).location;
			if ((location.getX() < minx) || (location.getX() > maxx) || (location.getZ() < minz) || (location.getZ() > maxz)) {
				elements.remove(i--);
			}
		}
	}

	// sends a visualization to a player
	public static void Apply(Player player, Visualization visualization) {
		final PlayerData playerData = GriefPreventionPlus.getInstance().getDataStore().getPlayerData(player.getUniqueId());

		// if he has any current visualization, clear it first
		if (playerData.currentVisualization != null) {
			Visualization.Revert(player);
		}

		// if he's online, create a task to send him the visualization
		if (player.isOnline() && (visualization.elements.size() > 0) && visualization.elements.get(0).location.getWorld().equals(player.getWorld())) {
			GriefPreventionPlus.getInstance().getServer().getScheduler().scheduleSyncDelayedTask(GriefPreventionPlus.getInstance(), new VisualizationApplicationTask(player, playerData, visualization), 1L);
		}
	}

	// convenience method to build a visualization from a claim
	// visualizationType determines the style (gold blocks, silver, red,
	// diamond, etc)
	public static Visualization FromClaim(Claim claim, int height, VisualizationType visualizationType, Location locality) {
		// visualize only top level claims
		if (claim.getParent() != null) {
			return FromClaim(claim.getParent(), height, visualizationType, locality);
		}

		final Visualization visualization = new Visualization();

		// add subdivisions first
		for (int i = 0; i < claim.getChildren().size(); i++) {
			visualization.addClaimElements(claim.getChildren().get(i), height, VisualizationType.Subdivision, locality);
		}

		// special visualization for administrative land claims
		if (claim.isAdminClaim() && (visualizationType == VisualizationType.Claim)) {
			visualizationType = VisualizationType.AdminClaim;
		}

		// add top level last so that it takes precedence (it shows on top when
		// the child claim boundaries overlap with its boundaries)
		visualization.addClaimElements(claim, height, visualizationType, locality);

		return visualization;
	}

	public static Visualization fromClaims(Iterable<Claim> claims, int height, VisualizationType type, Location locality) {
		final Visualization visualization = new Visualization();

		for (final Claim claim : claims) {
			visualization.addClaimElements(claim, height, type, locality);
		}

		return visualization;
	}

	// reverts a visualization by sending another block change list, this time
	// with the real world block values
	public static void Revert(Player player) {
		if (!player.isOnline()) {
			return;
		}

		final PlayerData playerData = GriefPreventionPlus.getInstance().getDataStore().getPlayerData(player.getUniqueId());

		final Visualization visualization = playerData.currentVisualization;

		if (playerData.currentVisualization != null) {

			// locality
			final int minx = player.getLocation().getBlockX() - 100;
			final int minz = player.getLocation().getBlockZ() - 100;
			final int maxx = player.getLocation().getBlockX() + 100;
			final int maxz = player.getLocation().getBlockZ() + 100;

			// remove any elements which are too far away
			visualization.removeElementsOutOfRange(visualization.elements, minx, minz, maxx, maxz);

			// send real block information for any remaining elements
			for (int i = 0; i < visualization.elements.size(); i++) {
				final VisualizationElement element = visualization.elements.get(i);

				// check player still in world where visualization exists
				if (i == 0) {
					if (!player.getWorld().equals(element.location.getWorld())) {
						return;
					}
				}

				player.sendBlockChange(element.location, element.realMaterial, element.realData);
			}
		}

		playerData.currentVisualization = null;

	}

	// finds a block the player can probably see. this is how visualizations
	// "cling" to the ground or ceiling
	private static Location getVisibleLocation(World world, int x, int y, int z, boolean waterIsTransparent) {
		Block block = world.getBlockAt(x, y, z);
		final BlockFace direction = (isTransparent(block, waterIsTransparent)) ? BlockFace.DOWN : BlockFace.UP;

		while ((block.getY() >= 1) && (block.getY() < (world.getMaxHeight() - 1)) && (!isTransparent(block.getRelative(BlockFace.UP), waterIsTransparent) || isTransparent(block, waterIsTransparent))) {
			block = block.getRelative(direction);
		}

		return block.getLocation();
	}

	// helper method for above. allows visualization blocks to sit underneath
	// partly transparent blocks like grass and fence
	private static boolean isTransparent(Block block, boolean waterIsTransparent) {
		return ((block.getType() != Material.SNOW) && ((block.getType() == Material.AIR) || (block.getType() == Material.FENCE) || (waterIsTransparent && (block.getType() == Material.STATIONARY_WATER)) || block.getType().isTransparent()));
	}
}
