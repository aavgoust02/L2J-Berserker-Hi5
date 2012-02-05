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
package handlers.admincommandhandlers;

/**
 *
 * @author: HanWik
 *
 */
import java.util.StringTokenizer;

import com.l2jserver.gameserver.ThreadPoolManager;
import com.l2jserver.gameserver.handler.IAdminCommandHandler;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.entity.events.Raid;
import com.l2jserver.gameserver.network.serverpackets.NpcHtmlMessage;

import com.l2jserver.util.StringUtil;

public class AdminRaidEngine implements IAdminCommandHandler
{

	private static final String[]	ADMIN_COMMANDS	=
													{
			"admin_raid",
			"admin_rai_name",
			"admin_rai_desc",
			"admin_rai_join_loc",
			"admin_rai_minlvl",
			"admin_rai_maxlvl",
			"admin_rai_npc",
			"admin_rai_npc_pos",
			"admin_rai_bos",
			"admin_rai_boss_pos",
			"admin_rai_reward",
			"admin_rai_reward_amount",
			"admin_rai_team_pos",
			"admin_rai_join",
			"admin_rai_teleport",
			"admin_rai_start",
			"admin_rai_abort",
			"admin_rai_finish",
			"admin_rai_sit",
			"admin_rai_save",
			"admin_rai_load",
			"admin_rai_jointime",
			"admin_rai_eventtime",
			"admin_rai_autoevent",
			"admin_rai_minplayers",
			"admin_rai_maxplayers",
			"admin_raidkick"						};

	private static final int		REQUIRED_LEVEL	= 127;

	public boolean useAdminCommand(String command, L2PcInstance activeChar)
	{
		if (command.equals("admin_raid"))
			showMainPage(activeChar);
		else if (command.startsWith("admin_rai_name "))
		{
			Raid._eventName = command.substring(15);
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_rai_desc "))
		{
			Raid._eventDesc = command.substring(15);
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_rai_minlvl "))
		{
			if (!Raid.checkMinLevel(Integer.valueOf(command.substring(17))))
				return false;
			Raid._minlvl = Integer.valueOf(command.substring(17));
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_rai_maxlvl "))
		{
			if (!Raid.checkMaxLevel(Integer.valueOf(command.substring(17))))
				return false;
			Raid._maxlvl = Integer.valueOf(command.substring(17));
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_rai_minplayers "))
		{
			Raid._minPlayers = Integer.valueOf(command.substring(21));
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_rai_maxplayers "))
		{
			Raid._maxPlayers = Integer.valueOf(command.substring(21));
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_rai_join_loc "))
		{
			Raid._joiningLocationName = command.substring(19);
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_rai_npc "))
		{
			Raid._npcId = Integer.valueOf(command.substring(14));
			showMainPage(activeChar);
		}
		else if (command.equals("admin_rai_npc_pos"))
		{
			Raid.setNpcPos(activeChar);
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_rai_bos "))
		{
			Raid._bossId = Integer.valueOf(command.substring(14));
			showMainPage(activeChar);
		}
		else if (command.equals("admin_rai_boss_pos"))
		{
			Raid.setBossPos(activeChar);
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_rai_reward "))
		{
			Raid._rewardId = Integer.valueOf(command.substring(17));
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_rai_reward_amount "))
		{
			Raid._rewardAmount = Integer.valueOf(command.substring(24));
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_rai_jointime "))
		{
			Raid._joinTime = Integer.valueOf(command.substring(19));
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_rai_eventtime "))
		{
			Raid._eventTime = Integer.valueOf(command.substring(20));
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_rai_team_pos "))
		{
			Raid.setTeamPos(activeChar);
			showMainPage(activeChar);
		}
		else if (command.equals("admin_rai_join"))
		{
			Raid.startJoin(activeChar);
			showMainPage(activeChar);
		}
		else if (command.equals("admin_rai_teleport"))
		{
			Raid.teleportStart();
			showMainPage(activeChar);
		}
		else if (command.equals("admin_rai_start"))
		{
			Raid.startEvent(activeChar);
			showMainPage(activeChar);
		}
		else if (command.equals("admin_rai_abort"))
		{
			activeChar.sendMessage("Aborting event");
			Raid.abortEvent();
			showMainPage(activeChar);
		}
		else if (command.equals("admin_rai_finish"))
		{
			Raid.finishEvent();
			showMainPage(activeChar);
		}
		else if (command.equals("admin_rai_sit"))
		{
			Raid.sit();
			showMainPage(activeChar);
		}
		else if (command.equals("admin_rai_load"))
		{
			Raid.loadData();
			showMainPage(activeChar);
		}
		else if (command.equals("admin_rai_autoevent"))
		{
			if (!Raid._started)
			{
				if (Raid._joinTime > 0 && Raid._eventTime > 0)
				{
					ThreadPoolManager.getInstance().scheduleGeneral(new Runnable()
					{
						public void run()
						{
							Raid.autoEvent();
						}
					}, 0);
				}
				else
					activeChar.sendMessage("Wrong usege: join time or event time invalid.");
			}
			else
				activeChar.sendMessage("A Raid Event has already been started.");
			showMainPage(activeChar);
		}
		
		else if (command.equals("admin_rai_save"))
		{
			Raid.saveData();
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_raidkick"))
		{
			StringTokenizer st = new StringTokenizer(command);
			if (st.countTokens() > 1)
			{
				st.nextToken();
				String plyr = st.nextToken();
				L2PcInstance playerToKick = L2World.getInstance().getPlayer(plyr);
				if (playerToKick != null)
				{
					Raid.kickPlayerFromRaid(playerToKick);
					activeChar.sendMessage("You kicked " + playerToKick.getName() + " from the Raid.");
				}
				else
					activeChar.sendMessage("Wrong usege: //raidkick <player>");
			}
		}
		return true;
	}

	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}

	public boolean checkLevel(int level)
	{
		return (level >= REQUIRED_LEVEL);
	}

	public void showMainPage(L2PcInstance activeChar)
	{
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		final StringBuilder replyMSG = StringUtil.startAppend(1000, "<html><body>");

		replyMSG.append("<center><font color=\"LEVEL\">[Raid Engine]</font></center><br><br><br>");
		replyMSG.append("<table><tr><td><edit var=\"input1\" width=\"125\"></td><td><edit var=\"input2\" width=\"125\"></td></tr></table>");
		replyMSG.append("<table border=\"0\"><tr>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Name\" action=\"bypass -h admin_rai_name $input1\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Description\" action=\"bypass -h admin_rai_desc $input1\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Join Location\" action=\"bypass -h admin_rai_join_loc $input1\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG.append("</tr></table><br><table><tr>");
		replyMSG.append("</tr></table><br><table><tr>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Max lvl\" action=\"bypass -h admin_rai_maxlvl $input1\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Min lvl\" action=\"bypass -h admin_rai_minlvl $input1\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG.append("</tr></table><br><table><tr>");
		replyMSG.append("</tr></table><br><table><tr>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Max players\" action=\"bypass -h admin_rai_maxplayers $input1\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Min players\" action=\"bypass -h admin_rai_minplayers $input1\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG.append("</tr></table><br><table><tr>");
		replyMSG
				.append("<td width=\"100\"><button value=\"NPC\" action=\"bypass -h admin_rai_npc $input1\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG
				.append("<td width=\"100\"><button value=\"NPC Pos\" action=\"bypass -h admin_rai_npc_pos\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG
				.append("</tr><tr><td width=\"100\"><button value=\"Raidboss\" action=\"bypass -h admin_rai_bos $input1\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Raidboss Pos\" action=\"bypass -h admin_rai_boss_pos\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG.append("</tr></table><br><table><tr>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Reward\" action=\"bypass -h admin_rai_reward $input1\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Reward Amount\" action=\"bypass -h admin_rai_reward_amount $input1\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");

		replyMSG.append("</tr></table><br><table><tr>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Join Time\" action=\"bypass -h admin_rai_jointime $input1\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Event Time\" action=\"bypass -h admin_rai_eventtime $input1\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG.append("</tr></table><br><table><tr>");

		replyMSG
				.append("<td width=\"100\"><button value=\"Fight Pos\" action=\"bypass -h admin_rai_team_pos $input1\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG.append("</tr></table><table><tr>");

		replyMSG.append("</tr></table><br><table><tr>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Join\" action=\"bypass -h admin_rai_join\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Teleport\" action=\"bypass -h admin_rai_teleport\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Start\" action=\"bypass -h admin_rai_start\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG.append("</tr></table><table><tr>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Abort\" action=\"bypass -h admin_rai_abort\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Finish\" action=\"bypass -h admin_rai_finish\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG.append("</tr></table><br><table><tr>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Sit Force\" action=\"bypass -h admin_rai_sit\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG.append("</tr></table><br><br><table><tr>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Save\" action=\"bypass -h admin_rai_save\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Load\" action=\"bypass -h admin_rai_load\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG
				.append("<td width=\"100\"><button value=\"Auto Event\" action=\"bypass -h admin_rai_autoevent\" width=90 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG.append("</tr></table><br><br>");
		replyMSG.append("Current event...<br1>");
		replyMSG.append("    ... name:&nbsp;<font color=\"00FF00\">" + Raid._eventName + "</font><br1>");
		replyMSG.append("    ... description:&nbsp;<font color=\"00FF00\">" + Raid._eventDesc + "</font><br1>");
		replyMSG.append("    ... joining location name:&nbsp;<font color=\"00FF00\">" + Raid._joiningLocationName + "</font><br1>");
		replyMSG.append("    ... joining NPC ID:&nbsp;<font color=\"00FF00\">" + Raid._npcId + " on pos " + Raid._npcX + "," + Raid._npcY + "," + Raid._npcZ
				+ "</font><br1>");
		replyMSG.append("    ... Raidboss ID:&nbsp;<font color=\"00FF00\">" + Raid._bossId + " on pos " + Raid._bossX + "," + Raid._bossY + "," + Raid._bossZ
				+ "</font><br1>");
		replyMSG.append("    ... Fight pos:&nbsp;<font color=\"00FF00\">" + Raid._startX + "," + Raid._startY + "," + Raid._startZ
				+ "</font><br1>");
		replyMSG.append("    ... reward ID:&nbsp;<font color=\"00FF00\">" + Raid._rewardId + "</font><br1>");
		replyMSG.append("    ... reward Amount:&nbsp;<font color=\"00FF00\">" + Raid._rewardAmount + "</font><br><br>");
		replyMSG.append("    ... Min lvl:&nbsp;<font color=\"00FF00\">" + Raid._minlvl + "</font><br>");
		replyMSG.append("    ... Max lvl:&nbsp;<font color=\"00FF00\">" + Raid._maxlvl + "</font><br><br>");
		replyMSG.append("    ... Min Players:&nbsp;<font color=\"00FF00\">" + Raid._minPlayers + "</font><br>");
		replyMSG.append("    ... Max Players:&nbsp;<font color=\"00FF00\">" + Raid._maxPlayers + "</font><br><br>");
		replyMSG.append("    ... Joining Time:&nbsp;<font color=\"00FF00\">" + Raid._joinTime + "</font><br>");
		replyMSG.append("    ... Event Timer:&nbsp;<font color=\"00FF00\">" + Raid._eventTime + "</font><br><br>");
		replyMSG.append("<center><table border=\"0\">");

		replyMSG.append("</table></center>");

		replyMSG.append("</body></html>");
		adminReply.setHtml(replyMSG.toString());
		activeChar.sendPacket(adminReply);
	}
}
