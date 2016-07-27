package com.airwatch.tool;

/**
 * Created by manishk on 7/25/16.
 */
public enum CommandType {

    SYNC("Cmd=Sync&"),
    PING("Cmd=Ping&"),
    ITEM_OPERATIONS("Cmd=ItemOperations&");

    private String command;

    CommandType(String commandUrlParam) {
        this.command = commandUrlParam;
    }

    public String getCommand() {
        return command;
    }
}
