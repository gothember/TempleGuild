package ru.templeguild.clans;

import java.util.UUID;

public class ClanInvite {
    private final String clanName;
    private final UUID invitedPlayerUUID;
    private final UUID inviterUUID;
    private final long timestamp;

    public ClanInvite(String clanName, UUID invitedPlayerUUID, UUID inviterUUID) {
        this.clanName = clanName;
        this.invitedPlayerUUID = invitedPlayerUUID;
        this.inviterUUID = inviterUUID;
        this.timestamp = System.currentTimeMillis();
    }

    public String getClanName() {
        return clanName;
    }

    public UUID getInvitedPlayerUUID() {
        return invitedPlayerUUID;
    }

    public UUID getInviterUUID() {
        return inviterUUID;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // Optional: check if expired
    public boolean isExpired(long timeoutMillis) {
        return (System.currentTimeMillis() - timestamp) > timeoutMillis;
    }
}
