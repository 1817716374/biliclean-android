package com.biliclean.app;

final class QrLoginResult {
    int code;
    String message = "";
    String cookie = "";

    boolean loggedIn() {
        return code == 0 && cookie.contains("SESSDATA");
    }
}
