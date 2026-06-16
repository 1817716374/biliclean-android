package com.biliclean.app;

final class PrefetchedVideo {
    final FeedItem item;
    final PlayInfo playInfo;

    PrefetchedVideo(FeedItem item, PlayInfo playInfo) {
        this.item = item;
        this.playInfo = playInfo;
    }
}
