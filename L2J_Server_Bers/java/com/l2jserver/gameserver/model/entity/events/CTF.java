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

import javolution.text.TextBuilder;

import java.util.logging.Logger;

import com.l2jserver.Config;
import com.l2jserver.L2DatabaseFactory;
import com.l2jserver.gameserver.Announcements;
import com.l2jserver.gameserver.ThreadPoolManager;
import com.l2jserver.gameserver.datatables.ItemTable;
import com.l2jserver.gameserver.datatables.NpcTable;
import com.l2jserver.gameserver.datatables.SpawnTable;
import com.l2jserver.gameserver.model.L2Effect;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.L2Party;
import com.l2jserver.gameserver.model.L2Radar;
import com.l2jserver.gameserver.model.L2Spawn;
import com.l2jserver.gameserver.model.actor.L2Summon;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.actor.instance.L2PetInstance;
import com.l2jserver.gameserver.model.itemcontainer.Inventory;
import com.l2jserver.gameserver.network.serverpackets.ActionFailed;
import com.l2jserver.gameserver.network.serverpackets.CreatureSay;
import com.l2jserver.gameserver.network.serverpackets.InventoryUpdate;
import com.l2jserver.gameserver.network.serverpackets.ItemList;
import com.l2jserver.gameserver.network.serverpackets.MagicSkillUse;
import com.l2jserver.gameserver.network.serverpackets.NpcHtmlMessage;
import com.l2jserver.gameserver.network.serverpackets.PlaySound;
import com.l2jserver.gameserver.network.serverpackets.RadarControl;
import com.l2jserver.gameserver.templates.chars.L2NpcTemplate;

import com.l2jserver.util.Rnd;

public class CTF
{
	private final static Logger	_log	= Logger.getLogger(CTF.class.getName());
	private static int			_FlagNPC	= 35062, _FLAG_IN_HAND_ITEM_ID = 6718;
	public static String		_eventName	= new String(), _eventDesc = new String(), _topTeam = new String(), _joiningLocationName = new String();
	public static CopyOnWriteArrayList<String>	_teams	= new CopyOnWriteArrayList<String>(), _savePlayers = new CopyOnWriteArrayList<String>(), _savePlayerTeams = new CopyOnWriteArrayList<String>();
	public static CopyOnWriteArrayList<L2PcInstance>	_players	= new CopyOnWriteArrayList<L2PcInstance>(), _playersShuffle = new CopyOnWriteArrayList<L2PcInstance>();
	public static CopyOnWriteArrayList<Integer>		_teamPlayersCount	= new CopyOnWriteArrayList<Integer>(), _teamColors = new CopyOnWriteArrayList<Integer>(), _teamsX = new CopyOnWriteArrayList<Integer>(),
			_teamsY = new CopyOnWriteArrayList<Integer>(), _teamsZ = new CopyOnWriteArrayList<Integer>();
	public static boolean				_joining			= false, _teleport = false, _started = false, _sitForced = false;
	public static L2Spawn				_npcSpawn;
	public static int					_npcId				= 0, _npcX = 0, _npcY = 0, _npcZ = 0, _npcHeading = 0, _rewardId = 0, _rewardAmount = 0, 
			_minlvl = 0, _maxlvl = 0, _joinTime = 0, _eventTime = 0, _minPlayers = 0, _maxPlayers = 0;
	
	public static int					_giftId					= 0;
	public static int					_giftAmount				= 0;
	public static CopyOnWriteArrayList<Integer>		_teamPointsCount	= new CopyOnWriteArrayList<Integer>();
	public static CopyOnWriteArrayList<Integer>		_flagIds			= new CopyOnWriteArrayList<Integer>(), _flagsX = new CopyOnWriteArrayList<Integer>(), _flagsY = new CopyOnWriteArrayList<Integer>(),
			_flagsZ = new CopyOnWriteArrayList<Integer>();
	public static CopyOnWriteArrayList<L2Spawn>		_flagSpawns			= new CopyOnWriteArrayList<L2Spawn>(), _throneSpawns = new CopyOnWriteArrayList<L2Spawn>();
	public static CopyOnWriteArrayList<Boolean>		_flagsTaken			= new CopyOnWriteArrayList<Boolean>();
	public static int					_topScore			= 0, eventCenterX = 0, eventCenterY = 0, eventCenterZ = 0, eventOffset = 0;

	public static void showFlagHtml(L2PcInstance eventPlayer, String objectId, String teamName)
	{
		if (eventPlayer == null)
			return;

		try
		{
			NpcHtmlMessage adminReply = new NpcHtmlMessage(5);

			TextBuilder replyMSG = new TextBuilder();
			
			replyMSG.append("<html><body><center>");
			replyMSG.append("CTF Flag<br><br>");
			replyMSG.append("<font color=\"00FF00\">" + teamName + "'s Flag</font><br1>");
			if (eventPlayer._teamNameCTF != null && eventPlayer._teamNameCTF.equals(teamName))
				replyMSG.append("<font color=\"LEVEL\">This is your Flag</font><br1>");
			else
				replyMSG.append("<font color=\"LEVEL\">Enemy Flag!</font><br1>");
			if (_started)
			{
				processInFlagRange(eventPlayer);
			}
			else
				replyMSG.append("CTF match is not in progress yet.<br>Wait for a GM to start the event<br>");
			replyMSG.append("</center></body></html>");
			adminReply.setHtml(replyMSG.toString());
			eventPlayer.sendPacket(adminReply);
		}
		catch (Exception e)
		{
			_log.warning("" + "CTF Engine[showEventHtlm(" + eventPlayer.getName() + ", " + objectId + ")]: exception: " + e.getStackTrace());
		}
	}

	public static void CheckRestoreFlags()
	{
		CopyOnWriteArrayList<Integer> teamsTakenFlag = new CopyOnWriteArrayList<Integer>();
		try
		{
			for (L2PcInstance player : _players)
			{ //if there's a player with a flag
				//add the index of the team who's FLAG WAS TAKEN to the list
				if (player != null)
				{
					if (!player.isOnline() && player._haveFlagCTF)// logged off with a flag in his hands
					{
						AnnounceToPlayers(false, _eventName + "(CTF): " + player.getName() + " logged off with a CTF flag!");
						player._haveFlagCTF = false;
						if (_teams.indexOf(player._teamNameHaveFlagCTF) >= 0)
						{
							if (_flagsTaken.get(_teams.indexOf(player._teamNameHaveFlagCTF)))
							{
								_flagsTaken.set(_teams.indexOf(player._teamNameHaveFlagCTF), false);
								spawnFlag(player._teamNameHaveFlagCTF);
								AnnounceToPlayers(false, _eventName + "(CTF): " + player._teamNameHaveFlagCTF + " flag now returned to place.");
							}
						}
						removeFlagFromPlayer(player);
						player._teamNameHaveFlagCTF = null;
						return;
					}
					else if (player._haveFlagCTF)
						teamsTakenFlag.add(_teams.indexOf(player._teamNameHaveFlagCTF));
				}
			}
			//Go over the list of ALL teams
			for (String team : _teams)
			{
				if (team == null)
					continue;
				int index = _teams.indexOf(team);
				if (!teamsTakenFlag.contains(index))
				{
					if (_flagsTaken.get(index))
					{
						_flagsTaken.set(index, false);
						spawnFlag(team);
						AnnounceToPlayers(false, _eventName + "(CTF): " + team + " flag returned due to player error.");
					}
				}
			}
			//Check if a player ran away from the event holding a flag:
			for (L2PcInstance player : _players)
			{
				if (player != null && player._haveFlagCTF)
				{
					if (isOutsideCTFArea(player))
					{
						AnnounceToPlayers(false, _eventName + "(CTF): " + player.getName() + " escaped from the event holding a flag!");
						player._haveFlagCTF = false;
						if (_teams.indexOf(player._teamNameHaveFlagCTF) >= 0)
						{
							if (_flagsTaken.get(_teams.indexOf(player._teamNameHaveFlagCTF)))
							{
								_flagsTaken.set(_teams.indexOf(player._teamNameHaveFlagCTF), false);
								spawnFlag(player._teamNameHaveFlagCTF);
								AnnounceToPlayers(false, _eventName + "(CTF): " + player._teamNameHaveFlagCTF + " flag now returned to place.");
							}
						}
						removeFlagFromPlayer(player);
						player._teamNameHaveFlagCTF = null;
						player.teleToLocation(_teamsX.get(_teams.indexOf(player._teamNameCTF)), _teamsY.get(_teams.indexOf(player._teamNameCTF)), _teamsZ
								.get(_teams.indexOf(player._teamNameCTF)));
						player.sendMessage("You have been returned to your team spawn");
						return;
					}
				}
			}
		}
		catch (Exception e)
		{
			_log.warning("CTF.restoreFlags() Error:" + e.toString());
		}
	}

	public static void kickPlayerFromCTf(L2PcInstance playerToKick)
	{
		if (playerToKick == null)
			return;
		
		if (_joining)
		{
			_playersShuffle.remove(playerToKick);
			_players.remove(playerToKick);
			playerToKick.inEventCTF = false;
			playerToKick._teamNameCTF = new String();
		}
		if (_started || _teleport)
		{
			_playersShuffle.remove(playerToKick);
			playerToKick.inEventCTF = false;
			removePlayer(playerToKick);
			if (playerToKick.isOnline())
			{
				playerToKick.getAppearance().setNameColor(playerToKick._originalNameColorCTF);
				playerToKick.setKarma(playerToKick._originalKarmaCTF);
				playerToKick.setTitle(playerToKick._originalTitleCTF);
				playerToKick.broadcastUserInfo();
				playerToKick.sendMessage("You have been kicked from the CTF.");
				playerToKick.teleToLocation(_npcX, _npcY, _npcZ, false);
			}
		}
	}

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

	public static void Started(L2PcInstance player)
	{
		player._teamNameHaveFlagCTF = null;
		player._haveFlagCTF = false;
	}

	public static void StartEvent()
	{
		for (L2PcInstance player : _players)
		{
			if (player != null)
			{
				player._teamNameHaveFlagCTF = null;
				player._haveFlagCTF = false;
			}
		}
		AnnounceToPlayers(false, _eventName + "(CTF): Started. Go Capture the Flags!");
	}

	public static void addFlagToPlayer(L2PcInstance _player)
	{
		//remove items from the player hands (right, left, both)
		// This is NOT a BUG, I don't want them to see the icon they have 8D
		L2ItemInstance wpn = _player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
		L2ItemInstance wpn2 = _player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		if (wpn != null)
		{
			_player.getInventory().unEquipItemInBodySlotAndRecord(wpn.getItem().getBodyPart());
			
			if (wpn2 != null)
				_player.getInventory().unEquipItemInBodySlotAndRecord(wpn2.getItem().getBodyPart());
		}
		//add the flag in his hands
		_player.getInventory().equipItem(ItemTable.getInstance().createItem("", CTF._FLAG_IN_HAND_ITEM_ID, 1, _player, null));
		_player._haveFlagCTF = true;
		_player.broadcastUserInfo();
		CreatureSay cs = new CreatureSay(_player.getObjectId(), 15, ":", "You got it! Run back! ::"); // 8D
		_player.sendPacket(cs);
	}

	public static void removeFlagFromPlayer(L2PcInstance player)
	{
		L2ItemInstance wpn = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
		player._haveFlagCTF = false;
		if (wpn != null)
		{
			L2ItemInstance[] unequiped = player.getInventory().unEquipItemInBodySlotAndRecord(wpn.getItem().getBodyPart());
			player.getInventory().destroyItemByItemId("", CTF._FLAG_IN_HAND_ITEM_ID, 1, player, null);
			InventoryUpdate iu = new InventoryUpdate();
			for (L2ItemInstance element : unequiped)
				iu.addModifiedItem(element);
			player.sendPacket(iu);
			player.sendPacket(new ItemList(player, true)); // get your weapon back now ...
			player.abortAttack();
			player.broadcastUserInfo();
		}
		else
		{
			player.getInventory().destroyItemByItemId("", CTF._FLAG_IN_HAND_ITEM_ID, 1, player, null);
			player.sendPacket(new ItemList(player, true)); // get your weapon back now ...
			player.abortAttack();
			player.broadcastUserInfo();
		}
	}

	public static void setTeamFlag(String teamName, L2PcInstance activeChar)
	{
		int index = _teams.indexOf(teamName);

		if (index == -1)
			return;
		addOrSet(_teams.indexOf(teamName), null, false, _FlagNPC, activeChar.getX(), activeChar.getY(), activeChar.getZ());
	}

	public static void setTeamFlag(String teamName, int x, int y, int z)
	{
		int index = _teams.indexOf(teamName);

		if (index == -1)
			return;
		addOrSet(_teams.indexOf(teamName), null, false, _FlagNPC, x, y, z);
	}

	public static void spawnAllFlags()
	{
		while (_flagSpawns.size() < _teams.size())
			_flagSpawns.add(null);
		while (_throneSpawns.size() < _teams.size())
			_throneSpawns.add(null);
		for (String team : _teams)
		{
			int index = _teams.indexOf(team);
			L2NpcTemplate tmpl = NpcTable.getInstance().getTemplate(_flagIds.get(index));
			L2NpcTemplate throne = NpcTable.getInstance().getTemplate(32027);
			try
			{
				//spawn throne
				_throneSpawns.set(index, new L2Spawn(throne));
				_throneSpawns.get(index).setLocx(_flagsX.get(index));
				_throneSpawns.get(index).setLocy(_flagsY.get(index));
				_throneSpawns.get(index).setLocz(_flagsZ.get(index) - 10);
				_throneSpawns.get(index).setAmount(1);
				_throneSpawns.get(index).setHeading(0);
				_throneSpawns.get(index).setRespawnDelay(1);
				SpawnTable.getInstance().addNewSpawn(_throneSpawns.get(index), false);
				_throneSpawns.get(index).init();
				_throneSpawns.get(index).getLastSpawn().getStatus().setCurrentHp(999999999);
				_throneSpawns.get(index).getLastSpawn().decayMe();
				_throneSpawns.get(index).getLastSpawn().spawnMe(_throneSpawns.get(index).getLastSpawn().getX(), _throneSpawns.get(index).getLastSpawn().getY(), _throneSpawns.get(index).getLastSpawn().getZ());
				_throneSpawns.get(index).getLastSpawn().setTitle(team + " Throne");
				_throneSpawns.get(index).getLastSpawn().broadcastPacket(new MagicSkillUse(_throneSpawns.get(index).getLastSpawn(), _throneSpawns.get(index).getLastSpawn(), 1036, 1, 5500, 1));
				_throneSpawns.get(index).getLastSpawn().isCTF_throneSpawn = true;

				//spawn flag
				_flagSpawns.set(index, new L2Spawn(tmpl));
				_flagSpawns.get(index).setLocx(_flagsX.get(index));
				_flagSpawns.get(index).setLocy(_flagsY.get(index));
				_flagSpawns.get(index).setLocz(_flagsZ.get(index));
				_flagSpawns.get(index).setAmount(1);
				_flagSpawns.get(index).setHeading(0);
				_flagSpawns.get(index).setRespawnDelay(1);
				SpawnTable.getInstance().addNewSpawn(_flagSpawns.get(index), false);
				_flagSpawns.get(index).init();
				_flagSpawns.get(index).getLastSpawn().getStatus().setCurrentHp(999999999);
				_flagSpawns.get(index).getLastSpawn().setTitle(team + "'s Flag");
				_flagSpawns.get(index).getLastSpawn().CTF_FlagTeamName = team;
				_flagSpawns.get(index).getLastSpawn().decayMe();
				_flagSpawns.get(index).getLastSpawn().spawnMe(_flagSpawns.get(index).getLastSpawn().getX(), _flagSpawns.get(index).getLastSpawn().getY(), _flagSpawns.get(index).getLastSpawn().getZ());
				_flagSpawns.get(index).getLastSpawn().isCTF_Flag = true;
				calculateOutSideOfCTF(); // sets event boundaries so players don't run with the flag.
			}
			catch (Exception e)
			{
				_log.warning("CTF Engine[spawnAllFlags()]: exception: " + e.getStackTrace());
			}
		}
	}

	public static void processTopTeam()
	{

		_topTeam = null;
		for (String team : _teams)
		{
			if (teamPointsCount(team) == _topScore && _topScore > 0)
				_topTeam = null;
			if (teamPointsCount(team) > _topScore)
			{
				_topTeam = team;
				_topScore = teamPointsCount(team);
			}
		}
		if (_topScore <= 0)
		{
			AnnounceToPlayers(true, _eventName + "(CTF): No flags taken).");
		}
		else
		{
			if (_topTeam == null)
				AnnounceToPlayers(true, _eventName + "(CTF): Maximum flags taken : " + _topScore + " flags! No one won.");
			else
			{
				AnnounceToPlayers(true, _eventName + "(CTF): Team " + _topTeam + " wins the match, with " + _topScore + " flags taken!");
				rewardTeam(_topTeam);
			}
		}
		teleportFinish();
	}

	public static void unspawnAllFlags()
	{
		try
		{
			if (_throneSpawns == null || _flagSpawns == null || _teams == null)
				return;
			for (String team : _teams)
			{
				int index = _teams.indexOf(team);
				if (_throneSpawns.get(index) != null)
				{
					_throneSpawns.get(index).getLastSpawn().deleteMe();
					_throneSpawns.get(index).stopRespawn();
					SpawnTable.getInstance().deleteSpawn(_throneSpawns.get(index), true);
				}
				if (_flagSpawns.get(index) != null)
				{
					_flagSpawns.get(index).getLastSpawn().deleteMe();
					_flagSpawns.get(index).stopRespawn();
					SpawnTable.getInstance().deleteSpawn(_flagSpawns.get(index), true);
				}
			}
			_throneSpawns.clear();
		}
		catch (Throwable t)
		{
			_log.warning("CTF Engine[unspawnAllFlags()]: exception: " + t.getStackTrace());
		}
	}

	private static void unspawnFlag(String teamName)
	{
		int index = _teams.indexOf(teamName);

		_flagSpawns.get(index).getLastSpawn().deleteMe();
		_flagSpawns.get(index).stopRespawn();
		SpawnTable.getInstance().deleteSpawn(_flagSpawns.get(index), true);
	}

	public static void spawnFlag(String teamName)
	{
		int index = _teams.indexOf(teamName);
		L2NpcTemplate tmpl = NpcTable.getInstance().getTemplate(_flagIds.get(index));

		try
		{
			_flagSpawns.set(index, new L2Spawn(tmpl));

			_flagSpawns.get(index).setLocx(_flagsX.get(index));
			_flagSpawns.get(index).setLocy(_flagsY.get(index));
			_flagSpawns.get(index).setLocz(_flagsZ.get(index));
			_flagSpawns.get(index).setAmount(1);
			_flagSpawns.get(index).setHeading(0);
			_flagSpawns.get(index).setRespawnDelay(1);

			SpawnTable.getInstance().addNewSpawn(_flagSpawns.get(index), false);

			_flagSpawns.get(index).init();
			_flagSpawns.get(index).getLastSpawn().getStatus().setCurrentHp(999999999);
			_flagSpawns.get(index).getLastSpawn().setTitle(teamName + "'s Flag");
			_flagSpawns.get(index).getLastSpawn().CTF_FlagTeamName = teamName;
			_flagSpawns.get(index).getLastSpawn().isCTF_Flag = true;
			_flagSpawns.get(index).getLastSpawn().decayMe();
			_flagSpawns.get(index).getLastSpawn().spawnMe(_flagSpawns.get(index).getLastSpawn().getX(), _flagSpawns.get(index).getLastSpawn().getY(),
					_flagSpawns.get(index).getLastSpawn().getZ());
		}
		catch (Exception e)
		{
			_log.warning("CTF Engine[spawnFlag(" + teamName + ")]: exception: " + e.getStackTrace());
		}
	}

	public static boolean InRangeOfFlag(L2PcInstance _player, int flagIndex, int offset)
	{
        return _player.getX() > CTF._flagsX.get(flagIndex) - offset && _player.getX() < CTF._flagsX.get(flagIndex) + offset
                && _player.getY() > CTF._flagsY.get(flagIndex) - offset && _player.getY() < CTF._flagsY.get(flagIndex) + offset
                && _player.getZ() > CTF._flagsZ.get(flagIndex) - offset && _player.getZ() < CTF._flagsZ.get(flagIndex) + offset;
		}

	public static void processInFlagRange(L2PcInstance _player)
	{
		try
		{
			CheckRestoreFlags();
			for (String team : _teams)
			{
				if (team.equals(_player._teamNameCTF))
				{
					int indexOwn = _teams.indexOf(_player._teamNameCTF);

					//if player is near his team flag holding the enemy flag
					if (InRangeOfFlag(_player, indexOwn, 100) && !_flagsTaken.get(indexOwn) && _player._haveFlagCTF)
					{
						int indexEnemy = _teams.indexOf(_player._teamNameHaveFlagCTF);
						//return enemy flag to place
						_flagsTaken.set(indexEnemy, false);
						spawnFlag(_player._teamNameHaveFlagCTF);
						//remove the flag from this player
						_player.broadcastUserInfo();
						_player.broadcastUserInfo();
						removeFlagFromPlayer(_player);
						_teamPointsCount.set(indexOwn, teamPointsCount(team) + 1);
						_player.broadcastPacket(new PlaySound(0, "ItemSound.quest_finish", 1, _player.getObjectId(), _player.getX(), _player.getY(), _player
								.getZ()));
						_player.broadcastUserInfo();
						AnnounceToPlayers(false, _eventName + "(CTF): " + _player.getName() + " scores for " + _player._teamNameCTF + ".");
					}
				}
				else
				{
					int indexEnemy = _teams.indexOf(team);
					//if the player is near a enemy flag
					if (InRangeOfFlag(_player, indexEnemy, 100) && !_flagsTaken.get(indexEnemy) && !_player._haveFlagCTF && !_player.isDead())
					{
						if(_player.isMounted() || _player.isFlying())
						{
							_player.sendMessage("You can not mount a pet while on CTF Event.");
							break;
						}

						_flagsTaken.set(indexEnemy, true);
						unspawnFlag(team);
						_player._teamNameHaveFlagCTF = team;
						addFlagToPlayer(_player);
						_player.broadcastUserInfo();
						_player._haveFlagCTF = true;
						AnnounceToPlayers(false, _eventName + "(CTF): " + team + " flag taken by " + _player.getName() + "...");
						pointTeamTo(_player, team);
						break;
					}
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public static void pointTeamTo(L2PcInstance hasFlag, String ourFlag)
	{
		try
		{
			for (L2PcInstance player : _players)
			{
				if (player != null && player.isOnline())
				{
					if (player._teamNameCTF.equals(ourFlag))
					{
						player.sendMessage(hasFlag.getName() + " took your flag!");
						if (player._haveFlagCTF)
						{
							player.sendMessage("You can not return the flag to headquarters, until your flag is returned to it's place.");
							player.sendPacket(new RadarControl(1, 1, player.getX(), player.getY(), player.getZ()));
						}
						else
						{
							player.sendPacket(new RadarControl(0, 1, hasFlag.getX(), hasFlag.getY(), hasFlag.getZ()));
							L2Radar rdr = new L2Radar(player);
							L2Radar.RadarOnPlayer radar = rdr.new RadarOnPlayer(hasFlag, player);
							ThreadPoolManager.getInstance().scheduleGeneral(radar, 10000 + Rnd.get(30000));
						}
					}
				}
			}
		}
		catch (Throwable t)
		{
		}
	}

	public static int teamPointsCount(String teamName)
	{
		int index = _teams.indexOf(teamName);

		if (index == -1)
			return -1;

		return _teamPointsCount.get(index);
	}

	public static void setTeamPointsCount(String teamName, int teamPointCount)
	{
		int index = _teams.indexOf(teamName);

		if (index == -1)
			return;

		_teamPointsCount.set(index, teamPointCount);
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
				_log.fine("CTF Engine[addTeam(" + teamName + ")]: checkTeamOk() = false");
			return;
		}

		if (teamName.equals(" "))
			return;

		_teams.add(teamName);
		_teamPlayersCount.add(0);
		_teamColors.add(0);
		_teamsX.add(0);
		_teamsY.add(0);
		_teamsZ.add(0);
		_teamPointsCount.add(0);
		addOrSet(_teams.indexOf(teamName), null, false, _FlagNPC, 0, 0, 0);
	}

	private static void addOrSet(int listSize, L2Spawn flagSpawn, boolean flagsTaken, int flagId, int flagX, int flagY, int flagZ)
	{
		while (_flagsX.size() <= listSize)
		{
			_flagSpawns.add(null);
			_flagsTaken.add(false);
			_flagIds.add(_FlagNPC);
			_flagsX.add(0);
			_flagsY.add(0);
			_flagsZ.add(0);
		}
		_flagSpawns.set(listSize, flagSpawn);
		_flagsTaken.set(listSize, flagsTaken);
		_flagIds.set(listSize, flagId);
		_flagsX.set(listSize, flagX);
		_flagsY.set(listSize, flagY);
		_flagsZ.set(listSize, flagZ);
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
				_log.fine("CTF Engine[removeTeam(" + teamName + ")]: checkTeamOk() = false");
			return;
		}

		if (teamPlayersCount(teamName) > 0)
		{
			if (Config.DEBUG)
				_log.fine("CTF Engine[removeTeam(" + teamName + ")]: teamPlayersCount(teamName) > 0");
			return;
		}

		int index = _teams.indexOf(teamName);

		if (index == -1)
			return;

		_teamsZ.remove(index);
		_teamsY.remove(index);
		_teamsX.remove(index);
		_teamColors.remove(index);
		_teamPointsCount.remove(index);
		_teamPlayersCount.remove(index);
		_teams.remove(index);
		_flagSpawns.remove(index);
		_flagsTaken.remove(index);
		_flagIds.remove(index);
		_flagsX.remove(index);
		_flagsY.remove(index);
		_flagsZ.remove(index);
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
			activeChar.sendMessage("Event not setted propertly.");
			if (Config.DEBUG)
				_log.fine("CTF Engine[startJoin(" + activeChar.getName() + ")]: startJoinOk() = false");
			return;
		}

		_joining = true;
		spawnEventNpc(activeChar);
		AnnounceToPlayers(true, _eventName + " (CTF)!");
		if (Config.CTF_ANNOUNCE_REWARD)
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
				_log.fine("CTF Engine[startJoin(startJoinOk() = false");
			return;
		}

		_joining = true;
		spawnEventNpc();
		AnnounceToPlayers(true, "Recruiting levels " + _minlvl + " to " + _maxlvl);
		AnnounceToPlayers(true, "Joinable in " + _joiningLocationName + "!");
		if (Config.CTF_ANNOUNCE_REWARD)
			AnnounceToPlayers(true, "Reward: " + _rewardAmount + " " + ItemTable.getInstance().getTemplate(_rewardId).getName());
	}

	public static boolean startAutoJoin()
	{
		if (!startJoinOk())
		{
			if (Config.DEBUG)
				_log.fine("CTF Engine[startJoin]: startJoinOk() = false");
			return false;
		}

		_joining = true;
		spawnEventNpc();
		AnnounceToPlayers(true, _eventName + " (CTF)!");
		if (Config.CTF_ANNOUNCE_REWARD)
			AnnounceToPlayers(true, "Reward: " + _rewardAmount + " " + ItemTable.getInstance().getTemplate(_rewardId).getName());
		AnnounceToPlayers(true, "Recruiting levels " + _minlvl + " to " + _maxlvl);
		AnnounceToPlayers(true, "Joinable in " + _joiningLocationName + "!");
		return true;
	}

	public static boolean startJoinOk()
	{
		if (_started || _teleport || _joining || _teams.size() < 2 || _eventName.equals("") || _joiningLocationName.equals("") || _eventDesc.equals("")
				|| _npcId == 0 || _npcX == 0 || _npcY == 0 || _npcZ == 0 || _rewardId == 0 || _rewardAmount == 0 || _giftId == 0 || _giftAmount == 0 || _teamsX.contains(0) || _teamsY.contains(0)
				|| _teamsZ.contains(0))
			return false;
		try
		{
			if (_flagsX.contains(0) || _flagsY.contains(0) || _flagsZ.contains(0) || _flagIds.contains(0))
				return false;
			if (_flagsX.size() < _teams.size() || _flagsY.size() < _teams.size() || _flagsZ.size() < _teams.size() || _flagIds.size() < _teams.size())
				return false;
		}
		catch (ArrayIndexOutOfBoundsException e)
		{
			return false;
		}
		return true;
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
			_npcSpawn.getLastSpawn().isEventMobCTF = true;
			_npcSpawn.getLastSpawn().isAggressive();
			_npcSpawn.getLastSpawn().decayMe();
			_npcSpawn.getLastSpawn().spawnMe(_npcSpawn.getLastSpawn().getX(), _npcSpawn.getLastSpawn().getY(), _npcSpawn.getLastSpawn().getZ());

			_npcSpawn.getLastSpawn().broadcastPacket(new MagicSkillUse(_npcSpawn.getLastSpawn(), _npcSpawn.getLastSpawn(), 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			_log.warning("CTF Engine[spawnEventNpc(" + activeChar.getName() + ")]: exception: " + e.getMessage());
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
			_npcSpawn.getLastSpawn().isEventMobCTF = true;
			_npcSpawn.getLastSpawn().isAggressive();
			_npcSpawn.getLastSpawn().decayMe();
			_npcSpawn.getLastSpawn().spawnMe(_npcSpawn.getLastSpawn().getX(), _npcSpawn.getLastSpawn().getY(), _npcSpawn.getLastSpawn().getZ());

			_npcSpawn.getLastSpawn().broadcastPacket(new MagicSkillUse(_npcSpawn.getLastSpawn(), _npcSpawn.getLastSpawn(), 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			_log.warning("CTF Engine[spawnEventNpc(exception: " + e.getMessage());
		}
	}

	public static void teleportStart()
	{
		if (!_joining || _started || _teleport)
			return;

		if (Config.CTF_EVEN_TEAMS.equals("SHUFFLE") && checkMinPlayers(_playersShuffle.size()))
		{
			removeOfflinePlayers();
			shuffleTeams();
		}
		else if (Config.CTF_EVEN_TEAMS.equals("SHUFFLE") && !checkMinPlayers(_playersShuffle.size()))
		{
			AnnounceToPlayers(true, "Not enough players for event. Min Requested : " + _minPlayers + ", Participating : " + _playersShuffle.size());
			return;
		}

		_joining = false;
		AnnounceToPlayers(true, _eventName + "(CTF): Teleport to team spot in 20 seconds!");

		setUserData();
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable()
		{
			public void run()
			{
				CTF.sit();
				CTF.spawnAllFlags();
				for (L2PcInstance player : _players)
				{
					if (player != null)
					{
						if (Config.CTF_ON_START_UNSUMMON_PET)
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
						player.teleToLocation(_teamsX.get(_teams.indexOf(player._teamNameCTF)), _teamsY.get(_teams.indexOf(player._teamNameCTF)), _teamsZ
								.get(_teams.indexOf(player._teamNameCTF)));
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

		if (Config.CTF_EVEN_TEAMS.equals("SHUFFLE") && checkMinPlayers(_playersShuffle.size()))
		{
			removeOfflinePlayers();
			shuffleTeams();
		}
		else if (Config.CTF_EVEN_TEAMS.equals("SHUFFLE") && !checkMinPlayers(_playersShuffle.size()))
		{
			AnnounceToPlayers(true, "Not enough players for event. Min Requested : " + _minPlayers + ", Participating : " + _playersShuffle.size());
			return false;
		}

		_joining = false;
		AnnounceToPlayers(false, _eventName + "(CTF): Teleport to team spot in 20 seconds!");

		setUserData();
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable()
		{
			public void run()
			{
				sit();
				spawnAllFlags();

				for (L2PcInstance player : _players)
				{
					if (player != null)
					{
						if (Config.CTF_ON_START_UNSUMMON_PET)
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

						player.teleToLocation(_teamsX.get(_teams.indexOf(player._teamNameCTF)), _teamsY.get(_teams.indexOf(player._teamNameCTF)), _teamsZ
								.get(_teams.indexOf(player._teamNameCTF)));
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
				_log.fine("CTF Engine[startEvent(" + activeChar.getName() + ")]: startEventOk() = false");
			return;
		}

		_teleport = false;
		sit();
		_started = true;
		StartEvent();
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
				_log.fine("CTF Engine[startEvent]: startEventOk() = false");
			return false;
		}

		_teleport = false;
		for (L2PcInstance player : _players)
		if (Config.CTF_ON_START_REMOVE_ALL_EFFECTS)
		{
			for (L2Effect e : player.getAllEffects())
			{
				if (e != null)
					e.exit();
			}
		}
		sit();
		AnnounceToPlayers(false, "Defend your flag at all cost and capture the enemy Flag!");
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
				waiter(30 * 1000); // 30 seconds wait time untill start fight after teleported
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
						AnnounceToPlayers(true, _eventName + "(CTF): Joinable in " + _joiningLocationName + "!");
						AnnounceToPlayers(true, "CTF Event: " + seconds / 60 / 60 + " hour(s) till registration close!");
					}
					else if (_started)
						AnnounceToPlayers(false, "CTF Event: " + seconds / 60 / 60 + " hour(s) till event finish!");

					break;
				case 1800: // 30 minutes left
				case 600: //  10 minutes left
				case 300: // 5 minutes left
				case 60: // 1 minute left
					if (_joining)
					{
						removeOfflinePlayers();
						AnnounceToPlayers(true, _eventName + "(CTF): Joinable in " + _joiningLocationName + "!");
						AnnounceToPlayers(true, "CTF Event: " + seconds / 60 + " minute(s) till registration ends!");
					}
					else if (_started)
						AnnounceToPlayers(false, "CTF Event: " + seconds / 60 + " minute(s) till event ends!");

					break;
				case 30: // 30 seconds left
				case 10: // 10 seconds left
				case 3: // 3 seconds left
				case 2: // 2 seconds left
				case 1: // 1 seconds left
					if (_joining)
						AnnounceToPlayers(true, "CTF Event: " + seconds + " second(s) till registration close!");
					else if (_teleport)
						AnnounceToPlayers(false, "CTF Event: " + seconds + " seconds(s) till fight starts!");
					else if (_started)
						AnnounceToPlayers(false, "CTF Event: " + seconds + " second(s) till event ends!");

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

		if (Config.CTF_EVEN_TEAMS.equals("NO") || Config.CTF_EVEN_TEAMS.equals("BALANCE"))
		{
			if (_teamPlayersCount.contains(0))
				return false;
		}
		else if (Config.CTF_EVEN_TEAMS.equals("SHUFFLE"))
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
			player._originalNameColorCTF = player.getAppearance().getNameColor();
			player._originalKarmaCTF = player.getKarma();

			_players.add(player);
			_players.get(playersCount)._teamNameCTF = _teams.get(teamCount);
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
			player.getAppearance().setNameColor(_teamColors.get(_teams.indexOf(player._teamNameCTF)));
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
				_log.fine("CTF Engine[finishEvent]: finishEventOk() = false");
			return;
		}

		_started = false;
		unspawnEventNpc();
		unspawnAllFlags();
		processTopTeam();

		if (Config.CTF_ANNOUNCE_TEAM_STATS)
		{
			AnnounceToPlayers(true, _eventName + " Team Statistics:");
			for (String team : _teams)
			{
				int _flags_ = teamFlagCount(team);
				AnnounceToPlayers(true, "Team: " + team + " - Flags taken: " + _flags_);
			}
		}

		teleportFinish();
	}

	private static boolean finishEventOk()
	{
        return _started;
	}

	public static void rewardTeam(String teamName)
	{
		for (L2PcInstance player : _players)
		{
			if (player != null)
			{
				if (player._teamNameCTF.equals(teamName))
				{
					player.addItem("CTF Event: " + _eventName, _rewardId, _rewardAmount, player, true);

					NpcHtmlMessage nhm = new NpcHtmlMessage(5);
					TextBuilder replyMSG = new TextBuilder();

					replyMSG.append("<html><body>Your team wins the event. Look in your inventory for the reward.</body></html>");

					nhm.setHtml(replyMSG.toString());
					player.sendPacket(nhm);

					// Send a Server->Client ActionFailed to the L2PcInstance in order to avoid that the client wait another packet
					player.sendPacket(ActionFailed.STATIC_PACKET);
				}
				if (!player._teamNameCTF.equals(teamName))
				{
					player.addItem("CTF Event: " + _eventName, _giftId, _giftAmount, player, true);

					NpcHtmlMessage nhm = new NpcHtmlMessage(5);
					TextBuilder replyMSG = new TextBuilder();

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
			cleanCTF();
			_joining = false;
			AnnounceToPlayers(true, _eventName + "(CTF): Match aborted!");
			return;
		}
		_joining = false;
		_teleport = false;
		_started = false;
		unspawnEventNpc();
		unspawnAllFlags();
		AnnounceToPlayers(true, _eventName + "(CTF): Match aborted!");
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
			_log.warning(">> CTF Engine infos dump (INACTIVE) <<");
			_log.warning("<<--^----^^-----^----^^------^^----->>");
		}
		else if (_joining && !_teleport && !_started)
		{
			_log.warning("<<--------------------------------->>");
			_log.warning(">> CTF Engine infos dump (JOINING) <<");
			_log.warning("<<--^----^^-----^----^^------^----->>");
		}
		else if (!_joining && _teleport && !_started)
		{
			_log.warning("<<---------------------------------->>");
			_log.warning(">> CTF Engine infos dump (TELEPORT) <<");
			_log.warning("<<--^----^^-----^----^^------^^----->>");
		}
		else if (!_joining && !_teleport && _started)
		{
			_log.warning("<<--------------------------------->>");
			_log.warning(">> CTF Engine infos dump (STARTED) <<");
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
			_log.warning(team + " Flags Taken :" + _teamPointsCount.get(_teams.indexOf(team)));

		if (Config.CTF_EVEN_TEAMS.equals("SHUFFLE"))
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
				_log.warning("Name: " + player.getName() + "   Team: " + player._teamNameCTF + "  Flags :" + player._countCTFflags);
		}

		_log.warning("");
		_log.warning("#####################################################################");
		_log.warning("# _savePlayers(CopyOnWriteArrayList<String>) and _savePlayerTeams(CopyOnWriteArrayList<String>) #");
		_log.warning("#####################################################################");

		for (String player : _savePlayers)
			_log.warning("Name: " + player + "	Team: " + _savePlayerTeams.get(_savePlayers.indexOf(player)));

		_log.warning("");
		_log.warning("");
		_log.warning("**********==CTF==************");
		_log.warning("CTF._teamPointsCount:" + _teamPointsCount.toString());
		_log.warning("CTF._flagIds:" + _flagIds.toString());
		_log.warning("CTF._flagSpawns:" + _flagSpawns.toString());
		_log.warning("CTF._throneSpawns:" + _throneSpawns.toString());
		_log.warning("CTF._flagsTaken:" + _flagsTaken.toString());
		_log.warning("CTF._flagsX:" + _flagsX.toString());
		_log.warning("CTF._flagsY:" + _flagsY.toString());
		_log.warning("CTF._flagsZ:" + _flagsZ.toString());
		_log.warning("************EOF**************");
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
		_teamPointsCount = new CopyOnWriteArrayList<Integer>();
		_teamColors = new CopyOnWriteArrayList<Integer>();
		_teamsX = new CopyOnWriteArrayList<Integer>();
		_teamsY = new CopyOnWriteArrayList<Integer>();
		_teamsZ = new CopyOnWriteArrayList<Integer>();

		_throneSpawns = new CopyOnWriteArrayList<L2Spawn>();
		_flagSpawns = new CopyOnWriteArrayList<L2Spawn>();
		_flagsTaken = new CopyOnWriteArrayList<Boolean>();
		_flagIds = new CopyOnWriteArrayList<Integer>();
		_flagsX = new CopyOnWriteArrayList<Integer>();
		_flagsY = new CopyOnWriteArrayList<Integer>();
		_flagsZ = new CopyOnWriteArrayList<Integer>();

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
		_topScore = 0;
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

			statement = con.prepareStatement("Select * from ctf");
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
				statement = con.prepareStatement("Select * from ctf_teams where teamId = ?");
				statement.setInt(1, index);
				rs = statement.executeQuery();
				while (rs.next())
				{
					_teams.add(rs.getString("teamName"));
					_teamPlayersCount.add(0);
					_teamPointsCount.add(0);
					_teamColors.add(0);
					_teamsX.add(0);
					_teamsY.add(0);
					_teamsZ.add(0);
					_teamsX.set(index, rs.getInt("teamX"));
					_teamsY.set(index, rs.getInt("teamY"));
					_teamsZ.set(index, rs.getInt("teamZ"));
					_teamColors.set(index, rs.getInt("teamColor"));
					_flagsX.add(0);
					_flagsY.add(0);
					_flagsZ.add(0);
					_flagsX.set(index, rs.getInt("flagX"));
					_flagsY.set(index, rs.getInt("flagY"));
					_flagsZ.set(index, rs.getInt("flagZ"));
					_flagSpawns.add(null);
					_flagIds.add(_FlagNPC);
					_flagsTaken.add(false);

				}
				index++;
				statement.close();
			}
		}
		catch (Exception e)
		{
			_log.warning("Exception: CTF.loadData(): " + e.getMessage());
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

			statement = con.prepareStatement("Delete from ctf");
			statement.execute();
			statement.close();

			statement = con
					.prepareStatement("INSERT INTO ctf (eventName, eventDesc, joiningLocation, minlvl, maxlvl, npcId, npcX, npcY, npcZ, npcHeading, rewardId, rewardAmount, giftId, giftAmount, teamsCount, joinTime, eventTime, minPlayers, maxPlayers) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
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

			statement = con.prepareStatement("Delete from ctf_teams");
			statement.execute();
			statement.close();

			for (String teamName : _teams)
			{
				int index = _teams.indexOf(teamName);

				if (index == -1)
					return;
				statement = con
						.prepareStatement("INSERT INTO ctf_teams (teamId ,teamName, teamX, teamY, teamZ, teamColor, flagX, flagY, flagZ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
				statement.setInt(1, index);
				statement.setString(2, teamName);
				statement.setInt(3, _teamsX.get(index));
				statement.setInt(4, _teamsY.get(index));
				statement.setInt(5, _teamsZ.get(index));
				statement.setInt(6, _teamColors.get(index));
				statement.setInt(7, _flagsX.get(index));
				statement.setInt(8, _flagsY.get(index));
				statement.setInt(9, _flagsZ.get(index));
				statement.execute();
				statement.close();
			}
		}
		catch (Exception e)
		{
			_log.warning("Exception: CTF.saveData(): " + e.getMessage());
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
			TextBuilder replyMSG = new TextBuilder();

			replyMSG.append("<html><body>");
			replyMSG.append("CTF Match<br><br><br>");
			replyMSG.append("Current event...<br1>");
			replyMSG.append("   ... description:&nbsp;<font color=\"00FF00\">" + _eventDesc + "</font><br>");
			if (Config.CTF_ANNOUNCE_REWARD)
				replyMSG.append("   ... reward: (" + _rewardAmount + ") " + ItemTable.getInstance().getTemplate(_rewardId).getName() + "<br>");

			if (!_started && !_joining)
				replyMSG.append("<center>Wait till the admin/gm start the participation.</center>");
			else if (Config.CTF_EVEN_TEAMS.equals("SHUFFLE") && !checkMaxPlayers(_playersShuffle.size()))
			{
				if (!CTF._started)
				{
					replyMSG.append("<font color=\"FFFF00\">The event has reached its maximum capacity.</font><br>Keep checking, someone may crit and you can steal their spot.");
				}
			}
			else if (eventPlayer.isCursedWeaponEquipped() && !Config.CTF_JOIN_CURSED)
			{
				replyMSG.append("<font color=\"FFFF00\">You can't participate in this event with a cursed Weapon.</font><br>");
			}
			else if (!_started && _joining && eventPlayer.getLevel() >= _minlvl && eventPlayer.getLevel() <=_maxlvl)
			{
				if (_players.contains(eventPlayer) || checkShufflePlayers(eventPlayer))
				{
					if (Config.CTF_EVEN_TEAMS.equals("NO") || Config.CTF_EVEN_TEAMS.equals("BALANCE"))
						replyMSG.append("You are already participating in team <font color=\"LEVEL\">" + eventPlayer._teamNameCTF + "</font><br><br>");
					else if (Config.CTF_EVEN_TEAMS.equals("SHUFFLE"))
						replyMSG.append("You are already participating!<br><br>");

					replyMSG.append("<table border=\"0\"><tr>");
					replyMSG.append("<td width=\"200\">Wait till event start or</td>");
					replyMSG.append("<td width=\"60\"><center><button value=\"remove\" action=\"bypass -h npc_" + objectId + "_ctf_player_leave\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></center></td>");
					replyMSG.append("<td width=\"100\">your participation!</td>");
					replyMSG.append("</tr></table>");
				}
				else
				{
					replyMSG.append("<td width=\"200\">Your level : <font color=\"00FF00\">" + eventPlayer.getLevel() + "</font></td><br>");
					replyMSG.append("<td width=\"200\">Min level : <font color=\"00FF00\">" + _minlvl + "</font></td><br>");
					replyMSG.append("<td width=\"200\">Max level : <font color=\"00FF00\">" + _maxlvl + "</font></td><br><br>");

					if (Config.CTF_EVEN_TEAMS.equals("NO") || Config.CTF_EVEN_TEAMS.equals("BALANCE"))
					{
						replyMSG.append("<center><table border=\"0\">");

						for (String team : _teams)
						{
							replyMSG.append("<tr><td width=\"100\"><font color=\"LEVEL\">" + team + "</font>&nbsp;(" + teamPlayersCount(team) + " joined)</td>");
							replyMSG.append("<td width=\"60\"><button value=\"Join\" action=\"bypass -h npc_" + objectId + "_ctf_player_join " + team + "\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
						}

						replyMSG.append("</table></center>");
					}
					else if (Config.CTF_EVEN_TEAMS.equals("SHUFFLE"))
					{
						replyMSG.append("<center><table border=\"0\">");

						for (String team : _teams)
							replyMSG.append("<tr><td width=\"100\"><font color=\"LEVEL\">" + team + "</font></td>");

						replyMSG.append("</table></center><br>");

						replyMSG.append("<button value=\"Join\" action=\"bypass -h npc_" + objectId	+ "_ctf_player_join eventShuffle\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
						replyMSG.append("Teams will be randomly generated!");
					}
				}
			}
			else if (_started && !_joining)
				replyMSG.append("<center>CTF match is in progress.</center>");
			else if (eventPlayer.getLevel() < _minlvl || eventPlayer.getLevel() > _maxlvl)
			{
				replyMSG.append("Your lvl : <font color=\"00FF00\">" + eventPlayer.getLevel() + "</font><br>");
				replyMSG.append("Min level : <font color=\"00FF00\">" + _minlvl + "</font><br>");
				replyMSG.append("Max level : <font color=\"00FF00\">" + _maxlvl + "</font><br><br>");
				replyMSG.append("<font color=\"FFFF00\">You can't participatein this event.</font><br>");
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
			_log.warning("CTF Engine[showEventHtlm(" + eventPlayer.getName() + ", " + objectId + ")]: exception" + e.getMessage());
		}
	}

	public static void addPlayer(L2PcInstance player, String teamName)
	{
		if (!addPlayerOk(teamName, player))
			return;

		if (Config.CTF_EVEN_TEAMS.equals("NO") || Config.CTF_EVEN_TEAMS.equals("BALANCE"))
		{
			player._teamNameCTF = teamName;
			_players.add(player);
			setTeamPlayersCount(teamName, teamPlayersCount(teamName) + 1);
		}
		else if (Config.CTF_EVEN_TEAMS.equals("SHUFFLE"))
			_playersShuffle.add(player);

		player.inEventCTF = true;
		player._countCTFflags = 0;
	}

	public static void removeOfflinePlayers()
	{
		try
		{
			if (_playersShuffle != null && !_playersShuffle.isEmpty())
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
			_log.warning("CTF Engine exception: " + e.getMessage());
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
					eventPlayer.inEventCTF = false;
					continue;
				}
				else if (player.getObjectId() == eventPlayer.getObjectId())
				{
					eventPlayer.inEventCTF = true;
					eventPlayer._countCTFflags = 0;
					return true;
				}
				//this 1 is incase player got new objectid after DC or reconnect
				else if (player.getName().equals(eventPlayer.getName()))
				{
					_playersShuffle.remove(player);
					_playersShuffle.add(eventPlayer);
					eventPlayer.inEventCTF = true;
					eventPlayer._countCTFflags = 0;
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
			if (checkShufflePlayers(eventPlayer) || eventPlayer.inEventCTF)
			{
				eventPlayer.sendMessage("You are already participating in the event!");
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
			if (eventPlayer.isInOlympiadMode())
			{
				eventPlayer.sendMessage("You cant join if you are registered in olympiads"); 
				return false;
			}
		}
		catch (Exception e)
		{
			_log.warning("CTF Siege Engine exception: " + e.getMessage());
		}

		if (Config.CTF_EVEN_TEAMS.equals("NO"))
			return true;
		else if (Config.CTF_EVEN_TEAMS.equals("BALANCE"))
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
		else if (Config.CTF_EVEN_TEAMS.equals("SHUFFLE"))
			return true;

		eventPlayer.sendMessage("Too many players in team \"" + teamName + "\"");
		return false;
	}

	public static void addDisconnectedPlayer(L2PcInstance player)
	{
		/*
		 * !!! CAUTION !!!
		 * Do NOT fix multiple object Ids on this event or you will ruin the flag reposition check!!!
		 * All Multiple object Ids will be collected by the Garbage Collector, after the event ends, memory sweep is made!!!
		 */

		if ((Config.CTF_EVEN_TEAMS.equals("SHUFFLE") && (_teleport || _started))
				|| (Config.CTF_EVEN_TEAMS.equals("NO") || Config.CTF_EVEN_TEAMS.equals("BALANCE") && (_teleport || _started)))
		{
			if (Config.CTF_ON_START_REMOVE_ALL_EFFECTS)
			{
				for (L2Effect e : player.getAllEffects())
				{
					if (e != null)
						e.exit();
				}
			}

			player._teamNameCTF = _savePlayerTeams.get(_savePlayers.indexOf(player.getName()));
			for (L2PcInstance p : _players)
			{
				if (p == null)
				{
					continue;
				}
				//check by name incase player got new objectId
				else if (p.getName().equals(player.getName()))
				{
					player._originalNameColorCTF = player.getAppearance().getNameColor();
					player._originalKarmaCTF = player.getKarma();
					player.inEventCTF = true;
					player._countCTFflags = p._countCTFflags;
					_players.remove(p); //removing old object id from CopyOnWriteArrayList
					_players.add(player); //adding new objectId to CopyOnWriteArrayList
					break;
				}
			}
			player.getAppearance().setNameColor(_teamColors.get(_teams.indexOf(player._teamNameCTF)));
			player.setKarma(0);
			if (_teams.size() >= 2)
				player.setTeam(_teams.indexOf(player._teamNameTvT) + 1);
			player.broadcastUserInfo();
			player.teleToLocation(_teamsX.get(_teams.indexOf(player._teamNameCTF)), _teamsY.get(_teams.indexOf(player._teamNameCTF)), _teamsZ.get(_teams
					.indexOf(player._teamNameCTF)));
			Started(player);
			CheckRestoreFlags();
		}
	}

	public static void removePlayer(L2PcInstance player)
	{
		if (player.inEventCTF)
		{
			if (!_joining)
			{
				player.getAppearance().setNameColor(player._originalNameColorCTF);
				player.setKarma(player._originalKarmaCTF);
				if (_teams.size() >= 2)
					player.setTeam(0);
				player.broadcastUserInfo();
			}
			player._teamNameCTF = new String();
			player._countCTFflags = 0;
			player.inEventCTF = false;

			if ((Config.CTF_EVEN_TEAMS.equals("NO") || Config.CTF_EVEN_TEAMS.equals("BALANCE")) && _players.contains(player))
			{
				setTeamPlayersCount(player._teamNameCTF, teamPlayersCount(player._teamNameCTF) - 1);
				_players.remove(player);
			}
			else if (Config.CTF_EVEN_TEAMS.equals("SHUFFLE") && (!_playersShuffle.isEmpty() && _playersShuffle.contains(player)))
				_playersShuffle.remove(player);
		}
	}

	public static void cleanCTF()
	{
		_log.warning("CTF : Cleaning players.");
		for (L2PcInstance player : _players)
		{
			if (player != null)
			{
				if (player._haveFlagCTF)
					removeFlagFromPlayer(player);
				else
					player.getInventory().destroyItemByItemId("", CTF._FLAG_IN_HAND_ITEM_ID, 1, player, null);
				player._haveFlagCTF = false;
				removePlayer(player);
				if (_savePlayers.contains(player.getName()))
					_savePlayers.remove(player.getName());
				player.inEventCTF = false;
			}
		}
		if (_playersShuffle != null && !_playersShuffle.isEmpty())
		{
			for (L2PcInstance player : _playersShuffle)
			{
				if (player != null)
					player.inEventCTF = false;
			}
		}
		_log.warning("CTF : Cleaning teams and flags.");
		for (String team : _teams)
		{
			int index = _teams.indexOf(team);
			_teamPointsCount.set(index, 0);
			_flagSpawns.set(index, null);
			_flagsTaken.set(index, false);
			_teamPlayersCount.set(index, 0);
			_teamPointsCount.set(index, 0);
		}
		_topScore = 0;
		_topTeam = new String();
		_players = new CopyOnWriteArrayList<L2PcInstance>();
		_playersShuffle = new CopyOnWriteArrayList<L2PcInstance>();
		_savePlayers = new CopyOnWriteArrayList<String>();
		_savePlayerTeams = new CopyOnWriteArrayList<String>();
		_teamPointsCount = new CopyOnWriteArrayList<Integer>();
		_flagSpawns = new CopyOnWriteArrayList<L2Spawn>();
		_flagsTaken = new CopyOnWriteArrayList<Boolean>();
		_teamPlayersCount = new CopyOnWriteArrayList<Integer>();
		_log.warning("Cleaning CTF done.");
		_log.warning("Loading new data from MySql");
		loadData();
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
		AnnounceToPlayers(false, _eventName + "(CTF): Teleport back to participation NPC in 20 seconds!");
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
								_log.warning("CTF Engine exception: " + se.getMessage());
							}
							finally
							{
								try { con.close(); } catch (Exception e) {}
							}
						}
					}
				}
				cleanCTF();
			}
		}, 20000);
	}

	public static int teamFlagCount(String teamName)
	{
		int index = _teams.indexOf(teamName);

		if (index == -1)
			return -1;

		return _teamPointsCount.get(index);
	}

	public static void setTeamFlagCount(String teamName, int teamFlagCount)
	{
		int index = _teams.indexOf(teamName);

		if (index == -1)
			return;

		_teamPointsCount.set(index, teamFlagCount);
	}

	/**
	 * Used to calculate the event CTF area, so that players don't run off with the flag.
	 * Essential, since a player may take the flag just so other teams can't score points.
	 * This function is Only called upon ONE time on BEGINING OF EACH EVENT right after we spawn the flags.
	 */
	private static void calculateOutSideOfCTF()
	{
		if (_teams == null || _flagSpawns == null || _teamsX == null || _teamsY == null || _teamsZ == null)
			return;

		int division = _teams.size() * 2, pos = 0;
		int[] locX = new int[division], locY = new int[division], locZ = new int[division];
		//Get all coordinates inorder to create a polygon:
		for (L2Spawn flag : _flagSpawns)
		{
			locX[pos] = flag.getLocx();
			locY[pos] = flag.getLocy();
			locZ[pos] = flag.getLocz();
			pos++;
			if (pos > division / 2)
				break;
		}

		for (int x = 0; x < _teams.size(); x++)
		{
			locX[pos] = _teamsX.get(x);
			locY[pos] = _teamsY.get(x);
			locZ[pos] = _teamsZ.get(x);
			pos++;
			if (pos > division)
				break;
		}

		//find the polygon center, note that it's not the mathematical center of the polygon, 
		//rather than a point which centers all coordinates:
		int centerX = 0, centerY = 0, centerZ = 0;
		for (int x = 0; x < pos; x++)
		{
			centerX += (locX[x] / division);
			centerY += (locY[x] / division);
			centerZ += (locZ[x] / division);
		}

		//now let's find the furthest distance from the "center" to the egg shaped sphere 
		//surrounding the polygon, size x1.5 (for maximum logical area to wander...):
		int maxX = 0, maxY = 0, maxZ = 0;
		for (int x = 0; x < pos; x++)
		{
			if (maxX < 2 * Math.abs(centerX - locX[x]))
				maxX = (2 * Math.abs(centerX - locX[x]));
			if (maxY < 2 * Math.abs(centerY - locY[x]))
				maxY = (2 * Math.abs(centerY - locY[x]));
			if (maxZ < 2 * Math.abs(centerZ - locZ[x]))
				maxZ = (2 * Math.abs(centerZ - locZ[x]));
		}

		//centerX,centerY,centerZ are the coordinates of the "event center".
		//so let's save those coordinates to check on the players:
		eventCenterX = centerX;
		eventCenterY = centerY;
		eventCenterZ = centerZ;
		eventOffset = maxX;
		if (eventOffset < maxY)
			eventOffset = maxY;
		if (eventOffset < maxZ)
			eventOffset = maxZ;
	}

	public static boolean isOutsideCTFArea(L2PcInstance _player)
	{
		if (_player == null || !_player.isOnline())
			return true;
        return !(_player.getX() > eventCenterX - eventOffset && _player.getX() < eventCenterX + eventOffset && _player.getY() > eventCenterY - eventOffset
                && _player.getY() < eventCenterY + eventOffset && _player.getZ() > eventCenterZ - eventOffset && _player.getZ() < eventCenterZ + eventOffset);
		}
}
