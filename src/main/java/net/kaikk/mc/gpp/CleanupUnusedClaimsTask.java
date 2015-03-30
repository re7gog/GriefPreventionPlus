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

import java.util.Iterator;

import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

public class CleanupUnusedClaimsTask extends BukkitRunnable {
	GriefPreventionPlus instance;
	Iterator<Claim> iterator;
	static int areaOfDefaultClaim = 0;
	static int count=0;
	
	static long claimsRemMillisecs=GriefPreventionPlus.instance.config_claims_expirationDays*86400000L;
	static long chestMillisecs=GriefPreventionPlus.instance.config_claims_chestClaimExpirationDays*86400000L;

	CleanupUnusedClaimsTask(GriefPreventionPlus instance) {
		this.instance = instance;
		if (this.instance.dataStore.claims.size()!=0) {
			if(this.instance.config_claims_automaticClaimsForNewPlayersRadius >= 0) { //determine area of the default chest claim
				areaOfDefaultClaim = (int)Math.pow(this.instance.config_claims_automaticClaimsForNewPlayersRadius * 2 + 1, 2);  
			}
			count=0;
			if (claimsRemMillisecs==0&&chestMillisecs==0) {
				this.iterator = null;
			} else {
				this.iterator = this.instance.dataStore.claims.values().iterator();
			}
		} else {
			this.iterator = null;
		}
	}
	
	CleanupUnusedClaimsTask(GriefPreventionPlus instance, Iterator<Claim> iterator) {
		this.instance = instance;
		this.iterator = iterator;
	}

	@Override
	public void run() {
		if(this.iterator==null) {
			new CleanupUnusedClaimsTask(this.instance).runTaskLater(instance, 20L*60*60*12);
			return;
		}

		if (this.iterator.hasNext()) {
			Claim claim = this.iterator.next();
			if (!claim.isAdminClaim()) {
				OfflinePlayer player = this.instance.getServer().getOfflinePlayer(claim.ownerID);
				PlayerData playerData = this.instance.dataStore.getPlayerData(claim.ownerID);
				if (player!=null) {
					long timeElapsed=System.currentTimeMillis()-player.getLastPlayed();

					if (
							(claimsRemMillisecs > 0 && timeElapsed>claimsRemMillisecs) ||
							(chestMillisecs > 0 && timeElapsed>chestMillisecs && claim.getArea() <= areaOfDefaultClaim  && (playerData==null || playerData.getClaims().size()==1))
						) {
						claim.removeSurfaceFluids(null);
						this.instance.dataStore.deleteClaim(claim, true);
						if(this.instance.creativeRulesApply(claim.world) || this.instance.config_claims_survivalAutoNatureRestoration) {
							this.instance.restoreClaim(claim, 0);
						}
						count++;
						GriefPreventionPlus.addLogEntry("Claim ID ["+claim.id+"] at "+claim.locationToString()+" owned by " + claim.getOwnerName() + " has expired.");
					}
				}
			}
			new CleanupUnusedClaimsTask(this.instance, this.iterator).runTaskLater(instance, 20L);
		} else {
			GriefPreventionPlus.addLogEntry("Claims cleanup task completed. Removed "+count+" claims.");
			new CleanupUnusedClaimsTask(this.instance).runTaskLater(instance, 20L*60*60*12);
		}
	}
}
