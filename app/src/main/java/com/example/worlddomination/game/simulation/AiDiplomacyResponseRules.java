package com.example.worlddomination.game.simulation;

/** Pure decision rules used when an AI nation evaluates an incoming proposal. */
final class AiDiplomacyResponseRules {
    private AiDiplomacyResponseRules() {
    }

    /**
     * Mirrors {@code Model.AI_consider_diplomacyArr} in the Python reference.
     * Proposal options 0-2 are offers; options 3-5 are demands.
     */
    static boolean shouldAccept(double regardTowardProposer,
                                int receiverStrength,
                                int proposerStrength,
                                int receiverPopulation,
                                int proposerPopulation,
                                int receiverMoney,
                                int receiverTurnMoney,
                                int optionIndex,
                                int amountMillions) {
        int accept = 2;
        int reject = 0;
        if (optionIndex < 3) {
            if (regardTowardProposer > 50) {
                accept++;
            } else {
                reject++;
            }
            accept += optionIndex * 4;
            if (receiverStrength < proposerStrength) {
                accept++;
            } else {
                reject++;
            }
            if (proposerPopulation > receiverPopulation) {
                accept++;
            }
            if (receiverMoney < 30) {
                accept++;
            }
        } else {
            if (regardTowardProposer > 50) {
                accept++;
            } else {
                reject++;
            }
            reject += Math.round(optionIndex / 2.0f);
            if (receiverStrength < proposerStrength) {
                accept++;
            } else {
                reject++;
            }
            if (receiverStrength < proposerStrength / 2.0f) {
                accept += 2;
            } else {
                reject++;
            }
            if (proposerPopulation > receiverPopulation) {
                accept++;
            } else {
                reject++;
            }
        }
        return accept > reject && (optionIndex < 3
                || receiverTurnMoney >= amountMillions);
    }
}
