package ru.templeguild.clans;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public class Clan {
    private String name;
    private UUID leader;
    private Set<UUID> members;
    private boolean pvpEnabled;
    // Clan storage (e.g., serialized inventory) might be added later

    public Clan(String name, UUID leader) {
        this.name = name;
        this.leader = leader;
        this.members = new HashSet<>();
        this.members.add(leader); // Leader is a member by default
        this.pvpEnabled = false; // Default PvP status
    }

    // Getters
    public String getName() { return name; }
    public UUID getLeader() { return leader; }
    public Set<UUID> getMembers() { return members; }
    public boolean isPvpEnabled() { return pvpEnabled; }

    // Setters
    public void setLeader(UUID leader) { this.leader = leader; }
    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }

    // Member management
    public void addMember(UUID member) {
        this.members.add(member);
    }

    public void removeMember(UUID member) {
        this.members.remove(member);
    }

    public boolean isMember(UUID member) {
        return this.members.contains(member);
    }

    private String serializedStorage; // Base64 string of the inventory

    // Add getter and setter
    public String getSerializedStorage() {
        return serializedStorage;
    }

    public void setSerializedStorage(String serializedStorage) {
        this.serializedStorage = serializedStorage;
    }

    private int kills = 0;

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public void incrementKills(int amount) {
        this.kills += amount;
    }

    // equals and hashCode based on clan name for uniqueness
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Clan clan = (Clan) o;
        return name.equalsIgnoreCase(clan.name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }
}
