package com.solar.launcher.flow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Session cache of built {@link FlowItem} lists — instant Flow re-enter on same library scan.
 * ponytail: keyed by {@code libraryScanGen}; stale snapshot kept for paint-first reopen after scan bump.
 */
public final class FlowCatalogSessionCache {

    private int libraryGen = -1;
    private int optionsKey;
    private final Map<FlowMode, List<FlowItem>> byMode = new HashMap<FlowMode, List<FlowItem>>();
    /** Last built list per mode — survives generation bump for instant carousel paint. */
    private final Map<FlowMode, List<FlowItem>> staleByMode = new HashMap<FlowMode, List<FlowItem>>();

    private void syncGeneration(int libraryGen, int optionsKey) {
        if (libraryGen != this.libraryGen || optionsKey != this.optionsKey) {
            byMode.clear();
            this.libraryGen = libraryGen;
            this.optionsKey = optionsKey;
        }
    }

    public synchronized List<FlowItem> peek(FlowMode mode, int libraryGen, int optionsKey) {
        syncGeneration(libraryGen, optionsKey);
        return byMode.get(mode);
    }

    /** Paint-first reopen when library generation changed but a prior catalog still exists. */
    public synchronized List<FlowItem> peekStale(FlowMode mode) {
        if (mode == null || mode == FlowMode.UNSPECIFIED) mode = FlowMode.ALBUM;
        List<FlowItem> fresh = byMode.get(mode);
        if (fresh != null && !fresh.isEmpty()) return fresh;
        return staleByMode.get(mode);
    }

    public synchronized void put(FlowMode mode, int libraryGen, int optionsKey, List<FlowItem> items) {
        syncGeneration(libraryGen, optionsKey);
        byMode.put(mode, items);
        if (items != null && !items.isEmpty()) {
            staleByMode.put(mode, items);
        }
    }

    public synchronized void clear() {
        byMode.clear();
        staleByMode.clear();
        libraryGen = -1;
        optionsKey = 0;
    }
}
