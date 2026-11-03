package com.arcanerealms;
import java.util.*;
public record Spell(String id,String name,String element,int tier,int mana,int cooldown,List<String> prerequisites) {
    public static final List<String> ELEMENTS=List.of("fire","water","ice","lightning","nature","combo");
    public static final Map<String,Spell> ALL;
    static {
        Map<String,Spell> spells=new LinkedHashMap<>();
        add(spells,"spark","Искра","fire",1,8,3);
        add(spells,"fireball","Огненный шар","fire",2,18,6,"spark");
        add(spells,"flame_wall","Стена пламени","fire",3,30,12,"fireball");
        add(spells,"meteor","Метеор","fire",4,42,18,"flame_wall");
        add(spells,"water_bolt","Водяной снаряд","water",1,9,3);
        add(spells,"tidal_surge","Волна","water",2,20,7,"water_bolt");
        add(spells,"water_guard","Водяной покров","water",3,24,12,"tidal_surge");
        add(spells,"ice_needle","Ледяная стрела","ice",1,8,3);
        add(spells,"frost_armor","Ледяная броня","ice",2,20,10,"ice_needle");
        add(spells,"freeze","Заморозка","ice",3,26,12,"frost_armor");
        add(spells,"shock","Разряд","lightning",1,10,4);
        add(spells,"chain_lightning","Цепная молния","lightning",2,24,9,"shock");
        add(spells,"haste","Ускорение","lightning",3,22,12,"chain_lightning");
        add(spells,"heal","Лечение","nature",1,12,7);
        add(spells,"roots","Корни","nature",2,22,8,"heal");
        add(spells,"stone_shield","Каменный щит","nature",3,26,12,"roots");
        add(spells,"guardian","Страж","nature",4,40,20,"stone_shield");
        add(spells,"steam","Пар","combo",2,28,12,"fireball","tidal_surge");
        add(spells,"lava","Лава","combo",2,34,14,"fireball","roots");
        add(spells,"thunderstorm","Грозовая буря","combo",2,40,18,"frost_armor","chain_lightning");
        add(spells,"living_lightning","Живая молния","combo",2,36,14,"roots","chain_lightning");
        add(spells,"thermal_shock","Термический удар","combo",2,32,12,"fireball","frost_armor");
        add(spells,"electric_wave","Электрическая волна","combo",2,34,14,"tidal_surge","chain_lightning");
        ALL=Collections.unmodifiableMap(spells);
    }
    private static void add(Map<String,Spell> map,String id,String name,String e,int tier,int mana,int cd,String... parents) {
        map.put(id,new Spell(id,name,e,tier,mana,cd,List.of(parents)));
    }
    public static int xpNeeded(int level){return Math.max(1,level)*50;}
    public static int ratingDelta(int winner,int loser){return Math.max(1,(int)Math.round(24*(1-1/(1+Math.pow(10,(loser-winner)/400.0)))));}
}
