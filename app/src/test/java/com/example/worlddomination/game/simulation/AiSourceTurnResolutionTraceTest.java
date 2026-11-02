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

    // The source trace's single 10..12 casualty roll is a raw random draw and
    // is asserted separately below. This list contains every bounded draw.
    private static final List<String> EXPECTED_EIGHTEENTH_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:79", "3:2", "91:75", "3:1", "91:14", "3:0", "91:46", "3:0",
            "4:0", "21:4", "61:1", "4:3", "21:4", "61:48", "61:0", "3:2",
            "141:98", "4:2", "21:9", "61:40", "2:0", "3:0", "2:0", "141:96",
            "5:2", "5:0", "5:4", "5:2", "6:5", "6:2", "6:4", "6:2",
            "6:3", "6:4", "26:5", "26:16", "26:4", "26:6", "26:20", "26:8",
            "51:9", "11:2", "11:2", "11:8", "11:1", "11:10", "11:3", "11:6",
            "11:9", "11:6", "11:7", "11:6", "11:4", "11:10", "11:1", "11:6",
            "51:16", "51:49", "51:38", "51:34", "2:0", "24:0", "2:1", "24:15",
            "2:1", "24:19", "2:0", "24:16", "2:0", "24:16", "2:1", "24:4",
            "2:1", "24:2", "2:1", "24:23", "2:0", "24:12", "2:0", "24:18",
            "2:0", "24:19", "2:1", "24:8", "2:0", "24:14", "2:0", "24:10",
            "2:0", "24:3", "2:0", "24:3", "2:0", "24:17", "2:1", "24:23",
            "2:0", "24:18", "2:0", "24:4", "2:0", "24:18", "2:1", "24:17",
            "2:0", "24:15", "2:1", "24:13", "2:0", "24:17", "2:0", "24:8",
            "2:1", "24:10", "2:0", "24:10", "2:1", "24:19", "2:0", "24:11",
            "2:1", "24:2", "2:1", "24:14", "3:1", "51:34", "3:2", "3:1",
            "3:2", "3:0", "3:2", "3:0", "3:1", "3:1", "3:2", "3:0",
            "3:1", "3:1", "3:1", "3:0", "3:0", "3:0", "3:0", "3:2",
            "3:0", "3:0", "3:1", "3:0", "3:2", "3:0", "3:2", "3:0",
            "3:0", "3:0", "3:1", "3:1", "3:1", "2:0", "2:1", "51:30",
            "2:0", "2:1", "2:0", "2:1", "2:0", "2:0", "2:1", "2:1",
            "2:0", "2:0", "2:0", "2:1", "2:0", "2:1", "2:1", "2:1",
            "2:1", "2:1", "2:0", "2:1", "2:0", "2:0", "2:1", "2:1",
            "2:0", "2:0", "2:1", "2:1", "2:0", "2:0");

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

    private static final int[][] EXPECTED_TWELFTH_TURN_PLAYER_STATE = {
            {129, 317, 100, 220360},
            {123, 432, 20, 544130},
            {116, 475, 47, 507028},
            {56, 470, 40, 277198},
            {21, 425, 49, 323854}
    };

    private static final int[][] EXPECTED_TWELFTH_TURN_CITY_POPULATIONS = {
            {73, 12, 80, 47, 105},
            {59, 97, 54, 84, 138},
            {62, 85, 115, 83, 130},
            {70, 114, 71, 100, 115},
            {107, 41, 67, 43, 167}
    };

    private static final int[][] EXPECTED_THIRTEENTH_TURN_PLAYER_STATE = {
            {153, 305, 100, 238378},
            {45, 432, 13, 562274},
            {58, 475, 42, 560512},
            {86, 470, 31, 326230},
            {30, 425, 39, 327956}
    };

    private static final int[][] EXPECTED_THIRTEENTH_TURN_CITY_POPULATIONS = {
            {73, 0, 80, 47, 105},
            {59, 97, 54, 84, 138},
            {62, 85, 115, 83, 130},
            {70, 114, 71, 100, 115},
            {107, 41, 67, 43, 167}
    };

    private static final List<String> EXPECTED_THIRTEENTH_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:44", "3:2", "91:55", "3:1", "91:32", "3:0", "91:49", "3:0",
            "61:31", "4:1", "21:11", "61:42", "61:11", "4:0", "21:7", "61:51",
            "61:26", "3:2", "141:34", "2:1", "5:2", "4:2", "141:81", "5:0",
            "5:1", "5:0", "5:3", "5:0", "5:2", "6:5", "6:4", "6:0",
            "6:2", "6:5", "51:50", "5:1", "11:6", "4:3", "4:0", "51:17",
            "3:1", "51:32", "11:5", "11:5", "11:0", "11:0", "11:2", "11:0",
            "11:4", "11:10", "11:10", "11:2", "11:3", "11:6", "11:8", "11:4",
            "11:1", "51:28", "51:40", "51:12", "51:41", "51:22", "3:2", "24:3",
            "3:1", "24:5", "3:0", "24:15", "3:1", "24:22", "3:2", "24:0",
            "3:1", "24:17", "3:0", "24:16", "3:0", "24:16", "3:1", "24:0",
            "3:2", "24:3", "3:2", "24:16", "3:0", "24:0", "3:0", "24:14",
            "3:0", "24:17", "3:0", "24:7", "3:0", "24:16", "3:2", "24:5",
            "3:1", "24:17", "3:2", "24:4", "3:0", "24:11", "3:0", "24:19",
            "3:0", "24:16", "3:1", "24:17", "3:0", "24:11", "3:0", "24:21",
            "3:2", "24:1", "3:1", "24:10", "3:1", "24:9", "3:0", "24:6",
            "3:2", "24:16", "3:2", "24:11", "3:2", "24:12", "2:1", "24:13",
            "2:1", "24:20", "2:0", "24:0", "2:1", "24:1", "2:0", "24:9",
            "2:1", "24:3", "2:1", "24:17", "2:1", "24:8", "2:0", "24:19",
            "2:0", "24:9", "2:0", "24:18", "2:0", "24:11", "2:0", "24:15",
            "2:0", "24:16", "2:0", "24:22", "2:0", "24:16", "2:1", "24:9",
            "2:1", "24:21", "2:1", "24:15", "2:1", "24:22", "2:0", "24:4",
            "2:0", "24:13", "2:1", "24:0", "2:0", "24:23", "2:1", "24:7",
            "2:0", "24:3", "2:1", "24:22", "2:1", "24:6", "2:0", "24:5",
            "2:1", "24:6", "2:0", "24:2", "2:1", "24:20", "3:1", "51:15",
            "3:2", "51:36", "3:2", "3:0", "51:32");

    private static final int[][] EXPECTED_FOURTEENTH_TURN_PLAYER_STATE = {
            {144, 305, 100, 253856},
            {81, 432, 33, 585334},
            {49, 475, 35, 598978},
            {118, 470, 50, 333030},
            {15, 425, 32, 344050}
    };

    private static final int[][] EXPECTED_FOURTEENTH_TURN_CITY_POPULATIONS = {
            {73, 0, 80, 47, 105},
            {59, 97, 54, 84, 138},
            {62, 85, 115, 83, 130},
            {70, 114, 71, 100, 115},
            {107, 41, 67, 43, 167}
    };

    private static final List<String> EXPECTED_FOURTEENTH_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:56", "3:1", "91:38", "3:0", "91:24", "3:2", "91:4", "3:2",
            "4:0", "21:16", "61:8", "61:20", "61:3", "61:54", "2:0", "3:2",
            "1:0", "141:13", "4:0", "5:3", "2:0", "3:1", "2:1", "141:136",
            "4:2", "21:14", "61:10", "61:38", "61:34", "6:2", "6:1", "6:2",
            "6:0", "6:5", "7:5", "21:1", "4:3", "11:5", "11:8", "11:2",
            "11:2", "11:0", "11:7", "11:7", "11:2", "11:2", "11:5", "11:4",
            "11:5", "11:10", "11:0", "11:0", "51:35", "51:29", "51:8", "51:25",
            "51:30", "51:18", "2:0", "24:22", "2:0", "24:7", "2:1", "24:2",
            "2:1", "24:18", "2:0", "24:3", "2:1", "24:0", "2:1", "24:19",
            "2:1", "24:1", "2:1", "24:5", "2:1", "24:18", "2:0", "24:11",
            "2:1", "24:3", "2:0", "24:15", "2:1", "24:20", "2:0", "24:7",
            "2:0", "24:5", "2:1", "24:18", "2:1", "24:6", "2:0", "24:12",
            "2:1", "24:7", "2:0", "24:14", "2:1", "24:2", "2:0", "24:9",
            "2:1", "24:15", "2:1", "24:20", "2:0", "24:23", "2:1", "24:12",
            "2:1", "24:14", "2:1", "24:0", "2:0", "24:10", "2:1", "24:15",
            "2:1", "24:7", "3:0", "51:48", "3:0", "3:1", "51:44", "1:0",
            "51:0", "2:0", "51:16", "2:0", "51:27");

    private static final int[][] EXPECTED_FIFTEENTH_TURN_PLAYER_STATE = {
            {115, 262, 100, 270872},
            {89, 432, 23, 585334},
            {52, 475, 50, 605478},
            {83, 470, 44, 361340},
            {50, 425, 25, 465772}
    };

    private static final int[][] EXPECTED_FIFTEENTH_TURN_CITY_POPULATIONS = {
            {73, 0, 37, 47, 105},
            {59, 97, 54, 84, 138},
            {62, 85, 115, 83, 130},
            {70, 114, 71, 100, 115},
            {107, 41, 67, 43, 167}
    };

    private static final List<String> EXPECTED_FIFTEENTH_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:14", "3:0", "91:10", "3:1", "91:74", "3:2", "91:2", "3:0",
            "61:11", "61:24", "4:0", "21:1", "61:57", "2:1", "4:2", "4:1",
            "141:8", "7:5", "4:2", "5:3", "5:3", "5:1", "5:4", "5:4",
            "61:30", "61:26", "4:2", "21:12", "61:39", "2:0", "3:0", "1:0",
            "141:74", "3:2", "4:2", "141:104", "6:2", "6:1", "6:5", "6:5",
            "6:4", "7:5", "21:16", "5:1", "51:29", "11:3", "11:5",
            "11:10", "11:7", "11:9", "11:9", "11:5", "11:4", "11:9", "11:9",
            "11:10", "11:7", "11:10", "11:4", "11:9", "51:29", "51:36", "51:13",
            "51:21", "3:1", "24:12", "3:1", "24:0", "3:0", "24:19", "3:0",
            "24:12", "3:0", "24:18", "3:0", "24:18", "3:0", "24:14", "3:1",
            "24:15", "3:2", "24:14", "3:2", "24:20", "3:0", "24:10", "3:2",
            "24:17", "3:1", "24:3", "3:2", "24:22", "3:2", "24:18", "3:0",
            "24:23", "3:2", "24:4", "3:2", "24:5", "3:1", "24:11", "3:0",
            "24:14", "3:0", "24:22", "3:0", "24:23", "3:1", "24:11", "3:0",
            "24:18", "3:2", "24:14", "3:1", "24:12", "3:0", "24:23", "3:0",
            "24:7", "3:0", "24:22", "3:2", "24:18", "3:0", "24:12", "3:0",
            "24:2", "3:0", "3:0", "3:2", "51:13", "3:1", "51:32", "3:1",
            "3:1", "3:1", "3:0", "3:1", "3:0", "3:0", "3:2", "3:0",
            "3:1", "3:1", "3:0", "3:1", "3:1", "3:2", "3:1", "3:0",
            "3:0", "3:0", "3:0", "3:1", "3:2", "3:0", "3:1", "3:0",
            "3:0", "3:2", "3:2");

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

    // The strike's 15..18 casualty roll is recorded as a separate raw draw.
    private static final List<String> EXPECTED_TWELFTH_TURN_RANDOM_DRAWS = Arrays.asList(
            "91:24", "3:1", "91:16", "3:1", "91:45", "3:2", "91:35", "3:1",
            "2:0", "3:0", "1:0", "141:50", "5:3", "5:4", "61:22", "61:50",
            "2:0", "3:1", "2:1", "141:115", "4:3", "21:18", "61:51", "3:0",
            "5:3", "5:3", "141:104", "6:4", "6:5", "6:1", "6:5", "6:5",
            "9:3", "31:10", "4:2", "51:1", "11:9", "11:4", "11:6", "11:8",
            "11:10", "11:4", "11:6", "11:10", "11:7", "11:3", "11:2", "11:6",
            "11:7", "11:4", "11:8", "51:3", "51:29", "51:46", "51:22", "51:14",
            "51:37", "3:1", "51:10", "3:0", "3:0", "3:0", "3:0", "3:0",
            "3:2", "51:24", "3:0", "3:1", "3:2", "3:1", "3:1", "3:0",
            "3:2", "3:0", "3:2", "3:2", "3:1", "3:1", "3:2", "3:0",
            "3:1", "3:2", "3:0", "3:2", "3:0", "3:0", "3:0", "3:2",
            "3:1", "3:2", "3:1");

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

    @Test
    public void hardSeededTwelfthTurnMatchesSpyStrikeAndCarrierPreparation() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 11; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            random.clear();
        }
        int spyStockBefore = game.getPlayerState(1).getWeaponStockByName("spy3");
        assertTrue("source readies a third-tier spy before turn twelve", spyStockBefore > 0);
        assertEquals("turn eleven prepares the source rocket", "rocket1",
                game.getPreparedCarrierName(4));
        assertEquals("source turn eleven has one plane1", 1,
                game.getPlayerState(3).getWeaponStockByName("plane1"));
        assertTrue("plane1 carrier should be available: "
                        + game.getAvailablePreparedCarriers(3),
                game.getAvailablePreparedCarriers(3).contains("plane1"));
        assertFalse("source still ranks the research action for player three",
                game.getPlayerState(3).isActionBioloaded(GameState.Action.RESEARCH));

        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(12, game.getTurn());
        assertEquals("turn-twelve AI and settlement draws match source; actual=" + random.draws,
                EXPECTED_TWELFTH_TURN_RANDOM_DRAWS, random.draws);
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.ATTACK),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.MASS_MEDIA),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PREPARED_ATTACK,
                        GameState.Action.PRODUCTION),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PREPARED_ATTACK,
                        GameState.Action.MASS_MEDIA),
                game.getPlayerState(4).getLastTurnActions());
        assertEquals("source mission consumes one spy3", spyStockBefore - 1,
                game.getPlayerState(1).getWeaponStockByName("spy3"));
        assertEquals("source mission becomes unready", 0,
                game.getPlayerState(1).spyReadiness);
        assertEquals("spy mission targets the source factory", 1,
                game.getLastTurnSpyMissions().size());
        GameState.SpyMissionEvent spy = game.getLastTurnSpyMissions().get(0);
        assertEquals(1, spy.attackerPlayerId);
        assertEquals(0, spy.defenderPlayerId);
        assertEquals("factory", spy.actionBuildingFrame);
        assertEquals("source prepares its plane after rejecting research twice",
                "plane1", game.getPreparedCarrierName(3));
        assertEquals("turn twelve strike consumes the prepared rocket", null,
                game.getPreparedCarrierName(4));
        assertEquals("prepared strike casualty draw", 1, random.rawDraws);
        assertSeededState(game, EXPECTED_TWELFTH_TURN_PLAYER_STATE,
                EXPECTED_TWELFTH_TURN_CITY_POPULATIONS);
    }

    @Test
    public void hardSeededThirteenthTurnMatchesSpyAttackAndAirstrike() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 12; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            random.clear();
        }
        assertEquals("source turn-twelve P2 sub investment",
                10, game.getPlayerState(2).getWeapons().get("sub").investmentMillions);
        assertEquals("source turn-twelve P2 ship investment",
                38, game.getPlayerState(2).getWeapons().get("ship").investmentMillions);
        assertEquals("turn twelve prepares the source airfield carrier", "plane1",
                game.getPreparedCarrierName(3));

        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(13, game.getTurn());
        assertEquals("source turn-thirteen bounded draw trace",
                EXPECTED_THIRTEENTH_TURN_RANDOM_DRAWS, random.draws);
        assertEquals("source turn-thirteen raw random draws", 2, random.rawDraws);
        assertEquals(Arrays.asList(GameState.Action.RESEARCH, GameState.Action.PRODUCTION),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PREPARED_ATTACK, GameState.Action.RESOURCES),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.ATTACK, GameState.Action.DIPLOMACY),
                game.getPlayerState(4).getLastTurnActions());
        assertTrue("source researches aircraft carrier technology",
                game.getPlayerState(1).hasTechnology("ship"));
        assertEquals("source consumes its prepared airfield carrier", null,
                game.getPreparedCarrierName(3));
        assertEquals("turn thirteen has one source spy attack", 1,
                game.getLastTurnSpyMissions().size());
        GameState.SpyMissionEvent spy = game.getLastTurnSpyMissions().get(0);
        assertEquals(4, spy.attackerPlayerId);
        assertEquals(3, spy.defenderPlayerId);
        assertEquals("tech", spy.actionBuildingFrame);
        GameState.DiplomacyProposal proposal = game.getPendingDiplomacyProposal(0, 4);
        assertTrue("source leaves P4's diplomacy offer to the human pending", proposal != null);
        assertEquals(5, proposal.optionIndex);
        GameState.PlayerState secondOpponent = game.getPlayerState(2);
        assertEquals("source P2 accumulates sub investment",
                43, secondOpponent.getWeapons().get("sub").investmentMillions);
        assertEquals("source P2 accumulates ship investment",
                61, secondOpponent.getWeapons().get("ship").investmentMillions);
        assertSeededState(game, EXPECTED_THIRTEENTH_TURN_PLAYER_STATE,
                EXPECTED_THIRTEENTH_TURN_CITY_POPULATIONS);
    }

    @Test
    public void hardSeededFourteenthTurnMatchesSourceProductionAndSpyAttack() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 13; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            random.clear();
        }
        assertSeededState(game, EXPECTED_THIRTEENTH_TURN_PLAYER_STATE,
                EXPECTED_THIRTEENTH_TURN_CITY_POPULATIONS);
        assertEquals("turn thirteen leaves P2's source ship investment", 61,
                game.getPlayerState(2).getWeapons().get("ship").investmentMillions);

        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(14, game.getTurn());
        assertEquals("source turn-fourteen bounded draw trace",
                EXPECTED_FOURTEENTH_TURN_RANDOM_DRAWS, random.draws);
        assertEquals("source turn fourteen has one empty-range random call",
                1, random.rawDraws);
        assertEquals(Arrays.asList(GameState.Action.MASS_MEDIA, GameState.Action.RESOURCES),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.ATTACK, GameState.Action.MASS_MEDIA),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PREPARED_ATTACK, GameState.Action.PRODUCTION),
                game.getPlayerState(4).getLastTurnActions());

        assertEquals("source P3 spy mission targets the human nation", 1,
                game.getLastTurnSpyMissions().size());
        GameState.SpyMissionEvent spy = game.getLastTurnSpyMissions().get(0);
        assertEquals(3, spy.attackerPlayerId);
        assertEquals(0, spy.defenderPlayerId);
        assertEquals("resources", spy.actionBuildingFrame);
        assertEquals("source prepares P4's airfield carrier", "plane1",
                game.getPreparedCarrierName(4));

        GameState.PlayerState secondOpponent = game.getPlayerState(2);
        assertEquals("source P2 produces one submarine", 2,
                secondOpponent.getWeaponStockByName("sub"));
        assertEquals("source P2 produces one ship", 1,
                secondOpponent.getWeaponStockByName("ship"));
        assertEquals("source P2 retains ship investment", 16,
                secondOpponent.getWeapons().get("ship").investmentMillions);
        assertSeededState(game, EXPECTED_FOURTEENTH_TURN_PLAYER_STATE,
                EXPECTED_FOURTEENTH_TURN_CITY_POPULATIONS);
    }

    @Test
    public void hardSeededFifteenthTurnMatchesSourceAirstrikeAndDiplomacy() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 14; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            random.clear();
        }
        assertSeededState(game, EXPECTED_FOURTEENTH_TURN_PLAYER_STATE,
                EXPECTED_FOURTEENTH_TURN_CITY_POPULATIONS);
        assertEquals("turn fourteen prepares P4's carrier", "plane1",
                game.getPreparedCarrierName(4));

        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(15, game.getTurn());
        assertEquals("source turn-fifteen bounded draw trace; actual=" + random.draws,
                EXPECTED_FIFTEENTH_TURN_RANDOM_DRAWS, random.draws);
        assertEquals("source strike casualty uses one raw random draw", 1, random.rawDraws);
        assertEquals(Arrays.asList(GameState.Action.PRODUCTION, GameState.Action.ATTACK),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.DIPLOMACY, GameState.Action.MASS_MEDIA),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PRODUCTION,
                        GameState.Action.PREPARED_ATTACK),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PREPARED_ATTACK,
                        GameState.Action.RESOURCES),
                game.getPlayerState(4).getLastTurnActions());

        assertEquals("source P1 spy mission targets the human nation", 1,
                game.getLastTurnSpyMissions().size());
        GameState.SpyMissionEvent spy = game.getLastTurnSpyMissions().get(0);
        assertEquals(1, spy.attackerPlayerId);
        assertEquals(0, spy.defenderPlayerId);
        assertEquals("resources", spy.actionBuildingFrame);
        assertEquals(0, game.getPlayerState(1).spyReadiness);
        assertEquals("source P1 consumes its second-tier spy", 0,
                game.getPlayerState(1).getWeaponStockByName("spy2"));

        assertEquals("source P2 leaves its demand to the human pending", 5,
                game.getPendingDiplomacyProposal(0, 2).optionIndex);
        assertEquals("source P3 prepares a silo carrier", "rocket1",
                game.getPreparedCarrierName(3));
        assertEquals("source P4 consumes its prepared airfield carrier", null,
                game.getPreparedCarrierName(4));

        assertEquals("one source prepared strike resolves", 1,
                game.getLastTurnCombatEvents().size());
        GameState.CombatEvent strike = game.getLastTurnCombatEvents().get(0);
        assertEquals(GameState.CombatEventType.STRIKE_IMPACT, strike.type);
        assertEquals(4, strike.attackerPlayerId);
        assertEquals(0, strike.defenderPlayerId);
        assertEquals(2, strike.cityIndex);
        assertEquals("plane1", strike.weaponName);
        assertEquals("warhead2", strike.payloadName);
        assertEquals(43, strike.casualtiesMillions);

        GameState.PlayerState firstOpponent = game.getPlayerState(1);
        assertEquals(17, firstOpponent.getWeapons().get("sub").investmentMillions);
        assertEquals(40, firstOpponent.getWeapons().get("ship").investmentMillions);
        assertEquals(22, firstOpponent.getWeapons().get("space").investmentMillions);
        assertSeededState(game, EXPECTED_FIFTEENTH_TURN_PLAYER_STATE,
                EXPECTED_FIFTEENTH_TURN_CITY_POPULATIONS);
    }

    @Test
    public void hardSeededSixteenthTurnMatchesSourceAttacksAndCarrierPreparation() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 15; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            random.clear();
        }
        assertSeededState(game, EXPECTED_FIFTEENTH_TURN_PLAYER_STATE,
                EXPECTED_FIFTEENTH_TURN_CITY_POPULATIONS);

        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(16, game.getTurn());
        assertEquals("source turn-sixteen bounded draw trace; actual=" + random.draws,
                Arrays.asList(
                        "91:83", "3:0", "91:81", "3:2", "91:73", "3:0", "91:41", "3:2",
                        "61:44", "4:1", "21:4", "61:19", "61:3", "5:3", "5:0", "5:0",
                        "2:0", "3:2", "4:3", "141:92", "3:0", "141:27", "2:1", "4:3",
                        "4:1", "141:16", "7:5", "6:5", "6:3", "6:4", "6:1", "6:4",
                        "51:5", "51:25", "51:34", "11:7", "11:5",
                        "11:10", "11:7", "11:9", "11:0", "11:5", "11:10", "11:10", "11:2",
                        "11:0", "11:10", "11:4", "11:2", "11:8", "51:28", "51:23", "51:46",
                        "51:30", "4:2", "24:23", "4:0", "24:20", "4:2", "24:14", "4:3",
                        "24:7", "4:3", "24:11", "4:3", "24:3", "4:2", "24:10", "4:3",
                        "24:5", "4:1", "24:23", "4:1", "24:2", "4:3", "24:19", "4:0",
                        "24:10", "4:1", "24:17", "4:3", "24:22", "4:0", "24:2", "4:0",
                        "24:2", "4:1", "24:18", "4:2", "24:17", "4:1", "24:11", "4:1",
                        "24:17", "4:2", "24:8", "4:0", "24:19", "4:0", "24:4", "4:2",
                        "24:15", "4:2", "24:8", "4:1", "24:19", "4:1", "24:22", "4:3",
                        "24:23", "4:1", "24:11", "4:2", "24:9", "4:1", "24:5", "4:3",
                        "24:3"), random.draws);
        assertEquals("source direct and prepared attacks use three casualty draws",
                3, random.rawDraws);
        assertEquals(Arrays.asList(GameState.Action.RESEARCH, GameState.Action.PRODUCTION),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.DIPLOMACY,
                        GameState.Action.PREPARED_ATTACK),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PREPARED_ATTACK, GameState.Action.ATTACK),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.MASS_MEDIA, GameState.Action.ATTACK),
                game.getPlayerState(4).getLastTurnActions());

        assertEquals("source P2 prepares its ship carrier", "ship",
                game.getPreparedCarrierName(2));
        assertEquals("source P3 consumes its prepared rocket", null,
                game.getPreparedCarrierName(3));
        assertEquals("source P2's demand remains pending for the human", 5,
                game.getPendingDiplomacyProposal(0, 2).optionIndex);

        List<GameState.CombatEvent> presentationEvents =
                game.getLastTurnCombatEventsInSourcePresentationOrder();
        assertEquals("source turn sixteen resolves two soldier attacks and one rocket",
                3, presentationEvents.size());
        GameState.CombatEvent firstAttack = presentationEvents.get(0);
        assertEquals(GameState.CombatEventType.DIRECT_ATTACK, firstAttack.type);
        assertEquals(4, firstAttack.attackerPlayerId);
        assertEquals(0, firstAttack.defenderPlayerId);
        assertEquals(0, firstAttack.cityIndex);
        assertEquals("soldier2", firstAttack.weaponName);
        assertEquals("source P4 casualty event weapon=" + firstAttack.weaponName,
                8, firstAttack.casualtiesMillions);

        GameState.CombatEvent secondAttack = presentationEvents.get(1);
        assertEquals(GameState.CombatEventType.DIRECT_ATTACK, secondAttack.type);
        assertEquals(3, secondAttack.attackerPlayerId);
        assertEquals(0, secondAttack.defenderPlayerId);
        assertEquals(3, secondAttack.cityIndex);
        assertEquals("soldier4", secondAttack.weaponName);
        assertEquals(18, secondAttack.casualtiesMillions);

        GameState.CombatEvent rocketStrike = presentationEvents.get(2);
        assertEquals(GameState.CombatEventType.STRIKE_IMPACT, rocketStrike.type);
        assertEquals(3, rocketStrike.attackerPlayerId);
        assertEquals(0, rocketStrike.defenderPlayerId);
        assertEquals(3, rocketStrike.cityIndex);
        assertEquals("rocket1", rocketStrike.weaponName);
        assertEquals("warhead1", rocketStrike.payloadName);
        assertEquals(16, rocketStrike.casualtiesMillions);

        int[][] expectedPlayers = {
                {135, 220, 100, 286912},
                {33, 432, 17, 609590},
                {55, 475, 41, 605478},
                {83, 470, 36, 444884},
                {50, 425, 40, 492174}
        };
        int[][] expectedCities = {
                {65, 0, 37, 13, 105},
                {59, 97, 54, 84, 138},
                {62, 85, 115, 83, 130},
                {70, 114, 71, 100, 115},
                {107, 41, 67, 43, 167}
        };
        assertSeededState(game, expectedPlayers, expectedCities);
    }

    @Test
    public void hardSeededSeventeenthTurnMatchesSourceShipPayloadsAndAiActions() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 16; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            random.clear();
        }
        assertSeededState(game,
                new int[][]{
                        {135, 220, 100, 286912},
                        {33, 432, 17, 609590},
                        {55, 475, 41, 605478},
                        {83, 470, 36, 444884},
                        {50, 425, 40, 492174}
                },
                new int[][]{
                        {65, 0, 37, 13, 105},
                        {59, 97, 54, 84, 138},
                        {62, 85, 115, 83, 130},
                        {70, 114, 71, 100, 115},
                        {107, 41, 67, 43, 167}
                });

        queueHumanTurn(game);
        game.confirmTurn();
        assertEquals(17, game.getTurn());
        assertEquals("source turn-seventeen bounded draw trace; actual=" + random.draws,
                Arrays.asList(
                        "91:71", "3:1", "91:27", "3:1", "91:38", "3:1", "91:13", "3:2",
                        "4:1", "4:3", "4:1", "141:132", "61:22", "61:41", "2:0", "3:2",
                        "1:0", "141:134", "2:1", "4:1", "4:1", "141:107", "7:6", "4:0",
                        "21:0", "61:41", "6:1", "6:4", "6:2", "6:2", "6:2", "3:0",
                        "3:2", "3:2", "51:30", "51:19", "51:19", "51:35", "11:4", "11:1",
                        "11:9", "11:9", "11:3", "11:9",
                        "11:8", "11:9", "11:4", "11:8", "11:8", "11:3", "11:2", "11:6",
                        "11:4", "51:22", "51:34", "51:45", "51:11", "51:19", "51:17", "1:0",
                        "51:44", "1:0", "51:10"), random.draws);
        assertEquals("source ship and soldier casualty rolls use raw draws", 4,
                random.rawDraws);
        assertEquals(Arrays.asList(GameState.Action.MASS_MEDIA, GameState.Action.RESOURCES),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PREPARED_ATTACK, GameState.Action.RESOURCES),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PREPARED_ATTACK, GameState.Action.MASS_MEDIA),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.ATTACK, GameState.Action.PRODUCTION),
                game.getPlayerState(4).getLastTurnActions());

        assertEquals("P3 prepares the next silo rocket", "rocket1",
                game.getPreparedCarrierName(3));
        assertEquals("P2 consumes its three-payload ship", null,
                game.getPreparedCarrierName(2));
        List<GameState.CombatEvent> resolvedEvents = game.getLastTurnCombatEvents();
        assertEquals("source ship impacts and final soldier attack", 4,
                resolvedEvents.size());
        int[] expectedShipCities = {3, 0, 3};
        int[] expectedShipCasualties = {13, 57, 0};
        for (int strikeIndex = 0; strikeIndex < 3; strikeIndex++) {
            GameState.CombatEvent strike = resolvedEvents.get(strikeIndex);
            assertEquals(GameState.CombatEventType.STRIKE_IMPACT, strike.type);
            assertEquals(2, strike.attackerPlayerId);
            assertEquals(0, strike.defenderPlayerId);
            assertEquals(expectedShipCities[strikeIndex], strike.cityIndex);
            assertEquals("warhead3", strike.payloadName);
            assertEquals(expectedShipCasualties[strikeIndex], strike.casualtiesMillions);
        }
        GameState.CombatEvent directAttack = resolvedEvents.get(3);
        assertEquals(GameState.CombatEventType.DIRECT_ATTACK, directAttack.type);
        assertEquals(4, directAttack.attackerPlayerId);
        assertEquals(0, directAttack.defenderPlayerId);
        assertEquals(3, directAttack.cityIndex);
        assertEquals("soldier2", directAttack.weaponName);
        assertEquals(0, directAttack.casualtiesMillions);
        assertSeededState(game,
                new int[][]{
                        {152, 150, 100, 301590},
                        {69, 432, 35, 632108},
                        {98, 475, 34, 797588},
                        {83, 470, 52, 451034},
                        {30, 425, 34, 510894}
                },
                new int[][]{
                        {8, 0, 37, 0, 105},
                        {59, 97, 54, 84, 138},
                        {62, 85, 115, 83, 130},
                        {70, 114, 71, 100, 115},
                        {107, 41, 67, 43, 167}
                });
    }

    @Test
    public void hardSeededEighteenthTurnMatchesSourceStateActionsAndRandomTrace() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            setup.toggleOpponent(leader);
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        for (int priorTurn = 0; priorTurn < 17; priorTurn++) {
            queueHumanTurn(game);
            game.confirmTurn();
            random.clear();
        }
        queueHumanTurn(game);
        game.confirmTurn();

        assertEquals(18, game.getTurn());
        assertEquals("source turn-eighteen bounded random trace; actual=" + random.draws,
                EXPECTED_EIGHTEENTH_TURN_RANDOM_DRAWS, random.draws);
        assertEquals("the source biological strike has one raw casualty draw", 1,
                random.rawDraws);
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.MASS_MEDIA),
                game.getPlayerState(0).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(1).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.RESOURCES, GameState.Action.PRODUCTION),
                game.getPlayerState(2).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PREPARED_ATTACK, GameState.Action.PRODUCTION),
                game.getPlayerState(3).getLastTurnActions());
        assertEquals(Arrays.asList(GameState.Action.PREPARED_ATTACK, GameState.Action.DIPLOMACY),
                game.getPlayerState(4).getLastTurnActions());
        assertEquals("the fourth opponent stages its level-two rocket", "rocket2",
                game.getPreparedCarrierName(4));
        assertTrue(game.isActionBioloaded(0, GameState.Action.PROPAGANDA));
        assertFalse(game.isActionBioloaded(0, GameState.Action.DIPLOMACY));

        List<GameState.CombatEvent> events = game.getLastTurnCombatEvents();
        assertEquals("one biological strike resolves on source turn eighteen", 1,
                events.size());
        GameState.CombatEvent strike = events.get(0);
        assertEquals(GameState.CombatEventType.STRIKE_IMPACT, strike.type);
        assertEquals(3, strike.attackerPlayerId);
        assertEquals(0, strike.defenderPlayerId);
        assertEquals(0, strike.cityIndex);
        assertEquals("rocket1", strike.weaponName);
        assertEquals("bioload1", strike.payloadName);
        assertEquals(8, strike.casualtiesMillions);

        assertSeededState(game,
                new int[][]{
                        {164, 142, 100, 313056},
                        {105, 432, 28, 649244},
                        {80, 475, 25, 829718},
                        {63, 470, 45, 486834},
                        {30, 425, 24, 510894}
                },
                new int[][]{
                        {0, 0, 37, 0, 105},
                        {59, 97, 54, 84, 138},
                        {62, 85, 115, 83, 130},
                        {70, 114, 71, 100, 115},
                        {107, 41, 67, 43, 167}
                });
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
