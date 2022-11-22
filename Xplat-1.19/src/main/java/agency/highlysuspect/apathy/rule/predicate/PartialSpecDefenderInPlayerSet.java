package agency.highlysuspect.apathy.rule.predicate;

import agency.highlysuspect.apathy.PlayerSetManager;
import agency.highlysuspect.apathy.hell.rule.CoolGsonHelper;
import agency.highlysuspect.apathy.hell.rule.PartialSerializer;
import agency.highlysuspect.apathy.rule.CodecUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public record PartialSpecDefenderInPlayerSet(Set<String> playerSetNames) implements PartialSpec<PartialSpecDefenderInPlayerSet> {
	@Override
	public PartialSpec<?> optimize() {
		if(playerSetNames.isEmpty()) return PartialSpecAlways.FALSE;
		else return this;
	}
	
	@Override
	public Partial build() {
		return (attacker, defender) -> {
			MinecraftServer server = defender.getServer();
			assert server != null;
			
			PlayerSetManager setManager = PlayerSetManager.getFor(server);
			for(String playerSetName : playerSetNames) {
				if(setManager.playerInSet(defender, playerSetName)) return true;
			}
			return false;
		};
	}
	
	@Override
	public PartialSerializer<PartialSpecDefenderInPlayerSet> getSerializer() {
		return Serializer.INSTANCE;
	}
	
	public static class Serializer implements PartialSerializer<PartialSpecDefenderInPlayerSet> {
		public static final Serializer INSTANCE = new Serializer();
		
		@Override
		public JsonObject write(PartialSpecDefenderInPlayerSet part, JsonObject json) {
			json.add("player_sets", part.playerSetNames.stream()
				.map(JsonPrimitive::new)
				.collect(CoolGsonHelper.toJsonArray()));
			return json;
		}
		
		@Override
		public PartialSpecDefenderInPlayerSet read(JsonObject json) {
			return new PartialSpecDefenderInPlayerSet(StreamSupport.stream(json.getAsJsonArray("player_sets").spliterator(), false)
				.map(JsonElement::getAsString)
				.collect(Collectors.toSet()));
		}
	}
	
	/// CODEC HELL ///
	
	@Deprecated(forRemoval = true)
	public static final Codec<PartialSpecDefenderInPlayerSet> CODEC = RecordCodecBuilder.create(i -> i.group(
		CodecUtil.setOf(Codec.STRING).fieldOf("player_sets").forGetter(x -> x.playerSetNames)
	).apply(i, PartialSpecDefenderInPlayerSet::new));
	@Deprecated(forRemoval = true)
	@Override
	public Codec<? extends PartialSpec<?>> codec() {
		return CODEC;
	}
}