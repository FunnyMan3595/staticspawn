package com.funnyman3595.staticspawn;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.DeferredWorkQueue;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.stream.Collectors;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("staticspawn")
public class StaticSpawnMod
{
    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();

    public StaticSpawnMod() {
        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);

        // Register ourselves for game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event)
    {
        DeferredWorkQueue.runLater(new AddWorldgenSpawns());
    }

    private class AddWorldgenSpawns implements Runnable {
        public void run() {
            int count = 0;
            for (Biome biome : Biome.BIOMES) {
                for (EntityClassification type : EntityClassification.values()) {
                    if (type == EntityClassification.CREATURE) {
                        continue;
                    }

                    for (Biome.SpawnListEntry spawn : biome.getSpawns(type)) {
                        biome.addSpawn(EntityClassification.CREATURE, spawn);
                        count++;
                    }
                }
            }
            LOGGER.info("Added {} worldgen spawns.", count);
        }
    }

    @SubscribeEvent
    public void blockNaturalSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.getSpawnReason() == SpawnReason.NATURAL) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void forbidDespawn(LivingSpawnEvent.AllowDespawn event) {
        event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public void buryMonsters(LivingSpawnEvent.SpecialSpawn event) {
        if (event.getSpawnReason() != SpawnReason.CHUNK_GENERATION) {
            // Not worldgen spawn.
            LOGGER.info("Non-worldgen spawn");
            return;
        }
        Entity rawEntity = event.getEntity();
        if (!(rawEntity instanceof MonsterEntity)) {
            // Not a monster.
            LOGGER.info("Non-monster spawn");
            return;
        }
        MonsterEntity entity = (MonsterEntity) rawEntity;

        if (!entity.world.isSkyLightMax(new BlockPos(entity.posX, (double)Math.round(entity.posY), entity.posZ))) {
            // Already shaded.
            LOGGER.info("Pre-shaded spawn");
            return;
        }

        // Find the lowest Y position where they could exist.
        EntitySpawnPlacementRegistry.PlacementType placement = EntitySpawnPlacementRegistry.getPlacementType(entity.getType());
        for (int y=1; y<Math.round(entity.posY); y++) {
            if (placement.canSpawnAt(entity.world, new BlockPos(entity.posX, (double)y, entity.posZ), entity.getType())) {
                LOGGER.info("Moving mob from {},{},{} to y={}", entity.posX, entity.posY, entity.posZ, y);
                entity.setPosition(entity.posX, (double)y, entity.posZ);
                return;
            }
        }
        LOGGER.info("Didn't find a new location for mob at {},{},{}", entity.posX, entity.posY, entity.posZ);
    }
}
