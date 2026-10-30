package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Responsive hit and render geometry for the landscape game setup screen. */
public final class SetupScreenLayout {
    public static final int VISIBLE_LEADERS = 6;

    public final Rectangle logo = new Rectangle();
    public final Rectangle difficultyPanel = new Rectangle();
    public final Rectangle playerStylePanel = new Rectangle();
    public final Rectangle enemiesPanel = new Rectangle();
    public final Rectangle leaderCarouselPanel = new Rectangle();
    public final Rectangle startPanel = new Rectangle();
    public final Rectangle leaderPrevious = new Rectangle();
    public final Rectangle leaderNext = new Rectangle();
    public final Rectangle leaderDescription = new Rectangle();
    public final Rectangle startButton = new Rectangle();
    public final Rectangle[] difficultyButtons = rectangles(3);
    public final Rectangle[] colorSwatches = rectangles(5);
    public final Rectangle[] buildingButtons = rectangles(5);
    public final Rectangle[] opponentSlots = rectangles(4);
    public final Rectangle[] leaderCards = rectangles(VISIBLE_LEADERS);

    /** Lays out the mockup's top difficulty row, split player style, rival picker and footer. */
    public void update(float width, float height, float safeLeft, float safeTop,
                       float safeRight, float safeBottom) {
        float left = Math.max(0f, safeLeft) + 2f;
        float bottom = Math.max(0f, safeBottom) + 2f;
        float contentWidth = Math.max(1f, width - left - Math.max(0f, safeRight) - 4f);
        float contentHeight = Math.max(1f, height - bottom - Math.max(0f, safeTop) - 4f);

        logo.set(left + contentWidth * 0.012f, bottom + contentHeight * 0.845f,
                contentWidth * 0.145f, contentHeight * 0.145f);
        difficultyPanel.set(left + contentWidth * 0.18f, bottom + contentHeight * 0.835f,
                contentWidth * 0.64f, contentHeight * 0.16f);
        playerStylePanel.set(left + contentWidth * 0.13f, bottom + contentHeight * 0.60f,
                contentWidth * 0.74f, contentHeight * 0.225f);
        enemiesPanel.set(left + contentWidth * 0.13f, bottom + contentHeight * 0.103f,
                contentWidth * 0.74f, contentHeight * 0.485f);
        startPanel.set(left + contentWidth * 0.13f, bottom + contentHeight * 0.005f,
                contentWidth * 0.74f, contentHeight * 0.085f);

        float difficultyGap = difficultyPanel.width * 0.035f;
        float difficultyWidth = (difficultyPanel.width * 0.87f - 2f * difficultyGap) / 3f;
        float difficultyHeight = difficultyPanel.height * 0.48f;
        float difficultyX = difficultyPanel.x + difficultyPanel.width * 0.065f;
        float difficultyY = difficultyPanel.y + difficultyPanel.height * 0.14f;
        for (int i = 0; i < difficultyButtons.length; i++) {
            difficultyButtons[i].set(difficultyX + i * (difficultyWidth + difficultyGap),
                    difficultyY, difficultyWidth, difficultyHeight);
        }

        float choiceY = playerStylePanel.y + playerStylePanel.height * 0.15f;
        float choiceHeight = playerStylePanel.height * 0.53f;
        float colorWidth = playerStylePanel.width * 0.068f;
        float colorGap = playerStylePanel.width * 0.018f;
        float colorX = playerStylePanel.x + playerStylePanel.width * 0.055f;
        for (int i = 0; i < colorSwatches.length; i++) {
            colorSwatches[i].set(colorX + i * (colorWidth + colorGap), choiceY,
                    colorWidth, choiceHeight);
        }
        float buildingWidth = playerStylePanel.width * 0.068f;
        float buildingGap = playerStylePanel.width * 0.016f;
        float buildingX = playerStylePanel.x + playerStylePanel.width * 0.55f;
        for (int i = 0; i < buildingButtons.length; i++) {
            buildingButtons[i].set(buildingX + i * (buildingWidth + buildingGap), choiceY,
                    buildingWidth, choiceHeight);
        }

        float slotWidth = enemiesPanel.width * 0.108f;
        float slotGap = enemiesPanel.width * 0.018f;
        float slotHeight = enemiesPanel.height * 0.235f;
        float slotsWidth = slotWidth * opponentSlots.length
                + slotGap * (opponentSlots.length - 1);
        float slotX = enemiesPanel.x + (enemiesPanel.width - slotsWidth) / 2f;
        float slotY = enemiesPanel.y + enemiesPanel.height * 0.60f;
        for (int i = 0; i < opponentSlots.length; i++) {
            opponentSlots[i].set(slotX + i * (slotWidth + slotGap), slotY,
                    slotWidth, slotHeight);
        }

        leaderCarouselPanel.set(enemiesPanel.x + enemiesPanel.width * 0.07f,
                enemiesPanel.y + enemiesPanel.height * 0.035f,
                enemiesPanel.width * 0.86f, enemiesPanel.height * 0.55f);
        float arrowWidth = enemiesPanel.width * 0.028f;
        float carouselInset = enemiesPanel.width * 0.018f;
        float cardGap = enemiesPanel.width * 0.012f;
        float cardWidth = (leaderCarouselPanel.width - 2f * carouselInset
                - 2f * arrowWidth - (VISIBLE_LEADERS + 1f) * cardGap) / VISIBLE_LEADERS;
        float cardHeight = enemiesPanel.height * 0.30f;
        float cardY = enemiesPanel.y + enemiesPanel.height * 0.21f;
        leaderPrevious.set(leaderCarouselPanel.x + carouselInset, cardY,
                arrowWidth, cardHeight);
        float cardsX = leaderPrevious.x + leaderPrevious.width + cardGap;
        for (int i = 0; i < leaderCards.length; i++) {
            leaderCards[i].set(cardsX + i * (cardWidth + cardGap), cardY,
                    cardWidth, cardHeight);
        }
        leaderNext.set(leaderCards[leaderCards.length - 1].x
                        + leaderCards[leaderCards.length - 1].width + cardGap,
                cardY, arrowWidth, cardHeight);
        leaderDescription.set(leaderCarouselPanel.x + carouselInset,
                enemiesPanel.y + enemiesPanel.height * 0.055f,
                leaderCarouselPanel.width - 2f * carouselInset,
                enemiesPanel.height * 0.105f);

        startButton.set(startPanel.x + startPanel.width * 0.75f,
                startPanel.y + startPanel.height * 0.13f,
                startPanel.width * 0.23f, startPanel.height * 0.74f);
    }

    /** Maps a visible portrait index to the circular 19-leader source list. */
    public static int leaderIdAt(int listStart, int visibleIndex, int leaderCount) {
        if (leaderCount <= 0 || visibleIndex < 0 || visibleIndex >= VISIBLE_LEADERS) {
            throw new IllegalArgumentException("Invalid leader carousel index");
        }
        return Math.floorMod(listStart + visibleIndex, leaderCount);
    }

    private static Rectangle[] rectangles(int count) {
        Rectangle[] result = new Rectangle[count];
        for (int i = 0; i < count; i++) {
            result[i] = new Rectangle();
        }
        return result;
    }
}
