package net.kaikk.mc.gpp.events;

import net.kaikk.mc.gpp.Claim;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Called when a claim is deleted */
public class ClaimDeletedEvent extends Event implements Cancellable {
	private final Claim claim;

	private boolean isCancelled;

	private final Reason reason;

	public ClaimDeletedEvent(Claim claim, Reason reason) {
		this.claim = claim;
		this.reason = reason;
	}

	public Claim getClaim() {
		return this.claim;
	}

	@Override
	public HandlerList getHandlers() {
		return handlerList;
	}

	public Reason getReason() {
		return this.reason;
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

	public enum Reason {
		ABANDON, DELETE, DELETEALL;
	}
}
