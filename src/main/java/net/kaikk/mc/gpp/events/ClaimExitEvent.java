package net.kaikk.mc.gpp.events;

import org.bukkit.entity.Player;

import net.kaikk.mc.gpp.Claim;

/** Called when a player exit a claim */
public class ClaimExitEvent extends ClaimEvent {
	private final Player player;
	
	public ClaimExitEvent(Player player, Claim claim) {
		super(claim);
		this.player = player;
	}

	public Player getPlayer() {
		return player;
	}
}
