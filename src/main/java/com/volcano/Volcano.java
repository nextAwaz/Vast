package com.volcano;

import com.volcano.vm.VolcanoVM;
import com.volcano.internal.exception.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Volcano {

    public static class VolcanoException extends RuntimeException {
        public VolcanoException(String message) {
            super(message);
        }

        public VolcanoException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class Builder {
        private boolean debug = false;
        private Object externalExtension = null;

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        /**
         * 设置外部扩展 - 现在通过库系统管理
         * 注意：externalExtension 应该实现 VolcanoExternalLibrary 接口
         */
        public Builder externalExtension(Object extension) {
            this.externalExtension = extension;
            return this;
        }

        public VolcanoVM build() {
            VolcanoVM vm = new VolcanoVM();
            vm.setDebugMode(debug);

            // 修复：不再使用 setGlobal，而是将外部扩展注册为库
            if (externalExtension != null) {
                registerExternalExtension(vm, externalExtension);
            }
            return vm;
        }


        /**
         * 注册外部扩展为库
         */
        private void registerExternalExtension(VolcanoVM vm, Object extension) {
            try {
                // 检查扩展是否实现了 VolcanoExternalLibrary 接口
                if (extension instanceof com.volcano.registry.VolcanoExternalLibrary) {
                    // 直接注册库实例
                    String libraryName = "ExternalExtension";
                    vm.getLibraryRegistry().registerLibraryInstance(libraryName,
                            (com.volcano.registry.VolcanoExternalLibrary) extension);
                } else {
                    // 如果不是 VolcanoExternalLibrary，创建一个包装器
                    ExternalExtensionWrapper wrapper = new ExternalExtensionWrapper(extension);
                    vm.getLibraryRegistry().registerLibraryInstance("ExternalWrapper", wrapper);
                }
            } catch (Exception e) {
                System.err.println("Failed to register external extension: " + e.getMessage());
                if (debug) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * 注册扩展实例
         */
        private void registerExtensionInstance(VolcanoVM vm, String libraryName, Object extension) {
            // 由于当前的 LibraryRegistry 设计只接受类而不是实例，
            // 我们需要创建一个适配器或者修改 LibraryRegistry
            // 这里简化处理：打印警告信息
            System.err.println("Warning: External extension registration not fully implemented in new library system");
            System.err.println("Extension class: " + extension.getClass().getName());

            // 临时方案：将扩展存储在 VM 的局部变量中，供 do 语句使用
            vm.getLocalVariables().put("_external_extension", extension);
        }

        /**
         * 包装非 VolcanoExternalLibrary 扩展
         */
        private void wrapAndRegisterExtension(VolcanoVM vm, Object extension) {
            // 创建一个包装器库
            ExternalExtensionWrapper wrapper = new ExternalExtensionWrapper(extension);
            registerExtensionInstance(vm, "ExternalWrapper", wrapper);
        }

        public void run(String scriptPath) {
            VolcanoVM vm = build();
            try {
                List<String> lines = Files.readAllLines(Paths.get(scriptPath));
                vm.execute(lines);
            } catch (Exception e) {
                throw new VolcanoException("Failed to execute script: " + e.getMessage(), e);
            }
        }

        public void execute(String code) {
            VolcanoVM vm = build();
            try {
                List<String> lines = List.of(code.split("\n"));
                vm.execute(lines);
            } catch (Exception e) {
                throw new VolcanoException("Failed to execute code: " + e.getMessage(), e);
            }
        }

        public Object runWithResult(String scriptPath) {
            VolcanoVM vm = build();
            try {
                List<String> lines = Files.readAllLines(Paths.get(scriptPath));
                return vm.executeWithResult(lines);
            } catch (Exception e) {
                throw new VolcanoException("Failed to execute script: " + e.getMessage(), e);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static void run(String scriptPath) {
        run(scriptPath, false);
    }

    public static void run(String scriptPath, boolean debug) {
        builder().debug(debug).run(scriptPath);
    }

    public static void execute(String code) {
        execute(code, false);
    }

    public static void execute(String code, boolean debug) {
        builder().debug(debug).execute(code);
    }

    public static Object runWithResult(String scriptPath) {
        return runWithResult(scriptPath, false);
    }

    public static Object runWithResult(String scriptPath, boolean debug) {
        return builder().debug(debug).runWithResult(scriptPath);
    }

    private static void validateScriptFile(String scriptPath) {
        File file = new File(scriptPath);
        if (!file.exists()) {
            throw new VolcanoException("Script file not found: " + scriptPath);
        }
        if (!scriptPath.endsWith(".vast")) {
            throw new VolcanoException("Only .vast files are supported: " + scriptPath);
        }
    }

    /**
     * 外部扩展包装器 - 将任意对象包装成 VolcanoExternalLibrary
     */
    private static class ExternalExtensionWrapper implements com.volcano.registry.VolcanoExternalLibrary {
        private final Object wrappedExtension;

        public ExternalExtensionWrapper(Object extension) {
            this.wrappedExtension = extension;
        }

        @Override
        public com.volcano.registry.LibraryMetadata getMetadata() {
            return new com.volcano.registry.LibraryMetadata(
                    "ExternalWrapper",
                    "0.0.3",
                    "Wrapper for external extension: " + wrappedExtension.getClass().getName(),
                    "Volcano System"
            );
        }

        @Override
        public void initialize(com.volcano.vm.VolcanoVM vm, com.volcano.registry.LibraryRegistry registry) {
            // 调用扩展的初始化方法（如果存在）
            try {
                java.lang.reflect.Method initMethod = wrappedExtension.getClass().getMethod("init");
                initMethod.invoke(wrappedExtension);
            } catch (NoSuchMethodException e) {
                // 没有 init 方法，忽略
            } catch (Exception e) {
                System.err.println("Failed to initialize external extension: " + e.getMessage());
            }
        }

        @Override
        public void cleanup() {
            // 调用扩展的清理方法（如果存在）
            try {
                java.lang.reflect.Method cleanupMethod = wrappedExtension.getClass().getMethod("cleanup");
                cleanupMethod.invoke(wrappedExtension);
            } catch (NoSuchMethodException e) {
                // 没有 cleanup 方法，忽略
            } catch (Exception e) {
                System.err.println("Failed to cleanup external extension: " + e.getMessage());
            }
        }

        @Override
        public java.util.Map<String, Class<?>> getProvidedClasses() {
            // 包装器不提供额外的类
            return java.util.Map.of();
        }

        @Override
        public java.util.Map<String, com.volcano.registry.KeywordHandler> getKeywordHandlers() {
            return java.util.Map.of();
        }

        @Override
        public java.util.Map<String, com.volcano.registry.StatementHandler> getStatementHandlers() {
            return java.util.Map.of();
        }

        /**
         * 获取被包装的扩展实例
         */
        public Object getWrappedExtension() {
            return wrappedExtension;
        }
    }
}