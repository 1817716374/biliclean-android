package com.biliclean.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

final class BiliApiClient {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36";
    static final String MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36";
    static final String APP_USER_AGENT = "Mozilla/5.0 BiliDroid/8.13.0 (bbcallen@gmail.com) os/android model/Pixel 7 mobi_app/android build/8130300 channel/bili innerVer/8130300 osVer/14 network/2";
    private static final String COMMENT_WBI_IMG_KEY = "839c8b697b0d44dc80e9a604592bb432";
    private static final String COMMENT_WBI_SUB_KEY = "02cd020b04d64aacad6b3a08d06f8eb0";
    private static final int[] WBI_MIXIN_KEY_ENC_TAB = new int[]{
            46, 47, 18, 2, 53, 8, 23, 32,
            15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19,
            29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61,
            26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63,
            57, 62, 11, 36, 20, 34, 44, 52
    };

    private int feedIndex = 1;
    private int webPage = 1;
    private volatile String authCookie = "";
    private volatile String lastCommentDebug = "";
    private volatile String lastDanmakuDebug = "";

    void setAuthCookie(String authCookie) {
        this.authCookie = authCookie == null ? "" : authCookie.trim();
    }

    boolean hasAuthCookie() {
        return !authCookie.isEmpty();
    }

    String lastCommentDebug() {
        return lastCommentDebug;
    }

    String lastDanmakuDebug() {
        return lastDanmakuDebug;
    }

    AuthState fetchAuthState() throws Exception {
        AuthState state = new AuthState();
        if (authCookie.isEmpty()) {
            state.message = "anonymous";
            return state;
        }
        JSONObject root = getJson("https://api.bilibili.com/x/web-interface/nav", "https://www.bilibili.com/");
        if (root.optInt("code", -1) != 0) {
            state.message = root.optString("message", "nav failed");
            return state;
        }
        JSONObject data = root.optJSONObject("data");
        if (data != null && data.optBoolean("isLogin")) {
            state.loggedIn = true;
            state.userName = data.optString("uname");
            state.message = "ok";
        } else {
            state.message = "not logged in";
        }
        return state;
    }

    QrLoginSession createQrLoginSession() throws Exception {
        JSONObject root = getJson(
                "https://passport.bilibili.com/x/passport-login/web/qrcode/generate",
                "https://www.bilibili.com/"
        );
        if (root.optInt("code", -1) != 0) {
            throw new IllegalStateException(root.optString("message", "二维码生成失败"));
        }
        JSONObject data = root.optJSONObject("data");
        QrLoginSession session = new QrLoginSession();
        if (data != null) {
            session.url = data.optString("url");
            session.key = data.optString("qrcode_key");
        }
        if (session.url.isEmpty() || session.key.isEmpty()) {
            throw new IllegalStateException("二维码接口缺少 url/key");
        }
        return session;
    }

    QrLoginResult pollQrLogin(String key) throws Exception {
        String url = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=" + encode(key);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Referer", "https://www.bilibili.com/");
        connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
        int httpCode = connection.getResponseCode();
        InputStream stream = httpCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = readAll(stream);
        JSONObject root = new JSONObject(response);
        JSONObject data = root.optJSONObject("data");
        QrLoginResult result = new QrLoginResult();
        result.code = data == null ? root.optInt("code", -1) : data.optInt("code", root.optInt("code", -1));
        result.message = data == null ? root.optString("message", "") : data.optString("message", root.optString("message", ""));
        result.cookie = extractSetCookieHeader(connection);
        return result;
    }

    List<FeedItem> fetchFeedIndex() throws Exception {
        String url = "https://app.bilibili.com/x/v2/feed/index"
                + "?mobi_app=android&platform=android&network=wifi&qn=32&fnver=1&fnval=272"
                + "&force_host=2&video_mode=1&s_locale=zh_CN&idx=" + feedIndex++;
        JSONObject root = getJson(url, "https://www.bilibili.com/");
        JSONArray items = root.optJSONObject("data") == null ? null : root.optJSONObject("data").optJSONArray("items");
        return normalizeItems("feed_index", items);
    }

    List<FeedItem> fetchWebRcmd() throws Exception {
        int idx = webPage++;
        String url = "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd"
                + "?fresh_type=4&ps=12&fresh_idx=" + idx
                + "&fresh_idx_1h=" + idx
                + "&fetch_row=" + (idx * 4)
                + "&web_location=1430650";
        JSONObject root = getJson(url, "https://www.bilibili.com/");
        JSONArray items = root.optJSONObject("data") == null ? null : root.optJSONObject("data").optJSONArray("item");
        return normalizeItems("web_rcmd", items);
    }

    PlayInfo fetchPlayInfo(FeedItem item, int qn) throws Exception {
        String idParam = item.bvid.isEmpty()
                ? "avid=" + encode(item.aid)
                : "bvid=" + encode(item.bvid);
        String url = "https://api.bilibili.com/x/player/playurl?"
                + idParam
                + "&cid=" + encode(item.cid)
                + "&qn=" + qn
                + "&fnver=0&fnval=4048&fourk=1&platform=pc&high_quality=1";
        JSONObject root = getJson(url, item.webUrl());
        PlayInfo info = new PlayInfo();
        info.requestedQuality = qn;
        if (root.optInt("code", -1) != 0) {
            info.error = root.optString("message", "playurl failed");
            return info;
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            info.error = "playurl data missing";
            return info;
        }
        info.quality = data.optInt("quality");
        info.format = data.optString("format");
        JSONObject dash = data.optJSONObject("dash");
        if (dash != null) {
            JSONArray videos = dash.optJSONArray("video");
            JSONArray audios = dash.optJSONArray("audio");
            info.qualityOptions.addAll(parseQualityOptions(data, videos, info.quality));
            info.videoUrl = selectVideoUrl(videos, info.quality);
            info.audioUrl = firstMediaUrl(audios);
            info.playable = !info.videoUrl.isEmpty();
            return info;
        }
        info.qualityOptions.addAll(parseQualityOptions(data, null, info.quality));
        JSONArray durl = data.optJSONArray("durl");
        if (durl != null && durl.length() > 0) {
            JSONObject first = durl.optJSONObject(0);
            if (first != null) {
                info.videoUrl = first.optString("url");
                info.playable = !info.videoUrl.isEmpty();
            }
        }
        return info;
    }

    void enrichItem(FeedItem item) throws Exception {
        if (item.aid.isEmpty() && item.bvid.isEmpty()) return;
        String idParam = item.bvid.isEmpty()
                ? "aid=" + encode(item.aid)
                : "bvid=" + encode(item.bvid);
        JSONObject root = getJson("https://api.bilibili.com/x/web-interface/view?" + idParam, item.webUrl());
        if (root.optInt("code", -1) != 0) return;
        JSONObject data = root.optJSONObject("data");
        if (data == null) return;
        if (item.aid.isEmpty()) item.aid = data.optString("aid");
        if (item.bvid.isEmpty()) item.bvid = data.optString("bvid");
        if (item.cid.isEmpty()) item.cid = data.optString("cid");
        item.title = firstNonEmpty(data.optString("title"), item.title);
        item.desc = firstNonEmpty(data.optString("desc"), item.desc);
        item.cover = firstNonEmpty(data.optString("pic"), item.cover);
        item.durationSeconds = data.optInt("duration", item.durationSeconds);

        JSONObject owner = data.optJSONObject("owner");
        if (owner != null) {
            item.ownerName = firstNonEmpty(owner.optString("name"), item.ownerName);
            item.ownerMid = firstNonEmpty(owner.optString("mid"), item.ownerMid);
            item.ownerFace = firstNonEmpty(owner.optString("face"), item.ownerFace);
        }
        enrichOwnerFollowers(item);
        fillStats(item, data.optJSONObject("stat"));
        JSONObject dimension = data.optJSONObject("dimension");
        if (dimension != null) {
            item.width = dimension.optInt("width", item.width);
            item.height = dimension.optInt("height", item.height);
        }
    }

    void enrichStoryEntrance(FeedItem item) {
        if (item == null || (item.aid.isEmpty() && item.bvid.isEmpty())) return;
        try {
            StringBuilder url = new StringBuilder("https://app.bilibili.com/x/v2/feed/index/story?display_id=1&build=8130300&mobi_app=android&platform=android");
            if (!item.aid.isEmpty()) url.append("&aid=").append(encode(item.aid));
            if (!item.bvid.isEmpty()) url.append("&bvid=").append(encode(item.bvid));
            url.append("&statistics=").append(encode("{\"appId\":1,\"platform\":3,\"version\":\"8.13.0\",\"abtest\":\"\"}"));
            JSONObject root = getJson(url.toString(), item.webUrl(), APP_USER_AGENT);
            JSONObject data = root.optJSONObject("data");
            JSONArray items = data == null ? null : data.optJSONArray("items");
            if (items == null) return;
            JSONObject fallback = null;
            for (int i = 0; i < items.length(); i++) {
                JSONObject raw = items.optJSONObject(i);
                if (raw == null) continue;
                if (fallback == null) fallback = raw;
                String aid = first(raw, "aid", "id", "param");
                String bvid = first(raw, "bvid");
                if ((!item.aid.isEmpty() && item.aid.equals(aid))
                        || (!item.bvid.isEmpty() && item.bvid.equals(bvid))) {
                    applyCreativeEntrance(item, raw.optJSONObject("creative_entrance"));
                    return;
                }
            }
            if (fallback != null) applyCreativeEntrance(item, fallback.optJSONObject("creative_entrance"));
        } catch (Exception ignored) {
        }
    }

    private void enrichOwnerFollowers(FeedItem item) {
        if (item.ownerMid.isEmpty() || item.ownerFollowerCount > 0) return;
        try {
            JSONObject root = getJson("https://api.bilibili.com/x/relation/stat?vmid=" + encode(item.ownerMid), item.webUrl());
            if (root.optInt("code", -1) != 0) return;
            JSONObject data = root.optJSONObject("data");
            if (data != null) item.ownerFollowerCount = data.optLong("follower", 0);
        } catch (Exception ignored) {
        }
    }

    List<CommentItem> fetchComments(FeedItem item, int count) throws Exception {
        return fetchComments(item, count, false);
    }

    List<CommentItem> fetchComments(FeedItem item, int count, boolean byTime) throws Exception {
        return fetchCommentPage(item, count, byTime, "").comments;
    }

    CommentPage fetchCommentPage(FeedItem item, int count, boolean byTime, String offset) throws Exception {
        CommentPage page = new CommentPage();
        if (item.aid.isEmpty()) {
            lastCommentDebug = "missing aid bvid=" + item.bvid + " cid=" + item.cid;
            page.debug = lastCommentDebug;
            return page;
        }
        int mode = byTime ? 2 : 3;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("mode", String.valueOf(mode));
        params.put("oid", item.aid);
        params.put("pagination_str", "{\"offset\":\"" + escapeJson(offset == null ? "" : offset) + "\"}");
        params.put("plat", "1");
        params.put("seek_rpid", "");
        params.put("type", "1");
        params.put("web_location", "1315875");
        String url = "https://api.bilibili.com/x/v2/reply/wbi/main?" + signedWbiQuery(params);
        JSONObject root = getJson(url, item.webUrl());
        page = parseCommentPage(root);
        lastCommentDebug = (byTime ? "time" : "hot")
                + " wbi code=" + root.optInt("code", -1)
                + " msg=" + root.optString("message")
                + " len=" + page.comments.size()
                + " next=" + page.nextOffset
                + " mode=" + mode
                + " aid=" + item.aid
                + " bvid=" + item.bvid;
        android.util.Log.d("BiliClean", "comments " + lastCommentDebug);
        page.debug = lastCommentDebug;
        return page;
    }

    private List<CommentItem> parseComments(JSONObject root) {
        return parseCommentPage(root).comments;
    }

    private CommentPage parseCommentPage(JSONObject root) {
        CommentPage page = new CommentPage();
        List<CommentItem> comments = new ArrayList<>();
        if (root.optInt("code", -1) != 0) return page;
        JSONObject data = root.optJSONObject("data");
        Set<String> seen = new HashSet<>();
        parseCommentControl(data == null ? null : data.optJSONObject("control"), page.control);
        JSONArray topReplies = data == null ? null : data.optJSONArray("top_replies");
        appendCommentArray(topReplies, comments, seen);
        JSONArray replies = data == null ? null : data.optJSONArray("replies");
        appendCommentArray(replies, comments, seen);
        if (comments.isEmpty() && data != null) {
            JSONObject top = data.optJSONObject("top");
            appendCommentObject(top, comments, seen);
            JSONObject upper = data.optJSONObject("upper");
            JSONObject upperTop = upper == null ? null : upper.optJSONObject("top");
            appendCommentObject(upperTop, comments, seen);
        }
        JSONObject cursor = data == null ? null : data.optJSONObject("cursor");
        JSONObject paginationReply = cursor == null ? null : cursor.optJSONObject("pagination_reply");
        if (paginationReply != null) {
            page.nextOffset = paginationReply.optString("next_offset");
        }
        if (page.nextOffset.isEmpty() && cursor != null) {
            page.nextOffset = cursor.optString("next_offset");
        }
        page.hasMore = !page.nextOffset.isEmpty();
        page.comments.addAll(comments);
        return page;
    }

    private static void parseCommentControl(JSONObject raw, CommentControl control) {
        if (raw == null || control == null) return;
        control.rootInputText = raw.optString("root_input_text");
        control.childInputText = raw.optString("child_input_text");
        control.giveupInputText = raw.optString("giveup_input_text");
        control.inputDisabled = raw.optBoolean("input_disable", false);
    }

    private void appendCommentArray(JSONArray source, List<CommentItem> target, Set<String> seen) {
        if (source == null) return;
        for (int i = 0; i < source.length(); i++) {
            appendCommentObject(source.optJSONObject(i), target, seen);
        }
    }

    private void appendCommentObject(JSONObject reply, List<CommentItem> target, Set<String> seen) {
        if (reply == null) return;
        CommentItem comment = parseComment(reply, true);
        if (comment.invisible) return;
        String key = comment.rpid == null ? "" : comment.rpid;
        if (!key.isEmpty() && !seen.add(key)) return;
        target.add(comment);
    }

    VideoShotInfo fetchVideoShot(FeedItem item) throws Exception {
        VideoShotInfo info = new VideoShotInfo();
        if (item.aid.isEmpty() || item.cid.isEmpty()) return info;
        String url = "https://api.bilibili.com/x/player/videoshot?aid="
                + encode(item.aid)
                + "&cid=" + encode(item.cid);
        JSONObject root = getJson(url, item.webUrl());
        if (root.optInt("code", -1) != 0) return info;
        JSONObject data = root.optJSONObject("data");
        if (data == null) return info;
        info.pvDataUrl = normalizeUrl(data.optString("pvdata"));
        info.imgXLen = data.optInt("img_x_len");
        info.imgYLen = data.optInt("img_y_len");
        info.imgXSize = data.optInt("img_x_size");
        info.imgYSize = data.optInt("img_y_size");
        JSONArray images = data.optJSONArray("image");
        if (images != null) {
            for (int i = 0; i < images.length(); i++) {
                String image = normalizeUrl(images.optString(i));
                if (!image.isEmpty()) info.imageUrls.add(image);
            }
        }
        JSONArray index = data.optJSONArray("index");
        if (index != null && index.length() > 0) {
            info.pvTimes = parseIntArray(index);
        } else if (!info.pvDataUrl.isEmpty()) {
            info.pvTimes = parsePvData(downloadBytes(info.pvDataUrl, item.webUrl()));
        }
        return info;
    }

    List<CommentItem> fetchCommentReplies(FeedItem item, String rootRpid, int count) throws Exception {
        if (item.aid.isEmpty() || rootRpid == null || rootRpid.isEmpty()) return new ArrayList<>();
        String url = "https://api.bilibili.com/x/v2/reply/reply?type=1&oid="
                + encode(item.aid)
                + "&root=" + encode(rootRpid)
                + "&ps=" + count
                + "&pn=1";
        JSONObject root = getJson(url, item.webUrl());
        List<CommentItem> comments = new ArrayList<>();
        if (root.optInt("code", -1) != 0) return comments;
        JSONObject data = root.optJSONObject("data");
        JSONArray replies = data == null ? null : data.optJSONArray("replies");
        if (replies == null) return comments;
        for (int i = 0; i < replies.length(); i++) {
            JSONObject reply = replies.optJSONObject(i);
            if (reply != null) comments.add(parseComment(reply, false));
        }
        return comments;
    }

    List<String> fetchDanmaku(FeedItem item, int count) throws Exception {
        List<DanmakuEntry> entries = fetchDanmakuEntries(item, count);
        List<String> result = new ArrayList<>();
        for (DanmakuEntry entry : entries) result.add(entry.text);
        return result;
    }

    List<DanmakuEntry> fetchDanmakuEntries(FeedItem item, int count) throws Exception {
        List<DanmakuEntry> entries = new ArrayList<>();
        if (item.cid.isEmpty()) return entries;
        String xml = getText("https://api.bilibili.com/x/v1/dm/list.so?oid=" + encode(item.cid), item.webUrl());
        lastDanmakuDebug = "cid=" + item.cid + " bytes=" + xml.length();
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader(xml));
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT && entries.size() < count) {
            if (event == XmlPullParser.START_TAG && "d".equals(parser.getName())) {
                String p = parser.getAttributeValue(null, "p");
                String text = parser.nextText();
                if (text != null && !text.trim().isEmpty()) {
                    DanmakuEntry entry = parseDanmakuEntry(p, text.trim());
                    entries.add(entry);
                }
            }
            event = parser.next();
        }
        lastDanmakuDebug += " count=" + entries.size();
        android.util.Log.d("BiliClean", "danmaku " + lastDanmakuDebug);
        return entries;
    }

    private DanmakuEntry parseDanmakuEntry(String p, String text) {
        DanmakuEntry entry = new DanmakuEntry();
        entry.text = text;
        if (p == null || p.isEmpty()) return entry;
        String[] parts = p.split(",");
        try {
            if (parts.length > 0) entry.timeMs = Math.max(0L, Math.round(Double.parseDouble(parts[0]) * 1000.0));
            if (parts.length > 1) entry.mode = Integer.parseInt(parts[1]);
            if (parts.length > 2) entry.fontSize = Integer.parseInt(parts[2]);
            if (parts.length > 3) entry.color = 0xFF000000 | (Integer.parseInt(parts[3]) & 0x00FFFFFF);
        } catch (Exception ignored) {
        }
        entry.durationMs = entry.mode == 4 || entry.mode == 5 ? 3800L : 7200L;
        return entry;
    }

    void sendComment(FeedItem item, String message) throws Exception {
        if (authCookie.isEmpty()) throw new IllegalStateException("需要先登录");
        if (item.aid.isEmpty()) throw new IllegalStateException("当前视频缺少 aid，不能评论");
        String csrf = csrfToken();
        if (csrf.isEmpty()) throw new IllegalStateException("登录 Cookie 中缺少 bili_jct");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("type", "1");
        form.put("oid", item.aid);
        form.put("message", message);
        form.put("csrf", csrf);
        JSONObject root = postForm("https://api.bilibili.com/x/v2/reply/add", item.webUrl(), form);
        if (root.optInt("code", -1) != 0) {
            throw new IllegalStateException(root.optString("message", "评论发送失败"));
        }
    }

    void likeVideo(FeedItem item) throws Exception {
        if (authCookie.isEmpty()) throw new IllegalStateException("需要先登录");
        if (item.aid.isEmpty()) throw new IllegalStateException("当前视频缺少 aid，不能点赞");
        String csrf = csrfToken();
        if (csrf.isEmpty()) throw new IllegalStateException("登录 Cookie 中缺少 bili_jct");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("aid", item.aid);
        form.put("like", "1");
        form.put("csrf", csrf);
        JSONObject root = postForm("https://api.bilibili.com/x/web-interface/archive/like", item.webUrl(), form);
        if (root.optInt("code", -1) != 0) {
            throw new IllegalStateException(root.optString("message", "点赞失败"));
        }
    }

    private List<FeedItem> normalizeItems(String source, JSONArray items) {
        List<FeedItem> result = new ArrayList<>();
        if (items == null) return result;
        for (int i = 0; i < items.length(); i++) {
            JSONObject raw = items.optJSONObject(i);
            if (raw == null) continue;
            if (!HardFilter.reasons(raw).isEmpty()) continue;
            FeedItem item = new FeedItem();
            item.source = source;
            item.aid = first(raw, "aid", "id", "param");
            item.bvid = first(raw, "bvid");
            item.cid = firstPath(raw, "player_args.cid", "cid");
            item.title = first(raw, "title");
            item.ownerName = firstPath(raw, "owner.name", "args.up_name", "name");
            item.ownerMid = firstPath(raw, "owner.mid", "owner_mid", "mid");
            item.ownerFace = firstPath(raw, "owner.face", "face");
            item.cover = first(raw, "pic", "cover");
            item.uri = first(raw, "uri", "url");
            applyCreativeEntrance(item, raw.optJSONObject("creative_entrance"));
            item.rawGoto = raw.optString("goto");
            item.cardGoto = raw.optString("card_goto");
            item.cardType = raw.optString("card_type");
            item.durationSeconds = durationSeconds(first(raw, "duration", "duration_text"));
            item.viewCount = countValue(first(raw, "cover_left_text_1"));
            item.danmakuCount = countValue(first(raw, "cover_left_text_2"));
            fillStats(item, raw.optJSONObject("stat"));
            if (!item.cid.isEmpty() && (!item.aid.isEmpty() || !item.bvid.isEmpty()) && !item.title.isEmpty()) {
                result.add(item);
            }
        }
        return result;
    }

    private static void applyCreativeEntrance(FeedItem item, JSONObject entrance) {
        if (item == null || entrance == null) return;
        item.searchEntranceTitle = first(entrance, "title");
        item.searchEntranceJumpUri = first(entrance, "jump_uri", "uri");
        item.searchEntranceIcon = first(entrance, "icon");
        item.searchEntranceTrackInfo = first(entrance, "track_info");
    }

    private CommentItem parseComment(JSONObject reply, boolean includePreview) {
        CommentItem comment = new CommentItem();
        comment.rpid = reply.optString("rpid_str");
        if (comment.rpid.isEmpty()) comment.rpid = reply.optString("rpid");
        comment.like = reply.optLong("like");
        comment.invisible = reply.optBoolean("invisible", false);
        comment.replyCount = reply.optInt("rcount");
        comment.ctimeSeconds = reply.optLong("ctime");
        comment.ctimeText = formatTime(comment.ctimeSeconds);
        comment.location = reply.optString("reply_control_location");

        JSONObject member = reply.optJSONObject("member");
        if (member != null) {
            comment.mid = member.optString("mid");
            comment.user = member.optString("uname");
            comment.face = member.optString("avatar");
            JSONObject level = member.optJSONObject("level_info");
            if (level != null) comment.level = "LV" + level.optInt("current_level");
            JSONObject vip = member.optJSONObject("vip");
            if (vip != null) {
                comment.vip = vip.optInt("vipStatus") == 1
                        || vip.optInt("vipType") > 0
                        || vip.optInt("type") > 0;
            }
        }
        JSONObject content = reply.optJSONObject("content");
        if (content != null) {
            comment.message = content.optString("message");
            JSONObject emote = content.optJSONObject("emote");
            if (emote != null) {
                JSONArray names = emote.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String key = names.optString(i);
                        JSONObject data = emote.optJSONObject(key);
                        String url = data == null ? "" : data.optString("url");
                        if (!key.isEmpty() && !url.isEmpty()) comment.emoteUrls.put(key, url);
                    }
                }
            }
            JSONArray pictures = content.optJSONArray("pictures");
            if (pictures != null) {
                for (int i = 0; i < pictures.length() && i < 9; i++) {
                    JSONObject picture = pictures.optJSONObject(i);
                    if (picture == null) continue;
                    String url = firstNonEmpty(
                            picture.optString("img_src"),
                            picture.optString("url"),
                            picture.optString("src")
                    );
                    if (!url.isEmpty()) {
                        comment.pictureUrls.add(url);
                        comment.pictureWidths.add(firstPositiveInt(
                                picture.optInt("img_width"),
                                picture.optInt("width"),
                                picture.optInt("w")
                        ));
                        comment.pictureHeights.add(firstPositiveInt(
                                picture.optInt("img_height"),
                                picture.optInt("height"),
                                picture.optInt("h")
                        ));
                    }
                }
            }
        }
        if (includePreview) {
            JSONArray replies = reply.optJSONArray("replies");
            if (replies != null) {
                for (int i = 0; i < replies.length() && i < 2; i++) {
                    JSONObject child = replies.optJSONObject(i);
                    if (child != null) comment.previewReplies.add(parseComment(child, false));
                }
            }
        }
        return comment;
    }

    private static void fillStats(FeedItem item, JSONObject stat) {
        if (stat == null) return;
        item.viewCount = firstPositive(stat.optLong("view"), item.viewCount);
        item.likeCount = firstPositive(stat.optLong("like"), item.likeCount);
        item.replyCount = firstPositive(stat.optLong("reply"), item.replyCount);
        item.coinCount = firstPositive(stat.optLong("coin"), item.coinCount);
        item.favoriteCount = firstPositive(stat.optLong("favorite"), item.favoriteCount);
        item.shareCount = firstPositive(stat.optLong("share"), item.shareCount);
        item.danmakuCount = firstPositive(stat.optLong("danmaku"), item.danmakuCount);
    }

    private static List<QualityOption> parseQualityOptions(JSONObject data, JSONArray videos, int actualQn) {
        List<QualityOption> options = new ArrayList<>();
        JSONArray formats = data.optJSONArray("support_formats");
        if (formats != null) {
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < formats.length(); i++) {
                JSONObject raw = formats.optJSONObject(i);
                if (raw == null) continue;
                QualityOption option = new QualityOption();
                option.qn = raw.optInt("quality");
                if (option.qn <= 0 || !seen.add(option.qn)) continue;
                option.title = firstNonEmpty(raw.optString("new_description"), raw.optString("display_desc"), raw.optString("format"));
                option.displayDesc = raw.optString("display_desc");
                option.superscript = raw.optString("superscript");
                option.format = raw.optString("format");
                JSONArray codecs = raw.optJSONArray("codecs");
                if (codecs != null) {
                    for (int j = 0; j < codecs.length(); j++) {
                        String codec = codecs.optString(j);
                        if (!codec.isEmpty()) option.codecs.add(codec);
                    }
                }
                option.playable = hasVideoQuality(videos, option.qn);
                option.selected = option.qn == actualQn;
                option.requiresVip = raw.optInt("can_watch_qn_reason", 0) != 0
                        || !raw.optString("limit_watch_reason").isEmpty()
                        || raw.optString("report").contains("EXT_VIP_REPORT_PARAMS")
                        || (!option.playable && option.qn >= 112);
                options.add(option);
            }
        }
        if (!options.isEmpty()) return options;

        JSONArray acceptQuality = data.optJSONArray("accept_quality");
        JSONArray acceptDescription = data.optJSONArray("accept_description");
        if (acceptQuality == null) return options;
        for (int i = 0; i < acceptQuality.length(); i++) {
            QualityOption option = new QualityOption();
            option.qn = acceptQuality.optInt(i);
            option.title = acceptDescription == null ? clarityLabel(option.qn) : firstNonEmpty(acceptDescription.optString(i), clarityLabel(option.qn));
            option.displayDesc = option.title;
            option.playable = hasVideoQuality(videos, option.qn);
            option.selected = option.qn == actualQn;
            option.requiresVip = !option.playable && option.qn >= 112;
            options.add(option);
        }
        return options;
    }

    private static boolean hasVideoQuality(JSONArray videos, int qn) {
        if (videos == null) return false;
        for (int i = 0; i < videos.length(); i++) {
            JSONObject video = videos.optJSONObject(i);
            if (video != null && video.optInt("id") == qn && !mediaUrl(video).isEmpty()) return true;
        }
        return false;
    }

    private static String selectVideoUrl(JSONArray videos, int actualQn) {
        JSONObject selected = selectVideoObject(videos, actualQn);
        return selected == null ? "" : mediaUrl(selected);
    }

    private static JSONObject selectVideoObject(JSONArray videos, int actualQn) {
        if (videos == null || videos.length() == 0) return null;
        JSONObject exact = chooseBestCodec(videos, actualQn);
        if (exact != null) return exact;
        int lowerQn = -1;
        for (int i = 0; i < videos.length(); i++) {
            JSONObject video = videos.optJSONObject(i);
            if (video == null || mediaUrl(video).isEmpty()) continue;
            int id = video.optInt("id");
            if (id <= actualQn && id > lowerQn) lowerQn = id;
        }
        if (lowerQn > 0) {
            JSONObject lower = chooseBestCodec(videos, lowerQn);
            if (lower != null) return lower;
        }
        int highestQn = -1;
        for (int i = 0; i < videos.length(); i++) {
            JSONObject video = videos.optJSONObject(i);
            if (video == null || mediaUrl(video).isEmpty()) continue;
            highestQn = Math.max(highestQn, video.optInt("id"));
        }
        return highestQn > 0 ? chooseBestCodec(videos, highestQn) : null;
    }

    private static JSONObject chooseBestCodec(JSONArray videos, int qn) {
        JSONObject fallback = null;
        JSONObject hevc = null;
        JSONObject av1 = null;
        for (int i = 0; i < videos.length(); i++) {
            JSONObject video = videos.optJSONObject(i);
            if (video == null || video.optInt("id") != qn || mediaUrl(video).isEmpty()) continue;
            String codecs = video.optString("codecs").toLowerCase(Locale.ROOT);
            if (fallback == null) fallback = video;
            if (codecs.startsWith("avc1")) return video;
            if (hevc == null && (codecs.startsWith("hev1") || codecs.startsWith("hvc1"))) hevc = video;
            if (av1 == null && codecs.startsWith("av01")) av1 = video;
        }
        if (hevc != null) return hevc;
        if (av1 != null) return av1;
        return fallback;
    }

    private static String mediaUrl(JSONObject object) {
        if (object == null) return "";
        return first(object, "base_url", "baseUrl", "url");
    }

    private static String clarityLabel(int quality) {
        switch (quality) {
            case 127:
                return "8K 超高清";
            case 126:
                return "杜比视界";
            case 125:
                return "HDR";
            case 120:
                return "4K 超高清";
            case 116:
                return "1080P 60帧";
            case 112:
                return "1080P 高码率";
            case 80:
                return "1080P 高清";
            case 64:
                return "720P 准高清";
            case 32:
                return "480P 标清";
            case 16:
                return "360P 流畅";
            default:
                return quality + "P";
        }
    }

    private static String firstMediaUrl(JSONArray array) {
        if (array == null || array.length() == 0) return "";
        JSONObject first = array.optJSONObject(0);
        return mediaUrl(first);
    }

    private JSONObject getJson(String url, String referer) throws Exception {
        return new JSONObject(getText(url, referer));
    }

    private JSONObject getJson(String url, String referer, String userAgent) throws Exception {
        return new JSONObject(new String(downloadBytes(url, referer, userAgent), StandardCharsets.UTF_8));
    }

    private String getText(String url, String referer) throws Exception {
        return new String(downloadBytes(url, referer), StandardCharsets.UTF_8);
    }

    byte[] downloadBytes(String url, String referer) throws Exception {
        return downloadBytes(url, referer, USER_AGENT);
    }

    private byte[] downloadBytes(String url, String referer, String userAgent) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(normalizeUrl(url)).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", userAgent == null || userAgent.isEmpty() ? USER_AGENT : userAgent);
        connection.setRequestProperty("Referer", referer);
        connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        connection.setRequestProperty("Origin", "https://www.bilibili.com");
        if (!authCookie.isEmpty()) {
            connection.setRequestProperty("Cookie", authCookie);
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String encoding = connection.getContentEncoding();
        byte[] bytes = readBytes(stream);
        if (encoding != null) {
            if ("gzip".equalsIgnoreCase(encoding)) {
                bytes = readBytes(new GZIPInputStream(new ByteArrayInputStream(bytes)));
            } else if ("deflate".equalsIgnoreCase(encoding)) {
                try {
                    bytes = readBytes(new InflaterInputStream(new ByteArrayInputStream(bytes)));
                } catch (Exception zlibError) {
                    bytes = readBytes(new InflaterInputStream(new ByteArrayInputStream(bytes), new Inflater(true)));
                }
            }
        }
        return bytes;
    }

    private JSONObject postForm(String url, String referer, Map<String, String> form) throws Exception {
        byte[] body = encodeForm(form).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Referer", referer);
        connection.setRequestProperty("Origin", "https://www.bilibili.com");
        connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("Content-Length", String.valueOf(body.length));
        if (!authCookie.isEmpty()) {
            connection.setRequestProperty("Cookie", authCookie);
        }
        OutputStream output = connection.getOutputStream();
        output.write(body);
        output.close();
        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = readAll(stream);
        return new JSONObject(response);
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "{}";
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    private static String extractSetCookieHeader(HttpURLConnection connection) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            String name = entry.getKey();
            if (name == null || !"Set-Cookie".equalsIgnoreCase(name)) continue;
            for (String rawCookie : entry.getValue()) {
                if (rawCookie == null || rawCookie.isEmpty()) continue;
                String cookie = rawCookie.split(";", 2)[0].trim();
                if (cookie.isEmpty()) continue;
                if (builder.length() > 0) builder.append("; ");
                builder.append(cookie);
            }
        }
        return builder.toString();
    }

    private static byte[] readBytes(InputStream stream) throws Exception {
        if (stream == null) return new byte[0];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String first(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key);
            if (value != null && !value.isEmpty() && !"null".equals(value)) return value;
        }
        return "";
    }

    private static String firstPath(JSONObject object, String... paths) {
        for (String path : paths) {
            String value = path(object, path);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty() && !"null".equals(value)) return value;
        }
        return "";
    }

    private static String path(JSONObject object, String path) {
        String[] parts = path.split("\\.");
        Object current = object;
        for (String part : parts) {
            if (!(current instanceof JSONObject)) return "";
            current = ((JSONObject) current).opt(part);
            if (current == null || current == JSONObject.NULL) return "";
        }
        return String.valueOf(current);
    }

    private static int durationSeconds(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            if (value.matches("\\d+")) return Integer.parseInt(value);
            String[] parts = value.split(":");
            if (parts.length == 2) return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            if (parts.length == 3) return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {
        }
        return 0;
    }

    private static long countValue(String value) {
        if (value == null || value.isEmpty()) return 0;
        String normalized = value.trim().replace(",", "");
        try {
            if (normalized.endsWith("万")) {
                return Math.round(Double.parseDouble(normalized.substring(0, normalized.length() - 1)) * 10000);
            }
            if (normalized.endsWith("亿")) {
                return Math.round(Double.parseDouble(normalized.substring(0, normalized.length() - 1)) * 100000000);
            }
            return Long.parseLong(normalized);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static long firstPositive(long value, long fallback) {
        return value > 0 ? value : fallback;
    }

    private static int firstPositiveInt(int... values) {
        for (int value : values) {
            if (value > 0) return value;
        }
        return 0;
    }

    private static String formatTime(long ctimeSeconds) {
        if (ctimeSeconds <= 0) return "";
        long diff = Math.max(0, (System.currentTimeMillis() / 1000L) - ctimeSeconds);
        if (diff < 60) return "刚刚";
        if (diff < 3600) return (diff / 60) + "分钟前";
        if (diff < 86400) return (diff / 3600) + "小时前";
        if (diff < 2592000) return (diff / 86400) + "天前";
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        return format.format(new Date(ctimeSeconds * 1000L));
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private static String signedWbiQuery(Map<String, String> params) throws Exception {
        Map<String, String> signed = new LinkedHashMap<>(params);
        signed.put("wts", String.valueOf(System.currentTimeMillis() / 1000L));
        List<String> keys = new ArrayList<>(signed.keySet());
        keys.sort(String::compareTo);
        StringBuilder query = new StringBuilder();
        for (String key : keys) {
            if (query.length() > 0) query.append('&');
            query.append(encode(key))
                    .append('=')
                    .append(encode(filterWbiValue(signed.get(key))));
        }
        String wRid = md5(query + mixinKey(COMMENT_WBI_IMG_KEY, COMMENT_WBI_SUB_KEY));
        query.append("&w_rid=").append(wRid);
        return query.toString();
    }

    private static String filterWbiValue(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replace("!", "")
                .replace("'", "")
                .replace("(", "")
                .replace(")", "")
                .replace("*", "");
    }

    private static String mixinKey(String imgKey, String subKey) {
        String source = imgKey + subKey;
        StringBuilder builder = new StringBuilder();
        for (int index : WBI_MIXIN_KEY_ENC_TAB) {
            if (index >= 0 && index < source.length()) builder.append(source.charAt(index));
            if (builder.length() >= 32) break;
        }
        return builder.toString();
    }

    private static String md5(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) builder.append('0');
            builder.append(hex);
        }
        return builder.toString();
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        if (trimmed.startsWith("//")) return "https:" + trimmed;
        if (trimmed.startsWith("http://")) return "https://" + trimmed.substring("http://".length());
        return trimmed;
    }

    private static String escapeJson(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static int[] parsePvData(byte[] bytes) {
        if (bytes == null || bytes.length < 2) return new int[0];
        int count = bytes.length / 2;
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            int hi = bytes[i * 2] & 0xFF;
            int lo = bytes[i * 2 + 1] & 0xFF;
            result[i] = (hi << 8) | lo;
        }
        return result;
    }

    private static int[] parseIntArray(JSONArray array) {
        if (array == null || array.length() == 0) return new int[0];
        int[] result = new int[array.length()];
        for (int i = 0; i < array.length(); i++) {
            result[i] = Math.max(0, array.optInt(i));
        }
        return result;
    }

    private static String encodeForm(Map<String, String> form) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (builder.length() > 0) builder.append('&');
            builder.append(encode(entry.getKey()))
                    .append('=')
                    .append(encode(entry.getValue()));
        }
        return builder.toString();
    }

    private String csrfToken() {
        String[] parts = authCookie.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("bili_jct=")) {
                return trimmed.substring("bili_jct=".length());
            }
        }
        return "";
    }
}
