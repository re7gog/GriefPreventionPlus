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

import net.kaikk.mc.gpp.GriefPreventionPlus;
import net.kaikk.mc.gpp.PlayerData;

import org.bukkit.entity.Player;

//applies a visualization for a player by sending him block change packets
@SuppressWarnings("deprecation")
class VisualizationApplicationTask implements Runnable {
	private final Visualization visualization;
	private final Player player;
	private final PlayerData playerData;

	public VisualizationApplicationTask(Player player, PlayerData playerData, Visualization visualization) {
		this.visualization = visualization;
		this.playerData = playerData;
		this.player = player;
	}

	@Override
	public void run() {
		// for each element (=block) of the visualization
		for (int i = 0; i < this.visualization.elements.size(); i++) {
			final VisualizationElement element = this.visualization.elements.get(i);

			// send the player a fake block change event
			if (!element.location.getChunk().isLoaded()) {
				continue; // cheap distance check
			}
			this.player.sendBlockChange(element.location, element.visualizedMaterial, element.visualizedData);
		}

		// remember the visualization applied to this player for later (so it
		// can be inexpensively reverted)
		this.playerData.currentVisualization = this.visualization;

		// schedule automatic visualization reversion in 60 seconds.
		GriefPreventionPlus.getInstance().getServer().getScheduler().scheduleSyncDelayedTask(GriefPreventionPlus.getInstance(), new VisualizationReversionTask(this.player, this.playerData, this.visualization), 20L * 60); // 60
																																																								// seconds
	}
}
