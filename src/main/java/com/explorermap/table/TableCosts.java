package com.explorermap.table;

import com.explorermap.config.ExplorerMapServerConfig;
import com.explorermap.data.ExplorerMapSavedData;
import com.explorermap.data.MapIdentity;
import com.explorermap.network.ExpansionFamilySync;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * What locking and copying a map costs at the vanilla cartography table. Vanilla takes one glass pane to lock a
 * map and one empty map to copy it; here a map with expansion tiles takes more of the same item, stacked in the
 * lower slot. The table gives the result only when the stack is big enough, and takes the whole price with it.
 * Zooming out (paper) is left as vanilla has it.
 */
public final class TableCosts {

    private static final int MAP_SLOT = 0;
    private static final int PAYMENT_SLOT = 1;
    private static final int RESULT_SLOT = 2;

    // What to charge once a click that takes the result has gone through.
    private record Pending(int mapsBefore, int extraPerCraft) {}

    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

    private TableCosts() {}

    /** How many of the item the lower slot must hold: one, plus one more for every {@code tilesPerExtra} tiles. */
    public static int requiredCount(int tiles, int tilesPerExtra, int maxStack) {
        if (tiles <= 0 || tilesPerExtra <= 0) return 1;
        return Math.min(Math.max(1, maxStack), 1 + Math.ceilDiv(tiles, tilesPerExtra));
    }

    /**
     * Before a click in a menu: true if it would take the result of a cartography table whose lower slot holds
     * less than the map costs. The click is then refused and the player is told what is needed.
     */
    public static boolean refuses(AbstractContainerMenu menu, int slotId, ContainerInput input, Player player) {
        PENDING.remove();
        if (slotId != RESULT_SLOT || !(menu instanceof CartographyTableMenu)) return false;
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.isCreative()) return false;
        if (!takesResult(input)) return false;

        if (menu.getSlot(RESULT_SLOT).getItem().isEmpty()) return false;
        ItemStack map = menu.getSlot(MAP_SLOT).getItem();
        ItemStack payment = menu.getSlot(PAYMENT_SLOT).getItem();

        int tilesPerExtra;
        if (payment.is(Items.GLASS_PANE)) {
            tilesPerExtra = ExplorerMapServerConfig.tilesPerExtraLockPane();
        } else if (payment.is(Items.MAP)) {
            tilesPerExtra = ExplorerMapServerConfig.tilesPerExtraCloneMap();
        } else {
            return false;
        }
        if (tilesPerExtra <= 0) return false;

        Level level = serverPlayer.level();
        MinecraftServer server = level.getServer();
        MapIdentity root = MapIdentity.ofStack(map, level);
        if (server == null || root == null) return false;

        int tiles = ExpansionFamilySync.familyOf(ExplorerMapSavedData.get(server), root).maps().size() - 1;
        int perCraft = requiredCount(tiles, tilesPerExtra, payment.getMaxStackSize());
        if (perCraft <= 1) return false;

        // Shift-clicking keeps crafting while the inputs last, so it has to be paid for in full up front.
        int crafts = input == ContainerInput.QUICK_MOVE ? Math.min(map.getCount(), payment.getCount()) : 1;
        int needed = perCraft * crafts;
        if (payment.getCount() < needed) {
            serverPlayer.sendOverlayMessage(
                    Component.translatable("explorermap.table.cost", needed, payment.getHoverName()));
            return true;
        }

        PENDING.set(new Pending(map.getCount(), perCraft - 1));
        return false;
    }

    /** After a click: if it took the result, vanilla has taken one payment item, so take the rest of the price. */
    public static void afterClick(AbstractContainerMenu menu) {
        Pending pending = PENDING.get();
        if (pending == null) return;
        PENDING.remove();

        int crafts = pending.mapsBefore() - menu.getSlot(MAP_SLOT).getItem().getCount();
        if (crafts <= 0) return;

        Slot payment = menu.getSlot(PAYMENT_SLOT);
        int toTake = Math.min(payment.getItem().getCount(), crafts * pending.extraPerCraft());
        if (toTake > 0) payment.remove(toTake);
    }

    // Every way of taking a crafted result: click, shift-click, drop, and swapping it onto a hotbar slot.
    private static boolean takesResult(ContainerInput input) {
        return input == ContainerInput.PICKUP
                || input == ContainerInput.QUICK_MOVE
                || input == ContainerInput.THROW
                || input == ContainerInput.SWAP;
    }
}
