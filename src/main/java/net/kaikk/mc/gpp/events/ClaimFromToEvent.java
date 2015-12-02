package net.kaikk.mc.gpp.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.kaikk.mc.gpp.Claim;

/**
 * Called when a player goes from a claim to another, or inside/outside a
 * subdivision, or different subdivisions
 */
public class ClaimFromToEvent extends ClaimPlayerMoveEvent {
	private final Claim newClaim;

	public ClaimFromToEvent(Player player, Claim oldClaim, Claim newClaim, Location from, Location to) {
		super(oldClaim, player, from, to);
		this.newClaim = newClaim;
	}

	public Claim getNewClaim() {
		return this.newClaim;
	}

	public Claim getOldClaim() {
		return super.getClaim();
	}
}
