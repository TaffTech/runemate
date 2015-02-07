package com.runemate.geashawscripts.LazyAutoTanner;

//Imports are all the classes that we are going to use methods from

import com.runemate.game.api.client.ClientUI;
import com.runemate.game.api.client.paint.PaintListener;
import com.runemate.game.api.hybrid.RuneScape;
import com.runemate.game.api.hybrid.input.Keyboard;
import com.runemate.game.api.hybrid.local.Skill;
import com.runemate.game.api.hybrid.local.hud.interfaces.*;
import com.runemate.game.api.hybrid.util.StopWatch;
import com.runemate.game.api.rs3.local.hud.interfaces.eoc.ActionBar;
import com.runemate.game.api.rs3.local.hud.interfaces.eoc.SlotAction;
import com.runemate.game.api.rs3.net.GrandExchange;
import com.runemate.game.api.script.Execution;
import com.runemate.game.api.script.framework.LoopingScript;
import com.runemate.game.api.script.framework.listeners.InventoryListener;
import com.runemate.game.api.script.framework.listeners.events.ItemEvent;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.NumberFormat;

public class LazyAutoTanner extends LoopingScript implements PaintListener, InventoryListener, MouseListener, MouseMotionListener {

    private String status = "Loading...";
    private final String makeLeatherAction = "Make Leather";
    private final String dragonHide = "Green dragonhide";
    private final String tannedHide = "Green dragon leather";

    private String userName;

    private final StopWatch runtime = new StopWatch();
    private StopWatch updateTimer = new StopWatch();
    private final int updateIntervalMilliSeconds = 60000;

    private long timeRanSoFar, lastRunTime;

    private int startExp = -1, xpGained = 0, expGainedSoFar = 0, lastExpGained = 0,
            hidesTanned = 0, hidesTannedSoFar = 0, lastHidesTanned = 0,
            profitMade, profitMadeSoFar = 0, lastProfitMade = 0,
            hidePrice, leatherPrice, bodyRunePrice, astralRunePrice, runeCostPerHide, profitPerHide,
            userId;

    @Override
    public void onStart(String... args) {
        hidePrice = GrandExchange.lookup(1753).getPrice();
        leatherPrice = GrandExchange.lookup(1745).getPrice();
        bodyRunePrice = GrandExchange.lookup(559).getPrice();
        astralRunePrice = GrandExchange.lookup(9075).getPrice();
        // Getting the forum data.
        userId = com.runemate.game.api.hybrid.Environment.getForumId();
        userName = com.runemate.game.api.hybrid.Environment.getForumName();
        // Add the listener for the paint.
        getEventDispatcher().addListener(this);
        // Starting both timers.
        runtime.start(); updateTimer.start();
        // Standard loop delay little slower than normal.
        setLoopDelay(100, 200);
    }

    @Override
    public void onPause() {
        runtime.stop();
    }

    @Override
    public void onResume() {
        runtime.start();
    }

    @Override
    public void onLoop() {

        // Check if the user is logged in.
        if (RuneScape.isLoggedIn()) {

            // Update the database.
            if (updateTimer.getRuntime() > updateIntervalMilliSeconds) {

                timeRanSoFar = (runtime.getRuntime() - lastRunTime);
                expGainedSoFar = xpGained - lastExpGained;
                profitMadeSoFar = profitMade - lastProfitMade;
                hidesTannedSoFar = hidesTanned - lastHidesTanned;

                updateDatabase(userId, userName, timeRanSoFar, expGainedSoFar, profitMadeSoFar, hidesTannedSoFar);

                updateTimer.reset();

                // Reset xp gained, profit made and hides tanned.
                lastRunTime = runtime.getRuntime();
                lastExpGained = xpGained;
                lastProfitMade = profitMade;
                lastHidesTanned = hidesTanned;
            }

            // Perform the loop actions.
            if (gotHides() && !gotLeather()) {
                if (startExp == -1 && Skill.CONSTITUTION.getExperience() > 0) {
                    runtime.start();
                    startExp = Skill.MAGIC.getExperience();
                }
                if (interfaceTextIsVisible("Tan")) {
                    pressSpacebar();
                } else {
                    if (spellSelected()) {
                        clickHide();
                    } else {
                        selectSpell();
                    }
                }
            } else if (gotLeather() && !gotHides()) {
                if (Bank.isOpen()) {
                    performBankPreset();
                } else {
                    openBank();
                }
            }
        }
    }

    /**
     * @return Whether or not the spell is selected.
     */
    public boolean spellSelected() {
        SlotAction action = ActionBar.getFirstAction(makeLeatherAction);

        if (action != null) {
            if (action.isSelected()) {

                return true;
            } else {
                Execution.delayUntil(() -> action.isSelected(), 0, 500);
            }
        }

        return false;
    }

    /**
     * Check if an interface with a specific text is visible.
     */

    public boolean interfaceTextIsVisible(String text) {
        InterfaceComponent x = Interfaces.newQuery().texts(text).visible().results().first();
        return x != null && x.isValid() && x.isVisible();
    }

    /**
     * Check if the inventory contains at least 5 hides.
     */
    private boolean gotHides() {
        return (Inventory.contains(dragonHide) && Inventory.getQuantity(dragonHide) >= 5);
    }

    /**
     * Check if the inventory contains at least 5 tanned hides.
     */
    private boolean gotLeather() {
        return (Inventory.contains(tannedHide) && Inventory.getQuantity(tannedHide) >= 5);
    }

    /**
     * Select the Make Leather spell from the ability bar.
     */
    private boolean selectSpell() {
        SlotAction action = ActionBar.getFirstAction(makeLeatherAction);

        if (action != null) {
            status = "Activating Make Leather.";
            if (Keyboard.typeKey(action.getSlot().getKeyBind())) {
                if (!action.isSelected()) {
                    Execution.delayUntil(() -> action.isSelected(), 0, 500);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Click the dragon hide in the inventory.
     */
    private boolean clickHide() {
        final SpriteItem hide = Inventory.getItems(dragonHide).random();
        if (hide != null) {
            status = "Interacting Make Leather.";
            if (hide.interact("Cast")) {
                Execution.delayUntil(() -> interfaceTextIsVisible("Tan"), 500, 1000);
            }
            return true;
        }
        return false;
    }

    /**
     * Presses space bar.
     */
    private boolean pressSpacebar() {
        status = "Pressing spacebar.";
        if (Keyboard.typeKey(" ")) {
            if (interfaceTextIsVisible("Tan")) {
                status = "Tanning hides.";
                Execution.delayUntil(() -> !interfaceTextIsVisible("Tan"), 0, 500);
            }
            return true;
        }

        return false;
    }

    /**
     * Open the bank.
     */
    private boolean openBank() {
        status = "Opening the bank.";
        if (!Bank.isOpen()) {
            if (Bank.open()) {
                Execution.delayUntil(() -> Bank.isOpen(), 500);
            }

            return true;
        }
        return false;
    }

    /**
     * Perform quick banking with a bank preset.
     */
    private boolean performBankPreset() {
        InterfaceComponent component = Interfaces.getAt(762, 41);

        if (component != null && component.isVisible()) {
            if (component.click()) {
                status = "Performing bank preset";
                Execution.delayUntil(() -> !Bank.isOpen(), 0, 1000);
                return true;
            }
        }

        return false;
    }

    /**
     * Method to return hourly experience.
     *
     * @param amount  The amount of experience.
     * @param elapsed The elapsed time.
     * @return Returns experience per hour.
     */
    public int getHourly(final int amount, final long elapsed) {
        return (int) (amount * 3600000.0D / elapsed);
    }

    /**
     * Method to format thousands decimal.
     *
     * @param i The integer to format.
     * @return Returns the integer as formatted number.
     */
    protected String formatNumber(int i) {
        return NumberFormat.getIntegerInstance().format(i);
    }

    /**
     * Helper method used to replace System.out.println(text);
     *
     * @param text The text to send to the console.
     */
    private void debug(String text) {
        System.out.println(text);
    }

    /**
     * Count items that are added to inventory.
     */
    @Override
    public void onItemAdded(ItemEvent arg0) {
        if (arg0.getItem().getDefinition().getName().equals(tannedHide)) {
            hidesTanned++;
        }
    }

    private void updateDatabase(int userId, String userName, long time, int exp, int profit, int hidesTanned) {

        System.out.println("--- Inserting data into the database ---");
        System.out.println("1. Runtime: " + time);
        System.out.println("2. Experience: " + exp);
        System.out.println("3. Profit made: " + profit);
        System.out.println("4. Hides tanned: " + hidesTanned);

        try {
            String website = "http://erikdekamps.nl";
            URL submit = new URL(website + "/update?uid="+userId+"&username="+userName+"&runtime="+(time/1000)+"&exp="+exp+"&profit="+profit+"&hides="+hidesTanned);
            URLConnection connection = submit.openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            final BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;

            while ((line = rd.readLine()) != null) {
                if (rd.readLine().contains("success")) {
                    System.out.println("Successfully updated!");
                } else if (line.toLowerCase().contains("fuck off")) {
                    System.out.println("Something is fucked up, couldn't update!");
                }
                rd.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int startX, startY = 0;
    private int relativeX, relativeY;
    public int paintWidth = 200;
    public int paintHeight = 95;
    public int userCoverWith = 100;
    public int userCoverHeight = 19;

    private static boolean isMouseDown = false;

    /**
     * This is where we put everything that we want to draw to the screen.
     */
    @Override
    public void onPaint(Graphics2D g) {
        int TextXLocation = startX + 5;
        int TextYLocation = startY + 5;

        final Color color1 = new Color(51, 102, 255, 155);
        final Color color2 = new Color(0, 0, 0);
        final Color color3 = new Color(255, 255, 255);
        final BasicStroke stroke1 = new BasicStroke(1);
        final Font font1 = new Font("Tahoma", 0, 12);

        xpGained = Skill.MAGIC.getExperience() - startExp;

        runeCostPerHide = ((2 * bodyRunePrice) / 5) - ((2 * astralRunePrice) / 5);
        profitPerHide = leatherPrice - hidePrice - runeCostPerHide;
        profitMade = profitPerHide * hidesTanned;

        g.setColor(color1);
        g.fillRect(startX + 1, startY + 1, paintWidth, paintHeight);
        g.fillRect(startX + 1, startY + 1, paintWidth, paintHeight);
        g.setColor(color2);
        g.setStroke(stroke1);
        g.drawRect(startX + 1, startY + 1, paintWidth, paintHeight);
        g.setFont(font1);
        g.setColor(color3);

        g.drawString(getMetaData().getName() + " - Version " + getMetaData().getVersion(), TextXLocation, TextYLocation += 10);
        g.drawString("Run time: " + runtime.getRuntimeAsString(), TextXLocation, TextYLocation += 15);
        g.drawString("Status: " + status, TextXLocation, TextYLocation += 15);
        g.drawString("Exp gained: " + formatNumber(xpGained) + " (" + formatNumber(getHourly(xpGained, runtime.getRuntime())) + ")", TextXLocation, TextYLocation += 15);
        g.drawString("Hides tanned: " + formatNumber(hidesTanned) + " (" + formatNumber(getHourly(hidesTanned, runtime.getRuntime())) + ")", TextXLocation, TextYLocation += 15);
        g.drawString("Profit made: " + formatNumber(profitMade) + " (" + formatNumber(getHourly(profitMade, runtime.getRuntime())) + ")", TextXLocation, TextYLocation += 15);

        //Username Coverupper
        g.setColor(Color.black);
        g.fillRect(0, ClientUI.getFrame().getHeight() - 103, userCoverWith, userCoverHeight);
    }
    public void mouseClicked(MouseEvent arg0) {
        // TODO Auto-generated method stub
    }
    public void mouseEntered(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }
    public void mouseExited(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }
    public void mousePressed(MouseEvent arg0) {
        // TODO Auto-generated method stub
        isMouseDown = true;
        relativeX = arg0.getX() - startX;
        relativeY = arg0.getY() - startY;
    }
    public void mouseReleased(MouseEvent arg0) {
        // TODO Auto-generated method stub
        isMouseDown = false;
    }
    public void mouseDragged(MouseEvent e) {
        if (isMouseDown == true) {
            startX = e.getX() - relativeX;
            startY = e.getY() - relativeY;
        }
    }
    public void mouseMoved(MouseEvent e) {

    }
}