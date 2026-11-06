package com.example.autofish;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = "autofish", name = "AutoFish", version = "1.0", clientSideOnly = true)
public class AutoFishMod {

    // Ticks to wait after reeling before recasting.
    private static final int RECAST_DELAY = 12;

    private final Minecraft mc = Minecraft.getMinecraft();
    private KeyBinding toggleKey;
    private KeyBinding holdKey;
    private boolean enabled = false;
    private boolean holdAttack = false;
    private volatile boolean biteHeard = false;
    private int recastTimer = -1;
    private int ticksSinceCast = 0;

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        toggleKey = new KeyBinding("Toggle AutoFish", Keyboard.KEY_K, "AutoFish");
        ClientRegistry.registerKeyBinding(toggleKey);
        holdKey = new KeyBinding("Toggle Hold Left Click", Keyboard.KEY_L, "AutoFish");
        ClientRegistry.registerKeyBinding(holdKey);
        MinecraftForge.EVENT_BUS.register(this);
        net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (toggleKey.isPressed()) {
            enabled = !enabled;
            recastTimer = -1;
            ticksSinceCast = 0;
            biteHeard = false;
            msg("AutoFish " + (enabled ? "ON" : "OFF"));
        }
        if (holdKey.isPressed()) {
            holdAttack = !holdAttack;
            if (!holdAttack) setAttack(false);
            msg("Hold left click " + (holdAttack ? "ON" : "OFF"));
        }
    }

    // A fish bite makes a splash sound at the bobber
    @SubscribeEvent
    public void onSound(PlaySoundEvent e) {
        if (!enabled || e.name == null || e.sound == null) return;
        if (!e.name.contains("splash")) return;
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return;
        EntityFishHook hook = p.fishEntity;
        if (hook == null) return;
        double dx = e.sound.getXPosF() - hook.posX;
        double dz = e.sound.getZPosF() - hook.posZ;
        double dy = e.sound.getYPosF() - hook.posY;
        if (dx * dx + dz * dz < 2.25D && Math.abs(dy) < 2.0D) {
            biteHeard = true;
        }
    }

    private void setAttack(boolean down) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), down);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        // Keep left click held while the lock is on (released when a menu is open)
        if (holdAttack) {
            if (mc.thePlayer == null || mc.currentScreen != null) {
                setAttack(false);
            } else {
                setAttack(true);
            }
        }

        if (!enabled) return;
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null || mc.currentScreen != null) return;
        if (p.getHeldItem() == null || !(p.getHeldItem().getItem() instanceof ItemFishingRod)) return;

        // Waiting to recast after a reel-in
        if (recastTimer >= 0) {
            biteHeard = false;
            if (recastTimer-- == 0) {
                useRod(p);
                ticksSinceCast = 0;
            }
            return;
        }

        EntityFishHook hook = p.fishEntity;
        if (hook == null) {
            ticksSinceCast = 0;
            biteHeard = false;
            return;
        }
        ticksSinceCast++;

        // Ignore the first second while the bobber is still flying / settling
        if (ticksSinceCast < 20) {
            biteHeard = false;
            return;
        }

        if (biteHeard) {
            biteHeard = false;
            useRod(p);               // reel in
            recastTimer = RECAST_DELAY;
        }
    }

    private void useRod(EntityPlayerSP p) {
        if (p.getHeldItem() == null) return;
        mc.playerController.sendUseItem(p, mc.theWorld, p.getHeldItem());
        p.swingItem();
    }

    private void msg(String s) {
        if (mc.thePlayer != null)
            mc.thePlayer.addChatMessage(new ChatComponentText("[AutoFish] " + s));
    }
}
