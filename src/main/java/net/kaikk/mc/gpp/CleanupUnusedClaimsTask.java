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
	
	CleanupUnusedClaimsTask(GriefPreventionPlus instance) {
		this.instance = instance;
		if (this.instance.dataStore.claims.size()!=0) {
			this.iterator = this.instance.dataStore.claims.values().iterator();
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

		//determine area of the default chest claim
		int areaOfDefaultClaim = 0;
		if(this.instance.config_claims_automaticClaimsForNewPlayersRadius >= 0) {
			areaOfDefaultClaim = (int)Math.pow(this.instance.config_claims_automaticClaimsForNewPlayersRadius * 2 + 1, 2);  
		}
		
		int claimsRemDays=GriefPreventionPlus.instance.config_claims_expirationDays;
		long claimsRemMillisecs=claimsRemDays*2592000000L;
		
		int chestDays=this.instance.config_claims_chestClaimExpirationDays;
		long chestMillisecs=chestDays*2592000000L;
		
		/*int claimsUnusedDays=GriefPreventionPlus.instance.config_claims_unusedClaimExpirationDays;
		long claimsUnusedMillisecs=claimsUnusedDays*2592000000L;*/

		if (claimsRemDays==0&&chestDays==0/*&&claimsUnusedDays==0*/) {
			return;
		}
		
		if (this.iterator.hasNext()) {
			Claim claim = this.iterator.next();
			if (!claim.isAdminClaim()) {
				OfflinePlayer player = this.instance.getServer().getOfflinePlayer(claim.ownerID);
				PlayerData playerData = this.instance.dataStore.getPlayerData(claim.ownerID);
				if (player!=null) {
					long timeElapsed=System.currentTimeMillis()-player.getLastPlayed();
					/*GriefPreventionPlus.addLogEntry(
					"("+claimsRemDays+" > 0 && "+timeElapsed+">"+String.valueOf(claimsRemMillisecs)+") ||\n"+
					"("+chestDays+" > 0 && "+timeElapsed+">"+chestMillisecs+" && "+claim.getArea()+" <= "+areaOfDefaultClaim+"  && ("+playerData+"==null || "+playerData.getClaims().size()+"==1)) ||\n"+
					"("+claimsUnusedDays+" > 0 && "+timeElapsed+">"+claimsUnusedMillisecs+" && "+claim.getArea()+">500 && ("+playerData+"==null || "+playerData.getClaims().size()+"==1) && "+claim.getPlayerInvestmentScore()+"<400)"
					);*/
					
					if (
							(claimsRemDays > 0 && timeElapsed>claimsRemMillisecs) ||
							(chestDays > 0 && timeElapsed>chestMillisecs && claim.getArea() <= areaOfDefaultClaim  && (playerData==null || playerData.getClaims().size()==1))
							//(claimsUnusedDays > 0 && timeElapsed>claimsUnusedMillisecs && claim.getArea()>500 && (playerData==null || playerData.getClaims().size()==1) && claim.getPlayerInvestmentScore()<400)
						) {
						claim.removeSurfaceFluids(null);
						this.instance.dataStore.deleteClaim(claim, true);
						if(this.instance.creativeRulesApply(claim.world) || this.instance.config_claims_survivalAutoNatureRestoration) {
							this.instance.restoreClaim(claim, 0);
						}
						GriefPreventionPlus.addLogEntry("Claim ID ["+claim.id+"] at "+claim.locationToString()+" owned by " + claim.getOwnerName() + " has expired.");
					}
				} else {
					GriefPreventionPlus.addLogEntry("WARN: Claim ID ["+claim.id+"] at "+claim.locationToString()+" is orphan (no player dat found on the server).");
				}
			}
			
			new CleanupUnusedClaimsTask(this.instance, this.iterator).runTaskLater(instance, 20L);
		} else {
			new CleanupUnusedClaimsTask(this.instance).runTaskLater(instance, 20L*60*60*12);
		}
	}
}