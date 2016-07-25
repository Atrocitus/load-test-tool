package com.airwatch.tool;

/**
 * Created by manishk on 7/25/16.
 */
public enum CommandType {

    SYNC("/Microsoft-Server-ActiveSync?Cmd=Sync&"),
    PING("/Microsoft-Server-ActiveSync?Cmd=Ping&"),
    ITEM_OPERATIONS("/Microsoft-Server-ActiveSync?Cmd=ItemOperations&");

    private String serverUriAndCommand;

    CommandType(String commandUrlParam) {
        this.serverUriAndCommand = commandUrlParam;
    }

    public String getServerUriAndCommand() {
        return serverUriAndCommand;
    }
}
