package com.biliclean.app;

final class CommentControl {
    String rootInputText = "";
    String childInputText = "";
    String giveupInputText = "";
    boolean inputDisabled;

    String rootHint() {
        return rootInputText == null || rootInputText.isEmpty() ? "发条友善的评论吧" : rootInputText;
    }

    String childHint() {
        return childInputText == null || childInputText.isEmpty() ? "回复一下吧" : childInputText;
    }

    String giveupHint() {
        return giveupInputText == null || giveupInputText.isEmpty() ? "不发没关系，请继续友善哦~" : giveupInputText;
    }
}
