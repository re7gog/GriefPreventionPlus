package net.kaikk.mc.gpp.events;

import java.util.UUID;

import org.bukkit.entity.Player;

import net.kaikk.mc.gpp.Claim;

public class ClaimOwnerTransfer extends ClaimEvent {
	private UUID newOwnerUUID;
	public ClaimOwnerTransfer(Claim claim, Player sender, UUID newOwnerUUID) {
		super(claim, sender);
		
		this.newOwnerUUID=newOwnerUUID;
	}
	
	public UUID getNewOwnerUUID() {
		return newOwnerUUID;
	}
}
