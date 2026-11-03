package com.example.worlddomination.game.simulation;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Compares resolved multi-turn Android state to recovered Python game sessions.
 * Source rows cover forty turns (or the terminal defeat at turn 38) and come
 * from SetupMenu.start_game and Game.advance with the human
 * queuing Resources and Mass Media on each turn. Rows include each active
 * player's funds, population, religion, score, city populations, regular weapon
 * stock, and invented technologies; defense counters remain in their own source
 * array and are intentionally excluded from the weapon-stock row.
 */
@RunWith(Parameterized.class)
public final class AiSourceResolvedTurnParityTest {
    private static final String[][] SOURCE_TURN_HASHES = {
            {
                    "5c7a601c20844305cca8336165249aa0ff25342b5ba551862298f90245ddc4c2",
                    "f78b151e00f97939d3a73e9272185a2869ca97d9d99c4e42f81ab39c0cdde91c",
                    "e871b101da07e4c0bf024f9a7179dc32b91216fc67e96c8740c880362d8240a8",
                    "e90c0cda94eef37e2427ea7cca8bdda08379f7c1543679355a44dd84a6f609b3",
                    "4f36ee2d232a4cb3b0fb787c5346504347ea37bba054db40b75c78625348271a",
                    "e368dd0737832b6a6c9b8e9c993eb0bd78901d996ff16cedc90c743a1193b2fd",
                    "f0d0a31f2ae9eaf7cbd11d630d9de55550f56b71a0ab3c40f427c8da7c9defbc",
                    "ad221e151d1a5f4920f310290d08229c4625744266008589e08a28b882520a10",
                    "0067efc606dbb4b3372ee2422920982c4076a756bf1fbdc988bc43dd0b1584e9",
                    "204044106f1bc12078de39be64b2117b5835d328615f3efc79acf8d6afcace2a",
                    "0b4d6516c93e43b0a92f7735bc7852cf5140f28580d36f11e065b47351c6622c",
                    "842b5987675a3bc4b1884a35ec6d3d9828288f85c78165854af16547f33d2f9d",
                    "dbfe6cc09bd941e18206d10b4f5db9c29a7f5adc10e6660bfe790c9f7080f03d",
                    "c66112be1264360c7b921f3e6fa69793119f74e8078a7abe8472f2d23e1cee8e",
                    "50c93c3f44887db0ea28e01a11deeb7c51b775657eef04d13acd3cebdfa914e9",
                    "cb1f7bd8664d9f35621c10a57ec9e60ffae37a6cf662664f40f34fce04e38bae",
                    "978809f2ca7004e5188ec21db771926868c0435c65dd66133c2d4836be97e240",
                    "4e40a1acaa99568db489b6fb74874e29e7275f840d737e12d3e229e93a6bef8f",
                    "423488f1eb237b439f2fe57b505ce78212191ae356f485d79ff5379f8f2c6633",
                    "27defcf9d1c13cb568de5a2284296639d186e837486c424afc64baa6f8bc6899",
                    "6b3222ee1e90f28d7c68024d1fc6a5c0fcaaba3dcbb5a07a834c40f4bc01112e",
                    "7e0523a1ada0ff691e4b17868b7192484712e6f7d746ad32dd7f4fe8f16ecceb",
                    "b0ee76f74989533f7bcb1447b9c3c5836751327e0c5b5d526199075b9612b8a4",
                    "4bb5ce937d845f38d2a9ef57895ea7c74e221cc7e0f1ae2c45560a16221b6ea0",
                    "b0bd582dc6bd1cd94ea96e47f7a4ce59ad02433b24f901a5ab76259d97ccf810",
                    "4b7d2dbb3d8f996f529939dc94316f1820d6c612aa99386b3376e7533c3d4bd2",
                    "9be22721168034c0004eb6526f461b336ad43809536c53dccae525c9e2b579e5",
                    "ec6d708a440f17ab275853ad2adcd1d3e974ab7ad48961620c177967cb7dc137",
                    "e14862975ccbe16d8a4c71f589d07869415c7c51e26bebb9ad1ebfa4bd48cda6",
                    "2cc4ea041d8bd112e420e21a74dbf285111ca438487601881256c6db735cbb6a",
                    "94c9baf12d9cc0981776a32d0a832e9b2a0f60d12b374e802759f81426994a87",
                    "3b2befe099540579edfce36c66b5746bbff7f363d30cf4f90486741e5d4f5650",
                    "3154db4590f7c6266326d56c9d344129fc43169ecfad154143680b8106abf806",
                    "f97fe711cf9f452526abd9bf3349c9ef7fdd0e7a5f3dcf23b9b0943ab38d133e",
                    "06d9baadf78c365efa19adb80670836e4c0ac8b65fc2ead5f093a318b8af58d7",
                    "30b06709efe3dcdf37990450ae980ed60437a969d2c351f5167c90dd72fd71e8",
                    "7b1797d60915e1567dddfbae9829ab8b0f546457e6af08569e813352684c7f9f",
                    "8e018ed5a856f7cef5e8c067c1202888e945c828aafede72707a6a9a98f51ff4",
                    "e02501aaff0d970ba92873dfac95972509f28edc091188c1c855cac5532e0928",
                    "7c6303715698a2a32ea059bffe2af1fcc4299880f95200eb3baed3b1f66577d7"
            },
            {
                    "3b14348e00af8c1f0c59ee3438ba743cbd101b4a8718637e032057dc67ad4d9b",
                    "3295361b7d2fb45d29692a926fab66c762a9ce651a34e1cadad248d1d4e2d36f",
                    "3eeda30a78b2be567666911453828930133dc205e8352dc3929da38459de4a6f",
                    "e59bac49dc9efaa586e3026ec49617427ef8de08bf89c8eedb073be2f44d408d",
                    "c82c676b23084533d89f72718a2a4a1a8e38c14272e6dce37f24ca177e104b8f",
                    "983a77d9d0a67942239aa658b418626f3f3b134e77fc8af5bfbc9d1a5fdcc71d",
                    "da5a79b65a980466cbd77db8a75c98fe0e0d6f79c06fb16ceca4a41d04094e46",
                    "0ec9927d06daa38f1fd9e9c4d12bc67b02259060f14aecb0e83156f966f00797",
                    "84a059b51c3613c0bc84703364b6c866ba80d1897e965ac8cbb9f6d22269d819",
                    "82873bcdadd08d68e2324bb17902207b14e157c9404a89d30a07b3c952fc77e7",
                    "a838ff909aa18718423d5acef36dc410d15002643360847846b961c62879a917",
                    "e2633efc82727eebec3fd9c758e560d7d7f7e7ff8c635f5ff593bfda035ea23a",
                    "188eb792a72bf2e6fb1cde9818320f1f0b482ee555c36c6f4d593ae2bea1f3ea",
                    "dcd033d5e6a6a7278e7bf4386b239eeb3a980ac277cb283fe8e30bae30316976",
                    "f2ea1952d42f161cd4e5cd5496a0ac3e4a167f33932e7f36f0f1605f74726c85",
                    "a630556cd41248c5bfb97049e084d9192bdf010731edf32cbeb178944b4fe30b",
                    "19f770c0bab03e9d6c2cba6d2d5d792700444eb0bbd539e6b40b4072f084e679",
                    "13544da0e309fd3986a17d47e8550e0ae2111455fe7ac02e8ebdf65355099df8",
                    "1f91ae980e7eb28cc671a55cf088031ab62e987c6cd66897666f59fe89076af9",
                    "61934f809b71c41e13ccfb4a8876291e27c61ab2ddf3b7404ba5d87e2adfa468",
                    "1c63e2e2967177d1f834d233fe1803d7042336ad70e863db1854e9579391f758",
                    "38b7097e49db60a152332520e9041c1e573b66fbdf57db1ecc588d5eaa51bb98",
                    "8b425dea5d80ae2389b3bc800d259512cb3652806c1b64abb5599ed4120f4301",
                    "1eacafe014d92adebe6ff8a7acc0045946abe61f0ea087bd64c757b0b4d096e7",
                    "d7de2d4bae1561a56b566264bfbacfa49f46e82fa7b608ca6bed613dbe211f72",
                    "672d5303c82fb864d3af75418da47d2895bc6a28de2c91cc6dd9f8757bacf577",
                    "da27f178b7aae44282659079ad84c14846885747c9040485c2bfa612a3da8f47",
                    "d5ad50ef61382dbd410a7cf0d3c305c702b700f1d28233fc612f05a75a78c7fa",
                    "43aa78aedb3d2eb578948fdd7ac398ce0400743d32eb7cc3d3bb1aaa0df246b9",
                    "b16c3d94ce131cc773fb2c5fbda5cbe21f67e4a342678608ff8bb083e1f0e040",
                    "66f7ed9a374f712bf1e759e37d21a37ce5f77a678ef5250fb7d90879d8b2a653",
                    "f2f1c0c5e467f67d73c247153ea1b2b672425e67f30bcb763d7fc9b4c7453630",
                    "8c148945c1d96680373b579ebb81219702a305aa998ee341f8b23f4325c86fd2",
                    "d99da543f8458ac3e85d1152fdcbb4794f319900a1c41253aced8f886ff3fd77",
                    "1eaf8edb306cb5bb82fba10ce85054b284bd91fea82e145c67dba99ff2abebe1",
                    "a9e8c84ff50e03d9f60b94928a5f666faf3222c287bd0d1e4e6ab1cfc06a5f1e",
                    "04f82150ae84f61edce7c4a40541e7193c83491d090b1fb0d2cc8c39887e3fb7",
                    "b74148f019b7a404a69b94d9d874cc4e717adeb7b5948c472d16ab1184cc7a05",
                    "93441dca302b7dac9d3df8a56fcba602e55c23590fb9974284cbbd24a3a51d43",
                    "fb42db09d7e20ee8fc8d52744ebea08b47d5d55c70765e2a68154e32094fe7d7"
            },
            {
                    "83a970dbf9e09e977f4f8b97c3e8220c554b4e71b5c76c102e349593b948dc47",
                    "d496666a9b05ad64af82d037e6b66ddc9dab66a50e7cdad4f9c61671fe05cbf0",
                    "fd8a252944df5ddfe65c9d91e3a65c104c027003fb7d0e3643a65cbb17140473",
                    "787bbad6a0cbd9f7147dcb53236ee567c2c16b71fd51aa3b3e98575c7aec597a",
                    "f9e0277c2550b7168a074cbaa7178fe46499de74fd1614f4969bc78298a9c7f5",
                    "2e912425f2926f7d7717b8e347e71787202e13c6d468e58b83ec1d537a05cb1d",
                    "11610b3cba551b0e3797ac224411659ca7d9c7d57e31597d700fe003ccb348cb",
                    "85b4653a3e444429c5b3e59d2b75215479a1c9b1bf7e3698644d0879e8be4733",
                    "6586d1c3e6f3a607abd92967a994d6ec583b589aa80f13af533410dbb6f56993",
                    "cfe1da9d2e134fad89199c7b15c3277a34c366f2ea7b93164c1842afeaaaf7c7",
                    "5fbc289567e7a7b3e56802db051a82b6d7edf309261f66dde7e08ec8a777be33",
                    "5d06bd2f53b72f6ecee11b1a887366f688cee7617a1a443412cf1965d269ee69",
                    "030e1a27bf21c358a1cd24a6023861744c461ef574d3c392ab18423759ab97e1",
                    "91641e2ce080de4a20776eba96f8eba34d09d10f0a411e600f91fc72a1d357ed",
                    "cb83aaca888a5356fef01c81869d5b254c0ea1c4fe505ffcccf96b871b1b8ff3",
                    "5ec55c98ddb804d677f4a43d2cddc8c4fc9d2592f88495194f470ec1d3aab098",
                    "4550b11d718ba47e9928cd485eae6dc5fd003d7816bf6117417c8a8c284861be",
                    "bf9e64a0e7a72d299c10f0d8a78730e4066b1d664669469d23ca60246fa746eb",
                    "f5c8e87729d13d6969f0674e8eb40b39f635ddfc568b88b96dae7644dc6c6f74",
                    "7f7210978f1a3ea410112cccbda53b9419e3029fa14b1a7dc4a8f616feb15a52",
                    "ab85ac54de4fd8946a0069ecb19926de96903e82fe546c58ed9bac8e02f01901",
                    "6f0719cc5549ce41c5c2299bf8da8bda0aede20141061a81167823f84aefaa6f",
                    "255756ced244b084eb8d6be2e4ac9776504365ff6f14e9057db73e067b8b087f",
                    "9fc642ee1000e6ebac3f49bf17146adca1a5c7e02773938cc31e114a60856ff2",
                    "6179c488f545db17c821e4e3f8d250d8d6d6dc2886c6a4847789b656a0c25913",
                    "73de9f760c82c8d14de7c915fc593c771453f2c6f7318c51203f86dcf31ce6a2",
                    "acbaa8573473c01c8d7488e85a528e1a5f60fbfa9e256885e5c5afa7719f3a79",
                    "b737a186035c0464243ea08206136c1560100d4a92f663b1cd3fdc7b9e6d7c0c",
                    "33d15439e1fdf7657315d6137bf7a105f9767a68caf060cd242cae5d0a8b3add",
                    "b5cbbcd5c5d15c3cf0edcdc7b9c72ce6648748b2c728b0988d03380f37c2a197",
                    "ab01eb2773eb879a3e26b8485a09f354f1f7e9467e74f2fe84ce29ab027c6637",
                    "50be5dce88372622c55a697626ad8d48eca68d72b0f9fec08fe1e57e98406729",
                    "ebd27f7ce28afcdb35498c9fb6235966b2f3071c83710c84b7628578609dcd83",
                    "c873768550ce196b2ab982f8ccfa7ed6ab269de93934e86753ad3c65851cdac9",
                    "31b9d82985ec669004d6e8ba64af7b7ea2da767cb4c97d504224a46155e072da",
                    "7e68e8f69158e63d56bb9cc6d07673b8bce762d617951a6feea03010740d8df2",
                    "0f09f80d20612c03d6e60283d4de6ec5e95b309c74d65eed0ad23e22ba34e864",
                    "b4bceaf23cc282a4e7a00a2a6f9ecdc9d5839a7f7e03da5ef991b9dd983a457a"
            },
            {
                    "a7dfcd5215bd3c2cfc37c352218a26faa027afaa36389ba804bdda4badc106e7",
                    "f88b3b607f7322d5aded2822945c38a6f164279540436eefa730c5a43b9e3a40",
                    "9a0d1363c4b447d74f012712e72d197c306ee7cd6ba767990e189f3d984ae663",
                    "a767aebe133d097332d29a0afecfa07997141e8b9a00f11c87765b6b45e607d8",
                    "cfec13ca1c89cd036b9975916adfcfb61ad6c5132ef5296acc04c1d8c9ea1f66",
                    "8908bd456e47355e8dc64b65e07d247e04a82e1ba984962d80ed319987c243ba",
                    "5e2403be58ed4fddf98df9f6fe645d045d3f8fcbfa8cc0a4cc81ca0183d9ccc5",
                    "293f38bd15df2016fe82d753871803283996712061d72a24df800c946dfbde67",
                    "77dca5dd612761dd168ec19fd29fc9012c1c8ddba5103366e5b521180d6d6b56",
                    "fecabf806621eb5efbd7ff154dd25b38ca6b0fe9fe8f47f466ca637bf6990c6a",
                    "cd6dbb49cbdf608b0805decc4916172f02e24aad676d90f25413eb5f811a5662",
                    "5e28be26a466cb1390f7184d20eb5aeea0369e456eb9d62324262435fc67bd9b",
                    "e08eb1d3e06382b61a14ee67cbf6c9cf6e185fc0ec48bd873365d98aaa511e25",
                    "b07f014f89063aae752c982e0bf35acccda3f00758744c81a3ce989e8c3974c3",
                    "2d3fe3d447a499c4853ed375e1b1ba214609f16cef8783d9044f0bb023003a34",
                    "edb1bc98451f5b7950aa6037a3c551c330a74b1975fb09a1f2cae81ee27442b3",
                    "15c5620a74db272a6d63acbbb8afc7a040c8eea56d3faaa8df3947fb2f7e02b6",
                    "0fb0d6b13cbfd99fc89401d358dce3ec10426c491a09277131b8c739a1fa5e8b",
                    "26c38523a64f760f72cd296cb1ae49e02414548ad818a688ea6e158fd5e912dc",
                    "283036c7f333acadbfaad1da4ddf4a295e9d2d47d0cfb362944333b76634b6e7",
                    "2add3057054624748d39235159d97b5c52b154cd85174d3bc718c476153af6eb",
                    "45af138c60e468eace0ec6a197f79b1dc534577228d6cb2864c697abd077d477",
                    "42dca3e5b5f01c3bf8533da46a779908cb350adf3ce51d19718ec8058615d235",
                    "5d008c8187deab778e256397fe3497e6d5cba68661fa1c8992732303b4d82b6a",
                    "7e6ebbf0a5088c00cce2d37d130f4543feacb7a90524bfc08d4af7c4835854c5",
                    "46cb98e5da9804057e5bb320e27b8a13d9cce6d597d1860270c579ea91c2d5a2",
                    "a0a4501529e9b66859eb65a08dd5b7c7ae1fd6579c35674174d63f6895b585e6",
                    "15b46c8b52d2bd1358fb5a252558b7e9a9e3c12f4dc69d349f37cf7042593bd7",
                    "5d23f07a5f12f00bcf8a37b46ede7ca251cb1e30facd39f52093abbfa8c061ae",
                    "f1484127a2ac38705aad24a747b44487acc40f70b5ba93e77194de1bd1782ae8",
                    "d9d40b8bf1fd32cd64e53be5fe166d7c84a452e4183fc6f9cf40922132c7ee11",
                    "7b319a26b057c0119b7773eaacdcde5f12a4b06875df77f10fb49d0ff6b7b97e",
                    "4f64454dc2ab589764696ccf8c600d0fb64e2c38c1f151d1248e520c1124a32a",
                    "463e465623f69ee59e283a95c331f6c26d39277c548bccc190e06e22abf5df64",
                    "e147da7c8db378a66d998a52ea6b08b233ad7378ad4bfd64492a64ff5a3eaa47",
                    "b525b91b83dd47c389dd225aad05aaa9554ee61c72916976a42ba65404278333",
                    "8dba20cb1964d02ecdf1c3062b78d4b581b58dc6cda7b44fa0c3cf9177b1ca6d",
                    "00f32c6e5d1d2a965c50ed1c7fcd2e3e59e4771b0316bd65db3768d4630da7d0",
                    "3db62d1f3b86e7df90b2ecf14847e0c090dce981eb678f7362fd80a85e89692c",
                    "52ee8a6671cb5598f5ea5f14c007a81789729dae0f88c7475331327777bbbb10"
            },
    };

    private final int scenario;
    private final long seed;
    private final int difficulty;
    private final int[] rivals;

    public AiSourceResolvedTurnParityTest(int scenario, long seed, int difficulty,
                                          int[] rivals) {
        this.scenario = scenario;
        this.seed = seed;
        this.difficulty = difficulty;
        this.rivals = rivals;
    }

    @Parameterized.Parameters(name = "resolved source session {0}, seed {1}, difficulty {2}")
    public static Collection<Object[]> scenarios() {
        return Arrays.asList(new Object[][]{
                {0, 0L, 0, new int[]{0}},
                {1, 7L, 1, new int[]{2, 9}},
                {2, 42L, 2, new int[]{4}},
                {3, 1337L, 1, new int[]{0, 9, 12, 18}}
        });
    }

    @Test
    public void extendedResolvedTurnsMatchRecoveredPythonSession() throws Exception {
        GameSetup setup = new GameSetup(seed);
        setup.setDifficulty(difficulty);
        for (int rival : rivals) {
            assertTrue("setup accepts selected source rival " + rival,
                    setup.toggleOpponent(rival));
        }
        RecordingRandom random = new RecordingRandom(setup.copyGameplayRandom());
        GameState game = new GameState(setup, random);
        random.clear();

        List<String> actionHistory = new ArrayList<>();
        List<String> randomHistory = new ArrayList<>();
        for (int turn = 1; turn <= SOURCE_TURN_HASHES[scenario].length; turn++) {
            queueHumanResourcesAndMassMedia(game);
            random.clear();
            game.confirmTurn();

            actionHistory.add("turn " + turn + ": " + lastTurnActions(game)
                    + " combat=" + combatEvents(game));
            randomHistory.add("turn " + turn + "=" + random.draws.size() + ":"
                    + sha256(String.join(",", random.draws)));
            String trace = resolvedStateTrace(game, turn);
            assertEquals("source resolved-turn mismatch; scenario=" + scenario
                            + " turn=" + turn + "\nresolved state:\n" + trace
                            + "\nhidden state:\n" + hiddenStateTrace(game)
                            + "\nrecent actions: " + recentRows(actionHistory, 4)
                            + "\nrecent random streams: " + recentRows(randomHistory, 4),
                    SOURCE_TURN_HASHES[scenario][turn - 1], sha256(trace));
        }
        if (scenario == 2) {
            assertEquals("the source Hard session terminates on turn 38",
                    GameState.MatchStatus.DEFEAT, game.getMatchStatus());
            assertEquals("defeat skips payload stock settlement", 3,
                    game.getWeaponStock(1, "bioload4"));
        }
    }

    private static String recentRows(List<String> rows, int limit) {
        return String.join("; ", rows.subList(Math.max(0, rows.size() - limit), rows.size()));
    }

    private String resolvedStateTrace(GameState game, int turn) {
        List<String> rows = new ArrayList<>();
        for (int playerId = 0; playerId <= rivals.length; playerId++) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            List<String> cities = new ArrayList<>();
            for (CityState city : player.getCities()) {
                cities.add(Integer.toString(city.populationMillions));
            }

            Map<String, Integer> stocks = new TreeMap<>();
            for (Map.Entry<String, GameState.WeaponState> weapon
                    : player.getWeapons().entrySet()) {
                if (!"anti".equals(weapon.getValue().definition.type)) {
                    stocks.put(weapon.getKey(), weapon.getValue().inStock);
                }
            }
            List<String> stockRows = new ArrayList<>();
            for (Map.Entry<String, Integer> stock : stocks.entrySet()) {
                stockRows.add(stock.getKey() + "=" + stock.getValue());
            }

            Set<String> technologies = player.getInventedTechnologies();
            List<String> sortedTechnologies = new ArrayList<>(technologies);
            Collections.sort(sortedTechnologies);

            rows.add(String.join("|", "P", Integer.toString(scenario),
                    Integer.toString(turn), Integer.toString(player.id),
                    player.alive ? "1" : "0", Integer.toString(player.moneyMillions),
                    Integer.toString(player.populationMillions),
                    Integer.toString(player.religion), Long.toString(player.score),
                    String.join(",", cities), String.join(",", stockRows),
                    String.join(",", sortedTechnologies)));
        }
        return String.join("\n", rows);
    }

    private static final class RecordingRandom extends Random {
        private final Random delegate;
        private final List<String> draws = new ArrayList<>();

        RecordingRandom(Random delegate) {
            this.delegate = delegate;
        }

        void clear() {
            draws.clear();
        }

        @Override
        public int nextInt(int bound) {
            int value = delegate.nextInt(bound);
            draws.add(bound + ":" + value);
            return value;
        }

        @Override
        public double nextDouble() {
            double value = delegate.nextDouble();
            draws.add("raw:" + Double.toHexString(value));
            return value;
        }
    }

    private String lastTurnActions(GameState game) {
        List<String> actions = new ArrayList<>();
        for (int playerId = 0; playerId <= rivals.length; playerId++) {
            actions.add(playerId + "=" + game.getPlayerState(playerId).getLastTurnActions());
        }
        return String.join(", ", actions);
    }

    private String combatEvents(GameState game) {
        List<String> events = new ArrayList<>();
        for (GameState.CombatEvent event : game.getLastTurnCombatEvents()) {
            events.add(event.type + ":P" + event.attackerPlayerId + "->P"
                    + event.defenderPlayerId + "/C" + event.cityIndex + "/"
                    + event.payloadName + "/" + event.casualtiesMillions);
        }
        return events.toString();
    }

    private String hiddenStateTrace(GameState game) {
        List<String> rows = new ArrayList<>();
        for (int playerId = 0; playerId <= rivals.length; playerId++) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            List<String> regard = new ArrayList<>();
            List<String> ceasefire = new ArrayList<>();
            List<String> diplomacy = new ArrayList<>();
            for (int otherId = 0; otherId <= rivals.length; otherId++) {
                regard.add(String.format(Locale.US, "%.5f",
                        player.getRegardTowardExact(otherId)));
                ceasefire.add(Integer.toString(player.getCeasefireTurnsWith(otherId)));
                diplomacy.add(Integer.toString(
                        player.getDiplomacyOfferedLastTurn(otherId)));
            }
            List<String> desires = new ArrayList<>();
            List<String> active = new ArrayList<>();
            for (Map.Entry<String, GameState.WeaponState> weapon
                    : player.getWeapons().entrySet()) {
                int desire = player.getDesiredStock(weapon.getKey());
                if (desire > 0) {
                    desires.add(weapon.getKey() + "=" + desire);
                }
                if (weapon.getValue().active) {
                    active.add(weapon.getKey());
                }
            }
            List<String> bioloaded = new ArrayList<>();
            for (GameState.Action action : GameState.Action.values()) {
                if (player.isActionBioloaded(action)) {
                    bioloaded.add(action.name());
                }
            }
            rows.add(String.join("|", "P" + playerId,
                    player.isSpecialPerson() ? "special" : "ordinary",
                    player.isSuicidePerson() ? "suicide" : "ordinary",
                    "enemy=" + player.getPrimaryEnemyId() + "/"
                            + player.getSecondaryEnemyId(),
                    "prepared=" + player.getPreparedCarrierName(),
                    "spy=" + player.spyReadiness,
                    "regard=" + regard,
                    "cease=" + ceasefire,
                    "diplomacy=" + diplomacy,
                    "money=" + player.moneyMillions + "/" + player.turnMoneyMillions,
                    "bio=" + bioloaded,
                    "desired=" + desires,
                    "active=" + active,
                    "tech=" + player.getInventedTechnologies()));
        }
        return String.join(";", rows);
    }

    private static void queueHumanResourcesAndMassMedia(GameState game) {
        try {
            Field contaminatedActions = GameState.PlayerState.class
                    .getDeclaredField("bioloadedActions");
            contaminatedActions.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<GameState.Action> locked = (Set<GameState.Action>)
                    contaminatedActions.get(game.getHuman());
            Set<GameState.Action> original = new java.util.LinkedHashSet<>(locked);
            // The Python source-session adapter queues actions directly, without
            // consulting the touch UI's temporary biological lockout.
            locked.clear();
            try {
                game.selectAction(GameState.Action.RESOURCES);
                assertTrue(game.acceptSelectedAction());
                game.selectAction(GameState.Action.MASS_MEDIA);
                assertTrue(game.acceptSelectedAction());
            } finally {
                locked.addAll(original);
            }
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not reproduce source-session action queuing",
                    exception);
        }
    }

    private static String sha256(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            result.append(String.format(Locale.US, "%02x", item & 0xff));
        }
        return result.toString();
    }
}
