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

import java.util.Date;
import java.util.UUID;
import java.util.Vector;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import net.kaikk.mc.gpp.visualization.Visualization;

//holds all of GriefPrevention's player-tied data
public class PlayerData {
	// the player's ID
	public UUID playerID;

	// the player's claims
	private Vector<Claim> claims = null;

	// how many claim blocks the player has earned via play time
	private Integer accruedClaimBlocks = null;

	// temporary holding area to avoid opening data files too early
	private int newlyAccruedClaimBlocks = 0;

	// where this player was the last time we checked on him for earning claim
	// blocks
	public Location lastAfkCheckLocation = null;

	// how many claim blocks the player has been gifted by admins, or purchased
	// via economy integration
	private Integer bonusClaimBlocks = null;

	// what "mode" the shovel is in determines what it will do when it's used
	public ShovelMode shovelMode = ShovelMode.Basic;

	// radius for restore nature fill mode
	int fillRadius = 0;

	// last place the player used the shovel, useful in creating and resizing
	// claims,
	// because the player must use the shovel twice in those instances
	public Location lastShovelLocation = null;

	// the claim this player is currently resizing
	public Claim claimResizing = null;

	// the claim this player is currently subdividing
	public Claim claimSubdividing = null;

	// whether or not the player has a pending /trapped rescue
	public boolean pendingTrapped = false;

	// whether this player was recently warned about building outside land
	// claims
	boolean warnedAboutBuildingOutsideClaims = false;

	// visualization
	public Visualization currentVisualization = null;

	// ignore claims mode
	public boolean ignoreClaims = false;

	// the last claim this player was in, that we know of
	public Claim lastClaim = null;

	// safety confirmation for deleting multi-subdivision claims
	public boolean warnedAboutMajorDeletion = false;

	// message to send to player after he respawns
	String messageOnRespawn = null;

	// player which a pet will be given to when it's right-clicked
	OfflinePlayer petGiveawayRecipient = null;

	// timestamp for last "you're building outside your land claims" message
	Long buildWarningTimestamp = null;

	UUID lastWorld;
	int lastX, lastZ;
	
	public long lastSeen;

	public PlayerData(UUID playerID) {
		this.playerID = playerID;
		this.lastSeen = System.currentTimeMillis();
		this.initLastLocation();
	}

	public PlayerData(UUID playerID, Integer accruedClaimBlocks, Integer bonusClaimBlocks, long lastSeen) {
		this.playerID = playerID;
		this.accruedClaimBlocks = accruedClaimBlocks;
		this.bonusClaimBlocks = bonusClaimBlocks;
		this.lastSeen = lastSeen;
		this.initLastLocation();
	}
	
	void initLastLocation() {
		final Player player = GriefPreventionPlus.getInstance().getServer().getPlayer(this.playerID);
		if (player==null) {
			return;
		}
		this.lastX=player.getLocation().getBlockX();
		this.lastZ=player.getLocation().getBlockZ();
		this.lastWorld=player.getLocation().getWorld().getUID();
	}

	public void accrueBlocks(int howMany) {
		this.newlyAccruedClaimBlocks += howMany;
	}

	// don't load data from secondary storage until it's needed
	public int getAccruedClaimBlocks() {
		if (this.accruedClaimBlocks == null) {
			this.loadDataFromSecondaryStorage();
		}

		// move any in the holding area
		int newTotal = this.accruedClaimBlocks + this.newlyAccruedClaimBlocks;
		this.newlyAccruedClaimBlocks = 0;

		// respect limits
		if (newTotal > GriefPreventionPlus.getInstance().config.claims_maxAccruedBlocks) {
			newTotal = GriefPreventionPlus.getInstance().config.claims_maxAccruedBlocks;
		}
		this.accruedClaimBlocks = newTotal;

		return this.accruedClaimBlocks;
	}

	public int getBonusClaimBlocks() {
		if (this.bonusClaimBlocks == null) {
			this.loadDataFromSecondaryStorage();
		}
		return this.bonusClaimBlocks;
	}

	public Vector<Claim> getClaims() {
		if (this.claims == null) {
			int totalClaimsArea = 0;
			this.claims = new Vector<Claim>();

			// find all the claims belonging to this player and note them for
			// future reference
			for (final Claim claim : GriefPreventionPlus.getInstance().getDataStore().claims.values()) {
				if (this.playerID.equals(claim.getOwnerID())) {
					this.claims.add(claim);
					totalClaimsArea += claim.getArea();
				}
			}

			// ensure player has claim blocks for his claims, and at least the
			// minimum accrued
			this.loadDataFromSecondaryStorage();

			// if total claimed area is more than total blocks available
			int totalBlocks = this.accruedClaimBlocks + this.getBonusClaimBlocks() + GriefPreventionPlus.getInstance().getDataStore().getGroupBonusBlocks(this.playerID);
			if (totalBlocks < totalClaimsArea) {
				// try to fix it by adding to accrued blocks
				this.accruedClaimBlocks = totalClaimsArea;
				if (this.accruedClaimBlocks > GriefPreventionPlus.getInstance().config.claims_maxAccruedBlocks) {
					// remember to respect the maximum on accrued blocks
					this.accruedClaimBlocks = GriefPreventionPlus.getInstance().config.claims_maxAccruedBlocks;
				}

				// if that didn't fix it, then make up the difference with bonus
				// blocks
				totalBlocks = this.accruedClaimBlocks + this.getBonusClaimBlocks() + GriefPreventionPlus.getInstance().getDataStore().getGroupBonusBlocks(this.playerID);
				if (totalBlocks < totalClaimsArea) {
					this.bonusClaimBlocks += totalClaimsArea - totalBlocks;
				}
			}
		}

		return this.claims;
	}

	public Date getLastLogin() {
		return new Date(this.lastSeen);
	}

	// the number of claim blocks a player has available for claiming land
	public int getRemainingClaimBlocks() {
		// accrued blocks + bonus blocks + permission bonus blocks
		int remainingBlocks = this.getAccruedClaimBlocks() + this.getBonusClaimBlocks() + GriefPreventionPlus.getInstance().getDataStore().getGroupBonusBlocks(this.playerID);
		for (final Claim claim : this.getClaims()) {
			remainingBlocks -= claim.getArea();
		}

		return remainingBlocks;
	}

	public long getTimeLastLogin() {
		return GriefPreventionPlus.getInstance().getServer().getOfflinePlayer(this.playerID).getLastPlayed();
	}

	public void setAccruedClaimBlocks(Integer accruedClaimBlocks) {
		this.accruedClaimBlocks = accruedClaimBlocks;
		this.newlyAccruedClaimBlocks = 0;
	}

	public void setBonusClaimBlocks(Integer bonusClaimBlocks) {
		this.bonusClaimBlocks = bonusClaimBlocks;
	}

	private void loadDataFromSecondaryStorage() {
		// reach out to secondary storage to get any data there
		PlayerData storageData = GriefPreventionPlus.getInstance().getDataStore().getPlayerDataFromStorage(this.playerID);

		if (storageData == null) {
			// initialize new player data
			storageData = new PlayerData(this.playerID);

			// shove that new player data into the hash map cache
			GriefPreventionPlus.getInstance().getDataStore().playersData.put(this.playerID, storageData);
		}

		if (this.accruedClaimBlocks == null) {
			if (storageData.accruedClaimBlocks != null) {
				this.accruedClaimBlocks = storageData.accruedClaimBlocks;
				// ensure at least minimum accrued are accrued (in case of
				// settings changes to increase initial amount)
				if (this.accruedClaimBlocks < GriefPreventionPlus.getInstance().config.claims_initialBlocks) {
					this.accruedClaimBlocks = GriefPreventionPlus.getInstance().config.claims_initialBlocks;
				}
			} else {
				this.accruedClaimBlocks = GriefPreventionPlus.getInstance().config.claims_initialBlocks;
			}
		}

		if (this.bonusClaimBlocks == null) {
			if (storageData.bonusClaimBlocks != null) {
				this.bonusClaimBlocks = storageData.bonusClaimBlocks;
			} else {
				this.bonusClaimBlocks = 0;
			}
		}
	}
}
