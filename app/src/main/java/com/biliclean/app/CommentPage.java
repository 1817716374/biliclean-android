package com.biliclean.app;

import java.util.ArrayList;
import java.util.List;

final class CommentPage {
    final List<CommentItem> comments = new ArrayList<>();
    final CommentControl control = new CommentControl();
    String nextOffset = "";
    boolean hasMore;
    String debug = "";
}
