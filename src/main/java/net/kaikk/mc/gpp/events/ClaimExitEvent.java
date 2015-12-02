package net.kaikk.mc.gpp.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.kaikk.mc.gpp.Claim;

/** Called when a player exit a claim */
public class ClaimExitEvent extends ClaimPlayerMoveEvent {
	public ClaimExitEvent(Claim claim, Player player, Location from, Location to) {
		super(claim, player, from, to);
	}
}
