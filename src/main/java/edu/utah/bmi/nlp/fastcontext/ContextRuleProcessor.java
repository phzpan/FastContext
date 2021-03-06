/*
 * ******************************************************************************
 *  * Copyright  2017  Department of Biomedical Informatics, University of Utah
 *  * <p>
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  * <p>
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  * <p>
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  ******************************************************************************
 */
package edu.utah.bmi.nlp.fastcontext;

import edu.utah.bmi.nlp.context.common.ConTextSpan;
import edu.utah.bmi.nlp.context.common.ContextRule;
import edu.utah.bmi.nlp.context.common.ContextValueSet.TriggerTypes;
import edu.utah.bmi.nlp.context.common.IOUtil;
import edu.utah.bmi.nlp.core.Span;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is to construct the chained-up HashMaps structure for the rulesMap, and provide methods to
 * process the rulesMap
 *
 * @author Jianlin Shi
 */
@SuppressWarnings("rawtypes")
public class ContextRuleProcessor {
    //   the nested map structure for rule processing
    private HashMap rulesMap = new HashMap();
    //   map a rule to its corresponding line number
    public LinkedHashMap<Integer, ContextRule> rules = new LinkedHashMap<Integer, ContextRule>();


    private Pattern pdigit;
    private Matcher mt;
    private final String END = "<END>";
    protected boolean caseInsensitive = false;

    public ContextRuleProcessor(String ruleFileName) {
        initiate(IOUtil.readRuleFile(ruleFileName));
    }

    public ContextRuleProcessor(String ruleFileName, String splitter) {
        initiate(IOUtil.readCSVRuleFile(ruleFileName, splitter));
    }

    public ContextRuleProcessor(ArrayList<String> ruleslist) {
        initiate(IOUtil.convertListToRuleMap(ruleslist, "\\|"));
    }


    public ContextRuleProcessor(String ruleFileName, boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
        initiate(IOUtil.readRuleFile(ruleFileName));
    }

    public ContextRuleProcessor(ArrayList<String> ruleslist, boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
        initiate(IOUtil.convertListToRuleMap(ruleslist, "\\|"));
    }


    public void setCaseInsensitive(boolean CaseInsensitive) {
        this.caseInsensitive = CaseInsensitive;
    }


    private void initiate(LinkedHashMap<Integer, ContextRule> rules) {
        this.rules = rules;
        rulesMap.clear();
        if (pdigit == null)
            pdigit = Pattern.compile("(\\d+)(-\\w+)?");
        for (ContextRule rule : rules.values()) {
            if (rule.getDirection() == TriggerTypes.both) {
                rule.setDirection(TriggerTypes.forward);
                addRule(rule);
                rule.setDirection(TriggerTypes.backward);
                addRule(rule);
                rule.setDirection(TriggerTypes.both);
            } else
                addRule(rule);
        }
    }


    /**
     * @param rule Parsed context rule from String
     * @return true: if the rule is added; false: if the rule is a duplicate
     */
    private boolean addRule(ContextRule rule) {
        // use to store the HashMap sub-chain that have the key chain that meet
        // the rule[]
        ArrayList<HashMap> rules_tmp = new ArrayList<HashMap>();
        HashMap rule1 = rulesMap;
        HashMap rule2 = new HashMap();
        HashMap rulet = new HashMap();
        if (caseInsensitive)
            rule.rule = rule.rule.toLowerCase();
        String[] ruleContent = rule.rule.split("\\s+");
        int length = ruleContent.length;
        int i = 0;
        rules_tmp.add(rulesMap);
        while (i < length && rule1 != null && rule1.containsKey(ruleContent[i])) {
            rule1 = (HashMap) rule1.get(ruleContent[i]);
            i++;
        }
        // if the rule has been included
        if (i > length)
            return false;
        // start with the determinant, construct the last descendant HashMap
        // <Determinant, null>
        if (i == length) {
            if (rule1.containsKey(END)) {
                ((HashMap) rule1.get(END)).put(rule.determinant, rule.id);
            } else {
                rule2.put(rule.determinant, rule.id);
                rule1.put(END, rule2.clone());
            }
            return true;
        } else {
            rule2.put(rule.determinant, rule.id);
            rule2.put(END, rule2.clone());
            rule2.remove(rule.determinant);

            // filling the HashMap chain which rules doesn't have the key chain
            for (int j = length - 1; j > i; j--) {
                rulet = (HashMap) rule2.clone();
                rule2.clear();
                rule2.put(ruleContent[j], rulet);
            }
        }
        rule1.put(ruleContent[i], rule2.clone());
        return true;
    }


    /**
     * @param contextTokens The context tokens in an ArrayList of String
     * @param startposition Keep track of the position where matching starts
     * @param matches       Storing the matched context spans
     */
    public void processRules(List<Span> contextTokens, int startposition, LinkedHashMap<String, ConTextSpan> matches) {
        // use the first "startposition" to remember the original start matching
        // position.
        // use the 2nd one to remember the start position in which recursion.
        if (caseInsensitive)
            for (int i = startposition; i < contextTokens.size(); i++) {
                contextTokens.get(i).text = contextTokens.get(i).text.toLowerCase();
            }
        for (int i = startposition; i < contextTokens.size(); i++) {
            // System.out.println(contextTokens.get(i));
            processRules(contextTokens, rulesMap, i, i, matches);
        }

    }


    /**
     * @param contextTokens   The context tokens in an ArrayList of String
     * @param rule            Constructed Rules Map
     * @param matchBegin      Keep track of the begin position of matched span
     * @param currentPosition Keep track of the position where matching starts
     * @param matches         Storing the matched context spans
     */
    private void processRules(List<Span> contextTokens, HashMap rule, int matchBegin, int currentPosition,
                              LinkedHashMap<String, ConTextSpan> matches) {
        // when reach the end of the tunedcontext, end the iteration
        if (currentPosition < contextTokens.size()) {
            // start processing the tunedcontext tokens
            String thisToken = contextTokens.get(currentPosition).text;
//			System.out.println("thisToken-"+thisToken+"<");
            if (rule.containsKey("\\w+")) {
                processRules(contextTokens, (HashMap) rule.get("\\w+"), matchBegin, currentPosition + 1, matches);
            }
            if (rule.containsKey("\\W+") && isUpperCase(thisToken)) {
                processRules(contextTokens, (HashMap) rule.get("\\W+"), matchBegin, currentPosition + 1, matches);
            }
            // if the end of a rule is met
            if (rule.containsKey(END)) {
                addDeterminants(rule, matches, matchBegin, currentPosition);
            }
            // if the current token match the element of a rule
            if (rule.containsKey(thisToken)) {
                processRules(contextTokens, (HashMap) rule.get(thisToken), matchBegin, currentPosition + 1, matches);
            }
            if (rule.containsKey(">") && Character.isDigit(thisToken.charAt(0))) {
                processDigits(contextTokens, (HashMap) rule.get(">"), matchBegin, currentPosition, matches);
            }
        } else if (currentPosition == contextTokens.size() && rule.containsKey(END)) {
            addDeterminants(rule, matches, matchBegin, currentPosition);
        }
    }


    /**
     * Because the digit regular expressions are revised into the format like
     * "> 13 days", the rulesMap need to be handled differently to the token
     * matching
     * rulesMap
     *
     * @param contextTokens   The context tokens in an ArrayList of String
     * @param rule            Constructed Rules Map
     * @param matchBegin      Keep track of the begin position of matched span
     * @param currentPosition Keep track of the position where matching starts
     * @param matches         Storing the matched context spans
     */
    private void processDigits(List<Span> contextTokens, HashMap rule, int matchBegin, int currentPosition,
                               LinkedHashMap<String, ConTextSpan> matches) {
        mt = pdigit.matcher(contextTokens.get(currentPosition).text);
        if (mt.find()) {
            int thisDigit;
//			prevent length over limit
            if (mt.group(1).length() < 4) {
                thisDigit = Integer.parseInt(mt.group(1));
            } else {
                thisDigit = 1000;
            }
            Set<String> numbers = rule.keySet();
            for (String num : numbers) {
                int ruleDigit = Integer.parseInt(num);
                if (thisDigit > ruleDigit) {
                    if (mt.group(2) == null) {
                        // if this token is a number
                        processRules(contextTokens, (HashMap) rule.get(ruleDigit + ""), matchBegin, currentPosition + 1, matches);
                    } else {
                        // thisToken is like "30-days"
                        HashMap ruletmp = (HashMap) rule.get(ruleDigit + "");
                        String subtoken = mt.group(2).substring(1);
                        if (ruletmp.containsKey(subtoken)) {
                            processRules(contextTokens, (HashMap) ruletmp.get(subtoken), matchBegin, currentPosition + 1, matches);
                        }
                    }
                }
            }
        }
    }


    /**
     * if reaches the end of one or more rulesMap, add all corresponding
     * determinants into the results
     * <p/>
     * The priority of multiple applicable rulesMap can be modified. This version
     * uses the following three rulesMap:
     * 1. if determinant spans overlap, choose the determinant with the widest
     * span
     * 2. else if prefer right determinant, choose the determinant with the
     * largest end.
     * 3. else if prefer left determinant, choose the determinant with the
     * smallest begin.
     *
     * @param rule            Constructed Rules Map
     * @param matches         Storing the matched context spans
     * @param matchBegin      Keep track of the begin position of matched span
     * @param currentPosition Keep track of the position where matching starts
     */
    private void addDeterminants(HashMap rule, LinkedHashMap<String, ConTextSpan> matches, int matchBegin, int currentPosition) {
        HashMap<String, ?> matchedRules = (HashMap<String, ?>) rule.get(END);

//        int id = (Integer) rule.values().iterator().next();
//        ContextRule matchedRule = rules.get(id);
//        Span currentSpan = new Span(matchBegin, i - 1, id);
        for (String key : matchedRules.keySet()) {
            ConTextSpan originalSpan = null;
            int id = (Integer) matchedRules.get(key);
            char matchedDirection = key.charAt(0);
            ConTextSpan currentSpan = new ConTextSpan(matchBegin, currentPosition - 1, id);
            if (matches.containsKey(key)) {
                originalSpan = matches.get(key);
                switch (matchedDirection) {
                    case 'f':
                        if (originalSpan.begin >= currentSpan.end) {
                            continue;
                        } else if (originalSpan.width > currentSpan.width && originalSpan.end >= currentSpan.begin) {
                            continue;
                        }
                        break;
                    case 'b':
                        if (originalSpan.end <= currentSpan.begin) {
                            continue;
                        } else if (originalSpan.width > currentSpan.width && originalSpan.begin >= currentSpan.end) {
                            continue;
                        }
                        break;
                }

                ContextRule matchedRule = rules.get(id);
                //  adjust context window, for later version that support combining modifiers with shared context window terminals
                if (originalSpan.begin != -1 && (matchedDirection == 'f')) {
                    if (matchedRule.triggerType == TriggerTypes.termination) {
                        currentSpan.winBegin = originalSpan.winBegin > currentSpan.end ? originalSpan.winBegin : currentSpan.end;
                    } else {
                        currentSpan.winBegin = originalSpan.winBegin;
                    }
                } else if (originalSpan.end != -1 && (matchedDirection == 'b')) {
                    if (matchedRule.triggerType == TriggerTypes.termination) {
                        currentSpan.winEnd = originalSpan.winEnd < currentSpan.winEnd ? originalSpan.winEnd : currentSpan.begin;
                    } else {
                        currentSpan.winEnd = originalSpan.winEnd;
                    }
                }
            }
            matches.put(key, currentSpan);
        }

    }

    protected boolean isUpperCase(String token) {
        boolean res = true;
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isUpperCase(token.charAt(i))) {
                res = false;
                break;
            }
        }
        return res;
    }

}
