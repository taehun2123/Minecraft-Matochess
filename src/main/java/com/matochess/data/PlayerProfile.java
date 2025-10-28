package com.matochess.data;

import java.util.UUID;

/**
 * Represents persistent player profile data
 * Stored in database for ranking and statistics
 */
public class PlayerProfile {

    private final UUID playerId;
    private String playerName;

    // Ranking
    private Tier tier;
    private int division; // 5 to 1 (5 = lowest, 1 = highest in tier)
    private int ratingPoints;

    // Statistics
    private int gamesPlayed;
    private int wins; // 1st place
    private int top4; // 1st to 4th place
    private int totalPlacement; // Sum of all placements for average calculation

    // Timestamps
    private long firstPlayed;
    private long lastPlayed;

    public PlayerProfile(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.tier = Tier.COPPER;
        this.division = 5;
        this.ratingPoints = 0;
        this.gamesPlayed = 0;
        this.wins = 0;
        this.top4 = 0;
        this.totalPlacement = 0;
        this.firstPlayed = System.currentTimeMillis();
        this.lastPlayed = System.currentTimeMillis();
    }

    /**
     * Add rating points and check for tier/division changes
     * @param points Points to add (can be negative)
     * @return true if tier/division changed
     */
    public boolean addRating(int points) {
        ratingPoints += points;

        boolean changed = false;

        // Check for promotion
        while (ratingPoints >= 100) {
            ratingPoints -= 100;
            changed = true;

            if (division > 1) {
                division--;
            } else {
                // Promote to next tier
                Tier nextTier = tier.next();
                if (nextTier != null) {
                    tier = nextTier;
                    division = 5;
                } else {
                    // Already max tier
                    ratingPoints = 100; // Cap at 100 for max tier division 1
                }
            }
        }

        // Check for demotion
        while (ratingPoints < 0) {
            ratingPoints += 100;
            changed = true;

            if (division < 5) {
                division++;
            } else {
                // Demote to previous tier
                Tier prevTier = tier.previous();
                if (prevTier != null) {
                    tier = prevTier;
                    division = 1;
                } else {
                    // Already min tier
                    ratingPoints = 0; // Cap at 0 for min tier division 5
                }
            }
        }

        return changed;
    }

    /**
     * Record a game result
     */
    public void recordGame(int placement) {
        gamesPlayed++;
        totalPlacement += placement;
        lastPlayed = System.currentTimeMillis();

        if (placement == 1) {
            wins++;
        }

        if (placement <= 4) {
            top4++;
        }
    }

    /**
     * Get average placement
     */
    public double getAveragePlacement() {
        if (gamesPlayed == 0) return 0.0;
        return (double) totalPlacement / gamesPlayed;
    }

    /**
     * Get win rate (1st place only)
     */
    public double getWinRate() {
        if (gamesPlayed == 0) return 0.0;
        return (double) wins / gamesPlayed * 100;
    }

    /**
     * Get top 4 rate
     */
    public double getTop4Rate() {
        if (gamesPlayed == 0) return 0.0;
        return (double) top4 / gamesPlayed * 100;
    }

    /**
     * Get formatted rank string
     */
    public String getRankString() {
        return tier.getFormattedName() + " " + division;
    }

    /**
     * Get total rating (for leaderboard sorting)
     */
    public int getTotalRating() {
        int tierPoints = (tier.getRank() - 1) * 500; // 500 points per tier
        int divisionPoints = (5 - division) * 100; // 100 points per division
        return tierPoints + divisionPoints + ratingPoints;
    }

    // Getters and setters
    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public Tier getTier() {
        return tier;
    }

    public void setTier(Tier tier) {
        this.tier = tier;
    }

    public int getDivision() {
        return division;
    }

    public void setDivision(int division) {
        this.division = division;
    }

    public int getRatingPoints() {
        return ratingPoints;
    }

    public void setRatingPoints(int ratingPoints) {
        this.ratingPoints = ratingPoints;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public void setGamesPlayed(int gamesPlayed) {
        this.gamesPlayed = gamesPlayed;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getTop4() {
        return top4;
    }

    public void setTop4(int top4) {
        this.top4 = top4;
    }

    public int getTotalPlacement() {
        return totalPlacement;
    }

    public void setTotalPlacement(int totalPlacement) {
        this.totalPlacement = totalPlacement;
    }

    public long getFirstPlayed() {
        return firstPlayed;
    }

    public void setFirstPlayed(long firstPlayed) {
        this.firstPlayed = firstPlayed;
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        this.lastPlayed = lastPlayed;
    }
}
