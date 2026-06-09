import java.util.HashMap;
import java.util.Objects;
import java.util.function.Supplier;

import processing.core.PApplet;
import processing.core.PFont;
import processing.event.KeyEvent;
import processing.event.MouseEvent;

/**
 * Sand Physics Like Sandbox
 * @author directconnections
 */
public class Sketch extends PApplet {
    /**
     * Each array element (long) has data represented like this
     * 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000
     * <------[rgb / hsb]------->  Type    <-------[metadata]------->  |<State and Rules (aka. flags)
     * 
     * Red, Green, Blue: Particle Color (this channel can also support hsb)
     * Type: Particle Type (for special behaviour if applicable)
     * Metadata: storing data about the cell i guess. could be useful for keeping track of stuff
     *      00000000 00000000 00000000
     * State and Rules (aka. flags) (the state of the cell and some rules that all cells probably have in common)
     *      See below for misc flags. start at {@link #MASK_TICKED}
     */

    /** Cell Encoding: Color Sector */
    public final long MASK_COLOR =             Long.parseUnsignedLong("11111111 11111111 11111111 00000000 00000000 00000000 00000000 00000000".replace(" ", ""), 2);
    public final int SHIFT_COLOR =             40;
    /** Cell Encoding: Type Sector */
    public final long MASK_TYPE =              Long.parseUnsignedLong("00000000 00000000 00000000 11111111 00000000 00000000 00000000 00000000".replace(" ", ""), 2);
    public final int SHIFT_TYPE =              32;
    /** Cell Encoding: Metadata Sector */
    public final long MASK_METADATA =          Long.parseUnsignedLong("00000000 00000000 00000000 00000000 11111111 11111111 11111111 00000000".replace(" ", ""), 2);
    public final int SHIFT_METADATA =          8;

    /** has this cell ticked this iteration yet? */
    public final long MASK_TICKED =            Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 10000000".replace(" ", ""), 2);
    /** this cell can swap positions with other cells who ask */
    public final long MASK_CAN_SWAP_WITH_ANY = Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 01000000".replace(" ", ""), 2);
    /** this cell cant swap with a target cell. However a target cell CAN swap to this cell (i.e. this cell can't move on its own) */
    public final long MASK_SELF_CANT_SWAP =    Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00100000".replace(" ", ""), 2);
    /** this cell wants to swap upwards (by default they will swap down) */
    public final long MASK_SWAP_UP =           Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00010000".replace(" ", ""), 2);
    /** this cell cant be destroyed unless the user destroys it */
    public final long MASK_INDESTRUCTABLE =    Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00001000".replace(" ", ""), 2);
    /** the cell's color value is custom and not natural to the default generation (could be useful for type handler) */
    public final long MASK_CUSTOM_COLOR =      Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000100".replace(" ", ""), 2);
    /** other cells that are shuffling can shuffle on this cell */
    public final long MASK_OTHERS_SHUFFLE_ON = Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000010".replace(" ", ""), 2);
    /** this cell can shuffle. they can only shuffle on cells with {@link #MASK_OTHERS_SHUFFLE_ON} */
    public final long MASK_CAN_SHUFFLE =       Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000001".replace(" ", ""), 2);

    // combined masks, represents multiple masks under a common name
    public final long MASKC_NATURALLY_IMMOVABLE = MASK_SELF_CANT_SWAP | MASK_OTHERS_SHUFFLE_ON;
    public final long MASKC_FLOATING_DISPLACEABLE = MASK_SELF_CANT_SWAP | MASK_CAN_SWAP_WITH_ANY;

    public final int TOOL_BRUSH_REPLACE_AIR = 0;
    public final int TOOL_BRUSH_REPLACE_ALL = 1;
    public final int TOOL_BRUSH_ERASE = 2;
    public final int TOOL_INSPECT = 3;
    public int selectedTool = 0;
    public String[] toolSymbols = {"🪣", "🖌️", "🧼", "🔍"};

    public boolean runSimulation = true;
    public boolean debuggingMetrics = false;
    public boolean canInteractStatusBar = true;
    public boolean canInteractCanvas = true;
    public boolean brushSelectionOpen = false;

    public int brushRadius = 2;
    public long[][] canvas;
    public int canvasX = 0;
    public int canvasY = 0;
    public int canvasWidth = 100 * 2;
    public int canvasHeight = 60 * 2;
    public int sandSize = 5;

    public HashMap<Byte, TypeCellCreate> typeCellCreate = new HashMap<>();
    public HashMap<Byte, TypeCellTick> typeCellTick = new HashMap<>();
    public HashMap<String, Byte> typeNames = new HashMap<>();

    public int statusbarSeperatorWidth = 5;
    public int statusbarContentHeight = 40;
    public int statusbarHeight = statusbarContentHeight + statusbarSeperatorWidth;

    public PFont fontEmoji;
    public PFont fontDefault;

    public int brushSelectionScrollAt = 0;

    public final byte TYPE_AIR = 0;
    public final byte TYPE_SAND = 1;
    public final byte TYPE_WATER = 2;
    public final byte TYPE_LAVA = 3;
    public final byte TYPE_COBBLESTONE = 4;
    public final byte TYPE_RAINBOW_SAND = 5;
    public final byte TYPE_BARRIER = -128;

    public final long CELL_AIR = cellEncodeData(color(0), TYPE_AIR, 0, (byte)(MASKC_FLOATING_DISPLACEABLE));
    public final long CELL_BARRIER_FLOOR = cellEncodeData(color(0), TYPE_BARRIER, 0, (byte)(MASK_INDESTRUCTABLE | MASKC_NATURALLY_IMMOVABLE | MASK_TICKED));

    public byte selectedType = TYPE_RAINBOW_SAND;

    public final Supplier<Integer> PALETTE_SAND = colorGeneratePaletteRandomizer(
        color(246, 215, 176),
        color(242, 210, 169),
        color(236, 204, 162),
        color(231, 196, 150),
        color(225, 191, 146)
    );

    public final Supplier<Integer> PALETTE_WATER = colorGeneratePaletteRandomizer(
        color(15, 94, 156),
        color(35, 137, 218),
        color(28, 163, 236),
        color(90, 188, 216),
        color(116, 204, 244)
    );

    public final Supplier<Integer> PALETTE_LAVA = colorGeneratePaletteRandomizer(
        color(255, 37, 0),
        color(255, 102, 0),
        color(242, 242, 23),
        color(234, 92, 15),
        color(229, 101, 32)
    );

    public final Supplier<Integer> PALETTE_COBBLESTONE = colorGeneratePaletteRandomizer(
        color(176, 179, 184),
        color(107, 110, 114),
        color(209, 211, 214),
        color(228, 230, 233),
        color(163, 166, 173)
    );

    public static void main(String[] args) {
        PApplet.main("Sketch");
    }

    @Override
    public void settings() {
        size(canvasWidth * sandSize, canvasHeight * sandSize + statusbarHeight);
        canvasInit(canvasWidth, canvasHeight);
    }

    @Override
    public void setup() {
        fontEmoji = createFont("font/NotoEmoji.ttf", 20);
        fontDefault = createFont("SansSerif", 12);

        colorMode(HSB, 360, 100, 100);
        registerCellTypes();
    }

    @Override
    public void draw() {
        cellRenderCanvas(canvasX, canvasY);
        if (runSimulation) cellTickAll();

        if (brushSelectionOpen) brushCellPanelSelector();
        renderStatusBar();

        if (!brushSelectionOpen) {
            if (selectedTool != TOOL_INSPECT) {
                renderToolOverlay(canvasX, canvasY, brushRadius);
            } else {
                renderToolOverlay(canvasX, canvasY, 0);
            }
        }

        setCursor();

        if (debuggingMetrics) renderDebuggers();
    }
    
    @Override
    public void mouseDragged() {
        if (brushSelectionOpen) return;

        toolApplyOnCanvas();
    }

    @Override
    public void mousePressed(MouseEvent event) {
        if (brushSelectionOpen) return;

        toolApplyOnCanvas();
        if (event.getButton() == LEFT && canInteractStatusBar) handleStatusBarMouse();
    }

    @Override
    public void mouseWheel(MouseEvent event) {
        int direction = event.getCount();

        if (direction < 0) {
            if (brushSelectionScrollAt < typeNames.size()) brushSelectionScrollAt++;
            if (brushRadius < Math.max(canvasHeight, canvasWidth)) brushRadius++;
        } else if (direction > 0) {
            if (brushSelectionScrollAt > 0) brushSelectionScrollAt--;
            if (brushRadius > 0) brushRadius--;
        }
    }

    @Override
    public void keyPressed(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int key = event.getKey();
        final int oneKey = 49;
        final int fourKey = 52;

        if (key == ESC) {
            this.key = 0;

            if (brushSelectionOpen) brushSelectionOpen = false;
            return;
        }

        if (keyCode >= oneKey && keyCode <= fourKey) selectedTool = keyCode - oneKey;
        if (keyCode == java.awt.event.KeyEvent.VK_F3) debuggingMetrics = !debuggingMetrics;
        if (key == ' ') runSimulation = !runSimulation;  // 32 is space
        if (key == 's' && !runSimulation) cellTickAll();  // step keybind
        if (keyCode == SHIFT) {
            brushSelectionScrollAt = 0;
            brushSelectionOpen = !brushSelectionOpen;
        }

        if (keyCode == UP && brushRadius < Math.max(canvasHeight, canvasWidth)) {
            brushRadius++;
        } else if (keyCode == DOWN && brushRadius > 0) {
            brushRadius--;
        }
    }

    /** Renders and handles input for the cell selection panel for the brush */
    public void brushCellPanelSelector() {
        push();
        colorMode(RGB, 255, 255, 255, 255);
        textFont(fontDefault);
        textAlign(LEFT, TOP);
        textSize(20);
        
        // background tint
        fill(0, 80);
        rect(0, 0, width, height);

        int borderWidth = 4;
        float xOffset = width * 0.5f;
        translate(xOffset, 0);

        // modal and border
        fill(255);
        rect(-borderWidth, 0, borderWidth, height - statusbarHeight);
        fill(0);
        rect(0, 0, width * 0.6f, height - statusbarHeight);

        float modalHeight = height - statusbarHeight;
        int numberOfTypesIndexes = typeNames.size() - 1;
        float textHeight = textAscent() + textDescent();
        int fittableTexts = (int)(modalHeight / textHeight);
        String[] cellNames = new String[numberOfTypesIndexes + 1];
        cellNames = typeNames.keySet().<String>toArray(cellNames);

        fill(255);
        for (int i = 0; i < fittableTexts; i++) {
            int index = Math.clamp(brushSelectionScrollAt + i, 0, numberOfTypesIndexes);
            String textToRender = cellNames[index];
            
            // input handler
            final boolean mouseWithinX = mouseX >= xOffset;
            final boolean mouseWithinSelectionY = mouseY >= i * textHeight && mouseY <= (i + 1) * textHeight;
            if (mousePressed && mouseWithinX && mouseWithinSelectionY) selectedType = typeNames.get(cellNames[index]);
            if (typeNames.get(textToRender) == selectedType) textToRender = "> " + textToRender;

            text(textToRender, 0, i * textHeight);
            if (index >= numberOfTypesIndexes) break;
        }

        pop();
    }

    /**
     * Helper Function for {@link #toolApplyOnCanvas(int, int)}
     * @see #toolApplyOnCanvas(int, int) Underlaying Function
     */
    public void toolApplyOnCanvas() {
        Integer[] pos = canvasXYFromMouse(canvasX, canvasY);
        if (Objects.isNull(pos)) return;

        toolApplyOnCanvas(pos[0], pos[1]);
    }

    /**
     * Applies tool behaviour to the canvas (i.e paint and erase)
     * @param cellX cell coordinate on the canvas to apply the tool
     * @param cellY cell coordinate on the canvas to apply the tool
     * @see #toolApplyOnCanvas() Helper Function for this function
     */
    public void toolApplyOnCanvas(int cellX, int cellY) {
        int xBeginBrush = Math.clamp(cellX - brushRadius, 0, canvasWidth - 1);
        int yBeginBrush = Math.clamp(cellY - brushRadius, 0, canvasHeight - 1);
        int xEndBrush = Math.clamp(cellX + brushRadius, 0, canvasWidth - 1) ;
        int yEndBrush = Math.clamp(cellY + brushRadius, 0, canvasHeight - 1) ;

        TypeCellCreate cellCreator = typeCellCreate.get(selectedType);

        // brush handler
        for (int x = xBeginBrush; x <= xEndBrush; x++) {
            for (int y = yBeginBrush; y <= yEndBrush; y++) {
                if (
                    (selectedTool == TOOL_BRUSH_REPLACE_AIR && cellGetType(canvas[x][y]) == TYPE_AIR)
                    || (selectedTool == TOOL_BRUSH_REPLACE_ALL)
                ) {
                    canvas[x][y] = cellCreator.create(x, y);
                } else if (selectedTool == TOOL_BRUSH_ERASE) {
                    canvas[x][y] = CELL_AIR;
                }
            }
        }
    }

    /**
     * Handles mouse inputs for the status bar
     * @see #renderStatusBar() Status Bar Renderer
     */
    public void handleStatusBarMouse() {
        if (!mouseWithinStatusBar()) return;

        int simulationControlEnd = statusbarContentHeight;
        int stepEnd = simulationControlEnd * 2;
        if (mouseX <= simulationControlEnd) { // pause/play
            runSimulation = !runSimulation;
        } else if (mouseX <= stepEnd) { // step
            if (!runSimulation) cellTickAll();
        }

        // tool selection
        int toolSectionBegin = stepEnd + statusbarSeperatorWidth;
        int toolSectionEnd = toolSectionBegin + (statusbarContentHeight * toolSymbols.length);
        if (mouseX >= toolSectionBegin && mouseX <= toolSectionEnd) {
            selectedTool = Math.floorDiv((mouseX - (statusbarContentHeight * 2) - statusbarSeperatorWidth), statusbarContentHeight);
        }

        int helpBegin = width - statusbarContentHeight;
        if (mouseX > helpBegin) { // help button
            System.out.println("help pressed");
        }
    }

    /**
     * Checks if the mouse is outside the status bar
     * @return true if it is, false otherwise
     */
    public boolean mouseWithinStatusBar() {
        return mouseX >= 0 && mouseX <= width && mouseY < height && mouseY >= height - statusbarContentHeight;
    }

    /** Sets the cursor icon when hovering over an interactable */
    public void setCursor() {
        if (brushSelectionOpen) {
            cursor(ARROW);
        } else if (mouseWithinStatusBar()) {
            int toolBarBegin = (statusbarContentHeight * 2) + statusbarSeperatorWidth;
            int toolBarEnd = toolBarBegin + (statusbarContentHeight * toolSymbols.length);
            
            boolean hoveringTools = (mouseX >= toolBarBegin && mouseX <= toolBarEnd);
            boolean hoveringRunStep = mouseX <= (statusbarContentHeight * 2);
            boolean hoveringHelp = mouseX > width - statusbarContentHeight;

            if (hoveringRunStep || hoveringTools || hoveringHelp) {
                cursor(HAND);
            } else {
                cursor(ARROW);
            }
        } else if (!canvasIsMouseOutside(canvasX, canvasY)) {
            if (selectedTool == TOOL_INSPECT) {
                cursor(HAND);
            } else {
                noCursor();
            }
        } else {
            cursor(ARROW);
        }
    }

    /**
     * Renders a square overlay for the selected tool
     * @param canvasX canvas begining x position
     * @param canvasY canvas begining y position
     * @param radius radius of the brush. the diamater of the brush is (1 + (2 * radius))
     */
    public void renderToolOverlay(int canvasX, int canvasY, int radius) {
        if (canvasIsMouseOutside(canvasX, canvasY)) return;
        Integer[] pos = canvasXYFromMouse(canvasX, canvasY);
        if (Objects.isNull(pos)) return;
        
        int x = pos[0];
        int y = pos[1];

        // to make sure brush doesnt clip off the canvas
        int brushWidth = (radius * 2 + 1);
        int brushHeight = (radius * 2 + 1);

        if (x <= radius) {
            brushWidth -= radius - x;
        } else if (x >= (canvasWidth - radius)) {
            brushWidth -= (x + radius + 1) - canvasWidth;
        }

        if (y <= radius) {
            brushHeight -= radius - y;
        } else if (y >= (canvasHeight - radius)) {
            brushHeight -= (y + radius + 1) - canvasHeight;
        }

        brushWidth = Math.clamp(brushWidth, 0, canvasWidth);
        brushHeight = Math.clamp(brushHeight, 0, canvasHeight);
        fill(255, 220);
        rect(
            Math.clamp(canvasX + (x - radius) * sandSize, canvasX, canvasX + canvasWidth * sandSize),
            Math.clamp(canvasY + (y - radius) * sandSize, canvasY, canvasY + canvasHeight * sandSize),
            brushWidth * sandSize,
            brushHeight * sandSize
        );
    }

    /**
     * Renders the status bar
     * @see #handleStatusBarMouse() Status Bar Input Handler
     */
    public void renderStatusBar() {
        push();
        translate(0, height - statusbarContentHeight);

        // top seperator
        fill(0, 0, 78);
        rect(0, -statusbarSeperatorWidth, width, statusbarSeperatorWidth);

        // body
        fill(0);
        rect(0, 0, width, statusbarContentHeight);
        
        textFont(fontEmoji);
        textAlign(CENTER, CENTER);
        if (runSimulation) {  // play
            fill(152, 55, 72);
            square(0, 0, statusbarContentHeight);

            fill(0, 0, 100);
            text("▶", statusbarContentHeight / 2f, statusbarContentHeight / 2f);
        } else {  // pause
            fill(0, 99, 80);
            square(0, 0, statusbarContentHeight);
            
            fill(0, 0, 100);
            text("⏸", statusbarContentHeight / 2f, statusbarContentHeight / 2f);
        }

        // step
        translate(statusbarContentHeight, 0);
        textAlign(CENTER, CENTER);
        textFont(fontDefault, 50);
        text(">", statusbarContentHeight / 2f, statusbarContentHeight / 2f);

        // seperator vertical
        fill(0, 0, 78);
        rect(statusbarContentHeight, 0, statusbarSeperatorWidth, statusbarContentHeight);
        
        // selected tool
        translate(statusbarSeperatorWidth + statusbarContentHeight, 0);
        fill(0, 0, 100);
        square(selectedTool * statusbarContentHeight, 0, statusbarContentHeight);

        // tool render
        textAlign(CENTER, CENTER);
        textFont(fontEmoji, 25);
        for (int i = 0; i < toolSymbols.length; i++) {
            fill(0, 0, i == selectedTool ? 0 : 255);
            text(toolSymbols[i], (statusbarContentHeight / 2f) + (statusbarContentHeight * i), (statusbarContentHeight / 2f));
        }

        // seperator vertical
        fill(0, 0, 78);
        translate((toolSymbols.length - 1) * statusbarContentHeight, 0);
        rect(statusbarContentHeight, 0, statusbarSeperatorWidth, statusbarContentHeight);

        pop();
        push();
        translate(width - statusbarContentHeight, height - statusbarContentHeight);

        // help button
        fill(0, 0, 100);
        textAlign(CENTER, CENTER);
        textFont(fontDefault, 25);
        text("❓", statusbarContentHeight / 2f, statusbarContentHeight / 2f);

        // seperator vertical
        fill(0, 0, 78);
        rect(-statusbarSeperatorWidth, 0, statusbarSeperatorWidth, statusbarContentHeight);

        pop();
    }

    /** Debug Renderers */
    public void renderDebuggers() {
        String debugText = String.format("FPS: %d", (int)frameRate);
        debugText += String.format("\nBrush Radius: %d", brushRadius);

        // cell information
        Integer[] pos = canvasXYFromMouse(canvasX, canvasY);
        if (Objects.nonNull(pos)) {
            int x = pos[0];
            int y = pos[1];
            debugText += String.format("\nHovering Over: %d, %d", x, y);
            debugText += "\nCell Data: " + debugLongToBinaryString(canvas[x][y]);
        }

        textFont(fontDefault);
        fill(255);
        textAlign(LEFT, TOP);
        text(debugText, 0, 0);
    }

    /**
     * Tick all the cells in the canvas
     * @see #cellTick(long, int, int, boolean) Underlaying Function
     */
    public void cellTickAll() {
        for (int x = 0; x < canvasWidth; x++) {
            for (int y = 0; y < canvasHeight; y++) {
                long cell = canvas[x][y];
                if (!cellIsFlagOn(cell, MASK_TICKED)) cellTick(cell, x, y, true);
            }
        }
        
        long resetTickedFlag = ~MASK_TICKED;
        for (int x = 0; x < canvasWidth; x++) {
            for (int y = 0; y < canvasHeight; y++) {
                canvas[x][y] &= resetTickedFlag;
            }
        }
    }

    /**
     * Tick the cell
     * @param cell cell value at (x, y)
     * @param x the cell at x position
     * @param y the cell at y position
     * @param canMove allow the cell to move in this tick ({@link TypeCellTick} bypass this)
     */
    public void cellTick(long cell, int x, int y, boolean canMove) {
        canvas[x][y] |= MASK_TICKED;

        if (canMove) {
            if (cellApplyGravity(cell, x, y)) {
                y++;
                cell = canvas[x][y];
            } else if (cellIsFlagOn(cell, MASK_CAN_SHUFFLE)) {
                Integer[] updatedPos = cellApplyShuffling(cell, x, y);
                x = updatedPos[0];
                y = updatedPos[1];
                cell = canvas[x][y];
            }
        }

        TypeCellTick handler = typeCellTick.get(cellGetType(cell));
        if (Objects.nonNull(handler)) handler.tick(cell, x, y);
    }

    /**
     * Apply cell shuffling
     * @param cell cell data
     * @param x cell x on canvas
     * @param y cell y on canvas
     * @return updated x and y coordinates packed into an array. in this format: {@code int[] position = {x, y};}
     * @see #cellShuffle(long, int, int, int, int) Underlaying Function
     */
    public Integer[] cellApplyShuffling(long cell, int x, int y) {
        // when shuffling:
        //      tick the cell ahead if it hasn't already ticked
        //      you must shuffle on a surface that permits you to do so
        //      if you are unable to shuffle. you are prob on a still surface. permit others to shuffle on you by enabling the MASK_OTHERS_SHUFFLE_ON flag
        int direction = cellSwapDirection(cell);
        if (direction == 0 || cellIsFlagOn(cell, MASK_SELF_CANT_SWAP)) return new Integer[]{x, y};
        long cellAhead = cellAtXYSafe(x, y + direction);

        if (!cellIsFlagOn(cellAhead, MASK_TICKED)) {
            cellTick(cellAhead, x, y + direction, true);
            cellAhead = cellAtXYSafe(x, y + direction);
        }

        long disableOthersShuffling = ~MASK_OTHERS_SHUFFLE_ON;
        if (cellIsFlagOn(cellAhead, MASK_OTHERS_SHUFFLE_ON)) {
            if (cellShuffle(cell, x, y, 1, direction)) {
                x++;
                y += direction;
            } else if (cellShuffle(cell, x, y, -1, direction)) {
                x--;
                y += direction;
            } else {
                canvas[x][y] |= MASK_OTHERS_SHUFFLE_ON;
            }
        } else {
            canvas[x][y] &= disableOthersShuffling;
        }

        return new Integer[]{x, y};
    }

    /**
     * Shuffle the cell (i.e fall over) see shuffle rule explanation in function
     * @param cell the cell data at (x, y)
     * @param x the cell x position
     * @param y the cell y position
     * @param relX shuffling on the x axis, must be either 1 or -1
     * @param relY shuffling on the y axis, must be either 1 or -1
     * @return true if it successfully shuffled, false otherwise
     */
    public boolean cellShuffle(long cell, int x, int y, int relX, int relY) {
        // shuffle rule:
        //      you must yield to cells that are moving into your shuffling position
        //      the cell you are shuffling towards must be able to swap with anyone
        //      cells that you must pass thru to get to your shuffled position (left or right, then up or down),
        //          must be able to be swapped with anyone
        int targetY = y + relY;
        long cellAdj = cellAtXYSafe(x + relX, y);
        int direction = cellSwapDirection(cell);

        boolean canAdjacentDisplace;
        boolean canTargetYDisplace;

        if (relX == 0) {
            canAdjacentDisplace = true;
        } else {
            canAdjacentDisplace = cellIsFlagOn(cellAdj, MASK_CAN_SWAP_WITH_ANY)
                                    && cellSwapDirection(cellAdj) != direction;
        }

        if (relY == 0) {
            canTargetYDisplace = true;
        } else {
            canTargetYDisplace = cellIsFlagOn(cellAtXYSafe(x + relX, targetY), MASK_CAN_SWAP_WITH_ANY);
        }

        if (
            canAdjacentDisplace
            && canTargetYDisplace
        ) {
            cellMoveRelativeInternal(x, y, relX, relY);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Applies gravity to the affected cell.
     * @param cell cell value at (x, y)
     * @param x cell x coords in the canvas
     * @param y cell y coords in the canvas
     * @return true when the cell has moved, false otherwise
     */
    public boolean cellApplyGravity(long cell, int x, int y) {
        int cellDirection = cellSwapDirection(cell);
        long cellBelow = cellAtXYSafe(x, y + 1);
        int cellBelowDirection = cellSwapDirection(cellBelow);
        int netDirection = Math.abs(cellDirection + cellBelowDirection);

        // both cells must be able to swap, both cannot move in the same direction, the cell below cannot be ticked
        if (cellIsFlagOn(cell & cellBelow, MASK_SELF_CANT_SWAP)) return false;
        if (netDirection == 2) return false;
        if (cellIsFlagOn(cellBelow, MASK_TICKED)) return false;
        
        boolean isSameType =              cellGetType(cell) == cellGetType(cellBelow);
        boolean isBothSwappable =         !cellIsFlagOn(cell | cellBelow, MASK_SELF_CANT_SWAP);
        boolean isColliding =             netDirection == 0 && cellIsFlagOn(cellBelow, MASK_SWAP_UP);

        boolean canMutuallySwap =         (cellIsFlagOn(cell, MASK_CAN_SWAP_WITH_ANY) && cellBelowDirection == -1)
                                            || (cellDirection == 1 && cellIsFlagOn(cellBelow, MASK_CAN_SWAP_WITH_ANY));
        boolean displaceablePresent =     netDirection == 1;

        if (
            (isSameType && isColliding && isBothSwappable)
            || (displaceablePresent && canMutuallySwap)
        ) {
            cellMoveRelativeInternal(x, y, 0, 1);
            return true;
        }

        return false;
    }

    /**
     * Renders the canvas to the screen
     * @param canvasX position the left of the canvas on this x coordinate
     * @param canvasY position the top of the canvas on this y coordinate
     */
    public void cellRenderCanvas(int canvasX, int canvasY) {
        noStroke();
        background(128);

        translate(canvasX, canvasY);
        for (int x = 0; x < canvasWidth; x++) {
            for (int y = 0; y < canvasHeight; y++) {
                fill(cellGetColor(canvas[x][y]));
                square(x * sandSize, y * sandSize, sandSize);
            }
        }
        translate(-canvasX, -canvasY);
    }

    /**
     * Removes the cell on the canvas (i.e replaces it with {@link #CELL_AIR}). if {@link #MASK_INDESTRUCTABLE} is set then the cell cant be removed unless it is forced
     * @param x cell x position
     * @param y cell y position
     * @param forced if true then the cell will be removed even with {@link #MASK_INDESTRUCTABLE}, else the cell can persist if that mask is set
     * @return true if it was removed successfully, false otherwise
     */
    public boolean cellRemove(int x, int y, boolean forced) {
        if (canvasIsCoordinatesOutOfBounds(x, y)) return false;
        if (cellIsFlagOn(canvas[x][y], MASK_INDESTRUCTABLE) && !forced) return false;
        
        canvas[x][y] = CELL_AIR;
        return true;
    }

    /**
     * Get cell at (x, y); if the coordinates are unreachable then {@link #CELL_BARRIER_FLOOR} will be return instead
     * @param x cell x position
     * @param y cell y position
     * @return the cell's value or {@link #CELL_BARRIER_FLOOR} if (x, y) is not on the canvas
     */
    public long cellAtXYSafe(int x, int y) {
        if (canvasIsCoordinatesOutOfBounds(x, y)) return CELL_BARRIER_FLOOR;
        return canvas[x][y];
    }

    /**
     * Get the direction that the cell wants to swap towards. If cell has {@link #MASK_SELF_CANT_SWAP} then 0 is returned
     * @param cell the cell to check
     * @return 1 if it wants to go down, -1 if up, 0 if {@link #MASK_SELF_CANT_SWAP} is enabled on the cell
     */
    public int cellSwapDirection(long cell) {
        if (cellIsFlagOn(cell, MASK_SELF_CANT_SWAP)) return 0;
        return cellIsFlagOn(cell, MASK_SWAP_UP) ? -1 : 1;
    }

    /**
     * Move the cell.
     *      <br> CAUTION: this function will only check if the target exists. it assumes that (x, y) are valid coordinates
     *      <br> NOTE: if the target has not ticked yet it will tick
     * @param x cell at the x position
     * @param y cell at the y position
     * @param targetX the resulting x position to move the cell to
     * @param targetY the resulting y position to move the cell to
     * @return true indicating a success at moving the cell, false otherwise
     * @see #cellMoveRelative(int, int, int, int) Related Function: Relative Moving
     */
    public boolean cellMoveAbsolute(int x, int y, int targetX, int targetY) {
        if (cellIsFlagOn(canvas[x][y], MASK_SELF_CANT_SWAP)) return false;
        cellMoveAbsoluteInternal(x, y, targetX, targetY);
        if (!cellIsFlagOn(canvas[x][y], MASK_TICKED)) cellTick(canvas[x][y], x, y, false);
        return true;
    }

    /**
     * Move the cell.
     *      <br> NOTE: this function will move the cell regardless if it has {@link #MASK_SELF_CANT_SWAP}.
     *      <br>       type handlers should use {@link #cellMoveAbsolute(int, int, int, int)} or {@link #cellMoveRelative(int, int, int, int)}
     *      <br> CAUTION: this function assumes both coordinates are valid
     * @param x cell at the x position
     * @param y cell at the y position
     * @param targetX the resulting x position to move the cell to
     * @param targetY the resulting y position to move the cell to
     * @see #cellMoveRelativeInternal(int, int, int, int) Related Internal Function: Relative Moving
     */
    public void cellMoveAbsoluteInternal(int x, int y, int targetX, int targetY) {
        long inital = canvas[x][y];
        long target = canvas[targetX][targetY];
        
        canvas[x][y] = target;
        canvas[targetX][targetY] = inital;
    }
    
    /**
     * Move the cell (with values relX, relY) relative to (x, y).
     * @param x cell at the x position
     * @param y cell at the y position
     * @param relX how much to move the cell on the x axis
     * @param relY how much to move the cell on the y axis
     * @return true indicating a success at moving the cell, false otherwise
     * @see #cellMoveAbsolute(int, int, int, int) Additional Function Information
     */
    public boolean cellMoveRelative(int x, int y, int relX, int relY) {
        return cellMoveAbsolute(x, y, x + relX, y + relY);
    }

    /**
     * Move the cell (with values relX, relY) relative to (x, y).
     * @param x cell at the x position
     * @param y cell at the y position
     * @param relX how much to move the cell on the x axis
     * @param relY how much to move the cell on the y axis
     * @see #cellMoveAbsoluteInternal(int, int, int, int) Additional Function Information
     */
    public void cellMoveRelativeInternal(int x, int y, int relX, int relY) {
        cellMoveAbsoluteInternal(x, y, x + relX, y + relY);
    }

    /**
     * Checks if the mouse is outside the canvas region
     * @param canvasX the x coordinate of the top left of the canvas
     * @param canvasY the y coordinate of the top left of the canvas
     * @return true if the mouse is outside the canvas, false otherwise
     */
    public boolean canvasIsMouseOutside(int canvasX, int canvasY) {
        int x = mouseX - canvasX;
        int y = mouseY - canvasY;
        
        return (x <= 0 || x >= (canvasWidth * sandSize))
            || (y <= 0 || y >= (canvasHeight * sandSize));
    }

    /**
     * Checks if cell(s) flag(s) are on (enabled). here comes some boolean algebra if you want to know how to use this to your advantage
     *      <br> to check for multiple flags, use bitwise or ({@code |}) to combine the flags
     *      <br> to check if either one or many cells has this flag. use bitwise or ({@code |}) to combine the many cells together
     *      <br> to check if all the cells have this flag, use ({@code &}) to combine the many cells together
     * @param cell the cell(s) to check against
     * @param enabledFlags the flag(s) to check on the cell(s)
     * @return true if the flag(s) are present on the cell(s), false otherwise
     * @see #MASK_CAN_SHUFFLE Flag Example
     */
    public boolean cellIsFlagOn(long cell, long enabledFlags) {
        return (cell & enabledFlags) == enabledFlags;
    }

    /**
     * Checks if (x, y) is within the canvas bounds
     * @param x the x axis to check
     * @param y the y axis to check
     * @return true if (x, y) is within the canvas bounds, false otherwise
     */
    public boolean canvasIsCoordinatesOutOfBounds(int x, int y) {
        return (x < 0 || x >= canvasWidth) || (y < 0 || y >= canvasHeight);
    }

    /**
     * Return the cell coordinate on the canvas from mouse position
     * @param canvasX where the canvas has been rendered on the x axis
     * @param canvasY where the canvas has been rendered on the y axis
     * @return cell (x, y) coordinates. It returns null if the mouse is outside the canvas
     */
    public Integer[] canvasXYFromMouse(int canvasX, int canvasY) {
        int x = (mouseX - canvasX) / sandSize;
        int y = (mouseY - canvasY) / sandSize;

        if (canvasIsMouseOutside(canvasX, canvasY)) return null;
        return new Integer[]{x, y};
    }

    /**
     * Creates and Initilizes the canvas with {@link #CELL_AIR}
     * @param width canvas width dimensions
     * @param height canvas height dimensions
     * @see #canvas Canvas Object
     */
    public void canvasInit(int width, int height) {
        canvas = new long[width][height];

        for (int x = 0; x < canvasWidth; x++) {
            for (int y = 0; y < canvasHeight; y++) {
                canvas[x][y] = CELL_AIR;
            }
        }
    }

    /**
     * Register the type with the cell system
     * @param name common name to associate with this type (no it doesn't have to be a fancy name in the code base. eg. {@code namespace:name}. this value will be presented to the end user)
     * @param uniqueID unique id for the type
     * @param creator cell type creation handler. see {@link TypeCellCreate}
     * @param ticker cell type tick handler. see {@link TypeCellTick}
     * @throws Exception will throw hands if name is already taken or uniqueID has already been reserved 
     * @see TypeCellTick Function Header for the ticking function
     * @see TypeCellCreate Function Header for the cell creation function
     */
    public void typeRegister(String name, byte uniqueID, TypeCellCreate creator, TypeCellTick ticker) {
        if (typeNames.containsKey(name)) throw new RuntimeException(String.format("type of name \"%s\" exists", name));
        if (typeCellCreate.containsKey(uniqueID) || typeCellTick.containsKey(uniqueID)) throw new RuntimeException(String.format("id %d is already reserved, cannot assign \"%s\"", uniqueID, name));

        typeNames.put(name, uniqueID);
        typeCellCreate.put(uniqueID, creator);
        typeCellTick.put(uniqueID, ticker);
    }

    /** Register the cell types to the system */
    public void registerCellTypes() {
        typeRegister("Air", TYPE_AIR, (x, y) -> CELL_AIR, (a, b, c) -> {});

        typeRegister("Sand", TYPE_SAND,
            (x, y) -> cellEncodeData(PALETTE_SAND.get(), TYPE_SAND, 0, (byte)MASK_CAN_SHUFFLE)
        , (cell, x, y) -> {
            behaviourHeavy(cell, x, y, TYPE_WATER);
        });

        typeRegister("Water", TYPE_WATER, 
            (x, y) -> cellEncodeData(PALETTE_WATER.get(), TYPE_WATER, 1, (byte)(MASK_CAN_SHUFFLE))
        , (cell, x, y) -> {
            x += behaviourFluid(cell, x, y);
            behaviourCellExchange(cell, x, y, TYPE_LAVA, TYPE_COBBLESTONE);
        });

        typeRegister("Lava", TYPE_LAVA, 
            (x, y) -> cellEncodeData(PALETTE_LAVA.get(), TYPE_LAVA, 1, (byte)(MASK_CAN_SHUFFLE))
        , (cell, x, y) -> {
            x += behaviourFluid(cell, x, y);
            behaviourCellExchange(cell, x, y, TYPE_WATER, TYPE_COBBLESTONE);
        });

        typeRegister("Cobblestone", TYPE_COBBLESTONE, 
            (x, y) -> cellEncodeData(PALETTE_COBBLESTONE.get(), TYPE_COBBLESTONE, 0, (byte)MASK_OTHERS_SHUFFLE_ON)
        , (cell, x, y) -> {
            behaviourHeavy(cell, x, y, TYPE_WATER);
        });

        typeRegister("Rainbow Sand", TYPE_RAINBOW_SAND, (x, y) -> {
            int hue = ((x + y) + (frameCount / 2)) % 360;
            return cellEncodeData(color(hue, 100, 100), TYPE_RAINBOW_SAND, 0, (byte)MASK_CAN_SHUFFLE);
        }, (cell, x, y) -> {
            behaviourRainbowCell(cell, x, y);
            behaviourHeavy(cell, x, y, TYPE_WATER);
        });
    }

    /**
     * Cell behaviour: If this cell touches another cell in the direction of its swap of type (collidedType), both cells will be destroyed and a cell of (targetType) will be created. It will fail to create if the target cell fails to be destroyed
     * @param cell cell data at (x, y)
     * @param x cell x coordinates
     * @param y cell y coordinates
     * @param collidedType cell type to be touching in the direction of its swap in order to create `targetType`
     * @param targetType cell type to create
     * @see #MASK_INDESTRUCTABLE Possible Reason for Failure (Indestructable Flag)
     */
    public void behaviourCellExchange(long cell, int x, int y, byte collidedType, byte targetType) {
        int moveDirection = cellSwapDirection(cell);
        long cellAhead = cellAtXYSafe(x, y + moveDirection);

        if (cellGetType(cellAhead) == collidedType && !cellIsFlagOn(cell, MASK_INDESTRUCTABLE) && cellRemove(x, y + moveDirection, false)) {
            canvas[x][y] = typeCellCreate.get(targetType).create(x, y);
        }
    }

    /**
     * Cell behaviour: This cell will intentionally displace `typeAgainst` because it is heavier than it
     * @param cell cell data at (x, y)
     * @param x cell x coordinates
     * @param y cell y coordinates
     * @param typeAgainst type to displace against, can be multiple if you add more arguments that are bytes
     * @return true if the cell has moved down, false otherwise
     */
    public boolean behaviourHeavy(long cell, int x, int y, byte... typeAgainst) {
        int moveDirection = cellSwapDirection(cell);
        long cellAhead = cellAtXYSafe(x, y + moveDirection);
        long cellAheadType = cellGetType(cellAhead);

        for (byte type : typeAgainst) {
            if (cellAheadType != type) continue;
            return cellMoveRelative(x, y, 0, moveDirection);
        }

        return false;
    }

    /**
     * Cell behaviour: This cell will change colors to all of the colors on the hue spectrum every 2 frames
     * @param cell cell data at (x, y)
     * @param x cell x coordinates
     * @param y cell y coordinates
     */
    public void behaviourRainbowCell(long cell, int x, int y) {
        if (frameCount % 2 != 0) return;

        int cellColor = cellGetColor(cell);
        float chan1 = (hue(cellColor) + 1) % 360;
        float chan2 = saturation(cellColor);
        float chan3 = brightness(cellColor);

        cellReplaceColor(cell, x, y, color(chan1, chan2, chan3));
    }

    /**
     * Cell behaviour: Fluid like so it will try to level out instead of making a mountain like shape
     * @param cell cell data at (x, y)
     * @param x cell x coordinates
     * @param y cell y coordinates
     * @return a number representing how many cells it moved on the x axis.
     */
    public int behaviourFluid(long cell, int x, int y) {
        if (!cellIsFlagOn(cell, MASK_OTHERS_SHUFFLE_ON)) return 0;
        short moveDirection = (short)cellGetMetadata(cell);
        if (cellShuffle(cell, x, y, moveDirection, 0)) return moveDirection;

        cellReplaceMetadata(cell, x, y, -moveDirection);
        return 0;
    }

    /**
     * Encode cell information into a long
     * @param color cell color value
     * @param type cell type
     * @param metadata cell metadata
     * @param flags cell flags
     * @return an encode cell value
     * @see #TYPE_BARRIER Cell Type Example
     * @see #MASK_INDESTRUCTABLE Cell Flag Example
     */
    public long cellEncodeData(int color, byte type, int metadata, byte flags) {
        final int rightThreeBytes = 16777215;

        return Integer.toUnsignedLong(color & rightThreeBytes) << SHIFT_COLOR
            | Byte.toUnsignedLong(type) << SHIFT_TYPE
            | Integer.toUnsignedLong(metadata & rightThreeBytes) << SHIFT_METADATA
            | Byte.toUnsignedLong(flags);
    }

    /**
     * Get cell color
     * @param cell the cell to get the color from
     * @return Proccessing compatible color value
     * @see #color(int, int, int)
     */
    public int cellGetColor(long cell) {
        final int alphaChannel = -16777216;
        return alphaChannel | (int)((cell & MASK_COLOR) >>> SHIFT_COLOR);
    }

    /**
     * Get cell type
     * @param cell the cell to fetch type from
     * @return the cell's type
     */
    public byte cellGetType(long cell) {
        return (byte)((cell & MASK_TYPE) >>> SHIFT_TYPE);
    }

    /**
     * Get cell metadata
     * @param cell the cell to fetch the metadata from
     * @return cell metadata
     */
    public int cellGetMetadata(long cell) {
        return (int)((cell & MASK_METADATA) >>> SHIFT_METADATA);
    }


    /**
     * Replaces the color of the cell
     *      <br> CAUTION: this function assumes that the coordinates (x, y) are valid
     * @param cell cell value at (x, y)
     * @param x cell position at x
     * @param y cell position at y
     * @param color processing compatible color to encode
     * @see #behaviourRainbowCell(long, int, int) Function Usage Example (Calling and Application)
     */
    public void cellReplaceColor(long cell, int x, int y, int color) {
        final int rightThreeBytes = 16777215;
        long toAdd = Integer.toUnsignedLong(color & rightThreeBytes) << SHIFT_COLOR;

        canvas[x][y] = (cell & ~MASK_COLOR) | toAdd;
    }
    
    /**
     * Converts metadata into cell compatible data
     *      <br> CAUTION: this function assumes that the coordinates (x, y) are valid
     * @param cell cell value at (x, y)
     * @param x cell position at x
     * @param y cell position at y
     * @param metadata metadata to encode
     * @see #behaviourFluid(long, int, int) Function Usage Example (Calling and Application)
     */
    public void cellReplaceMetadata(long cell, int x, int y, int metadata) {
        final int rightThreeBytes = 16777215;
        long toAdd = Integer.toUnsignedLong(metadata & rightThreeBytes) << SHIFT_METADATA;

        canvas[x][y] = (cell & ~MASK_METADATA) | toAdd;
    }

    /**
     * Generates a function that supplies you with a random selection of a color you supplied
     * @param color processing compatible colors (see {@link #color}). this function accepts multiple color's seperated by commas
     * @return a function that returns a random color from the colors you gave it
     * @see #PALETTE_WATER Function Call Example
     * @see #registerCellTypes() Usage of the return value
     */
    public Supplier<Integer> colorGeneratePaletteRandomizer(int... color) {
        return () -> color[(int)random(color.length)];
    }

    /**
     * Converts a long to a binary representation, where every 8 bits will be seperated by a space for visualizing each byte of the long
     * @param value long to convert
     * @return convert long as a binary string seperated by spaces every 8 bits
     */
    public String debugLongToBinaryString(long value) {
        String toParse = Long.toBinaryString(value);
        String result = "";
        toParse = "0".repeat(64 - toParse.length()) + toParse;
            
        for (int i = 0; i < 64; i += 8) {
            result += toParse.substring(i, i + 8) + " ";
        }

        return result.strip();
    }

    /**
     * This function will handle cell creation
     * @param x cell's x position
     * @param y cell's y position
     * @return cell data to place at (x, y)
     */
    @FunctionalInterface
    public interface TypeCellCreate {
        long create(int x, int y);
    }

    /**
     * This function will tick the cell at (x, y)
     * @param cell cell's data at (x, y)
     * @param x cell's x position
     * @param y cell's y position
     */
    @FunctionalInterface
    public interface TypeCellTick {
        void tick(long cell, int x, int y);
    }
}