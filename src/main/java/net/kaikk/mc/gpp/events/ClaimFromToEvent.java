package net.kaikk.mc.gpp.events;

import net.kaikk.mc.gpp.Claim;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when a player goes from a claim to another, or inside/outside a
 * subdivision, or different subdivisions
 */
public class ClaimFromToEvent extends Event implements Cancellable {
	private final Claim oldClaim, newClaim;

	private final Player player;

	private boolean isCancelled;

	public ClaimFromToEvent(Player player, Claim oldClaim, Claim newClaim) {
		this.player = player;
		this.oldClaim = oldClaim;
		this.newClaim = newClaim;
	}

	@Override
	public HandlerList getHandlers() {
		return handlerList;
	}

	public Claim getNewClaim() {
		return this.newClaim;
	}

	public Claim getOldClaim() {
		return this.oldClaim;
	}

	public Player getPlayer() {
		return this.player;
	}

	@Override
	public boolean isCancelled() {
		return this.isCancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.isCancelled = cancel;
	}

	private static final HandlerList handlerList = new HandlerList();

	public static HandlerList getHandlerList() {
		return handlerList;
	}
}
