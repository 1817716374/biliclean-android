package com.biliclean.app;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class CleanQueueRepository {
    private final BiliApiClient apiClient = new BiliApiClient();
    private final ArrayDeque<FeedItem> queue = new ArrayDeque<>();
    private final Set<String> seen = new HashSet<>();
    private boolean preferFeedIndex = true;

    synchronized int size() {
        return queue.size();
    }

    synchronized FeedItem poll() {
        return queue.pollFirst();
    }

    synchronized FeedItem peek() {
        return queue.peekFirst();
    }

    synchronized void clear() {
        queue.clear();
        seen.clear();
        preferFeedIndex = true;
    }

    void fillTo(int target) throws Exception {
        int attempts = 0;
        while (size() < target && attempts < 12) {
            List<FeedItem> items = preferFeedIndex ? apiClient.fetchFeedIndex() : apiClient.fetchWebRcmd();
            preferFeedIndex = !preferFeedIndex;
            addAll(items);
            attempts++;
        }
    }

    BiliApiClient apiClient() {
        return apiClient;
    }

    private synchronized void addAll(List<FeedItem> items) {
        for (FeedItem item : items) {
            String identity = item.identity();
            if (seen.contains(identity)) continue;
            seen.add(identity);
            queue.addLast(item);
        }
    }
}
