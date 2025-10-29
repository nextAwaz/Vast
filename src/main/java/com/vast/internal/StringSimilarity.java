package com.vast.internal;

import java.util.*;

public class StringSimilarity {

    /**
     * 计算两个字符串的编辑距离（Levenshtein距离）
     */
    public static int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[len1][len2];
    }

    /**
     * 计算字符串相似度（0-1之间，1表示完全相同）
     */
    public static double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0;
        if (s1.equals(s2)) return 1.0;

        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLength;
    }

    /**
     * 查找最相似的字符串
     */
    public static List<String> findSimilarStrings(String target, Collection<String> candidates,
                                                  int maxResults, double minSimilarity) {
        List<SimilarityResult> results = new ArrayList<>();

        for (String candidate : candidates) {
            double similarity = similarity(target, candidate);
            if (similarity >= minSimilarity) {
                results.add(new SimilarityResult(candidate, similarity));
            }
        }

        // 按相似度降序排序
        results.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        // 返回前N个结果
        List<String> topResults = new ArrayList<>();
        for (int i = 0; i < Math.min(maxResults, results.size()); i++) {
            topResults.add(results.get(i).candidate);
        }

        return topResults;
    }

    private static class SimilarityResult {
        String candidate;
        double similarity;

        SimilarityResult(String candidate, double similarity) {
            this.candidate = candidate;
            this.similarity = similarity;
        }
    }
}