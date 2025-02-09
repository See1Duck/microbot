package net.runelite.client.plugins.microbot.mining;

import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.NpcID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mining.enums.Rocks;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

enum State {
    MINING,
    RESETTING,
}

public class AutoMiningScript extends Script {

    public static final String version = "1.4.3";
    private static final int GEM_MINE_UNDERGROUND = 11410;
    private static final int BASALT_MINE = 11425;
    State state = State.MINING;

    public boolean run(AutoMiningConfig config) {
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        Rs2AntibanSettings.actionCooldownChance = 0.1;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                if (!config.ORE().hasRequiredLevel()) {
                    Microbot.log("You do not have the required mining level to mine this ore.");
                    return;
                }

                if (Rs2Equipment.isWearing("Dragon pickaxe"))
                    Rs2Combat.setSpecState(true, 1000);

                if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Microbot.pauseAllScripts) return;

                switch (state) {
                    case MINING:
                        if (Rs2Inventory.isFull()) {
                            state = State.RESETTING;
                            return;
                        }

                        GameObject rock = Rs2GameObject.findReachableObject(config.ORE().getName(), true, config.distanceToStray(), initialPlayerLocation);

                        if (rock != null) {
                            if (Rs2GameObject.interact(rock)) {
                                Rs2Player.waitForXpDrop(Skill.MINING, true);
                                Rs2Antiban.actionCooldown();
                                Rs2Antiban.takeMicroBreakByChance();
                            }
                        }
                        break;
                    case RESETTING:
                        List<String> itemNames = Arrays.stream(config.itemsToBank().split(",")).map(String::toLowerCase).collect(Collectors.toList());

                        if (config.useBank()) {
                            if (config.ORE() == Rocks.GEM && Rs2Player.getWorldLocation().getRegionID() == GEM_MINE_UNDERGROUND) {
                                if (Rs2DepositBox.openDepositBox()) {
                                    Rs2DepositBox.depositAll();
                                    Rs2DepositBox.closeDepositBox();
                                }
                            }
                            if (config.ORE() == Rocks.BASALT && Rs2Player.getWorldLocation().getRegionID() == BASALT_MINE) {
                                if (Rs2Walker.walkTo(2872,3935,0)){
                                    Rs2Inventory.useItemOnNpc(ItemID.BASALT, NpcID.SNOWFLAKE);
                                    Rs2Walker.walkTo(2841,10339,0);
                                }
                            } else {
                                if (!Rs2Bank.bankItemsAndWalkBackToOriginalPosition(itemNames, initialPlayerLocation, 0, config.distanceToStray()))
                                    return;
                            }

                        } else {
                            Rs2Inventory.dropAllExcept(Arrays.stream(config.itemsToKeep().split(",")).map(String::trim).toArray(String[]::new));
                        }

                        state = State.MINING;
                        break;
                }
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown(){
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }
}
