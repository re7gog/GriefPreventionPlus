/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2015 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.kaikk.mc.gpp;

public class ClaimResult {
	// Success result
	private Result result;

	// when succeeded, this is a reference to the new claim
	// when failed, this is a reference to the pre-existing, conflicting claim
	private Claim claim;
	
	private String reason;

	ClaimResult() {
		result = Result.SUCCESS;
	}

	ClaimResult(Result result, Claim claim) {
		this.setResult(result);
		this.setClaim(claim);
	}
	
	/** this will be set by an event that cancels the event */
	ClaimResult(String reason) {
		this.result=Result.EVENT;
		this.reason=reason;
	}

	public Claim getClaim() {
		return this.claim;
	}
	
	public Result getResult() {
		return result;
	}

	public void setClaim(Claim claim) {
		this.claim = claim;
	}

	public void setResult(Result result) {
		this.result = result;
	}
	
	public enum Result {
		SUCCESS, OVERLAP, WGREGION, EVENT;
	}

	/** the reason received by the event */
	public String getReason() {
		return reason;
	}

	/** set the reason that will be shown to the player */
	public void setReason(String reason) {
		this.reason = reason;
	}
}
