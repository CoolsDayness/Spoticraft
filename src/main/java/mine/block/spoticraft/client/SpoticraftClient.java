package mine.block.spoticraft.client;

import mine.block.spoticraft.client.config.SpoticraftConfig;
import mine.block.spoticraft.client.config.SpoticraftConfigModel;
import mine.block.spoticraft.client.ui.SpotifyScreen;
import mine.block.spotify.SpotifyHandler;
import mine.block.spotify.SpotifyUtils;
import mine.block.utils.LiveWriteProperties;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.apache.hc.core5.http.ParseException;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Environment(EnvType.CLIENT)
public class SpoticraftClient implements ClientModInitializer {

    public static final String MODID = "spoticraft";
    public static final Logger LOGGER = LoggerFactory.getLogger("Spoticraft");
    public static final LiveWriteProperties SPOTIFY_CONFIG = new LiveWriteProperties();
    public static final String VERSION = "1.1.2";
    public static final SpoticraftConfig MOD_CONFIG = SpoticraftConfig.createAndLoad();

    @Override
    public void onInitializeClient() {
        MOD_CONFIG.subscribeToResetSpotifyCredentials(SpoticraftConfigModel.Callbacks::onResetCredentials);
        MOD_CONFIG.subscribeToAutoMuteIngameMusic(SpoticraftConfigModel.Callbacks::onToggleMuteMusic);

        boolean reset = MOD_CONFIG.resetSpotifyCredentials();
        if (reset) {
            MOD_CONFIG.resetSpotifyCredentials(false);
            MOD_CONFIG.save();
        }
        SpotifyHandler.setup(reset);

        SpotifyHandler.PollingThread thread = new SpotifyHandler.PollingThread();
        ExecutorService checkTasksExecutorService = new ThreadPoolExecutor(1, 10,
                100000, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>());
        checkTasksExecutorService.execute(thread);
        SpotifyHandler.songChangeEvent.add(SpotifyUtils::run);

        var key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spotify.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "category.spotify.main"
        ));

        var forward = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spotify.forwards",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_PERIOD,
                "category.spotify.main"
        ));

        var backwards = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spotify.backwards",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_COMMA,
                "category.spotify.main"
        ));

        var playpause = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spotify.playpause",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_SEMICOLON,
                "category.spotify.main"
        ));


        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.wasPressed()) {
                client.setScreen(new SpotifyScreen(client.currentScreen));
            }


            while (forward.wasPressed()) {
                new Thread(() -> {try {
                    SpotifyHandler.SPOTIFY_API.skipUsersPlaybackToNextTrack().build().execute();
                    new SpotifyHandler.PollingThread().run();
                    SpotifyHandler.songChangeEvent.add(SpotifyUtils::run);
                } catch (IOException | ParseException | SpotifyWebApiException ignored) {}}).start();
            }

            while (backwards.wasPressed()) {
                new Thread(() -> {try {
                    SpotifyHandler.SPOTIFY_API.skipUsersPlaybackToPreviousTrack().build().execute();
                    new SpotifyHandler.PollingThread().run();
                    SpotifyHandler.songChangeEvent.add(SpotifyUtils::run);
                } catch (IOException | ParseException | SpotifyWebApiException ignored) {}}).start();
            }

            while (playpause.wasPressed()) {
                new Thread(() -> {try {
                    var status = SpotifyHandler.SPOTIFY_API.getInformationAboutUsersCurrentPlayback().build().execute();
                    if(status.getIs_playing()) SpotifyHandler.SPOTIFY_API.pauseUsersPlayback().build().execute();
                    else SpotifyHandler.SPOTIFY_API.startResumeUsersPlayback().build().execute();
                } catch (IOException | ParseException | SpotifyWebApiException ignored) {}}).start();
            }

        });
    }
}
