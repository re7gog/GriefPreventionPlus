package net.kaikk.mc.gpp.events;

import org.bukkit.entity.Player;

import net.kaikk.mc.gpp.Claim;

/**
 * Called when a player goes from a claim to another, or inside/outside a
 * subdivision, or different subdivisions
 */
public class ClaimFromToEvent extends ClaimEvent {
	private final Claim newClaim;
	private final Player player;

	public ClaimFromToEvent(Player player, Claim oldClaim, Claim newClaim) {
		super(oldClaim);
		this.player = player;
		this.newClaim = newClaim;
	}

	public Claim getNewClaim() {
		return this.newClaim;
	}

	public Claim getOldClaim() {
		return super.getClaim();
	}

	public Player getPlayer() {
		return this.player;
	}

}
