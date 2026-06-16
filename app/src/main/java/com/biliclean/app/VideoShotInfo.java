package com.biliclean.app;

import java.util.ArrayList;
import java.util.List;

final class VideoShotInfo {
    String pvDataUrl = "";
    final List<String> imageUrls = new ArrayList<>();
    int imgXLen = 0;
    int imgYLen = 0;
    int imgXSize = 0;
    int imgYSize = 0;
    int[] pvTimes = new int[0];

    boolean isUsable() {
        return imgXLen > 0
                && imgYLen > 0
                && imgXSize > 0
                && imgYSize > 0
                && !imageUrls.isEmpty()
                && pvTimes.length > 0;
    }
}
