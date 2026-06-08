import java.util.HashMap;

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
     * Red, Green, Blue: Particle Color (mask is -1099511627776) (this channel can also support hsb) (we are cutting the first bit for a flag)
     * Type: Particle Type (for special behaviour if applicable) (mask is 1095216660480)
     *      0 represents air
     *      -127 represents a barrier floor (basically cells outside the canvas range to prevent the cell from escaping)
     * Metadata (just borrowed the rest of the bits from the gravity byte, dont mind me)
     *      xx000000 00000000 00000000
     *      storing data about the cell i guess. could be useful for keeping track of stuff
     *      This value is represented as a byte
     * State and Rules (aka. flags) (the state of the cell and some rules that all cells probably have in common)
     *      See below for misc flags. start at {@link #MASK_TICKED}
     */

    public final long MASK_COLOR =             Long.parseUnsignedLong("11111111 11111111 11111111 00000000 00000000 00000000 00000000 00000000".replace(" ", ""), 2);
    public final long MASK_TYPE =              Long.parseUnsignedLong("00000000 00000000 00000000 11111111 00000000 00000000 00000000 00000000".replace(" ", ""), 2);
    public final long MASK_METADATA =          Long.parseUnsignedLong("00000000 00000000 00000000 00000000 11111111 11111111 11111111 00000000".replace(" ", ""), 2);

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

    // TOOL_BRUSH_FILL = 0;
    // TOOL_BRUSH_FILL_OVERRIDE = 1;
    // TOOL_BRUSH_ERASE = 2;
    // TOOL_INSPECT = 3;
    // TOOL_COPY = 4;
    // TOOL_CUT = 5;
    // TOOL_PASTE = 6;
    public int selectedTool = 0;
    public String[] toolSymbols = {"🪣", "🖌️", "🧼", "🔍", "📄", "✂️", "📋"};

    public boolean runSimulation = true;
    public boolean canInteractStatusBar = true;
    public boolean canInteractCanvas = true;

    public int brushRadius = 2;
    public long[][] canvas;  // x then y
    public int canvasX = 25;
    public int canvasY = 25;
    public int canvasWidth = 100 * 2;
    public int canvasHeight = 60 * 2;
    public int sandSize = 5;

    public HashMap<Byte, TypeHandler> typeHandler = new HashMap<>();
    public HashMap<String, Byte> typeNames = new HashMap<>();

    public int statusbarSeperatorWidth = 5;
    public int statusbarHeight = 40;

    public PFont fontEmoji;
    public PFont fontDefault;

    // TODO: subjected for removal (temorary testing)
    public int count = 0;

    public final long CELL_AIR = cellEncodeData(true, color(0), (byte)0, 0, (byte)(MASKC_FLOATING_DISPLACEABLE));
    public final long CELL_BARRIER_FLOOR = cellEncodeData(true, color(0), (byte)-128, 0, (byte)(MASK_INDESTRUCTABLE | MASKC_NATURALLY_IMMOVABLE | MASK_TICKED));

    public static void main(String[] args) {
        PApplet.main("Sketch");
    }

    @Override
    public void settings() {
        size(canvasWidth * sandSize + 50, canvasHeight * sandSize + (sandSize + 40) + 50);
        canvas = new long[canvasWidth][canvasHeight];

        for (int x = 0; x < canvasWidth; x++) {
            for (int y = 0; y < canvasHeight; y++) {
                canvas[x][y] = CELL_AIR;
            }
        }
    }

    @Override
    public void setup() {
        fontEmoji = createFont("font/NotoEmoji.ttf", 20);
        fontDefault = createFont("SansSerif", 12);
    }

    @Override
    public void draw() {
        background(255);

        cellRenderCanvas(canvasX, canvasY);
        if (runSimulation) cellsTickAll();

        renderStatusBar();
        if (selectedTool <= 2) renderToolBrushOverlay(canvasX, canvasY);

        // fps
        textFont(fontDefault);
        fill(255);
        textAlign(LEFT, TOP);
        text(String.format("fps %d", (int)frameRate), 0, 0);
    }

    /**
     * return the canvas coordinate for the related mouse position
     * @param canvasX where the canvas has been rendered on the x axis
     * @param canvasY where the canvas has been rendered on the y axis
     * @param event mouse coordinates if mouseX and mouseY is unreliable, if you dont have this just pass in null
     * @return updated x and y coordinates. 32bits on the left is the x coordinates as int, and 32bits on the right is y coordinates as int
     *         <br> Use a bitwise mask to access the value {@code x = (int)(res >>> 32);} {@code y = (int)(res & Integer.MAX_VALUE)}
     */
    public long mousePosToCanvas(int canvasX, int canvasY, MouseEvent event) {
        int mouseX = this.mouseX;
        int mouseY = this.mouseY;

        if (event != null) {
            mouseX = event.getX();
            mouseY = event.getY();
        }

        int x = (mouseX - canvasX) / sandSize;
        int y = (mouseY - canvasY) / sandSize;

        return ((long)x << 32) | y;
    }

    @Override
    public void mouseDragged(MouseEvent event) {
        long pos = mousePosToCanvas(canvasX, canvasY, event);
        int mouseX = (int)(pos >>> 32);
        int mouseY = (int)(pos & Integer.MAX_VALUE);

        if (
            (mouseX < 0 || mouseX > canvasWidth - 1)
            || (mouseY < 0 || mouseY > canvasHeight - 1)
        ) return;
        
        // TODO: get rid of hue and replace with something else
        int hue = count++ % 360;
        
        int xRightBounds = Math.clamp(mouseX + brushRadius, 0, canvasWidth - 1) ;
        int yDownBounds = Math.clamp(mouseY + brushRadius, 0, canvasHeight - 1) ;

        long cell = cellEncodeData(
            true, color(hue, 255, 255),
            (byte)(keyPressed && keyCode == SHIFT ? 2 : 1),
            0,
            (byte)((MASK_CAN_SHUFFLE)
            | (count % 2 == 0 ? MASK_SWAP_UP : 0)
            | (keyPressed && keyCode == CONTROL ? MASKC_NATURALLY_IMMOVABLE : 0)
        ));

        // brush handler
        for (int x = Math.clamp(mouseX - brushRadius, 0, canvasWidth - 1); x <= xRightBounds; x++) {
            for (int y = Math.clamp(mouseY - brushRadius, 0, canvasHeight - 1); y <= yDownBounds; y++) {
                if (selectedTool == 0 && cellGetType(canvas[x][y]) == 0) {  // fill air space
                    canvas[x][y] = cell;
                } else if (selectedTool == 1) {  // brush, replace cells if nessesary
                    canvas[x][y] = cell;
                } else if (selectedTool == 2) {  // eraser
                    canvas[x][y] = CELL_AIR;
                }
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent event) {
        if (canInteractStatusBar) handleStatusBarClick(event.getX(), event.getY());
    }

    public void handleStatusBarClick(int x, int y) {
        if (x < 0 || x > width || y > height || y < height - 40) return;

        if (x <= 40) { // pause/play
            runSimulation = !runSimulation;
        } else if (x <= 80) { // step
            if (!runSimulation) cellsTickAll();
        }

        // tool selection
        if (x >= 80 + 5 && x <= ((80 + 5) + (40 * toolSymbols.length))) {
            selectedTool = Math.floorDiv((x - 80 - 5), 40);
        }

        if (x > width - 40) { // help button
            System.out.println("help pressed");
        }
    }

    public boolean canvasMouseOutside(int canvasX, int canvasY, MouseEvent event) {
        int x = mouseX;
        int y = mouseY;

        if (event != null) {
            x = event.getX();
            y = event.getY();
        }

        x -= canvasX;
        y -= canvasY;
        
        return (x <= 0 || x >= (canvasWidth * sandSize))
            || (y <= 0 || y >= (canvasHeight * sandSize));
    }

    public void renderToolBrushOverlay(int canvasX, int canvasY) {
        if (canvasMouseOutside(canvasX, canvasY, null)) return;
        long pos = mousePosToCanvas(canvasX, canvasY, null);
        int x = (int)(pos >>> 32);
        int y = (int)(pos & Integer.MAX_VALUE);

        // to make sure brush doesnt clip off the canvas
        int xOffset = 0;
        int yOffset = 0;

        if (x <= brushRadius) {
            xOffset = brushRadius - x;
        } else if (x >= (canvasWidth - brushRadius)) {
            xOffset = brushRadius - (canvasWidth - x - 1);
        }

        if (y <= brushRadius) {
            yOffset = brushRadius - y;
        } else if (y >= (canvasHeight - brushRadius)) {
            yOffset = brushRadius - (canvasHeight - y - 1);
        }

        int brushWidth = (brushRadius * 2 + 1 - xOffset) * sandSize;
        int brushHeight = (brushRadius * 2 + 1 - yOffset) * sandSize;

        fill(255, 200);
        rect(
            Math.clamp(canvasX + (x - brushRadius) * sandSize, canvasX, canvasX + canvasWidth * sandSize),
            Math.clamp(canvasY + (y - brushRadius) * sandSize, canvasY, canvasY + canvasHeight * sandSize),
            brushWidth,
            brushHeight
        );
    }

    // note: status bar is 40 px tall
    // note: seperator size is 5
    public void renderStatusBar() {
        push();
        colorMode(RGB, 255, 255, 255);
        translate(0, height - statusbarHeight);

        // top seperator
        fill(200);
        rect(0, -statusbarSeperatorWidth, width, statusbarSeperatorWidth);

        // body
        fill(0);
        rect(0, 0, width, 40);
        
        textFont(fontEmoji);
        textAlign(CENTER, CENTER);
        if (runSimulation) {  // play
            fill(82, 183, 136);
            square(0, 0, statusbarHeight);

            fill(255);
            text("▶", statusbarHeight / 2f, statusbarHeight / 2f);
        } else {  // pause
            fill(204, 2, 2);
            square(0, 0, statusbarHeight);
            
            fill(255);
            text("⏸", statusbarHeight / 2f, statusbarHeight / 2f);
        }

        // step
        translate(statusbarHeight, 0);
        textAlign(CENTER, CENTER);
        textFont(fontDefault, 50);
        text(">", statusbarHeight / 2f, statusbarHeight / 2f);

        // seperator vertical
        fill(200);
        rect(statusbarHeight, 0, statusbarSeperatorWidth, statusbarHeight);
        
        // selected tool
        translate(statusbarSeperatorWidth + statusbarHeight, 0);
        fill(255);
        square(selectedTool * statusbarHeight, 0, statusbarHeight);

        // tool render
        textAlign(CENTER, CENTER);
        textFont(fontEmoji, 25);
        for (int i = 0; i < toolSymbols.length; i++) {
            fill(i == selectedTool ? 0 : 255);
            text(toolSymbols[i], (statusbarHeight / 2f) + (statusbarHeight * i), (statusbarHeight / 2f));
        }

        // seperator vertical
        fill(200);
        translate((toolSymbols.length - 1) * statusbarHeight, 0);
        rect(statusbarHeight, 0, statusbarSeperatorWidth, statusbarHeight);

        pop();
        push();
        translate(width - statusbarHeight, height - statusbarHeight);

        // help button
        fill(255);
        textAlign(CENTER, CENTER);
        textFont(fontDefault, 25);
        text("❓", statusbarHeight / 2f, statusbarHeight / 2f);

        // seperator vertical
        fill(200);
        rect(-statusbarSeperatorWidth, 0, statusbarSeperatorWidth, statusbarHeight);

        // TODO: some random text in the status bar that could be useful

        pop();
    }

    /** tick all the cells in the canvas */
    public void cellsTickAll() {
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
     * @param canMove allow the cell to move in this tick (special behaviours can bypass this argument)
     */
    public void cellTick(long cell, int x, int y, boolean canMove) {
        canvas[x][y] |= MASK_TICKED;

        if (canMove) {
            if (cellCommonsApplyGravity(cell, x, y)) {
                y++;
                cell = canvas[x][y];
            } else if (cellIsFlagOn(cell, MASK_CAN_SHUFFLE)) {
                long updatedPos = cellApplyShuffling(cell, x, y);
                x = (int)(updatedPos >>> 32);
                y = (int)(updatedPos & Integer.MAX_VALUE);
                cell = canvas[x][y];
            }
        }

        if (typeHandler.get(cellGetType(cell)) instanceof TypeHandler handler) {
            handler.run(cell, x, y, true, false);
        }
    }

    /**
     * Register the type with the cell system
     * @param name common name to associate with this type (no it doesn't have to be a fancy name in the code base. eg. {@code namespace:name}. this value will be presented to the end user)
     * @param uniqueID unique id for the type
     * @param handler the type handler. see {@link TypeHandler}
     * @throws Exception will throw hands if name is already taken or uniqueID has already been reserved 
     * @see TypeHandler
     */
    public void registerType(String name, byte uniqueID, TypeHandler handler) throws Exception {
        if (typeNames.containsKey(name)) throw new Exception(String.format("type of name \"%s\" exists", name));
        if (typeHandler.containsKey(uniqueID)) throw new Exception(String.format("id &d is already reserved, cannot assign \"%s\"", name));

        typeNames.put(name, uniqueID);
        typeHandler.put(uniqueID, handler);
    }

    /**
     * Apply cell shuffling
     * @param cell cell data
     * @param x cell x on canvas
     * @param y cell y on canvas
     * @param direction where the cell is moving towards according to gravity rule (see above for sector)
     * @return updated x and y coordinates. 32bits on the left is the x coordinates as int, and 32bits on the right is y coordinates as int
     *         <br> Use a bitwise mask to access the value {@code x = (int)(res >>> 32);} {@code y = (int)(res & Integer.MAX_VALUE)}
     */
    public long cellApplyShuffling(long cell, int x, int y) {
        // when shuffling:
        //      tick the cell ahead if it hasn't already ticked
        //      you must shuffle on a surface that permits you to do so
        //      if you are unable to shuffle. you are prob on a still surface. permit others to shuffle on you by enabling the MASK_OTHERS_SHUFFLE_ON flag
        int direction = cellSwapDirection(cell);
        if (direction == 0 || cellIsFlagOn(cell, MASK_SELF_CANT_SWAP)) return ((long)x << 32) | y;
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

        return ((long)x << 32) | y;
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

        return cellIsFlagOn(cellAtXYSafe(x + relX, targetY), MASK_CAN_SWAP_WITH_ANY)
            && cellIsFlagOn(cellAdj, MASK_CAN_SWAP_WITH_ANY)
            && cellSwapDirection(cellAdj) != direction
            && cellMoveRelativeInternal(x, y, relX, relY);
    }

    /**
     * Applies gravity to the affected cell.
     * @param cell cell value at (x, y)
     * @param x cell x coords in the canvas
     * @param y cell y coords in the canvas
     * @return true when the cell has moved
     */
    public boolean cellCommonsApplyGravity(long cell, int x, int y) {
        int cellDirection = cellSwapDirection(cell);
        long cellBelow = cellAtXYSafe(x, y + 1);
        int cellBelowDirection = cellSwapDirection(cellBelow);
        int netDirection = cellDirection + cellBelowDirection;

        // both cells must be able to swap, both cannot move in the same direction, the cell below cannot be ticked
        if (cellIsFlagOn(cell & cellBelow, MASK_SELF_CANT_SWAP)) return false;
        if (Math.abs(netDirection) == 2) return false;
        if (cellIsFlagOn(cellBelow, MASK_TICKED)) return false;
        
        boolean sameType = cellGetType(cell) == cellGetType(cellBelow);
        if (
            sameType                                                                    // both must be the same type, must be moving towards together by checking: 
            && Math.abs(netDirection) == 0 && cellIsFlagOn(cellBelow, MASK_SWAP_UP)     // if the one below is going up (if this is true then  current cell is down since netDirection would equal 0)
            && !cellIsFlagOn(cell | cellBelow, MASK_SELF_CANT_SWAP)                     // also both must be swappable
        ) {
            return cellMoveRelativeInternal(x, y, 0, 1);
        } else if (
            Math.abs(netDirection) == 1                                                         // cell is not of same type so that means there is a displaceable and a swapee
            && (                                                                                // we can preforme a swap if the current cell is a swappable and the cell below wants to swap up
                (cellIsFlagOn(cell, MASK_CAN_SWAP_WITH_ANY) && cellBelowDirection == -1)        // or if the current cell wants to swap down and the cell below is a swappable
                || (cellDirection == 1 && cellIsFlagOn(cellBelow, MASK_CAN_SWAP_WITH_ANY))
            )
        ) {
            return cellMoveRelativeInternal(x, y, 0, 1);
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
        if (x < 0 || x >= canvasWidth || isYOutOfBounds(y)) return false;
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
     * checks if y is within the canvas bounds
     * @param y the y axis to check
     * @return true if y is within the canvas bounds, else otherwise
     */
    public boolean isYOutOfBounds(int y) {
        return y < 0 || y >= canvasHeight;
    }

    /**
     * Get cell at x, y; if the coordinates are unreachable then {@link #CELL_BARRIER_FLOOR} will be return instead
     * @param x cell x position
     * @param y cell y position
     * @return the cell's value or {@link #CELL_BARRIER_FLOOR} if x, y is not on the canvas
     */
    public long cellAtXYSafe(int x, int y) {
        if (isYOutOfBounds(y)) return CELL_BARRIER_FLOOR;
        if (x < 0 || x >= canvasWidth) return CELL_BARRIER_FLOOR;
        return canvas[x][y];
    }

    // TODO: maybe add some parameters to place the canvas anywhere
    /** renders the canvas to the screen */
    public void cellRenderCanvas(int canvasX, int canvasY) {
        noStroke();
        background(128);
        colorMode(HSB, 255, 255, 255);

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
        return cellMoveAbsoluteInternal(x, y, targetX, targetY);
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
        return cellMoveAbsoluteInternal(x, y, x + relX, y + relY);
    }

    /**
     * Move the cell.
     *      <br> NOTE: this function will move the cell regardless if it has {@link #MASK_SELF_CANT_SWAP}.
     *      <br>       type handlers should use {@link #cellMoveAbsolute(int, int, int, int)} or {@link #cellMoveRelative(int, int, int, int)}
     *      <br> NOTE: this function will only check if the target exists. it assumes that (x, y) are valid coordinates
     *      <br> NOTE: if the target has not ticked yet it will tick
     * @param x cell at the x position
     * @param y cell at the y position
     * @param targetX the resulting x position to move the cell to
     * @param targetY the resulting y position to move the cell to
     * @return true indicating a success at moving the cell, false otherwise
     * @see #cellMoveRelativeInternal(int, int, int, int)
     */
    public boolean cellMoveAbsoluteInternal(int x, int y, int targetX, int targetY) {
        long inital = canvas[x][y];
        long target = cellAtXYSafe(targetX, targetY);
        
        canvas[x][y] = target;
        canvas[targetX][targetY] = inital;

        if (!cellIsFlagOn(target, MASK_TICKED)) cellTick(target, x, y, false);

        return true;
    }

    /**
     * Move the cell (with values relX, relY) relative to (x, y).
     *  <br> see {@link #cellMoveAbsoluteInternal(int, int, int, int)} for additional information on how the move works
     * @param x cell at the x position
     * @param y cell at the y position
     * @param relX how much to move the cell on the x axis
     * @param relY how much to move the cell on the y axis
     * @return true indicating a success at moving the cell, false otherwise
     * @see #cellMoveAbsoluteInternal(int, int, int, int)
     */
    public boolean cellMoveRelativeInternal(int x, int y, int relX, int relY) {
        return cellMoveAbsoluteInternal(x, y, x + relX, y + relY);
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
        // 16777215 represnts the first 3 bytes of the integer on the right.
        // in this case for color we do not care about the byte on the left because the alpha channel is meaningless to us
        // for metadata we do the same

        return Integer.toUnsignedLong(color & 16777215) << 40
            | Byte.toUnsignedLong(type) << 32
            | Integer.toUnsignedLong(metadata & 16777215) << 8
            | Byte.toUnsignedLong(flags);
    }

    /**
     * Get cell color
     * @param cell the cell t get the color from
     * @return Proccessing compatible color value
     * @see #color(int, int, int)
     */
    public int cellGetColor(long cell) {
        // -1099511627776 represents the rgb section of the cell value
        // we must add back the alpha section so that processing can recognise the color (append 8 on bits to the left)
        //      this value is -16777216

        return -16777216 | (int)((cell & MASK_COLOR) >>> 40);
    }

    /**
     * Get cell type
     * @param cell the cell to fetch type from
     * @return the cell's type
     */
    public byte cellGetType(long cell) {
        return (byte)((cell & MASK_TYPE) >>> 32);
    }

    /**
     * Get cell metadata
     * @param cell the cell to fetch the metadata from
     * @return cell metadata
     */
    public int getMetadata(long cell) {
        return (int)((cell & MASK_METADATA) >>> 24);
    }

    /**
     * Cells have a type assigned to them, this function allows the assignment of special behaviours to these types
     * @param cell cell data, only use this if executeTick is true
     * @param x cell's x position
     * @param y cell's y position
     * @param executeTick this cell is ticking at (x, y), do something with it
     * @param executeCreate the cell wants to be created at (x, y)
     */
    @FunctionalInterface
    public interface TypeHandler {
        void run(long cell, int x, int y, boolean executeTick, boolean executeCreate);
    }
}

// cell debugger
        // if (keyPressed && !waitingRelease) {
        //     waitingRelease = true;
        //     String cell = Long.toBinaryString(cellAtXYSafe(this.mouseX / sandSize, this.mouseY / sandSize));
        //     cell = " ".repeat(64 - cell.length()) + cell;
            
        //     for (int i = 0; i < 64; i += 8) {
        //         System.out.print(cell.substring(i, i + 8) + " ");
        //     }
        //     System.out.println();
        // } else if (!keyPressed && waitingRelease) {
        //     waitingRelease = false;
        // }