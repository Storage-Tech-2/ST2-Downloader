package com.andrews.gui.widget;

import org.lwjgl.glfw.GLFW;

public class KeyboardHelper {
    private final long windowHandle;
    
    public KeyboardHelper(long windowHandle) {
        this.windowHandle = windowHandle;
    }
    
    public boolean isKeyPressed(int key) {
        return GLFW.glfwGetKey(windowHandle, key) == GLFW.GLFW_PRESS;
    }
    
    public boolean isCtrlHeld() {
        return isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyPressed(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
    
    public boolean isShiftHeld() {
        return isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT) || isKeyPressed(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
    
    public boolean isAltHeld() {
        return isKeyPressed(GLFW.GLFW_KEY_LEFT_ALT) || isKeyPressed(GLFW.GLFW_KEY_RIGHT_ALT);
    }
    
    public boolean isMouseButtonPressed(int button) {
        return GLFW.glfwGetMouseButton(windowHandle, button) == GLFW.GLFW_PRESS;
    }
    
    public boolean isLeftMousePressed() {
        return isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    
    public boolean isRightMousePressed() {
        return isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }
}
