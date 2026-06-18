package com.biliclean.app;

final class FeedItem {
    String source = "";
    String aid = "";
    String bvid = "";
    String cid = "";
    String title = "";
    String desc = "";
    String ownerName = "";
    String ownerMid = "";
    String ownerFace = "";
    long ownerFollowerCount = 0;
    String cover = "";
    String uri = "";
    String searchEntranceTitle = "";
    String searchEntranceJumpUri = "";
    String searchEntranceIcon = "";
    String searchEntranceTrackInfo = "";
    String rawGoto = "";
    String cardGoto = "";
    String cardType = "";
    int durationSeconds = 0;
    int width = 0;
    int height = 0;
    long viewCount = 0;
    long likeCount = 0;
    long replyCount = 0;
    long coinCount = 0;
    long favoriteCount = 0;
    long shareCount = 0;
    long danmakuCount = 0;
    int copyright = 0;
    boolean liked = false;

    String identity() {
        if (!bvid.isEmpty()) return bvid;
        if (!aid.isEmpty()) return aid;
        return source + ":" + title + ":" + ownerName;
    }

    String webUrl() {
        if (!bvid.isEmpty()) return "https://www.bilibili.com/video/" + bvid;
        if (!aid.isEmpty()) return "https://www.bilibili.com/video/av" + aid;
        return uri;
    }

    boolean isHorizontal() {
        return width > 0 && height > 0 && width >= height;
    }
}
