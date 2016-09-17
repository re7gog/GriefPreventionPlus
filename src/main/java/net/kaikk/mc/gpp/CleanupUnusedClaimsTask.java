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
import java.util.Iterator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

import net.kaikk.mc.gpp.events.ClaimDeleteEvent;
import net.kaikk.mc.gpp.events.ClaimDeleteEvent.Reason;

class CleanupUnusedClaimsTask extends BukkitRunnable {
	GriefPreventionPlus instance;
	int areaOfDefaultClaim = 0;

	long claimsRemMillisecs = GriefPreventionPlus.getInstance().config.claims_expirationDays * 86400000L;
	long chestMillisecs = GriefPreventionPlus.getInstance().config.claims_chestClaimExpirationDays * 86400000L;
	
	List<Claim> claimsToRemove;
	
	Iterator<Claim> iterator;

	/** This constructor takes time to check claims to remove, then it'll remove claims
	 *  when you run this task, one claim for each execution (use runTaskTimer!), until it removed all the claims.
	 *  This is thread-unsafe, so don't use asynchronous calls.
	 * */
	CleanupUnusedClaimsTask(GriefPreventionPlus instance) {
		this.instance = instance;
		
		if (this.instance.config.claims_automaticClaimsForNewPlayersRadius >= 0) { 
			// determine area of the default chest claim
			this.areaOfDefaultClaim = (int) Math.pow((this.instance.config.claims_automaticClaimsForNewPlayersRadius * 2) + 1, 2);
		}
		
		this.claimsToRemove = new ArrayList<Claim>();
		
		GriefPreventionPlus.addLogEntry("Unused claims task start! Calculating claims to remove...");
		
		long time = System.currentTimeMillis();
		
		for (Claim claim : this.instance.getDataStore().claims.values()) {
			if (!claim.isAdminClaim()) {
				final PlayerData playerData = this.instance.getDataStore().getPlayerData(claim.getOwnerID());
				if (playerData.lastSeen != 0) {
					final long timeElapsed = System.currentTimeMillis() - playerData.lastSeen;
					if ((this.claimsRemMillisecs > 0 && timeElapsed > this.claimsRemMillisecs) || (this.chestMillisecs > 0 && timeElapsed > this.chestMillisecs && claim.getArea() <= this.areaOfDefaultClaim && (playerData == null || playerData.getClaims().size() == 1))) {
						final OfflinePlayer player = this.instance.getServer().getOfflinePlayer(claim.getOwnerID());
						if (!instance.hasPermission(player, "griefprevention.skipclaimexpiration")) {
							this.claimsToRemove.add(claim);
							if (this.claimsToRemove.size() > 100) { // do not remove more than 100 claims every time
								break;
							}
						}
					}
				} else {
					playerData.lastSeen = System.currentTimeMillis(); // reset the lastSeen to "today"
				}
			}
		}
		
		GriefPreventionPlus.addLogEntry("Found "+this.claimsToRemove.size()+" claims to be removed ("+(System.currentTimeMillis()-time)+" ms.)");
		
		this.iterator=this.claimsToRemove.iterator();
	}

	@Override
	public void run() {
		try {
			if (this.iterator.hasNext()) {
				Claim claim = this.iterator.next();
				// call event
				ClaimDeleteEvent event = new ClaimDeleteEvent(claim, null, Reason.EXPIRED);
				Bukkit.getPluginManager().callEvent(event);
				if (event.isCancelled()) {
					return;
				}
				claim.removeSurfaceFluids(null);
				this.instance.getDataStore().deleteClaim(claim);
				if (this.instance.creativeRulesApply(claim.getWorld()) || this.instance.config.claims_survivalAutoNatureRestoration) {
					this.instance.restoreClaim(claim, 0);
				}
				//this.count++;
				GriefPreventionPlus.addLogEntry("Claim ID [" + claim.id + "] at " + claim.locationToString() + " owned by " + claim.getOwnerName() + " has expired.");
			} else {
				GriefPreventionPlus.addLogEntry("Claims cleanup task completed. Removed " + this.claimsToRemove.size() + " claims.");
				this.reschedule();
				return;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	void reschedule() {
		this.cancel();
		new BukkitRunnable() {
			@Override
			public void run() {
				new CleanupUnusedClaimsTask(instance).runTaskTimer(instance, 4L, 4L);
			}
		}.runTaskLater(this.instance, 20L * 60 * 60 * 12);
	}
}
