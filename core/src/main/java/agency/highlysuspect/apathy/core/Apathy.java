package agency.highlysuspect.apathy.core;

import agency.highlysuspect.apathy.core.config.ConfigSchema;
import agency.highlysuspect.apathy.core.config.CookedConfig;
import agency.highlysuspect.apathy.core.rule.JsonSerializer;
import agency.highlysuspect.apathy.core.rule.Partial;
import agency.highlysuspect.apathy.core.rule.PartialSpecAll;
import agency.highlysuspect.apathy.core.rule.PartialSpecAlways;
import agency.highlysuspect.apathy.core.rule.PartialSpecAny;
import agency.highlysuspect.apathy.core.rule.PartialSpecNot;
import agency.highlysuspect.apathy.core.rule.Rule;
import agency.highlysuspect.apathy.core.rule.RuleSpecAlways;
import agency.highlysuspect.apathy.core.rule.RuleSpecChain;
import agency.highlysuspect.apathy.core.rule.RuleSpecDebug;
import agency.highlysuspect.apathy.core.rule.RuleSpecDifficultyCase;
import agency.highlysuspect.apathy.core.rule.RuleSpecJson;
import agency.highlysuspect.apathy.core.rule.RuleSpecPredicated;
import agency.highlysuspect.apathy.core.rule.Spec;
import agency.highlysuspect.apathy.core.wrapper.Attacker;
import agency.highlysuspect.apathy.core.wrapper.Defender;
import agency.highlysuspect.apathy.core.wrapper.LogFacade;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * how much of Apathy is it possible to port while not touching minecraft at all? Surprisingly, a lot, i guess
 */
public abstract class Apathy {
	public static final String MODID = "apathy";
	
	/** constructor sets this as a side effect */
	public static Apathy instance;
	
	/** Ideally this should be a subdirectory of the platform-specific config directory */
	//TODO: move dump directory somewhere else?
	public final Path configPath;
	public final LogFacade log;
	
	public final NotRegistry<JsonSerializer<? extends Spec<Rule, ?>>> ruleSerializers = new NotRegistry<>();
	public final NotRegistry<JsonSerializer<? extends Spec<Partial, ?>>> partialSerializers = new NotRegistry<>();
	
	public CookedConfig generalCfg;
	public CookedConfig mobCfg;
	public CookedConfig bossCfg;
	
	public Rule configuredRule = RuleSpecAlways.ALWAYS_ALLOW.build();
	public @Nullable Rule jsonRule;
	
	public Apathy(Path configPath, LogFacade log) {
		if(instance == null) {
			instance = this;
		} else {
			IllegalStateException e = new IllegalStateException("Apathy instantiated twice!");
			log.error("Apathy instantiated twice!", e);
			throw e;
		}
		
		this.configPath = configPath;
		this.log = log;
	}
	
	public void init() {
		try {
			Files.createDirectories(configPath);
		} catch (IOException e) {
			throw new RuntimeException("Problem creating Apathy config directory at " + configPath, e);
		}
		
		//rule setup
		addRules();
		
		//config
		ConfigSchema generalConfigSchema = new ConfigSchema();
		addGeneralConfig(generalConfigSchema);
		generalCfg = generalConfigBakery().cook(generalConfigSchema);
		
		ConfigSchema mobsConfigSchema = new ConfigSchema();
		addMobConfig(mobsConfigSchema);
		mobCfg = mobsConfigBakery().cook(mobsConfigSchema);
		
		ConfigSchema bossConfigSchema = new ConfigSchema();
		addBossConfig(bossConfigSchema);
		bossCfg = bossConfigBakery().cook(bossConfigSchema);
		
		//misc
		installConfigFileReloader();
		installCommandRegistrationCallback();
		installPlayerSetManagerTicker();
	}
	
	public boolean allowedToTargetPlayer(Attacker attacker, Defender defender) {
		TriState result = configuredRule.apply(attacker, defender);
		if(result != TriState.DEFAULT) return result.get();
		else return mobCfg.get(CoreMobOptions.fallthrough);
	}
	
	public boolean loadConfig() {
		boolean ok = true;
		
		//TODO: rethink error handling here (keep the old config around)
		ok &= generalCfg.refresh();
		ok &= mobCfg.refresh();
		ok &= bossCfg.refresh();
		
		Rule newConfiguredRule = configuredRule;
		try {
			newConfiguredRule = bakeMobsConfigRule();
		} catch (Exception e) {
			log.error("Problem baking rule: ", e);
			ok = false;
		} finally {
			configuredRule = newConfiguredRule;
		}
		
		Rule newJsonRule = jsonRule;
		try {
			newJsonRule = JsonRule.loadJson(configPath.resolve("mobs.json"));
		} catch (Exception e) {
			log.error("Problem reading mobs.json: ", e);
			ok = false;
		} finally {
			jsonRule = newJsonRule;
		}
		
		return ok;
	}
	
	public void addRules() {
		ruleSerializers.register("always", RuleSpecAlways.Serializer.INSTANCE);
		ruleSerializers.register("chain", RuleSpecChain.Serializer.INSTANCE);
		ruleSerializers.register("debug", RuleSpecDebug.Serializer.INSTANCE);
		ruleSerializers.register("difficulty_case", RuleSpecDifficultyCase.Serializer.INSTANCE);
		ruleSerializers.register("evaluate_json_file", RuleSpecJson.Serializer.INSTANCE);
		ruleSerializers.register("predicated", RuleSpecPredicated.Serializer.INSTANCE);
		
		partialSerializers.register("all", PartialSpecAll.Serializer.INSTANCE);
		partialSerializers.register("always", PartialSpecAlways.Serializer.INSTANCE);
		partialSerializers.register("any", PartialSpecAny.Serializer.INSTANCE);
		partialSerializers.register("not", PartialSpecNot.Serializer.INSTANCE);
		
		//backwards compatibilty/deprecated stuff (allow_if is a subset of predicated's functionality)
		ruleSerializers.register("allow_if", RuleSpecPredicated.LegacyAllowIfSerializer.INSTANCE);
		ruleSerializers.register("deny_if", RuleSpecPredicated.LegacyDenyIfSerializer.INSTANCE);
	}
	
	public void addGeneralConfig(ConfigSchema schema) {
		CoreGenOptions.visit(schema);
	}
	
	public void addMobConfig(ConfigSchema schema) {
		CoreMobOptions.visit(schema);
	}
	
	public void addBossConfig(ConfigSchema schema) {
		CoreBossOptions.visit(schema);
	}
	
	public abstract ConfigSchema.Bakery generalConfigBakery();
	public abstract ConfigSchema.Bakery mobsConfigBakery();
	public abstract ConfigSchema.Bakery bossConfigBakery();
	
	public abstract Rule bakeMobsConfigRule();
	
	public abstract void installConfigFileReloader();
	public abstract void installCommandRegistrationCallback();
	public abstract void installPlayerSetManagerTicker();
	
	//TODO maybe find a better home for these 4 methods?
	public Spec<Rule, ?> readRule(JsonElement jsonElem) {
		if(!(jsonElem instanceof JsonObject)) throw new IllegalArgumentException("Not json object");
		JsonObject json = (JsonObject) jsonElem;
		
		String type = json.getAsJsonPrimitive("type").getAsString();
		JsonSerializer<? extends Spec<Rule, ?>> jsonSerializer = ruleSerializers.get(type);
		
		//TODO way better error message (list the valid options?)
		if(jsonSerializer == null) throw new IllegalArgumentException("No rule serializer with name " + type);
		else return jsonSerializer.read(json);
	}
	
	public <T extends Spec<Rule, T>> JsonObject writeRule(Spec<Rule, T> rule) {
		JsonObject ok = new JsonObject();
		JsonSerializer<T> serializer = rule.getSerializer();
		
		String name = ruleSerializers.getName(serializer);
		if(name == null) throw new IllegalArgumentException("internal error, unregistered rule serializer: " + serializer.getClass().getName());
		
		ok.addProperty("type", name);
		serializer.writeErased(rule, ok);
		return ok;
	}
	
	public Spec<Partial, ?> readPartial(JsonElement jsonElem) {
		if(!(jsonElem instanceof JsonObject)) throw new IllegalArgumentException("Not json object");
		JsonObject json = (JsonObject) jsonElem;
		
		String type = json.getAsJsonPrimitive("type").getAsString();
		JsonSerializer<? extends Spec<Partial, ?>> jsonSerializer = partialSerializers.get(type);
		
		//TODO way better error message (list the valid options?)
		if(jsonSerializer == null) throw new IllegalArgumentException("No partial serializer with name " + type);
		else return jsonSerializer.read(json);
	}
	
	public <T extends Spec<Partial, T>> JsonObject writePartial(Spec<Partial, T> part) {
		JsonObject ok = new JsonObject();
		JsonSerializer<T> serializer = part.getSerializer();
		
		String name = partialSerializers.getName(serializer);
		if(name == null) throw new IllegalArgumentException("internal error, unregistered partial serializer: " + serializer.getClass().getName());
		
		ok.addProperty("type", name);
		serializer.writeErased(part, ok);
		return ok;
	}
}