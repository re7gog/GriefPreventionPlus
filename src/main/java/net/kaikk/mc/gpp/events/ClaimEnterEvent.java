package net.kaikk.mc.gpp.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.kaikk.mc.gpp.Claim;

/** Called when a player enters the claim */
public class ClaimEnterEvent extends ClaimPlayerMoveEvent {
	public ClaimEnterEvent(Claim claim, Player player, Location from, Location to) {
		super(claim, player, from, to);
	}
}
