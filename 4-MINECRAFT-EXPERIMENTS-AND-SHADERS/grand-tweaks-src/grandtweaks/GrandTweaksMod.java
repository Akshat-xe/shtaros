package grandtweaks;

import net.fabricmc.api.ClientModInitializer;

public class GrandTweaksMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GrandConfig.load();
        System.out.println("[Grand Tweaks] Initialized with Metal support.");
    }
}
