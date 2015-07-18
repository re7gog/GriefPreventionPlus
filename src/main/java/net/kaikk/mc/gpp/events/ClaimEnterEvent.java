package net.kaikk.mc.gpp.events;

import net.kaikk.mc.gpp.Claim;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Called when a player enters the claim */
public class ClaimEnterEvent extends Event implements Cancellable {
	private final Claim claim;

	private final Player player;

	private boolean isCancelled;

	public ClaimEnterEvent(Player player, Claim claim) {
		this.player = player;
		this.claim = claim;
	}

	public Claim getClaim() {
		return this.claim;
	}

	@Override
	public HandlerList getHandlers() {
		return handlerList;
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
