package com.explorermap.mixin;

import com.explorermap.table.TableCosts;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Every click in a menu passes through here, which is where the cartography table's price is checked before
// the result is handed over and paid afterwards. The work is in TableCosts, which ignores every other menu.
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void explorermap$beforeClick(int slotId, int button, ContainerInput input, Player player,
                                         CallbackInfo ci) {
        if (TableCosts.refuses((AbstractContainerMenu) (Object) this, slotId, input, player)) ci.cancel();
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void explorermap$afterClick(int slotId, int button, ContainerInput input, Player player,
                                        CallbackInfo ci) {
        TableCosts.afterClick((AbstractContainerMenu) (Object) this);
    }
}
