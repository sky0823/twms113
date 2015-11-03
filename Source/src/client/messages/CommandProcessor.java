/*
 This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc> 
 Matthias Butz <matze@odinms.de>
 Jan Christian Meyer <vimes@odinms.de>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License version 3
 as published by the Free Software Foundation. You may not use, modify
 or distribute this program under any other version of the
 GNU Affero General Public License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package client.messages;

import java.util.ArrayList;
import client.MapleCharacter;
import client.MapleClient;
import client.messages.commands.*;
import client.messages.commands.AdminCommand;
import client.messages.commands.PlayerCommand;
import client.messages.commands.GMCommand;
import client.messages.commands.InternCommand;
import constants.ServerConstants.CommandType;
import constants.ServerConstants.PlayerGMRank;
import database.DatabaseConnection;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import tools.FilePrinter;

public class CommandProcessor {

    private final static HashMap<String, CommandObject> commands = new HashMap<>();
    private final static HashMap<Integer, ArrayList<String>> commandList = new HashMap<>();

    static {

        Class<?>[] CommandFiles = {
            PlayerCommand.class, AdminCommand.class, GMCommand.class, InternCommand.class
        };

        for (Class<?> clasz : CommandFiles) {
            try {
                PlayerGMRank rankNeeded = (PlayerGMRank) clasz.getMethod("getPlayerLevelRequired", new Class<?>[]{}).invoke(null, (Object[]) null);
                Class<?>[] commandClasses = clasz.getDeclaredClasses();
                ArrayList<String> cL = new ArrayList<>();
                for (Class<?> c : commandClasses) {
                    try {
                        if (!Modifier.isAbstract(c.getModifiers()) && !c.isSynthetic()) {
                            Object o = c.newInstance();
                            boolean enabled;
                            try {
                                enabled = c.getDeclaredField("enabled").getBoolean(c.getDeclaredField("enabled"));
                            } catch (NoSuchFieldException ex) {
                                enabled = true; //Enable all coded commands by default.
                            }
                            if (o instanceof CommandExecute && enabled) {
                                cL.add(rankNeeded.getCommandPrefix() + c.getSimpleName().toLowerCase());
                                commands.put(rankNeeded.getCommandPrefix() + c.getSimpleName().toLowerCase(), new CommandObject(rankNeeded.getCommandPrefix() + c.getSimpleName().toLowerCase(), (CommandExecute) o, rankNeeded.getLevel()));
                            }
                        }
                    } catch (InstantiationException | IllegalAccessException | SecurityException | IllegalArgumentException ex) {
                        FilePrinter.printError(FilePrinter.CommandProccessor, ex);
                    }
                }
                Collections.sort(cL);
                commandList.put(rankNeeded.getLevel(), cL);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                FilePrinter.printError(FilePrinter.CommandProccessor, ex);
            }
        }
    }

    private static void sendDisplayMessage(MapleClient c, String msg, CommandType type) {
        if (c.getPlayer() == null) {
            return;
        }
        switch (type) {
            case NORMAL:
                c.getPlayer().dropMessage(6, msg);
                break;
            case TRADE:
                c.getPlayer().dropMessage(-2, "錯誤 : " + msg);
                break;
        }

    }

    public static boolean processCommand(MapleClient c, String line, CommandType type) {

        char commandPrefix = line.charAt(0);

        if (commandPrefix == PlayerGMRank.NORMAL.getCommandPrefix()) {
            String[] splitted = line.split(" ");
            splitted[0] = splitted[0].toLowerCase();

            CommandObject co = commands.get(splitted[0]);
            if (co == null || co.getType() != type) {
                sendDisplayMessage(c, "沒有這個指令,可以使用 @幫助/@help 來查看指令.", type);
                return false;
            }
            try {
                boolean ret = co.execute(c, splitted);
                return ret;
            } catch (Exception e) {
                sendDisplayMessage(c, "有錯誤.", type);
                if (c.getPlayer().isGM()) {
                    sendDisplayMessage(c, "錯誤: " + e, type);
                }
            }
            return true;
        } else if (c.getPlayer().getGMLevel() > PlayerGMRank.NORMAL.getLevel()) {
            if (line.charAt(0) == PlayerGMRank.GM.getCommandPrefix() 
                    || line.charAt(0) == PlayerGMRank.ADMIN.getCommandPrefix() 
                    || line.charAt(0) == PlayerGMRank.INTERN.getCommandPrefix()) { //Redundant for now, but in case we change symbols later. This will become extensible.
                String[] splitted = line.split(" ");
                splitted[0] = splitted[0].toLowerCase();
                if (line.charAt(0) == '!') { //GM Commands
                    CommandObject co = commands.get(splitted[0]);
                    if (co == null || co.getType() != type) {
                        sendDisplayMessage(c, "沒有這個指令.", type);
                        return true;
                    }
                    if (c.getPlayer().getGMLevel() >= co.getReqGMLevel()) {
                        boolean ret = co.execute(c, splitted);
                        if (ret && c.getPlayer() != null) { //incase d/c after command or something
                            logGMCommandToDB(c.getPlayer(), line);
                            if (c.getPlayer().getGMLevel() == 5) {
                                System.out.println("＜超級管理員＞ " + c.getPlayer().getName() + " 使用了指令: " + line);
                            } else if (c.getPlayer().getGMLevel() == 4) {
                                System.out.println("＜領導者＞ " + c.getPlayer().getName() + " 使用了指令: " + line);
                            } else if (c.getPlayer().getGMLevel() == 3) {
                                System.out.println("＜巡邏者＞ " + c.getPlayer().getName() + " 使用了指令: " + line);
                            } else if (c.getPlayer().getGMLevel() == 2) {
                                System.out.println("＜老實習生＞ " + c.getPlayer().getName() + " 使用了指令: " + line);
                            } else if (c.getPlayer().getGMLevel() == 1) {
                                System.out.println("＜新實習生＞ " + c.getPlayer().getName() + " 使用了指令: " + line);
                            } else {
                                sendDisplayMessage(c, "你沒有權限可以使用指令.", type);
                            }
                        } else if (!ret && c.getPlayer() != null) {
                            c.getPlayer().dropMessage("指令錯誤，用法： " + co.getMessage());
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void logGMCommandToDB(MapleCharacter player, String command) {
        PreparedStatement ps = null;
        try {
            ps = DatabaseConnection.getConnection().prepareStatement("INSERT INTO gmlog (cid, command, mapid) VALUES (?, ?, ?)");
            ps.setInt(1, player.getId());
            ps.setString(2, command);
            ps.setInt(3, player.getMap().getId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            FilePrinter.printError(FilePrinter.CommandProccessor, ex, "logGMCommandToDB");
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
            } catch (SQLException ex) {

                FilePrinter.printError(FilePrinter.CommandProccessor, ex, "logGMCommandToDB");
            }
        }
    }
}
