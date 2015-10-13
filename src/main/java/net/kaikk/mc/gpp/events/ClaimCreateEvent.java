package net.kaikk.mc.gpp.events;

import net.kaikk.mc.gpp.Claim;

/** Called when a claim is resized */
public class ClaimCreateEvent extends ClaimEvent {
	public ClaimCreateEvent(Claim claim) {
		super(claim);
	}
}
