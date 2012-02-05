/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jserver.gameserver.model.entity.events;

/**
 * 
 * @author HanWik
 * 
 */

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.concurrent.CopyOnWriteArrayList;

import java.util.logging.Logger;

import com.l2jserver.Config;
import com.l2jserver.L2DatabaseFactory;
import com.l2jserver.gameserver.Announcements;
import com.l2jserver.gameserver.ThreadPoolManager;
import com.l2jserver.gameserver.datatables.ItemTable;
import com.l2jserver.gameserver.datatables.NpcTable;
import com.l2jserver.gameserver.datatables.SkillTable;
import com.l2jserver.gameserver.datatables.SpawnTable;
import com.l2jserver.gameserver.model.L2Effect;
import com.l2jserver.gameserver.model.L2Party;
import com.l2jserver.gameserver.model.L2Skill;
import com.l2jserver.gameserver.model.L2Spawn;
import com.l2jserver.gameserver.model.actor.L2Summon;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PetInstance;
import com.l2jserver.gameserver.network.serverpackets.ActionFailed;
import com.l2jserver.gameserver.network.serverpackets.CreatureSay;
import com.l2jserver.gameserver.network.serverpackets.MagicSkillUse;
import com.l2jserver.gameserver.network.serverpackets.NpcHtmlMessage;
import com.l2jserver.gameserver.templates.chars.L2NpcTemplate;
import com.l2jserver.util.StringUtil;
import com.l2jserver.util.Rnd;

public class Raid
{
	private final static Logger			_log					= Logger.getLogger(Raid.class.getName());
	public static String				_eventName				= "";
	public static String				_eventDesc				= "";
	public static String				_joiningLocationName	= "";
	public static CopyOnWriteArrayList<String>		_savePlayers 			= new CopyOnWriteArrayList<String>();
	public static CopyOnWriteArrayList<L2PcInstance>	_players				= new CopyOnWriteArrayList<L2PcInstance>();
	public static boolean				_joining				= false;
	public static boolean				_teleport				= false;
	public static boolean				_started				= false;
	public static boolean				_sitForced				= false;
	public static L2Spawn				_npcSpawn;
	public static L2Spawn				_bossSpawn;
	public static int					_npcId					= 0;
	public static int					_npcX					= 0;
	public static int					_npcY					= 0;
	public static int					_npcZ					= 0;
	public static int					_npcHeading				= 0;
	public static int					_bossId					= 0;
	public static int					_bossX					= 0;
	public static int					_bossY					= 0;
	public static int					_bossZ					= 0;
	public static int					_bossHeading			= 0;
	public static int					_rewardId				= 0;
	public static int					_rewardAmount			= 0;
	public static int					_startX					= 0;
	public static int					_startY					= 0;
	public static int					_startZ					= 0;
	public static int					_minlvl					= 0;
	public static int					_maxlvl					= 0;
	public static int					_joinTime				= 0;
	public static int					_eventTime				= 0;
	public static int					_minPlayers				= 0;
	public static int					_maxPlayers				= 0;
	public static int					_playersWon				= 0;
	public static int	 				_radius 				= 0;

	public static void AnnounceToPlayers(Boolean toall, String announce)
	{
		if (toall)
			Announcements.getInstance().announceToAll(announce);
		else
		{
			CreatureSay cs = new CreatureSay(0, 2, "", "Announcements: " + announce);
			if (_players != null && !_players.isEmpty())
			{
				for (L2PcInstance player : _players)
				{
					if (player != null && player.isOnline())
						player.sendPacket(cs);
				}
			}
		}
	}

	public static void kickPlayerFromRaid(L2PcInstance playerToKick)
	{
		if (playerToKick == null)
			return;
		
		if (_joining)
		{
			_players.remove(playerToKick);
			playerToKick.inEventRaid = false;
		}
		if (_started || _teleport)
		{
			playerToKick.inEventRaid = false;
			removePlayer(playerToKick);
			if (playerToKick.isOnline())
			{
				playerToKick.getAppearance().setNameColor(playerToKick._originalNameColorRaid);
				playerToKick.getAppearance().setTitleColor(playerToKick._originalTitleColorRaid);
				playerToKick.setKarma(playerToKick._originalKarmaRaid);
				playerToKick.broadcastUserInfo();
				playerToKick.sendMessage("You have been kicked from the Raid.");
				playerToKick.teleToLocation(_npcX, _npcY, _npcZ, false);
			}
		}
	}

	public static void setNpcPos(L2PcInstance activeChar)
	{
		_npcX = activeChar.getX();
		_npcY = activeChar.getY();
		_npcZ = activeChar.getZ();
		_npcHeading = activeChar.getHeading();
	}

	public static void setNpcPos(int x, int y, int z)
	{
		_npcX = x;
		_npcY = y;
		_npcZ = z;
	}
	public static void setBossPos(L2PcInstance activeChar)
	{
		_bossX = activeChar.getX();
		_bossY = activeChar.getY();
		_bossZ = activeChar.getZ();
		_bossHeading = activeChar.getHeading();
	}

	public static void setBossPos(int x, int y, int z)
	{
		_bossX = x;
		_bossY = y;
		_bossZ = z;
	}

	public static boolean checkMaxLevel(int maxlvl)
	{
		return _minlvl < maxlvl;
	}

	public static boolean checkMinLevel(int minlvl)
	{
		return _maxlvl > minlvl;
	}

	/** returns true if participated players is higher or equal then minimum needed players */
	public static boolean checkMinPlayers(int players)
	{
		return _minPlayers <= players;
	}

	/** returns true if max players is higher or equal then participated players */
	public static boolean checkMaxPlayers(int players)
	{
		return _maxPlayers > players;
	}

	public static void setTeamPos(L2PcInstance activeChar)
	{
		_startX = activeChar.getX();
		_startY = activeChar.getY();
		_startZ = activeChar.getZ();
	}

	public static void setTeamPos(int x, int y, int z)
	{
		_startX = x;
		_startY = y;
		_startZ = z;
	}


	public static boolean checkTeamOk()
	{
		return !(_started || _teleport || _joining);
	}

	public static void startJoin(L2PcInstance activeChar)
	{
		if (!startJoinOk())
		{
			activeChar.sendMessage("Event not setted propertly.");
			if (Config.DEBUG)
				_log.fine("Raid Engine[startJoin(" + activeChar.getName() + ")]: startJoinOk() = false");
			return;
		}

		_joining = true;
		spawnEventNpc(activeChar);
		spawnEventBoss(activeChar);
		AnnounceToPlayers(true, _eventName + "!");
		if (Config.RAID_ANNOUNCE_REWARD)
			AnnounceToPlayers(true, "Reward: " + _rewardAmount + " " + ItemTable.getInstance().getTemplate(_rewardId).getName());
		AnnounceToPlayers(true, "Recruiting levels " + _minlvl + " to " + _maxlvl);
		AnnounceToPlayers(true, "Joinable in " + _joiningLocationName + "!");
	}

	public static void startJoin()
	{
		if (!startJoinOk())
		{
			_log.warning("Event not setted propertly.");
			if (Config.DEBUG)
				_log.fine("Raid Engine[startJoin(startJoinOk() = false");
			return;
		}

		_joining = true;
		spawnEventNpc();
		spawnEventBoss();
		AnnounceToPlayers(true, _eventName + "!");
		if (Config.RAID_ANNOUNCE_REWARD)
			AnnounceToPlayers(true, "Reward: " + _rewardAmount + " " + ItemTable.getInstance().getTemplate(_rewardId).getName());
		AnnounceToPlayers(true, "Recruiting levels " + _minlvl + " to " + _maxlvl);
		AnnounceToPlayers(true, "Joinable in " + _joiningLocationName + "!");
	}

	public static boolean startAutoJoin()
	{
		if (!startJoinOk())
		{
			if (Config.DEBUG)
				_log.fine("Raid Engine[startJoin]: startJoinOk() = false");
			return false;
		}
		_joining = true;
		spawnEventNpc();
		spawnEventBoss();
		AnnounceToPlayers(true, _eventName + "!");
		if (Config.RAID_ANNOUNCE_REWARD)
			AnnounceToPlayers(true, "Reward: " + _rewardAmount + " " + ItemTable.getInstance().getTemplate(_rewardId).getName());
		AnnounceToPlayers(true, "Recruiting levels " + _minlvl + " to " + _maxlvl);
		AnnounceToPlayers(true, "Joinable in " + _joiningLocationName + "!");
		return true;
	}

	public static boolean startJoinOk()
	{
		return !(_started || _teleport || _joining
				|| _npcId == 0 || _npcX == 0 || _npcY == 0 || _npcZ == 0
				|| _bossId == 0 || _bossX == 0 || _bossY == 0 || _bossZ == 0 || _rewardId == 0 || _rewardAmount == 0);

	}

	private static void spawnEventNpc(L2PcInstance activeChar)
	{
		L2NpcTemplate tmpl = NpcTable.getInstance().getTemplate(_npcId);

		try
		{
			_npcSpawn = new L2Spawn(tmpl);

			_npcSpawn.setLocx(_npcX);
			_npcSpawn.setLocy(_npcY);
			_npcSpawn.setLocz(_npcZ);
			_npcSpawn.setAmount(1);
			_npcSpawn.setHeading(_npcHeading);
			_npcSpawn.setRespawnDelay(1);

			SpawnTable.getInstance().addNewSpawn(_npcSpawn, false);

			_npcSpawn.init();
			_npcSpawn.getLastSpawn().setTitle(_eventName);
			_npcSpawn.getLastSpawn().isEventMobRaid = true;
			_npcSpawn.getLastSpawn().isAggressive();
			_npcSpawn.getLastSpawn().decayMe();
			_npcSpawn.getLastSpawn().spawnMe(_npcSpawn.getLastSpawn().getX(), _npcSpawn.getLastSpawn().getY(), _npcSpawn.getLastSpawn().getZ());
			_npcSpawn.getLastSpawn().broadcastPacket(new MagicSkillUse(_npcSpawn.getLastSpawn(), _npcSpawn.getLastSpawn(), 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			_log.warning("Raid Engine[spawnEventNpc(" + activeChar.getName() + ")]: exception: " + e.getMessage());
		}
	}

	private static void spawnEventNpc()
	{
		L2NpcTemplate tmpl = NpcTable.getInstance().getTemplate(_npcId);

		try
		{
			_npcSpawn = new L2Spawn(tmpl);

			_npcSpawn.setLocx(_npcX);
			_npcSpawn.setLocy(_npcY);
			_npcSpawn.setLocz(_npcZ);
			_npcSpawn.setAmount(1);
			_npcSpawn.setHeading(_npcHeading);
			_npcSpawn.setRespawnDelay(1);

			SpawnTable.getInstance().addNewSpawn(_npcSpawn, false);

			_npcSpawn.init();
			_npcSpawn.getLastSpawn().setTitle(_eventName);
			_npcSpawn.getLastSpawn().isEventMobRaid = true;
			_npcSpawn.getLastSpawn().isAggressive();
			_npcSpawn.getLastSpawn().decayMe();
			_npcSpawn.getLastSpawn().spawnMe(_npcSpawn.getLastSpawn().getX(), _npcSpawn.getLastSpawn().getY(), _npcSpawn.getLastSpawn().getZ());
			_npcSpawn.getLastSpawn().broadcastPacket(new MagicSkillUse(_npcSpawn.getLastSpawn(), _npcSpawn.getLastSpawn(), 1034, 1, 1, 1));	

		}
		catch (Exception e)
		{
			_log.warning("Raid Engine[spawnEventNpc(exception: " + e.getMessage());
		}
	}

	private static void spawnEventBoss(L2PcInstance activeChar)
	{
		L2NpcTemplate tmpl = NpcTable.getInstance().getTemplate(_bossId);

		try
		{
			_bossSpawn = new L2Spawn(tmpl);

			_bossSpawn.setLocx(_bossX);
			_bossSpawn.setLocy(_bossY);
			_bossSpawn.setLocz(_bossZ);
			_bossSpawn.setAmount(1);
			_bossSpawn.setHeading(_bossHeading);
			_bossSpawn.setRespawnDelay(1);

			SpawnTable.getInstance().addNewSpawn(_bossSpawn, false);

			_bossSpawn.init();
			_bossSpawn.getLastSpawn().setTitle(_eventName);
			_bossSpawn.getLastSpawn().isEventMobRaid = true;
			_bossSpawn.getLastSpawn().isAggressive();
			_bossSpawn.getLastSpawn().decayMe();
			_bossSpawn.getLastSpawn().spawnMe(_bossSpawn.getLastSpawn().getX(), _bossSpawn.getLastSpawn().getY(), _bossSpawn.getLastSpawn().getZ());

			_bossSpawn.getLastSpawn().broadcastPacket(new MagicSkillUse(_bossSpawn.getLastSpawn(), _bossSpawn.getLastSpawn(), 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			_log.warning("Raid Engine[spawnEventNpc(" + activeChar.getName() + ")]: exception: " + e.getMessage());
		}
	}
	
	private static void spawnEventBoss()
	{
		L2NpcTemplate tmpl = NpcTable.getInstance().getTemplate(_bossId);

		try
		{
			_bossSpawn = new L2Spawn(tmpl);

			_bossSpawn.setLocx(_bossX);
			_bossSpawn.setLocy(_bossY);
			_bossSpawn.setLocz(_bossZ);
			_bossSpawn.setAmount(1);
			_bossSpawn.setHeading(_bossHeading);
			_bossSpawn.setRespawnDelay(1);

			SpawnTable.getInstance().addNewSpawn(_bossSpawn, false);

			_bossSpawn.init();
			_bossSpawn.getLastSpawn().setTitle(_eventName);
			_bossSpawn.getLastSpawn().isEventMobRaid = true;
			_bossSpawn.getLastSpawn().isAggressive();
			_bossSpawn.getLastSpawn().decayMe();
			_bossSpawn.getLastSpawn().spawnMe(_bossSpawn.getLastSpawn().getX(), _bossSpawn.getLastSpawn().getY(), _bossSpawn.getLastSpawn().getZ());

			_bossSpawn.getLastSpawn().broadcastPacket(new MagicSkillUse(_bossSpawn.getLastSpawn(), _bossSpawn.getLastSpawn(), 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			_log.warning("Raid Engine[spawnEventNpc(exception: " + e.getMessage());
		}
	}

	public static void teleportStart()
	{
		if (!_joining || _started || _teleport)
			return;

		removeOfflinePlayers();
		if (!checkMinPlayers(_players.size()))
		{
			AnnounceToPlayers(true, "Not enough players in event. Min Requested : " + _minPlayers + ", Participating : " + _players.size());
			return;
		}

		_joining = false;
		AnnounceToPlayers(false, _eventName + ": Teleport to starting spot in 10 seconds!");

		setUserData();
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable()
		{
			public void run()
			{
				Raid.sit();

				for (L2PcInstance player : _players)
				{
					if (player != null)
					{
						if (Config.RAID_ON_START_UNSUMMON_PET)
						{
							//Remove Summon's buffs
							if (player.getPet() != null)
							{
								L2Summon summon = player.getPet();
								for (L2Effect e : summon.getAllEffects())
									if (e != null)
										e.exit();

								if (summon instanceof L2PetInstance)
									summon.unSummon(player);
							}
						}

						if (Config.RAID_ON_START_REMOVE_ALL_EFFECTS)
						{
							for (L2Effect e : player.getAllEffects())
							{
								if (e != null)
									e.exit();
							}
						}

						//Remove player from his party
						if (player.getParty() != null)
						{
							L2Party party = player.getParty();
							party.removePartyMember(player);
						}
						player.teleToLocation((_startX + Rnd.get(-(_radius), +(_radius))), (_startY + Rnd.get(-(_radius), +(_radius))), _startZ, false);
					}
				}
			}
		}, 10000);
		_teleport = true;
	}

	public static boolean teleportAutoStart()
	{
		if (!_joining || _started || _teleport)
			return false;

		removeOfflinePlayers();
		if (!checkMinPlayers(_players.size()))
		{
			AnnounceToPlayers(true, "Not enough players for event. Min Requested : " + _minPlayers + ", Participating : " + _players.size());
			return false;
		}

		_joining = false;
		AnnounceToPlayers(false, _eventName + ": Teleport to starting spot in 10 seconds!");

		setUserData();
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable()
		{
			public void run()
			{
				Raid.sit();

				for (L2PcInstance player : _players)
				{
					if (player != null)
					{
						if (Config.RAID_ON_START_UNSUMMON_PET)
						{
							//Remove Summon's buffs
							if (player.getPet() != null)
							{
								L2Summon summon = player.getPet();
								for (L2Effect e : summon.getAllEffects())
									if (e != null)
										e.exit();

								if (summon instanceof L2PetInstance)
									summon.unSummon(player);
							}
						}

						if (Config.RAID_ON_START_REMOVE_ALL_EFFECTS)
						{
							for (L2Effect e : player.getAllEffects())
							{
								if (e != null)
									e.exit();
							}
						}

						//Remove player from his party
						if (player.getParty() != null)
						{
							L2Party party = player.getParty();
							party.removePartyMember(player);
						}

						player.teleToLocation((_startX + Rnd.get(-(_radius), +(_radius))), (_startY + Rnd.get(-(_radius), +(_radius))), _startZ, false);
					}
				}
			}
		}, 10000);
		_teleport = true;
		return true;
	}

	public static void startEvent(L2PcInstance activeChar)
	{
		if (!startEventOk())
		{
			if (Config.DEBUG)
				_log.fine("Raid Engine[startEvent(" + activeChar.getName() + ")]: startEventOk() = false");
			return;
		}
		_teleport = false;
		
		sit();
		AnnounceToPlayers(false, _eventName + " Started. Go kill the raid!");
		_started = true;
	}

	public static void setJoinTime(int time)
	{
		_joinTime = time;
	}

	public static void setEventTime(int time)
	{
		_eventTime = time;
	}

	public static boolean startAutoEvent()
	{
		if (!startEventOk())
		{
			if (Config.DEBUG)
				_log.fine("Raid Engine[startEvent]: startEventOk() = false");
			return false;
		}
		_teleport = false;
		
		sit();
		AnnounceToPlayers(false, _eventName + " Started.");
		_started = true;
		return true;
	}

	public static void autoEvent()
	{
		if (startAutoJoin())
		{
			if (_joinTime > 0)
				waiter(_joinTime * 60 * 1000); // minutes for join event
			else if (_joinTime <= 0)
			{
				abortEvent();
				return;
			}
			if (teleportAutoStart())
			{
				waiter(1 * 30 * 1000); // 30 sec wait time untill start fight after teleported
				if (startAutoEvent())
				{
					waiter(_eventTime * 60 * 1000); // minutes for event time
					finishEvent();
				}
			}
			else if (!teleportAutoStart())
			{
				abortEvent();
			}
		}
	}

	private static void waiter(long interval)
	{
		long startWaiterTime = System.currentTimeMillis();
		int seconds = (int) (interval / 1000);

		while (startWaiterTime + interval > System.currentTimeMillis())
		{
			seconds--; // here because we don't want to see two time announce at the same time

			if (_joining || _started || _teleport)
			{
				switch (seconds)
				{
				case 3600: // 1 hour left
					if (_joining)
					{
						AnnounceToPlayers(true, "Raid Event: Joinable in " + _joiningLocationName + "!");
						AnnounceToPlayers(true, "Raid Event: " + seconds / 60 / 60 + " hour(s) till registration ends!");

					}
					else if (_started)
						AnnounceToPlayers(false, "Raid Event: " + seconds / 60 / 60 + " hour(s) till event ends!");

					break;
				case 1800: // 30 minutes left
				case 900: // 15 minutes left
				case 600: //  10 minutes left 
				case 300: // 5 minutes left
				case 60: // 1 minute left
					if (_joining)
					{
						removeOfflinePlayers();
						AnnounceToPlayers(true, "Raid Event: Joinable in " + _joiningLocationName + "!");
						AnnounceToPlayers(true, "Raid Event: " + seconds / 60 + " minute(s) till registration ends!");
					}
					else if (_started)
						AnnounceToPlayers(false, "Raid Event: " + seconds / 60 + " minute(s) till event ends!");

					break;
				case 30: // 30 seconds left
				case 10: // 10 seconds left
				case 3: // 3 seconds left
				case 2: // 2 seconds left
				case 1: // 1 seconds left
					if (_joining)
						AnnounceToPlayers(true, "Raid Event: " + seconds + " second(s) till registration ends!");
					else if (_teleport)
						AnnounceToPlayers(false, "Raid Event: " + seconds + " seconds(s) till fight starts!");
					else if (_started)
						AnnounceToPlayers(false, "Raid Event: " + seconds + " second(s) till event ends!");

					break;
				}
			}

			long startOneSecondWaiterStartTime = System.currentTimeMillis();

			// only the try catch with Thread.sleep(1000) give bad countdown on high wait times
			while (startOneSecondWaiterStartTime + 1000 > System.currentTimeMillis())
			{
				try
				{
					Thread.sleep(1);
				}
				catch (InterruptedException ie)
				{
				}
			}
		}
	}

	private static boolean startEventOk()
	{
		if (_joining || !_teleport || _started)
			return false;

		if (_players == null || _players.isEmpty() || _players.size() == 0)
			return false;

		return true;
	}

	public static void setUserData()
	{
		for (L2PcInstance player : _players)
		{

			player._originalNameColorRaid = player.getAppearance().getNameColor();
			player._originalTitleColorRaid = player.getAppearance().getTitleColor();
			player.getAppearance().setNameColor(Integer.decode("0x" + "0000ff"));
			player.getAppearance().setTitleColor(Integer.decode("0x" + "0000ff"));
			player.setKarma(0);
			player.setTeam(2);
			player.broadcastUserInfo();
		}
	}

	public static void finishEvent()
	{
		if (!finishEventOk())
		{
			if (Config.DEBUG)
				_log.fine("Raid Engine[finishEvent]: finishEventOk() = false");
			return;
		}
		_started = false;
		unspawnEventNpc();
		unspawnEventBoss();

		if (_playersWon == 0)
			AnnounceToPlayers(true, _eventName + ": The event is over and you have failed to kill the raid.");

		teleportFinish();
	}

	private static boolean finishEventOk()
	{
		return _started;
	}

	public static void rewardTeam()
	{
		for (L2PcInstance player : _players)
		{
			if (player != null && player.isOnline() && player.inEventRaid == true)
			{
				player.addItem("Raid Event:" + _eventName, _rewardId, _rewardAmount, player, true);

				NpcHtmlMessage nhm = new NpcHtmlMessage(5);
				final StringBuilder replyMSG = StringUtil.startAppend(1000, "<html><body>");

				replyMSG.append("<html><body>Your have killed the raid and won the event. Look in your inventory for the reward.</body></html>");

				nhm.setHtml(replyMSG.toString());
				player.sendPacket(nhm);

				// Send a Server->Client ActionFailed to the L2PcInstance in order to avoid that the client wait another packet
				player.sendPacket(ActionFailed.STATIC_PACKET);
			}
		}
	}

	public static void raidKilled()
	{
		_playersWon = 1;
		rewardTeam();

		AnnounceToPlayers(true, _eventName + ": The players have killed the raidboss!");

		finishEvent();
	}

	public static void abortEvent()
	{
		if (!_joining && !_teleport && !_started)
			return;
		_joining = false;
		_teleport = false;
		_started = false;
		unspawnEventNpc();
		unspawnEventBoss();
		teleportFinish();
		AnnounceToPlayers(true, _eventName + ": Match aborted!");
		cleanRaid();
		
	}

	public static void sit()
	{
		if (_sitForced)
			_sitForced = false;
		else
			_sitForced = true;

		for (L2PcInstance player : _players)
		{
			if (player != null)
			{
				if (_sitForced)
				{
					player.stopMove(null, false);
					player.abortAttack();
					player.abortCast();

					if (!player.isSitting())
						player.sitDown();
				}
				else
				{
					if (player.isSitting())
						player.standUp();
				}
				
				// Noblesse all players before starting the event.
				L2Skill skill = SkillTable.getInstance().getInfo(1323,1);
				if (skill != null)
					skill.getEffects(player, player);
			}
		}
	}

	public static void loadData()
	{
		_eventName = new String();
		_eventDesc = new String();
		_joiningLocationName = new String();
		_players = new CopyOnWriteArrayList<L2PcInstance>();
		_savePlayers = new CopyOnWriteArrayList<String>();
		_startX = 0;
		_startY = 0;
		_startZ = 0;
		_joining = false;
		_teleport = false;
		_started = false;
		_sitForced = false;
		_npcId = 0;
		_npcX = 0;
		_npcY = 0;
		_npcZ = 0;
		_npcHeading = 0;
		_bossId = 0;
		_bossX = 0;
		_bossY = 0;
		_bossZ = 0;
		_bossHeading = 0;
		_rewardId = 0;
		_rewardAmount = 0;
		_minlvl = 0;
		_maxlvl = 0;
		_joinTime = 0;
		_eventTime = 0;
		_minPlayers = 0;
		_maxPlayers = 0;
		_radius = 0;

		Connection con = null;
		try
		{
			PreparedStatement statement;
			ResultSet rs;

			con = L2DatabaseFactory.getInstance().getConnection();

			statement = con.prepareStatement("Select * from raid");
			rs = statement.executeQuery();

			while (rs.next())
			{
				_eventName = rs.getString("eventName");
				_eventDesc = rs.getString("eventDesc");
				_joiningLocationName = rs.getString("joiningLocation");
				_minlvl = rs.getInt("minlvl");
				_maxlvl = rs.getInt("maxlvl");
				_npcId = rs.getInt("npcId");
				_npcX = rs.getInt("npcX");
				_npcY = rs.getInt("npcY");
				_npcZ = rs.getInt("npcZ");
				_npcHeading = rs.getInt("npcHeading");
				_bossId = rs.getInt("bossId");
				_bossX = rs.getInt("bossX");
				_bossY = rs.getInt("bossY");
				_bossZ = rs.getInt("bossZ");
				_bossHeading = rs.getInt("bossHeading");
				_rewardId = rs.getInt("rewardId");
				_rewardAmount = rs.getInt("rewardAmount");
				_joinTime = rs.getInt("joinTime");
				_eventTime = rs.getInt("eventTime");
				_minPlayers = rs.getInt("minPlayers");
				_maxPlayers = rs.getInt("maxPlayers");
				_radius = rs.getInt("radius");
				_startX = rs.getInt("startX");
				_startY = rs.getInt("startY");
				_startZ = rs.getInt("startZ");
			}
			statement.close();
		}
		catch (Exception e)
		{
			_log.warning("Exception: Raid.loadData(): " + e.getMessage());
		}
		finally
		{
			try { con.close(); } catch (Exception e) {}
		}
	}

	public static void saveData()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;

			statement = con.prepareStatement("Delete from raid");
			statement.execute();
			statement.close();

			statement = con.prepareStatement("INSERT INTO raid (eventName, eventDesc, joiningLocation, minlvl, maxlvl, npcId, npcX, npcY, npcZ, npcHeading, bossId, bossX, bossY, bossZ, bossHeading, rewardId, rewardAmount, joinTime, eventTime, minPlayers, maxPlayers, radius, startX, startY, startZ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
			statement.setString(1, _eventName);
			statement.setString(2, _eventDesc);
			statement.setString(3, _joiningLocationName);
			statement.setInt(4, _minlvl);
			statement.setInt(5, _maxlvl);
			statement.setInt(6, _npcId);
			statement.setInt(7, _npcX);
			statement.setInt(8, _npcY);
			statement.setInt(9, _npcZ);
			statement.setInt(10, _npcHeading);
			statement.setInt(11, _bossId);
			statement.setInt(12, _bossX);
			statement.setInt(13, _bossY);
			statement.setInt(14, _bossZ);
			statement.setInt(15, _bossHeading);
			statement.setInt(16, _rewardId);
			statement.setInt(17, _rewardAmount);
			statement.setInt(18, _joinTime);
			statement.setInt(19, _eventTime);
			statement.setInt(20, _minPlayers);
			statement.setInt(21, _maxPlayers);
			statement.setInt(22, _radius);
			statement.setInt(23, _startX);
			statement.setInt(24, _startY);
			statement.setInt(25, _startZ);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warning("Exception: Raid.saveData(): " + e.getMessage());
		}
		finally
		{
			try { con.close(); } catch (Exception e) {}
		}
	}

	public static void showEventHtml(L2PcInstance eventPlayer, String objectId)
	{
		try
		{
			NpcHtmlMessage adminReply = new NpcHtmlMessage(5);

			final StringBuilder replyMSG = StringUtil.startAppend(1000, "<html><body>");

			replyMSG.append("Raid Match<br><br><br>");
			replyMSG.append("Current event...<br1>");
			replyMSG.append("Name:&nbsp;<font color=\"00FF00\">" + _eventName + "</font><br1>");
			replyMSG.append("Description:&nbsp;<font color=\"00FF00\">" + _eventDesc + "</font><br>");
			if (Config.RAID_ANNOUNCE_REWARD)
				replyMSG.append("Reward: (" + _rewardAmount + ") " + ItemTable.getInstance().getTemplate(_rewardId).getName() + "<br>");

			if (!_started && !_joining)
				replyMSG.append("<center>Wait till the admin/gm start the participation.</center>");
			else if (eventPlayer.isCursedWeaponEquipped() && !Config.RAID_JOIN_CURSED)
			{
				replyMSG.append("<font color=\"FFFF00\">You can't participate in this event with a cursed Weapon.</font><br>");
			}
			else if (!_started && _joining && eventPlayer.getLevel() >= _minlvl && eventPlayer.getLevel() <=_maxlvl)
			{
				if (_players.contains(eventPlayer))
				{
					replyMSG.append("You are already participating<br><br>");

					replyMSG.append("<table border=\"0\"><tr>");
					replyMSG.append("<td width=\"60\"><center><button value=\"Remove\" action=\"bypass -h npc_" + objectId + "_raid_player_leave\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></center></td>");
					replyMSG.append("<td width=\"120\">your participation!</td>");
					replyMSG.append("</tr></table>");
				}
				else
				{
					replyMSG.append("You want to participate in the event?<br><br>");
					replyMSG.append("<td width=\"200\">Your level : <font color=\"00FF00\">" + eventPlayer.getLevel() + "</font></td><br>");
					replyMSG.append("<td width=\"200\">Min level : <font color=\"00FF00\">" + _minlvl + "</font></td><br>");
					replyMSG.append("<td width=\"200\">Max level : <font color=\"00FF00\">" + _maxlvl + "</font></td><br><br>");

					replyMSG.append("<center><table border=\"0\">");

						replyMSG
								.append("<tr><td width=\"100\">(" + _players.size() + " joined)</td>");

					replyMSG.append("<td width=\"60\"><button value=\"Join\" action=\"bypass -h npc_" + objectId + "_raid_player_join \" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");

					replyMSG.append("</table></center>");
				}
			}
			else if (_started && !_joining)
				replyMSG.append("<center>Raid match is in progress.</center>");
			else if (eventPlayer.getLevel() < _minlvl || eventPlayer.getLevel() > _maxlvl)
			{
				replyMSG.append("Your level : <font color=\"00FF00\">" + eventPlayer.getLevel() + "</font><br>");
				replyMSG.append("Min level : <font color=\"00FF00\">" + _minlvl + "</font><br>");
				replyMSG.append("Max level : <font color=\"00FF00\">" + _maxlvl + "</font><br><br>");
				replyMSG.append("<font color=\"FFFF00\">You can't participate in this event.</font><br>");
			}
			// Show how many players joined & how many are still needed to join
			replyMSG.append("<br>There are " + _players.size() + " player(s) participating in this event.<br>");
			if (_joining)
			{
				if (_players.size() < _minPlayers)
				{
					int playersNeeded = _minPlayers - _players.size();
					replyMSG.append("The event will not start unless " + playersNeeded + " more player(s) joins!");
				}
			}
			replyMSG.append("</body></html>");
			adminReply.setHtml(replyMSG.toString());
			eventPlayer.sendPacket(adminReply);

			// Send a Server->Client ActionFailed to the L2PcInstance in order to avoid that the client wait another packet
			eventPlayer.sendPacket(ActionFailed.STATIC_PACKET);
		}
		catch (Exception e)
		{
			_log.warning("Raid Engine[showEventHtlm(" + eventPlayer.getName() + ", " + objectId + ")]: exception" + e.getMessage());
		}
	}

	public static void addPlayer(L2PcInstance player)
	{
		if (!addPlayerOk(player))
			return;
		
		_players.add(player);
		player.inEventRaid = true;
		_savePlayers.add(player.getName());
	}


	public static void removeOfflinePlayers()
	{
		try
		{
			if (_players == null)
				return;
			else if (_players.isEmpty())
				return;
			else if (_players.size() > 0)
			{
				for (L2PcInstance player : _players)
				{
					if (player == null)
						_players.remove(player);					
					else if (!player.isOnline() || player.isInJail() || player.isDead())
						removePlayer(player);
					if (_players.size() == 0 || _players.isEmpty())
						break;
				}
			}
		}
		catch (Exception e)
		{
			_log.warning("Raid Engine exception: " + e.getMessage());
			return;
		}
	}


	public static boolean addPlayerOk(L2PcInstance eventPlayer)
	{
		try
		{
			if (eventPlayer.inEventRaid)
			{
				eventPlayer.sendMessage("You are already participating in the event!");
				return false;
			}
			if (eventPlayer.inEventCTF || eventPlayer.inEventDM || eventPlayer.inEventTvT)
			{
				eventPlayer.sendMessage("You are already participating in another event!");
				return false;
			}
			if (eventPlayer.isInOlympiadMode())
			{
				eventPlayer.sendMessage("You cant join if you are registered in olympiads"); 
				return false;
			}
			for (L2PcInstance player : _players)
			{
				if (player.getObjectId() == eventPlayer.getObjectId())
				{
					eventPlayer.sendMessage("You are already participating in the event!");
					return false;
				}
				else if (player.getName() == eventPlayer.getName())
				{
					eventPlayer.sendMessage("You are already participating in the event!");
					return false;
				}
			}
			if (_players.contains(eventPlayer))
			{
				eventPlayer.sendMessage("You are already participating in the event!");
				return false;
			}
			if (CTF._savePlayers.contains(eventPlayer.getName()) || TvT._savePlayers.contains(eventPlayer.getName()))
			{
				eventPlayer.sendMessage("You are already participating in another event!");
				return false;
			}
			if (eventPlayer.isInOlympiadMode())
			{
				eventPlayer.sendMessage("You cant join if you are registered in olympiads"); 
				return false;
			}
		}
		catch (Exception e)
		{
			_log.warning("Raid Engine exception: " + e.getMessage());
		}

		if (_players.size() < _maxPlayers)
			return true;

		eventPlayer.sendMessage("Too many players registered");
		return false;
	}

	public static synchronized void addDisconnectedPlayer(L2PcInstance player)
	{
		if ((_teleport || _started))
		{
			if (Config.RAID_ON_START_REMOVE_ALL_EFFECTS)
			{
				for (L2Effect e : player.getAllEffects())
				{
					if (e != null)
						e.exit();
				}
			}

			for (L2PcInstance p : _players)
			{
				if (p == null)
				{
					continue;
				}
				//check by name incase player got new objectId
				else if (p.getName().equals(player.getName()))
				{
					player._originalNameColorRaid = player.getAppearance().getNameColor();
					player._originalTitleColorRaid = player.getAppearance().getTitleColor();
					player._originalKarmaRaid = player.getKarma();
					player.inEventRaid = true;
					_players.remove(p); //removing old object id from CopyOnWriteArrayList
					_players.add(player); //adding new objectId to CopyOnWriteArrayList
					break;
				}
			}
			player.getAppearance().setNameColor(Integer.decode("0x" + "0099ff"));
			player.getAppearance().setTitleColor(Integer.decode("0x" + "0099ff"));
			player.setKarma(0);
			player.setTeam(2);
			player.broadcastUserInfo();
			player.teleToLocation((_startX + Rnd.get(-(_radius), +(_radius))), (_startY + Rnd.get(-(_radius), +(_radius))), _startZ, false);
		}
	}

	public static void removePlayer(L2PcInstance player)
	{
		if (player.inEventRaid)
		{
			if (!_joining)
			{
				player.getAppearance().setNameColor(player._originalNameColorRaid);
				player.getAppearance().setTitleColor(player._originalTitleColorRaid);
				player.setKarma(player._originalKarmaRaid);
				player.setTeam(0);
				player.broadcastUserInfo();
			}
			player.inEventRaid = false;
			_players.remove(player);
		}
	}

	public static void cleanRaid()
	{
		_log.warning("Raid: Cleaning players.");
		for (L2PcInstance player : _players)
		{
			if (player != null)
			{
				removePlayer(player);
				if (_savePlayers.contains(player.getName()))
					_savePlayers.remove(player.getName());
				player.inEventRaid = false;
			}
		}

		_players = new CopyOnWriteArrayList<L2PcInstance>();
		_savePlayers = new CopyOnWriteArrayList<String>();
		_log.warning("Cleaning Raid done.");
	}

	public static void unspawnEventNpc()
	{
		if (_npcSpawn == null)
			return;

		_npcSpawn.getLastSpawn().deleteMe();
		_npcSpawn.stopRespawn();
		SpawnTable.getInstance().deleteSpawn(_npcSpawn, true);
	}

	public static void unspawnEventBoss()
	{
		if (_bossSpawn == null)
			return;

		_bossSpawn.getLastSpawn().deleteMe();
		_bossSpawn.stopRespawn();
		SpawnTable.getInstance().deleteSpawn(_bossSpawn, true);
	}

	public static void teleportFinish()
	{
		AnnounceToPlayers(false, "Raid Event: Teleport back to participation NPC in 10 seconds!");

		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable()
		{
			public void run()
			{
				for (L2PcInstance player : _players)
				{
					if (player != null)
					{
						if (player.isOnline())
							player.teleToLocation(_npcX, _npcY, _npcZ, false);
						else
						{
							Connection con = null;
							try
							{
								con = L2DatabaseFactory.getInstance().getConnection();

								PreparedStatement statement = con.prepareStatement("UPDATE characters SET x=?, y=?, z=? WHERE char_name=?");
								statement.setInt(1, _npcX);
								statement.setInt(2, _npcY);
								statement.setInt(3, _npcZ);
								statement.setString(4, player.getName());
								statement.execute();
								statement.close();
							}
							catch (SQLException se)
							{
								_log.warning("Raid Engine exception: " + se.getMessage());
							}
							finally
							{
								try { con.close(); } catch (Exception e) {}
							}
						}
					}
				}
				_log.warning("Raid: Teleport done.");
				cleanRaid();
			}
		}, 10000);
	}
}
