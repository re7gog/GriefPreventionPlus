package net.kaikk.mc.gpp.events;

import org.bukkit.entity.Player;

import net.kaikk.mc.gpp.Claim;

/** Called when a claim is resized */
public class ClaimCreateEvent extends ClaimEvent {
	public ClaimCreateEvent(Claim claim, Player player) {
		super(claim, player);
	}
}
