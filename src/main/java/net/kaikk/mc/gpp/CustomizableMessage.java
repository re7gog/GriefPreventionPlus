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

public class CustomizableMessage {
	private final Messages id;
	private final String text;
	private String notes;

	public CustomizableMessage(Messages id, String text, String notes) {
		this.id = id;
		this.text = text;
		this.notes = notes;
	}

	public Messages getId() {
		return this.id;
	}

	public String getNotes() {
		return this.notes;
	}

	public String getText() {
		return this.text;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}
}
