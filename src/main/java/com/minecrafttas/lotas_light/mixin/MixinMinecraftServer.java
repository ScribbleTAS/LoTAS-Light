package com.minecrafttas.lotas_light.mixin;

//# craftmine
//# def
import org.spongepowered.asm.mixin.Final;
//# end
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.minecrafttas.lotas_light.duck.Tickratechanger;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;

@Mixin(MinecraftServer.class)	
public class MixinMinecraftServer {

	//# craftmine
	//# def
	@Shadow
	@Final
	private ServerTickRateManager tickRateManager;
	//# end

	@Shadow
	private long nextTickTimeNanos;

	private long offset = 0;
	private long currentTime = 0;

	//# craftmine
	//# def
	@ModifyVariable(method = "runServer", at = @At(value = "STORE"), index = 1, ordinal = 0)
	public long modifyVariable_preventOverload(long original) {
		if (isTickrateZero())
			return 50L;
		else
			return original;
	}

	@Redirect(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getNanos()J"))
	public long redirectGetMeasuringTimeMsInRun() {
		return getCurrentTime();
	}

	@Redirect(method = "haveTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getNanos()J"))
	public long redirectGetMeasuringTimeMsInShouldKeepTicking() {
		return getCurrentTime();
	}
	//# end

	private boolean isTickrateZero() {
		//# craftmine
//$$		ServerTickRateManager tickRateManager = ((MinecraftServer)(Object)this).theGame().tickRateManager();
		//# end
		return tickRateManager.tickrate() == 0f;
	}

	private boolean isTickAdvance() {
		//# craftmine
//$$		ServerTickRateManager tickRateManager = ((MinecraftServer)(Object)this).theGame().tickRateManager();
		//# end
		return ((Tickratechanger) tickRateManager).isAdvanceTick();
	}

	/**
	 * Returns the time dependant on if the current tickrate is tickrate 0
	 * @return In tickrates>0 the vanilla time - offset or the current time in tickrate 0
	 */
	private long getCurrentTime() {
		if (!isTickrateZero() || isTickAdvance()) {
			currentTime = Util.getNanos(); //Set the current time that will be returned if the player decides to activate tickrate 0
			return Util.getNanos() - offset; //Returns the Current time - offset which was set while tickrate 0 was active
		} else {
			offset = Util.getNanos() - currentTime; //Creating the offset from the measured time and the stopped time
			this.nextTickTimeNanos = currentTime + 50L;

			return currentTime;
		}
	}

	@Inject(method = "stopServer", at = @At("HEAD"))
	public void inject_stopServer(CallbackInfo ci) {
		//# craftmine
//$$		if(((MinecraftServer) (Object) this).theGame() == null)
//$$			return;
//$$		
//$$		Tickratechanger tickrateManager = (Tickratechanger) ((MinecraftServer) (Object) this).theGame().tickRateManager();
		//# def
		Tickratechanger tickrateManager = (Tickratechanger) ((MinecraftServer) (Object) this).tickRateManager();
		//# end
		tickrateManager.disconnect();
	}
}
