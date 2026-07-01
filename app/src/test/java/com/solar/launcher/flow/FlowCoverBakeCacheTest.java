package com.solar.launcher.flow;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FlowCoverBakeCacheTest {

    @Test
    public void peekDoesNotBakeOnMiss() {
        FlowCoverBakeCache cache = new FlowCoverBakeCache();
        CoverFlowLayout.Metrics m = CoverFlowLayout.metricsForViewport(480f, 360f);
        assertNull(cache.peek("album:test", m, true));
        assertNull(cache.get("album:test", null, m, true));
        cache.clear();
    }

    @Test
    public void reflectTableRowCap() {
        int[] table = PictureFlowLayout.buildReflectTable(120f);
        assertTrue(table.length <= PictureFlowLayout.REFLECT_TABLE_MAX_ROWS);
    }
}
