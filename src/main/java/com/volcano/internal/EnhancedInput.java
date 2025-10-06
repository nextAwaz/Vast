package com.volcano.internal;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * 增强的输入处理 - 支持同一行多段输入
 */
public class EnhancedInput {

    private static final PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);

    /**
     * 在同一行内捕获多个输入段
     * @param prompt 提示信息
     * @param segmentCount 输入段数量
     * @return 各输入段的值数组
     */
    public static String[] captureSegments(String prompt, int segmentCount) {
        try {
            out.print(prompt);
            out.flush();

            String[] segments = new String[segmentCount];

            for (int i = 0; i < segmentCount; i++) {
                StringBuilder currentSegment = new StringBuilder();
                boolean inputComplete = false;

                while (!inputComplete) {
                    int charCode = System.in.read();

                    if (charCode == -1) {
                        inputComplete = true; // EOF
                    } else {
                        char c = (char) charCode;

                        if (c == '\r' || c == '\n') {
                            // 回车键 - 完成当前段
                            inputComplete = true;
                            segments[i] = currentSegment.toString();

                            // 如果不是最后一个段，添加空格并继续
                            if (i < segmentCount - 1) {
                                out.print(' ');
                                out.flush();
                            } else {
                                out.println(); // 最后一个段完成后换行
                            }
                        } else if (c == '\b' || charCode == 127) {
                            // 退格键处理
                            if (currentSegment.length() > 0) {
                                currentSegment.deleteCharAt(currentSegment.length() - 1);
                                out.print("\b \b");
                                out.flush();
                            }
                        } else if (Character.isDefined(c) && !Character.isISOControl(c)) {
                            // 普通字符
                            currentSegment.append(c);
                            out.print(c);
                            out.flush();
                        }
                    }
                }
            }

            return segments;

        } catch (IOException e) {
            System.err.println("Input error: " + e.getMessage());
            String[] result = new String[segmentCount];
            for (int i = 0; i < segmentCount; i++) {
                result[i] = "";
            }
            return result;
        }
    }
}