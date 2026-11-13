package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.ui.BattlefieldCityOrderMarkers;

import java.util.List;
import java.util.Objects;

/** The battle root's direct children, including authored and dynamically attached clips. */
public final class BattlefieldDisplayState {
    public enum Kind {
        AUTHORED, CITY, CITY_MARKER, FLAG, ACTION_BUILDING,
        UNIT_BODY, UNIT_SHADOW, UNIT_TINT, UNIT_DOT, EFFECT
    }

    public static final class Item {
        public final Kind kind;
        public final int index;
        public final String name;
        /** Current animation for a persistent authored slot; assigned after route construction. */
        public Object owner;

        private Item(Kind kind, int index, String name, Object owner) {
            this.kind = kind;
            this.index = index;
            this.name = name;
            this.owner = owner;
        }
    }

    private static final int PLAYERS = BattlefieldCityLayout.PLAYER_COUNT;
    private static final int CITIES_PER_PLAYER = BattlefieldCityLayout.CITIES_PER_PLAYER;
    private final SourceDisplayList<Item> clips = new SourceDisplayList<>(160);
    @SuppressWarnings("unchecked")
    private final SourceDisplayList.Entry<Item>[] cities = new SourceDisplayList.Entry[
            PLAYERS * CITIES_PER_PLAYER];
    @SuppressWarnings("unchecked")
    private final SourceDisplayList.Entry<Item>[] cityMarkers = new SourceDisplayList.Entry[
            PLAYERS * CITIES_PER_PLAYER];
    @SuppressWarnings("unchecked")
    private final SourceDisplayList.Entry<Item>[] flags = new SourceDisplayList.Entry[PLAYERS];
    @SuppressWarnings("unchecked")
    private final SourceDisplayList.Entry<Item>[] actionBuildings =
            new SourceDisplayList.Entry[PLAYERS * 2];
    private final float[] actionBuildingY = new float[PLAYERS * 2];

    public SourceDisplayList<Item> clips() { return clips; }

    /** Detaches the parent and releases all session-specific clip owners. */
    public void clear() {
        clips.clear();
        java.util.Arrays.fill(cities, null);
        java.util.Arrays.fill(cityMarkers, null);
        java.util.Arrays.fill(flags, null);
        java.util.Arrays.fill(actionBuildings, null);
    }

    /** Recreates the source battle parent before dynamically spawned units are attached. */
    public void reset(FlashAnimationTimeline.Frame authored, GameState game, boolean battle) {
        Objects.requireNonNull(authored, "authored");
        Objects.requireNonNull(game, "game");
        clear();

        for (FlashAnimationTimeline.Layer layer : authored.layers) {
            String name = layer.name;
            int player = namedPlayer(name, "flag");
            if (player >= 0) {
                flags[player] = attach(Kind.FLAG, player, name, null, layer.depth);
                continue;
            }
            int slotIndex = namedActionBuilding(name);
            if (slotIndex >= 0) {
                actionBuildings[slotIndex] = attach(Kind.ACTION_BUILDING, slotIndex,
                        name, null, layer.depth);
                actionBuildingY[slotIndex] = layer.ty;
                continue;
            }
            attach(Kind.AUTHORED, layer.depth, name, null, layer.depth);
        }

        // GameState keeps placeholder cities for unselected opponents. Source
        // Model.playerArr contains only selected nations; color zero marks placeholders.
        for (int playerId = 0; playerId < PLAYERS; playerId++) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            if (player == null || player.getPlayerColor() == 0) continue;
            for (int cityIndex = 0; cityIndex < CITIES_PER_PLAYER; cityIndex++) {
                if (player.getCity(cityIndex) == null) continue;
                int address = playerId * CITIES_PER_PLAYER + cityIndex;
                cities[address] = attach(Kind.CITY, address, "city" + address, null,
                        BattlefieldCityLayout.sourceDepth(address));
            }
        }

        if (!battle) return;
        // ActionScript `for-in` visits the selected player array in reverse order.
        // View.setABDepth swaps slot 1, slot 2, then the flag for each player.
        for (int playerId = PLAYERS - 1; playerId >= 0; playerId--) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            if (player == null || player.getPlayerColor() == 0) continue;
            int first = playerId * 2;
            int second = first + 1;
            if (actionBuildings[first] != null) {
                clips.swapDepths(actionBuildings[first],
                        sourceActionDepth(actionBuildingY[first], 2));
            }
            if (actionBuildings[second] != null) {
                int row = sourceActionDepth(actionBuildingY[second], 0);
                clips.swapDepths(actionBuildings[second], row + 1);
                if (flags[playerId] != null) clips.swapDepths(flags[playerId], row + 3);
            }
        }
    }

    /** Rebinds physical action-building slots without changing their display depths. */
    public void bindActionBuildings(List<FlashActionBuildingAnimation> animations) {
        Objects.requireNonNull(animations, "animations");
        for (SourceDisplayList.Entry<Item> entry : actionBuildings) {
            if (entry != null) entry.getValue().owner = null;
        }
        for (FlashActionBuildingAnimation animation : animations) {
            Objects.requireNonNull(animation, "animation");
            int index = actionIndex(animation.playerId, animation.slot);
            SourceDisplayList.Entry<Item> entry = actionBuildings[index];
            if (entry != null && entry.isAttached()) entry.getValue().owner = animation;
        }
    }

    public SourceDisplayList.Entry<Item> city(int address) {
        if (address < 0 || address >= cities.length) throw new IndexOutOfBoundsException();
        return cities[address];
    }

    public SourceDisplayList.Entry<Item> marker(int address) {
        if (address < 0 || address >= cityMarkers.length) throw new IndexOutOfBoundsException();
        return cityMarkers[address];
    }

    /** Creates or removes source city icon parents only when their occupancy changes. */
    public void syncCityMarkers(GameState game) {
        Objects.requireNonNull(game, "game");
        for (int address = 0; address < cityMarkers.length; address++) {
            int player = address / CITIES_PER_PLAYER;
            int cityIndex = address % CITIES_PER_PLAYER;
            boolean marked = cities[address] != null && cities[address].isAttached()
                    && BattlefieldCityOrderMarkers.iconCount(game, player, cityIndex) > 0;
            SourceDisplayList.Entry<Item> current = cityMarkers[address];
            if (marked) {
                if (current == null || !current.isAttached()) {
                    cityMarkers[address] = attach(Kind.CITY_MARKER, address,
                            "cityMarker" + address, null, cities[address].getDepth() + 1500);
                }
            } else if (current != null) {
                clips.remove(current);
                cityMarkers[address] = null;
            }
        }
    }

    public SourceDisplayList.Entry<Item> flag(int island) {
        if (island < 0 || island >= PLAYERS) throw new IndexOutOfBoundsException();
        return flags[island];
    }

    public SourceDisplayList.Entry<Item> actionBuilding(int playerId, int slot) {
        return actionBuildings[actionIndex(playerId, slot)];
    }

    public SourceDisplayList.Entry<Item> attach(Kind kind, int index, String name,
                                                 Object owner, int depth) {
        return clips.attach(new Item(Objects.requireNonNull(kind, "kind"), index,
                name, owner), depth);
    }

    public void remove(SourceDisplayList.Entry<Item> entry) { clips.remove(entry); }

    public int nextDepth() { return clips.nextHighestDepth(); }

    private static int actionIndex(int playerId, int slot) {
        if (playerId < 0 || playerId >= PLAYERS || slot < 1 || slot > 2) {
            throw new IndexOutOfBoundsException("Invalid action-building slot");
        }
        return playerId * 2 + slot - 1;
    }

    private static int sourceActionDepth(float y, int offset) {
        return (int) Math.floor(y / 10.0) * 500 + offset;
    }

    private static int namedPlayer(String name, String prefix) {
        return name != null && name.length() == prefix.length() + 1
                && name.startsWith(prefix) && name.charAt(prefix.length()) >= '0'
                && name.charAt(prefix.length()) < '0' + PLAYERS
                ? name.charAt(prefix.length()) - '0' : -1;
    }

    private static int namedActionBuilding(String name) {
        if (name == null || name.length() != 5 || name.charAt(0) != 'a'
                || name.charAt(1) != 'b' || name.charAt(2) < '0'
                || name.charAt(2) >= '0' + PLAYERS || name.charAt(3) != '_'
                || name.charAt(4) < '1' || name.charAt(4) > '2') return -1;
        return (name.charAt(2) - '0') * 2 + name.charAt(4) - '1';
    }
}
