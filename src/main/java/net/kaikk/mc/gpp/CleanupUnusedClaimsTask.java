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

import java.util.ConcurrentModificationException;
import java.util.Iterator;

import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

class CleanupUnusedClaimsTask extends BukkitRunnable {
	GriefPreventionPlus instance;
	Iterator<Claim> iterator;
	int areaOfDefaultClaim = 0;
	int count = 0;

	long claimsRemMillisecs = GriefPreventionPlus.getInstance().config.claims_expirationDays * 86400000L;
	long chestMillisecs = GriefPreventionPlus.getInstance().config.claims_chestClaimExpirationDays * 86400000L;

	CleanupUnusedClaimsTask(GriefPreventionPlus instance) {
		if ((this.claimsRemMillisecs == 0) && (this.chestMillisecs == 0)) {
			this.cancel();
			return;
		}
		
		this.instance = instance;
		this.count = 0;
		
		if (this.instance.config.claims_automaticClaimsForNewPlayersRadius >= 0) { 
			// determine area of the default chest claim
			this.areaOfDefaultClaim = (int) Math.pow((this.instance.config.claims_automaticClaimsForNewPlayersRadius * 2) + 1, 2);
		}
	}

	CleanupUnusedClaimsTask(GriefPreventionPlus instance, Iterator<Claim> iterator) {
		this.instance = instance;
		this.iterator = iterator;
	}

	@Override
	public void run() {
		if (this.iterator == null) {
			this.iterator = this.instance.getDataStore().claims.values().iterator();
		}

		try {
			if (this.iterator.hasNext()) {
				final Claim claim = this.iterator.next();
				if (!claim.isAdminClaim()) {
					final OfflinePlayer player = this.instance.getServer().getOfflinePlayer(claim.getOwnerID());
					final PlayerData playerData = this.instance.getDataStore().getPlayerData(claim.getOwnerID());
					if (player != null) {
						if (player.getLastPlayed() != 0) {
							final long timeElapsed = System.currentTimeMillis() - player.getLastPlayed();

							if (((this.claimsRemMillisecs > 0) && (timeElapsed > this.claimsRemMillisecs)) || ((this.chestMillisecs > 0) && (timeElapsed > this.chestMillisecs) && (claim.getArea() <= this.areaOfDefaultClaim) && ((playerData == null) || (playerData.getClaims().size() == 1)))) {
								claim.removeSurfaceFluids(null);
								this.instance.getDataStore().deleteClaim(claim);
								if (this.instance.creativeRulesApply(claim.getWorldUID()) || this.instance.config.claims_survivalAutoNatureRestoration) {
									this.instance.restoreClaim(claim, 0);
								}
								this.count++;
								GriefPreventionPlus.addLogEntry("Claim ID [" + claim.id + "] at " + claim.locationToString() + " owned by " + claim.getOwnerName() + " has expired.");
							}
						} else {
							GriefPreventionPlus.addLogEntry(player.getName() + "'s LastPlayed is 0. Claim ID [" + claim.id + "] expiration skipped.");
						}
					}
				}
			} else {
				GriefPreventionPlus.addLogEntry("Claims cleanup task completed. Removed " + this.count + " claims.");
				this.reschedule();
				return;
			}
		} catch (final ConcurrentModificationException e) {
			new CleanupUnusedClaimsTask(this.instance).runTask(this.instance);
			this.cancel();
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	void reschedule() {
		new CleanupUnusedClaimsTask(this.instance).runTaskTimer(this.instance, 20L * 60 * 60 * 12, 4L);
		this.cancel();
	}
}
