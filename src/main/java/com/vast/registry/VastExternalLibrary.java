package com.vast.registry;

import com.vast.vm.VastVM;
import java.util.Map;

public interface VastExternalLibrary {
    LibraryMetadata getMetadata();
    void initialize(VastVM vm, VastLibraryRegistry registry);
    default void cleanup() {}
    Map<String, Class<?>> getProvidedClasses();
}