# Sandbox - A Sand Simulation

![Program Running with the text "The Sandbox"](images/program.png)

A falling sand cellular automata game written in Java using the Processing library. Put some "sand" onto the canvas and watch it fall or do something.

## Usage

Use the mouse and paint the canvas with the different types of cells provided in the cell selection panel. (press <kbd>Shift</kbd> to open/close the panel)

Keybinds:

- <kbd>Space</kbd>: Pause or resume the simulation
- <kbd>s</kbd>: Advance the simlation by one frame
- <kbd>Shift</kbd>: Opens up the cell selection panel
- <kbd>1</kbd>: Fill tool (Paints the canvas with the selected cell, does not override existing cells)
- <kbd>2</kbd>: Brush tool (Paints the canvas with the selected cell, overrides existing cell)
- <kbd>3</kbd>: Eraser tool
- <kbd>F3</kbd>: Display debug renderers
- <kbd>Up</kbd>, <kbd>Down</kbd>, <kbd>Scroll</kbd>: Increase/decrease brush size

While the cell selection panel is open you can press <kbd>Shift</kbd> or <kbd>Esc</kbd> to close it

## Features

- [x] Simulation Controls (pause/play/step)
- [x] Brushes
  - [x] Paint
  - [x] Erase
- [x] Extendable Cell System
- [x] Debug Render
- [ ] Cell Inspection Tool
- [ ] Help Menu
- [ ] Resizeable Canvas
- [ ] Custom Properties in the Cell Selection Panel

## Known Limitations

- The program's main loop is intended to be ran on a single thread, it does not support running in parallel to speed up performance
- Canvas size is limited from 0 to the maximum value of an integer (2,147,483,647)
  - Even if you do set it to an absurdly high number, the program would either hang or run at a very low FPS
  - In addition the memory consumption would also probably skyrocket

## Attribution

```txt
Noto Emoji Font
Source: https://fonts.google.com/noto/specimen/Noto+Emoji
License: SIL Open Font License, Version 1.1
```
