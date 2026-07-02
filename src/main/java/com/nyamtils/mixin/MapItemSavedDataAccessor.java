package com.nyamtils.mixin;

import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Exposes the dungeon map's player markers (a private field with no getter) for teammate heads. */
@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataAccessor {

    @Accessor("decorations")
    Map<String, MapDecoration> nyamtils$getDecorations();
}
