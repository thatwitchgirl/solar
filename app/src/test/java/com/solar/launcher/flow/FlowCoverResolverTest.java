package com.solar.launcher.flow;

import com.solar.launcher.AlbumNames;
import com.solar.launcher.ArtistNames;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FlowCoverResolverTest {

    @Test
    public void resolveDiskCachedNullForNullItem() {
        assertNull(FlowCoverResolver.resolveDiskCached(null, null, 144));
    }

    @Test
    public void initialsFromName() {
        assertEquals("R", FlowCoverResolver.initialsFor("Radiohead"));
        assertEquals("TC", FlowCoverResolver.initialsFor("The Cure"));
        assertEquals("?", FlowCoverResolver.initialsFor(""));
    }

    @Test
    public void albumMatchKeyStableForArtlessAlbum() {
        String key = FlowCoverResolver.albumMatchKey("No Art", "Band");
        assertEquals(AlbumNames.matchKey("No Art") + "|" + ArtistNames.matchKey("Band"), key);
    }
}
