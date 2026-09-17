package com.minecrafttas.lotas_light.keybind;

//# 26.3
//$$import com.mojang.blaze3d.platform.InputConstants;
//# def
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Minecraft;
//# end


/**
 * A LWJGL style keyboard method
 * @author Scribble
 */
public class Keyboard {
	public static boolean isKeyDown(int keyCode) {
		//# 26.3
//$$		return InputConstants.isKeyDown(keyCode);
		//# 1.21.10
//$$		return GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), keyCode) == GLFW.GLFW_PRESS;
		//# def
		return GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), keyCode) == GLFW.GLFW_PRESS;
		//# end
	}
}
