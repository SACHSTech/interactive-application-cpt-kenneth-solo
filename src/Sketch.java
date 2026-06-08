import java.util.HashMap;
import java.util.Objects;
import java.util.function.Supplier;

import processing.core.PApplet;
import processing.core.PFont;
import processing.event.MouseEvent;

// TODO: https://fonts.google.com/noto/specimen/Noto+Emoji source font

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
     * Red, Green, Blue: Particle Color (this channel can also support hsb) (we are cutting the first bit for a flag)
     * Type: Particle Type (for special behaviour if applicable)
     *      0 represents air
     *      -127 represents a barrier floor (basically cells outside the canvas range to prevent the cell from escaping)
     * Metadata: storing data about the cell i guess. could be useful for keeping track of stuff
     *      00000000 00000000 00000000
     * State and Rules (aka. flags) (the state of the cell and some rules that all cells probably have in common)
     *      See below for misc flags. start at {@link #MASK_TICKED}
     */

    public final long MASK_COLOR =             Long.parseUnsignedLong("11111111 11111111 11111111 00000000 00000000 00000000 00000000 00000000".replace(" ", ""), 2);
    public final int SHIFT_COLOR =             40;
    public final long MASK_TYPE =              Long.parseUnsignedLong("00000000 00000000 00000000 11111111 00000000 00000000 00000000 00000000".replace(" ", ""), 2);
    public final int SHIFT_TYPE =              32;
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
    public boolean debuggingMetrics = true;
    public boolean canInteractStatusBar = true;
    public boolean canInteractCanvas = true;

    public int brushRadius = 2;
    public long[][] canvas;
    public int canvasX = 25;
    public int canvasY = 25;
    public int canvasWidth = 100;
    public int canvasHeight = 60;
    public int sandSize = 5;

    public HashMap<Byte, TypeCellCreate> typeCellCreate = new HashMap<>();
    public HashMap<Byte, TypeCellTick> typeCellTick = new HashMap<>();
    public HashMap<String, Byte> typeNames = new HashMap<>();

    public int statusbarSeperatorWidth = 5;
    public int statusbarHeight = 40;

    public PFont fontEmoji;
    public PFont fontDefault;

    public final byte TYPE_AIR = 0;
    public final byte TYPE_SAND = 1;
    public final byte TYPE_WATER = 2;
    public final byte TYPE_LAVA = 3;
    public final byte TYPE_GRASS = 4;
    public final byte TYPE_COBBLESTONE = 5;
    public final byte TYPE_RAINBOW_SAND = 8;
    public final byte TYPE_BARRIER = -128;

    public final long CELL_AIR = cellEncodeData(true, color(0), TYPE_AIR, 0, (byte)(MASKC_FLOATING_DISPLACEABLE));
    public final long CELL_BARRIER_FLOOR = cellEncodeData(true, color(0), TYPE_BARRIER, 0, (byte)(MASK_INDESTRUCTABLE | MASKC_NATURALLY_IMMOVABLE | MASK_TICKED));

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
        size(canvasWidth * sandSize + 50, canvasHeight * sandSize + (sandSize + 40) + 50);
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

        renderStatusBar();

        if (selectedTool != TOOL_INSPECT) {
            renderToolOverlay(canvasX, canvasY, brushRadius);
        } else {
            renderToolOverlay(canvasX, canvasY, 0);
        }

        setCursor();

        if (debuggingMetrics) renderDebuggers();
    }
    
    @Override
    public void mouseDragged(MouseEvent event) {
        toolApplyOnCanvas(event);
    }

    @Override
    public void mousePressed(MouseEvent event) {
        if (canInteractStatusBar) handleStatusBarMouse(event.getX(), event.getY());
        toolApplyOnCanvas(event);
    }

    public void renderDebuggers() {
        String debugText = String.format("FPS: %d", (int)frameRate);

        // cell information
        Integer[] pos = canvasXYFromMouse(canvasX, canvasY, null);
        if (Objects.nonNull(pos)) {
            debugText += String.format("\nHovering Over: %d, %d", pos[0], pos[1]);
            debugText += "\nCell Data: ";

            String cell = Long.toBinaryString(canvas[pos[0]][pos[1]]);
            cell = "0".repeat(64 - cell.length()) + cell;
            
            for (int i = 0; i < 64; i += 8) {
                debugText += cell.substring(i, i + 8) + " ";
            }
        }

        textFont(fontDefault);
        fill(255);
        textAlign(LEFT, TOP);
        text(debugText, 0, 0);
    }

    /**
     * return the canvas coordinate for the related mouse position
     * @param canvasX where the canvas has been rendered on the x axis
     * @param canvasY where the canvas has been rendered on the y axis
     * @param event mouse coordinates if mouseX and mouseY is unreliable, if you dont have this just pass in null
     * @return updated x and y coordinates. (x, y)
     */
    public Integer[] canvasXYFromMouse(int canvasX, int canvasY, MouseEvent event) {
        int mouseX = this.mouseX;
        int mouseY = this.mouseY;

        if (Objects.nonNull(event)) {
            mouseX = event.getX();
            mouseY = event.getY();
        }

        int x = (mouseX - canvasX) / sandSize;
        int y = (mouseY - canvasY) / sandSize;

        // using x and y to detect if has gone below 0 does not work because 
        //      it returns 0 for the mouse being `sandSize` px to the left or top of 0
        //      (most likely from truncation when dividing by int)
        if (
            ((mouseX - canvasX) < 0 || x > canvasWidth - 1)
            || ((mouseY - canvasY) < 0 || y > canvasHeight - 1)
        ) return null;

        return new Integer[]{x, y};
    }

    public void canvasInit(int width, int height) {
        canvas = new long[width][height];

        for (int x = 0; x < canvasWidth; x++) {
            for (int y = 0; y < canvasHeight; y++) {
                canvas[x][y] = CELL_AIR;
            }
        }
    }

    public void toolApplyOnCanvas(MouseEvent event) {
        Integer[] pos = canvasXYFromMouse(canvasX, canvasY, event);
        if (Objects.isNull(pos)) return;

        toolApplyOnCanvas(pos[0], pos[1]);
    }

    public void toolApplyOnCanvas(int canvasX, int canvasY) {
        final int xBeginBrush = Math.clamp(canvasX - brushRadius, 0, canvasWidth - 1);
        final int yBeginBrush = Math.clamp(canvasY - brushRadius, 0, canvasHeight - 1);
        final int xEndBrush = Math.clamp(canvasX + brushRadius, 0, canvasWidth - 1) ;
        final int yEndBrush = Math.clamp(canvasY + brushRadius, 0, canvasHeight - 1) ;

        TypeCellCreate creator = typeCellCreate.get(selectedType);

        // brush handler
        for (int x = xBeginBrush; x <= xEndBrush; x++) {
            for (int y = yBeginBrush; y <= yEndBrush; y++) {
                if (
                    (selectedTool == TOOL_BRUSH_REPLACE_AIR && cellGetType(canvas[x][y]) == TYPE_AIR)
                    || (selectedTool == TOOL_BRUSH_REPLACE_ALL)
                ) {
                    canvas[x][y] = creator.create(x, y);
                } else if (selectedTool == TOOL_BRUSH_ERASE) {
                    canvas[x][y] = CELL_AIR;
                }
            }
        }
    }

    public void handleStatusBarMouse(int x, int y) {
        if (x < 0 || x > width || y > height || y < height - statusbarHeight) return;

        if (x <= statusbarHeight) { // pause/play
            runSimulation = !runSimulation;
        } else if (x <= (statusbarHeight * 2)) { // step
            if (!runSimulation) cellTickAll();
        }

        // tool selection
        final int toolSectionBegin = (statusbarHeight * 2) + statusbarSeperatorWidth;
        final int toolSectionEnd = toolSectionBegin + (statusbarHeight * toolSymbols.length);
        if (x >= toolSectionBegin && x <= toolSectionEnd) {
            selectedTool = Math.floorDiv((x - (statusbarHeight * 2) - statusbarSeperatorWidth), statusbarHeight);
        }

        final int helpBegin = width - statusbarHeight;
        if (x > helpBegin) { // help button
            System.out.println("help pressed");
        }
    }

    public void setCursor() {
        if (mouseX >= 0 && mouseX <= width && mouseY < height && mouseY >= height - statusbarHeight) {
            final int toolBarBegin = (statusbarHeight * 2) + statusbarSeperatorWidth;
            final int toolBarEnd = toolBarBegin + (statusbarHeight * toolSymbols.length);
            
            final boolean hoveringTools = (mouseX >= toolBarBegin && mouseX <= toolBarEnd);
            final boolean hoveringRunStep = mouseX <= (statusbarHeight * 2);
            final boolean hoveringHelp = mouseX > width - statusbarHeight;

            if (hoveringRunStep || hoveringTools || hoveringHelp) {
                cursor(HAND);
            } else {
                cursor(ARROW);
            }
        } else if (!canvasIsMouseOutside(canvasX, canvasY, null)) {
            if (selectedTool == TOOL_INSPECT) {
                cursor(HAND);
            } else {
                noCursor();
            }
        } else {
            cursor(ARROW);
        }
    }

    public boolean canvasIsMouseOutside(int canvasX, int canvasY, MouseEvent event) {
        int x = mouseX;
        int y = mouseY;

        if (Objects.nonNull(event)) {
            x = event.getX();
            y = event.getY();
        }

        x -= canvasX;
        y -= canvasY;
        
        return (x <= 0 || x >= (canvasWidth * sandSize))
            || (y <= 0 || y >= (canvasHeight * sandSize));
    }

    /**
     * renders a square overlay for the selected tool
     * @param canvasX canvas begining x position
     * @param canvasY canvas begining y position
     * @param radius radius of the brush. the diamater of the brush is (1 + (2 * radius))
     */
    public void renderToolOverlay(int canvasX, int canvasY, int radius) {
        if (canvasIsMouseOutside(canvasX, canvasY, null)) return;
        Integer[] pos = canvasXYFromMouse(canvasX, canvasY, null);
        if (Objects.isNull(pos)) return;
        
        int x = pos[0];
        int y = pos[1];

        // to make sure brush doesnt clip off the canvas
        int xOffset = 0;
        int yOffset = 0;

        if (x <= radius) {
            xOffset = radius - x;
        } else if (x >= (canvasWidth - radius)) {
            xOffset = radius - (canvasWidth - x - 1);
        }

        if (y <= radius) {
            yOffset = radius - y;
        } else if (y >= (canvasHeight - radius)) {
            yOffset = radius - (canvasHeight - y - 1);
        }

        int brushWidth = (radius * 2 + 1 - xOffset) * sandSize;
        int brushHeight = (radius * 2 + 1 - yOffset) * sandSize;

        fill(255, 200);
        rect(
            Math.clamp(canvasX + (x - radius) * sandSize, canvasX, canvasX + canvasWidth * sandSize),
            Math.clamp(canvasY + (y - radius) * sandSize, canvasY, canvasY + canvasHeight * sandSize),
            brushWidth,
            brushHeight
        );
    }

    public void renderStatusBar() {
        push();
        translate(0, height - statusbarHeight);

        // top seperator
        fill(0, 0, 78);
        rect(0, -statusbarSeperatorWidth, width, statusbarSeperatorWidth);

        // body
        fill(0);
        rect(0, 0, width, statusbarHeight);
        
        textFont(fontEmoji);
        textAlign(CENTER, CENTER);
        if (runSimulation) {  // play
            fill(152, 55, 72);
            square(0, 0, statusbarHeight);

            fill(0, 0, 100);
            text("▶", statusbarHeight / 2f, statusbarHeight / 2f);
        } else {  // pause
            fill(0, 99, 80);
            square(0, 0, statusbarHeight);
            
            fill(0, 0, 100);
            text("⏸", statusbarHeight / 2f, statusbarHeight / 2f);
        }

        // step
        translate(statusbarHeight, 0);
        textAlign(CENTER, CENTER);
        textFont(fontDefault, 50);
        text(">", statusbarHeight / 2f, statusbarHeight / 2f);

        // seperator vertical
        fill(0, 0, 78);
        rect(statusbarHeight, 0, statusbarSeperatorWidth, statusbarHeight);
        
        // selected tool
        translate(statusbarSeperatorWidth + statusbarHeight, 0);
        fill(0, 0, 100);
        square(selectedTool * statusbarHeight, 0, statusbarHeight);

        // tool render
        textAlign(CENTER, CENTER);
        textFont(fontEmoji, 25);
        for (int i = 0; i < toolSymbols.length; i++) {
            fill(0, 0, i == selectedTool ? 0 : 255);
            text(toolSymbols[i], (statusbarHeight / 2f) + (statusbarHeight * i), (statusbarHeight / 2f));
        }

        // seperator vertical
        fill(0, 0, 78);
        translate((toolSymbols.length - 1) * statusbarHeight, 0);
        rect(statusbarHeight, 0, statusbarSeperatorWidth, statusbarHeight);

        pop();
        push();
        translate(width - statusbarHeight, height - statusbarHeight);

        // help button
        fill(0, 0, 100);
        textAlign(CENTER, CENTER);
        textFont(fontDefault, 25);
        text("❓", statusbarHeight / 2f, statusbarHeight / 2f);

        // seperator vertical
        fill(0, 0, 78);
        rect(-statusbarSeperatorWidth, 0, statusbarSeperatorWidth, statusbarHeight);

        pop();
    }

    /** tick all the cells in the canvas */
    public void cellTickAll() {
        // int count = 0;
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
                // if (cellGetType(canvas[x][y]) != TYPE_AIR) count++;
            }
        }
        // System.out.println(count);
    }

    /**
     * Tick the cell
     * @param cell cell value at (x, y)
     * @param x the cell at x position
     * @param y the cell at y position
     * @param canMove allow the cell to move in this tick (special behaviours can bypass this argument)
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
        if (Objects.nonNull(handler)) {
            handler.tick(cell, x, y);
        }
    }

    /**
     * Register the type with the cell system
     * @param name common name to associate with this type (no it doesn't have to be a fancy name in the code base. eg. {@code namespace:name}. this value will be presented to the end user)
     * @param uniqueID unique id for the type
     * @param handler the type handler. see {@link TypeCellTick}
     * @throws Exception will throw hands if name is already taken or uniqueID has already been reserved 
     * @see TypeCellTick
     */
    public void typeRegister(String name, byte uniqueID, TypeCellCreate creator, TypeCellTick ticker) {
        if (typeNames.containsKey(name)) throw new RuntimeException(String.format("type of name \"%s\" exists", name));
        if (typeCellCreate.containsKey(uniqueID) || typeCellTick.containsKey(uniqueID)) throw new RuntimeException(String.format("id %d is already reserved, cannot assign \"%s\"", uniqueID, name));

        typeNames.put(name, uniqueID);
        typeCellCreate.put(uniqueID, creator);
        typeCellTick.put(uniqueID, ticker);
    }

    /**
     * Apply cell shuffling
     * @param cell cell data
     * @param x cell x on canvas
     * @param y cell y on canvas
     * @param direction where the cell is moving towards according to gravity rule (see above for sector)
     * @return updated x and y coordinates. (x, y)
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
            canvas[x][y] &= ~MASK_OTHERS_SHUFFLE_ON;
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
     * @return true if success else false
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
        
        final boolean isSameType =              cellGetType(cell) == cellGetType(cellBelow);
        final boolean isBothSwappable =         !cellIsFlagOn(cell | cellBelow, MASK_SELF_CANT_SWAP);
        final boolean isColliding =             netDirection == 0 && cellIsFlagOn(cellBelow, MASK_SWAP_UP);

        final boolean canMutuallySwap =         (cellIsFlagOn(cell, MASK_CAN_SWAP_WITH_ANY) && cellBelowDirection == -1)
                                                || (cellDirection == 1 && cellIsFlagOn(cellBelow, MASK_CAN_SWAP_WITH_ANY));
        final boolean displaceablePresent =     netDirection == 1;

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
     * Removes the cell on the canvas (i.e replaces it with air). if {@link #MASK_INDESTRUCTABLE} is set then the cell cant be removed unless the user requested it to be destroyed
     * @param x cell x position
     * @param y cell y position
     * @param userRequested if true then the cell will be removed even with {@link #MASK_INDESTRUCTABLE}, else the cell can persist if that mask is set
     * @return true if it was removed successfully, otherwise false
     */
    public boolean cellRemove(int x, int y, boolean userRequested) {
        if (canvasIsCoordinatesOutOfBounds(x, y)) return false;
        if (cellIsFlagOn(canvas[x][y], MASK_INDESTRUCTABLE) && !userRequested) return false;
        
        canvas[x][y] = CELL_AIR;
        return true;
    }

    /**
     * Get the direction that the cell wants to swap towards. If cell has {@link #MASK_SELF_CANT_SWAP} then return is 0
     * @param cell the cell to check
     * @return 1 if it wants to go down, -1 if up, 0 if {@link #MASK_SELF_CANT_SWAP} is enabled on the cell
     */
    public int cellSwapDirection(long cell) {
        if (cellIsFlagOn(cell, MASK_SELF_CANT_SWAP)) return 0;
        return cellIsFlagOn(cell, MASK_SWAP_UP) ? -1 : 1;
    }

    /**
     * checks if cell(s) flag(s) are on (enabled). here comes some boolean algebra if you want to know how to use this to your advantage
     *      <br> to check for multiple flags, use bitwise or ({@code |}) to combine the flags
     *      <br> to check if either one or many cells has this flag. use bitwise or ({@code |}) to combine the many cells together
     *      <br> to check if all the cells have this flag, use ({@code &}) to combine the many cells together
     * @param cell the cell or cells to check against
     * @param enabledFlags the flag or flags to check on the cell(s)
     * @return true if the flag(s) are present on the cell(s), false otherwise
     * @see #MASK_CAN_SHUFFLE flag example (MASK_CAN_SHUFFLE)
     */
    public boolean cellIsFlagOn(long cell, long enabledFlags) {
        return (cell & enabledFlags) == enabledFlags;
    }

    /**
     * checks if (x, y) is within the canvas bounds
     * @param x the x axis to check
     * @param y the y axis to check
     * @return true if (x, y) is within the canvas bounds, else otherwise
     */
    public boolean canvasIsCoordinatesOutOfBounds(int x, int y) {
        return (x < 0 || x >= canvasWidth) || (y < 0 || y >= canvasHeight);
    }

    /**
     * Get cell at x, y; if the coordinates are unreachable then {@link #CELL_BARRIER_FLOOR} will be return instead
     * @param x cell x position
     * @param y cell y position
     * @return the cell's value or {@link #CELL_BARRIER_FLOOR} if x, y is not on the canvas
     */
    public long cellAtXYSafe(int x, int y) {
        if (canvasIsCoordinatesOutOfBounds(x, y)) return CELL_BARRIER_FLOOR;
        return canvas[x][y];
    }

    /** renders the canvas to the screen */
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
     * Move the cell.
     *      <br> NOTE: this function will only check if the target exists. it assumes that (x, y) are valid coordinates
     *      <br> NOTE: if the target has not ticked yet it will tick
     * @param x cell at the x position
     * @param y cell at the y position
     * @param targetX the resulting x position to move the cell to
     * @param targetY the resulting y position to move the cell to
     * @return true indicating a success at moving the cell, false otherwise
     * @see #cellMoveRelative(int, int, int, int)
     */
    public boolean cellMoveAbsolute(int x, int y, int targetX, int targetY) {
        if (cellIsFlagOn(canvas[x][y], MASK_SELF_CANT_SWAP)) return false;
        cellMoveAbsoluteInternal(x, y, targetX, targetY);
        
        return true;
    }

    /**
     * Move the cell (with values relX, relY) relative to (x, y).
     *  <br> see {@link #cellMoveAbsolute(int, int, int, int)} for additional information on how the move works
     * @param x cell at the x position
     * @param y cell at the y position
     * @param relX how much to move the cell on the x axis
     * @param relY how much to move the cell on the y axis
     * @return true indicating a success at moving the cell, false otherwise
     * @see #cellMoveAbsolute(int, int, int, int)
     */
    public boolean cellMoveRelative(int x, int y, int relX, int relY) {
        if (cellIsFlagOn(canvas[x][y], MASK_SELF_CANT_SWAP)) return false;
        cellMoveAbsoluteInternal(x, y, x + relX, y + relY);

        return true;
    }

    /**
     * Move the cell.
     *      <br> NOTE: this function will move the cell regardless if it has {@link #MASK_SELF_CANT_SWAP}.
     *      <br>       type handlers should use {@link #cellMoveAbsolute(int, int, int, int)} or {@link #cellMoveRelative(int, int, int, int)}
     *      <br> NOTE: this function assumes both coordinates are valid
     *      <br> NOTE: if the target has not ticked yet it will tick
     * @param x cell at the x position
     * @param y cell at the y position
     * @param targetX the resulting x position to move the cell to
     * @param targetY the resulting y position to move the cell to
     * @see #cellMoveRelativeInternal(int, int, int, int)
     */
    public void cellMoveAbsoluteInternal(int x, int y, int targetX, int targetY) {
        long inital = canvas[x][y];
        long target = canvas[targetX][targetY];
        
        canvas[x][y] = target;
        canvas[targetX][targetY] = inital;

        if (!cellIsFlagOn(target, MASK_TICKED)) cellTick(target, x, y, false);
    }

    /**
     * Move the cell (with values relX, relY) relative to (x, y).
     *  <br> see {@link #cellMoveAbsoluteInternal(int, int, int, int)} for additional information on how the move works
     * @param x cell at the x position
     * @param y cell at the y position
     * @param relX how much to move the cell on the x axis
     * @param relY how much to move the cell on the y axis
     * @see #cellMoveAbsoluteInternal(int, int, int, int)
     */
    public void cellMoveRelativeInternal(int x, int y, int relX, int relY) {
        cellMoveAbsoluteInternal(x, y, x + relX, y + relY);
    }

    /**
     * Encode cell information into a long
     * @param customColor whether or not the color value is custom and not natural (this can be an indication to type handler that they should not modify the color while ticking)
     * @param color cell color value
     * @param type cell type
     * @param metadata cell metadata
     * @param flags cell flags. see {@link #MASK_INDESTRUCTABLE}, {@link #MASK_CAN_SHUFFLE}, etc as an example
     * @return
     */
    public long cellEncodeData(boolean customColor, int color, byte type, int metadata, byte flags) {
        final int rightThreeBytes = 16777215;

        return Integer.toUnsignedLong(color & rightThreeBytes) << SHIFT_COLOR
            | Byte.toUnsignedLong(type) << SHIFT_TYPE
            | Integer.toUnsignedLong(metadata & rightThreeBytes) << SHIFT_METADATA
            | Byte.toUnsignedLong(flags);
    }

    /**
     * Get cell color
     * @param cell the cell t get the color from
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

    public void registerCellTypes() {
        typeRegister("Air", TYPE_AIR, (x, y) -> CELL_AIR, (cell, x, y) -> {});

        typeRegister("Sand", TYPE_SAND,
            (x, y) -> cellEncodeData(false, PALETTE_SAND.get(), TYPE_SAND, 0, (byte)MASK_CAN_SHUFFLE)
        , (cell, x, y) -> {});

        typeRegister("Water", TYPE_WATER, 
            (x, y) -> cellEncodeData(false, PALETTE_WATER.get(), TYPE_WATER, 1, (byte)(MASK_CAN_SHUFFLE))
        , (cell, x, y) -> {
            if (!cellIsFlagOn(cell, MASK_OTHERS_SHUFFLE_ON)) return;
            short direction = (short)cellGetMetadata(cell);
            if (cellShuffle(cell, x, y, direction, 0)) return;

            long removeMetadata = ~MASK_METADATA;
            long addMetadata = cellEncodeCreateMetadata((short)-direction);
            canvas[x][y] = (cell & removeMetadata) | addMetadata;
        });


        typeRegister("Lava", TYPE_LAVA, 
            (x, y) -> cellEncodeData(false, PALETTE_LAVA.get(), TYPE_LAVA, 1, (byte)(MASK_CAN_SHUFFLE))
        , (cell, x, y) -> {
            if (!cellIsFlagOn(cell, MASK_OTHERS_SHUFFLE_ON)) return;
            short direction = (short)cellGetMetadata(cell);
            if (cellShuffle(cell, x, y, direction, 0)) return;

            long removeMetadata = ~MASK_METADATA;
            long addMetadata = cellEncodeCreateMetadata((short)-direction);
            canvas[x][y] = (cell & removeMetadata) | addMetadata;
        });

        typeRegister("Cobblestone", TYPE_COBBLESTONE, 
            (x, y) -> cellEncodeData(false, PALETTE_COBBLESTONE.get(), TYPE_COBBLESTONE, 1, (byte)0)
        , (cell, x, y) -> {});

        typeRegister("Rainbow Sand", TYPE_RAINBOW_SAND, (x, y) -> {
            int hue = ((x + y) + (frameCount / 2)) % 360;
            return cellEncodeData(false, color(hue, 100, 100), TYPE_RAINBOW_SAND, 0, (byte)MASK_CAN_SHUFFLE);
        }, (cell, x, y) -> {
            if (frameCount % 2 != 0) return;

            int cellColor = cellGetColor(cell);
            float chan1 = (hue(cellColor) + 1) % 360;
            float chan2 = saturation(cellColor);
            float chan3 = brightness(cellColor);

            long removeColor = ~MASK_COLOR;
            canvas[x][y] = (cell & removeColor) | cellEncodeCreateColor(color(chan1, chan2, chan3));
        });
    }

    public long cellEncodeCreateColor(int color) {
        final int rightThreeBytes = 16777215;
        return Integer.toUnsignedLong(color & rightThreeBytes) << SHIFT_COLOR;
    }

    public long cellEncodeCreateMetadata(int metadata) {
        final int rightThreeBytes = 16777215;
        return Integer.toUnsignedLong(metadata & rightThreeBytes) << SHIFT_METADATA;
    }

    public Supplier<Integer> colorGeneratePaletteRandomizer(int... color) {
        return () -> color[(int)random(color.length)];
    }

    /**
     * This function will handle cell creation
     * @param x cell's x position
     * @param y cell's y position
     * @returns cell data to place at (x, y)
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