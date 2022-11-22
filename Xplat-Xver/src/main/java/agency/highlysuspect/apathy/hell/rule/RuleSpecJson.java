package agency.highlysuspect.apathy.hell.rule;

import agency.highlysuspect.apathy.hell.ApathyHell;
import agency.highlysuspect.apathy.hell.TriState;
import com.google.gson.JsonObject;

public final record RuleSpecJson() implements RuleSpec<RuleSpecJson> {
	public static final RuleSpecJson INSTANCE = new RuleSpecJson();
	
	@Override
	public Rule build() {
		return (attacker, defender) -> ApathyHell.instance.jsonRule == null ? TriState.DEFAULT : ApathyHell.instance.jsonRule.apply(attacker, defender);
	}
	
	@Override
	public RuleSerializer<RuleSpecJson> getSerializer() {
		return Serializer.INSTANCE;
	}
	
	public static class Serializer implements RuleSerializer<RuleSpecJson> {
		public static final Serializer INSTANCE = new Serializer();
		
		@Override
		public void write(RuleSpecJson ruleSpecJson, JsonObject json) {
			//Nothing to write
		}
		
		@Override
		public RuleSpecJson read(JsonObject json) {
			return RuleSpecJson.INSTANCE;
		}
	}
}