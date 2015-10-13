package net.kaikk.mc.gpp.events;

import net.kaikk.mc.gpp.Claim;

/** Called when a claim is deleted */
public class ClaimDeleteEvent extends ClaimEvent {
	private final Reason deleteReason;
	
	public ClaimDeleteEvent(Claim claim, Reason deleteReason) {
		super(claim);
		this.deleteReason = deleteReason;
	}

	public Reason getDeleteReason() {
		return this.deleteReason;
	}

	public enum Reason {
		ABANDON, DELETE, DELETEALL;
	}
}
