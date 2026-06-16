package com.biliclean.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CommentItem {
    String rpid = "";
    String mid = "";
    String user = "";
    String face = "";
    String message = "";
    String ctimeText = "";
    long ctimeSeconds = 0L;
    String location = "";
    String level = "";
    boolean vip = false;
    long like = 0;
    boolean liked = false;
    boolean disliked = false;
    boolean invisible = false;
    int replyCount = 0;
    Map<String, String> emoteUrls = new LinkedHashMap<>();
    List<String> pictureUrls = new ArrayList<>();
    List<Integer> pictureWidths = new ArrayList<>();
    List<Integer> pictureHeights = new ArrayList<>();
    List<CommentItem> previewReplies = new ArrayList<>();
}
