package io.github.capttracker.spectrefactions.api;

public enum ProposalType {
    PEACE, ALLIANCE;

    public String translationKey() {
        return "proposal.spectrefactions." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
