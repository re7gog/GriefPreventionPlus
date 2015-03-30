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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

class Restrictor {
	private final static String dataDirPath = "plugins" + File.separator + "GriefPreventionData" + File.separator + "Restrictor" + File.separator;
	private final static String rangedFilePath = dataDirPath + "ranged.dat";
	private final static String aoeFilePath = dataDirPath + "aoe.dat";
	
	private ArrayList<RangedItem> rangedItems = new ArrayList<RangedItem>();
	private ArrayList<RangedItem> aoeItems = new ArrayList<RangedItem>();
    
    Restrictor() {
    	this.loadRanged();
    	this.loadAoe();
    }
    
    @SuppressWarnings("unchecked")
	void loadRanged() {
		try {
			new File(dataDirPath).mkdirs();
			FileInputStream fis = new FileInputStream(rangedFilePath);
			ObjectInputStream ois = new ObjectInputStream(fis);
			this.rangedItems = (ArrayList<RangedItem>) ois.readObject();
			ois.close();
		} catch (FileNotFoundException e) {
			this.rangedItems = new ArrayList<RangedItem>();
		} catch (Exception e) {
			e.printStackTrace();
			this.rangedItems = new ArrayList<RangedItem>();
			GriefPreventionPlus.addLogEntry("Couldn't read restricted ranged items data file.");
		}
    }
    
    
    @SuppressWarnings("unchecked")
	void loadAoe() {
		try {
			new File(dataDirPath).mkdirs();
			FileInputStream fis = new FileInputStream(aoeFilePath);
			ObjectInputStream ois = new ObjectInputStream(fis);
			this.aoeItems = (ArrayList<RangedItem>) ois.readObject();
			ois.close();
		} catch (FileNotFoundException e) {
			this.aoeItems = new ArrayList<RangedItem>();
		} catch (Exception e) {
			e.printStackTrace();
			this.aoeItems = new ArrayList<RangedItem>();
			GriefPreventionPlus.addLogEntry("Couldn't read restricted aoe items data file.");
		}
    }
    
    void saveRanged() {
		try {
			FileOutputStream fileOut = new FileOutputStream(rangedFilePath);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(this.rangedItems);
			out.close();
			fileOut.close();
		
		} catch (IOException e) {
			GriefPreventionPlus.addLogEntry("Couldn't save restricted ranged items data file.");
			e.printStackTrace();
		}
    }
    
    void saveAoe() {
		try {
			FileOutputStream fileOut = new FileOutputStream(aoeFilePath);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(this.aoeItems);
			out.close();
			fileOut.close();
		} catch (IOException e) {
			GriefPreventionPlus.addLogEntry("Couldn't save restricted aoe items data file.");
			e.printStackTrace();
		}
    }
    
    void addRanged(int id, byte data, int range, String world) {
    	RangedItem ri = new RangedItem(id, data, range, world);
    	this.rangedItems.add(ri);
    	this.saveRanged();
    }
    
    void removeRanged(int i) {
    	this.rangedItems.remove(i);
    	this.saveRanged();
    }
    
    RangedItem getRanged(int id, byte data, String world) {
    	for (RangedItem ri : this.rangedItems) {
    		if (ri.id==id && (ri.data==-1 || ri.data==data) && (ri.world==null || ri.world.equals(world))) {
    			return ri;
    		}
    	}
    	return null;
    }
    
    String listRanged() {
    	StringBuilder sb = new StringBuilder();
    	sb.append("Restricted ranged items (listId - id - metadata - range)\n");
    	for (int i=0; i<this.rangedItems.size(); i++) {
    		RangedItem ri = this.rangedItems.get(i);
    		
    		sb.append(i+"- "+ri.id+":"+(ri.data!=-1?ri.data:"*")+" "+ri.range+(ri.world!=null?" "+ri.world:" all")+"\n");
    	}
    	
    	return sb.toString();
    }
    
    
    void addAoe(int id, byte data, int range, String world) {
    	RangedItem ri = new RangedItem(id, data, range, world);
    	this.aoeItems.add(ri);
    	this.saveAoe();
    }
    
    void removeAoe(int i) {
    	this.aoeItems.remove(i);
    	this.saveAoe();
    }
    
    RangedItem getAoe(int id, byte data, String world) {
    	for (RangedItem ri : this.aoeItems) {
    		if (ri.id==id && (ri.data==-1 || ri.data==data) && (ri.world==null || ri.world.equals(world))) {
    			return ri;
    		}
    	}
    	return null;
    }
    
    String listAoe() {
    	StringBuilder sb = new StringBuilder();
    	sb.append("Restricted AoE items (listId - id - metadata - range - world)\n");
    	for (int i=0; i<this.aoeItems.size(); i++) {
    		RangedItem ri = this.aoeItems.get(i);
    		
    		sb.append(i+"- "+ri.id+":"+(ri.data!=-1?ri.data:"*")+" "+ri.range+(ri.world!=null?" "+ri.world:" all")+"\n");
    	}
    	
    	return sb.toString();
    }
    
    
    boolean checkRanged(Player player, Block targetBlock) {
    	RangedItem ri = this.getRanged(player.getItemInHand().getTypeId(), player.getItemInHand().getData().getData(), player.getWorld().getName());
		if (ri==null) { // this item is not restricted
			return true;
		}
		
		if (targetBlock==null) { // if we don't have any target block, let's get a far target block limited to the item range
			targetBlock = PlayerEventHandler.getTargetBlock(player, ri.range);
		}
		
		if (targetBlock==null) { // no target block found
			return true;
		}
		
		Claim claim = GriefPreventionPlus.instance.dataStore.getClaimAt(targetBlock.getLocation(), true, null);

		if (claim==null) { // the target block is unclaimed
			return true;
		}
		
		String reason=claim.allowBuild(player, Material.AIR);
		if (reason==null) { // you have trust on the target claim
			return true;
		}
		
		// this action should be disallowed
		return false;
    }
    
    boolean checkAoe(Player player, Block targetBlock) {
    	// GPP AoE items claim protection
		// check your position's claim
		// if you're allowed in that position, check if the range of the aoe item is included in that claim (or subdivision)... in this case 
		// you're allowed to use that item without any other check.
		// otherwise if you're on a subdivision and the range goes beyond this subdivision boundaries, check for parents permission (deny item use if you haven't trust on the parent)
		// if you're not standing on any claim, disallow. This check won't particularly affect server performances, but you won't be able to use the
		// item if you're not in your claim.
		
		RangedItem ri = this.getAoe(player.getItemInHand().getTypeId(), player.getItemInHand().getData().getData(), player.getWorld().getName());
		if (ri==null) { // this item is not restricted
			return true;
		}
		
		Location playerLocation = player.getLocation();
		Claim claim = GriefPreventionPlus.instance.dataStore.getClaimAt(playerLocation, true, null);
		if (claim!=null) {
			if (claim.allowBuild(player)!=null) {
				// you have no perms on this claim, disallow.
				GriefPreventionPlus.sendMessage(player, TextMode.Err, Messages.NoAccessPermission, claim.getOwnerName());
				GriefPreventionPlus.addLogEntry(player.getName()+" tried to use "+ri.id+":"+ri.data+" at ["+GriefPreventionPlus.getfriendlyLocationString(player.getLocation())+"]");
				return false;
			}
			
			if ((claim.contains(new Location(playerLocation.getWorld(), playerLocation.getBlockX()+ri.range, 0, playerLocation.getBlockZ()+ri.range), true, false) &&
				claim.contains(new Location(playerLocation.getWorld(), playerLocation.getBlockX()-ri.range, 0, playerLocation.getBlockZ()-ri.range), true, false))) {
				// the item's range is in this claim's boundaries. You're allowed to use this item.
				return true;
			}
				 
			if (claim.parent!=null) {
				// you're on a subdivision
				if (claim.parent.allowBuild(player)!=null) {
					// you have no build permission on the top claim... disallow.
					return false;
				}
			
				if (claim.parent.contains(new Location(playerLocation.getWorld(), playerLocation.getBlockX()+ri.range, 0, playerLocation.getBlockZ()+ri.range), true, false) &&
				claim.parent.contains(new Location(playerLocation.getWorld(), playerLocation.getBlockX()-ri.range, 0, playerLocation.getBlockZ()-ri.range), true, false)) {
				    // the restricted item's range is in the top claim's boundaries. you're allowed to use this item.
					return true;
				}
			}
		}
		
		// the range is not entirely on a claim you're trusted in... we need to search for nearby claims too.
		for (Claim nClaim : GriefPreventionPlus.instance.dataStore.posClaimsGet(playerLocation, ri.range)) {
			if (nClaim.allowBuild(player)!=null) {
				// if not allowed on claims in range, disallow.
				return false;
			}
		}		
		
		return true;
    }
}
