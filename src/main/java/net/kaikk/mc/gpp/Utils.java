package net.kaikk.mc.gpp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class Utils {
	private Utils() {}
	
	public static boolean isFakePlayer(Player player) {
		if (player.getName().charAt(0) != '[') {
			for (Player p : Bukkit.getOnlinePlayers()) {
				if (player==p) {
					return false;
				}
			}
		}
		return true;
	}
}
