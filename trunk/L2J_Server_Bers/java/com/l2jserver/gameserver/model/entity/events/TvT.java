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
import com.l2jserver.gameserver.datatables.SpawnTable;
import com.l2jserver.gameserver.model.L2Effect;
import com.l2jserver.gameserver.model.L2Party;
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

public class TvT
{
	private final static Logger			_log					= Logger.getLogger(TvT.class.getName());
	public static String				_eventName				= "";
	public static String				_eventDesc				= "";
	public static String				_topTeam				= "";
	public static String				_joiningLocationName	= "";
	public static CopyOnWriteArrayList<String>		_teams					= new CopyOnWriteArrayList<String>();
	public static CopyOnWriteArrayList<String>		_savePlayers			= new CopyOnWriteArrayList<String>();
	public static CopyOnWriteArrayList<String>		_savePlayerTeams		= new CopyOnWriteArrayList<String>();

	public static CopyOnWriteArrayList<L2PcInstance>	_players				= new CopyOnWriteArrayList<L2PcInstance>();
	public static CopyOnWriteArrayList<L2PcInstance>	_playersShuffle			= new CopyOnWriteArrayList<L2PcInstance>();
	public static CopyOnWriteArrayList<Integer>		_teamPlayersCount		= new CopyOnWriteArrayList<Integer>();
	public static CopyOnWriteArrayList<Integer>		_teamKillsCount			= new CopyOnWriteArrayList<Integer>();
	public static CopyOnWriteArrayList<Integer>		_teamColors				= new CopyOnWriteArrayList<Integer>();
	public static CopyOnWriteArrayList<Integer>		_teamsX					= new CopyOnWriteArrayList<Integer>();
	public static CopyOnWriteArrayList<Integer>		_teamsY					= new CopyOnWriteArrayList<Integer>();
	public static CopyOnWriteArrayList<Integer>		_teamsZ					= new CopyOnWriteArrayList<Integer>();
	public static boolean				_joining				= false;
	public static boolean				_teleport				= false;
	public static boolean				_started				= false;
	public static boolean				_sitForced				= false;
	public static L2Spawn				_npcSpawn;
	
	public static int					_npcId					= 0;
	public static int					_npcX					= 0;
	public static int					_npcY					= 0;
	public static int					_npcZ					= 0;
	public static int					_npcHeading				= 0;
	public static int					_rewardId				= 0;
	public static int					_rewardAmount			= 0;
	public static int					_giftId					= 0;
	public static int					_giftAmount				= 0;
	public static int					_topKills				= 0;
	public static int					_minlvl					= 0;
	public static int					_maxlvl					= 0;
	public static int					_joinTime				= 0;
	public static int					_eventTime				= 0;
	public static int					_minPlayers				= 0;
	public static int					_maxPlayers				= 0;
	public static int					_playerWon				= 0;

	public static void AnnounceToPlayers(Boolean toall, String announce)
	{
		if (toall)
			Announcements.getInstance().announceToAll(announce);
		else
		{
			CreatureSay cs = new CreatureSay(0, 2, "", "Announcements : " + announce);
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

	public static void kickPlayerFromTvt(L2PcInstance playerToKick)
	{
		if (playerToKick == null)
			return;
		
		if (_joining)
		{
			_playersShuffle.remove(playerToKick);
			_players.remove(playerToKick);
			playerToKick.inEventTvT = false;
			playerToKick._teamNameTvT = "";
			playerToKick._countTvTkills = 0;
		}
		if (_started || _teleport)
		{
			_playersShuffle.remove(playerToKick);
			playerToKick.inEventTvT = false;
			removePlayer(playerToKick);
			if (playerToKick.isOnline())
			{
				playerToKick.getAppearance().setNameColor(playerToKick._originalNameColorTvT);
				playerToKick.getAppearance().setTitleColor(playerToKick._originalTitleColorTvT);
				playerToKick.setKarma(playerToKick._originalKarmaTvT);
				playerToKick.setTitle(playerToKick._originalTitleTvT);
				playerToKick.broadcastUserInfo();
				playerToKick.sendMessage("You have been kicked from the TvT.");
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

	public static void addTeam(String teamName)
	{
		if (!checkTeamOk())
		{
			if (Config.DEBUG)
				_log.fine("TvT Engine[addTeam(" + teamName + ")]: checkTeamOk() = false");
			return;
		}

		if (teamName.equals(" "))
			return;

		_teams.add(teamName);
		_teamPlayersCount.add(0);
		_teamKillsCount.add(0);
		_teamColors.add(0);
		_teamsX.add(0);
		_teamsY.add(0);
		_teamsZ.add(0);
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

	public static void removeTeam(String teamName)
	{
		if (!checkTeamOk() || _teams.isEmpty())
		{
			if (Config.DEBUG)
				_log.fine("TvT Engine[removeTeam(" + teamName + ")]: checkTeamOk() = false");
			return;
		}

		if (teamPlayersCount(teamName) > 0)
		{
			if (Config.DEBUG)
				_log.fine("TvT Engine[removeTeam(" + teamName + ")]: teamPlayersCount(teamName) > 0");
			return;
		}

		int index = _teams.indexOf(teamName);

		if (index == -1)
			return;

		_teamsZ.remove(index);
		_teamsY.remove(index);
		_teamsX.remove(index);
		_teamColors.remove(index);
		_teamKillsCount.remove(index);
		_teamPlayersCount.remove(index);
		_teams.remove(index);
	}

	public static void setTeamPos(String teamName, L2PcInstance activeChar)
	{
		int index = _teams.indexOf(teamName);

		if (index == -1)
			return;

		_teamsX.set(index, activeChar.getX());
		_teamsY.set(index, activeChar.getY());
		_teamsZ.set(index, activeChar.getZ());
	}

	public static void setTeamPos(String teamName, int x, int y, int z)
	{
		int index = _teams.indexOf(teamName);

		if (index == -1)
			return;

		_teamsX.set(index, x);
		_teamsY.set(index, y);
		_teamsZ.set(index, z);
	}

	public static void setTeamColor(String teamName, int color)
	{
		if (!checkTeamOk())
			return;

		int index = _teams.indexOf(teamName);

		if (index == -1)
			return;

		_teamColors.set(index, color);
	}

	public static boolean checkTeamOk()
	{
		return !(_started || _teleport || _joining);
	}

	public static void startJoin(L2PcInstance activeChar)
	{
		if (!startJoinOk())
		{
			activeChar.sendMessage("Event not setted properly.");
			if (Config.DEBUG)
				_log.fine("TvT Engine[startJoin(" + activeChar.getName() + ")]: startJoinOk() = false");
			return;
		}

		_joining = true;
		spawnEventNpc(activeChar);
		AnnounceToPlayers(true, _eventName + " (TvT)");
		if (Config.TVT_ANNOUNCE_REWARD)
			AnnounceToPlayers(true, "Reward: " + _rewardAmount + " " + ItemTable.getInstance().getTemplate(_rewardId).getName());
		AnnounceToPlayers(true, "Recruiting levels " + _minlvl + " to " + _maxlvl + ".");
		AnnounceToPlayers(true, "Joinable in " + _joiningLocationName + ".");
	}

	public static void startJoin()
	{
		if (!startJoinOk())
		{
			_log.warning("Event not setted properly.");
			if (Config.DEBUG)
				_log.fine("TvT Engine[startJoin(startJoinOk() = false");
			return;
		}

		_joining = true;
		spawnEventNpc();
		AnnounceToPlayers(true, _eventName + " (TvT)");
		if (Config.TVT_ANNOUNCE_REWARD)
			AnnounceToPlayers(true, "Reward: " + _rewardAmount + " " + ItemTable.getInstance().getTemplate(_rewardId).getName());
		AnnounceToPlayers(true, "Recruiting levels " + _minlvl + " to " + _maxlvl + ".");
		AnnounceToPlayers(true, "Joinable in " + _joiningLocationName + ".");
	}

	public static boolean startAutoJoin()
	{
		if (!startJoinOk())
		{
			if (Config.DEBUG)
				_log.fine("TvT Engine[startJoin]: startJoinOk() = false");
			return false;
		}

		_joining = true;
		spawnEventNpc();
		AnnounceToPlayers(true, _eventName + " (TvT)");
		if (Config.TVT_ANNOUNCE_REWARD)
			AnnounceToPlayers(true, "Reward: " + _rewardAmount + " " + ItemTable.getInstance().getTemplate(_rewardId).getName());
		AnnounceToPlayers(true, "Recruiting levels " + _minlvl + " to " + _maxlvl + ".");
		AnnounceToPlayers(true, "Joinable in " + _joiningLocationName + ".");
		return true;
	}

	public static boolean startJoinOk()
	{
        return !(_started || _teleport || _joining || _teams.size() < 2 || _eventName.equals("") || _joiningLocationName.equals("") || _eventDesc.equals("")
                || _npcId == 0 || _npcX == 0 || _npcY == 0 || _npcZ == 0 || _rewardId == 0 || _rewardAmount == 0 || _giftId == 0 || _giftAmount == 0 || _teamsX.contains(0) || _teamsY.contains(0)
                || _teamsZ.contains(0));
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
			_npcSpawn.getLastSpawn().getStatus().setCurrentHp(999999999);
			_npcSpawn.getLastSpawn().setTitle(_eventName);
			_npcSpawn.getLastSpawn().isEventMobTvT = true;
			_npcSpawn.getLastSpawn().isAggressive();
			_npcSpawn.getLastSpawn().decayMe();
			_npcSpawn.getLastSpawn().spawnMe(_npcSpawn.getLastSpawn().getX(), _npcSpawn.getLastSpawn().getY(), _npcSpawn.getLastSpawn().getZ());
			_npcSpawn.getLastSpawn().broadcastPacket(new MagicSkillUse(_npcSpawn.getLastSpawn(), _npcSpawn.getLastSpawn(), 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			_log.warning("TvT Engine[spawnEventNpc(" + activeChar.getName() + ")]: exception: " + e.getMessage());
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
			_npcSpawn.getLastSpawn().getStatus().setCurrentHp(999999999);
			_npcSpawn.getLastSpawn().setTitle(_eventName);
			_npcSpawn.getLastSpawn().isEventMobTvT = true;
			_npcSpawn.getLastSpawn().isAggressive();
			_npcSpawn.getLastSpawn().decayMe();
			_npcSpawn.getLastSpawn().spawnMe(_npcSpawn.getLastSpawn().getX(), _npcSpawn.getLastSpawn().getY(), _npcSpawn.getLastSpawn().getZ());
			_npcSpawn.getLastSpawn().broadcastPacket(new MagicSkillUse(_npcSpawn.getLastSpawn(), _npcSpawn.getLastSpawn(), 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			_log.warning("TvT Engine[spawnEventNpc(exception: " + e.getMessage());
		}
	}

	public static void teleportStart()
	{
		if (!_joining || _started || _teleport)
			return;

		if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE") && checkMinPlayers(_playersShuffle.size()))
		{
			removeOfflinePlayers();
			shuffleTeams();
		}
		else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE") && !checkMinPlayers(_playersShuffle.size()))
		{
			AnnounceToPlayers(true, "Not enough players for event. Min Requested : " + _minPlayers + ", Participating : " + _playersShuffle.size());
			return;
		}

		_joining = false;
		AnnounceToPlayers(false, "Teleport to team spot in 10 seconds!");

		setUserData();
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable()
		{
			public void run()
			{
				TvT.sit();

				for (L2PcInstance player : _players)
				{
					if (player != null)
					{
						if (Config.TVT_ON_START_UNSUMMON_PET)
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

						//Remove player from his party
						if (player.getParty() != null)
						{
							L2Party party = player.getParty();
							party.removePartyMember(player);
						}
						//player.getAppearance().setVisibleTitle("Kills: " + player._countTvTkills);
						player.teleToLocation(_teamsX.get(_teams.indexOf(player._teamNameTvT)), _teamsY.get(_teams.indexOf(player._teamNameTvT)), _teamsZ
								.get(_teams.indexOf(player._teamNameTvT)));
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

		if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE") && checkMinPlayers(_playersShuffle.size()))
		{
			removeOfflinePlayers();
			shuffleTeams();
		}
		else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE") && !checkMinPlayers(_playersShuffle.size()))
		{
			AnnounceToPlayers(true, "Not enough players for event. Min Requested : " + _minPlayers + ", Participating : " + _playersShuffle.size());
			return false;
		}

		_joining = false;
		AnnounceToPlayers(false, "Teleport to team spot in 10 seconds!");

		setUserData();
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable()
		{
			public void run()
			{
				TvT.sit();
				for (L2PcInstance player : _players)
				{
					if (player != null)
					{
						if (Config.TVT_ON_START_UNSUMMON_PET)
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

						//Remove player from his party
						if (player.getParty() != null)
						{
							L2Party party = player.getParty();
							party.removePartyMember(player);
						}
						//player.getAppearance().setVisibleTitle("Kills: " + player._countTvTkills);
						player.teleToLocation(_teamsX.get(_teams.indexOf(player._teamNameTvT)), _teamsY.get(_teams.indexOf(player._teamNameTvT)), _teamsZ
								.get(_teams.indexOf(player._teamNameTvT)));
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
			return;
		
		_teleport = false;
			
		for (L2PcInstance player : _players)
		if (Config.TVT_ON_START_REMOVE_ALL_EFFECTS)
		{
			for (L2Effect e : player.getAllEffects())
			{
				if (e != null)
				e.exit();
			}
		}

		sit();
		AnnounceToPlayers(false, "Go kill your enemies!");
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
				_log.fine("TvT Engine[startEvent]: startEventOk() = false");
			return false;
		}

		_teleport = false;
			
		for (L2PcInstance player : _players)
		if (Config.TVT_ON_START_REMOVE_ALL_EFFECTS)
		{
			for (L2Effect e : player.getAllEffects())
			{
				if (e != null)
				e.exit();
			}
		}

		sit();
		AnnounceToPlayers(false, "Go kill your enemies!");
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
				waiter(30 * 1000); // 30 sec wait time untill start fight after teleported
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
						AnnounceToPlayers(true, "(TvT Event): Joinable in " + _joiningLocationName + ".");
						AnnounceToPlayers(true, seconds / 60 / 60 + " hours till TvT registration ends.");

					}
					else if (_started)
						AnnounceToPlayers(false, seconds / 60 / 60 + " hours till TvT event ends.");

					break;
				case 1800: // 30 minutes left
				case 900: // 15 minutes left
				case 600: //  10 minutes left 
				case 300: // 5 minutes left
				case 60: // 1 minute left
					if (_joining)
					{
						removeOfflinePlayers();
						AnnounceToPlayers(true, "(TvT Event) Joinable in " + _joiningLocationName + ".");
						AnnounceToPlayers(true, seconds / 60 + " minute(s) till registration ends.");
					}
					
					else if (_started)
						AnnounceToPlayers(true, seconds / 60 + " minute(s) till TvT ends!");
					break;
				case 30: // 30 seconds left
				case 10: // 10 seconds left
				case 3: // 3 seconds left
				case 2: // 2 seconds left
				case 1: // 1 seconds left
					if (_joining)
						AnnounceToPlayers(true, seconds + " second(s) till TvT registration ends!");
					else if (_teleport)
						AnnounceToPlayers(false, seconds + " seconds till fight starts!");
					else if (_started)
						AnnounceToPlayers(false, seconds + " seconds till event ends!");

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

		if (Config.TVT_EVEN_TEAMS.equals("NO") || Config.TVT_EVEN_TEAMS.equals("BALANCE"))
		{
			if (_teamPlayersCount.contains(0))
				return false;
		}
		else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE"))
		{
			CopyOnWriteArrayList<L2PcInstance> playersShuffleTemp = new CopyOnWriteArrayList<L2PcInstance>();
			int loopCount = 0;

			loopCount = _playersShuffle.size();

			for (int i = 0; i < loopCount; i++)
			{
				if (_playersShuffle != null)
					playersShuffleTemp.add(_playersShuffle.get(i));
			}

			_playersShuffle = playersShuffleTemp;
			playersShuffleTemp.clear();

			//  if (_playersShuffle.size() < (_teams.size()*2)){
			//	  return false;
			//  }
		}

		return true;
	}

	public static void shuffleTeams()
	{
		int teamCount = 0, playersCount = 0;

		for (;;)
		{
			if (_playersShuffle.isEmpty())
				break;

			int playerToAddIndex = Rnd.nextInt(_playersShuffle.size());
			L2PcInstance player = null;
			player = _playersShuffle.get(playerToAddIndex);
			player._originalNameColorTvT = player.getAppearance().getNameColor();
			player._originalTitleColorTvT = player.getAppearance().getTitleColor();
			player._originalTitleTvT = player.getTitle();
			player._originalKarmaTvT = player.getKarma();

			_players.add(player);
			_players.get(playersCount)._teamNameTvT = _teams.get(teamCount);
			_savePlayers.add(_players.get(playersCount).getName());
			_savePlayerTeams.add(_teams.get(teamCount));
			playersCount++;

			if (teamCount == _teams.size() - 1)
				teamCount = 0;
			else
				teamCount++;

			_playersShuffle.remove(playerToAddIndex);
		}
	}

	public static void setUserData()
	{
		for (L2PcInstance player : _players)
		{
			player.getAppearance().setNameColor(_teamColors.get(_teams.indexOf(player._teamNameTvT)));
			player.getAppearance().setTitleColor(_teamColors.get(_teams.indexOf(player._teamNameTvT)));
			player.setKarma(0);
			if (_teams.size() >= 2)
				player.setTeam(_teams.indexOf(player._teamNameTvT) + 1);
			player.broadcastUserInfo();
		}
	}

	public static void finishEvent()
	{
		if (!finishEventOk())
		{
			if (Config.DEBUG)
				_log.fine("TvT Engine[finishEvent]: finishEventOk() = false");
			return;
		}

		_started = false;
		unspawnEventNpc();
		processTopTeam();

		if (_topKills == 0)
			AnnounceToPlayers(true, "No team wins the event(nobody killed).");
		else
		{
			AnnounceToPlayers(true, _topTeam + " Team wins with " + _topKills + " kills.");
			rewardTeam(_topTeam);
		}

		if (Config.TVT_ANNOUNCE_TEAM_STATS)
		{
			AnnounceToPlayers(true, "(TvT Event) Team Statistics:");
			for (String team : _teams)
			{
				int _kills = teamKillsCount(team);
				AnnounceToPlayers(true, "Team" + team + " Kills: " + _kills);
			}
		}

		teleportFinish();
	}

	private static boolean finishEventOk()
	{
		return _started;
	}

	public static void processTopTeam()
	{
		for (String team : _teams)
		{
			if (teamKillsCount(team) > _topKills)
			{
				_topTeam = team;
				_topKills = teamKillsCount(team);
			}
		}
	}

	public static void rewardTeam(String teamName)
	{
		for (L2PcInstance player : _players)
		{
			if (player != null && player.isOnline() && player.inEventTvT)
			{
				if (player._teamNameTvT.equals(teamName) && (player._countTvTkills > 0 || Config.TVT_PRICE_NO_KILLS))
				{
					player.addItem("TvT Event: " + _eventName, _rewardId, _rewardAmount, player, true);

					_playerWon = 1;

					NpcHtmlMessage nhm = new NpcHtmlMessage(5);
					final StringBuilder replyMSG = StringUtil.startAppend(1000, "<html><body>");

					replyMSG.append("<html><body>Your team won the event. Look in your inventory for the reward.</body></html>");

					nhm.setHtml(replyMSG.toString());
					player.sendPacket(nhm);

					// Send a Server->Client ActionFailed to the L2PcInstance in order to avoid that the client wait another packet
					player.sendPacket(ActionFailed.STATIC_PACKET);
				}
				if (!player._teamNameTvT.equals(teamName) && (player._countTvTkills > 0 || Config.TVT_PRICE_NO_KILLS))
				{
					player.addItem("TvT Event: " + _eventName, _giftId, _giftAmount, player, true);

					NpcHtmlMessage nhm = new NpcHtmlMessage(5);
					final StringBuilder replyMSG = StringUtil.startAppend(1000, "<html><body>");

					replyMSG.append("<html><body>Your team have lost the event but I will reward you for tring your best.</body></html>");

					nhm.setHtml(replyMSG.toString());
					player.sendPacket(nhm);

					// Send a Server->Client ActionFailed to the L2PcInstance in order to avoid that the client wait another packet
					player.sendPacket(ActionFailed.STATIC_PACKET);
				}
			}
		}
	}

	public static void abortEvent()
	{
		if (!_joining && !_teleport && !_started)
			return;
		if (_joining && !_teleport && !_started)
		{
			unspawnEventNpc();
			cleanTvT();
			_joining = false;
			AnnounceToPlayers(true, "(TvT Event): Match aborted!");
			return;
		}
		_joining = false;
		_teleport = false;
		_started = false;
		unspawnEventNpc();
		AnnounceToPlayers(true, "(TvT Event): Match aborted!");
		teleportFinish();
	}

	public static void sit()
	{
		_sitForced = !_sitForced;

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
			}
		}
	}

	public static void dumpData()
	{
		_log.warning("");
		_log.warning("");

		if (!_joining && !_teleport && !_started)
		{
			_log.warning("<<---------------------------------->>");
			_log.warning(">> TvT Engine infos dump (INACTIVE) <<");
			_log.warning("<<--^----^^-----^----^^------^^----->>");
		}
		else if (_joining && !_teleport && !_started)
		{
			_log.warning("<<--------------------------------->>");
			_log.warning(">> TvT Engine infos dump (JOINING) <<");
			_log.warning("<<--^----^^-----^----^^------^----->>");
		}
		else if (!_joining && _teleport && !_started)
		{
			_log.warning("<<---------------------------------->>");
			_log.warning(">> TvT Engine infos dump (TELEPORT) <<");
			_log.warning("<<--^----^^-----^----^^------^^----->>");
		}
		else if (!_joining && !_teleport && _started)
		{
			_log.warning("<<--------------------------------->>");
			_log.warning(">> TvT Engine infos dump (STARTED) <<");
			_log.warning("<<--^----^^-----^----^^------^----->>");
		}

		_log.warning("Name: " + _eventName);
		_log.warning("Desc: " + _eventDesc);
		_log.warning("Join location: " + _joiningLocationName);
		_log.warning("Min lvl: " + _minlvl);
		_log.warning("Max lvl: " + _maxlvl);
		_log.warning("");
		_log.warning("##########################");
		_log.warning("# _teams(CopyOnWriteArrayList<String>) #");
		_log.warning("##########################");

		for (String team : _teams)
			_log.warning(team + " Kills Done :" + _teamKillsCount.get(_teams.indexOf(team)));

		if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE"))
		{
			_log.warning("");
			_log.warning("#########################################");
			_log.warning("# _playersShuffle(CopyOnWriteArrayList<L2PcInstance>) #");
			_log.warning("#########################################");

			for (L2PcInstance player : _playersShuffle)
			{
				if (player != null)
					_log.warning("Name: " + player.getName());
			}
		}

		_log.warning("");
		_log.warning("##################################");
		_log.warning("# _players(CopyOnWriteArrayList<L2PcInstance>) #");
		_log.warning("##################################");

		for (L2PcInstance player : _players)
		{
			if (player != null)
				_log.warning("Name: " + player.getName() + "   Team: " + player._teamNameTvT + "  Kills Done:" + player._countTvTkills);
		}

		_log.warning("");
		_log.warning("#####################################################################");
		_log.warning("# _savePlayers(CopyOnWriteArrayList<String>) and _savePlayerTeams(CopyOnWriteArrayList<String>) #");
		_log.warning("#####################################################################");

		for (String player : _savePlayers)
			_log.warning("Name: " + player + "	Team: " + _savePlayerTeams.get(_savePlayers.indexOf(player)));

		_log.warning("");
		_log.warning("");
	}

	public static void loadData()
	{
		_eventName = new String();
		_eventDesc = new String();
		_topTeam = new String();
		_joiningLocationName = new String();
		_teams = new CopyOnWriteArrayList<String>();
		_savePlayers = new CopyOnWriteArrayList<String>();
		_savePlayerTeams = new CopyOnWriteArrayList<String>();
		_players = new CopyOnWriteArrayList<L2PcInstance>();
		_playersShuffle = new CopyOnWriteArrayList<L2PcInstance>();
		_teamPlayersCount = new CopyOnWriteArrayList<Integer>();
		_teamKillsCount = new CopyOnWriteArrayList<Integer>();
		_teamColors = new CopyOnWriteArrayList<Integer>();
		_teamsX = new CopyOnWriteArrayList<Integer>();
		_teamsY = new CopyOnWriteArrayList<Integer>();
		_teamsZ = new CopyOnWriteArrayList<Integer>();
		_joining = false;
		_teleport = false;
		_started = false;
		_sitForced = false;
		_npcId = 0;
		_npcX = 0;
		_npcY = 0;
		_npcZ = 0;
		_npcHeading = 0;
		_rewardId = 0;
		_rewardAmount = 0;
		_giftId = 0;
		_giftAmount = 0;
		_topKills = 0;
		_minlvl = 0;
		_maxlvl = 0;
		_joinTime = 0;
		_eventTime = 0;
		_minPlayers = 0;
		_maxPlayers = 0;

		Connection con = null;
		try
		{
			PreparedStatement statement;
			ResultSet rs;

			con = L2DatabaseFactory.getInstance().getConnection();

			statement = con.prepareStatement("Select * from tvt");
			rs = statement.executeQuery();

			int teams = 0;

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
				_rewardId = rs.getInt("rewardId");
				_rewardAmount = rs.getInt("rewardAmount");
				_giftId = rs.getInt("giftId");
				_giftAmount = rs.getInt("giftAmount");
				teams = rs.getInt("teamsCount");
				_joinTime = rs.getInt("joinTime");
				_eventTime = rs.getInt("eventTime");
				_minPlayers = rs.getInt("minPlayers");
				_maxPlayers = rs.getInt("maxPlayers");
			}
			statement.close();

			int index = -1;
			if (teams > 0)
				index = 0;
			while (index < teams && index > -1)
			{
				statement = con.prepareStatement("Select * from tvt_teams where teamId = ?");
				statement.setInt(1, index);
				rs = statement.executeQuery();
				while (rs.next())
				{
					_teams.add(rs.getString("teamName"));
					_teamPlayersCount.add(0);
					_teamKillsCount.add(0);
					_teamColors.add(0);
					_teamsX.add(0);
					_teamsY.add(0);
					_teamsZ.add(0);
					_teamsX.set(index, rs.getInt("teamX"));
					_teamsY.set(index, rs.getInt("teamY"));
					_teamsZ.set(index, rs.getInt("teamZ"));
					_teamColors.set(index, rs.getInt("teamColor"));
				}
				index++;
				statement.close();
			}
		}
		catch (Exception e)
		{
			_log.warning("Exception: TvT.loadData(): " + e.getMessage());
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

			statement = con.prepareStatement("Delete from tvt");
			statement.execute();
			statement.close();

			statement = con
					.prepareStatement("INSERT INTO tvt (eventName, eventDesc, joiningLocation, minlvl, maxlvl, npcId, npcX, npcY, npcZ, npcHeading, rewardId, rewardAmount, giftId, giftAmount, teamsCount, joinTime, eventTime, minPlayers, maxPlayers) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
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
			statement.setInt(11, _rewardId);
			statement.setInt(12, _rewardAmount);
			statement.setInt(13, _giftId);
			statement.setInt(14, _giftAmount);
			statement.setInt(15, _teams.size());
			statement.setInt(16, _joinTime);
			statement.setInt(17, _eventTime);
			statement.setInt(18, _minPlayers);
			statement.setInt(19, _maxPlayers);
			statement.execute();
			statement.close();

			statement = con.prepareStatement("Delete from tvt_teams");
			statement.execute();
			statement.close();

			for (String teamName : _teams)
			{
				int index = _teams.indexOf(teamName);

				if (index == -1)
					return;
				statement = con.prepareStatement("INSERT INTO tvt_teams (teamId ,teamName, teamX, teamY, teamZ, teamColor) VALUES (?, ?, ?, ?, ?, ?)");
				statement.setInt(1, index);
				statement.setString(2, teamName);
				statement.setInt(3, _teamsX.get(index));
				statement.setInt(4, _teamsY.get(index));
				statement.setInt(5, _teamsZ.get(index));
				statement.setInt(6, _teamColors.get(index));
				statement.execute();
				statement.close();
			}
		}
		catch (Exception e)
		{
			_log.warning("Exception: TvT.saveData(): " + e.getMessage());
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
			replyMSG.append("TvT Match<br><br><br>");
			replyMSG.append("Name:&nbsp;<font color=\"00FF00\">" + _eventName + "</font><br1>");
			replyMSG.append("Description:&nbsp;<font color=\"00FF00\">" + _eventDesc + "</font><br>");
			if (Config.TVT_ANNOUNCE_REWARD)
				replyMSG.append("Reward: (" + _rewardAmount + ") " + ItemTable.getInstance().getTemplate(_rewardId).getName() + "<br>");

			if (!_started && !_joining)
				replyMSG.append("<center>Wait till the admin/gm start the participation.</center>");
			else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE") && !checkMaxPlayers(_playersShuffle.size()))
			{
				if (!TvT._started)
				{
					replyMSG.append("<font color=\"FFFF00\">The event has reached its maximum capacity.</font><br>Keep checking, someone may crit and you can steal their spot.");
				}
			}
			else if (eventPlayer.isCursedWeaponEquipped() && !Config.TVT_JOIN_CURSED)
			{
				replyMSG.append("<font color=\"FFFF00\">You can't participate in this event with a cursed Weapon.</font><br>");
			}
			else if (!_started && _joining && eventPlayer.getLevel() >= _minlvl && eventPlayer.getLevel() <=_maxlvl)
			{
				if (_players.contains(eventPlayer) || checkShufflePlayers(eventPlayer))
				{
					if (Config.TVT_EVEN_TEAMS.equals("NO") || Config.TVT_EVEN_TEAMS.equals("BALANCE"))
						replyMSG.append("You are already participating in team <font color=\"LEVEL\">" + eventPlayer._teamNameTvT + "</font><br><br>");
					else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE"))
						replyMSG.append("You are already participating!<br><br>");

					replyMSG.append("<table border=\"0\"><tr>");
					replyMSG.append("<td width=\"60\"><center><button value=\"remove\" action=\"bypass -h npc_" + objectId + "_tvt_player_leave\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></center></td>");
					replyMSG.append("<td width=\"120\">your participation!</td>");
					replyMSG.append("</tr></table>");
				}
				else
				{
					replyMSG.append("You want to participate in the event?<br><br>");
					replyMSG.append("<td width=\"200\">Your level : <font color=\"00FF00\">" + eventPlayer.getLevel() + "</font></td><br>");
					replyMSG.append("<td width=\"200\">Min level : <font color=\"00FF00\">" + _minlvl + "</font></td><br>");
					replyMSG.append("<td width=\"200\">Max level : <font color=\"00FF00\">" + _maxlvl + "</font></td><br><br>");

					if (Config.TVT_EVEN_TEAMS.equals("NO") || Config.TVT_EVEN_TEAMS.equals("BALANCE"))
					{
						replyMSG.append("<center><table border=\"0\">");

						for (String team : _teams)
						{
							replyMSG.append("<tr><td width=\"100\"><font color=\"LEVEL\">" + team + "</font>&nbsp;(" + teamPlayersCount(team) + " joined)</td>");
							replyMSG.append("<td width=\"60\"><button value=\"Join\" action=\"bypass -h npc_" + objectId + "_tvt_player_join " + team
									+ "\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
						}

						replyMSG.append("</table></center>");
					}
					else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE"))
					{
						replyMSG.append("<center><table border=\"0\">");

						for (String team : _teams)
							replyMSG.append("<tr><td width=\"100\"><font color=\"LEVEL\">" + team + "</font></td>");

						replyMSG.append("</table></center><br>");

						replyMSG.append("<button value=\"Join\" action=\"bypass -h npc_" + objectId + "_tvt_player_join eventShuffle\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
						replyMSG.append("Teams will be randomly generated!");
					}
				}
			}
			else if (_started && !_joining)
				replyMSG.append("<center>TvT match is in progress.</center>");
			else if (eventPlayer.getLevel() < _minlvl || eventPlayer.getLevel() > _maxlvl)
			{
				replyMSG.append("Your level : <font color=\"00FF00\">" + eventPlayer.getLevel() + "</font><br>");
				replyMSG.append("Min level : <font color=\"00FF00\">" + _minlvl + "</font><br>");
				replyMSG.append("Max level : <font color=\"00FF00\">" + _maxlvl + "</font><br><br>");
				replyMSG.append("<font color=\"FFFF00\">You can't participate in this event.</font><br>");
			}
			// Show how many players joined & how many are still needed to join
			replyMSG.append("<br>There are " + _playersShuffle.size() + " player(s) participating in this event.<br>");
			if (_joining)
			{
				if (_playersShuffle.size() < _minPlayers)
				{
					int playersNeeded = _minPlayers - _playersShuffle.size();
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
			_log.warning("TvT Engine[showEventHtlm(" + eventPlayer.getName() + ", " + objectId + ")]: exception " + e.getMessage());
		}
	}

	public static void addPlayer(L2PcInstance player, String teamName)
	{
		if (!addPlayerOk(teamName, player))
			return;
		
		if (Config.TVT_EVEN_TEAMS.equals("NO") || Config.TVT_EVEN_TEAMS.equals("BALANCE"))
		{
			player._teamNameTvT = teamName;
			_players.add(player);
			setTeamPlayersCount(teamName, teamPlayersCount(teamName) + 1);
		}
		else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE"))
			_playersShuffle.add(player);

		player.inEventTvT = true;
		player._countTvTkills = 0;
	}

	public static void removeOfflinePlayers()
	{
		try
		{
			if (_playersShuffle == null)
				return;
			else if (_playersShuffle.isEmpty())
				return;
			else if (_playersShuffle.size() > 0)
			{
				for (L2PcInstance player : _playersShuffle)
				{
					if (player == null)
						_playersShuffle.remove(player);			
						
					else if (!player.isOnline() || player.isInJail() || player.isDead())
						removePlayer(player);

					if (_playersShuffle.size() == 0 || _playersShuffle.isEmpty())
						break;
				}
			}
		}
		catch (Exception e)
		{
			_log.warning("TVT Engine Exception: " + e.getMessage());
			return;
		}
	}

	public static boolean checkShufflePlayers(L2PcInstance eventPlayer)
	{
		try
		{
			for (L2PcInstance player : _playersShuffle)
			{
				if (player == null || !player.isOnline())
				{
					_playersShuffle.remove(player);
					eventPlayer.inEventTvT = false;
					continue;
				}
				else if (player.getObjectId() == eventPlayer.getObjectId())
				{
					eventPlayer.inEventTvT = true;
					eventPlayer._countTvTkills = 0;
					return true;
				}
				//this 1 is incase player got new objectid after DC or reconnect
				else if (player.getName().equals(eventPlayer.getName()))
				{
					_playersShuffle.remove(player);
					_playersShuffle.add(eventPlayer);
					eventPlayer.inEventTvT = true;
					eventPlayer._countTvTkills = 0;
					return true;
				}
			}
		}
		catch (Exception e)
		{
		}
		return false;
	}

	public static boolean addPlayerOk(String teamName, L2PcInstance eventPlayer)
	{
		try
		{
			if (checkShufflePlayers(eventPlayer) || eventPlayer.inEventTvT)
			{
				eventPlayer.sendMessage("You are already participating in the event!");
				return false;
			}
			if (eventPlayer.inEventCTF || eventPlayer.inEventDM || eventPlayer.inEventRaid)
			{
				eventPlayer.sendMessage("You are already participating in another event!");
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
			if (CTF._savePlayers.contains(eventPlayer.getName()))
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
			_log.warning("TvT Engine exception: " + e.getMessage());
		}

		if (Config.TVT_EVEN_TEAMS.equals("NO"))
			return true;
		else if (Config.TVT_EVEN_TEAMS.equals("BALANCE"))
		{
			boolean allTeamsEqual = true;
			int countBefore = -1;

			for (int playersCount : _teamPlayersCount)
			{
				if (countBefore == -1)
					countBefore = playersCount;

				if (countBefore != playersCount)
				{
					allTeamsEqual = false;
					break;
				}

				countBefore = playersCount;
			}

			if (allTeamsEqual)
				return true;

			countBefore = Integer.MAX_VALUE;

			for (int teamPlayerCount : _teamPlayersCount)
			{
				if (teamPlayerCount < countBefore)
					countBefore = teamPlayerCount;
			}

			CopyOnWriteArrayList<String> joinableTeams = new CopyOnWriteArrayList<String>();

			for (String team : _teams)
			{
				if (teamPlayersCount(team) == countBefore)
					joinableTeams.add(team);
			}

			if (joinableTeams.contains(teamName))
				return true;
		}
		else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE"))
			return true;

		eventPlayer.sendMessage("Too many players in team \"" + teamName + "\"");
		return false;
	}

	public static synchronized void addDisconnectedPlayer(L2PcInstance player)
	{
		if ((Config.TVT_EVEN_TEAMS.equals("SHUFFLE") && (_teleport || _started))
				|| (Config.TVT_EVEN_TEAMS.equals("NO") || Config.TVT_EVEN_TEAMS.equals("BALANCE") && (_teleport || _started)))
		{
			if (Config.TVT_ON_START_REMOVE_ALL_EFFECTS)
			{
				for (L2Effect e : player.getAllEffects())
				{
					if (e != null)
						e.exit();
				}
			}

			player._teamNameTvT = _savePlayerTeams.get(_savePlayers.indexOf(player.getName()));
			for (L2PcInstance p : _players)
			{
				if (p == null)
				{
					continue;
				}
				//check by name incase player got new objectId
				else if (p.getName().equals(player.getName()))
				{
					player._originalNameColorTvT = player.getAppearance().getNameColor();
					player._originalTitleColorTvT = player.getAppearance().getTitleColor();
					player._originalTitleTvT = player.getTitle();
					player._originalKarmaTvT = player.getKarma();
					player.inEventTvT = true;
					player._countTvTkills = p._countTvTkills;
					_players.remove(p); //removing old object id from CopyOnWriteArrayList
					_players.add(player); //adding new objectId to CopyOnWriteArrayList
					break;
				}
			}

			player.getAppearance().setNameColor(_teamColors.get(_teams.indexOf(player._teamNameTvT)));
			player.getAppearance().setTitleColor(_teamColors.get(_teams.indexOf(player._teamNameTvT)));
			player.setKarma(0);
			if (_teams.size() >= 2)
				player.setTeam(_teams.indexOf(player._teamNameTvT) + 1);
			player.broadcastUserInfo();
			player.teleToLocation(_teamsX.get(_teams.indexOf(player._teamNameTvT)), _teamsY.get(_teams.indexOf(player._teamNameTvT)), _teamsZ.get(_teams.indexOf(player._teamNameTvT)));
		}
	}

	public static void removePlayer(L2PcInstance player)
	{
		if (player.inEventTvT)
		{
			if (!_joining)
			{
				player.getAppearance().setNameColor(player._originalNameColorTvT);
				player.getAppearance().setTitleColor(player._originalTitleColorTvT);
				player.setKarma(player._originalKarmaTvT);
				if (_teams.size() >= 2)
					player.setTeam(0);
				player.broadcastUserInfo();
			}
			player._teamNameTvT = "";
			player._countTvTkills = 0;
			player.inEventTvT = false;

			if ((Config.TVT_EVEN_TEAMS.equals("NO") || Config.TVT_EVEN_TEAMS.equals("BALANCE")) && _players.contains(player))
			{
				setTeamPlayersCount(player._teamNameTvT, teamPlayersCount(player._teamNameTvT) - 1);
				_players.remove(player);
			}
			else if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE") && (!_playersShuffle.isEmpty() && _playersShuffle.contains(player)))
				_playersShuffle.remove(player);
		}
	}

	public static void cleanTvT()
	{
		_log.warning("TvT : Cleaning players.");
		for (L2PcInstance player : _players)
		{
			if (player != null)
			{
				removePlayer(player);
				if (_savePlayers.contains(player.getName()))
					_savePlayers.remove(player.getName());
				player.inEventTvT = false;
			}
		}
		if (_playersShuffle != null && !_playersShuffle.isEmpty())
		{
			for (L2PcInstance player : _playersShuffle)
			{
				if (player != null)
					player.inEventTvT = false;
			}
		}
		_log.warning("TvT : Cleaning teams.");
		for (String team : _teams)
		{
			int index = _teams.indexOf(team);

			_teamPlayersCount.set(index, 0);
			_teamKillsCount.set(index, 0);
		}

		_topKills = 0;
		_topTeam = "";
		_players = new CopyOnWriteArrayList<L2PcInstance>();
		_playersShuffle = new CopyOnWriteArrayList<L2PcInstance>();
		_savePlayers = new CopyOnWriteArrayList<String>();
		_savePlayerTeams = new CopyOnWriteArrayList<String>();
		_log.warning("Cleaning TvT done.");
	}

	public static void unspawnEventNpc()
	{
		if (_npcSpawn == null)
			return;

		_npcSpawn.getLastSpawn().deleteMe();
		_npcSpawn.stopRespawn();
		SpawnTable.getInstance().deleteSpawn(_npcSpawn, true);
	}

	public static void teleportFinish()
	{
		AnnounceToPlayers(false, "Teleport back to participation NPC in 20 seconds!");

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
								_log.warning("TVT Engine exception: " + se.getMessage());
							}
							finally
							{
								try { con.close(); } catch (Exception e) {}
							}
						}
					}
				}
				_log.warning("TvT: Teleport done.");
				cleanTvT();
			}
		}, 20000);
	}

	public static int teamKillsCount(String teamName)
	{
		int index = _teams.indexOf(teamName);

		if (index == -1)
			return -1;

		return _teamKillsCount.get(index);
	}

	public static void setTeamKillsCount(String teamName, int teamKillsCount)
	{
		int index = _teams.indexOf(teamName);

		if (index == -1)
			return;

		_teamKillsCount.set(index, teamKillsCount);
	}

	public static int teamPlayersCount(String teamName)
	{
		int index = _teams.indexOf(teamName);

		if (index == -1)
			return -1;

		return _teamPlayersCount.get(index);
	}

	public static void setTeamPlayersCount(String teamName, int teamPlayersCount)
	{
		int index = _teams.indexOf(teamName);

		if (index == -1)
			return;

		_teamPlayersCount.set(index, teamPlayersCount);
	}
}
