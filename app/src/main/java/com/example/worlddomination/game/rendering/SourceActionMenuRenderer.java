package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.WeaponCatalog;
import com.example.worlddomination.game.ui.SourceActionMenuState;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cached four-times source centerScreen artwork with live, high-resolution text. */
public final class SourceActionMenuRenderer implements Disposable {
    public interface PortraitDrawer {
        void draw(int leaderId, float x, float y, float width, float height);
    }
    private static final String DIRECTORY = "original/ui/action-menus/";
    // The source iPhone adapter copies religion text3 with TextFormat size 16
    // and translates its authored clip down by seven source units. The SWF's
    // unmodified field metadata still reports size 22.
    private static final float MASS_MEDIA_VALUE_FONT_SIZE = 16f;
    private static final float MASS_MEDIA_VALUE_OFFSET_Y = 7f;
    private final JsonValue menus = new JsonReader().parse(Gdx.files.internal(DIRECTORY + "menus.json"));
    private final LinkedHashMap<String, Texture> cache = new LinkedHashMap<>(4, .75f, true);
    private final Texture accept = texture("btnAccept"), cancel = texture("btnCancel");
    private final Texture selected = texture("leader-selected");
    private final Texture readiness = texture("spy-readiness");
    private final TextureRegion readinessRegion = new TextureRegion(readiness);
    private final Texture mediaOpinion = texture("media-opinion");
    private final TextureRegion opinionRegion = new TextureRegion(mediaOpinion);
    private final BitmapFont font = new BitmapFont(Gdx.files.internal("original/ui/fonts/resource-arial-48.fnt"));
    private final Rectangle scratch = new Rectangle();
    private final Matrix4 savedTransform = new Matrix4(), textTransform = new Matrix4();
    private JsonValue screen;
    private String key;
    private int revision = -1, funds = Integer.MIN_VALUE, opinion;
    private GameState.DiplomacyProposal lastProposal;
    private final GlyphLayout propagandaLayout = new GlyphLayout();
    private int propagandaOpinion = Integer.MIN_VALUE;
    private TextField[] fields = new TextField[0];

    private static final class TextField {
        final JsonValue source;
        final GlyphLayout glyph = new GlyphLayout();
        float scale, x;
        TextField(JsonValue source) { this.source = source; }
    }

    public SourceActionMenuRenderer() {
        font.setUseIntegerPositions(false);
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    public void prepare(GameState game, SourceActionMenuState state) {
        GameState.DiplomacyProposal proposal = game.getPendingHumanDiplomacyProposal();
        String next = proposal == null ? state.getKey()
                : state.hasPendingError() ? "diplomacy_error"
                : proposal.optionIndex < 3 ? "diplomacyOffer" : "diplomacyDemand";
        if (next == null) return;
        if (!next.equals(key)) {
            key = next;
            screen = menus.get(key);
            if (screen == null) throw new IllegalStateException("Missing source menu " + key);
            if (!cache.containsKey(key)) {
                if (cache.size() == 3) {
                    Map.Entry<String, Texture> oldest = cache.entrySet().iterator().next();
                    oldest.getValue().dispose();
                    cache.remove(oldest.getKey());
                }
                cache.put(key, texture(key));
            }
            JsonValue list = screen.get("fields");
            fields = new TextField[list.size];
            for (int i = 0; i < fields.length; i++) fields[i] = new TextField(list.get(i));
            revision = -1;
        }
        if (revision == state.getRevision() && funds == game.getHuman().turnMoneyMillions
                && opinion == game.getHuman().religion && lastProposal == proposal) return;
        lastProposal = proposal;
        revision = state.getRevision();
        funds = game.getHuman().turnMoneyMillions;
        opinion = game.getHuman().religion;
        for (TextField field : fields) layout(field, value(field.source, game, state, proposal), game, state);
    }

    private String value(JsonValue field, GameState game, SourceActionMenuState state,
                         GameState.DiplomacyProposal proposal) {
        String name = field.getString("name");
        if ("textFunds".equals(name)) return "Budget: " + funds + ".000.000";
        if (key.startsWith("religion-")) {
            if ("text1".equals(name)) return game.getHuman().populationMillions + ".000.000";
            if ("text2".equals(name)) return opinion + "%";
            if ("text3".equals(name)) return "+" + game.getMassMediaPreviewGain() + " %";
        }
        if ("diplomacy".equals(key)) {
            if (name.startsWith("btn") && name.endsWith("/text1")) {
                int option = name.charAt(3) - '1';
                return Integer.toString(game.getDiplomacyOptionDuration(option));
            }
            if (name.matches("text[1-6]")) {
                int option = name.charAt(4) - '1';
                return (option < 3 ? "turns no attack, I pay $ " : "turns no attack pay me $ ")
                        + game.getDiplomacyOptionAmount(option) + " mln";
            }
        }
        if (proposal != null) {
            if ("text1".equals(name)) return " $ " + game.getDiplomacyOptionAmount(proposal.optionIndex) + ".000.000";
            if ("btn1/text1".equals(name)) return Integer.toString(game.getDiplomacyOptionDuration(proposal.optionIndex));
        }
        if ("error_window/text1".equals(name) && state.isEconomyWarning(game)) return state.getError();
        if ("error_window/text1".equals(name)) return "spy_error".equals(key)
                ? "Your spy readiness is not high enough to use this spy.\nChoose a different spy or wait a few turns."
                : "Not enough money";
        if ("propaganda2".equals(key) && "text1".equals(name))
            return "Propaganda strength: " + opinion + " %";
        if (key.startsWith("preAttack3-") && "text1".equals(name)) {
            WeaponCatalog.Definition weapon = WeaponCatalog.byName(game.getSelectedPreparedPayload());
            int[] megatons = {0, 10, 25, 50, 95};
            return weapon == null ? "" : megatons[weapon.level] + " megaton";
        }
        return field.getString("text").replace('\u00a0', ' ').trim();
    }

    private void layout(TextField field, String text, GameState game, SourceActionMenuState state) {
        JsonValue source = field.source, b = source.get("bounds");
        float width = Math.max(1, b.getFloat(2) - 4);
        field.scale = source.getFloat("size") / 48f;
        font.getData().setScale(field.scale);
        JsonValue c = source.get("color");
        Color ink = new Color(c.getInt(0) / 255f, c.getInt(1) / 255f, c.getInt(2) / 255f, c.getInt(3) / 255f);
        String name = source.getString("name");
        if (key.startsWith("religion-") && "text3".equals(name)) {
            field.scale = MASS_MEDIA_VALUE_FONT_SIZE / 48f;
            font.getData().setScale(field.scale);
        }
        if ("diplomacy".equals(key) && state.hasOption()
                && name.equals("text" + (game.getSelectedDiplomacyOption() + 1))) ink.set(Color.GREEN);
        int align = source.getInt("align"), alignment = align == 1 ? Align.right : align == 2 ? Align.center : Align.left;
        field.glyph.setText(font, text, ink, width, alignment, source.getBoolean("wrap"));
        if (!source.getBoolean("wrap") && field.glyph.width > width) {
            field.scale *= width / field.glyph.width;
            font.getData().setScale(field.scale);
            field.glyph.setText(font, text, ink, width, alignment, false);
        }
        field.x = b.getFloat(0) + 2;
    }

    /** Rendering and all hit testing share this uniform transform. */
    public static Rectangle fit(Rectangle container, Rectangle output) {
        float scale = Math.min(container.width / 365f, container.height / 275f);
        return output.set(container.x + (container.width - 365f * scale) / 2,
                container.y + (container.height - 275f * scale) / 2, 365f * scale, 275f * scale);
    }

    public void draw(SpriteBatch batch, Rectangle container, GameState game,
                     SourceActionMenuState state, PortraitDrawer portraits, TurnControlRenderer turnControls,
                     TextureRegion sourceGlass, BitmapFont propagandaFont) {
        prepare(game, state);
        if (screen == null) return;
        fit(container, scratch);
        float scale = scratch.width / 365f;
        batch.setColor(Color.WHITE);
        batch.draw(cache.get(key), scratch.x, scratch.y, scratch.width, scratch.height);
        GameState.DiplomacyProposal proposal = game.getPendingHumanDiplomacyProposal();
        JsonValue bar = screen.get("readiness");
        if (bar != null && !bar.isNull()) {
            float amount = Math.max(0, Math.min(100, game.getSpyReadiness())) / 100f;
            if (amount > 0) {
                readinessRegion.setRegion(0, 0, Math.round(readiness.getWidth() * amount), readiness.getHeight());
                batch.draw(readinessRegion, scratch.x + bar.getFloat(0) * scale,
                        scratch.y + scratch.height - (bar.getFloat(1) + bar.getFloat(3)) * scale,
                        bar.getFloat(2) * scale * amount, bar.getFloat(3) * scale);
            }
        }
        JsonValue opinionBar = screen.get("opinion");
        if (opinionBar != null && !opinionBar.isNull()) {
            float amount = Math.max(0, Math.min(100, opinion)) / 100f;
            if (amount > 0) {
                opinionRegion.setRegion(0, 0, Math.round(mediaOpinion.getWidth() * amount), mediaOpinion.getHeight());
                batch.draw(opinionRegion, scratch.x + opinionBar.getFloat(0) * scale,
                        scratch.y + scratch.height - (opinionBar.getFloat(1) + opinionBar.getFloat(3)) * scale,
                        opinionBar.getFloat(2) * scale * amount, opinionBar.getFloat(3) * scale);
            }
        }
        JsonValue scheme = screen.get("scheme");
        if (proposal != null && scheme != null && !scheme.isNull()) {
            turnControls.drawDiplomacyScheme(batch, scratch.x + scheme.getFloat(0) * scale,
                    scratch.y + scratch.height - (scheme.getFloat(1) + scheme.getFloat(3)) * scale,
                    scheme.getFloat(2) * scale, scheme.getFloat(3) * scale, proposal.proposerId, game);
        }
        JsonValue leaders = screen.get("portraits");
        for (int i = 0; i < leaders.size; i++) {
            int player = proposal == null ? i + 1 : proposal.proposerId;
            GameState.PlayerState nation = game.getPlayerState(player);
            if (!nation.alive || proposal == null && game.getHuman().hasCeasefireWith(player)) continue;
            JsonValue b = leaders.get(i).get("bounds");
            portraits.draw(nation.getLeaderId(), scratch.x + b.getFloat(0) * scale,
                    scratch.y + scratch.height - (b.getFloat(1) + b.getFloat(3)) * scale,
                    b.getFloat(2) * scale, b.getFloat(3) * scale);
        }
        if ("diplomacy".equals(key) && state.hasLeader()) {
            JsonValue b = screen.get("selectedPortraits").get(game.getSelectedTargetPlayer() - 1).get("bounds");
            batch.draw(selected, scratch.x + b.getFloat(0) * scale,
                    scratch.y + scratch.height - (b.getFloat(1) + b.getFloat(3)) * scale,
                    b.getFloat(2) * scale, b.getFloat(3) * scale);
        }
        savedTransform.set(batch.getTransformMatrix());
        textTransform.set(savedTransform).translate(scratch.x, scratch.y + scratch.height, 0)
                .scale(scale, scale, 1);
        batch.setTransformMatrix(textTransform);
        for (TextField field : fields) {
            JsonValue b = field.source.get("bounds");
            if (b.getFloat(0) >= 365 || b.getFloat(1) >= 275) continue;
            if ("propaganda2".equals(key) && "text1".equals(field.source.getString("name"))) continue;
            font.getData().setScale(field.scale);
            boolean massMediaValue = key.startsWith("religion-")
                    && "text3".equals(field.source.getString("name"));
            // wd2/iphone_ui.py applies Matrix(ty=7) to the copied text3 clip.
            float baseline = massMediaValue
                    ? b.getFloat(1) + 4f + MASS_MEDIA_VALUE_OFFSET_Y
                    : b.getFloat(1) + 4f;
            font.draw(batch, field.glyph, field.x, -baseline);
        }
        if ("propaganda2".equals(key)) {
            propagandaFont.getData().setScale(.25f);
            if (propagandaOpinion != opinion) {
                propagandaOpinion = opinion;
                propagandaLayout.setText(propagandaFont, "Propaganda strength: " + opinion + " %",
                        new Color(1, .47f, 0, 1), 98.2f, Align.right, true);
            }
            propagandaFont.draw(batch, propagandaLayout, 37, -125.75f);
        }
        batch.setTransformMatrix(savedTransform);
        boolean pending = proposal != null && !state.hasPendingError();
        if (proposal != null ? pending : state.isAcceptVisible()) button(batch, accept, 279.15f, scale);
        if (proposal != null ? pending : state.isCancelVisible()) button(batch, cancel, 28.15f, scale);
        batch.setColor(Color.WHITE);
        if (!screen.getBoolean("map"))
            batch.draw(sourceGlass, scratch.x, scratch.y, scratch.width, scratch.height);
    }

    private void button(SpriteBatch batch, Texture image, float x, float scale) {
        JsonValue origin = screen.get("origin");
        batch.draw(image, scratch.x + (x + origin.getFloat(0)) * scale,
                scratch.y + scratch.height - (270.45f + origin.getFloat(1)) * scale,
                55 * scale, 24 * scale);
    }

    public String controlAt(Rectangle container, float x, float y, GameState game,
                            SourceActionMenuState state) {
        prepare(game, state);
        if (screen == null) return null;
        fit(container, scratch);
        float sourceX = (x - scratch.x) * 365f / scratch.width;
        float sourceY = (scratch.y + scratch.height - y) * 275f / scratch.height;
        boolean pending = game.getPendingHumanDiplomacyProposal() != null && !state.hasPendingError();
        JsonValue origin = screen.get("origin");
        float footerY = 246.45f + origin.getFloat(1);
        if (inside(sourceX, sourceY, 279.15f + origin.getFloat(0), footerY, 55, 24)
                && (game.getPendingHumanDiplomacyProposal() != null ? pending : state.isAcceptVisible())) return "btnAccept";
        if (inside(sourceX, sourceY, 28.15f + origin.getFloat(0), footerY, 55, 24)
                && (game.getPendingHumanDiplomacyProposal() != null ? pending : state.isCancelVisible())) return "btnCancel";
        if ("diplomacy".equals(key)) {
            JsonValue leaders = screen.get("portraits");
            for (int i = 0; i < leaders.size; i++) {
                if (inside(sourceX, sourceY, leaders.get(i).get("bounds"))) return "leader" + (i + 1);
            }
            // The original entire sentence selects its duration/amount option.
            for (int i = 0; i < 6; i++) {
                if (inside(sourceX, sourceY, i < 3 ? 18 : 196,
                        160 + (i % 3) * 20, 166, 18)) return "btn" + (i + 1);
            }
        }
        for (JsonValue control : screen.get("controls")) {
            String name = control.getString("name");
            if (name.equals("btnAccept") || name.equals("btnCancel")) continue;
            if (inside(sourceX, sourceY, control.get("bounds"))) return name;
        }
        return null;
    }

    private static boolean inside(float x, float y, JsonValue b) {
        return b != null && !b.isNull() && inside(x, y, b.getFloat(0), b.getFloat(1), b.getFloat(2), b.getFloat(3));
    }
    private static boolean inside(float x, float y, float bx, float by, float w, float h) {
        return x >= bx && x <= bx + w && y >= by && y <= by + h;
    }
    private static Texture texture(String name) {
        Texture result = new Texture(Gdx.files.internal(DIRECTORY + name + ".png"));
        result.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return result;
    }
    @Override public void dispose() {
        for (Texture texture : cache.values()) texture.dispose();
        cache.clear(); accept.dispose(); cancel.dispose(); selected.dispose(); readiness.dispose(); mediaOpinion.dispose(); font.dispose();
    }
}
