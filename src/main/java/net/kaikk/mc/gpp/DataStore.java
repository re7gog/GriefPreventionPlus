/*
    GriefPreventionPlus Server Plugin for Minecraft
    Copyright (C) 2015 Antonino Kai Pocorobba
    (forked from GriefPrevention by Ryan Hamshire)

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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.UUID;

import net.kaikk.mc.gpp.events.ClaimDeletedEvent;
import net.kaikk.mc.gpp.events.ClaimDeletedEvent.Reason;
import net.kaikk.mc.gpp.events.ClaimResizedEvent;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

//singleton class which manages all GriefPrevention data (except for config options)
public class DataStore {
	// in-memory cache for player data
	protected HashMap<UUID, PlayerData> playerNameToPlayerDataMap = new HashMap<UUID, PlayerData>();

	// in-memory cache for group (permission-based) data
	protected HashMap<String, Integer> permissionToBonusBlocksMap = new HashMap<String, Integer>();

	// in-memory cache for claim data
	HashMap<Integer, Claim> claims = new HashMap<Integer, Claim>();
	HashMap<Long, Map<Integer, Claim>> posClaims = new HashMap<Long, Map<Integer, Claim>>();

	// in-memory cache for messages
	private String[] messages;

	// pattern for unique user identifiers (UUIDs)
	// protected final static Pattern uuidpattern =
	// Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	// list of UUIDs which are soft-muted
	HashMap<UUID, Boolean> softMuteMap = new HashMap<UUID, Boolean>();
	// world guard reference, if available
	private WorldGuardWrapper worldGuard = null;
	private Connection databaseConnection = null;

	private final String databaseUrl, userName, password;

	DataStore(String url, String userName, String password) throws Exception {
		this.databaseUrl = url;
		this.userName = userName;
		this.password = password;

		this.initialize();
	}

	// initialization!
	void initialize() throws Exception {
		try {
			// load the java driver for mySQL
			Class.forName("com.mysql.jdbc.Driver");
		} catch (final Exception e) {
			GriefPreventionPlus.addLogEntry("ERROR: Unable to load Java's mySQL database driver.  Check to make sure you've installed it properly.");
			throw e;
		}

		try {
			this.refreshDataConnection();
		} catch (final Exception e2) {
			GriefPreventionPlus.addLogEntry("ERROR: Unable to connect to database.  Check your config file settings.");
			throw e2;
		}

		try {
			final Statement statement = this.databaseConnection.createStatement();

			ResultSet results = statement.executeQuery("SHOW TABLES LIKE 'gpp_claims'");
			if (!results.next()) {
				statement.execute("CREATE TABLE IF NOT EXISTS gpp_claims (" + "id int(11) NOT NULL AUTO_INCREMENT," + "owner binary(16) NOT NULL COMMENT 'UUID'," + "world binary(16) NOT NULL COMMENT 'UUID'," + "lesserX mediumint(9) NOT NULL," + "lesserZ mediumint(9) NOT NULL," + "greaterX mediumint(9) NOT NULL," + "greaterZ mediumint(9) NOT NULL," + "parentid int(11) NOT NULL," + "PRIMARY KEY (id));");

				statement.execute("CREATE TABLE IF NOT EXISTS gpp_groupdata (" + "gname varchar(100) NOT NULL," + "blocks int(11) NOT NULL," + "UNIQUE KEY gname (gname));");

				statement.execute("CREATE TABLE IF NOT EXISTS gpp_permsbukkit (" + "claimid int(11) NOT NULL," + "pname varchar(80) NOT NULL," + "perm tinyint(4) NOT NULL," + "PRIMARY KEY (claimid,pname)," + "KEY claimid (claimid));");

				statement.execute("CREATE TABLE IF NOT EXISTS gpp_permsplayer (" + "claimid int(11) NOT NULL," + "player binary(16) NOT NULL COMMENT 'UUID'," + "perm tinyint(4) NOT NULL," + "PRIMARY KEY (claimid,player)," + "KEY claimid (claimid));");

				statement.execute("CREATE TABLE IF NOT EXISTS gpp_playerdata (" + "player binary(16) NOT NULL COMMENT 'UUID'," + "accruedblocks int(11) NOT NULL," + "bonusblocks int(11) NOT NULL," + "PRIMARY KEY (player));");

				results = statement.executeQuery("SHOW TABLES LIKE 'griefprevention_claimdata';");
				if (results.next()) {
					// migration from griefprevention
					GriefPreventionPlus.addLogEntry("Migrating data from Grief Prevention. It may take some time.");

					// claims
					results = statement.executeQuery("SELECT * FROM griefprevention_claimdata ORDER BY parentid ASC;");
					final Statement statement2 = this.databaseConnection.createStatement();

					String tString;
					String playerId;
					long i = 0;
					long j = 0;
					long k = 0;

					long claimId = 1;
					Long nextParentId;

					final HashMap<Long, Long> migratedClaims = new HashMap<Long, Long>();
					while (results.next()) {
						final String ownerString = results.getString(2);
						playerId = "0";

						if ((ownerString.length() == 36) && ((tString = ownerString.replace("-", "")).length() == 32)) {
							playerId = "0x" + tString;
						}

						final String[] lesser = results.getString(3).split(";");
						final String[] greater = results.getString(4).split(";");
						if ((lesser.length != 4) || (greater.length != 4)) { // wrong
							// corners,
							// skip
							// this
							// claim
							GriefPreventionPlus.addLogEntry("Skipping claim " + results.getLong(1) + ": wrong corners");
							continue;
						}

						final World world = GriefPreventionPlus.getInstance().getServer().getWorld(lesser[0]);
						if (world == null) { // this world doesn't exist, skip
							// this claim
							GriefPreventionPlus.addLogEntry("Skipping claim " + results.getLong(1) + ": world " + lesser[0] + " doesn't exist");
							continue;
						}

						// insert this claim in new claims table

						if (results.getLong(9) == -1) { // claims
							migratedClaims.put(results.getLong(1), claimId++);
							nextParentId = (long) -1;
							if (playerId.equals("0")) {
								playerId = UUIDtoHexString(GriefPreventionPlus.UUID1); // administrative
								// claims
							}
						} else { // subclaims
							nextParentId = migratedClaims.get(results.getLong(9));
						}

						if (nextParentId == null) {
							GriefPreventionPlus.addLogEntry("Skipping orphan subclaim (parentid: " + results.getLong(9) + ").");
							continue;
						}

						statement2.executeUpdate("INSERT INTO gpp_claims (owner, world, lesserX, lesserZ, greaterX, greaterZ, parentid) VALUES (" + playerId + ", " + UUIDtoHexString(world.getUID()) + ", " + lesser[1] + ", " + lesser[3] + ", " + greater[1] + ", " + greater[3] + ", " + nextParentId + ");");

						i++;

						// convert permissions for this claim
						// builders
						if (!results.getString(5).isEmpty()) {
							for (final String s : results.getString(5).split(";")) {
								if (s.startsWith("[")) {
									statement2.executeUpdate("INSERT INTO gpp_permsbukkit VALUES(" + i + ", '" + s.substring(1, s.length() - 1) + "', 2) ON DUPLICATE KEY UPDATE perm = perm | 2;");
								} else {
									if ((s.length() == 36) && ((tString = s.replace("-", "")).length() == 32)) {
										statement2.executeUpdate("INSERT INTO gpp_permsplayer VALUES(" + i + ", 0x" + tString + ", 2) ON DUPLICATE KEY UPDATE perm = perm | 2;");
									} else if (s.equals("public")) {
										statement2.executeUpdate("INSERT INTO gpp_permsplayer VALUES(" + i + ", " + UUIDtoHexString(GriefPreventionPlus.UUID0) + ", 2) ON DUPLICATE KEY UPDATE perm = perm | 2;");
									}
								}
								j++;
							}
						}

						// containers
						if (!results.getString(6).isEmpty()) {
							for (final String s : results.getString(6).split(";")) {
								if (s.startsWith("[")) {
									statement2.executeUpdate("INSERT INTO gpp_permsbukkit VALUES(" + i + ", '" + s.substring(1, s.length() - 1) + "', 4) ON DUPLICATE KEY UPDATE perm = perm | 4;");
								} else {
									if ((s.length() == 36) && ((tString = s.replace("-", "")).length() == 32)) {
										statement2.executeUpdate("INSERT INTO gpp_permsplayer VALUES(" + i + ", 0x" + tString + ", 4) ON DUPLICATE KEY UPDATE perm = perm | 4;");
									} else if (s.equals("public")) {
										statement2.executeUpdate("INSERT INTO gpp_permsplayer VALUES(" + i + ", " + UUIDtoHexString(GriefPreventionPlus.UUID0) + ", 4) ON DUPLICATE KEY UPDATE perm = perm | 4;");
									}
								}
								j++;
							}
						}

						// accessors
						if (!results.getString(7).isEmpty()) {
							for (final String s : results.getString(7).split(";")) {
								if (s.startsWith("[")) {
									statement2.executeUpdate("INSERT INTO gpp_permsbukkit VALUES(" + i + ", '" + s.substring(1, s.length() - 1) + "', 8) ON DUPLICATE KEY UPDATE perm = perm | 8;");
								} else {
									if ((s.length() == 36) && ((tString = s.replace("-", "")).length() == 32)) {
										statement2.executeUpdate("INSERT INTO gpp_permsplayer VALUES(" + i + ", 0x" + tString + ", 8) ON DUPLICATE KEY UPDATE perm = perm | 8;");
									} else if (s.equals("public")) {
										statement2.executeUpdate("INSERT INTO gpp_permsplayer VALUES(" + i + ", " + UUIDtoHexString(GriefPreventionPlus.UUID0) + ", 8) ON DUPLICATE KEY UPDATE perm = perm | 8;");
									}
								}
								j++;
							}
						}

						// managers
						if (!results.getString(8).isEmpty()) {
							for (final String s : results.getString(8).split(";")) {
								if (s.startsWith("[")) {
									statement2.executeUpdate("INSERT INTO gpp_permsbukkit VALUES(" + i + ", '" + s.substring(1, s.length() - 1) + "', 1) ON DUPLICATE KEY UPDATE perm = perm | 1;");
								} else {
									if ((s.length() == 36) && ((tString = s.replace("-", "")).length() == 32)) {
										statement2.executeUpdate("INSERT INTO gpp_permsplayer VALUES(" + i + ", 0x" + tString + ", 1) ON DUPLICATE KEY UPDATE perm = perm | 1;");
									} else if (s.equals("public")) {
										statement2.executeUpdate("INSERT INTO gpp_permsplayer VALUES(" + i + ", " + UUIDtoHexString(GriefPreventionPlus.UUID0) + ", 1) ON DUPLICATE KEY UPDATE perm = perm | 1;");
									}
								}
								j++;
							}
						}
					}

					results = statement.executeQuery("SELECT name, accruedblocks, bonusblocks FROM griefprevention_playerdata;");

					final Map<String, Integer[]> claimBlocksMap = new HashMap<String, Integer[]>();
					while (results.next()) {
						final String ownerString = results.getString(1);

						if ((ownerString.length() == 36) && ((tString = ownerString.replace("-", "")).length() == 32)) {
							final Integer[] existingBlocks = claimBlocksMap.get(tString);
							if (existingBlocks != null) {
								GriefPreventionPlus.addLogEntry("WARNING: Found duplicated key for " + tString);

								final int a = existingBlocks[0];
								final int b = existingBlocks[1];

								final Integer[] blocks = { (results.getInt(2) == a ? a : results.getInt(2) + a), (results.getInt(3) == b ? b : results.getInt(3) + b) };
								claimBlocksMap.put(tString, blocks);
							} else {
								final Integer[] blocks = { results.getInt(2), results.getInt(3) };
								claimBlocksMap.put(tString, blocks);
							}

							playerId = tString;
						} else {
							GriefPreventionPlus.addLogEntry("Skipping GriefPrevention data for user " + ownerString + ": no UUID.");
							continue;
						}
					}

					for (final Entry<String, Integer[]> gppbf : claimBlocksMap.entrySet()) {
						statement2.executeUpdate("INSERT INTO gpp_playerdata VALUES (0x" + gppbf.getKey() + ", " + gppbf.getValue()[0] + ", " + gppbf.getValue()[1] + ");");
						k++;
					}

					statement.close();
					statement2.close();
					GriefPreventionPlus.addLogEntry("Migration complete. Claims: " + i + " - Permissions: " + j + " - PlayerData: " + k);
				}
			}
		} catch (final Exception e3) {
			GriefPreventionPlus.addLogEntry("ERROR: Unable to create the necessary database table.  Details:");
			GriefPreventionPlus.addLogEntry(e3.getMessage());
			e3.printStackTrace();
			throw e3;
		}

		// load group data into memory
		final Statement statement = this.databaseConnection.createStatement();
		ResultSet results = statement.executeQuery("SELECT gname, blocks FROM gpp_groupdata;");

		while (results.next()) {
			this.permissionToBonusBlocksMap.put(results.getString(1), results.getInt(2));
		}

		// load claims data into memory
		results = statement.executeQuery("SELECT * FROM gpp_claims;");
		final Statement statementPerms = this.databaseConnection.createStatement();
		ResultSet resultsPerms;

		while (results.next()) {
			final int id = results.getInt(1);
			final int parentid = results.getInt(8);
			UUID owner = null;
			final HashMap<UUID, Integer> permissionMapPlayers = new HashMap<UUID, Integer>();
			final HashMap<String, Integer> permissionMapBukkit = new HashMap<String, Integer>();
			final HashMap<String, Integer> permissionMapFakePlayer = new HashMap<String, Integer>();

			final UUID world = toUUID(results.getBytes(3));

			if (results.getBytes(2) != null) {
				owner = toUUID(results.getBytes(2));
			}

			resultsPerms = statementPerms.executeQuery("SELECT player, perm FROM gpp_permsplayer WHERE claimid=" + id + ";");
			while (resultsPerms.next()) {
				permissionMapPlayers.put(toUUID(resultsPerms.getBytes(1)), resultsPerms.getInt(2));
			}

			resultsPerms = statementPerms.executeQuery("SELECT pname, perm FROM gpp_permsbukkit WHERE claimid=" + id + ";");
			while (resultsPerms.next()) {
				if (resultsPerms.getString(1).startsWith("#")) {
					permissionMapFakePlayer.put(resultsPerms.getString(1).substring(1), resultsPerms.getInt(2));
				} else {
					permissionMapBukkit.put(resultsPerms.getString(1), resultsPerms.getInt(2));
				}
			}

			final Claim claim = new Claim(world, results.getInt(4), results.getInt(5), results.getInt(6), results.getInt(7), owner, permissionMapPlayers, permissionMapBukkit, permissionMapFakePlayer, id);

			if (parentid == -1) {
				this.addClaim(claim, false);
			} else {
				final Claim topClaim = this.claims.get(parentid);
				if (topClaim == null) {
					// parent claim doesn't exist, skip this subclaim
					GriefPreventionPlus.addLogEntry("Orphan subclaim: " + claim.locationToString());
					continue;
				}
				claim.setParent(topClaim);
				topClaim.getChildren().add(claim);
			}
		}

		GriefPreventionPlus.addLogEntry(this.claims.size() + " total claims loaded.");

		// load up all the messages from messages.yml
		this.loadMessages();
		GriefPreventionPlus.addLogEntry("Customizable messages loaded.");

		// load list of soft mutes
		this.loadSoftMutes();

		// try to hook into world guard
		try {
			this.worldGuard = new WorldGuardWrapper();
			GriefPreventionPlus.addLogEntry("Successfully hooked into WorldGuard.");
		}
		// if failed, world guard compat features will just be disabled.
		catch (final ClassNotFoundException exception) {
		} catch (final NoClassDefFoundError exception) {
		}
	}
	
	// grants a group (players with a specific permission) bonus claim blocks as
	// long as they're still members of the group
	synchronized public int adjustGroupBonusBlocks(String groupName, int amount) {
		Integer currentValue = this.permissionToBonusBlocksMap.get(groupName);
		if (currentValue == null) {
			currentValue = 0;
		}

		currentValue += amount;
		this.permissionToBonusBlocksMap.put(groupName, currentValue);

		// write changes to storage to ensure they don't get lost
		this.saveGroupBonusBlocks(groupName, currentValue);

		return currentValue;
	}

	// saves changes to player data. MUST be called after you're done making
	// changes, otherwise a reload will lose them
	public void asyncSavePlayerData(UUID playerID, PlayerData playerData) {
		// never save data for the "administrative" account. an empty string for
		// player name indicates administrative account
		if (playerID == null) {
			return;
		}

		try {
			this.refreshDataConnection();

			final Statement statement = this.databaseConnection.createStatement();
			statement.executeUpdate("INSERT INTO gpp_playerdata VALUES (" + UUIDtoHexString(playerData.playerID) + ", \"" + playerData.getAccruedClaimBlocks() + "\", " + playerData.getBonusClaimBlocks() + ") ON DUPLICATE KEY UPDATE accruedblocks=" + playerData.getAccruedClaimBlocks() + ", bonusblocks=" + playerData.getBonusClaimBlocks() + ";");
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to save data for player " + playerID.toString() + ".  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
		}
	}

	synchronized public void changeClaimOwner(Claim claim, UUID newOwnerID) throws Exception {
		// if it's a subdivision, throw an exception
		if (claim.getParent() != null) {
			throw new Exception("Subdivisions can't be transferred.  Only top-level claims may change owners.");
		}

		// otherwise update information

		// determine current claim owner
		PlayerData ownerData = null;
		if (!claim.isAdminClaim()) {
			ownerData = this.getPlayerData(claim.getOwnerID());
		}

		// determine new owner
		PlayerData newOwnerData = null;

		if (newOwnerID != null) {
			newOwnerData = this.getPlayerData(newOwnerID);
		}

		// transfer
		claim.setOwnerID(newOwnerID);
		this.dbUpdateOwner(claim);

		// adjust blocks and other records
		if (ownerData != null) {
			ownerData.getClaims().remove(claim);
		}

		if (newOwnerData != null) {
			newOwnerData.getClaims().add(claim);
		}
	}

	public void clearPermissionsOnPlayerClaims(UUID ownerId) {
		final PlayerData ownerData = this.getPlayerData(ownerId);
		if (ownerData != null) {
			this.dbUnsetPerm(ownerId);
			for (final Claim c : ownerData.getClaims()) {
				c.clearMemoryPermissions();
			}
		}
	}

	// creates a claim.
	// if the new claim would overlap an existing claim, returns a failure along
	// with a reference to the existing claim
	// if the new claim would overlap a WorldGuard region where the player
	// doesn't have permission to build, returns a failure with NULL for claim
	// otherwise, returns a success along with a reference to the new claim
	// use ownerName == "" for administrative claims
	// for top level claims, pass parent == NULL
	// DOES adjust claim blocks available on success (players can go into
	// negative quantity available)
	// DOES check for world guard regions where the player doesn't have
	// permission
	// does NOT check a player has permission to create a claim, or enough claim
	// blocks.
	// does NOT check minimum claim size constraints
	// does NOT visualize the new claim for any players
	synchronized public ClaimResult createClaim(UUID world, int x1, int x2, int z1, int z2, UUID ownerID, Claim parent, Integer id, Player creatingPlayer) {
		final ClaimResult result = new ClaimResult();

		int smallx, bigx, smallz, bigz;

		// determine small versus big inputs
		if (x1 < x2) {
			smallx = x1;
			bigx = x2;
		} else {
			smallx = x2;
			bigx = x1;
		}

		if (z1 < z2) {
			smallz = z1;
			bigz = z2;
		} else {
			smallz = z2;
			bigz = z1;
		}

		// create a new claim instance (but don't save it, yet)
		final Claim newClaim = new Claim(world, smallx, smallz, bigx, bigz, ownerID, null, null, null, id);

		newClaim.setParent(parent);

		// ensure this new claim won't overlap any existing claims
		ArrayList<Claim> claimsToCheck;
		if (newClaim.getParent() != null) {
			claimsToCheck = newClaim.getParent().getChildren();
		} else {
			claimsToCheck = new ArrayList<Claim>(this.claims.values());
		}

		for (int i = 0; i < claimsToCheck.size(); i++) {
			final Claim otherClaim = claimsToCheck.get(i);

			// if we find an existing claim which will be overlapped
			if (otherClaim.overlaps(newClaim)) {
				// result = fail, return conflicting claim
				result.setSucceeded(false);
				result.setClaim(otherClaim);
				return result;
			}
		}

		// if worldguard is installed, also prevent claims from overlapping any
		// worldguard regions
		if (GriefPreventionPlus.getInstance().config.claims_respectWorldGuard && (this.worldGuard != null) && (creatingPlayer != null)) {
			if (!this.worldGuard.canBuild(newClaim.getWorld(), newClaim.lesserX, newClaim.lesserZ, newClaim.greaterX, newClaim.greaterZ, creatingPlayer)) {
				result.setSucceeded(false);
				result.setClaim(null);
				return result;
			}
		}

		// otherwise add this new claim to the data store to make it effective
		this.addClaim(newClaim, true);

		// then return success along with reference to new claim
		result.setSucceeded(true);
		result.setClaim(newClaim);
		return result;
	}

	// deletes a claim or subdivision
	synchronized public void deleteClaim(Claim claim) {
		if (claim.getParent() != null) { // subdivision
			final Claim parentClaim = claim.getParent();
			parentClaim.getChildren().remove(claim);
			this.deleteClaimFromSecondaryStorage(claim);
			return;
		}

		// remove from memory
		this.posClaimsRemove(claim);
		this.claims.remove(claim.id);

		// remove from secondary storage
		this.deleteClaimFromSecondaryStorage(claim);

		// update player data, except for administrative claims, which have no
		// owner
		if (!claim.isAdminClaim()) {
			final PlayerData ownerData = this.getPlayerData(claim.getOwnerID());
			for (int i = 0; i < ownerData.getClaims().size(); i++) {
				if (ownerData.getClaims().get(i).id == claim.id) {
					ownerData.getClaims().remove(i);
					break;
				}
			}
			this.savePlayerData(claim.getOwnerID(), ownerData);
		}
	}

	// deletes all claims owned by a player
	synchronized public void deleteClaimsForPlayer(UUID playerID, boolean deleteCreativeClaims) {
		List<Claim> claimsToRemove = new ArrayList<Claim>();
		for (final Claim claim : this.claims.values()) {
			if (((playerID == claim.getOwnerID()) || ((playerID != null) && playerID.equals(claim.getOwnerID()))) && (deleteCreativeClaims || !GriefPreventionPlus.getInstance().creativeRulesApply(claim.getWorldUID()))) {
				// fire event
				final ClaimDeletedEvent event = new ClaimDeletedEvent(claim, Reason.DELETEALL);
				GriefPreventionPlus.getInstance().getServer().getPluginManager().callEvent(event);
				if (!event.isCancelled()) {
					claimsToRemove.add(claim);
				}
			}
		}
		
		for (final Claim claim : claimsToRemove) {
			claim.removeSurfaceFluids(null);
			this.deleteClaim(claim);

			// if in a creative mode world, delete the claim
			if (GriefPreventionPlus.getInstance().creativeRulesApply(claim.getWorldUID())) {
				GriefPreventionPlus.getInstance().restoreClaim(claim, 0);
			}
		}
	}

	public void dropPermissionOnPlayerClaims(UUID ownerId, String permBukkit) {
		final PlayerData ownerData = this.getPlayerData(ownerId);
		if (ownerData != null) {
			this.dbUnsetPerm(ownerId, permBukkit);
			for (final Claim c : ownerData.getClaims()) {
				c.unsetPermission(permBukkit);
			}
		}
	}

	public void dropPermissionOnPlayerClaims(UUID ownerId, UUID playerId) {
		final PlayerData ownerData = this.getPlayerData(ownerId);
		if (ownerData != null) {
			this.dbUnsetPerm(ownerId, playerId);
			for (final Claim c : ownerData.getClaims()) {
				c.unsetPermission(playerId);
			}
		}
	}

	/** get a claim by ID */
	public synchronized Claim getClaim(int id) {
		return this.claims.get(id);
	}

	public Claim getClaimAt(Location location) {
		return this.getClaimAt(location, true, null);
	}

	public Claim getClaimAt(Location location, boolean ignoreHeight) {
		return this.getClaimAt(location, ignoreHeight, null);
	}

	public Claim getClaimAt(Location location, boolean ignoreHeight, Claim cachedClaim) {
		final Claim claim = this.getClaimAt(location, cachedClaim);
		if (ignoreHeight || ((claim != null) && claim.checkHeight(location))) {
			return claim;
		}

		return null;
	}

	// gets the claim at a specific location
	// cachedClaim can be NULL, but will help performance if you have a
	// reasonable guess about which claim the location is in
	public Claim getClaimAt(Location location, Claim cachedClaim) {
		// check cachedClaim guess first. if it's in the datastore and the
		// location is inside it, we're done
		if ((cachedClaim != null) && cachedClaim.isInDataStore() && cachedClaim.contains(location, true, false)) {
			return cachedClaim;
		}

		// find a top level claim
		final Claim claim = this.posClaimsGet(location);
		if (claim != null) {
			// when we find a top level claim, if the location is in one of its
			// subdivisions,
			// return the SUBDIVISION, not the top level claim
			for (final Claim subdivision : claim.getChildren()) {
				if (subdivision.contains(location, true, false)) {
					return subdivision;
				}
			}
		}

		return claim;
	}

	synchronized public String getMessage(Messages messageID, String... args) {
		String message = this.messages[messageID.ordinal()];

		for (int i = 0; i < args.length; i++) {
			final String param = args[i];
			message = message.replace("{" + i + "}", param);
		}

		return message;
	}

	// gets all the claims "near" a location
	public Map<Integer, Claim> getNearbyClaims(Location location) {
		return this.posClaimsGet(location, 128);
	}

	// retrieves player data from memory or secondary storage, as necessary
	// if the player has never been on the server before, this will return a
	// fresh player data with default values
	synchronized public PlayerData getPlayerData(UUID playerID) {
		// first, look in memory
		PlayerData playerData = this.playerNameToPlayerDataMap.get(playerID);

		// if not there, build a fresh instance with some blanks for what may be
		// in secondary storage
		if (playerData == null) {
			playerData = new PlayerData(playerID);

			// shove that new player data into the hash map cache
			this.playerNameToPlayerDataMap.put(playerID, playerData);
		}

		return playerData;
	}

	/**
	 * This method checks if a claim overlaps an existing claim. subdivision are
	 * accepted too
	 *
	 * @return the overlapped claim (or itself if it would overlap a worldguard
	 *         region), null if it doesn't overlap!
	 */
	public synchronized Claim overlapsClaims(Claim claim, Claim excludedClaim, Player creatingPlayer) {
		if (claim.getParent() != null) {
			// top claim contains this subclaim
			if (!claim.getParent().contains(claim.getLesserBoundaryCorner(), true, false) || !claim.getParent().contains(claim.getGreaterBoundaryCorner(), true, false)) {
				return claim.getParent();
			}

			// check parent's subclaims
			for (final Claim otherSubclaim : claim.getParent().getChildren()) {
				if ((otherSubclaim == claim) || (otherSubclaim == excludedClaim)) { // exclude this claim
					continue;
				}

				if (otherSubclaim.overlaps(claim)) {
					return otherSubclaim;
				}
			}
		} else {
			// if this claim has subclaims, check that every subclaim is within
			// the top claim
			for (final Claim otherClaim : claim.getChildren()) {
				if (!claim.contains(otherClaim.getGreaterBoundaryCorner(), true, false) || !claim.contains(otherClaim.getLesserBoundaryCorner(), true, false)) {
					return otherClaim;
				}
			}

			// Check for other claims
			for (final Claim otherClaim : this.claims.values()) {
				if ((otherClaim == claim) || (otherClaim == excludedClaim)) {
					continue; // exclude this claim
				}

				if (otherClaim.overlaps(claim)) {
					return otherClaim;
				}
			}

			// if worldguard is installed, also prevent claims from overlapping
			// any worldguard regions
			if (GriefPreventionPlus.getInstance().config.claims_respectWorldGuard && (this.worldGuard != null) && (creatingPlayer != null)) {
				if (!this.worldGuard.canBuild(claim.getWorld(), claim.lesserX, claim.lesserZ, claim.greaterX, claim.greaterZ, creatingPlayer)) {
					return claim;
				}
			}
		}
		return null;
	}

	// This method will return a set with all claims on the specified range
	public Map<Integer, Claim> posClaimsGet(Location loc, int blocksRange) {
		int lx = loc.getBlockX() - blocksRange;
		int lz = loc.getBlockZ() - blocksRange;

		int gx = loc.getBlockX() + blocksRange;
		int gz = loc.getBlockZ() + blocksRange;

		final Claim validArea = new Claim(loc.getWorld().getUID(), lx, lz, gx, gz, null, null, null, null, null);

		lx = lx >> 8;
		lz = lz >> 8;
		gx = gx >> 8;
		gz = gz >> 8;

		final Map<Integer, Claim> claims = new HashMap<Integer, Claim>();

		for (int i = lx; i <= gx; i++) {
			for (int j = lz; j <= gz; j++) {
				final Map<Integer, Claim> claimMap = this.posClaims.get(from2int(i, j));
				if (claimMap != null) {
					for (final Claim claim : claimMap.values()) {
						if (claim.overlaps(validArea)) {
							claims.put(claim.getID(), claim);
						}
					}
				}
			}
		}
		return claims;
	}

	/**
	 * tries to resize a claim see createClaim() for details on return value
	 */
	synchronized public ClaimResult resizeClaim(Claim claim, int newx1, int newz1, int newx2, int newz2, Player resizingPlayer) {
		// create a fake claim with new coords
		final Claim newClaim = new Claim(claim.getWorldUID(), newx1, newz1, newx2, newz2, claim.getOwnerID(), null, null, null, claim.id);
		newClaim.setParent(claim.getParent());
		newClaim.setChildren(claim.getChildren());

		final Claim claimCheck = this.overlapsClaims(newClaim, claim, resizingPlayer);

		if (claimCheck == null) {
			// fire event
			final ClaimResizedEvent event = new ClaimResizedEvent(claim, newClaim);
			GriefPreventionPlus.getInstance().getServer().getPluginManager().callEvent(event);
			if (event.isCancelled()) {
				return new ClaimResult(false, claimCheck);
			}

			// let's update this claim

			this.posClaimsRemove(claim);
			final String oldLoc = claim.locationToString();

			claim.setLocation(claim.getWorldUID(), newx1, newz1, newx2, newz2);
			this.dbUpdateLocation(claim);

			this.posClaimsAdd(claim);

			GriefPreventionPlus.addLogEntry(claim.getOwnerName() + " resized claim id " + claim.id + " from " + oldLoc + " to " + claim.locationToString());
			return new ClaimResult(true, claim);
		} else {
			return new ClaimResult(false, claimCheck);
		}
	}

	// saves changes to player data to secondary storage. MUST be called after
	// you're done making changes, otherwise a reload will lose them
	public void savePlayerData(UUID playerID, PlayerData playerData) {
		new SavePlayerDataThread(playerID, playerData).start();
	}

	// saves changes to player data to secondary storage. MUST be called after
	// you're done making changes, otherwise a reload will lose them
	public void savePlayerDataSync(UUID playerID, PlayerData playerData) {
		// ensure player data is already read from file before trying to save
		playerData.getAccruedClaimBlocks();
		playerData.getClaims();

		this.asyncSavePlayerData(playerID, playerData);
	}

	private void addDefault(HashMap<String, CustomizableMessage> defaults, Messages id, String text, String notes) {
		final CustomizableMessage message = new CustomizableMessage(id, text, notes);
		defaults.put(id.name(), message);
	}

	private void loadMessages() {
		final Messages[] messageIDs = Messages.values();
		this.messages = new String[Messages.values().length];

		final HashMap<String, CustomizableMessage> defaults = new HashMap<String, CustomizableMessage>();

		// initialize defaults
		this.addDefault(defaults, Messages.RespectingClaims, "Now respecting claims.", null);
		this.addDefault(defaults, Messages.IgnoringClaims, "Now ignoring claims.", null);
		this.addDefault(defaults, Messages.NoCreativeUnClaim, "You can't unclaim this land.  You can only make this claim larger or create additional claims.", null);
		this.addDefault(defaults, Messages.SuccessfulAbandon, "Claims abandoned.  You now have {0} available claim blocks.", "0: remaining blocks");
		this.addDefault(defaults, Messages.RestoreNatureActivate, "Ready to restore some nature!  Right click to restore nature, and use /BasicClaims to stop.", null);
		this.addDefault(defaults, Messages.RestoreNatureAggressiveActivate, "Aggressive mode activated.  Do NOT use this underneath anything you want to keep!  Right click to aggressively restore nature, and use /BasicClaims to stop.", null);
		this.addDefault(defaults, Messages.FillModeActive, "Fill mode activated with radius {0}.  Right click an area to fill.", "0: fill radius");
		this.addDefault(defaults, Messages.TransferClaimPermission, "That command requires the administrative claims permission.", null);
		this.addDefault(defaults, Messages.TransferClaimMissing, "There's no claim here.  Stand in the administrative claim you want to transfer.", null);
		this.addDefault(defaults, Messages.TransferClaimAdminOnly, "Only administrative claims may be transferred to a player.", null);
		this.addDefault(defaults, Messages.PlayerNotFound2, "No player by that name has logged in recently.", null);
		this.addDefault(defaults, Messages.TransferTopLevel, "Only top level claims (not subdivisions) may be transferred.  Stand outside of the subdivision and try again.", null);
		this.addDefault(defaults, Messages.TransferSuccess, "Claim transferred.", null);
		this.addDefault(defaults, Messages.TrustListNoClaim, "Stand inside the claim you're curious about.", null);
		this.addDefault(defaults, Messages.ClearPermsOwnerOnly, "Only the claim owner can clear all permissions.", null);
		this.addDefault(defaults, Messages.UntrustIndividualAllClaims, "Revoked {0}'s access to ALL your claims.  To set permissions for a single claim, stand inside it.", "0: untrusted player");
		this.addDefault(defaults, Messages.UntrustEveryoneAllClaims, "Cleared permissions in ALL your claims.  To set permissions for a single claim, stand inside it.", null);
		this.addDefault(defaults, Messages.NoPermissionTrust, "You don't have {0}'s permission to manage permissions here.", "0: claim owner's name");
		this.addDefault(defaults, Messages.ClearPermissionsOneClaim, "Cleared permissions in this claim.  To set permission for ALL your claims, stand outside them.", null);
		this.addDefault(defaults, Messages.UntrustIndividualSingleClaim, "Revoked {0}'s access to this claim.  To set permissions for a ALL your claims, stand outside them.", "0: untrusted player");
		this.addDefault(defaults, Messages.OnlySellBlocks, "Claim blocks may only be sold, not purchased.", null);
		this.addDefault(defaults, Messages.BlockPurchaseCost, "Each claim block costs {0}.  Your balance is {1}.", "0: cost of one block; 1: player's account balance");
		this.addDefault(defaults, Messages.ClaimBlockLimit, "You've reached your claim block limit.  You can't purchase more.", null);
		this.addDefault(defaults, Messages.InsufficientFunds, "You don't have enough money.  You need {0}, but you only have {1}.", "0: total cost; 1: player's account balance");
		this.addDefault(defaults, Messages.PurchaseConfirmation, "Withdrew {0} from your account.  You now have {1} available claim blocks.", "0: total cost; 1: remaining blocks");
		this.addDefault(defaults, Messages.OnlyPurchaseBlocks, "Claim blocks may only be purchased, not sold.", null);
		this.addDefault(defaults, Messages.BlockSaleValue, "Each claim block is worth {0}.  You have {1} available for sale.", "0: block value; 1: available blocks");
		this.addDefault(defaults, Messages.NotEnoughBlocksForSale, "You don't have that many claim blocks available for sale.", null);
		this.addDefault(defaults, Messages.BlockSaleConfirmation, "Deposited {0} in your account.  You now have {1} available claim blocks.", "0: amount deposited; 1: remaining blocks");
		this.addDefault(defaults, Messages.AdminClaimsMode, "Administrative claims mode active.  Any claims created will be free and editable by other administrators.", null);
		this.addDefault(defaults, Messages.BasicClaimsMode, "Returned to basic claim creation mode.", null);
		this.addDefault(defaults, Messages.SubdivisionMode, "Subdivision mode.  Use your shovel to create subdivisions in your existing claims.  Use /basicclaims to exit.", null);
		this.addDefault(defaults, Messages.SubdivisionVideo2, "Click for Subdivision Help: {0}", "0:video URL");
		this.addDefault(defaults, Messages.DeleteClaimMissing, "There's no claim here.", null);
		this.addDefault(defaults, Messages.DeletionSubdivisionWarning, "This claim includes subdivisions.  If you're sure you want to delete it, use /DeleteClaim again.", null);
		this.addDefault(defaults, Messages.DeleteSuccess, "Claim deleted.", null);
		this.addDefault(defaults, Messages.CantDeleteAdminClaim, "You don't have permission to delete administrative claims.", null);
		this.addDefault(defaults, Messages.DeleteAllSuccess, "Deleted all of {0}'s claims.", "0: owner's name");
		this.addDefault(defaults, Messages.NoDeletePermission, "You don't have permission to delete claims.", null);
		this.addDefault(defaults, Messages.AllAdminDeleted, "Deleted all administrative claims.", null);
		this.addDefault(defaults, Messages.AdjustBlocksSuccess, "Adjusted {0}'s bonus claim blocks by {1}.  New total bonus blocks: {2}.", "0: player; 1: adjustment; 2: new total");
		this.addDefault(defaults, Messages.NotTrappedHere, "You can build here.  Save yourself.", null);
		this.addDefault(defaults, Messages.RescuePending, "If you stay put for 10 seconds, you'll be teleported out.  Please wait.", null);
		this.addDefault(defaults, Messages.AbandonClaimMissing, "Stand in the claim you want to delete, or consider /AbandonAllClaims.", null);
		this.addDefault(defaults, Messages.NotYourClaim, "This isn't your claim.", null);
		this.addDefault(defaults, Messages.DeleteTopLevelClaim, "To delete a subdivision, stand inside it.  Otherwise, use /AbandonTopLevelClaim to delete this claim and all subdivisions.", null);
		this.addDefault(defaults, Messages.AbandonSuccess, "Claim abandoned.  You now have {0} available claim blocks.", "0: remaining claim blocks");
		this.addDefault(defaults, Messages.CantGrantThatPermission, "You can't grant a permission you don't have yourself.", null);
		this.addDefault(defaults, Messages.GrantPermissionNoClaim, "Stand inside the claim where you want to grant permission.", null);
		this.addDefault(defaults, Messages.GrantPermissionConfirmation, "Granted {0} permission to {1} {2}.", "0: target player; 1: permission description; 2: scope (changed claims)");
		this.addDefault(defaults, Messages.ManageUniversalPermissionsInstruction, "To manage permissions for ALL your claims, stand outside them.", null);
		this.addDefault(defaults, Messages.ManageOneClaimPermissionsInstruction, "To manage permissions for a specific claim, stand inside it.", null);
		this.addDefault(defaults, Messages.CollectivePublic, "the public", "as in 'granted the public permission to...'");
		this.addDefault(defaults, Messages.BuildPermission, "build", null);
		this.addDefault(defaults, Messages.ContainersPermission, "access containers and animals", null);
		this.addDefault(defaults, Messages.EntryPermission, "enter the claim", null);
		this.addDefault(defaults, Messages.AccessPermission, "use buttons and levers", null);
		this.addDefault(defaults, Messages.PermissionsPermission, "manage permissions", null);
		this.addDefault(defaults, Messages.LocationCurrentClaim, "in this claim", null);
		this.addDefault(defaults, Messages.LocationAllClaims, "in all your claims", null);
		this.addDefault(defaults, Messages.PvPImmunityStart, "You're protected from attack by other players as long as your inventory is empty.", null);
		this.addDefault(defaults, Messages.DonateItemsInstruction, "To give away the item(s) in your hand, left-click the chest again.", null);
		this.addDefault(defaults, Messages.ChestFull, "This chest is full.", null);
		this.addDefault(defaults, Messages.DonationSuccess, "Item(s) transferred to chest!", null);
		this.addDefault(defaults, Messages.PlayerTooCloseForFire, "You can't start a fire this close to {0}.", "0: other player's name");
		this.addDefault(defaults, Messages.TooDeepToClaim, "This chest can't be protected because it's too deep underground.  Consider moving it.", null);
		this.addDefault(defaults, Messages.ChestClaimConfirmation, "This chest is protected.", null);
		this.addDefault(defaults, Messages.AutomaticClaimNotification, "This chest and nearby blocks are protected from breakage and theft.", null);
		this.addDefault(defaults, Messages.UnprotectedChestWarning, "This chest is NOT protected.  Consider using a golden shovel to expand an existing claim or to create a new one.", null);
		this.addDefault(defaults, Messages.ThatPlayerPvPImmune, "You can't injure defenseless players.", null);
		this.addDefault(defaults, Messages.CantFightWhileImmune, "You can't fight someone while you're protected from PvP.", null);
		this.addDefault(defaults, Messages.NoDamageClaimedEntity, "That belongs to {0}.", "0: owner name");
		this.addDefault(defaults, Messages.ShovelBasicClaimMode, "Shovel returned to basic claims mode.", null);
		this.addDefault(defaults, Messages.RemainingBlocks, "You may claim up to {0} more blocks.", "0: remaining blocks");
		this.addDefault(defaults, Messages.CreativeBasicsVideo2, "Click for Land Claim Help: {0}", "{0}: video URL");
		this.addDefault(defaults, Messages.SurvivalBasicsVideo2, "Click for Land Claim Help: {0}", "{0}: video URL");
		this.addDefault(defaults, Messages.TrappedChatKeyword, "trapped", "When mentioned in chat, players get information about the /trapped command.");
		this.addDefault(defaults, Messages.TrappedInstructions, "Are you trapped in someone's land claim?  Try the /trapped command.", null);
		this.addDefault(defaults, Messages.PvPNoDrop, "You can't drop items while in PvP combat.", null);
		this.addDefault(defaults, Messages.PvPNoContainers, "You can't access containers during PvP combat.", null);
		this.addDefault(defaults, Messages.PvPImmunityEnd, "Now you can fight with other players.", null);
		this.addDefault(defaults, Messages.NoBedPermission, "{0} hasn't given you permission to sleep here.", "0: claim owner");
		this.addDefault(defaults, Messages.NoWildernessBuckets, "You may only dump buckets inside your claim(s) or underground.", null);
		this.addDefault(defaults, Messages.NoLavaNearOtherPlayer, "You can't place lava this close to {0}.", "0: nearby player");
		this.addDefault(defaults, Messages.TooFarAway, "That's too far away.", null);
		this.addDefault(defaults, Messages.BlockNotClaimed, "No one has claimed this block.", null);
		this.addDefault(defaults, Messages.BlockClaimed, "That block has been claimed by {0}.", "0: claim owner");
		this.addDefault(defaults, Messages.RestoreNaturePlayerInChunk, "Unable to restore.  {0} is in that chunk.", "0: nearby player");
		this.addDefault(defaults, Messages.NoCreateClaimPermission, "You don't have permission to claim land.", null);
		this.addDefault(defaults, Messages.ResizeClaimTooSmall, "This new size would be too small.  Claims must be at least {0} x {0}.", "0: minimum claim size");
		this.addDefault(defaults, Messages.ResizeNeedMoreBlocks, "You don't have enough blocks for this size.  You need {0} more.", "0: how many needed");
		this.addDefault(defaults, Messages.ClaimResizeSuccess, "Claim resized.  {0} available claim blocks remaining.", "0: remaining blocks");
		this.addDefault(defaults, Messages.ResizeFailOverlap, "Can't resize here because it would overlap another nearby claim.", null);
		this.addDefault(defaults, Messages.ResizeStart, "Resizing claim.  Use your shovel again at the new location for this corner.", null);
		this.addDefault(defaults, Messages.ResizeFailOverlapSubdivision, "You can't create a subdivision here because it would overlap another subdivision.  Consider /abandonclaim to delete it, or use your shovel at a corner to resize it.", null);
		this.addDefault(defaults, Messages.SubdivisionStart, "Subdivision corner set!  Use your shovel at the location for the opposite corner of this new subdivision.", null);
		this.addDefault(defaults, Messages.CreateSubdivisionOverlap, "Your selected area overlaps another subdivision.", null);
		this.addDefault(defaults, Messages.SubdivisionSuccess, "Subdivision created!  Use /trust to share it with friends.", null);
		this.addDefault(defaults, Messages.CreateClaimFailOverlap, "You can't create a claim here because it would overlap your other claim.  Use /abandonclaim to delete it, or use your shovel at a corner to resize it.", null);
		this.addDefault(defaults, Messages.CreateClaimFailOverlapOtherPlayer, "You can't create a claim here because it would overlap {0}'s claim.", "0: other claim owner");
		this.addDefault(defaults, Messages.ClaimsDisabledWorld, "Land claims are disabled in this world.", null);
		this.addDefault(defaults, Messages.ClaimStart, "Claim corner set!  Use the shovel again at the opposite corner to claim a rectangle of land.  To cancel, put your shovel away.", null);
		this.addDefault(defaults, Messages.NewClaimTooSmall, "This claim would be too small.  Any claim must be at least {0} x {0}.", "0: minimum claim size");
		this.addDefault(defaults, Messages.CreateClaimInsufficientBlocks, "You don't have enough blocks to claim that entire area.  You need {0} more blocks.", "0: additional blocks needed");
		this.addDefault(defaults, Messages.AbandonClaimAdvertisement, "To delete another claim and free up some blocks, use /AbandonClaim.", null);
		this.addDefault(defaults, Messages.CreateClaimFailOverlapShort, "Your selected area overlaps an existing claim.", null);
		this.addDefault(defaults, Messages.CreateClaimSuccess, "Claim created!  Use /trust to share it with friends.", null);
		this.addDefault(defaults, Messages.RescueAbortedMoved, "You moved!  Rescue cancelled.", null);
		this.addDefault(defaults, Messages.OnlyOwnersModifyClaims, "Only {0} can modify this claim.", "0: owner name");
		this.addDefault(defaults, Messages.NoBuildPvP, "You can't build in claims during PvP combat.", null);
		this.addDefault(defaults, Messages.NoBuildPermission, "You don't have {0}'s permission to build here.", "0: owner name");
		this.addDefault(defaults, Messages.NoAccessPermission, "You don't have {0}'s permission to use that.", "0: owner name.  access permission controls buttons, levers, and beds");
		this.addDefault(defaults, Messages.NoEntryPermission, "You don't have {0}'s permission to enter this claim.", "0: owner name");
		this.addDefault(defaults, Messages.NoContainersPermission, "You don't have {0}'s permission to use that.", "0: owner's name.  containers also include crafting blocks");
		this.addDefault(defaults, Messages.OwnerNameForAdminClaims, "an administrator", "as in 'You don't have an administrator's permission to build here.'");
		this.addDefault(defaults, Messages.ClaimTooSmallForEntities, "This claim isn't big enough for that.  Try enlarging it.", null);
		this.addDefault(defaults, Messages.TooManyEntitiesInClaim, "This claim has too many entities already.  Try enlarging the claim or removing some animals, monsters, paintings, or minecarts.", null);
		this.addDefault(defaults, Messages.YouHaveNoClaims, "You don't have any land claims.", null);
		this.addDefault(defaults, Messages.ConfirmFluidRemoval, "Abandoning this claim will remove lava inside the claim.  If you're sure, use /AbandonClaim again.", null);
		this.addDefault(defaults, Messages.AutoBanNotify, "Auto-banned {0}({1}).  See logs for details.", null);
		this.addDefault(defaults, Messages.AdjustGroupBlocksSuccess, "Adjusted bonus claim blocks for players with the {0} permission by {1}.  New total: {2}.", "0: permission; 1: adjustment amount; 2: new total bonus");
		this.addDefault(defaults, Messages.InvalidPermissionID, "Please specify a player name, or a permission in [brackets].", null);
		this.addDefault(defaults, Messages.UntrustOwnerOnly, "Only {0} can revoke permissions here.", "0: claim owner's name");
		this.addDefault(defaults, Messages.HowToClaimRegex, "(^|.*\\W)how\\W.*\\W(claim|protect|lock)(\\W.*|$)", "This is a Java Regular Expression.  Look it up before editing!  It's used to tell players about the demo video when they ask how to claim land.");
		this.addDefault(defaults, Messages.NoBuildOutsideClaims, "You can't build here unless you claim some land first.", null);
		this.addDefault(defaults, Messages.PlayerOfflineTime, "  Last login: {0} days ago.", "0: number of full days since last login");
		this.addDefault(defaults, Messages.BuildingOutsideClaims, "Other players can build here, too.  Consider creating a land claim to protect your work!", null);
		this.addDefault(defaults, Messages.TrappedWontWorkHere, "Sorry, unable to find a safe location to teleport you to.  Contact an admin, or consider /kill if you don't want to wait.", null);
		this.addDefault(defaults, Messages.CommandBannedInPvP, "You can't use that command while in PvP combat.", null);
		this.addDefault(defaults, Messages.UnclaimCleanupWarning, "The land you've unclaimed may be changed by other players or cleaned up by administrators.  If you've built something there you want to keep, you should reclaim it.", null);
		this.addDefault(defaults, Messages.BuySellNotConfigured, "Sorry, buying anhd selling claim blocks is disabled.", null);
		this.addDefault(defaults, Messages.NoTeleportPvPCombat, "You can't teleport while fighting another player.", null);
		this.addDefault(defaults, Messages.NoTNTDamageAboveSeaLevel, "Warning: TNT will not destroy blocks above sea level.", null);
		this.addDefault(defaults, Messages.NoTNTDamageClaims, "Warning: TNT will not destroy claimed blocks.", null);
		this.addDefault(defaults, Messages.IgnoreClaimsAdvertisement, "To override, use /IgnoreClaims.", null);
		this.addDefault(defaults, Messages.NoPermissionForCommand, "You don't have permission to do that.", null);
		this.addDefault(defaults, Messages.ClaimsListNoPermission, "You don't have permission to get information about another player's land claims.", null);
		this.addDefault(defaults, Messages.ExplosivesDisabled, "This claim is now protected from explosions.  Use /ClaimExplosions again to disable.", null);
		this.addDefault(defaults, Messages.ExplosivesEnabled, "This claim is now vulnerable to explosions.  Use /ClaimExplosions again to re-enable protections.", null);
		this.addDefault(defaults, Messages.ClaimExplosivesAdvertisement, "To allow explosives to destroy blocks in this land claim, use /ClaimExplosions.", null);
		this.addDefault(defaults, Messages.PlayerInPvPSafeZone, "That player is in a PvP safe zone.", null);
		this.addDefault(defaults, Messages.NoPistonsOutsideClaims, "Warning: Pistons won't move blocks outside land claims.", null);
		this.addDefault(defaults, Messages.SoftMuted, "Soft-muted {0}.", "0: The changed player's name.");
		this.addDefault(defaults, Messages.UnSoftMuted, "Un-soft-muted {0}.", "0: The changed player's name.");
		this.addDefault(defaults, Messages.DropUnlockAdvertisement, "Other players can't pick up your dropped items unless you /UnlockDrops first.", null);
		this.addDefault(defaults, Messages.PickupBlockedExplanation, "You can't pick this up unless {0} uses /UnlockDrops.", "0: The item stack's owner.");
		this.addDefault(defaults, Messages.DropUnlockConfirmation, "Unlocked your drops.  Other players may now pick them up (until you die again).", null);
		this.addDefault(defaults, Messages.AdvertiseACandACB, "You may use /ACB to give yourself more claim blocks, or /AdminClaims to create a free administrative claim.", null);
		this.addDefault(defaults, Messages.AdvertiseAdminClaims, "You could create an administrative land claim instead using /AdminClaims, which you'd share with other administrators.", null);
		this.addDefault(defaults, Messages.AdvertiseACB, "You may use /ACB to give yourself more claim blocks.", null);
		this.addDefault(defaults, Messages.NotYourPet, "That belongs to {0} until it's given to you with /GivePet.", "0: owner name");
		this.addDefault(defaults, Messages.PetGiveawayConfirmation, "Pet transferred.", null);
		this.addDefault(defaults, Messages.PetTransferCancellation, "Pet giveaway cancelled.", null);
		this.addDefault(defaults, Messages.ReadyToTransferPet, "Ready to transfer!  Right-click the pet you'd like to give away, or cancel with /GivePet cancel.", null);
		this.addDefault(defaults, Messages.AvoidGriefClaimLand, "Prevent grief!  If you claim your land, you will be grief-proof.", null);
		this.addDefault(defaults, Messages.BecomeMayor, "Subdivide your land claim and become a mayor!", null);
		this.addDefault(defaults, Messages.ClaimCreationFailedOverClaimCountLimit, "You've reached your limit on land claims.  Use /AbandonClaim to remove one before creating another.", null);
		this.addDefault(defaults, Messages.CreateClaimFailOverlapRegion, "You can't claim all of this because you're not allowed to build here.", null);
		this.addDefault(defaults, Messages.ResizeFailOverlapRegion, "You don't have permission to build there, so you can't claim that area.", null);
		this.addDefault(defaults, Messages.NoBuildPortalPermission, "You can't use this portal because you don't have {0}'s permission to build an exit portal in the destination land claim.", "0: Destination land claim owner's name.");
		this.addDefault(defaults, Messages.ShowNearbyClaims, "Found {0} land claims.", "0: Number of claims found.");
		this.addDefault(defaults, Messages.NoChatUntilMove, "Sorry, but you have to move a little more before you can chat.  We get lots of spam bots here.  :)", null);
		this.addDefault(defaults, Messages.SetClaimBlocksSuccess, "Updated accrued claim blocks.", null);

		// load the config file
		final FileConfiguration config = YamlConfiguration.loadConfiguration(new File(messagesFilePath));

		// for each message ID
		for (int i = 0; i < messageIDs.length; i++) {
			// get default for this message
			final Messages messageID = messageIDs[i];
			CustomizableMessage messageData = defaults.get(messageID.name());

			// if default is missing, log an error and use some fake data for
			// now so that the plugin can run
			if (messageData == null) {
				GriefPreventionPlus.addLogEntry("Missing message for " + messageID.name() + ".  Please contact the developer.");
				messageData = new CustomizableMessage(messageID, "Missing message!  ID: " + messageID.name() + ".  Please contact a server admin.", null);
			}

			// read the message from the file, use default if necessary
			this.messages[messageID.ordinal()] = config.getString("Messages." + messageID.name() + ".Text", messageData.getText());
			config.set("Messages." + messageID.name() + ".Text", this.messages[messageID.ordinal()]);

			if (messageData.getNotes() != null) {
				messageData.setNotes(config.getString("Messages." + messageID.name() + ".Notes", messageData.getNotes()));
				config.set("Messages." + messageID.name() + ".Notes", messageData.getNotes());
			}
		}

		// save any changes
		try {
			config.save(DataStore.messagesFilePath);
		} catch (final IOException exception) {
			GriefPreventionPlus.addLogEntry("Unable to write to the configuration file at \"" + DataStore.messagesFilePath + "\"");
		}

		defaults.clear();
		System.gc();
	}

	private void loadSoftMutes() {
		final File softMuteFile = new File(softMuteFilePath);
		if (softMuteFile.exists()) {
			BufferedReader inStream = null;
			try {
				// open the file
				inStream = new BufferedReader(new FileReader(softMuteFile.getAbsolutePath()));

				// while there are lines left
				String nextID = inStream.readLine();
				while (nextID != null) {
					// parse line into a UUID
					UUID playerID;
					try {
						playerID = UUID.fromString(nextID);
					} catch (final Exception e) {
						playerID = null;
						GriefPreventionPlus.addLogEntry("Failed to parse soft mute entry as a UUID: " + nextID);
					}

					// push it into the map
					if (playerID != null) {
						this.softMuteMap.put(playerID, true);
					}

					// move to the next
					nextID = inStream.readLine();
				}
			} catch (final Exception e) {
				GriefPreventionPlus.addLogEntry("Failed to read from the soft mute data file: " + e.toString());
				e.printStackTrace();
			}

			try {
				if (inStream != null) {
					inStream.close();
				}
			} catch (final IOException exception) {
			}
		}
	}

	private void saveSoftMutes() {
		BufferedWriter outStream = null;

		try {
			// open the file and write the new value
			final File softMuteFile = new File(softMuteFilePath);
			softMuteFile.createNewFile();
			outStream = new BufferedWriter(new FileWriter(softMuteFile));

			for (final Map.Entry<UUID, Boolean> entry : this.softMuteMap.entrySet()) {
				if (entry.getValue().booleanValue()) {
					outStream.write(entry.getKey().toString());
					outStream.newLine();
				}
			}

		}

		// if any problem, log it
		catch (final Exception e) {
			GriefPreventionPlus.addLogEntry("Unexpected exception saving soft mute data: " + e.getMessage());
			e.printStackTrace();
		}

		// close the file
		try {
			if (outStream != null) {
				outStream.close();
			}
		} catch (final IOException exception) {
		}
	}

	// adds a claim to the datastore, making it an effective claim
	synchronized void addClaim(Claim newClaim, boolean writeToStorage) {
		// subdivisions are easy
		if (newClaim.getParent() != null) {
			newClaim.getParent().getChildren().add(newClaim);
			if (writeToStorage) {
				this.dbNewClaim(newClaim);
			}
			return;
		}

		if (writeToStorage) { // write the new claim on the db, so we get the id
			// generated by the database
			this.dbNewClaim(newClaim);
			GriefPreventionPlus.addLogEntry(newClaim.getOwnerName() + " made a new claim (id " + newClaim.id + ") at " + newClaim.locationToString());
		}

		this.claims.put(newClaim.id, newClaim);

		this.posClaimsAdd(newClaim);

		// except for administrative claims (which have no owner), update the
		// owner's playerData with the new claim
		if (!newClaim.isAdminClaim() && writeToStorage) {
			final PlayerData ownerData = this.getPlayerData(newClaim.getOwnerID());
			ownerData.getClaims().add(newClaim);
			this.savePlayerData(newClaim.getOwnerID(), ownerData);
		}
	}

	// removes cached player data from memory
	synchronized void clearCachedPlayerData(UUID playerID) {
		this.playerNameToPlayerDataMap.remove(playerID);
	}

	int clearOrphanClaims() {
		int count = 0;
		try {
			this.refreshDataConnection();
			final Statement statement = this.databaseConnection.createStatement();
			final Statement statement2 = this.databaseConnection.createStatement();
			final ResultSet results = statement.executeQuery("SELECT * FROM gpp_claims;");

			while (results.next()) {
				final World world = GriefPreventionPlus.getInstance().getServer().getWorld(toUUID(results.getBytes(3)));
				if ((world == null) || !GriefPreventionPlus.getInstance().getServer().getOfflinePlayer(toUUID(results.getBytes(2))).hasPlayedBefore() || ((results.getInt(8) != -1) && (this.getClaim(results.getInt(8)) == null))) {
					statement2.executeUpdate("DELETE FROM gpp_claims WHERE id=" + results.getInt(1));
					count++;
				}
			}
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("SQL Error during clear orphan claims. Details: " + e.getMessage());
		}

		return count;
	}

	synchronized void close() {
		if (this.databaseConnection != null) {
			try {
				if (!this.databaseConnection.isClosed()) {
					this.databaseConnection.close();
				}
			} catch (final SQLException e) {
			}
			;
		}

		this.databaseConnection = null;
	}

	synchronized void dbNewClaim(Claim claim) {
		try {
			this.refreshDataConnection();
			final Statement statement = this.databaseConnection.createStatement();

			statement.executeUpdate("INSERT INTO gpp_claims (owner, world, lesserX, lesserZ, greaterX, greaterZ, parentid) " + "VALUES (" + UUIDtoHexString(claim.getOwnerID()) + ", " + UUIDtoHexString(claim.getLesserBoundaryCorner().getWorld().getUID()) + ", " + claim.getLesserBoundaryCorner().getBlockX() + ", " + claim.getLesserBoundaryCorner().getBlockZ() + ", " + claim.getGreaterBoundaryCorner().getBlockX() + ", " + claim.getGreaterBoundaryCorner().getBlockZ() + ", " + (claim.getParent() != null ? claim.getParent().id : -1) + ");", Statement.RETURN_GENERATED_KEYS);

			final ResultSet result = statement.getGeneratedKeys();
			result.next();
			claim.id = result.getInt(1);
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to insert data for new claim at " + claim.locationToString() + ".  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
		}
	}

	synchronized void dbSetPerm(Integer claimId, String permString, int perm) {
		try {
			this.refreshDataConnection();
			final Statement statement = this.databaseConnection.createStatement();

			statement.executeUpdate("INSERT INTO gpp_permsbukkit VALUES (" + claimId + ", \"" + permString + "\", " + perm + ") ON DUPLICATE KEY UPDATE perm=perm | " + perm + ";");
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to set perms for claim id " + claimId + " perm [" + permString + "].  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
		}
	}

	synchronized void dbSetPerm(Integer claimId, UUID playerId, int perm) {
		try {
			this.refreshDataConnection();
			final Statement statement = this.databaseConnection.createStatement();

			statement.executeUpdate("INSERT INTO gpp_permsplayer VALUES (" + claimId + ", " + UUIDtoHexString(playerId) + ", " + perm + ") ON DUPLICATE KEY UPDATE perm=perm | " + perm + ";");
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to set perms for claim id " + claimId + " player {" + playerId.toString() + "}.  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
		}
	}

	/** Unset all claim's perms */
	synchronized void dbUnsetPerm(Integer claimId) {
		try {
			this.refreshDataConnection();
			final Statement statement = this.databaseConnection.createStatement();

			statement.executeUpdate("DELETE FROM gpp_permsplayer WHERE claimid=" + claimId + ";");
			statement.executeUpdate("DELETE FROM gpp_permsbukkit WHERE claimid=" + claimId + ";");
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to unset perms for claim id " + claimId + ".  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
		}
	}

	/** Unset permBukkit's perm from claim */
	synchronized void dbUnsetPerm(Integer claimId, String permString) {
		try {
			this.refreshDataConnection();
			final Statement statement = this.databaseConnection.createStatement();

			statement.executeUpdate("DELETE FROM gpp_permsbukkit WHERE claimid=" + claimId + " AND pname=\"" + permString + "\";");
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to unset perms for claim id " + claimId + " perm [" + permString + "].  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
		}
	}

	/** Unset playerId's perm from claim */
	synchronized void dbUnsetPerm(Integer claimId, UUID playerId) {
		try {
			this.refreshDataConnection();
			final Statement statement = this.databaseConnection.createStatement();

			statement.executeUpdate("DELETE FROM gpp_permsplayer WHERE claimid=" + claimId + " AND player=" + UUIDtoHexString(playerId) + ";");
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to unset perms for claim id " + claimId + " player {" + playerId.toString() + "}.  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
		}
	}

	/** Unset all player claims' perms */
	synchronized void dbUnsetPerm(UUID playerId) {
		try {
			this.refreshDataConnection();
			final Statement statement = this.databaseConnection.createStatement();

			statement.executeUpdate("DELETE p FROM gpp_permsplayer AS p INNER JOIN gpp_claims AS c ON p.claimid = c.id WHERE c.owner=" + UUIDtoHexString(playerId) + ";");
			statement.executeUpdate("DELETE p FROM gpp_permsbukkit AS p INNER JOIN gpp_claims AS c ON p.claimid = c.id WHERE c.owner=" + UUIDtoHexString(playerId) + ";");
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to unset perms for " + playerId.toString() + "'s claims.  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
		}
	}

	/** Unset permbukkit perms from all owner's claim */
	synchronized void dbUnsetPerm(UUID owner, String permString) {
		try {
			this.refreshDataConnection();
			final Statement statement = this.databaseConnection.createStatement();

			statement.executeUpdate("DELETE p FROM gpp_permsbukkit AS p INNER JOIN gpp_claims AS c ON p.claimid = c.id WHERE c.owner=" + UUIDtoHexString(owner) + " AND p.pname=\"" + permString + "\";");
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to unset [" + permString + "] perms from {" + owner.toString() + "}'s claims.  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
		}
	}

	/** Unset playerId perms from all owner's claim */
	synchronized void dbUnsetPerm(UUID owner, UUID playerId) {
		try {
			this.refreshDataConnection();
			final Statement statement = this.databaseConnection.createStatement();

			statement.executeUpdate("DELETE p FROM gpp_permsplayer AS p INNER JOIN gpp_claims AS c ON p.claimid = c.id WHERE c.owner=" + UUIDtoHexString(owner) + " AND p.player=" + UUIDtoHexString(playerId) + ";");
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to unset {" + playerId.toString() + "} perms from {" + owner.toString() + "}'s claims.  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
		}
	}

	synchronized void dbUpdateLocation(Claim claim) {
		try {
			this.refreshDataConnection();
			final Statement statement = this.databaseConnection.createStatement();

			statement.executeUpdate("UPDATE gpp_claims SET lesserX=" + claim.getLesserBoundaryCorner().getBlockX() + ", lesserZ=" + claim.getLesserBoundaryCorner().getBlockZ() + ", greaterX=" + claim.getGreaterBoundaryCorner().getBlockX() + ", greaterZ=" + claim.getGreaterBoundaryCorner().getBlockZ() + " WHERE id=" + claim.id);
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to update location for claim id " + claim.id + ".  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
		}
	}

	synchronized void dbUpdateOwner(Claim claim) {
		try {
			this.refreshDataConnection();
			final Statement statement = this.databaseConnection.createStatement();

			statement.executeUpdate("UPDATE gpp_claims SET owner=" + UUIDtoHexString(claim.getOwnerID()) + " WHERE id=" + claim.id);
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to update owner for claim id " + claim.id + ".  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
		}
	}

	// deletes a claim from the database (this delete subclaims too)
	synchronized void deleteClaimFromSecondaryStorage(Claim claim) {
		try {
			this.refreshDataConnection();

			final Statement statement = this.databaseConnection.createStatement();
			if (claim.getChildren().isEmpty()) {
				statement.execute("DELETE p FROM gpp_claims AS c RIGHT JOIN gpp_permsbukkit AS p ON c.id = p.claimid WHERE c.id=" + claim.id + ";");
				statement.execute("DELETE p FROM gpp_claims AS c RIGHT JOIN gpp_permsplayer AS p ON c.id = p.claimid WHERE c.id=" + claim.id + ";");
				statement.execute("DELETE FROM gpp_claims WHERE id=" + claim.id + ";");
			} else {
				statement.execute("DELETE p FROM gpp_claims AS c RIGHT JOIN gpp_permsbukkit AS p ON c.id = p.claimid WHERE c.id=" + claim.id + " OR c.parentid=" + claim.id + ";");
				statement.execute("DELETE p FROM gpp_claims AS c RIGHT JOIN gpp_permsplayer AS p ON c.id = p.claimid WHERE c.id=" + claim.id + " OR c.parentid=" + claim.id + ";");
				statement.execute("DELETE FROM gpp_claims WHERE id=" + claim.id + " OR parentid=" + claim.id + ";");
			}
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to delete data for claim " + claim.id + ".  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
			e.printStackTrace();
		}
	}

	// gets the number of bonus blocks a player has from his permissions
	// Bukkit doesn't allow for checking permissions of an offline player.
	// this will return 0 when he's offline, and the correct number when online.
	synchronized int getGroupBonusBlocks(UUID playerID) {
		final Player player = GriefPreventionPlus.getInstance().getServer().getPlayer(playerID);
		if (player != null) {
			int bonusBlocks = 0;
			for (final Entry<String, Integer> e : this.permissionToBonusBlocksMap.entrySet()) {
				if ((player != null) && player.hasPermission(e.getKey())) {
					bonusBlocks += e.getValue();
				}
			}
			return bonusBlocks;
		} else {
			return 0;
		}
	}

	synchronized PlayerData getPlayerDataFromStorage(UUID playerID) {
		try {
			this.refreshDataConnection();

			final Statement statement = this.databaseConnection.createStatement();
			final ResultSet results = statement.executeQuery("SELECT * FROM gpp_playerdata WHERE player=" + UUIDtoHexString(playerID) + ";");

			// if data for this player exists, use it
			if (results.next()) {
				return new PlayerData(playerID, results.getInt(2), results.getInt(3));
			}
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to retrieve data for player " + playerID.toString() + ".  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
			e.printStackTrace();
		}

		return null;
	}
	
	boolean isSoftMuted(UUID playerID) {
		final Boolean mapEntry = this.softMuteMap.get(playerID);
		if ((mapEntry == null) || (mapEntry == Boolean.FALSE)) {
			return false;
		}

		return true;
	}

	void posClaimsAdd(Claim claim) {
		final int lx = claim.getLesserBoundaryCorner().getBlockX() >> 8;
		final int lz = claim.getLesserBoundaryCorner().getBlockZ() >> 8;

		final int gx = claim.getGreaterBoundaryCorner().getBlockX() >> 8;
		final int gz = claim.getGreaterBoundaryCorner().getBlockZ() >> 8;

		for (int i = lx; i <= gx; i++) {
			for (int j = lz; j <= gz; j++) {
				Map<Integer, Claim> claimMap = this.posClaims.get(from2int(i, j));
				if (claimMap == null) {
					claimMap = new HashMap<Integer, Claim>();
					this.posClaims.put(from2int(i, j), claimMap);
				}
				claimMap.put(claim.getID(), claim);
			}
		}
	}

	Claim posClaimsGet(Location loc) {
		final Map<Integer, Claim> claimMap = this.posClaims.get(from2int(loc.getBlockX() >> 8, loc.getBlockZ() >> 8));
		if (claimMap != null) {
			for (final Claim claim : claimMap.values()) {
				if (claim.contains(loc, true, false)) {
					return claim;
				}
			}
		}
		return null;
	}

	void posClaimsRemove(Claim claim) {
		final int lx = claim.getLesserBoundaryCorner().getBlockX() >> 8;
		final int lz = claim.getLesserBoundaryCorner().getBlockZ() >> 8;

		final int gx = claim.getGreaterBoundaryCorner().getBlockX() >> 8;
		final int gz = claim.getGreaterBoundaryCorner().getBlockZ() >> 8;

		for (int i = lx; i <= gx; i++) {
			for (int j = lz; j <= gz; j++) {
				final Map<Integer, Claim> claimMap = this.posClaims.get(from2int(i, j));
				if (claimMap != null) {
					claimMap.remove(claim.getID());
				}
			}
		}
	}

	synchronized void refreshDataConnection() throws SQLException {
		if ((this.databaseConnection == null) || this.databaseConnection.isClosed()) {
			// set username/pass properties
			final Properties connectionProps = new Properties();
			connectionProps.put("user", this.userName);
			connectionProps.put("password", this.password);
			connectionProps.put("autoReconnect", "true");
			connectionProps.put("maxReconnects", "4");

			// establish connection
			this.databaseConnection = DriverManager.getConnection(this.databaseUrl, connectionProps);
		}
	}

	// updates the database with a group's bonus blocks
	synchronized void saveGroupBonusBlocks(String groupName, int currentValue) {
		// group bonus blocks are stored in the player data table, with player
		// name = $groupName
		try {
			this.refreshDataConnection();

			final Statement statement = this.databaseConnection.createStatement();
			statement.executeUpdate("INSERT INTO gpp_groupdata VALUES (\"" + groupName + "\", " + currentValue + ") ON DUPLICATE KEY UPDATE blocks=" + currentValue + ";");
		} catch (final SQLException e) {
			GriefPreventionPlus.addLogEntry("Unable to save data for group " + groupName + ".  Details:");
			GriefPreventionPlus.addLogEntry(e.getMessage());
		}
	}

	// updates soft mute map and data file
	boolean toggleSoftMute(UUID playerID) {
		final boolean newValue = !this.isSoftMuted(playerID);

		this.softMuteMap.put(playerID, newValue);
		this.saveSoftMutes();

		return newValue;
	}

	// path information, for where stuff stored on disk is well... stored
	protected final static String dataLayerFolderPath = "plugins" + File.separator + "GriefPreventionData";

	final static String configFilePath = dataLayerFolderPath + File.separator + "config.yml";

	final static String messagesFilePath = dataLayerFolderPath + File.separator + "messages.yml";

	final static String softMuteFilePath = dataLayerFolderPath + File.separator + "softMute.txt";

	// video links
	static final String SURVIVAL_VIDEO_URL = "" + ChatColor.DARK_AQUA + ChatColor.UNDERLINE + "bit.ly/mcgpuser";

	static final String CREATIVE_VIDEO_URL = "" + ChatColor.DARK_AQUA + ChatColor.UNDERLINE + "bit.ly/mcgpcrea";

	static final String SUBDIVISION_VIDEO_URL = "" + ChatColor.DARK_AQUA + ChatColor.UNDERLINE + "bit.ly/mcgpsub";

	public static UUID toUUID(byte[] bytes) {
		if (bytes.length != 16) {
			throw new IllegalArgumentException();
		}
		int i = 0;
		long msl = 0;
		for (; i < 8; i++) {
			msl = (msl << 8) | (bytes[i] & 0xFF);
		}
		long lsl = 0;
		for (; i < 16; i++) {
			lsl = (lsl << 8) | (bytes[i] & 0xFF);
		}
		return new UUID(msl, lsl);
	}

	public static String UUIDtoHexString(UUID uuid) {
		if (uuid == null) {
			return "0";
		}
		return "0x" + org.apache.commons.lang.StringUtils.leftPad(Long.toHexString(uuid.getMostSignificantBits()), 16, "0") + org.apache.commons.lang.StringUtils.leftPad(Long.toHexString(uuid.getLeastSignificantBits()), 16, "0");
	}

	static long from2int(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	static int[] fromLongTo2int(long l) {
		final int[] r = { (int) (l >> 32), (int) l };
		return r;
	}

	private class SavePlayerDataThread extends Thread {
		private final UUID playerID;
		private final PlayerData playerData;

		SavePlayerDataThread(UUID playerID, PlayerData playerData) {
			this.playerID = playerID;
			this.playerData = playerData;
		}

		@Override
		public void run() {
			// ensure player data is already read from file before trying to
			// save
			this.playerData.getAccruedClaimBlocks();
			this.playerData.getClaims();
			DataStore.this.asyncSavePlayerData(this.playerID, this.playerData);
		}
	}
}
