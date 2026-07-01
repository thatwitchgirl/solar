package com.solar.launcher.podcast;

import org.junit.Test;

import java.util.List;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class PodcastIjkPlayerTest {

    @Test
    public void podcastOptions_includeSoundTouchForPitchPreservingSpeed() {
        PodcastIjkPlayer.selfCheck();
        List<PodcastIjkPlayer.Option> opts = PodcastIjkPlayer.podcastPlayerOptions();
        if (opts.isEmpty()) throw new AssertionError("empty");
        boolean soundtouch = false;
        for (PodcastIjkPlayer.Option o : opts) {
            if (o.name == null || o.name.isEmpty()) throw new AssertionError("name");
            if ("soundtouch".equals(o.name)
                    && o.category == IjkMediaPlayer.OPT_CATEGORY_PLAYER
                    && o.longValue == 1) {
                soundtouch = true;
            }
        }
        if (!soundtouch) throw new AssertionError("soundtouch=1 missing");
    }
}
