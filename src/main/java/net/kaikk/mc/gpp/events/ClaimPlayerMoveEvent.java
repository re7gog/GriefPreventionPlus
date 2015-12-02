package net.kaikk.mc.gpp.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.kaikk.mc.gpp.Claim;

public abstract class ClaimPlayerMoveEvent extends ClaimEvent {
	private Location from, to;

	public ClaimPlayerMoveEvent(Claim claim, Player player, Location from, Location to) {
		super(claim, player);
		this.from = from;
		this.to = to;
	}

	public Location getFrom() {
		return from;
	}

	public Location getTo() {
		return to;
	}
}
