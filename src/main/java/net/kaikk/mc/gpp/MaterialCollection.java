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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

//ordered list of material info objects, for fast searching
public class MaterialCollection {
	Multimap<Material,MaterialInfo> materials = HashMultimap.create();

	public void clear() {
		this.materials.clear();
	}

	public int size() {
		return this.materials.size();
	}

	@Override
	public String toString() {
		final StringBuilder stringBuilder = new StringBuilder();
		for (MaterialInfo mi : materials.values()) {
			stringBuilder.append(mi.toString() + " ");
		}

		return stringBuilder.toString();
	}

	void add(MaterialInfo materialInfo) {
		this.materials.put(materialInfo.material, materialInfo);
	}

	boolean contains(MaterialInfo material) {
		return materials.containsEntry(material.material, material);
	}
}
