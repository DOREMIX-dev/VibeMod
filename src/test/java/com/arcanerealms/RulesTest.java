package com.arcanerealms;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RulesTest {
    @Test void reachable(){Set<String> known=new HashSet<>();int points=5;for(String e:Spell.ELEMENTS)if(!e.equals("combo")){Spell starter=Spell.ALL.values().stream().filter(s->s.element().equals(e)&&s.prerequisites().isEmpty()).findFirst().orElseThrow();known.add(starter.id());points--;}assertEquals(0,points);for(int round=0;round<50;round++){points++;for(Spell s:Spell.ALL.values())if(!known.contains(s.id())&&known.containsAll(s.prerequisites())&&points>0){known.add(s.id());points--;}}assertEquals(Spell.ALL.keySet(),known);}
    @Test void combinations(){Map<String,Set<String>> expected=Map.of("steam",Set.of("fire","water"),"lava",Set.of("fire","nature"),"thunderstorm",Set.of("ice","lightning"),"living_lightning",Set.of("nature","lightning"),"thermal_shock",Set.of("fire","ice"),"electric_wave",Set.of("water","lightning"));expected.forEach((id,e)->{Set<String> actual=new HashSet<>();Spell.ALL.get(id).prerequisites().forEach(p->actual.add(Spell.ALL.get(p).element()));assertEquals(e,actual);});}
    @Test void rating(){assertEquals(12,Spell.ratingDelta(1000,1000));assertTrue(Spell.ratingDelta(800,1400)>Spell.ratingDelta(1400,800));for(int w=0;w<3000;w+=100)for(int l=0;l<3000;l+=100){int d=Spell.ratingDelta(w,l);assertTrue(d>=1&&d<=24);}}
    @Test void bounded(){for(Spell s:Spell.ALL.values()){assertTrue(s.mana()>0&&s.mana()<=100);assertTrue(s.cooldown()>0&&s.cooldown()<=60);assertTrue(s.tier()<=50);}}
}
