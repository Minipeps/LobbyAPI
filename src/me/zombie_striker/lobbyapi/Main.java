/*
 *  Copyright (C) 2017 Zombie_Striker
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation; either version 2 of
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with this program;
 *  if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 *  02111-1307 USA
 */
package me.zombie_striker.lobbyapi;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import me.zombie_striker.lobbyapi.LobbyWorld.WeatherState;
import me.zombie_striker.lobbyapi.utils.*;
import me.zombie_striker.pluginconstructor.GithubUpdater;
import me.zombie_striker.pluginconstructor.PluginConstructorAPI;
import me.zombie_striker.lobbyapi.utils.ConfigHandler.ConfigKeys;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Main extends JavaPlugin implements Listener {

	private HashMap<String, World> lastWorld = new HashMap<String, World>();
	Set<LobbyWorld> worlds = new HashSet<LobbyWorld>();
	Set<LobbyServer> bungeeServers = new HashSet<LobbyServer>();

	Random random = ThreadLocalRandom.current();
	int inventorySize = 9;
	Inventory inventory;
	@SuppressWarnings("unused")
	private LobbyAPI la = new LobbyAPI(this);

	private ItemStack worldSelector = null;

	private static String prefix = ChatColor.GOLD + "[" + ChatColor.WHITE
			+ "LobbyAPI" + ChatColor.GOLD + "]" + ChatColor.WHITE;
	int enchID = 1;

	@EventHandler
	public void event(WeatherChangeEvent e) {
		LobbyWorld lw = LobbyAPI.getLobbyWorld(e.getWorld());
		if (lw == null)
			return;
		if (lw.getWeatherState() != WeatherState.NORMAL) 
			if ((e.toWeatherState() == (lw.getWeatherState() == WeatherState.NO_RAIN))
					|| (e.toWeatherState() != (lw.getWeatherState() == WeatherState.ALWAYS_RAIN)))
				e.setCancelled(true);
		
	}

	@EventHandler
	public void onHungerChange(FoodLevelChangeEvent e) {
		if (LobbyAPI.getLobbyWorld(e.getEntity().getWorld()) != null
				&& LobbyAPI.getLobbyWorld(e.getEntity().getWorld())
						.hasDisabledHungerAndHealth())
			e.setCancelled(true);
	}

	@EventHandler
	public void onDamage(EntityDamageEvent e) {
		if (e.getEntity() instanceof Player)
			if (LobbyAPI.getLobbyWorld(e.getEntity().getWorld())
					.hasDisabledHungerAndHealth())
				e.setCancelled(true);
	}

	public void onEnable() {
		if(!getDataFolder().exists()&&!new File(getDataFolder(),"config.yml").exists()){
			getConfig().set("disallowHubCommandNoPerm", true);
			saveConfig();
		}
		ConfigHandler.setConfig(getConfig(), new File(getDataFolder(),
				"config.yml"));

		// Download the API dependancy
		if (Bukkit.getPluginManager().getPlugin("PluginConstructorAPI") == null) {
		//	new DependencyDownloader(this, 276723);
			me.zombie_striker.lobbyapi.utils.GithubDependDownloader.autoUpdate(this, new File(getDataFolder().getParentFile(),"PluginConstructorAPI.jar"), "ZombieStriker", "PluginConstructorAPI", "PluginConstructorAPI.jar");
		}

		if (ConfigHandler.getCustomWorldKeys() != null)
			for (String w : ConfigHandler.getCustomWorldKeys()) 
				Bukkit.createWorld(new WorldCreator(w).seed(ConfigHandler
						.getCustomWorldInt(w,
								ConfigKeys.CustomAddedWorlds_Seed.s)));
			

		if (ConfigHandler.containsLobbyAPIVariable(ConfigKeys.WorldSelector.s)){
			
			setWorldSelector(ConfigHandler
					.getLobbyAPIVariableItemstack(ConfigKeys.WorldSelector.s));
		}

		if (Bukkit.getPluginManager().getPlugin("Multiverse-Inventories") != null) {
			Bukkit.getPluginManager().disablePlugin(
					Bukkit.getPluginManager().getPlugin(
							"Multiverse-Inventories"));
			Bukkit.broadcastMessage(prefix
					+ " LobbyAPI is incompatible with Multiverse-Inventories. Remove Multiverse-Inventories, as LobbyAPI should handle multiple inventories");
		}
		if (Bukkit.getPluginManager().getPlugin("PerWorldInventory") != null) {
			Bukkit.getPluginManager().disablePlugin(
					Bukkit.getPluginManager().getPlugin("PerWorldInventory"));
			Bukkit.broadcastMessage(prefix
					+ " LobbyAPI is incompatible with PerWorldInventory. Remove PerWorldInventory, as LobbyAPI should handle multiple inventories");
		}

		LobbyCommands command = new LobbyCommands(this);
		getCommand("lobbyAPI").setExecutor(command);
		getCommand("lobby").setExecutor(command);
		getCommand("lobbyAPI").setTabCompleter(command);
		getCommand("lobby").setTabCompleter(command);

		getServer().getPluginManager().registerEvents(this, this);
		new BukkitRunnable() {
			public void run() {
				for (LobbyWorld lb : getWorlds()) {
					if (lb.hasVoidDisable()) {
						for (Player p : lb.getMainWorld().getPlayers()) {
							if (p.getLocation().getY() < -2) {
								p.teleport(lb.getSpawn());
								p.setVelocity(new Vector(0, 0, 0));
							}
						}
					}
				}
			}
		}.runTaskTimer(this, 0, 20);

		// Time Check
		new BukkitRunnable() {
			public void run() {
				for (LobbyWorld wo : worlds) 
					if (wo != null && wo.hasStaticTime()) 
						getServer().getWorld(wo.getWorldName()).setTime(
								wo.getStaticTime());	
			}
		}.runTaskTimer(this, 0, 10 * 20L);
		if (getConfig() != null && getConfig().contains("hasBungee")
				&& getConfig().getBoolean("hasBungee")) {
			getServer().getMessenger().registerOutgoingPluginChannel(this,
					"BungeeCord");
			getServer().getConsoleSender()
					.sendMessage(
							prefix + ChatColor.GREEN
									+ "Bungee For LobbyAPI is enabled");
		} else {
			getServer().getConsoleSender().sendMessage(
					prefix + ChatColor.DARK_RED
							+ "Bungee For LobbyAPI is disabled");
		}
		try {
			enchID = PluginConstructorAPI.registerGlow();
		} catch (Exception e) {
		}
		loadLocalWorlds();
		loadLocalServers();

		if (!getConfig().contains("auto-update")) {
			getConfig().set("auto-update", true);
			saveConfig();
		}
		if (!getConfig().contains("hasBungee")) {
			getConfig().set("hasBungee", false);
			saveConfig();
		}

		//@SuppressWarnings("unused")
		//final Updater updater = new Updater(this, 91206, true);
		GithubUpdater.autoUpdate(this, "ZombieStriker", "LobbyAPI", "LobbyAPI");

		Metrics met = new Metrics(this);
		met.addCustomChart(new Metrics.SimplePie("worlds-loaded") {
			@Override
			public String getValue() {
				return String.valueOf(worlds.size());
			}
		});
		met.addCustomChart(new Metrics.SimplePie("bungee-support") {
			@Override
			public String getValue() {
				return String.valueOf(bungeeServers.size());
			}
		});
		met.addCustomChart(new Metrics.SimplePie("updater-active") {
			@Override
			public String getValue() {
				return String.valueOf(getConfig().getBoolean("auto-update"));
			}
		});
		if (!getConfig().contains("Version")
				|| !getConfig().getString("Version").equals(
						this.getDescription().getVersion())) {
			new UpdateAnouncer(this);
			getConfig().set("Version", this.getDescription().getVersion());
			saveConfig();
		}

		/*
		 * new BukkitRunnable() { public void run() { // TODO: Works well. Make
		 * changes for the updaters of // PixelPrinter and Music later. if
		 * (updater.updaterActive) updater.download(false); }
		 * }.runTaskTimerAsynchronously(this, 20 * 60, (long) (20 * 60 * 5.5));
		 */
	}

	public void onDisable() {
		for (Player p : getServer().getOnlinePlayers()) {
			saveInventory(p, p.getWorld());
			LobbyWorld lw = LobbyAPI.getLobbyWorld(p.getWorld());
			if (lw != null)
				if (lw.shouldWorldShouldSavePlayerLocation())
					lw.getLastLocation().put(p.getUniqueId(), p.getLocation());
		}
		for (LobbyWorld lw : worlds)
			if (lw.shouldWorldShouldSavePlayerLocation())
				ConfigHandler.setWorldVariable(lw, ConfigKeys.SavingLocation.s,
						lw.getLastLocation());
	}

	void setInventorySize(boolean isOp) {
		int maxSlot = 0;
		for (LobbyWorld w : this.worlds) {
			if (w.isHidden())
				continue;
			if (w != null && w.getSlot() > maxSlot) {
				maxSlot = w.getSlot();
			}
		}

		for (LobbyServer w : this.bungeeServers) {
			if (w.isHidden())
				continue;
			if (w != null && w.getSlot() > maxSlot) {
				maxSlot = w.getSlot();
			}
		}

		inventorySize = ((maxSlot / 9) + 1) * 9;
	}

	public void startsWith(List<String> ls, String s, String arg) {
		if (s.toLowerCase().startsWith(arg.toLowerCase()))
			ls.add(s);
	}

	@SuppressWarnings("deprecation")
	@EventHandler
	public void onClicky(PlayerInteractEvent e) {
		// Keeping in hand for backwards compatibility.
		if (e.getPlayer().getItemInHand() != null && getWorldSelector() != null
				&& e.getPlayer().getItemInHand().isSimilar(getWorldSelector()))
			Bukkit.dispatchCommand(e.getPlayer(), "lobby");

		if (e.getClickedBlock() != null
				&& e.getClickedBlock().getType() == Material.ENDER_CHEST) {
			if (LobbyAPI
					.getLobbyWorld(e.getClickedBlock().getWorld().getName())
					.hasEnderChest()) {
				e.getPlayer().closeInventory();
				e.setCancelled(true);
			}
		}
	}

	@EventHandler
	private void onPlayerLeave(PlayerQuitEvent event) {
		LobbyWorld lw = LobbyAPI.getLobbyWorld(event.getPlayer().getWorld());
		if (lw != null) {
			if (lw.shouldWorldShouldSavePlayerLocation())
				lw.setLastLocation(event.getPlayer(), event.getPlayer()
						.getLocation());
			saveInventory(event.getPlayer(), event.getPlayer().getWorld());
		}
	}

	@EventHandler
	private void onDeath(PlayerDeathEvent event) {
		LobbyWorld lw = LobbyAPI.getLobbyWorld(event.getEntity().getWorld());
		if (lw != null)
			if (lw.shouldWorldShouldSavePlayerLocation())
				lw.getLastLocation().remove(event.getEntity().getUniqueId());
	}

	@EventHandler
	private void onPlayerDeath(PlayerRespawnEvent event) {
		LobbyWorld lb = LobbyAPI.getLobbyWorld(event.getPlayer().getWorld()
				.getName());

		if (!event.getPlayer().getWorld().getGameRuleValue("keepInventory")
				.equalsIgnoreCase("true"))
			clearInventory(event.getPlayer());
		saveInventory(event.getPlayer(), event.getPlayer().getWorld());

		if (event.getPlayer().getBedSpawnLocation() != null
				&& event.getPlayer().getBedSpawnLocation().getWorld()
						.equals(lb.getMainWorld()))
			return;
		// Do not interfere if the player has a bed in this world.

		if (lb.getRespawnWorld() != null) {
			event.setRespawnLocation(LobbyAPI.getLobbyWorld(
					lb.getRespawnWorld()).getSpawn());
		} else if (LobbyWorld.getMainLobby() == null) {
			event.setRespawnLocation(lb.getSpawn());
		} else if (LobbyWorld.getMainLobby() != null) {
			event.setRespawnLocation(LobbyWorld.getMainLobby().getSpawn());
		} else {
			event.setRespawnLocation(LobbyAPI.getLobby().getSpawn());
			// Should never happen. Test it
		}
	}

	public static String getPrefix() {
		return prefix;
	}

	@EventHandler
	private void onPlayeJoin(final PlayerJoinEvent event) {
		World goingTo = event.getPlayer().getWorld();
		if (LobbyWorld.getMainLobby() != null) {
			goingTo = LobbyWorld.getMainLobby().getMainWorld();
			new BukkitRunnable() {
				public void run() {
					if (event.getPlayer() != null
							&& LobbyWorld.getMainLobby() != null
							&& LobbyWorld.getMainLobby().getSpawn() != null) {
						event.getPlayer().teleport(
								LobbyWorld.getMainLobby().getSpawn());
						cancel();
					}
				}
			}.runTaskTimer(this, 0, 5);
		}
		for (LobbyWorld wo : worlds)
			lastWorld.put(event.getPlayer().getName(),
					wo.hasMainWorld() ? wo.getMainWorld() : goingTo);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	private void onTeleport(PlayerPortalEvent event) {
		if (LobbyAPI.getLobbyWorld(event.getFrom().getWorld()) != null
				&& !LobbyAPI.getLobbyWorld(event.getFrom().getWorld())
						.hasPortal())
			event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	private void onWorldchange(PlayerChangedWorldEvent event) {
		final Player p = event.getPlayer();
		saveInventory(p, event.getFrom());
		LobbyWorld lw = LobbyAPI.getLobbyWorld(p.getWorld());
		if (lw != null)
			if (lw.shouldWorldShouldSavePlayerLocation())
				lw.setLastLocation(event.getPlayer(), event.getPlayer()
						.getLocation());

		new BukkitRunnable() {
			public void run() {
				if (LobbyAPI.getLobbyWorld(p.getWorld()) != null) {
					clearInventory(p);
					loadInventory(p, p.getWorld());
					if (LobbyAPI.getLobbyWorld(p.getWorld()).getGameMode() != null)
						p.setGameMode(LobbyAPI.getLobbyWorld(
								p.getWorld().getName()).getGameMode());

					if (LobbyAPI.getLobbyWorld(p.getWorld()).getSpawnItems() != null
							&& LobbyAPI.getLobbyWorld(p.getWorld())
									.getSpawnItems().size() > 0)
						for (ItemStack is : LobbyAPI
								.getLobbyWorld(p.getWorld()).getSpawnItems())
							if (is != null)
								if (!p.getInventory().contains(is))
									p.getInventory().addItem(is);
				}
				lastWorld.put(p.getName(), p.getWorld());
			}
		}.runTaskLater(this, 5);
	}

	@EventHandler
	public void onSelect(InventoryClickEvent event) {
		if (event.getInventory() == null)
			return;
		if (event.getCurrentItem() == null)
			return;

		if (!event.getInventory().equals(event.getWhoClicked().getInventory())) {
			if (event.getInventory().getTitle().equals("LobbyAPI")) {
				if (event.getCurrentItem().hasItemMeta()
						&& event.getCurrentItem().getItemMeta()
								.hasDisplayName()) {
					boolean isBungee = false;
					if (this.bungeeServers.size() > 0) {

						for (String s : event.getCurrentItem().getItemMeta()
								.getLore()) {
							if (s.contains(ChatColor.RED + "" + ChatColor.GREEN)) {
								isBungee = true;
								break;
							}
						}
					}

					if (isBungee) {
						for (LobbyServer s : bungeeServers) {
							if (event.getCurrentItem().getItemMeta()
									.getDisplayName().equals(s.getName())) {
								teleportBungee((Player) event.getWhoClicked(),
										s.getName());
								break;
							}

							event.setCancelled(true);
							event.getWhoClicked().closeInventory();
						}
					} else
						for (LobbyWorld wo : worlds) {
							if (wo == null)
								continue;
							if (wo.getDisplayName() == null) {
								Bukkit.getConsoleSender().sendMessage(
										"This world has no name :" + wo);
								event.getWhoClicked().sendMessage(
										"This world has no name :" + wo);
								continue;
							}
							if (event.getCurrentItem().getItemMeta()
									.getDisplayName().equals(wo.getDisplayName())) {
								if (event.getWhoClicked().hasPermission("lobbyapi.bypassworldlimits")||!wo.isPrivate()
										|| (wo.isPrivate() && getServer()
												.getWorld(wo.getWorldName())
												.getPlayers().size() < wo
												.getMaxPlayers())) {
									final PlayerSelectWorldEvent e = new PlayerSelectWorldEvent(
											(Player) event.getWhoClicked(), wo);
									if (wo.shouldWorldShouldSavePlayerLocation()
											&& wo.getPlayersLastLocation(e
													.getPlayer()) != null)
										e.setDestination(wo
												.getPlayersLastLocation(e
														.getPlayer()));
									Bukkit.getPluginManager().callEvent(e);
									if (!e.getIsCanceled()) {

										// TODO: Find a better way of checking.
										// If the player happens to have
										// teleported from another world, this
										// will not save their location.

										LobbyWorld lw = LobbyAPI
												.getLobbyWorld(event
														.getWhoClicked()
														.getWorld());
										if (lw != null) {
											if (lw.shouldWorldShouldSavePlayerLocation())
												lw.setLastLocation(
														(Player) event
																.getWhoClicked(),
														event.getWhoClicked()
																.getLocation());
										}

										e.getPlayer().teleport(
												e.getDestination());

										StringBuilder playersOnline = new StringBuilder();
										Object[] oo = wo.getPlayers().toArray();
										for (int i = 0; i < (wo.getPlayers()
												.size() < 6 ? wo.getPlayers()
												.size() : 7); i++)
											playersOnline
													.append(((Player) oo[i])
															.getDisplayName()
															+ (i != (oo.length - 1 < 7 ? oo.length - 1
																	: 7) ? " ,"
																	: (oo.length - 7 > 0 ? " ...("
																			+ (oo.length - 7)
																			+ " more)"
																			: "")));

										try {
											Method method = e
													.getPlayer()
													.getClass()
													.getMethod("sendTitle",
															String.class,
															String.class);
											if (method != null) {
												method.invoke(
														e.getPlayer(),
														ChatColor.GOLD
																+ "Teleporting to "
																+ (wo.getWorldName()),
														ChatColor.GRAY
																+ "Players: "
																+ playersOnline
																		.toString());
											} else {
												e.getPlayer()
														.sendMessage(
																ChatColor.GOLD
																		+ "Teleporting to "
																		+ (wo.getWorldName()));
												e.getPlayer()
														.sendMessage(
																ChatColor.GRAY
																		+ "Players: "
																		+ playersOnline
																				.toString());
											}
										} catch (Exception e2) {
											e.getPlayer()
													.sendMessage(
															ChatColor.GOLD
																	+ "Teleporting to "
																	+ (wo.getWorldName()));
											e.getPlayer()
													.sendMessage(
															ChatColor.GRAY
																	+ "Players: "
																	+ playersOnline
																			.toString());

										}
										event.setCancelled(true);

										event.getWhoClicked().closeInventory();
										Bukkit.getScheduler()
												.scheduleSyncDelayedTask(this,
														new Runnable() {
															public void run() {
																PlayerChangeWorldEvent e2 = new PlayerChangeWorldEvent(
																		e.getPlayer());
																Bukkit.getPluginManager()
																		.callEvent(
																				e2);
															}
														}, 2);
										for (String s : wo.getCommandsOnJoin()) {
											s = s.replaceAll("%player%", e
													.getPlayer().getName());
											Bukkit.dispatchCommand(
													e.getPlayer(), s);
										}
									} else {
										return;
									}
								} else {
									event.getWhoClicked()
											.sendMessage(
													ChatColor.RED
															+ "This world is full, please try again later.");
								}
								break;
							}
							event.setCancelled(true);
							event.getWhoClicked().closeInventory();
						}
				}
			}
		}
	}

	private void teleportBungee(Player p, String to) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);
		try {
			dos.writeUTF("Connect");
			dos.writeUTF(to);
		} catch (Exception e) {
			e.printStackTrace();
		}
		p.sendPluginMessage(this, "BungeeCord", baos.toByteArray());
	}

	private void clearInventory(Player p) {
		p.getInventory().clear();
		p.setHealth((double) 20);
		p.setExp((float) 0);
		p.setLevel(0);
		p.setFoodLevel((int) 20);
		for (PotionEffect ep : p.getActivePotionEffects())
			p.removePotionEffect(ep.getType());
	}

	private void loadInventory(Player p, World w) {
		if (w == null) {
			getServer()
					.getConsoleSender()
					.sendMessage(
							prefix
									+ " The world \""
									+ w
									+ "\" is null! Inventories for this world will not be saved.");
			return;
		}
		if (LobbyAPI.getLobbyWorld(w) == null) {
			getServer()
					.getConsoleSender()
					.sendMessage(
							prefix
									+ " You have no registered world called \""
									+ w.getName()
									+ "\"! Inventories for this world will not be loaded.");
			return;
		}
		if (p.getInventory() == null) {
			Bukkit.getConsoleSender().sendMessage(prefix + "Inventory is null");
			return;
		}
		String s = LobbyAPI.getLobbyWorld(w).getSaveName();
		for (int kl = 0; kl < 36; kl++)
			if ((ItemStack) getConfig().get(p.getName() + "." + s + ".i." + kl) != null)
				p.getInventory().setItem(
						kl,
						(ItemStack) getConfig().get(
								p.getName() + "." + s + ".i." + kl));
		p.getInventory().setBoots(
				(ItemStack) getConfig().get(p.getName() + "." + s + ".a." + 1));
		p.getInventory().setLeggings(
				(ItemStack) getConfig().get(p.getName() + "." + s + ".a." + 2));
		p.getInventory().setChestplate(
				(ItemStack) getConfig().get(p.getName() + "." + s + ".a." + 3));
		p.getInventory().setHelmet(
				(ItemStack) getConfig().get(p.getName() + "." + s + ".a." + 4));

		if (getConfig().get(p.getName() + "." + s + ".xpl") != null)
			p.setLevel((int) getConfig().get(p.getName() + "." + s + ".xpl"));
		if (getConfig().get(p.getName() + "." + s + ".hunger") != null)
			p.setFoodLevel((int) getConfig().get(
					p.getName() + "." + s + ".hunger"));
	}

	private void saveInventory(Player p, World w) {
		if (w == null) {
			getServer()
					.getConsoleSender()
					.sendMessage(
							prefix
									+ " The world \""
									+ w
									+ "\" is null! Inventories for this world will not be saved.");
			return;
		}
		if (LobbyAPI.getLobbyWorld(w) == null) {
			getServer()
					.getConsoleSender()
					.sendMessage(
							prefix
									+ " You have no registered world called \""
									+ w.getName()
									+ "\"! Inventories for this world will not be saved.");
			return;
		}
		String world2 = LobbyAPI.getLobbyWorld(w.getName()).getSaveName();
		getConfig().set(p.getName() + "." + world2 + ".xp", p.getExp());
		getConfig().set(p.getName() + "." + world2 + ".xpl", p.getLevel());
		getConfig().set(p.getName() + "." + world2 + ".health", p.getHealth());
		getConfig().set(p.getName() + "." + world2 + ".hunger",
				p.getFoodLevel());
		ItemStack[] is = p.getInventory().getContents();
		for (int kl = 0; kl < 36; kl++)
			getConfig().set(p.getName() + "." + world2 + ".i." + kl, is[kl]);

		getConfig().set(p.getName() + "." + world2 + ".a." + 1,
				p.getInventory().getBoots());
		getConfig().set(p.getName() + "." + world2 + ".a." + 2,
				p.getInventory().getLeggings());
		getConfig().set(p.getName() + "." + world2 + ".a." + 3,
				p.getInventory().getChestplate());
		getConfig().set(p.getName() + "." + world2 + ".a." + 4,
				p.getInventory().getHelmet());
		saveConfig();
	}

	public void loadLocalWorlds() {

		getServer().getScheduler().scheduleSyncDelayedTask(this,
				new Runnable() {
					@SuppressWarnings({ "deprecation", "unchecked" })
					public void run() {
						if (getConfig().contains("Worlds")) {
							for (String name : getConfig()
									.getConfigurationSection("Worlds").getKeys(
											false)) {
								String s = getConfig().getString(
										"Worlds." + name + ".name");
								String displayname=null;
								if(getConfig().contains(
										"Worlds." + name + ".displayname")) {
									displayname = getConfig().getString(
											"Worlds." + name + ".displayname");
								}else {
									displayname = s;
									getConfig().set(
											"Worlds." + name + ".displayname",s);
									saveConfig();
								}
								if (getConfig().contains(
										"Worlds." + name + ".loc")) {
									Location l = (Location) getConfig().get(
											"Worlds." + name + ".loc");
									if (l != null) {
										getConfig().set(
												"Worlds." + name + ".spawnLoc.x",
												l.getX());
										getConfig().set(
												"Worlds." + name + ".spawnLoc.y",
												l.getY());
										getConfig().set(
												"Worlds." + name + ".spawnLoc.z",
												l.getZ());
									} else {
										getConfig().set(
												"Worlds." + name + ".spawnLoc.x",
												0);
										getConfig().set(
												"Worlds." + name + ".spawnLoc.y",
												90);
										getConfig().set(
												"Worlds." + name + ".spawnLoc.z",
												0);
										Bukkit.broadcastMessage(prefix
												+ " SpawnLocation has been reset for world "
												+ s
												+ ". Please reset the spawnlocation by using /LobbyAPI changeSpawn.");
									}
									getConfig().set(
											"Worlds." + name + ".spawnLoc.w", s);
									/**
									 * Only here to make sure that the loc
									 * variable will **always** be removed in
									 * case a user updates
									 */
									getConfig().set("Worlds." + name + ".loc",
											null);
									saveConfig();
								}
								double x = getConfig().getDouble(
										"Worlds." + name + ".spawnLoc.x");
								double y = getConfig().getDouble(
										"Worlds." + name + ".spawnLoc.y");
								double z = getConfig().getDouble(
										"Worlds." + name + ".spawnLoc.z");
								WeatherState ws = WeatherState
										.getWeatherStateByName(getConfig()
												.getString(
														"Worlds."
																+ name
																+ ".weatherstate"));
								World w = Bukkit
										.getWorld(getConfig().getString(
												"Worlds." + name + ".spawnLoc.w"));
								Location l = new Location(w, x, y, z);
								if (l == null || l.getWorld() == null)
									continue;

								int i = getConfig().getInt(
										"Worlds." + name + ".i");
								String save = ""
										+ getConfig().getString(
												"Worlds." + name + ".save");
								String desc = getConfig().getString(
										"Worlds." + name + ".desc");
								int color = getConfig().getInt(
										"Worlds." + name + ".color");
								GameMode gm = GameMode.SURVIVAL;
								try {
									String s4 = getConfig().getString(
											"Worlds." + name + ".gamemode");
									for (GameMode g : GameMode.values())
										if (g.name().equals(s4))
											gm = g;

								} catch (Exception e) {
									e.printStackTrace();
								}

								if (LobbyAPI.getLobbyWorld(s) != null)
									LobbyAPI.unregisterWorld(getServer()
											.getWorld(s));

								LobbyWorld lw = LobbyAPI
										.registerWorldFromConfig(w, l, save,
												desc, color, i,
												GameMode.SURVIVAL);
								for (String jC : getConfig().getStringList(
										"Worlds." + lw.getSaveName()
												+ ".joincommands"))
									lw.addCommand(jC);

								lw.setWeatherState(ws);
								lw.setGameMode(gm);
								
								lw.setDisplayName(displayname);
								
								if (ConfigHandler.containsWorldVariable(lw,
										ConfigKeys.CanUsePortals.s))
									lw.setPortal(ConfigHandler
											.getWorldVariableBoolean(lw,
													ConfigKeys.CanUsePortals.s));

								if (ConfigHandler.containsWorldVariable(lw,
										ConfigKeys.DefaultItems.s))
									lw.setSpawnItems((List<ItemStack>) ConfigHandler
											.getWorldVariableObject(lw,
													ConfigKeys.DefaultItems.s));

								if (ConfigHandler.containsWorldVariable(lw,
										ConfigKeys.DisableHealthAndHunger.s))
									lw.setDisableHungerAndHealth(ConfigHandler
											.getWorldVariableBoolean(
													lw,
													ConfigKeys.DisableHealthAndHunger.s));
								if (ConfigHandler.containsWorldVariable(lw,
										ConfigKeys.DisableVoid.s))
									lw.setVoidDisable(ConfigHandler
											.getWorldVariableBoolean(lw,
													ConfigKeys.DisableVoid.s));
								if (ConfigHandler.containsWorldVariable(lw,
										ConfigKeys.ShouldBeSavingLocation.s))
									lw.setWorldShouldSavePlayerLocation(ConfigHandler
											.getWorldVariableBoolean(
													lw,
													ConfigKeys.ShouldBeSavingLocation.s));
								if (ConfigHandler.containsWorldVariable(lw,
										ConfigKeys.SavingLocation.s))
									lw.setLastLocation((HashMap<UUID, Location>) ConfigHandler
											.getWorldVariableObject(lw,
													ConfigKeys.SavingLocation.s));
								

								if (ConfigHandler.containsWorldVariable(lw,
										ConfigKeys.isHidden.s))
									lw.setHidden(ConfigHandler.getWorldVariableBoolean(lw,
													ConfigKeys.isHidden.s));
								
								if (getConfig().contains(
										"Worlds." + name + ".material"))
									lw.setMaterial(Material
											.matchMaterial(getConfig()
													.getString(
															"Worlds."
																	+ name
																	+ ".material")));

								Bukkit.getConsoleSender().sendMessage(
										prefix + " Added world '" + s + "'");
								if (getConfig().contains(
										"Worlds." + name + ".isMainLobby")
										&& getConfig()
												.getBoolean(
														"Worlds."
																+ name
																+ ".isMainLobby")) {
									LobbyWorld.setMainLobby(LobbyAPI
											.getLobbyWorld(getServer()
													.getWorld(s)));
									Bukkit.getConsoleSender()
											.sendMessage(
													prefix
															+ ChatColor.GOLD
															+ "'"
															+ s
															+ "' is now the main lobby.");
								}
							}
						}
					}
				}, 2L);
	}

	public void loadLocalServers() {
		getServer().getScheduler().scheduleSyncDelayedTask(this,
				new Runnable() {
					@SuppressWarnings("deprecation")
					public void run() {
						if (getConfig().contains("Server")) {
							for (String fi : getConfig()
									.getConfigurationSection("Server").getKeys(
											false)) {
								String s = getConfig().getString(
										"Server." + fi + ".name");
								int i = getConfig().getInt(
										"Server." + fi + ".i");
								int color = getConfig().getInt(
										"Server." + fi + ".color");
								LobbyAPI.unregisterBungeeServer(s);
								LobbyAPI.registerBungeeServerFromConfig(s, i,
										color);
								Bukkit.getConsoleSender().sendMessage(
										prefix + " added server '" + s + "'");
							}
						}
					}
				}, 2L);
	}

	public ItemStack getWorldSelector() {
		return worldSelector;
	}

	public void setWorldSelector(ItemStack i) {
		worldSelector = i;
	}

	public HashMap<String, World> getLastWorld() {
		return lastWorld;
	}

	public Set<LobbyWorld> getWorlds() {
		return worlds;
	}

	public Set<LobbyServer> getBWorlds() {
		return bungeeServers;
	}
}
