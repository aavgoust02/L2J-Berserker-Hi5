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
package com.l2jserver.gameserver.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javolution.util.FastList;

import com.l2jserver.L2DatabaseFactory;
import com.l2jserver.gameserver.model.L2ItemInstance;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.entity.Couple;

/**
 * @author evill33t
 *
 */
public class CoupleManager
{
	private static final Logger _log = Logger.getLogger(CoupleManager.class.getName());
	
	private CoupleManager()
	{
		_log.info("L2JMOD: Initializing CoupleManager");
		load();
	}
	
	public static final CoupleManager getInstance()
	{
		return SingletonHolder._instance;
	}
	// =========================================================
	
	// =========================================================
	// Data Field
	private FastList<Couple> _couples;
	
	// =========================================================
	// Method - Public
	public void reload()
	{
		getCouples().clear();
		load();
	}
	
	// =========================================================
	// Method - Private
	private final void load()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement = con.prepareStatement("Select id from mods_wedding order by id");
			ResultSet rs = statement.executeQuery();
			
			while (rs.next())
			{
				getCouples().add(new Couple(rs.getInt("id")));
			}
			
			rs.close();
			statement.close();
			
			_log.info("Loaded: " + getCouples().size() + " couples(s)");
		}
		catch (Exception e)
		{
			_log.log(Level.SEVERE, "Exception: CoupleManager.load(): " + e.getMessage(), e);
		}
		
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	// =========================================================
	// Property - Public
	public final Couple getCouple(int coupleId)
	{
		int index = getCoupleIndex(coupleId);
		if (index >= 0)
			return getCouples().get(index);
		return null;
	}
	
	public void createCouple(L2PcInstance player1, L2PcInstance player2)
	{
		if (player1 != null && player2 != null)
		{
			if (player1.getPartnerId() == 0 && player2.getPartnerId() == 0)
			{
				int _player1id = player1.getObjectId();
				int _player2id = player2.getObjectId();
				
				Couple _new = new Couple(player1, player2);
				getCouples().add(_new);
				player1.setPartnerId(_player2id);
				player2.setPartnerId(_player1id);
				player1.setCoupleId(_new.getId());
				player2.setCoupleId(_new.getId());
			}
		}
	}
	
	public void deleteCouple(int coupleId)
	{
		int index = getCoupleIndex(coupleId);
		Couple couple = getCouples().get(index);
		if (couple != null)
		{
			L2PcInstance player1 = L2World.getInstance().getPlayer(couple.getPlayer1Id());
			L2PcInstance player2 = L2World.getInstance().getPlayer(couple.getPlayer2Id());
		    L2ItemInstance item = null;
			if (player1 != null)
			{
				player1.setPartnerId(0);
				player1.setMarried(false);
				player1.setCoupleId(0);
				
				item = player1.getInventory().getItemByItemId(9140);
				if (player1.isOnline() && item != null)
				{
					player1.destroyItem("Removing Cupids Bow", item, player1, true);
					player1.getInventory().updateDatabase();
				}
				if (!player1.isOnline() && item != null)
				{
					Integer PlayerId = player1.getObjectId();
					Integer ItemId = 9140;
					java.sql.Connection con = null;
					try
					{
						con = L2DatabaseFactory.getInstance().getConnection();
						PreparedStatement statement = con.prepareStatement("DELETE FROM items WHERE owner_id = ? AND item_id = ?");
						statement.setInt(1, PlayerId);
						statement.setInt(2, ItemId);
						statement.execute();
						statement.close();
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
		            finally { try { if (con != null) con.close(); } catch (SQLException e) { e.printStackTrace(); } }
				}
				
			}
			if (player2 != null)
			{
				player2.setPartnerId(0);
				player2.setMarried(false);
				player2.setCoupleId(0);
				
				item = player2.getInventory().getItemByItemId(9140);
				if (player2.isOnline() && item != null)
				{
					player2.destroyItem("Removing Cupids Bow", item, player2, true);
					player2.getInventory().updateDatabase();
				}
				if (!player2.isOnline() && item != null)
				{
					Integer Player2Id = player2.getObjectId();
					Integer Item2Id = 9140;
					java.sql.Connection con = null;
					try
					{
						con = L2DatabaseFactory.getInstance().getConnection();
						PreparedStatement statement = con.prepareStatement("DELETE FROM items WHERE owner_id = ? AND item_id = ?");
						statement.setInt(1, Player2Id);
						statement.setInt(2, Item2Id);
						statement.execute();
						statement.close();
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
		            finally { try { if (con != null) con.close(); } catch (SQLException e) { e.printStackTrace(); } }
				}
			}
			couple.divorce();
			getCouples().remove(index);
		}
	}
	
	public final int getCoupleIndex(int coupleId)
	{
		int i = 0;
		for (Couple temp : getCouples())
		{
			if (temp != null && temp.getId() == coupleId)
				return i;
			i++;
		}
		return -1;
	}
	
	public final FastList<Couple> getCouples()
	{
		if (_couples == null)
			_couples = new FastList<Couple>();
		return _couples;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final CoupleManager _instance = new CoupleManager();
	}
}
