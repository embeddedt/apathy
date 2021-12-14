package agency.highlysuspect.apathy.mixin.dragon.phase;

import agency.highlysuspect.apathy.Init;
import net.minecraft.entity.boss.dragon.phase.PhaseManager;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PhaseManager.class)
public class PhaseManagerMixin {
	@ModifyVariable(method = "setPhase", at = @At("HEAD"))
	private PhaseType<?> whenSettingPhase(PhaseType<?> type) {
		if(!Init.bossConfig.dragonFlies && (type == PhaseType.STRAFE_PLAYER || type == PhaseType.CHARGING_PLAYER)) return PhaseType.LANDING_APPROACH;
		else if(!Init.bossConfig.dragonSits && (type == PhaseType.SITTING_FLAMING || type == PhaseType.SITTING_ATTACKING)) return PhaseType.SITTING_SCANNING;
		else return type;
	}
}