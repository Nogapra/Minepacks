/*
 *   Copyright (C) 2014-2019 GeorgH93
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package at.pcgamingfreaks.MinePacks;

import at.pcgamingfreaks.Bukkit.Utils;
import at.pcgamingfreaks.ConsoleColor;
import at.pcgamingfreaks.MinePacks.Database.Config;
import at.pcgamingfreaks.MinePacks.Database.Database;
import at.pcgamingfreaks.MinePacks.Database.Language;
import at.pcgamingfreaks.MinePacks.Updater.UpdateResult;
import at.pcgamingfreaks.MinePacks.Updater.Updater;
import at.pcgamingfreaks.StringUtils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.logging.Logger;

public class MinePacks extends JavaPlugin
{
	private static MinePacks instance = null;

	public Logger log;
	public Config config;
	public Language lang;
	public Database DB;

	public HashMap<Player, Long> cooldowns = new HashMap<>();

	public static String backpackTitleOther, backpackTitle;
	public String messageInvalidBackpack;

	private static int maxSize;

	public static MinePacks getInstance()
	{
		return instance;
	}

	@Override
	public void onEnable()
	{
		log = getLogger();
		Utils.warnOnJava_1_7(log);
		instance = this;
		//region Check compatibility with used minecraft version
		String name = Bukkit.getServer().getClass().getPackage().getName();
		String[] version = name.substring(name.lastIndexOf('.') + 2).split("_");
		if((version[0].equals("1") && Integer.valueOf(version[1]) > 14) || Integer.valueOf(version[0]) > 1)
		{
			MinePacks.getInstance().warnOnVersionIncompatibility(version[0] + "." + version[1]);
			this.setEnabled(false);
			return;
		}
		//endregion
		config = new Config(this);
		lang = new Language(this);
		lang.load(config.getLanguage(), config.getLanguageUpdateMode());
		DB = Database.getDatabase(this);
		getCommand("backpack").setExecutor(new OnCommand(this));
		getServer().getPluginManager().registerEvents(new EventListener(this), this);

		if(config.getFullInvCollect())
		{
			(new ItemsCollector(this)).runTaskTimer(this, config.getFullInvCheckInterval(), config.getFullInvCheckInterval());
		}

		maxSize = config.getBackpackMaxSize();
		backpackTitleOther = config.getBPTitleOther();
		backpackTitle = StringUtils.limitLength(config.getBPTitle(), 32);
		messageInvalidBackpack = lang.getTranslated("Ingame.InvalidBackpack");
		getServer().getServicesManager().register(MinePacks.class, this, this, ServicePriority.Normal);

		if(config.getAutoUpdate()) // Lets check for updates
		{
			log.info("Checking for updates ...");
			Updater updater = new Updater(this, this.getFile(), true, 83445); // Create a new updater with dev.bukkit.org as update provider
			updater.update(new at.pcgamingfreaks.MinePacks.Updater.Updater.UpdaterResponse() {
				@Override
				public void onDone(UpdateResult updateResult)
				{
					if(updateResult == UpdateResult.UPDATE_AVAILABLE_V2)
					{
						new MinepacksV2IsAvailable(MinePacks.this);
					}
				}
			}); // Starts the update, if there is a new update available it will download while we close the rest
		}

		log.info(lang.get("Console.Enabled"));
	}

	@Override
	public void onDisable()
	{
		getCommand("backpack").setExecutor(new CommandExecutor() {
			@Override
			public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings)
			{
				return false;
			}
		});
		Updater updater = null;
		if(config != null && config.getAutoUpdate()) // Lets check for updates
		{
			log.info("Checking for updates ...");
			updater = new Updater(this, this.getFile(), true, 83445); // Create a new updater with dev.bukkit.org as update provider
			updater.update(); // Starts the update, if there is a new update available it will download while we close the rest
		}
		if(DB != null) DB.close(); // Close the DB connection, we won't need them any longer
		HandlerList.unregisterAll(this); // Stop the listener, they are no longer needed
		if(updater != null) updater.waitForAsyncOperation(); // The update can download while we kill the listeners and close the DB connections
		getServer().getScheduler().cancelTasks(this); // Kill all still running tasks
		instance = null;
		if(lang != null) log.info(lang.get("Console.Disabled"));
	}

	public void warnOnVersionIncompatibility(String version)
	{
		log.warning(ConsoleColor.RED + "################################" + ConsoleColor.RESET);
		log.warning(ConsoleColor.RED + String.format("Your minecraft version (MC %1$s) is currently not compatible with this plugins version (%2$s). Please check for updates!", version, getDescription().getVersion()) + ConsoleColor.RESET);
		log.warning(ConsoleColor.RED + "################################" + ConsoleColor.RESET);
		Utils.blockThread(5);
	}

	public void openBackpack(final Player opener, OfflinePlayer owner, final boolean editable)
	{
		if(owner == null)
		{
			return;
		}
		DB.getBackpack(owner, new Database.Callback<Backpack>()
		{
			@Override
			public void onResult(Backpack backpack)
			{
				openBackpack(opener, backpack, editable);
			}

			@Override
			public void onFail() {}
		});
	}

	public void openBackpack(Player opener, Backpack backpack, boolean editable)
	{
		if(backpack == null)
		{
			opener.sendMessage(messageInvalidBackpack);
			return;
		}
		backpack.open(opener, editable);
	}

	public static int getBackpackPermSize(Player player)
	{
		for(int i = maxSize; i > 1; i--)
		{
			if(player.hasPermission("backpack.size." + i))
			{
				return i * 9;
			}
		}
		return 9;
	}
}