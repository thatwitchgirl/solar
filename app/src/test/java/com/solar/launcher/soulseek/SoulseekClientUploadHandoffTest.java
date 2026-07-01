package com.solar.launcher.soulseek;

import org.junit.Test;

/** Upload queue must keep P socket open until runUpload finishes handshake. */
public class SoulseekClientUploadHandoffTest {

  @Test
  public void retainPeerSocketWhenUploadActiveOrQueued() {
    if (!SoulseekClient.shouldRetainPeerSocketAfterQueueUpload(
            SoulseekClient.UploadHandoffResult.ACTIVE)) {
      throw new AssertionError("ACTIVE must hand off P socket");
    }
    if (!SoulseekClient.shouldRetainPeerSocketAfterQueueUpload(
            SoulseekClient.UploadHandoffResult.QUEUED)) {
      throw new AssertionError("QUEUED must hand off P socket");
    }
    if (SoulseekClient.shouldRetainPeerSocketAfterQueueUpload(
            SoulseekClient.UploadHandoffResult.DENIED)) {
      throw new AssertionError("DENIED must not hand off P socket");
    }
  }
}
