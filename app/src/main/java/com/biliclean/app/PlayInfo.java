package com.biliclean.app;

import java.util.ArrayList;
import java.util.List;

final class PlayInfo {
    boolean playable;
    int quality;
    int requestedQuality;
    String format = "";
    String videoUrl = "";
    String audioUrl = "";
    String error = "";
    final List<QualityOption> qualityOptions = new ArrayList<>();
}
