package com.vast.vm;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义语法管理器
 */
public class CustomSyntaxManager {
    private final Map<String, CustomRule> rules = new HashMap<>();
    private final Map<String, String> keywordToRule = new HashMap<>();
    private final VastVM vm;

    public CustomSyntaxManager(VastVM vm) {
        this.vm = vm;
    }

    /**
     * 添加自定义规则
     */
    public void addRule(CustomRule rule) {
        rules.put(rule.getId(), rule);

        // 注册关键字到规则的映射
        for (String keyword : rule.getKeywords()) {
            String cleanKeyword = extractKeywordName(keyword);
            if (keywordToRule.containsKey(cleanKeyword)) {
                vm.getDebugger().warning("Keyword conflict: '" + cleanKeyword +
                        "' already mapped to rule: " + keywordToRule.get(cleanKeyword));
            } else {
                keywordToRule.put(cleanKeyword, rule.getId());
                vm.getDebugger().debug("Registered keyword: " + cleanKeyword + " -> " + rule.getId());
            }
        }
    }

    /**
     * 从关键字格式中提取关键字名称
     * 例如: "operator{0}" -> "operator"
     */
    private String extractKeywordName(String keyword) {
        int braceIndex = keyword.indexOf('{');
        if (braceIndex > 0) {
            return keyword.substring(0, braceIndex);
        }
        return keyword;
    }

    /**
     * 根据关键字查找规则
     */
    public CustomRule findRuleByKeyword(String keyword) {
        String ruleId = keywordToRule.get(keyword);
        return ruleId != null ? rules.get(ruleId) : null;
    }

    /**
     * 获取所有规则
     */
    public Collection<CustomRule> getAllRules() {
        return rules.values();
    }

    /**
     * 清理所有规则
     */
    public void clear() {
        rules.clear();
        keywordToRule.clear();
    }

    /**
     * 获取管理器状态信息
     */
    public String getManagerInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Custom Syntax Manager Status:\n");
        sb.append("Registered Rules: ").append(rules.size()).append("\n");
        sb.append("Registered Keywords: ").append(keywordToRule.size()).append("\n");

        if (!rules.isEmpty()) {
            sb.append("\nRules:\n");
            rules.values().forEach(rule ->
                    sb.append("  - ").append(rule).append("\n"));
        }

        return sb.toString();
    }
}