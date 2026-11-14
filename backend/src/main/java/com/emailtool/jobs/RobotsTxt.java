package com.emailtool.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The robots.txt rules that apply to one user-agent token (RFC 9309): the group naming the token,
 * else the {@code *} group; longest matching rule wins, Allow wins ties; {@code *} and {@code $}
 * wildcards.
 */
final class RobotsTxt {

    static final RobotsTxt ALLOW_ALL = new RobotsTxt(List.of());
    static final RobotsTxt DISALLOW_ALL = new RobotsTxt(List.of(new Rule(false, "/")));

    private record Rule(boolean allow, String pattern) {
        boolean matches(String path) {
            return toRegex(pattern).matcher(path).lookingAt();
        }
    }

    private final List<Rule> rules;

    private RobotsTxt(List<Rule> rules) {
        this.rules = rules;
    }

    static RobotsTxt parse(String body, String agentToken) {
        String token = agentToken.toLowerCase(Locale.ROOT);
        List<Rule> specific = new ArrayList<>();
        List<Rule> star = new ArrayList<>();
        boolean sawSpecific = false;
        List<String> groupAgents = new ArrayList<>();
        boolean inRules = false;
        List<Rule> groupRules = new ArrayList<>();
        for (String raw : (body + "\nuser-agent: __end__").split("\\r?\\n")) {
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                if (inRules) {
                    // close the previous group
                    for (String agent : groupAgents) {
                        if (agent.equals("*")) {
                            star.addAll(groupRules);
                        } else if (token.contains(agent) || agent.contains(token)) {
                            specific.addAll(groupRules);
                            sawSpecific = true;
                        }
                    }
                    groupAgents = new ArrayList<>();
                    groupRules = new ArrayList<>();
                    inRules = false;
                }
                groupAgents.add(value.toLowerCase(Locale.ROOT));
            } else if (key.equals("allow") || key.equals("disallow")) {
                inRules = true;
                if (!value.isEmpty()) {
                    groupRules.add(new Rule(key.equals("allow"), value));
                }
            }
        }
        return new RobotsTxt(sawSpecific ? specific : star);
    }

    boolean allows(String pathAndQuery) {
        Rule best = null;
        for (Rule rule : rules) {
            if (!rule.matches(pathAndQuery)) {
                continue;
            }
            if (best == null
                    || rule.pattern().length() > best.pattern().length()
                    || (rule.pattern().length() == best.pattern().length() && rule.allow())) {
                best = rule;
            }
        }
        return best == null || best.allow();
    }

    private static Pattern toRegex(String pattern) {
        StringBuilder re = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                re.append(".*");
            } else if (c == '$' && i == pattern.length() - 1) {
                re.append('$');
            } else {
                re.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(re.toString());
    }
}
