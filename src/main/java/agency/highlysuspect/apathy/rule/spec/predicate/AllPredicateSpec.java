package agency.highlysuspect.apathy.rule.spec.predicate;

import agency.highlysuspect.apathy.rule.CodecUtil;
import agency.highlysuspect.apathy.rule.Partial;
import agency.highlysuspect.apathy.rule.spec.Specs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class AllPredicateSpec implements PredicateSpec {
	public static final Codec<AllPredicateSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
		CodecUtil.setOf(Specs.PREDICATE_SPEC_CODEC).fieldOf("predicates").forGetter(x -> x.others)
	).apply(i, AllPredicateSpec::new));
	private final Set<PredicateSpec> others;
	
	public AllPredicateSpec(Set<PredicateSpec> others) {this.others = others;}
	
	@Override
	public PredicateSpec optimize() {
		Set<PredicateSpec> loweredSpecs = others.stream().map(PredicateSpec::optimize).collect(Collectors.toSet());
		
		//If an always-false predicate is here, surely this predicate will never match.
		if(loweredSpecs.stream().anyMatch(pred -> pred == AlwaysPredicateSpec.FALSE)) return AlwaysPredicateSpec.FALSE;
		
		//If an always-true predicate is here, it can be ignored
		loweredSpecs.removeIf(pred -> pred == AlwaysPredicateSpec.TRUE);
		
		//If there are no specs left, ?? always fail I guess??
		if(loweredSpecs.size() == 0) return AlwaysPredicateSpec.FALSE;
		
		//If there is one spec left, we don't need the wrapping
		if(loweredSpecs.size() == 1) return loweredSpecs.iterator().next();
		
		return new AllPredicateSpec(loweredSpecs);
	}
	
	@Override
	public Partial build() {
		Partial[] arrayParts = others.stream().map(PredicateSpec::build).toArray(Partial[]::new);
		return (attacker, defender) -> {
			for(Partial p : arrayParts) {
				if(!p.test(attacker, defender)) return false;
			}
			return true;
		};
	}
	
	@Override
	public Codec<? extends PredicateSpec> codec() {
		return CODEC;
	}
	
	public Set<PredicateSpec> others() {return others;}
	
	@Override
	public boolean equals(Object obj) {
		if(obj == this) return true;
		if(obj == null || obj.getClass() != this.getClass()) return false;
		var that = (AllPredicateSpec) obj;
		return Objects.equals(this.others, that.others);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(others);
	}
	
	@Override
	public String toString() {
		return "AllPredicateSpec[" +
			"others=" + others + ']';
	}
	
}
