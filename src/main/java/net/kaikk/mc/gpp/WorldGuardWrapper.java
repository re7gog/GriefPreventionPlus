package net.kaikk.mc.gpp;

import com.sk89q.worldedit.BlockVector;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.permission.RegionPermissionModel;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;

class WorldGuardWrapper {
	private WorldGuardPlugin worldGuard = null;

	public WorldGuardWrapper() throws ClassNotFoundException {
		this.worldGuard = (WorldGuardPlugin) GriefPreventionPlus.getInstance().getServer().getPluginManager().getPlugin("WorldGuard");
	}

	public boolean canBuild(World world, int lesserX, int lesserZ, int greaterX, int greaterZ, Player creatingPlayer) {
		if (new RegionPermissionModel(this.worldGuard, creatingPlayer).mayIgnoreRegionProtection(world)) {
			return true;
		}

		final RegionManager manager = this.worldGuard.getRegionManager(world);

		if (manager != null) {
			final ProtectedCuboidRegion tempRegion = new ProtectedCuboidRegion("GP_TEMP", new BlockVector(lesserX, 0, lesserZ), new BlockVector(greaterX, world.getMaxHeight(), greaterZ));
			final ApplicableRegionSet overlaps = manager.getApplicableRegions(tempRegion);
			final LocalPlayer localPlayer = this.worldGuard.wrapPlayer(creatingPlayer);
			return overlaps.testState(localPlayer, DefaultFlag.BUILD);
		}

		return true;
	}
}
