package com.campusask.campusask.crawler;

import java.util.*;
import java.util.regex.Pattern;

/** User-agent groups, wildcard paths, longest-match rules, and Allow on equal specificity. */
public final class RobotsRules {
    private record Rule(boolean allow,String path) {}
    private static final class Group {
        final List<String> agents=new ArrayList<>(); final List<Rule> rules=new ArrayList<>();
    }
    private final List<Rule> rules;
    public RobotsRules(String text) {
        List<Group> groups=new ArrayList<>(); Group current=new Group();
        for(String raw:text.split("\\R")) {
            String line=raw.split("#",2)[0].trim(); int colon=line.indexOf(':'); if(colon<0) continue;
            String key=line.substring(0,colon).trim().toLowerCase(Locale.ROOT), value=line.substring(colon+1).trim();
            if(key.equals("user-agent")) {
                if(!current.rules.isEmpty()) { groups.add(current); current=new Group(); }
                current.agents.add(value.toLowerCase(Locale.ROOT));
            } else if((key.equals("allow")||key.equals("disallow"))&&!value.isEmpty()&&!current.agents.isEmpty())
                current.rules.add(new Rule(key.equals("allow"),value));
        }
        groups.add(current);
        boolean specific=groups.stream().anyMatch(g -> g.agents.contains("campusaskbot"));
        rules=groups.stream().filter(g -> g.agents.contains(specific?"campusaskbot":"*")).flatMap(g -> g.rules.stream()).toList();
    }
    public boolean allowed(String path) {
        Rule best=null; int length=-1;
        for(Rule rule:rules) {
            String raw=rule.path(); boolean anchored=raw.endsWith("$"); if(anchored) raw=raw.substring(0,raw.length()-1);
            String regex=Arrays.stream(raw.split("\\*",-1)).map(Pattern::quote).reduce((a,b)->a+".*"+b).orElse("");
            int specificity=raw.replace("*","").length();
            if(Pattern.compile("^"+regex+(anchored?"$":"")).matcher(path).find()
                    && (specificity>length || (specificity==length && rule.allow()))) { best=rule; length=specificity; }
        }
        return best==null || best.allow();
    }
}
