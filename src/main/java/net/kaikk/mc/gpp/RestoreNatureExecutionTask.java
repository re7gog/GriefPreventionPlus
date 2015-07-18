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

package net.kaikk.mc.gpp;

import net.kaikk.mc.gpp.visualization.Visualization;
import net.kaikk.mc.gpp.visualization.VisualizationType;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;

//this main thread task takes the output from the RestoreNatureProcessingTask\
//and updates the world accordingly
@SuppressWarnings("deprecation")
class RestoreNatureExecutionTask implements Runnable {
	// results from processing thread
	// will be applied to the world
	private final BlockSnapshot[][][] snapshots;

	// boundaries for changes
	private final int miny;
	private final Location lesserCorner;
	private final Location greaterCorner;

	// player who should be notified about the result (will see a visualization
	// when the restoration is complete)
	private final Player player;

	public RestoreNatureExecutionTask(BlockSnapshot[][][] snapshots, int miny, Location lesserCorner, Location greaterCorner, Player player) {
		this.snapshots = snapshots;
		this.miny = miny;
		this.lesserCorner = lesserCorner;
		this.greaterCorner = greaterCorner;
		this.player = player;
	}

	@Override
	public void run() {
		// apply changes to the world, but ONLY to unclaimed blocks
		// note that the edge of the results is not applied (the 1-block-wide
		// band around the outside of the chunk)
		// those data were sent to the processing thread for reference purposes,
		// but aren't part of the area selected for restoration
		Claim cachedClaim = null;
		for (int x = 1; x < (this.snapshots.length - 1); x++) {
			for (int z = 1; z < (this.snapshots[0][0].length - 1); z++) {
				for (int y = this.miny; y < this.snapshots[0].length; y++) {
					final BlockSnapshot blockUpdate = this.snapshots[x][y][z];
					final Block currentBlock = blockUpdate.location.getBlock();
					if ((blockUpdate.typeId != currentBlock.getTypeId()) || (blockUpdate.data != currentBlock.getData())) {
						final Claim claim = GriefPreventionPlus.getInstance().getDataStore().getClaimAt(blockUpdate.location, false, cachedClaim);
						if (claim != null) {
							cachedClaim = claim;
							break;
						}

						if (currentBlock.getTypeId() > 200) {
							currentBlock.setType(Material.AIR);
						} else {
							try {
								currentBlock.setTypeId(blockUpdate.typeId);
								currentBlock.setData(blockUpdate.data);
							} catch (final Exception e) {
								e.printStackTrace();
							}
						}
					}
				}
			}
		}

		// clean up any entities in the chunk, ensure no players are suffocated
		final Chunk chunk = this.lesserCorner.getChunk();
		final Entity[] entities = chunk.getEntities();
		for (int i = 0; i < entities.length; i++) {
			final Entity entity = entities[i];
			if (!((entity instanceof Player) || (entity instanceof Animals))) {
				// hanging entities (paintings, item frames) are protected when
				// they're in land claims
				if (!(entity instanceof Hanging) || (GriefPreventionPlus.getInstance().getDataStore().getClaimAt(entity.getLocation(), false) == null)) {
					// everything else is removed
					entity.remove();
				}
			}

			// for players, always ensure there's air where the player is
			// standing
			else {
				final Block feetBlock = entity.getLocation().getBlock();
				feetBlock.setType(Material.AIR);
				feetBlock.getRelative(BlockFace.UP).setType(Material.AIR);
			}
		}

		// show visualization to player who started the restoration
		if (this.player != null) {
			final Claim claim = new Claim(this.lesserCorner, this.greaterCorner, null, null, null, null, null);
			final Visualization visualization = Visualization.FromClaim(claim, this.player.getLocation().getBlockY(), VisualizationType.RestoreNature, this.player.getLocation());
			Visualization.Apply(this.player, visualization);
		}
	}
}
