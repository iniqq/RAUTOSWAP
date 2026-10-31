package com.lungeswap;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

public class LungeSwapClient implements ClientModInitializer {
    public static KeyMapping activateKey;

    private static boolean toggled = false;
    private static int phase = 0;      // 0 = ready/waiting, 1 = swap to spear, 2 = swap back
    private static int timer = 0;
    private static int originalSlot = -1;
    private static int spearSlot = -1;
    private static Method startAttackMethod;

    @Override
    public void onInitializeClient() {
        LungeSwapConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("lungeswap", "main"));
        activateKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.lungeswap.activate", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, category));

        ClientTickEvents.END_CLIENT_TICK.register(LungeSwapClient::tick);
    }

    private static void tick(Minecraft mc) {
        LungeSwapConfig cfg = LungeSwapConfig.INSTANCE;

        if (mc.player == null || mc.gameMode == null || mc.gui.screen() != null) {
            abort(mc);
            while (activateKey.consumeClick()) { }
            return;
        }

        boolean active;
        if (cfg.toggleMode) {
            while (activateKey.consumeClick()) toggled = !toggled;
            active = toggled;
        } else {
            toggled = false;
            while (activateKey.consumeClick()) { }
            active = activateKey.isDown();
        }

        if (!active) {
            abort(mc);
            return;
        }

        switch (phase) {
            case 0 -> {
                if (timer > 0) { timer--; return; }

                int spear = findSpearInHotbar(mc);
                if (spear < 0) {
                    mc.player.sendOverlayMessage(Component.translatable("lungeswap.no_spear"));
                    timer = 20;
                    return;
                }
                originalSlot = mc.player.getInventory().getSelectedSlot();
                spearSlot = spear;

                clickAttack(mc);   // left click with whatever is currently held

                if (originalSlot == spearSlot) {
                    timer = Math.max(0, cfg.delayTicks - 1);
                } else {
                    phase = 1;
                }
            }
            case 1 -> {
                mc.player.getInventory().setSelectedSlot(spearSlot);
                if (cfg.attackWithSpear) clickAttack(mc);
                phase = 2;
            }
            case 2 -> {
                mc.player.getInventory().setSelectedSlot(originalSlot);
                phase = 0;
                timer = Math.max(0, cfg.delayTicks - 3);
            }
        }
    }

    private static void abort(Minecraft mc) {
        if (phase != 0 && originalSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }
        phase = 0;
        timer = 0;
    }

    /** Runs the game's own left-click routine with whatever slot is selected right now. */
    private static void clickAttack(Minecraft mc) {
        try {
            if (startAttackMethod == null) {
                startAttackMethod = Minecraft.class.getDeclaredMethod("startAttack");
                startAttackMethod.setAccessible(true);
            }
            startAttackMethod.invoke(mc);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int findSpearInHotbar(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().endsWith("_spear")) {
                return i;
            }
        }
        return -1;
    }
}
