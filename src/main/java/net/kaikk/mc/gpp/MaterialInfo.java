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

import org.bukkit.Material;

//represents a material or collection of materials
public class MaterialInfo {
	Material material;
	byte data;
	String description;

	public MaterialInfo(Material material, byte data, String description) {
		this.material = material;
		this.data = data;
		this.description = description;
	}

	public MaterialInfo(Material material, String description) {
		this.material = material;
		this.data = 0;
		this.description = description;
	}

	@Override
	public int hashCode() {
		int result = 31 * 1 + ((material == null) ? 0 : material.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof MaterialInfo)) {
			return false;
		}
		MaterialInfo other = (MaterialInfo) obj;
		if (material != other.material) {
			return false;
		}
		if (data != -1 && other.data != -1 && data != other.data) {
			return false;
		}
		
		return true;
	}

	@Override
	public String toString() {
		String returnValue = this.material + ":" + (data == -1 ? "*" : String.valueOf(this.data));
		if (this.description != null) {
			returnValue += ":" + this.description;
		}

		return returnValue;
	}

	public static MaterialInfo fromString(String string) {
		if ((string == null) || string.isEmpty()) {
			return null;
		}

		final String[] parts = string.split(":");
		if (parts.length < 3) {
			return null;
		}

		try {
			final Material material = Material.matchMaterial(parts[0]);

			byte data;
			if (parts[1].equals("*")) {
				data = -1;
			} else {
				data = Byte.parseByte(parts[1]);
			}

			return new MaterialInfo(material, data, parts[2]);
		} catch (final NumberFormatException exception) {
			return null;
		}
	}
}
