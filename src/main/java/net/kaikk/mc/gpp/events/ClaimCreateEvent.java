package net.kaikk.mc.gpp.events;

import net.kaikk.mc.gpp.Claim;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Called when a claim is resized */
public class ClaimCreateEvent extends Event implements Cancellable {
	private final Claim claim;
	private boolean isCancelled;
	private String reason;

	public ClaimCreateEvent(Claim claim) {
		this.claim = claim;
	}

	public Claim getClaim() {
		return this.claim;
	}

	@Override
	public HandlerList getHandlers() {
		return handlerList;
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

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}
}
