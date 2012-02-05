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
 * @author SqueezeD
 
 * Reworked & Improved by Blazer:
 *  Added "Auto Event" feature.
*  Added JoinTime & EventTime.
*  Added Full HP/MP/CP recovery when player revived.
*  Added min players and max players that can join the event.
*  Added "Kills:" to players title, so each player would know how many kills he has.
*  Reworked the clean event code, so players are CLEANED after the event is over.
*  Misc cleanup and updates.
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

public class DM
{   
	private final static Logger		_log		= Logger.getLogger(DM.class.getName());
	public static String _eventName = "",
						 _eventDesc = "",
						 _joiningLocationName = "";
	public static CopyOnWriteArrayList<String> _savePlayers = new CopyOnWriteArrayList<String>();
	public static boolean _joining = false,
						  _teleport = false,
						  _started = false,
						  _sitForced = false;
	public static L2Spawn _npcSpawn;
	public static CopyOnWriteArrayList<L2PcInstance>	_players	= new CopyOnWriteArrayList<L2PcInstance>();
	public static int					_top = 0;
	public static L2PcInstance			_topPlayer;

	public static int _npcId = 0,
					  _npcX = 0,
					  _npcY = 0,
					  _npcZ = 0,
					  _npcHeading = 0,
					  _rewardId = 0,
					  _rewardAmount = 0,
					  _finalAmount = 0,
					  _joinTime	= 0,
					  _eventTime = 0,
					  _minPlayers = 0,
					  _maxPlayers = 0,
					  _topKills = 0,
					  _minlvl = 0,
					  _maxlvl = 0,
					  _playerColors = 0,
					  _radius = 0,
					  _playerX = 0,
					  _playerY = 0,
					  _playerZ = 0;
					  
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
					  
	public static void setNpcPos(L2PcInstance activeChar)
	{
		_npcX = activeChar.getX();
		_npcY = activeChar.getY();
		_npcZ = activeChar.getZ();
		_npcHeading = activeChar.getHeading();
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

	public static void setPlayersPos(L2PcInstance activeChar)
	{
		_playerX = activeChar.getX();
		_playerY = activeChar.getY();
		_playerZ = activeChar.getZ();
	}

	public static boolean checkPlayerOk()
	{
        return !(_started || _teleport || _joining);
	}

	public static void startJoin(L2PcInstance activeChar)
	{
		if (!startJoinOk())
		{
			if (Config.DEBUG)
			_log.fine("DM Engine[startJoin(" + activeChar.getName() + ")]: startJoinOk() = false");
			return;
		}
		
		_joining = true;
		spawnEventNpc(activeChar);
		Announcements.getInstance().announceToAll(_eventName + "Death Match is joinable in " + _joiningLocationName + "!");
	}

	public static boolean startAutoJoin()
	{
		if (!startJoinOk())
		{
			if (Config.DEBUG)
				_log.fine("DM Engine[startJoin]: startJoinOk() = false");
			return false;
		}

		_joining = true;
		spawnEventNpc();
		AnnounceToPlayers(true, _eventName);
		AnnounceToPlayers(true, "Reward: " + _rewardAmount + " " + ItemTable.getInstance().getTemplate(_rewardId).getName() + " Per Kill.");
		AnnounceToPlayers(true, "Recruiting levels " + _minlvl + " to " + _maxlvl + ".");
		AnnounceToPlayers(true, "Joinable in " + _joiningLocationName + ".");
		return true;
	}

	private static boolean startJoinOk()
	{
        return !(_started || _teleport || _joining || _eventName.isEmpty() ||
                _joiningLocationName.isEmpty() || _eventDesc.isEmpty() || _npcId == 0 ||
                _npcX == 0 || _npcY == 0 || _npcZ == 0 || _rewardId == 0 || _rewardAmount == 0 || _radius==0 || 
                _playerX == 0 || _playerY == 0 || _playerZ == 0);
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
			_npcSpawn.getLastSpawn().isEventMobDM = true;
			_npcSpawn.getLastSpawn().isAggressive();
			_npcSpawn.getLastSpawn().decayMe();
			_npcSpawn.getLastSpawn().spawnMe(_npcSpawn.getLastSpawn().getX(), _npcSpawn.getLastSpawn().getY(), _npcSpawn.getLastSpawn().getZ());
			_npcSpawn.getLastSpawn().broadcastPacket(new MagicSkillUse(_npcSpawn.getLastSpawn(), _npcSpawn.getLastSpawn(), 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			if (Config.DEBUG)
			_log.fine("DM Engine[spawnEventNpc(" + activeChar.getName() + ")]: exception: " + e.getMessage());
			return;
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
			_npcSpawn.getLastSpawn().isEventMobDM = true;
			_npcSpawn.getLastSpawn().isAggressive();
			_npcSpawn.getLastSpawn().decayMe();
			_npcSpawn.getLastSpawn().spawnMe(_npcSpawn.getLastSpawn().getX(), _npcSpawn.getLastSpawn().getY(), _npcSpawn.getLastSpawn().getZ());
			_npcSpawn.getLastSpawn().broadcastPacket(new MagicSkillUse(_npcSpawn.getLastSpawn(), _npcSpawn.getLastSpawn(), 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			if (Config.DEBUG)
			_log.warning("DM Engine[spawnEventNpc(exception: " + e.getMessage());
			return;
		}
	}
	
	public static void teleportStart()
	{
		if (!_joining || _started || _teleport)
			return;
			
		removeOfflinePlayers();
		if (!checkMinPlayers(_players.size()))
		{
			AnnounceToPlayers(true, "Not enough players for event. Min Requested : " + _minPlayers + ", Participating : " + _players.size());
			return;
		}
		
		_joining = false;
		Announcements.getInstance().announceToAll(_eventName + "Teleport to team spot in 10 seconds!");

		setUserData();
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable()
		{
			public void run()
			{
				DM.sit();

				for (L2PcInstance player : DM._players)
				{
					if (player !=  null)
					{
						if (Config.DM_ON_START_UNSUMMON_PET)
						{
							//Remove Summon's buffs
							if (player.getPet() != null)
							{
								L2Summon summon = player.getPet();
								summon.stopAllEffects();

								if (summon instanceof L2PetInstance)
									summon.unSummon(player);
							}
						}
						//player.getAppearance().setVisibleTitle("Kills: " + player._countDMkills);
						player.teleToLocation((_playerX + Rnd.get(-(_radius), +(_radius))), (_playerY + Rnd.get(-(_radius), +(_radius))), _playerZ, false);
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
		AnnounceToPlayers(false, "Teleport to team spot in 10 seconds!");

		setUserData();
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable()
		{
			public void run()
			{
				DM.sit();

				for (L2PcInstance player : DM._players)
				{
					if (player !=  null)
					{
						if (Config.DM_ON_START_UNSUMMON_PET)
						{
							//Remove Summon's buffs
							if (player.getPet() != null)
							{
								L2Summon summon = player.getPet();
								summon.stopAllEffects();

								if (summon instanceof L2PetInstance)
									summon.unSummon(player);
							}
						}
						//player.getAppearance().setVisibleTitle("Kills: " + player._countDMkills);
						player.teleToLocation((_playerX + Rnd.get(-(_radius), +(_radius))), (_playerY + Rnd.get(-(_radius), +(_radius))), _playerZ, false);
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
			_log.fine("DM Engine[startEvent(" + activeChar.getName() + ")]: startEventOk() = false");
			return;
		}
		
		_teleport = false;
		
		for (L2PcInstance player : DM._players)
		// Remove player from his party
		if (player.getParty() != null)
		{
			L2Party party = player.getParty();
			party.removePartyMember(player);
		}
		
		for (L2PcInstance player : _players)
		if (Config.DM_ON_START_REMOVE_ALL_EFFECTS)
		{
			player.stopAllEffects();
		}
		
		sit();
		Announcements.getInstance().announceToAll(_eventName + "DM Started: Kill them all!");
		_started = true;
	}
	private static boolean startEventOk()
	{
        return !(_joining || !_teleport || _started);
	}

	public static void setUserData()
	{
		for (L2PcInstance player : _players)
		{
			player._originalNameColorDM = player.getAppearance().getNameColor();
			player._originalTitleColorDM = player.getAppearance().getTitleColor();
			player._originalKarmaDM = player.getKarma();
			player.inEventDM = true;
			player._countDMkills = 0;
			player.getAppearance().setNameColor(_playerColors);
			player.getAppearance().setTitleColor(_playerColors);
			player.setKarma(0);
			player.setTeam(2);
			player.broadcastUserInfo();
		}
	}

	public static void removeUserData()
	{
		for (L2PcInstance player : _players)
		{
			player.getAppearance().setNameColor(player._originalNameColorDM);
			player.getAppearance().setTitleColor(player._originalTitleColorDM);
			player.setTitle(player._originalTitleDM);
			player.setKarma(player._originalKarmaDM);
			player.inEventDM = false;
			player._countDMkills = 0;
			player.broadcastUserInfo();
		}
	}

	public static void finishEvent()
	{
		if (!finishEventOk())
		{
			if (Config.DEBUG)
			_log.fine("DM Engine[finishEvent]: finishEventOk() = false");
			return;
		}

		_started = false;
		unspawnEventNpc();
		processTopPlayer();

		if (_top  == 0)
			Announcements.getInstance().announceToAll(_eventName + "Death Match: No players won the match(nobody killed).");
		else
		{
			Announcements.getInstance().announceToAll(_topPlayer.getName() + " is the Top Killer in Death Match with " + _top + " kills.");
			rewardPlayer(_topPlayer);
		}
		
		teleportFinish();
	}

	private static boolean finishEventOk()
	{
        return _started;
	}
	public static void processTopPlayer()
	{
		//top 1
		for (L2PcInstance player : _players)
		{
			if (player._countDMkills > _top)
			{
				_topPlayer = player;
				_top = player._countDMkills;
			}
		}
	}
	/**
	 * @param activeChar  
	 */
	public static void rewardPlayer(L2PcInstance activeChar)
	{
		for (L2PcInstance player : _players)
		if (player != null && player._countDMkills > 0)
		{
			_finalAmount = _rewardAmount * player._countDMkills;
			player.addItem("DM Event: " + _eventName, _rewardId, _finalAmount, player, true);

			NpcHtmlMessage nhm = new NpcHtmlMessage(5);
			final StringBuilder replyMSG = StringUtil.startAppend(1000, "<html><body>");

			replyMSG.append("<html><body>You won. Look in your inventory for the reward.</body></html>");

			nhm.setHtml(replyMSG.toString());
			player.sendPacket(nhm);

			// Send a Server->Client ActionFailed to the L2PcInstance in order to avoid that the client wait another packet
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
	}

	public static void abortEvent()
	{
		if (!_joining && !_teleport && !_started)
			return;
		
		_joining = false;
		_teleport = false;
		_started = false;
		unspawnEventNpc();
		Announcements.getInstance().announceToAll(_eventName + "(DM): Match aborted!");
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
				
				// Noblesse all players before starting the event.
				L2Skill skill = SkillTable.getInstance().getInfo(1323,1);
				if (skill != null)
					skill.getEffects(player, player);
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
			_log.warning(">> DM Engine infos dump (INACTIVE) <<");
			_log.warning("<<--^----^^-----^----^^------^^----->>");
		}
		else if (_joining && !_teleport && !_started)
		{
			_log.warning("<<--------------------------------->>");
			_log.warning(">> DM Engine infos dump (JOINING) <<");
			_log.warning("<<--^----^^-----^----^^------^----->>");
		}
		else if (!_joining && _teleport && !_started)
		{
			_log.warning("<<---------------------------------->>");
			_log.warning(">> DM Engine infos dump (TELEPORT) <<");
			_log.warning("<<--^----^^-----^----^^------^^----->>");
		}
		else if (!_joining && !_teleport && _started)
		{
			_log.warning("<<--------------------------------->>");
			_log.warning(">> DM Engine infos dump (STARTED) <<");
			_log.warning("<<--^----^^-----^----^^------^----->>");
		}

		_log.warning("Name: " + _eventName);
		_log.warning("Desc: " + _eventDesc);
		_log.warning("Join location: " + _joiningLocationName);
		_log.warning("Min lvl: " + _minlvl);
		_log.warning("Max lvl: " + _maxlvl);
		
		_log.warning("");
		_log.warning("##################################");
		_log.warning("# _players(CopyOnWriteArrayList<L2PcInstance>) #");
		_log.warning("##################################");
		
		_log.warning("Total Players : " + _players.size());
		
		for (L2PcInstance player : _players)
		{
			if (player != null)
				_log.warning("Name: " + player.getName()+ " kills :" + player._countDMkills);
		}
		
		_log.warning("");
		_log.warning("################################");
		_log.warning("# _savePlayers(CopyOnWriteArrayList<String>) #");
		_log.warning("################################");
		
		for (String player : _savePlayers)
			_log.warning("Name: " + player );
		
		_log.warning("");
		_log.warning("");
	}

	public static void loadData()
	{
		_eventName = "";
		_eventDesc = "";
		_joiningLocationName = "";
		_savePlayers = new CopyOnWriteArrayList<String>();
		_players = new CopyOnWriteArrayList<L2PcInstance>();
		_topPlayer = null;
		_npcSpawn = null;
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
		_joinTime = 0;
		_eventTime = 0;
		_minPlayers = 0;
		_maxPlayers = 0;
		_minlvl = 0;
		_maxlvl = 0;
		_playerColors = 0;
		_radius = 0;
		_playerX = 0;
		_playerY = 0;
		_playerZ = 0;
										
		Connection con = null;
		try
		{
			PreparedStatement statement;
			ResultSet rs;

			con = L2DatabaseFactory.getInstance().getConnection();

			statement = con.prepareStatement("Select * from dm");
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
				_rewardId = rs.getInt("rewardId");
				_rewardAmount = rs.getInt("rewardAmount");
				_joinTime = rs.getInt("joinTime");
				_eventTime = rs.getInt("eventTime");
				_minPlayers = rs.getInt("minPlayers");
				_maxPlayers = rs.getInt("maxPlayers");
				_playerColors = rs.getInt("color");
				_radius = rs.getInt("radius");
				_playerX = rs.getInt("playerX");
				_playerY = rs.getInt("playerY");
				_playerZ = rs.getInt("playerZ");			
			}
			statement.close();
		}
		catch (Exception e)
		{
			_log.warning("Exception: DM.loadData(): " + e.getMessage());
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
			
			statement = con.prepareStatement("Delete from dm");
			statement.execute();
			statement.close();

			statement = con.prepareStatement("INSERT INTO dm (eventName, eventDesc, joiningLocation, minlvl, maxlvl, npcId, npcX, npcY, npcZ, npcHeading, rewardId, rewardAmount, joinTime, eventTime, minPlayers, maxPlayers, color, radius, playerX, playerY, playerZ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");  
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
			statement.setInt(13, _joinTime);
			statement.setInt(14, _eventTime);
			statement.setInt(15, _minPlayers);
			statement.setInt(16, _maxPlayers);
			statement.setInt(17, _playerColors);
			statement.setInt(18, _radius);
			statement.setInt(19, _playerX);
			statement.setInt(20, _playerY);
			statement.setInt(21, _playerZ);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warning("Exception: DM.saveData(): " + e.getMessage());
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

			replyMSG.append("Event Name:&nbsp;<font color=\"00FF00\">" + _eventName + "</font><br1>");
			replyMSG.append("Event Description:&nbsp;<font color=\"00FF00\">" + _eventDesc + "</font><br><br>");

			if (!_started && !_joining)
				replyMSG.append("<center>Wait till the admin/gm start the participation.</center>");
			else if (!_started && _joining && eventPlayer.getLevel()>=_minlvl && eventPlayer.getLevel()<=_maxlvl)
			{
				if (_players.contains(eventPlayer))
				{
					replyMSG.append("You are already participating!<br>");
					replyMSG.append("There are " + _players.size() + " player(s) participating in this event.<br><br>");
					replyMSG.append("<table border=\"0\"><tr>");
					replyMSG.append("<td width=\"60\"><center><button value=\"remove\" action=\"bypass -h npc_" + objectId + "_dmevent_player_leave\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></center></td>");
					replyMSG.append("<td width=\"120\">your participation!?<br></td>");
					replyMSG.append("</tr></table>");
				}
				else
				{
					replyMSG.append("You want to participate in the event?<br><br>");
					replyMSG.append("<td width=\"200\">Admin set min lvl : <font color=\"00FF00\">" + _minlvl + "</font></td><br>");
					replyMSG.append("<td width=\"200\">Admin set max lvl : <font color=\"00FF00\">" + _maxlvl + "</font></td><br><br>");
					replyMSG.append("<br>There are " + _players.size() + " player(s) participating in this event.<br>");
					replyMSG.append("<button value=\"Join\" action=\"bypass -h npc_" + objectId + "_dmevent_player_join\" width=50 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
					
				}
			}
			else if (_started && !_joining)
			{
				replyMSG.append("<center>DM match is in progress.</center>");
				replyMSG.append("<br>There are " + _players.size() + " players participating in this event.<br>");
			}
			else if (eventPlayer.getLevel()<_minlvl || eventPlayer.getLevel()>_maxlvl )
			{
				replyMSG.append("Your lvl : <font color=\"00FF00\">" + eventPlayer.getLevel() +"</font><br>");
				replyMSG.append("Admin set min lvl : <font color=\"00FF00\">" + _minlvl + "</font><br>");
				replyMSG.append("Admin set max lvl : <font color=\"00FF00\">" + _maxlvl + "</font><br><br>");
				replyMSG.append("<font color=\"FFFF00\">You can't participate to this event.</font><br>");
			}
			
			else if (!_started && _players.size() > _maxPlayers)
			{
				replyMSG.append("<font color=\"FFFF00\">The event has reached its maximum capacity.</font><br>Keep checking, someone may leave and you can steal their spot.");
			}
			replyMSG.append("</body></html>");
			adminReply.setHtml(replyMSG.toString());
			eventPlayer.sendPacket(adminReply);

			// Send a Server->Client ActionFailed to the L2PcInstance in order to avoid that the client wait another packet
			eventPlayer.sendPacket(ActionFailed.STATIC_PACKET);
		}
		catch (Exception e)
		{
			_log.warning("DM Engine[showEventHtlm(" + eventPlayer.getName() + ", " + objectId + ")]: exception" + e.getMessage());
		}
	}

	public static void addPlayer(L2PcInstance player)
	{
		if (!addPlayerOk(player))
			return;
			
		_players.add(player);
		player._originalNameColorDM = player.getAppearance().getNameColor();
		player._originalTitleColorDM = player.getAppearance().getTitleColor();
		player._originalTitleDM = player.getTitle();
		player._originalKarmaDM = player.getKarma();
		player.inEventDM = true;
		player._countDMkills = 0;
		_savePlayers.add(player.getName());
		
	}

	public static boolean addPlayerOk(L2PcInstance eventPlayer)
	{
		if (eventPlayer.inEventCTF || eventPlayer.inEventRaid || eventPlayer.inEventTvT)
		{
			eventPlayer.sendMessage("You are already participating in another event!");
			return false;
		}
		if (eventPlayer.isInOlympiadMode())
		{
			eventPlayer.sendMessage("You cant join if you are registered in olympiads"); 
			return false;
		}
		return true;
	}

	public static synchronized void addDisconnectedPlayer(L2PcInstance player)
	{
		if ((_teleport || _started))
		{
			if (Config.DM_ON_START_REMOVE_ALL_EFFECTS)
			{
				player.stopAllEffects();
			}
			for (L2PcInstance p : _players)
			{
				if (p==null)
				{
					continue;
				}
				//check by name incase player got new objectId
				else if (p.getName().equals(player.getName()))
				{
					player._originalNameColorDM = player.getAppearance().getNameColor();
					player._originalTitleColorDM = player.getAppearance().getTitleColor();
					player._originalKarmaDM = player.getKarma();
					player.inEventDM = true;
					player._countDMkills =p._countDMkills;
					_players.remove(p); //removing old object id from CopyOnWriteArrayList
					_players.add(player); //adding new objectId to CopyOnWriteArrayList
					break;
				}
			}
			
			player.getAppearance().setNameColor(_playerColors);
			player.getAppearance().setTitleColor(_playerColors);
			player.setKarma(0);
			player.setTeam(2);
			player.broadcastUserInfo();
			player.teleToLocation((_playerX + Rnd.get(-(_radius), +(_radius))), (_playerY + Rnd.get(-(_radius), +(_radius))), _playerZ, false);
		}
	}
	
	public static void removePlayer(L2PcInstance player)
	{
		if (player.inEventDM)
		{
			if (!_joining)
			{
				player.getAppearance().setNameColor(player._originalNameColorDM);
				player.getAppearance().setTitleColor(player._originalTitleColorDM);
				player.setTitle(player._originalTitleDM);
				player.setKarma(player._originalKarmaDM);
				player.setTeam(0);
				player.broadcastUserInfo();
			}
			player.inEventDM = false;
			_players.remove(player);
		}
	}
	
	public static void cleanDM()
	{
		_log.warning("DM: Cleaning players.");
		for (L2PcInstance player : _players)
		{
			if (player != null)
			{
				removePlayer(player);
				if (_savePlayers.contains(player.getName()))
					_savePlayers.remove(player.getName());
				player.inEventDM = false;
			}
		}

		_topPlayer = null;
		_npcSpawn = null;
		_joining = false;
		_teleport = false;
		_started = false;
		_sitForced = false;
		_top = 0;
		_players = new CopyOnWriteArrayList<L2PcInstance>();
		_savePlayers = new CopyOnWriteArrayList<String>();
		_log.warning("Cleaning DM done.");
		
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
				_log.fine("DM Engine[startEvent]: startEventOk() = false");
			return false;
		}

		_teleport = false;
		
		for (L2PcInstance player : DM._players)
		// Remove player from his party
		if (player.getParty() != null)
		{
			L2Party party = player.getParty();
			party.removePartyMember(player);
		}
		
		for (L2PcInstance player : _players)
		if (Config.DM_ON_START_REMOVE_ALL_EFFECTS)
		{
			for (L2Effect e : player.getAllEffects())
			{
				if (e != null)
				e.exit();
			}
		}
		
		sit();
		AnnounceToPlayers(false, "Go kill them all!");
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
						AnnounceToPlayers(true, "Death Match: Joinable in " + _joiningLocationName + ".");
						AnnounceToPlayers(true, seconds / 60 / 60 + " hours till DM registration ends.");

					}
					else if (_started)
						AnnounceToPlayers(false, seconds / 60 / 60 + " hours till DM event ends.");

					break;
				case 1800: // 30 minutes left
				case 900: // 15 minutes left
				case 600: //  10 minutes left 
				case 300: // 5 minutes left
				case 60: // 1 minute left
					if (_joining)
					{
						removeOfflinePlayers();
						AnnounceToPlayers(true, "Death Match is joinable in " + _joiningLocationName + ".");
						AnnounceToPlayers(true, seconds / 60 + " minutes till registration ends.");
					}
					
					else if (_started)
						AnnounceToPlayers(true, seconds / 60 + " minutes till Death Match ends!");
					break;
				case 30: // 30 seconds left
				case 10: // 10 seconds left
				case 3: // 3 seconds left
				case 2: // 2 seconds left
				case 1: // 1 seconds left
					if (_joining)
						AnnounceToPlayers(true, seconds + " seconds till DM registration ends!");
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
		AnnounceToPlayers(false, "Teleport back to participation NPC in 10 seconds!");

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
								_log.warning("DM Engine exception: " + se.getMessage());
							}
							finally
							{
								try { con.close(); } catch (Exception e) {}
							}
						}
					}
				}
				_log.warning("DM: Teleport done.");
				cleanDM();
			}
		}, 10000);
	}
}
