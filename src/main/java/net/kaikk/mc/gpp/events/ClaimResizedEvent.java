package net.kaikk.mc.gpp.events;

import net.kaikk.mc.gpp.Claim;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Called when a claim is resized */
public class ClaimResizedEvent extends Event implements Cancellable {
	private final Claim claim;

	private final Claim newClaim;

	private boolean isCancelled;

	public ClaimResizedEvent(Claim claim, Claim newClaim) {
		this.claim = claim;
		this.newClaim = newClaim;
	}

	public Claim getClaim() {
		return this.claim;
	}

	@Override
	public HandlerList getHandlers() {
		return handlerList;
	}

	public Claim getNewClaim() {
		return this.newClaim;
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
