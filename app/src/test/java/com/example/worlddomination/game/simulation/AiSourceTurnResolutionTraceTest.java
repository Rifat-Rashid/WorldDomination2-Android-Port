package com.example.worlddomination.game.simulation;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Seeded regression for source result ordering, score RNG, and production. */
public final class AiSourceTurnResolutionTraceTest {
    private static final int[][] EXPECTED_PLAYER_STATE = {
            // money, population, religion, score
            {86, 400, 74, 18146},
            {98, 450, 52, 16188},
            {101, 455, 50, 17252},
            {63, 450, 51, 44504},
            {78, 445, 53, 33834}
    };

    private static final int[][] EXPECTED_CITY_POPULATIONS = {
            {73, 47, 80, 95, 105},
            {59, 97, 54, 84, 156},
            {58, 81, 111, 79, 126},
            {66, 110, 67, 96, 111},
            {107, 41, 67, 43, 187}
    };

    private static final List<String> EXPECTED_RANDOM_DRAWS = Arrays.asList(
            "91:30", "3:2", "91:52", "3:0", "91:0", "3:2", "91:31", "3:1",
            "2:0", "3:1", "2:1", "141:43", "4:0", "21:20", "61:55", "4:0",
            "21:11", "61:41",
            "6:2", "6:4", "6:5", "6:3", "4:1", "3:0", "6:1",
            "11:1", "11:1", "11:2", "11:9", "11:5", "11:8", "11:10", "11:8",
            "11:0", "11:10", "11:1", "11:8", "11:9", "11:7", "11:4",
            "51:3", "51:4", "51:32", "51:12", "51:27", "51:13", "51:8", "6:1",
            "2:1", "51:24", "2:0", "51:24", "2:1", "51:26", "2:0", "51:49",
            "2:1", "2:0", "51:16", "2:0", "51:18", "2:0", "51:37", "2:1",
            "51:21");

    private static final int[][] EXPECTED_SECOND_TURN_PLAYER_STATE = {
            {117, 352, 93, 38992},
            {53, 450, 47, 191328},
            {67, 455, 43, 123816},
            {91, 450, 43, 70760},
            {63, 445, 45, 51948}
    };

    private static final int[][] EXPECTED_SECOND_TURN_CITY_POPULATIONS = {
            {73, 47, 80, 47, 105},
            {59, 97, 54, 84, 156},
            {58, 81, 111, 79, 126},
            {66, 110, 67, 96, 111},
            {107, 41, 67, 43, 187}
    };

    // nextDouble() for the strike casualty is checked by the 48M combat event
    // below; this list records every bounded draw surrounding that raw draw.
    private static final List<String> EXPECTED_SECOND_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:81", "3:0", "91:52", "3:2", "91:50", "3:1", "91:33", "3:2",
            "3:2", "5:1", "141:50", "4:2", "21:10", "61:31", "61:11", "5:2",
            "5:3", "5:3", "5:2", "2:0", "6:3", "6:3", "6:2", "6:0", "6:1",
            "51:42", "11:5", "11:8", "11:2", "11:2", "11:5", "11:7",
            "11:0", "11:5", "11:9", "11:6", "11:10", "11:1", "11:9", "11:9",
            "11:0", "51:27", "51:38", "51:11", "51:33", "51:43", "51:40", "51:21",
            "51:2", "51:27", "51:9", "51:27", "51:13", "51:17", "1:0", "51:41",
            "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0",
            "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0",
            "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0",
            "1:0", "1:0", "1:0", "1:0");

    private static final int[][] EXPECTED_THIRD_TURN_PLAYER_STATE = {
            {144, 352, 100, 57032},
            {51, 450, 40, 239658},
            {58, 455, 33, 142056},
            {66, 450, 36, 97052},
            {100, 445, 38, 69634}
    };

    private static final int[][] EXPECTED_THIRD_TURN_CITY_POPULATIONS = {
            {73, 47, 80, 47, 105},
            {59, 97, 54, 84, 156},
            {58, 81, 111, 79, 126},
            {66, 110, 67, 96, 111},
            {107, 41, 67, 43, 187}
    };

    private static final int[][] EXPECTED_FOURTH_TURN_PLAYER_STATE = {
            {128, 352, 100, 75958},
            {132, 450, 34, 255390},
            {99, 455, 51, 164282},
            {104, 450, 30, 112632},
            {40, 445, 32, 132278}
    };

    private static final int[][] EXPECTED_FOURTH_TURN_CITY_POPULATIONS = {
            {73, 47, 80, 47, 105},
            {59, 97, 54, 84, 156},
            {58, 81, 111, 79, 126},
            {66, 110, 67, 96, 111},
            {107, 41, 67, 43, 187}
    };

    private static final int[][] EXPECTED_FIFTH_TURN_PLAYER_STATE = {
            {155, 352, 100, 94356},
            {72, 450, 26, 320014},
            {60, 455, 43, 277566},
            {69, 450, 21, 149164},
            {40, 445, 22, 132278}
    };

    private static final int[][] EXPECTED_FIFTH_TURN_CITY_POPULATIONS = {
            {73, 47, 80, 47, 105},
            {59, 97, 54, 84, 156},
            {58, 81, 111, 79, 126},
            {66, 110, 67, 96, 111},
            {107, 41, 67, 43, 187}
    };

    private static final int[][] EXPECTED_SIXTH_TURN_PLAYER_STATE = {
            {182, 352, 100, 113328},
            {110, 450, 42, 341772},
            {101, 475, 37, 293526},
            {54, 450, 37, 166320},
            {40, 425, 37, 137578}
    };

    private static final int[][] EXPECTED_SIXTH_TURN_CITY_POPULATIONS = {
            {73, 47, 80, 47, 105},
            {59, 97, 54, 84, 156},
            {62, 85, 115, 83, 130},
            {66, 110, 67, 96, 111},
            {107, 41, 67, 43, 167}
    };

    private static final int[][] EXPECTED_SEVENTH_TURN_PLAYER_STATE = {
            {209, 352, 100, 130580},
            {90, 450, 34, 365972},
            {104, 475, 27, 342986},
            {92, 450, 27, 185244},
            {10, 425, 30, 161876}
    };

    private static final int[][] EXPECTED_SEVENTH_TURN_CITY_POPULATIONS = {
            {73, 47, 80, 47, 105},
            {59, 97, 54, 84, 156},
            {62, 85, 115, 83, 130},
            {66, 110, 67, 96, 111},
            {107, 41, 67, 43, 167}
    };

    private static final int[][] EXPECTED_EIGHTH_TURN_PLAYER_STATE = {
            {236, 334, 100, 147088},
            {5, 450, 28, 424890},
            {107, 475, 17, 371586},
            {92, 450, 45, 191644},
            {10, 425, 50, 212592}
    };

    private static final int[][] EXPECTED_EIGHTH_TURN_CITY_POPULATIONS = {
            {73, 29, 80, 47, 105},
            {59, 97, 54, 84, 156},
            {62, 85, 115, 83, 130},
            {66, 110, 67, 96, 111},
            {107, 41, 67, 43, 167}
    };

    private static final int[][] EXPECTED_NINTH_TURN_PLAYER_STATE = {
            {201, 334, 100, 165352},
            {43, 432, 45, 449110},
            {53, 475, 11, 425452},
            {92, 470, 38, 191644},
            {71, 425, 45, 212592}
    };

    private static final int[][] EXPECTED_NINTH_TURN_CITY_POPULATIONS = {
            {73, 29, 80, 47, 105},
            {59, 97, 54, 84, 138},
            {62, 85, 115, 83, 130},
            {70, 114, 71, 100, 115},
            {107, 41, 67, 43, 167}
    };

    private static final int[][] EXPECTED_TENTH_TURN_PLAYER_STATE = {
            {145, 334, 100, 184144},
            {131, 432, 37, 464014},
            {96, 475, 36, 448332},
            {72, 470, 57, 215138},
            {51, 425, 40, 253022}
    };

    private static final int[][] EXPECTED_TENTH_TURN_CITY_POPULATIONS = {
            {73, 29, 80, 47, 105},
            {59, 97, 54, 84, 138},
            {62, 85, 115, 83, 130},
            {70, 114, 71, 100, 115},
            {107, 41, 67, 43, 167}
    };

    private static final int[][] EXPECTED_ELEVENTH_TURN_PLAYER_STATE = {
            {171, 334, 100, 200686},
            {19, 432, 30, 528722},
            {73, 475, 28, 482768},
            {91, 470, 50, 248932},
            {21, 425, 33, 277378}
    };

    private static final int[][] EXPECTED_ELEVENTH_TURN_CITY_POPULATIONS = {
            {73, 29, 80, 47, 105},
            {59, 97, 54, 84, 138},
            {62, 85, 115, 83, 130},
            {70, 114, 71, 100, 115},
            {107, 41, 67, 43, 167}
    };

    // Source trace includes one empty-range random() call that Java records
    // separately from bounded nextInt draws.
    private static final List<String> EXPECTED_FOURTH_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:21", "3:2", "91:84", "3:2", "91:44", "3:1", "91:36", "3:2",
            "2:0", "3:2", "1:0", "141:4", "5:2", "5:0", "4:1", "21:0",
            "61:7", "61:26", "4:0", "21:6", "61:37", "6:1", "6:1", "6:2",
            "6:1", "6:3", "9:6", "31:0", "4:2", "11:5", "11:10", "11:7",
            "11:3", "11:6", "11:9", "11:10", "11:8", "11:1", "11:4", "11:5",
            "11:3", "11:8", "11:5", "11:10", "51:0", "51:41", "51:5", "51:26",
            "51:7", "51:19", "51:31", "51:48", "3:1", "51:5", "3:2", "3:0",
            "51:10");

    private static final List<String> EXPECTED_FIFTH_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:73", "3:0", "91:80", "3:0", "91:90", "3:0", "91:8", "3:1",
            "61:18", "4:2", "21:6", "61:20", "61:1", "61:5", "61:0", "4:1",
            "21:18", "61:32", "5:1", "5:2", "5:3", "5:4", "4:0", "21:13",
            "61:18", "6:5", "6:4", "6:3", "6:3", "6:4", "11:0", "11:3",
            "11:0", "11:1", "11:10", "11:6", "11:2", "11:3", "11:4", "11:0",
            "11:8", "11:4", "11:0", "11:5", "11:4", "51:38", "51:13", "51:12",
            "51:25", "51:20", "51:6", "51:37", "51:44", "51:42", "51:33", "3:1",
            "51:40", "3:0", "51:5", "1:0", "51:27", "3:0", "3:1", "51:41",
            "3:1", "3:0", "3:0", "3:0", "3:0", "3:0", "3:2", "3:2",
            "3:1", "3:0", "3:1", "3:2", "3:0", "3:1", "3:2", "3:0",
            "3:1", "3:2", "3:1", "3:0", "3:2", "3:1", "3:1", "3:1",
            "3:2", "3:1", "3:2", "3:0", "3:0", "3:0");

    private static final List<String> EXPECTED_SIXTH_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:65", "3:2", "91:0", "3:0", "91:34", "3:1", "91:36", "3:0",
            "4:0", "21:14", "61:36", "5:4", "5:1", "5:2", "6:5", "6:4",
            "6:1", "6:4", "6:2", "11:0", "11:1", "11:5", "11:3", "11:2",
            "11:7", "11:2", "11:4", "11:5", "11:0", "11:8", "11:2", "11:1",
            "11:5", "11:2", "51:1", "51:5", "51:2", "51:18", "51:16", "51:10",
            "51:8", "51:43", "6:3", "3:1", "51:0", "3:2", "3:0", "3:2",
            "3:1", "3:1", "3:1", "3:1", "3:1", "3:2", "3:0", "3:0",
            "3:1", "3:0", "3:1", "3:1", "3:0", "3:2", "3:0", "3:1",
            "3:2", "3:2", "3:2", "3:2", "3:0", "3:0", "3:1", "3:2",
            "3:1", "3:1", "3:2", "3:0");

    private static final List<String> EXPECTED_SEVENTH_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:51", "3:1", "91:80", "3:0", "91:49", "3:1", "91:64", "3:2",
            "61:45", "4:0", "21:20", "61:2", "4:2", "21:6", "61:6", "2:0",
            "3:0", "2:0", "141:136", "4:3", "21:7", "61:30", "61:36", "61:19",
            "61:8", "6:2", "6:5", "6:5", "6:3", "6:4", "11:4", "11:8",
            "11:8", "11:9", "11:2", "11:5", "11:0", "11:6", "11:5", "11:6",
            "11:5", "11:3", "11:1", "11:3", "11:8", "51:14", "51:49", "51:42",
            "51:13", "51:23", "51:40", "51:0", "1:0", "51:34", "2:0", "51:33",
            "2:0", "51:14", "2:1", "2:1", "2:0", "51:13", "1:0", "51:15");

    // The source's 15..18 strike casualty roll maps to nextDouble in Java and
    // is asserted separately through RecordingRandom.rawDraws.
    private static final List<String> EXPECTED_EIGHTH_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:20", "3:0", "91:45", "3:2", "91:74", "3:1", "91:85", "3:1",
            "61:50", "4:3", "21:15", "61:55", "61:36", "61:53", "61:16", "61:30",
            "4:2", "21:11", "61:3", "3:0", "5:3", "5:1", "141:98", "5:3",
            "5:2", "6:0", "6:2", "6:5", "6:1", "6:0", "51:18",
            "11:2", "11:5", "11:10", "11:6", "11:2", "11:0", "11:4", "11:2",
            "11:10", "11:9", "11:10", "11:10", "11:3", "11:6", "11:6", "51:27",
            "51:27", "51:11", "51:7", "51:2", "51:35", "51:8", "4:3", "51:33",
            "4:1", "51:45", "4:0", "51:10", "4:3", "4:1", "4:3", "4:1",
            "4:0", "51:47", "4:3", "51:23", "4:2", "4:2", "4:0", "51:38");

    // Turn nine includes an empty-range source random call, counted separately.
    private static final List<String> EXPECTED_NINTH_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:44", "3:2", "91:0", "3:1", "91:17", "3:2", "91:2", "3:0",
            "4:1", "21:17", "61:11", "61:41", "61:48", "61:36", "4:1", "21:15",
            "61:7", "2:0", "3:2", "2:0", "141:36", "5:0", "5:2", "5:4",
            "5:0", "5:2", "5:4", "6:0", "6:2", "6:1", "6:3", "6:5",
            "7:6", "21:6", "4:0", "11:5", "11:10", "11:2", "11:2", "11:4",
            "11:10", "11:3", "11:2", "11:4", "11:5", "11:2", "11:8", "11:2",
            "11:5", "11:3", "51:45", "51:49", "51:23", "51:20", "51:20", "51:32",
            "6:0", "2:0", "24:6", "2:1", "24:8", "2:0", "24:13", "2:1",
            "24:22", "2:0", "24:4", "2:1", "24:20", "2:0", "24:12", "2:0",
            "24:23", "2:1", "24:5", "2:1", "24:3", "2:0", "24:22", "2:0",
            "24:22", "2:1", "24:15", "2:1", "24:17", "2:0", "24:10", "2:0",
            "24:5", "2:0", "24:16", "2:0", "24:1", "2:1", "24:15", "2:1",
            "24:13", "2:0", "24:19", "2:1", "24:13", "2:1", "24:8", "2:0",
            "24:0", "2:0", "24:12", "2:0", "24:22", "2:1", "24:7", "2:0",
            "24:1", "2:1", "24:18", "2:0", "24:18", "2:0", "24:7", "2:0",
            "24:0", "4:0", "51:45", "4:0", "51:4", "4:2", "51:16", "4:2",
            "51:1", "4:0", "51:24", "4:3", "4:0", "4:0", "4:3", "4:3",
            "4:2", "4:2", "4:1", "4:0", "4:1", "4:1", "4:2", "4:3",
            "4:3", "4:2", "4:0", "4:2", "4:0", "4:0", "4:1", "4:3",
            "4:1", "4:2", "4:3", "4:2", "4:0", "4:2");

    // The source has one empty-range random draw during turn-ten AI planning.
    private static final List<String> EXPECTED_TENTH_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:24", "3:0", "91:5", "3:2", "91:83", "3:0", "91:37", "3:2",
            "2:1", "5:3", "4:0", "141:49", "7:0", "5:1", "5:3", "61:15",
            "61:59", "4:1", "21:10", "61:33", "4:2", "21:14", "61:31", "61:49",
            "61:35", "2:1", "5:4", "4:3", "141:132", "2:1", "5:2", "4:0",
            "141:34", "7:3", "5:3", "5:2", "6:0", "6:1", "6:3", "6:1",
            "7:6", "21:6", "4:0", "5:4", "11:5", "5:1", "11:1", "11:5",
            "11:10", "11:3", "11:3", "11:9", "11:7", "11:8", "11:0", "11:9",
            "11:10", "11:4", "11:8", "11:1", "11:2", "51:46", "51:11", "51:19",
            "51:16", "51:7", "51:46", "3:0", "3:2", "51:17", "3:1", "3:1",
            "3:0", "3:1", "3:2", "3:1", "3:0", "3:1", "3:2", "3:2",
            "3:1", "3:2", "3:1", "3:2", "3:1", "3:0", "3:2", "3:2",
            "3:0", "3:1", "3:1", "3:2", "3:2", "3:0", "3:1", "3:1",
            "3:1", "3:0", "3:1", "3:2", "3:0", "51:47", "3:2", "51:20",
            "3:0", "51:10", "3:2", "3:1", "3:2", "3:1", "3:1", "3:0",
            "3:0", "3:1", "3:0", "3:1", "3:1", "3:0", "3:0", "3:2",
            "3:2", "3:2", "3:0", "3:1", "3:2", "3:0", "3:1", "3:2",
            "3:2", "3:0", "3:1", "3:1", "3:1", "3:2", "3:0", "3:2",
            "51:19", "3:2", "3:2", "3:1", "3:0", "51:11");

    // Source turn eleven has no raw empty-range draws.
    private static final List<String> EXPECTED_ELEVENTH_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:85", "3:1", "91:71", "3:0", "91:72", "3:0", "91:60", "3:0",
            "61:44", "4:2", "21:20", "61:12", "61:25", "61:54", "4:2", "21:2",
            "61:6", "61:36", "4:0", "21:1", "61:27", "4:1", "21:9", "61:29",
            "61:40", "2:0", "3:2", "2:0", "141:112", "6:2", "6:2", "6:3",
            "6:2", "6:2", "11:10", "11:6", "11:6", "11:2", "11:10", "11:7",
            "11:3", "11:10", "11:2", "11:10", "11:10", "11:8", "11:3", "11:10",
            "11:5", "51:0", "51:23", "51:27", "51:21", "51:0", "2:0", "24:5",
            "2:0", "24:6", "2:1", "24:19", "2:1", "24:12", "2:0", "24:10",
            "2:0", "24:9", "2:0", "24:23", "2:1", "24:9", "2:1", "24:14",
            "2:1", "24:19", "2:1", "24:6", "2:1", "24:21", "2:0", "24:21",
            "2:0", "24:1", "2:0", "24:8", "2:0", "24:6", "2:1", "24:14",
            "2:0", "24:21", "2:1", "24:23", "2:1", "24:23", "2:1", "24:22",
            "2:0", "24:23", "2:0", "24:17", "2:1", "24:2", "2:1", "24:8",
            "2:1", "24:23", "2:0", "24:23", "2:0", "24:5", "2:1", "24:18",
            "2:0", "24:9", "2:0", "24:17", "2:0", "24:13", "3:2", "51:37",
            "3:0", "51:12", "3:0", "51:22", "3:2", "3:1", "3:1", "3:1",
            "3:2", "3:2", "3:2", "3:0", "3:0", "3:1", "3:0", "3:0",
            "3:0", "3:0", "3:0", "3:0", "3:2", "3:2", "3:0", "3:2",
            "3:2", "3:2", "3:2", "3:0", "3:2", "3:0", "3:1", "3:2",
            "3:0", "3:0", "51:47", "3:1", "51:9", "2:0", "24:8", "2:0",
            "24:19", "2:0", "24:14", "2:1", "24:22", "2:1", "24:13", "2:0",
            "24:14", "2:0", "24:3", "2:1", "24:22", "2:1", "24:2", "2:1",
            "24:17", "2:1", "24:9", "2:0", "24:18", "2:0", "24:8", "2:0",
            "24:14", "2:0", "24:18", "2:0", "24:21", "2:0", "24:10", "2:0",
            "24:7", "2:1", "24:9", "2:0", "24:2", "2:1", "24:0", "2:1",
            "24:11", "2:1", "24:2", "2:1", "24:3", "2:1", "24:16", "2:1",
            "24:14", "2:1", "24:14", "2:0", "24:5", "2:0", "24:20", "2:1",
            "24:11", "2:1", "24:18", "2:1", "24:16", "3:0", "51:28", "3:0",
            "51:39", "3:1", "3:0", "3:1", "3:2", "3:2", "3:1", "3:0",
            "3:0", "3:1", "3:0", "3:1", "3:1", "3:1", "3:2", "3:1",
            "3:2", "3:0", "3:2", "3:1", "3:1", "3:1", "3:0", "3:1",
            "3:0", "3:1", "3:1", "3:0", "3:1", "3:0", "3:0", "3:2",
            "51:49", "1:0", "51:15", "1:0", "1:0", "1:0", "1:0", "1:0",
            "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0",
            "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0",
            "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0",
            "1:0", "1:0", "3:0", "51:7", "3:2", "51:45");

    // Source trace collected from Model.doTurn through Sub_presenter.turnResults.
    // Empty and zero-width ranges remain because they consume source RNG calls.
    private static final List<String> EXPECTED_THIRD_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:69", "3:0", "91:50", "3:0", "91:48", "3:0", "91:73", "3:2",
            "4:3", "21:8", "61:37", "61:10", "61:43", "5:3", "5:2", "5:1",
            "5:3", "2:1", "61:48", "4:1", "21:5", "61:15", "4:0", "21:0",
            "61:7", "6:2", "6:2", "6:5", "6:2", "6:1", "11:10", "11:0",
            "11:3", "11:8", "11:3", "11:10", "11:8", "11:3", "11:9", "11:10",
            "11:5", "11:1", "11:1", "11:8", "11:9", "51:33", "51:39", "51:40",
            "51:12", "51:10", "51:44", "3:2", "51:22", "3:0", "51:4",
            "3:2", "51:10", "3:0", "51:1", "1:0", "51:3", "1:0", "1:0",
            "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0",
            "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0",
            "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0", "1:0",
            "1:0", "1:0", "1:0", "1:0", "1:0");

    @Test
    public void hardSeededFirstTurnMatchesSourceResolutionAndRandomTrace() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        // Initialization uses raw random() calls for city population splits.
        // Keep the recorder transparent, then compare only the turn trace below.
        random.clear();

        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();

        assertEquals(1, game.getTurn());
        for (int playerId = 0; playerId < EXPECTED_PLAYER_STATE.length; playerId++) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            assertEquals("money for player " + playerId,
                    EXPECTED_PLAYER_STATE[playerId][0], player.moneyMillions);
            assertEquals("population for player " + playerId,
                    EXPECTED_PLAYER_STATE[playerId][1], player.populationMillions);
            assertEquals("religion for player " + playerId,
                    EXPECTED_PLAYER_STATE[playerId][2], player.religion);
            assertEquals("score for player " + playerId,
                    EXPECTED_PLAYER_STATE[playerId][3], player.score);
            for (int cityIndex = 0; cityIndex < EXPECTED_CITY_POPULATIONS[playerId].length;
                 cityIndex++) {
                assertEquals("city " + cityIndex + " for player " + playerId,
                        EXPECTED_CITY_POPULATIONS[playerId][cityIndex],
                        player.getCity(cityIndex).populationMillions);
            }
        }

        assertEquals(0, game.getPlayerState(1).getWeaponStockByName("plane1"));
        assertEquals(5, game.getPlayerState(3).getWeaponStockByName("soldier1"));
        assertEquals(3, game.getPlayerState(3).getWeaponStockByName("soldier2"));
        assertEquals(4, game.getPlayerState(4).getWeaponStockByName("warhead1"));
        assertEquals(2, game.getPlayerState(4).getWeaponStockByName("warhead2"));
        assertEquals(Arrays.asList(GameState.Action.RESOURCES,
                        GameState.Action.PREPARED_ATTACK),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PROPAGANDA),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(4).getLastTurnActions());
        assertEquals("all AI, upkeep, timer, score, and production draws match source",
                EXPECTED_RANDOM_DRAWS, random.draws);

    }

    @Test
    public void hardSeededSecondTurnMatchesSourceActionSlotsAndState() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        queueHumanTurn(game);
        game.confirmTurn();
        assertEquals(1, game.getTurn());
        random.clear();
        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(2, game.getTurn());
        GameState.CombatEvent strike = game.getLastTurnCombatEvents().get(0);
        assertEquals(1, strike.attackerPlayerId);
        assertEquals(0, strike.defenderPlayerId);
        assertEquals(3, strike.cityIndex);
        assertEquals("warhead2", strike.payloadName);
        assertEquals("turn-two strike " + strike.attackerPlayerId + "->"
                        + strike.defenderPlayerId + " city=" + strike.cityIndex
                        + " weapon=" + strike.weaponName + " payload=" + strike.payloadName,
                48, strike.casualtiesMillions);
        assertSeededState(game, EXPECTED_SECOND_TURN_PLAYER_STATE,
                EXPECTED_SECOND_TURN_CITY_POPULATIONS);
        assertEquals("Presenter turn results reduce regard by casualties only", 6.0,
                game.getPlayerState(1).getRegardTowardExact(0), 0.0);
        assertEquals("second turn AI, city, and resolution draws match the source trace",
                EXPECTED_SECOND_TURN_RANDOM_DRAWS, random.draws);
        assertEquals(Arrays.asList(GameState.Action.PREPARED_ATTACK, GameState.Action.RESEARCH),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.RESEARCH),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESEARCH, GameState.Action.DIPLOMACY),
                game.getPlayerState(4).getLastTurnActions());
        assertEquals(2, game.getPlayerState(1).getWeaponStockByName("warhead1"));
        assertEquals(0, game.getPlayerState(1).getWeaponStockByName("warhead2"));
        assertEquals(5, game.getPlayerState(3).getWeaponStockByName("soldier1"));
        assertEquals(4, game.getPlayerState(3).getWeaponStockByName("soldier2"));
        assertTrue(game.getPlayerState(4).hasTechnology("warhead3"));
    }

    @Test
    public void hardSeededThirdTurnMatchesSourceAiChoicesUpkeepAndRandomTrace() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        queueHumanTurn(game);
        game.confirmTurn();
        random.clear();
        queueHumanTurn(game);
        game.confirmTurn();
        random.clear();
        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(3, game.getTurn());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.DIPLOMACY),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESEARCH, GameState.Action.PRODUCTION),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(4).getLastTurnActions());
        assertSeededState(game, EXPECTED_THIRD_TURN_PLAYER_STATE,
                EXPECTED_THIRD_TURN_CITY_POPULATIONS);
        assertEquals("source production buys the available second soldier tier",
                5, game.getPlayerState(3).getWeaponStockByName("soldier2"));
        assertEquals("third turn AI, upkeep, score, and production draws match source",
                EXPECTED_THIRD_TURN_RANDOM_DRAWS, random.draws);
    }

    @Test
    public void hardSeededFourthTurnMatchesSourceSpyMissionAndSettlement() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 3; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            random.clear();
        }
        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(4, game.getTurn());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.ATTACK),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.MASS_MEDIA),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESEARCH, GameState.Action.PRODUCTION),
                game.getPlayerState(4).getLastTurnActions());
        assertSeededState(game, EXPECTED_FOURTH_TURN_PLAYER_STATE,
                EXPECTED_FOURTH_TURN_CITY_POPULATIONS);
        assertEquals(0, game.getPlayerState(1).getWeaponStockByName("spy3"));
        assertEquals("fourth-turn spy and settlement draws match source",
                EXPECTED_FOURTH_TURN_RANDOM_DRAWS, random.draws);
    }

    @Test
    public void hardSeededFifthTurnMatchesSourceResearchProductionAndSettlement() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 4; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            random.clear();
        }
        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(5, game.getTurn());
        assertEquals(Arrays.asList(GameState.Action.RESEARCH, GameState.Action.PRODUCTION),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.RESEARCH),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESEARCH, GameState.Action.PRODUCTION),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.DIPLOMACY, GameState.Action.PRODUCTION),
                game.getPlayerState(4).getLastTurnActions());
        assertSeededState(game, EXPECTED_FIFTH_TURN_PLAYER_STATE,
                EXPECTED_FIFTH_TURN_CITY_POPULATIONS);
        assertEquals("fifth-turn AI planning and settlement draws match source",
                EXPECTED_FIFTH_TURN_RANDOM_DRAWS, random.draws);
        assertEquals("the empty production list consumes one raw random draw",
                1, random.rawDraws);
    }

    @Test
    public void hardSeededSixthTurnMatchesSourcePropagandaAndMultiTurnRandomState() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 5; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            if (priorTurn == 4) {
                GameState.DiplomacyProposal pending =
                        game.getPendingDiplomacyProposal(0, 4);
                assertTrue("source leaves the repeated fifth-turn demand pending",
                        pending != null);
                assertEquals("source fifth-turn demand option", 5, pending.optionIndex);
                assertEquals("source cooldown is set when the proposal is recorded", 2,
                        game.getPlayerState(4).getDiplomacyOfferedLastTurn(0));
            }
            random.clear();
        }
        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(6, game.getTurn());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.MASS_MEDIA),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PROPAGANDA),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.MASS_MEDIA, GameState.Action.PRODUCTION),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.MASS_MEDIA, GameState.Action.DIPLOMACY),
                game.getPlayerState(4).getLastTurnActions());
        assertEquals("sixth-turn AI and settlement draws match source",
                EXPECTED_SIXTH_TURN_RANDOM_DRAWS, random.draws);
        assertSeededState(game, EXPECTED_SIXTH_TURN_PLAYER_STATE,
                EXPECTED_SIXTH_TURN_CITY_POPULATIONS);
        assertEquals("sixth turn has no unbounded random draws", 0, random.rawDraws);
    }

    @Test
    public void hardSeededSeventhTurnMatchesSourceCarrierPreparationAndProduction() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 6; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            assertFalse("source keeps soldier3 locked after turn " + (priorTurn + 1)
                            + ": " + game.getPlayerState(4).getInventedTechnologies(),
                    game.getPlayerState(4).hasTechnology("soldier3"));
            if (priorTurn == 3) {
                assertTrue(game.getPlayerState(4).hasTechnology("warhead4"));
                assertTrue(game.getPlayerState(4).hasTechnology("spy3"));
            }
            random.clear();
        }
        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(7, game.getTurn());
        assertEquals(Arrays.asList(GameState.Action.RESEARCH, GameState.Action.PRODUCTION),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.RESEARCH),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PREPARED_ATTACK,
                        GameState.Action.PRODUCTION),
                game.getPlayerState(4).getLastTurnActions());
        assertEquals("seventh-turn source carrier choice is preserved", "rocket1",
                game.getPreparedCarrierName(4));
        assertEquals("carrier stock is consumed when it is prepared", 1,
                game.getPlayerState(4).getWeaponStockByName("rocket1"));
        assertEquals("source production fills warhead tier two", 4,
                game.getPlayerState(4).getWeaponStockByName("warhead2"));
        assertEquals("source production fills soldier tier one", 5,
                game.getPlayerState(4).getWeaponStockByName("soldier1"));
        assertEquals("source production buys one additional spy", 3,
                game.getPlayerState(4).getWeaponStockByName("spy1"));
        assertEquals("seventh-turn AI and settlement draws match source",
                EXPECTED_SEVENTH_TURN_RANDOM_DRAWS, random.draws);
        assertSeededState(game, EXPECTED_SEVENTH_TURN_PLAYER_STATE,
                EXPECTED_SEVENTH_TURN_CITY_POPULATIONS);
        assertEquals("seventh-turn empty production list consumes a raw draw",
                1, random.rawDraws);
    }

    @Test
    public void hardSeededEighthTurnMatchesPreparedStrikeAndSettlement() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 7; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            random.clear();
        }
        assertEquals("turn seven leaves the source-selected rocket ready",
                "rocket1", game.getPreparedCarrierName(4));

        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(8, game.getTurn());
        assertEquals(Arrays.asList(GameState.Action.RESEARCH, GameState.Action.PRODUCTION),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.RESEARCH),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PRODUCTION, GameState.Action.MASS_MEDIA),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PREPARED_ATTACK, GameState.Action.MASS_MEDIA),
                game.getPlayerState(4).getLastTurnActions());
        assertTrue("source consumes the prepared rocket after its turn-eight strike",
                game.getPreparedCarrierName(4) == null);
        assertEquals("turn-eight AI, strike, and settlement draws match source; actual="
                        + random.draws,
                EXPECTED_EIGHTH_TURN_RANDOM_DRAWS, random.draws);

        StringBuilder combatSummary = new StringBuilder();
        for (GameState.CombatEvent event : game.getLastTurnCombatEvents()) {
            combatSummary.append(event.type).append(':').append(event.attackerPlayerId)
                    .append("->").append(event.defenderPlayerId).append(" city=")
                    .append(event.cityIndex).append(" weapon=").append(event.weaponName)
                    .append(" payload=").append(event.payloadName).append(" casualties=")
                    .append(event.casualtiesMillions).append(';');
        }
        assertEquals("one prepared rocket hits the source-selected city: " + combatSummary,
                1, game.getLastTurnCombatEvents().size());
        GameState.CombatEvent strike = game.getLastTurnCombatEvents().get(0);
        assertEquals(GameState.CombatEventType.STRIKE_IMPACT, strike.type);
        assertEquals(4, strike.attackerPlayerId);
        assertEquals(0, strike.defenderPlayerId);
        assertEquals("prepared strike city: " + combatSummary, 1, strike.cityIndex);
        assertEquals("warhead1", strike.payloadName);
        assertEquals(18, strike.casualtiesMillions);
        assertSeededState(game, EXPECTED_EIGHTH_TURN_PLAYER_STATE,
                EXPECTED_EIGHTH_TURN_CITY_POPULATIONS);
        assertEquals("turn eight strike casualty consumes one raw random draw",
                1, random.rawDraws);
    }

    @Test
    public void hardSeededNinthTurnMatchesSpyDiplomacyAndProductionSettlement() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 8; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            random.clear();
        }
        int spyStockBefore = game.getPlayerState(4).getWeaponStockByName("spy2");

        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(9, game.getTurn());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.MASS_MEDIA),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PRODUCTION, GameState.Action.PROPAGANDA),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.ATTACK, GameState.Action.DIPLOMACY),
                game.getPlayerState(4).getLastTurnActions());
        assertEquals("turn-nine AI and settlement draws match source; actual=" + random.draws,
                EXPECTED_NINTH_TURN_RANDOM_DRAWS, random.draws);
        assertSeededState(game, EXPECTED_NINTH_TURN_PLAYER_STATE,
                EXPECTED_NINTH_TURN_CITY_POPULATIONS);

        assertEquals("source resolves P4's spy against the human", 1,
                game.getLastTurnSpyMissions().size());
        GameState.SpyMissionEvent spy = game.getLastTurnSpyMissions().get(0);
        assertEquals(4, spy.attackerPlayerId);
        assertEquals(0, spy.defenderPlayerId);
        assertEquals("religion", spy.actionBuildingFrame);
        assertEquals("source consumes one ready spy", spyStockBefore - 1,
                game.getPlayerState(4).getWeaponStockByName("spy2"));
        assertEquals("a resolved spy becomes unready", 0,
                game.getPlayerState(4).spyReadiness);

        GameState.DiplomacyProposal proposal = game.getPendingDiplomacyProposal(0, 4);
        assertTrue("source leaves P4's sixth diplomacy option pending", proposal != null);
        assertEquals(5, proposal.optionIndex);
        assertEquals("turn-nine empty filtered list consumes a raw random draw",
                1, random.rawDraws);
    }

    @Test
    public void hardSeededTenthTurnMatchesPairedSpyMissionsAndProduction() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 9; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            random.clear();
        }
        int firstSpyStock = game.getPlayerState(1).getWeaponStockByName("spy2");
        int fourthSpyStock = game.getPlayerState(4).getWeaponStockByName("spy1");
        assertTrue("source readies player one's second-tier spy", firstSpyStock > 0);
        assertTrue("source readies player four's first-tier spy", fourthSpyStock > 0);

        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(10, game.getTurn());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.ATTACK),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.MASS_MEDIA),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PRODUCTION, GameState.Action.MASS_MEDIA),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PRODUCTION, GameState.Action.ATTACK),
                game.getPlayerState(4).getLastTurnActions());
        assertEquals("turn-ten AI and settlement draws match source; actual=" + random.draws,
                EXPECTED_TENTH_TURN_RANDOM_DRAWS, random.draws);
        assertSeededState(game, EXPECTED_TENTH_TURN_PLAYER_STATE,
                EXPECTED_TENTH_TURN_CITY_POPULATIONS);

        assertEquals("source resolves two spy missions", 2,
                game.getLastTurnSpyMissions().size());
        GameState.SpyMissionEvent firstSpy = game.getLastTurnSpyMissions().get(0);
        assertEquals(1, firstSpy.attackerPlayerId);
        assertEquals(0, firstSpy.defenderPlayerId);
        assertEquals("spyCenter", firstSpy.actionBuildingFrame);
        GameState.SpyMissionEvent fourthSpy = game.getLastTurnSpyMissions().get(1);
        assertEquals(4, fourthSpy.attackerPlayerId);
        assertEquals(0, fourthSpy.defenderPlayerId);
        assertEquals("religion", fourthSpy.actionBuildingFrame);
        assertEquals("player one consumes its second-tier spy", firstSpyStock - 1,
                game.getPlayerState(1).getWeaponStockByName("spy2"));
        assertEquals("player four produces a replacement first-tier spy after its mission",
                fourthSpyStock,
                game.getPlayerState(4).getWeaponStockByName("spy1"));
        assertEquals("source production adds the unlocked third-tier spy", 1,
                game.getPlayerState(4).getWeaponStockByName("spy3"));
        assertEquals(0, game.getPlayerState(1).spyReadiness);
        assertEquals(0, game.getPlayerState(4).spyReadiness);

        GameState.DiplomacyProposal proposal = game.getPendingDiplomacyProposal(0, 4);
        assertTrue("turn-nine diplomacy demand remains pending", proposal != null);
        assertEquals(5, proposal.optionIndex);
        assertEquals("turn-ten empty filtered list consumes a raw random draw",
                1, random.rawDraws);
    }

    @Test
    public void hardSeededEleventhTurnMatchesResearchProductionAndPreparedLaunch() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 10; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            random.clear();
        }

        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(11, game.getTurn());
        assertEquals(Arrays.asList(GameState.Action.RESEARCH, GameState.Action.PRODUCTION),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PRODUCTION, GameState.Action.PREPARED_ATTACK),
                game.getPlayerState(4).getLastTurnActions());
        assertTrue("source turn eleven researches submarine technology",
                game.getPlayerState(1).hasTechnology("sub"));
        assertEquals("source prepares a rocket for the next turn", "rocket1",
                game.getPreparedCarrierName(4));
        assertEquals("turn-eleven AI and settlement draws match source; actual=" + random.draws,
                EXPECTED_ELEVENTH_TURN_RANDOM_DRAWS, random.draws);
        assertEquals("source turn eleven has no raw random draw", 0, random.rawDraws);
        assertSeededState(game, EXPECTED_ELEVENTH_TURN_PLAYER_STATE,
                EXPECTED_ELEVENTH_TURN_CITY_POPULATIONS);
    }

    private static void assertSeededState(GameState game, int[][] players,
                                         int[][] cities) {
        for (int playerId = 0; playerId < players.length; playerId++) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            assertEquals("money for player " + playerId,
                    players[playerId][0], player.moneyMillions);
            assertEquals("population for player " + playerId,
                    players[playerId][1], player.populationMillions);
            assertEquals("religion for player " + playerId,
                    players[playerId][2], player.religion);
            assertEquals("score for player " + playerId,
                    players[playerId][3], player.score);
            for (int cityIndex = 0; cityIndex < cities[playerId].length; cityIndex++) {
                assertEquals("city " + cityIndex + " for player " + playerId,
                        cities[playerId][cityIndex],
                        player.getCity(cityIndex).populationMillions);
            }
        }
    }

    private static void queueHumanTurn(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(game.acceptSelectedAction());
    }

    private static final class RecordingRandom extends Random {
        private final Random delegate;
        private final List<String> draws = new ArrayList<>();
        private int rawDraws;

        RecordingRandom(Random delegate) {
            this.delegate = delegate;
        }

        void clear() {
            draws.clear();
            rawDraws = 0;
        }

        @Override
        public int nextInt(int bound) {
            int value = delegate.nextInt(bound);
            draws.add(bound + ":" + value);
            return value;
        }

        @Override
        public double nextDouble() {
            rawDraws++;
            return delegate.nextDouble();
        }
    }
}
