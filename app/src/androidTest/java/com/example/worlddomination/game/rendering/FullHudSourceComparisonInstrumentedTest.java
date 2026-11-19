package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.SafeAreaInsets;
import com.example.worlddomination.game.ui.SourceHelpState;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Source values and bounded layout invariants in full World paints; not whole-image equality. */
@RunWith(AndroidJUnit4.class)
public final class FullHudSourceComparisonInstrumentedTest {
    @Test public void sourceHudValuesSurvivePhoneScalingAndHelpComposition() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            SafeAreaInsets oldInsets = (SafeAreaInsets) field("pendingSafeInsets").get(app);
            GameSetup setup = new GameSetup(0L);
            field("setup").set(app,setup);
            field("game").set(app,null);
            field("skipNextFrameDelta").setBoolean(app,true);
            app.render();
            SetupScreenLayout setupLayout=(SetupScreenLayout)field("setupLayout").get(app);
            tap(app,setupLayout.difficultyButtons[0]);
            tap(app,setupLayout.buildingButtons[1]);
            tap(app,setupLayout.leaderCards[0]);
            tap(app,setupLayout.startButton);
            GameState game=(GameState)field("game").get(app);
            assertNotNull("Legal setup callbacks start the game",game);
            assertEquals(0,setup.getDifficulty());
            assertEquals(2,setup.getStartingBuilding());
            assertEquals(1,setup.getSelectedOpponentIds().size());
            double random = game.createPresentationRandomCopy().nextDouble();
            try {
                advance(app, 24);
                captureBoth(app, game, "initial");
                tap(app, ((Rectangle[]) field("navRows").get(app))[0]);
                assertEquals(GameState.Action.RESOURCES, game.getSelectedAction());
                tap(app, (Rectangle) field("modalAccept").get(app));
                assertEquals(GameState.Action.RESOURCES, game.getQueuedAction(0));
                tap(app, (Rectangle) field("helpButton").get(app));
                advance(app, 24);
                SourceHelpState help = (SourceHelpState) field("helpState").get(app);
                assertTrue(help.isVisible());
                assertEquals("help_index", help.getTopic());
                captureBoth(app, game, "resources-help");
                assertEquals(0, game.getTurn());
                assertEquals(80, game.getHuman().moneyMillions);
                assertEquals(80, game.getHuman().turnMoneyMillions);
                assertEquals(0, game.getHuman().score);
                assertEquals(1, game.getQueuedActionCount());
                assertEquals("Presentation must not consume canonical RNG", random,
                        game.createPresentationRandomCopy().nextDouble(), 0d);
            } finally {
                app.setSafeAreaInsetsPixels(oldInsets.left, oldInsets.top,
                        oldInsets.right, oldInsets.bottom);
                app.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            }
        });
    }

    private static void captureBoth(WorldDominationGame app, GameState game, String state)
            throws Exception {
        byte[] manifest=testAsset("source-hud-composition/source-full-hud.json");
        assertEquals("fa0c89ecc5c824be05b2579ee322c360b72f641c14b40657d068b62f666d466a",sha(manifest));
        JSONObject source=new JSONObject(new String(manifest,StandardCharsets.UTF_8));
        JSONObject captured=source.getJSONObject(state.equals("initial")?"initial":"resourcesHelp");
        JSONObject sourceState=captured.getJSONObject("state");
        assertEquals(sourceState.getInt("turn"),game.getTurn());
        assertEquals(sourceState.getInt("turnMoney"),game.getHuman().turnMoneyMillions);
        assertEquals(sourceState.getLong("score"),game.getHuman().score);
        assertEquals(sourceState.getInt("humanPopulation"),game.getHuman().populationMillions);
        assertEquals(sourceState.getInt("technologyLevel"),game.getHuman().getTechnologyLevel());
        for (int[] size : new int[][]{{800,600,0,0,0,0},{1194,552,44,0,44,21}}) {
            int w = size[0], h = size[1];
            app.setSafeAreaInsetsPixels(size[2],size[3],size[4],size[5]);
            app.resize(w,h);
            FrameBuffer target = new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false);
            String name = "full-hud-" + state + "-" + w + "x" + h;
            try {
                byte[] actual = paint(app,target,w,h);
                byte[] repeated = paint(app,target,w,h);
                assertArrayEquals("Frozen full composition at " + name,actual,repeated);
                evidence(name + ".png",actual,w,h);
                JSONObject sourceImage=captured.getJSONArray("images").getJSONObject(w==800?0:1);
                byte[] original=testAsset("source-hud-composition/"+sourceImage.getString("file"));
                assertEquals(sourceImage.getString("sha256"),sha(original));
                Gdx.files.absolute(new File(evidenceDirectory(),"full-hud-source-"+state+"-"+w+"x"+h+".png")
                        .getAbsolutePath()).writeBytes(original,false);
                assertStatisticPixels(app,actual,w,h,sourceState.getJSONArray("statMaskXScale"));
                assertColumns(app,sourceImage);
                assertFundsDrawn(app,target,actual,w,h);
                if(state.equals("resources-help")) assertFirstTube(app,actual,w,h);
                JSONObject meta = new JSONObject();
                meta.put("width",w).put("height",h).put("state",state);
                meta.put("safePixels",new JSONArray(new int[]{size[2],size[3],size[4],size[5]}));
                meta.put("turn",game.getTurn()).put("funds",game.getHuman().turnMoneyMillions)
                        .put("score",game.getHuman().score).put("queuedCount",game.getQueuedActionCount());
                meta.put("technology",game.getHuman().getTechnologyLevel())
                        .put("religion",game.getHuman().religion)
                        .put("population",game.getHuman().populationMillions)
                        .put("spy",game.getHuman().spyReadiness);
                Viewport viewport = (Viewport) field("viewport").get(app);
                meta.put("worldWidth",viewport.getWorldWidth()).put("worldHeight",viewport.getWorldHeight());
                JSONObject regions = new JSONObject();
                for (String key : new String[]{"logoPanel","navPanel","navViewport","infoPanel",
                        "centerHeader","mapFrame","mapBounds","toolbarPanel","stockPanel",
                        "messagePanel","scorePanel","actionPanel","helpButton","confirmButton"}) {
                    regions.put(key,physical(viewport,(Rectangle)field(key).get(app),h));
                }
                JSONArray slots = new JSONArray();
                for (Rectangle r : (Rectangle[])field("actionSlots").get(app))
                    slots.put(physical(viewport,r,h));
                meta.put("regions",regions).put("actionSlots",slots);
                Gdx.files.absolute(new File(evidenceDirectory(),name + ".json").getAbsolutePath())
                        .writeString(meta.toString(2),false,"UTF-8");
            } finally { target.dispose(); }
        }
    }

    private static JSONArray physical(Viewport v,Rectangle r,int height) throws org.json.JSONException {
        Vector2 low=v.project(new Vector2(r.x,r.y));
        Vector2 high=v.project(new Vector2(r.x+r.width,r.y+r.height));
        return new JSONArray(new float[]{low.x,height-high.y,high.x-low.x,high.y-low.y});
    }

    /** Source mask values are captured from Sub_Presenter_interface, not from the Java state. */
    private static void assertStatisticPixels(WorldDominationGame app,byte[] rgba,int w,int h,JSONArray masks)
            throws Exception {
        Rectangle panel=(Rectangle)field("infoPanel").get(app);
        Viewport v=(Viewport)field("viewport").get(app);
        double width=panel.width*.47d;
        int left=(int)(panel.x+panel.width*.47f);
        java.nio.IntBuffer precision=com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL20.GL_SUBPIXEL_BITS,precision);
        int subpixelBits=precision.get(0);
        assertTrue("OpenGL ES rasterization precision",subpixelBits>=4);
        double rasterStep=Math.scalb(1d,-subpixelBits);
        int roundedPopulationDifferences=0;
        for(int row=1;row<=4;row++) {
            // Approved enlarged five-row mobile layout; the reference supplies the values.
            int bottom=(int)(panel.y+panel.height-8f-(row+.5f)*(panel.height-16f)/5f-3f);
            Vector2 a=v.project(new Vector2(left,bottom+3f));
            Vector2 b=v.project(new Vector2(left+(int)width,bottom+3f));
            double scale=v.getScreenWidth()/(double)v.getWorldWidth();
            int y=h-1-(int)Math.floor(a.y);
            int columns=(int)Math.rint(width*masks.getDouble(row-1)/100d);
            int ink=0;
            for(int x=(int)Math.ceil(a.x-.5);x<(int)Math.ceil(b.x-.5);x++) {
                // Test pixel centers, independently of the game's ShapeRenderer calls.
                double logical=(x+.5-a.x)/scale;
                int column=(int)Math.floor(logical);
                boolean expected=column>=0 && column<columns;
                int k=(y*w+x)*4;
                boolean painted=(rgba[k]&255)>245 && (rgba[k+1]&255)>=40 && (rgba[k+2]&255)<8;
                assertEquals("source statistic row="+row+" pixel="+x+","+y,
                        expected,painted);
                if(painted) {
                    ink++;
                    // Rasterized vertices have GL_SUBPIXEL_BITS precision. Only a
                    // sample within one subpixel of a column edge can belong to
                    // either neighbor; interior samples retain the exact oracle.
                    double uncertainty=rasterStep/scale+0.0001d;
                    int first=Math.max(0,(int)Math.floor(logical-uncertainty));
                    int last=Math.min(columns-1,(int)Math.floor(logical+uncertainty));
                    int low=(int)Math.rint(45d+190d*(first/Math.max(1d,width)));
                    int high=(int)Math.rint(45d+190d*(last/Math.max(1d,width)));
                    int green=rgba[k+1]&255;
                    assertTrue("source gradient column="+column+" green="+green
                            +" allowed="+low+".."+high+" subpixelBits="+subpixelBits,
                            green>=low-1 && green<=high+1);
                }
                if(row==3 && expected!=(column<(int)Math.rint(width*53d/100d)))
                    roundedPopulationDifferences++;
            }
            assertTrue("nonempty full World statistic row "+row,ink>10);
        }
        if(w==800) assertTrue("Whole-percent rounding is a detectable negative control",
                roundedPopulationDifferences>0);
    }

    private static void assertColumns(WorldDominationGame app,JSONObject source) throws Exception {
        Rectangle left=(Rectangle)field("leftPanel").get(app);
        Rectangle right=(Rectangle)field("rightPanel").get(app);
        Rectangle messages=(Rectangle)field("messagePanel").get(app);
        Rectangle score=(Rectangle)field("scorePanel").get(app);
        double content=right.x+right.width-left.x;
        double sourceContent=source.getJSONArray("content").getDouble(2);
        assertEquals(source.getJSONArray("left").getDouble(2)/sourceContent,left.width/content,1d/sourceContent);
        assertEquals(source.getJSONArray("right").getDouble(2)/sourceContent,right.width/content,1d/sourceContent);
        double center=score.x+score.width-messages.x;
        assertEquals(source.getJSONArray("messages").getDouble(2)/source.getJSONArray("center").getDouble(2),
                messages.width/center,1d/source.getJSONArray("center").getDouble(2));
        assertTrue(left.x+left.width<messages.x && score.x+score.width<right.x);
    }

    private static void assertFundsDrawn(WorldDominationGame app,FrameBuffer target,byte[] actual,int w,int h)
            throws Exception {
        BitmapFont original=(BitmapFont)field("font").get(app);
        FundsRecorder recorder=new FundsRecorder(original);
        try {
            field("font").set(app,recorder);
            assertArrayEquals("Recording actual font calls must not alter any pixels",actual,paint(app,target,w,h));
            assertEquals(1,recorder.funds.size());
            JSONObject values=new JSONObject(new String(testAsset("source-information-values.json"),StandardCharsets.UTF_8));
            String expected=null;
            JSONArray rows=values.getJSONArray("fundsLabels");
            for(int i=0;i<rows.length();i++) if(rows.getJSONObject(i).getInt("inputTurnMoneyMillions")==80)
                expected=rows.getJSONObject(i).getJSONObject("label").getString("text");
            assertNotNull(expected);
            assertEquals("Real full-World draw must receive the source phone label",expected,recorder.funds.get(0));
        } finally { field("font").set(app,original);recorder.dispose(); }
    }

    private static final class FundsRecorder extends BitmapFont {
        private final BitmapFont original;
        final List<String> funds=new ArrayList<>();
        FundsRecorder(BitmapFont original) {
            super(original.getData(),original.getRegions(),false);
            this.original=original;setOwnsTexture(false);
        }
        @Override public float getScaleX() { return original.getScaleX(); }
        @Override public float getScaleY() { return original.getScaleY(); }
        @Override public GlyphLayout draw(Batch batch,CharSequence text,float x,float y) {
            if(text.toString().startsWith("$ ")) funds.add(text.toString());
            return super.draw(batch,text,x,y);
        }
    }

    private static void assertFirstTube(WorldDominationGame app,byte[] rgba,int w,int h) throws Exception {
        Viewport v=(Viewport)field("viewport").get(app);
        Rectangle[] slots=(Rectangle[])field("actionSlots").get(app);
        int[] green=new int[2];
        for(int i=0;i<slots.length;i++) {
            JSONArray r=physical(v,slots[i],h);
            for(int y=(int)Math.ceil(r.getDouble(1));y<r.getDouble(1)+r.getDouble(3);y++)
                for(int x=(int)Math.ceil(r.getDouble(0));x<r.getDouble(0)+r.getDouble(2);x++) {
                    int k=(y*w+x)*4;
                    if((rgba[k]&255)<100 && (rgba[k+1]&255)>150 && (rgba[k+2]&255)<100) green[i]++;
                }
        }
        assertTrue("Source occupied first tube is visibly green",green[0]>10);
        assertEquals("Empty second tube must not acquire Resources ink",0,green[1]);
        assertNotEquals("Swapping slots fails the source occupancy invariant",green[0],green[1]);
    }

    private static byte[] testAsset(String name) throws Exception {
        try(java.io.InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name)) {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[8192];int count;
            while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
            return out.toByteArray();
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        StringBuilder text=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes)) text.append(String.format(Locale.ROOT,"%02x",b&255));
        return text.toString();
    }

    private static byte[] paint(WorldDominationGame app,FrameBuffer target,int w,int h)
            throws Exception {
        field("skipNextFrameDelta").setBoolean(app,true);
        target.begin();
        try {
            app.render();
            assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
            return ScreenUtils.getFrameBufferPixels(0,0,w,h,true);
        } finally { target.end(); }
    }

    private static void advance(WorldDominationGame app,int ticks) throws Exception {
        Method update=WorldDominationGame.class.getDeclaredMethod("fixedUpdate",float.class);
        update.setAccessible(true);
        for(int i=0;i<ticks;i++) update.invoke(app,1f/24f);
    }

    private static void tap(WorldDominationGame app,Rectangle r) throws Exception {
        Viewport v=(Viewport)field("viewport").get(app);
        Vector2 p=v.project(new Vector2(r.x+r.width/2,r.y+r.height/2));
        int x=Math.round(p.x),y=Math.round(Gdx.graphics.getHeight()-p.y);
        app.touchDown(x,y,0,0); app.touchUp(x,y,0,0);
    }

    private static File evidenceDirectory() {
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);assertTrue(dir.isDirectory() || dir.mkdirs());return dir;
    }

    private static void evidence(String name,byte[] rgba,int w,int h) {
        Pixmap pixels=new Pixmap(w,h,Pixmap.Format.RGBA8888);
        try {
            pixels.getPixels().put(rgba).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(evidenceDirectory(),name).getAbsolutePath()),pixels);
        } finally { pixels.dispose(); }
    }

    private static Field field(String name) throws Exception {
        Field f=WorldDominationGame.class.getDeclaredField(name);f.setAccessible(true);return f;
    }
}
