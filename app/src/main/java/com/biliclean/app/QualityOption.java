package com.biliclean.app;

import java.util.ArrayList;
import java.util.List;

final class QualityOption {
    int qn;
    String title = "";
    String displayDesc = "";
    String superscript = "";
    String format = "";
    final List<String> codecs = new ArrayList<>();
    boolean requiresVip;
    boolean playable;
    boolean selected;
}
