package net.kaikk.mc.gpp.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import net.kaikk.mc.gpp.Claim;

/** Called when a claim is resized */
public class ClaimResizeEvent extends ClaimEvent {
	private final Claim claim;
	private final Claim newClaim;
	private boolean isCancelled;
	private String reason;

	public ClaimResizeEvent(Claim claim, Claim newClaim, Player resizingPlayer) {
		super(claim, resizingPlayer);
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

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}
}
