package net.kaikk.mc.gpp.events;

import org.bukkit.entity.Player;

import net.kaikk.mc.gpp.Claim;

/** Called when a player enters the claim */
public class ClaimEnterEvent extends ClaimEvent {
	private final Player player;
	
	public ClaimEnterEvent(Player player, Claim claim) {
		super(claim);
		this.player = player;
	}

	public Player getPlayer() {
		return player;
	}
}
