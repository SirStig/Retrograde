package com.ironcoffee.retrograde.gui;

/**
 * Mouse-button and key numbers, shared by every screen and overlay in this
 * package that reads a raw button or key code.
 *
 * 26.3 swapped the input backend from GLFW to SDL, which numbers both the
 * mouse buttons and the non-printable keys differently - vanilla's own
 * AbstractWidget treats button 1 as the left button on 26.3 and button 0
 * on everything before it, which is how this was pinned down. Printable
 * keys are ASCII either way, so '-' and '=' are shared.
 *
 * This used to be a private constant block duplicated inside
 * ChunkMapScreen. It moved out so that overlay code (ManipulationOverlay,
 * SettingsOverlay, and anything else added later that hit-tests a click or
 * intercepts a scroll) reads the same numbers instead of risking a second,
 * silently-wrong copy - the class of bug this file exists to make
 * impossible is "works everywhere except 26.3".
 */
final class InputCodes {
	private InputCodes() {}

	//? if >=26.3 {
	/*static final int BUTTON_LEFT = 1;
	static final int BUTTON_MIDDLE = 2;
	static final int BUTTON_RIGHT = 3;
	static final int KEY_RIGHT = 1073741903;
	static final int KEY_LEFT = 1073741904;
	static final int KEY_DOWN = 1073741905;
	static final int KEY_UP = 1073741906;
	static final int KEY_KP_SUBTRACT = 1073741910;
	static final int KEY_KP_ADD = 1073741911;
	static final int KEY_W = 119;
	static final int KEY_A = 97;
	static final int KEY_S = 115;
	static final int KEY_D = 100;
	*///?} else {
	static final int BUTTON_LEFT = 0;
	static final int BUTTON_RIGHT = 1;
	static final int BUTTON_MIDDLE = 2;
	static final int KEY_RIGHT = 262;
	static final int KEY_LEFT = 263;
	static final int KEY_DOWN = 264;
	static final int KEY_UP = 265;
	static final int KEY_KP_SUBTRACT = 333;
	static final int KEY_KP_ADD = 334;
	static final int KEY_W = 87;
	static final int KEY_A = 65;
	static final int KEY_S = 83;
	static final int KEY_D = 68;
	//?}
	static final int KEY_MINUS = 45;
	static final int KEY_EQUAL = 61;
}
