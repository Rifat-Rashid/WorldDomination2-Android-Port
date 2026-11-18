package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;

/** Responsive hit and render geometry for the landscape game setup screen. */
public final class SetupScreenLayout {
    public static final int VISIBLE_LEADERS = 6;
    private static final float[] PORTRAIT_X = {-2.3f, -0.75f, -2.3f, -1.85f};
    private static final float[] PORTRAIT_Y = {-3f, -3f, -3.5f, -3.5f};
    private static final float[] CANCEL_X = {48.25f, 49.25f, 47.25f, 48.25f};
    /** Presenter_gameMenu.update_leader_list uses _alpha = 20 for selected rivals. */
    public static final float SELECTED_LEADER_ALPHA = 0.2f;

    public final Rectangle logo = new Rectangle();
    public final Rectangle difficultyPanel = new Rectangle();
    public final Rectangle playerStylePanel = new Rectangle();
    public final Rectangle enemiesPanel = new Rectangle();
    public final Rectangle leaderCarouselPanel = new Rectangle();
    public final Rectangle startPanel = new Rectangle();
    public final Rectangle leaderPrevious = new Rectangle();
    public final Rectangle leaderNext = new Rectangle();
    public final Rectangle opponentArrow = new Rectangle();
    public final Rectangle leaderDescription = new Rectangle();
    public final Rectangle startButton = new Rectangle();
    public final Rectangle[] difficultyButtons = rectangles(3);
    public final Rectangle[] colorSwatches = rectangles(5);
    public final Rectangle[] buildingButtons = rectangles(5);
    public final Rectangle[] opponentSlots = rectangles(4);
    public final Rectangle[] opponentRemoveIcons = rectangles(4);
    public final Rectangle[] opponentWells = rectangles(4);
    public final Rectangle[] opponentPortraits = rectangles(4);
    public final Rectangle[] leaderCards = rectangles(VISIBLE_LEADERS);

    /** Lays out the mockup's top difficulty row, split player style, rival picker and footer. */
    public void update(float width, float height, float safeLeft, float safeTop,
                       float safeRight, float safeBottom) {
        // The reference layout leaves a 13% margin beside its main panels. Keep
        // that placement for normal cutouts; move only when the margin cannot
        // contain the exclusion. Increasing the left offset reduces contentWidth.
        float originalContentWidth = Math.max(1f, width - Math.max(0f, safeRight) - 6f);
        float insetOverflow = Math.max(0f, Math.max(0f, safeLeft)
                - originalContentWidth * 0.13f);
        float left = 2f + insetOverflow / 0.87f;
        float bottom = Math.max(0f, safeBottom) + 2f;
        float contentWidth = Math.max(1f, width - left - Math.max(0f, safeRight) - 4f);
        float contentHeight = Math.max(1f, height - bottom - Math.max(0f, safeTop) - 4f);

        difficultyPanel.set(left + contentWidth * 0.18f, bottom + contentHeight * 0.835f,
                contentWidth * 0.64f, contentHeight * 0.16f);
        float logoX = Math.max(left + contentWidth * 0.012f,
                Math.max(0f, safeLeft) + 2f);
        float logoWidth = Math.min(contentWidth * 0.145f,
                difficultyPanel.x - logoX - 8f);
        logo.set(logoX, bottom + contentHeight * 0.845f,
                Math.max(1f, logoWidth), contentHeight * 0.145f);
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
        float choiceHeight = playerStylePanel.height * 0.47f;
        float colorWidth = playerStylePanel.width * 0.062f;
        float colorGap = playerStylePanel.width * 0.018f;
        float colorX = playerStylePanel.x + playerStylePanel.width * 0.055f;
        for (int i = 0; i < colorSwatches.length; i++) {
            colorSwatches[i].set(colorX + i * (colorWidth + colorGap), choiceY,
                    colorWidth, choiceHeight);
        }
        float buildingWidth = playerStylePanel.width * 0.062f;
        float buildingGap = playerStylePanel.width * 0.016f;
        float buildingX = playerStylePanel.x + playerStylePanel.width * 0.55f;
        for (int i = 0; i < buildingButtons.length; i++) {
            buildingButtons[i].set(buildingX + i * (buildingWidth + buildingGap), choiceY,
                    buildingWidth, choiceHeight);
        }

        float slotWidth = enemiesPanel.width * 0.108f;
        float slotGap = enemiesPanel.width * 0.018f;
        float slotHeight = enemiesPanel.height * 0.235f;
        // The source mobile mockup leaves room for the direction marker on the
        // left and places the four selected-rival cards just right of center.
        float slotX = enemiesPanel.x + enemiesPanel.width * 0.32f;
        float slotY = enemiesPanel.y + enemiesPanel.height * 0.67f;
        for (int i = 0; i < opponentSlots.length; i++) {
            opponentSlots[i].set(slotX + i * (slotWidth + slotGap), slotY,
                    slotWidth, slotHeight);
            // Fit the full 70x75 portrait, then place all leaves in the same
            // source coordinate system. The larger slot remains the hit target.
            float scale = Math.min(slotWidth / 70f, slotHeight / 75f);
            float portraitX = slotX + i * (slotWidth + slotGap) + (slotWidth - 70f * scale) / 2f;
            float portraitY = slotY + (slotHeight - 75f * scale) / 2f;
            opponentPortraits[i].set(portraitX, portraitY, 70f * scale, 75f * scale);
            float wellX = portraitX - PORTRAIT_X[i] * scale;
            float wellY = portraitY + (PORTRAIT_Y[i] + 5f) * scale;
            opponentWells[i].set(wellX, wellY, 64f * scale, 70f * scale);
            opponentRemoveIcons[i].set(wellX + CANCEL_X[i] * scale,
                    wellY + (70f - 0.35f - 17.1f) * scale, 17.3f * scale, 17.1f * scale);
        }
        float arrowScale = Math.min(enemiesPanel.width * 0.15f / 100f,
                opponentWells[0].height / 70f);
        opponentArrow.set(enemiesPanel.x + enemiesPanel.width * 0.12f,
                slotY + (slotHeight - 53f * arrowScale) / 2f,
                100f * arrowScale, 53f * arrowScale);

        leaderCarouselPanel.set(enemiesPanel.x + enemiesPanel.width * 0.07f,
                enemiesPanel.y + enemiesPanel.height * 0.085f,
                enemiesPanel.width * 0.86f, enemiesPanel.height * 0.50f);
        float arrowWidth = enemiesPanel.width * 0.028f;
        float carouselInset = enemiesPanel.width * 0.018f;
        float cardGap = enemiesPanel.width * 0.012f;
        float cardWidth = (leaderCarouselPanel.width - 2f * carouselInset
                - 2f * arrowWidth - (VISIBLE_LEADERS + 1f) * cardGap) / VISIBLE_LEADERS;
        float cardHeight = enemiesPanel.height * 0.326f;
        float cardY = enemiesPanel.y + enemiesPanel.height * 0.23f;
        float carouselArrowHeight = cardHeight * 0.72f;
        float carouselArrowY = cardY + (cardHeight - carouselArrowHeight) * 0.5f;
        leaderPrevious.set(leaderCarouselPanel.x + carouselInset, carouselArrowY,
                arrowWidth, carouselArrowHeight);
        float cardsX = leaderPrevious.x + leaderPrevious.width + cardGap;
        for (int i = 0; i < leaderCards.length; i++) {
            leaderCards[i].set(cardsX + i * (cardWidth + cardGap), cardY,
                    cardWidth, cardHeight);
        }
        leaderNext.set(leaderCards[leaderCards.length - 1].x
                        + leaderCards[leaderCards.length - 1].width + cardGap,
                carouselArrowY, arrowWidth, carouselArrowHeight);
        leaderDescription.set(leaderCarouselPanel.x + carouselInset,
                enemiesPanel.y + enemiesPanel.height * 0.11f,
                leaderCarouselPanel.width - 2f * carouselInset,
                enemiesPanel.height * 0.09f);

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

    /** The source right arrow visits the duplicate first page at start 19 before resetting. */
    public static int nextLeaderStart(int listStart, int leaderCount) {
        return listStart > leaderCount - 1 ? 0 : listStart + 1;
    }

    private static Rectangle[] rectangles(int count) {
        Rectangle[] result = new Rectangle[count];
        for (int i = 0; i < count; i++) {
            result[i] = new Rectangle();
        }
        return result;
    }
}
