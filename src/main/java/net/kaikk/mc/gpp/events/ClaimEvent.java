package net.kaikk.mc.gpp.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import net.kaikk.mc.gpp.Claim;

public abstract class ClaimEvent extends Event implements Cancellable {
	private static final HandlerList handlerList = new HandlerList();
	private final Claim claim;
	private boolean isCancelled;
	private String reason;

	public ClaimEvent(Claim claim) {
		this.claim = claim;
	}

	public Claim getClaim() {
		return this.claim;
	}
	
	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}
	
	@Override
	public boolean isCancelled() {
		return this.isCancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.isCancelled = cancel;
	}

	@Override
	public HandlerList getHandlers() {
		return handlerList;
	}

	public static HandlerList getHandlerList() {
		return handlerList;
	}


}
