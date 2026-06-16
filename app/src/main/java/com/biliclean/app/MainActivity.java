package com.biliclean.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.airbnb.lottie.LottieAnimationView;
import com.opensource.svgaplayer.SVGACallback;
import com.opensource.svgaplayer.SVGAImageView;
import com.opensource.svgaplayer.SVGAParser;
import com.opensource.svgaplayer.SVGAVideoEntity;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@UnstableApi
public final class MainActivity extends Activity {
    private static final String PREFS = "biliclean_auth";
    private static final String PREF_COOKIE = "cookie";
    private static final String PREF_QUALITY_QN = "quality_qn";
    private static final int BILI_PINK = 0xFFFF6699;
    private static final long MEDIA_CACHE_BYTES = 300L * 1024L * 1024L;
    private static final int FORWARD_PREFETCH_BATCH_SIZE = 5;
    private static final int FORWARD_PREFETCH_REFILL_THRESHOLD = 2;
    private static final int FORWARD_PREFETCH_LIMIT = 10;
    private static final long PREFETCH_STREAM_BYTES = 2L * 1024L * 1024L;
    private static final long PROGRESS_FRAME_BUCKET_MS = 4000L;
    private static final float DESIGN_STATUS_BOTTOM_PCT = 4.95f;
    private static final float DESIGN_GESTURE_TOP_PCT = 96.22f;
    private static final float DESIGN_APP_HEIGHT_PCT = DESIGN_GESTURE_TOP_PCT - DESIGN_STATUS_BOTTOM_PCT;
    private static final float SWIPE_SWITCH_VELOCITY_THRESHOLD = 1100f;

    private final CleanQueueRepository repository = new CleanQueueRepository();
    private final Object prefetchLock = new Object();
    private final ArrayDeque<PrefetchedVideo> previousStack = new ArrayDeque<>();
    private final ArrayDeque<PrefetchedVideo> nextStack = new ArrayDeque<>();
    private final ArrayDeque<PrefetchedVideo> forwardReturnStack = new ArrayDeque<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final DecelerateInterpolator swipeInterpolator = new DecelerateInterpolator(1.8f);
    private final Map<String, Bitmap> imageCache = new HashMap<>();
    private final Map<String, Bitmap> progressFrameCache = new HashMap<>();
    private final Map<String, Long> playbackPositions = new HashMap<>();
    private final Map<String, List<CommentVoteBinding>> commentVoteBindings = new HashMap<>();
    private final Map<String, List<TextView>> previewReplyViews = new HashMap<>();
    private final Set<String> notInterestedVideoIds = new HashSet<>();
    private final Set<String> watchLaterVideoIds = new HashSet<>();

    private SharedPreferences prefs;
    private ExoPlayer player;
    private ExoPlayer warmPlayer;
    private SimpleCache mediaCache;
    private AudioManager audioManager;
    private PlayerView playerView;
    private View videoTouchLayer;
    private ValueAnimator videoViewportAnimator;
    private ValueAnimator commentsPanelAnimator;
    private GestureDetector gestureDetector;
    private long suppressSingleTapUntilMs;
    private SVGAParser svgaParser;
    private SVGAVideoEntity doubleLikeSvgaVideo;
    private SVGAVideoEntity tripleLikeSvgaVideo;
    private FrameLayout root;
    private FrameLayout pageLayer;
    private View topShade;
    private View rightShade;
    private FrameLayout swipePreviewPage;
    private ImageView swipePreviewView;
    private FeedItem swipePreviewItem;
    private View swipePreviewTopShade;
    private View swipePreviewRightShade;
    private FrameLayout swipePreviewTopBar;
    private TextView swipePreviewWatching;
    private ImageView swipePreviewPeopleIcon;
    private TextView swipePreviewBackButton;
    private ImageView swipePreviewSearchButton;
    private ImageView swipePreviewMenuButton;
    private View swipePreviewBottomShade;
    private FrameLayout swipePreviewBottomInfo;
    private FrameLayout swipePreviewOwnerGroup;
    private LinearLayout swipePreviewOwnerTexts;
    private TextView swipePreviewFollowButton;
    private FrameLayout swipePreviewInputRow;
    private TextView swipePreviewDanmakuInputPill;
    private View swipePreviewDanmakuInputDivider;
    private ImageView swipePreviewDanmakuButton;
    private ImageView swipePreviewAvatar;
    private TextView swipePreviewOwner;
    private TextView swipePreviewFans;
    private TextView swipePreviewTitle;
    private TextView swipePreviewMeta;
    private TextView swipePreviewSearch;
    private FrameLayout swipePreviewDetailPageButton;
    private LinearLayout swipePreviewRail;
    private LinearLayout swipePreviewDanmaku;
    private TextView swipePreviewNotice;
    private FrameLayout swipePreviewProgressBar;
    private View swipePreviewProgressFill;
    private DisplayModeIconButton swipePreviewFullscreenButton;
    private RailActionButton swipePreviewLikeButton;
    private RailActionButton swipePreviewCommentButton;
    private RailActionButton swipePreviewCoinButton;
    private RailActionButton swipePreviewFavoriteButton;
    private RailActionButton swipePreviewShareButton;
    private DanmakuOverlayView danmakuLayer;
    private FrameLayout topBar;
    private ImageView topPeopleIcon;
    private TextView topBackButton;
    private ImageView topSearchButton;
    private ImageView topMenuButton;
    private LinearLayout actionRail;
    private FrameLayout bottomInfo;
    private View bottomShade;
    private FrameLayout ownerInfoGroup;
    private LinearLayout ownerTextStack;
    private FrameLayout bottomInputRow;
    private FrameLayout lightControls;
    private LinearLayout danmakuActionBox;
    private LinearLayout commentsPanel;
    private FrameLayout commentsContentFrame;
    private LinearLayout commentSubHeader;
    private LinearLayout commentsList;
    private ScrollView commentsScrollView;
    private LinearLayout commentDetailDrawer;
    private LinearLayout commentDetailList;
    private ScrollView commentDetailScrollView;
    private ImageView ownerAvatarView;
    private TextView watchingView;
    private TextView ownerView;
    private TextView fansView;
    private TextView ownerFollowButton;
    private TextView titleView;
    private TextView metaView;
    private TextView noticeView;
    private TextView searchSuggestView;
    private FrameLayout detailPageButton;
    private RailActionButton likeButton;
    private RailActionButton commentButton;
    private RailActionButton coinButton;
    private RailActionButton favoriteButton;
    private RailActionButton shareButton;
    private TextView centerOverlay;
    private DisplayModeIconButton fullscreenButton;
    private ImageView danmakuButton;
    private TextView danmakuInputPill;
    private View danmakuInputDivider;
    private TextView lightWatchingView;
    private TextView lightPlayButton;
    private TextView lightTimeView;
    private ImageView lightDanmakuButton;
    private TextView lightDanmakuInputPill;
    private FrameLayout lightProgressBar;
    private View lightProgressFill;
    private SeekTvThumbView lightProgressThumb;
    private TextView landscapeTitleView;
    private TextView landscapeOwnerNameView;
    private ImageView landscapeAudienceIconView;
    private TextView landscapeStatusTimeView;
    private TextView landscapeDurationView;
    private ImageView landscapeViewerAvatarView;
    private ImageView landscapeOwnerAvatarView;
    private TextView landscapeWatchingTextView;
    private TextView landscapeFollowButton;
    private TextView landscapeTreasureTextView;
    private TextView landscapeSpeedView;
    private TextView landscapeClarityView;
    private LandscapeActionButton landscapeLikeButton;
    private LandscapeActionButton landscapeCommentButton;
    private LandscapeActionButton landscapeFavoriteButton;
    private LandscapeActionButton landscapeCoinButton;
    private LandscapeActionButton landscapeShareButton;
    private FrameLayout progressBubbleCard;
    private final Runnable hideProgressBubbleRunnable = () -> {
        if (progressBubbleCard != null) progressBubbleCard.setVisibility(View.GONE);
    };
    private VideoShotPreviewView progressBubbleCover;
    private TextView progressBubble;
    private TextView commentTitleView;
    private TextView commentSortView;
    private TextView commentSectionTitleView;
    private ImageView commentExpandButton;
    private EditText commentInput;
    private TextView sendCommentButton;
    private FrameLayout progressBar;
    private View progressFill;
    private SeekTvThumbView progressThumb;
    private Dialog activeBottomSheet;

    private PrefetchedVideo currentVideo;
    private PrefetchedVideo warmVideo;
    private FeedItem currentItem;
    private PrefetchedVideo prefetchedVideo;
    private boolean prefetchRunning;
    private int prefetchGeneration;
    private boolean fastForwarding;
    private boolean uiHidden;
    private boolean clearScreenMode;
    private boolean landscapeMode;
    private boolean danmakuEnabled = true;
    private boolean danmakuVisible = true;
    private boolean currentFollowed;
    private boolean currentCoined;
    private boolean currentFavorited;
    private boolean autoSlideEnabled;
    private boolean mirrorEnabled;
    private boolean closeAfterCurrentVideo;
    private boolean backgroundAudioEnabled;
    private boolean pausedByBackgroundRule;
    private boolean tripleHoldActive;
    private boolean tripleLikeWasLiked;
    private boolean tripleCommitted;
    private float playbackSpeed = 1.0f;
    private boolean progressFrameLoading;
    private boolean progressVideoShotLoading;
    private String progressSpriteLoadingUrl = "";
    private String progressVideoShotKey = "";
    private VideoShotInfo progressVideoShotInfo;
    private int progressFrameGeneration;
    private long lastProgressFrameRequestMs;
    private long lastProgressFramePositionMs = -1L;
    private long pendingProgressFramePositionMs = -1L;
    private boolean progressDragging;
    private long progressDragPositionMs = -1L;
    private float progressLastTouchX = Float.NaN;
    private String currentClarity = "自动";
    private int globalPreferredQn = 80;
    private String timerCloseOption = "关闭";
    private final Runnable timerCloseRunnable = new Runnable() {
        @Override
        public void run() {
            closeAfterCurrentVideo = false;
            timerCloseOption = "关闭";
            if (player != null) player.pause();
            showCenter("已定时暂停");
            Toast.makeText(MainActivity.this, "定时关闭已生效", Toast.LENGTH_SHORT).show();
        }
    };
    private int danmakuOpacityPercent = 90;
    private int danmakuAreaPercent = 100;
    private int danmakuTextSp = 16;
    private int danmakuDelayMs = 520;
    private int danmakuMaxRows = 10;
    private boolean colorfulDanmaku = true;
    private boolean blockFixedDanmaku;
    private boolean blockRollingDanmaku;
    private boolean blockColorDanmaku;
    private boolean blockAdvancedDanmaku;
    private boolean blockCountDanmaku;
    private boolean hideRepeatedDanmaku;
    private boolean portraitProtectDanmaku = true;
    private boolean keywordShieldDanmaku = true;
    private boolean commentsByTime;
    private final CommentControl currentCommentControl = new CommentControl();
    private boolean commentInputEverFocused;
    private boolean commentSentInCurrentSession;
    private boolean commentsLoading;
    private boolean commentsHasMore;
    private boolean commentDetailOpen;
    private int commentsGeneration;
    private int commentDetailGeneration;
    private String commentsNextOffset = "";
    private String commentsVideoKey = "";
    private final List<CommentItem> loadedComments = new ArrayList<>();
    private int danmakuColorCursor;
    private int draftDanmakuColor = BILI_PINK;
    private int draftDanmakuMode = 1;
    private int draftDanmakuTextSp = 16;
    private int danmakuGeneration;
    private DanmakuSprite selectedDanmakuSprite;
    private List<DanmakuEntry> activeDanmakuEntries = java.util.Collections.emptyList();
    private int nextDanmakuEntryIndex;
    private long lastDanmakuPositionMs;
    private final long[] danmakuRowReadyAtMs = new long[32];
    private final Set<String> emittedDanmakuTexts = new HashSet<>();
    private final Set<String> danmakuShieldWords = new HashSet<>();
    private float touchDownY;
    private long touchDownTimeMs;
    private long tripleHoldStartMs;
    private VelocityTracker swipeVelocityTracker;
    private float lastDragY;
    private float commentsDragStartY;
    private int commentsDragStartHeight;
    private boolean commentsExpanded;
    private boolean swipeDragging;
    private boolean switchAnimating;
    private boolean swipePreviewForward;
    private boolean holdSwipePreviewUntilReady;
    private String swipePreviewVideoKey = "";
    private boolean landscapeProgressDragging;
    private boolean landscapeVerticalAdjusting;
    private int landscapeVerticalAdjustMode;
    private float landscapeProgressStartX;
    private float landscapeProgressLastX = Float.NaN;
    private long landscapeProgressStartPositionMs = -1L;
    private float landscapeGestureStartBrightness = 0.5f;
    private int landscapeGestureStartVolume;
    private boolean touchMovedBeyondTapSlop;
    private float touchDownX;

    private final Runnable releaseHeldSwipePreviewRunnable = new Runnable() {
        @Override
        public void run() {
            releaseHeldSwipePreview();
        }
    };

    private final Runnable hideLightControlsRunnable = new Runnable() {
        @Override
        public void run() {
            if (landscapeMode && !landscapeProgressDragging) {
                setLightControlsVisible(false);
            }
        }
    };

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            uiHandler.postDelayed(this, 500);
        }
    };

    private final Player.Listener playbackListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_READY && holdSwipePreviewUntilReady) {
                releaseHeldSwipePreview();
            }
            if (playbackState == Player.STATE_ENDED && autoSlideEnabled && !switchAnimating) {
                playNext();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        repository.apiClient().setAuthCookie(prefs.getString(PREF_COOKIE, ""));
        globalPreferredQn = prefs.getInt(PREF_QUALITY_QN, 80);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        buildUi();
        initPlayer();
        refreshAuthState();
        loadInitial();
        uiHandler.post(progressRunnable);
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        recycleSwipeVelocityTracker();
        if (player != null) {
            player.release();
            player = null;
        }
        releaseWarmPlayer();
        if (mediaCache != null) {
            try {
                mediaCache.release();
            } catch (Exception ignored) {
            }
            mediaCache = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (pausedByBackgroundRule && !backgroundAudioEnabled && player != null) {
            pausedByBackgroundRule = false;
            player.play();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applySystemBarsForMode();
    }

    @Override
    protected void onStop() {
        if (!backgroundAudioEnabled && player != null && player.isPlaying()) {
            pausedByBackgroundRule = true;
            player.pause();
        }
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (commentsPanel != null && commentsPanel.getVisibility() == View.VISIBLE) {
            if (commentDetailOpen) {
                hideCommentDetailDrawer(true);
                return;
            }
            hideComments();
            return;
        }
        if (landscapeMode) {
            exitLandscapeMode();
            return;
        }
        if (uiHidden || clearScreenMode) {
            exitLightControlsMode();
            return;
        }
        super.onBackPressed();
    }

    private void initPlayer() {
        File cacheDir = new File(getCacheDir(), "media-cache");
        mediaCache = new SimpleCache(
                cacheDir,
                new LeastRecentlyUsedCacheEvictor(MEDIA_CACHE_BYTES),
                new StandaloneDatabaseProvider(this)
        );
        player = newPlaybackPlayer();
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        playerView.setPlayer(player);
    }

    private ExoPlayer newPlaybackPlayer() {
        ExoPlayer next = new ExoPlayer.Builder(this)
                .setLoadControl(shortBufferLoadControl())
                .build();
        next.setSeekParameters(SeekParameters.EXACT);
        next.addListener(playbackListener);
        return next;
    }

    private DefaultLoadControl shortBufferLoadControl() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(2500, 9000, 700, 1500)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        pageLayer = new FrameLayout(this);
        pageLayer.setBackgroundColor(Color.BLACK);

        playerView = (PlayerView) getLayoutInflater().inflate(R.layout.player_view_texture, pageLayer, false);
        playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        pageLayer.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        videoTouchLayer = new View(this);
        videoTouchLayer.setBackgroundColor(Color.TRANSPARENT);
        videoTouchLayer.setClickable(true);
        pageLayer.addView(videoTouchLayer, new FrameLayout.LayoutParams(-1, -1));

        topShade = new View(this);
        topShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x88000000, 0x00000000}
        ));
        pageLayer.addView(topShade, new FrameLayout.LayoutParams(-1, dp(170), Gravity.TOP));

        rightShade = new View(this);
        rightShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00000000, 0x66000000}
        ));
        FrameLayout.LayoutParams rightShadeParams = new FrameLayout.LayoutParams(dp(130), -1, Gravity.RIGHT);
        pageLayer.addView(rightShade, rightShadeParams);

        swipePreviewPage = buildSwipePreviewPage();
        swipePreviewPage.setVisibility(View.GONE);
        root.addView(swipePreviewPage, new FrameLayout.LayoutParams(-1, -1));
        root.addView(pageLayer, new FrameLayout.LayoutParams(-1, -1));

        danmakuLayer = new DanmakuOverlayView(this);
        pageLayer.addView(danmakuLayer, new FrameLayout.LayoutParams(-1, -1));

        topBar = buildTopBar();
        pageLayer.addView(topBar, new FrameLayout.LayoutParams(-1, dp(88), Gravity.TOP));

        actionRail = buildActionRail();
        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(dp(72), -2, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        railParams.rightMargin = dp(8);
        pageLayer.addView(actionRail, railParams);
        actionRail.setTranslationY(dp(120));

        bottomInfo = buildBottomInfo();
        pageLayer.addView(bottomInfo, new FrameLayout.LayoutParams(-1, -1));

        lightWatchingView = text("100+人正在看", 15, 0xE6FFFFFF, Typeface.BOLD);
        lightWatchingView.setShadowLayer(5, 0, 1, 0xAA000000);
        lightWatchingView.setVisibility(View.GONE);
        FrameLayout.LayoutParams lightWatchingParams = new FrameLayout.LayoutParams(-2, dp(42), Gravity.LEFT | Gravity.TOP);
        lightWatchingParams.leftMargin = dp(66);
        lightWatchingParams.topMargin = dp(42);
        pageLayer.addView(lightWatchingView, lightWatchingParams);

        lightControls = buildLightControls();
        lightControls.setVisibility(View.GONE);
        pageLayer.addView(lightControls, new FrameLayout.LayoutParams(-1, -1));

        commentsPanel = buildCommentsPanel();
        hideComments();
        int panelHeight = commentsHalfHeight();
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, panelHeight, Gravity.BOTTOM);
        root.addView(commentsPanel, panelParams);

        centerOverlay = text("暂停", 26, Color.WHITE, Typeface.BOLD);
        centerOverlay.setGravity(Gravity.CENTER);
        centerOverlay.setVisibility(View.GONE);
        centerOverlay.setBackground(rounded(0x66000000, dp(32)));
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(dp(190), dp(76), Gravity.CENTER);
        root.addView(centerOverlay, overlayParams);

        progressBubbleCard = new FrameLayout(this);
        progressBubbleCard.setVisibility(View.GONE);
        progressBubbleCard.setBackground(rounded(0xCC111318, dp(10)));
        progressBubbleCover = new VideoShotPreviewView(this);
        progressBubbleCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        progressBubbleCard.addView(progressBubbleCover, new FrameLayout.LayoutParams(-1, dp(82), Gravity.TOP));
        progressBubble = pillText("00:00 / 00:00", 13, Color.WHITE, 0x99000000);
        progressBubble.setGravity(Gravity.CENTER);
        progressBubbleCard.addView(progressBubble, new FrameLayout.LayoutParams(-1, dp(30), Gravity.BOTTOM));
        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(dp(156), dp(112));
        bubbleParams.leftMargin = dp(16);
        bubbleParams.topMargin = dp(120);
        root.addView(progressBubbleCard, bubbleParams);

        fullscreenButton = new DisplayModeIconButton(this);
        fullscreenButton.setContentDescription("全屏/清屏切换");
        fullscreenButton.setOnClickListener(v -> toggleVideoDisplayMode());
        FrameLayout.LayoutParams fullParams = new FrameLayout.LayoutParams(dp(56), dp(46), Gravity.RIGHT | Gravity.BOTTOM);
        fullParams.rightMargin = dp(18);
        fullParams.bottomMargin = dp(38);
        pageLayer.addView(fullscreenButton, fullParams);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent event) {
                if (SystemClock.uptimeMillis() < suppressSingleTapUntilMs) return true;
                if (landscapeMode) {
                    toggleLandscapeControls();
                } else {
                    togglePlayPause();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent event) {
                if (landscapeMode) {
                    togglePlayPause();
                    return true;
                }
                suppressSingleTapUntilMs = SystemClock.uptimeMillis() + 520L;
                showDoubleLike(event.getX(), event.getY());
                likeCurrentFromGesture(false);
                return true;
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent event) {
                return true;
            }

            @Override
            public void onLongPress(MotionEvent event) {
                if (touchMovedBeyondTapSlop || swipeDragging || landscapeProgressDragging || switchAnimating) return;
                startFastForward();
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                return false;
            }
        });

        playerView.setOnTouchListener((view, event) -> handleVideoTouch(event));
        videoTouchLayer.setOnTouchListener((view, event) -> handleVideoTouch(event));

        setContentView(root);
        root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                applyResponsivePortraitLayout();
            }
        });
        root.post(this::applyResponsivePortraitLayout);
    }

    private FrameLayout buildSwipePreviewPage() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.BLACK);

        swipePreviewView = new ImageView(this);
        swipePreviewView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        swipePreviewView.setBackgroundColor(Color.BLACK);
        page.addView(swipePreviewView, new FrameLayout.LayoutParams(-1, -1));

        swipePreviewTopShade = new View(this);
        swipePreviewTopShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xAA000000, 0x00000000}
        ));
        page.addView(swipePreviewTopShade, new FrameLayout.LayoutParams(-1, dp(170), Gravity.TOP));

        swipePreviewRightShade = new View(this);
        swipePreviewRightShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00000000, 0x77000000}
        ));
        page.addView(swipePreviewRightShade, new FrameLayout.LayoutParams(dp(132), -1, Gravity.RIGHT));

        swipePreviewTopBar = buildSwipePreviewTopBar();
        page.addView(swipePreviewTopBar, new FrameLayout.LayoutParams(-1, dp(88), Gravity.TOP));

        swipePreviewDanmaku = new LinearLayout(this);
        swipePreviewDanmaku.setOrientation(LinearLayout.VERTICAL);
        swipePreviewDanmaku.setPadding(dp(18), 0, dp(88), 0);
        FrameLayout.LayoutParams dmParams = new FrameLayout.LayoutParams(-1, dp(210), Gravity.TOP);
        dmParams.topMargin = dp(124);
        page.addView(swipePreviewDanmaku, dmParams);

        swipePreviewRail = buildSwipePreviewMatchedRail();
        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(dp(72), -2, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        railParams.rightMargin = dp(8);
        page.addView(swipePreviewRail, railParams);

        swipePreviewBottomInfo = buildSwipePreviewMatchedBottomInfo();
        page.addView(swipePreviewBottomInfo, new FrameLayout.LayoutParams(-1, -1));

        swipePreviewFullscreenButton = new DisplayModeIconButton(this);
        swipePreviewFullscreenButton.setClickable(false);
        FrameLayout.LayoutParams fullParams = new FrameLayout.LayoutParams(dp(56), dp(46), Gravity.RIGHT | Gravity.BOTTOM);
        fullParams.rightMargin = dp(18);
        fullParams.bottomMargin = dp(38);
        page.addView(swipePreviewFullscreenButton, fullParams);
        return page;
    }

    private FrameLayout buildSwipePreviewTopBar() {
        FrameLayout top = new FrameLayout(this);
        top.setBackgroundColor(0x00000000);

        swipePreviewBackButton = text("‹", 38, Color.WHITE, Typeface.NORMAL);
        swipePreviewBackButton.setGravity(Gravity.CENTER);
        swipePreviewBackButton.setClickable(false);
        top.addView(swipePreviewBackButton, new FrameLayout.LayoutParams(-2, -1));

        swipePreviewPeopleIcon = iconImage(R.drawable.ic_bili_audience, "audience");
        top.addView(swipePreviewPeopleIcon, new FrameLayout.LayoutParams(-2, -2));

        swipePreviewWatching = text("正在看", 15, 0xF2FFFFFF, Typeface.BOLD);
        swipePreviewWatching.setGravity(Gravity.CENTER_VERTICAL);
        swipePreviewWatching.setShadowLayer(5, 0, 1, 0xAA000000);
        top.addView(swipePreviewWatching, new FrameLayout.LayoutParams(-2, -1));

        swipePreviewSearchButton = iconImage(R.drawable.ic_bili_search_clean, "search");
        swipePreviewSearchButton.setClickable(false);
        top.addView(swipePreviewSearchButton, new FrameLayout.LayoutParams(-2, -1));

        swipePreviewMenuButton = iconImage(R.drawable.ic_bili_more, "more");
        swipePreviewMenuButton.setColorFilter(Color.WHITE);
        swipePreviewMenuButton.setClickable(false);
        top.addView(swipePreviewMenuButton, new FrameLayout.LayoutParams(-2, -1));
        return top;
    }

    private LinearLayout buildSwipePreviewMatchedRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER);

        swipePreviewLikeButton = previewRailButton(R.drawable.ic_rail_like);
        swipePreviewCommentButton = previewRailButton(R.drawable.ic_bili_rail_comment);
        swipePreviewCommentButton.setStateIcons(R.drawable.ic_bili_rail_comment, 0);
        swipePreviewCoinButton = previewRailButton(R.drawable.ic_bili_rail_coin);
        swipePreviewCoinButton.setStateIcons(R.drawable.ic_bili_rail_coin, R.drawable.ic_bili_rail_coin_active);
        swipePreviewFavoriteButton = previewRailButton(R.drawable.ic_bili_rail_favorite);
        swipePreviewFavoriteButton.setStateIcons(R.drawable.ic_bili_rail_favorite, R.drawable.ic_bili_rail_favorite_active);
        swipePreviewShareButton = previewRailButton(R.drawable.ic_rail_share);
        rail.addView(swipePreviewLikeButton);
        rail.addView(swipePreviewCommentButton);
        rail.addView(swipePreviewCoinButton);
        rail.addView(swipePreviewFavoriteButton);
        rail.addView(swipePreviewShareButton);
        return rail;
    }

    private RailActionButton previewRailButton(int iconRes) {
        RailActionButton button = railButton(iconRes, "", "0");
        button.setClickable(false);
        button.setFocusable(false);
        return button;
    }

    private FrameLayout buildSwipePreviewMatchedBottomInfo() {
        FrameLayout bottom = new FrameLayout(this);

        swipePreviewBottomShade = new View(this);
        swipePreviewBottomShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xDD000000}
        ));
        bottom.addView(swipePreviewBottomShade, new FrameLayout.LayoutParams(-1, -1));

        swipePreviewOwnerGroup = new FrameLayout(this);
        swipePreviewAvatar = new ImageView(this);
        swipePreviewAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        swipePreviewAvatar.setBackground(rounded(0x66FFFFFF, dp(24)));
        makeCircular(swipePreviewAvatar);
        swipePreviewOwnerGroup.addView(swipePreviewAvatar, new FrameLayout.LayoutParams(-2, -2));

        swipePreviewOwnerTexts = new LinearLayout(this);
        swipePreviewOwnerTexts.setOrientation(LinearLayout.VERTICAL);
        swipePreviewOwner = text("", 16, Color.WHITE, Typeface.BOLD);
        swipePreviewOwner.setSingleLine(true);
        swipePreviewOwner.setIncludeFontPadding(false);
        swipePreviewOwner.setShadowLayer(5, 0, 1, 0xAA000000);
        swipePreviewOwnerTexts.addView(swipePreviewOwner, new LinearLayout.LayoutParams(-2, -2));
        swipePreviewFans = text("", 12, 0xD9FFFFFF, Typeface.NORMAL);
        swipePreviewFans.setSingleLine(true);
        swipePreviewFans.setEllipsize(TextUtils.TruncateAt.END);
        swipePreviewFans.setIncludeFontPadding(false);
        swipePreviewFans.setShadowLayer(4, 0, 1, 0x99000000);
        LinearLayout.LayoutParams fansParams = new LinearLayout.LayoutParams(-2, -2);
        fansParams.topMargin = dp(1);
        swipePreviewOwnerTexts.addView(swipePreviewFans, fansParams);
        swipePreviewOwnerGroup.addView(swipePreviewOwnerTexts, new FrameLayout.LayoutParams(-2, -2));

        swipePreviewFollowButton = pillText("+ 关注", 15, Color.WHITE, BILI_PINK);
        swipePreviewFollowButton.setGravity(Gravity.CENTER);
        swipePreviewFollowButton.setClickable(false);
        swipePreviewOwnerGroup.addView(swipePreviewFollowButton, new FrameLayout.LayoutParams(-2, -2));
        bottom.addView(swipePreviewOwnerGroup, new FrameLayout.LayoutParams(-2, -2));

        swipePreviewTitle = text("", 16, Color.WHITE, Typeface.BOLD);
        swipePreviewTitle.setSingleLine(true);
        swipePreviewTitle.setEllipsize(TextUtils.TruncateAt.END);
        swipePreviewTitle.setIncludeFontPadding(false);
        swipePreviewTitle.setShadowLayer(5, 0, 1, 0xAA000000);
        bottom.addView(swipePreviewTitle, new FrameLayout.LayoutParams(-2, -2));

        swipePreviewMeta = text("", 13, 0xE6FFFFFF, Typeface.NORMAL);
        swipePreviewMeta.setSingleLine(true);
        swipePreviewMeta.setEllipsize(TextUtils.TruncateAt.END);
        swipePreviewMeta.setIncludeFontPadding(false);
        swipePreviewMeta.setShadowLayer(4, 0, 1, 0x99000000);
        bottom.addView(swipePreviewMeta, new FrameLayout.LayoutParams(-2, -2));

        swipePreviewNotice = text("ⓘ 个人观点，仅供参考", 12, 0xB8FFFFFF, Typeface.NORMAL);
        swipePreviewNotice.setMaxLines(1);
        swipePreviewNotice.setVisibility(View.GONE);
        swipePreviewNotice.setShadowLayer(4, 0, 1, 0x99000000);
        bottom.addView(swipePreviewNotice, new FrameLayout.LayoutParams(-2, -2));

        swipePreviewSearch = pillText("", 15, 0xFFECEDEE, 0xDD141517);
        swipePreviewSearch.setGravity(Gravity.CENTER_VERTICAL);
        swipePreviewSearch.setPadding(dp(16), 0, dp(14), 0);
        swipePreviewSearch.setVisibility(View.GONE);
        bottom.addView(swipePreviewSearch, new FrameLayout.LayoutParams(-2, -2));

        swipePreviewProgressBar = new FrameLayout(this);
        swipePreviewProgressBar.setBackgroundColor(Color.TRANSPARENT);
        View progressTrack = new View(this);
        progressTrack.setBackgroundColor(0x66FFFFFF);
        swipePreviewProgressBar.addView(progressTrack, new FrameLayout.LayoutParams(-1, dp(3), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        swipePreviewProgressFill = new View(this);
        swipePreviewProgressFill.setBackgroundColor(BILI_PINK);
        swipePreviewProgressBar.addView(swipePreviewProgressFill, new FrameLayout.LayoutParams(1, dp(3), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        bottom.addView(swipePreviewProgressBar, new FrameLayout.LayoutParams(-2, -2));

        swipePreviewDetailPageButton = buildDetailPageButton(false);
        bottom.addView(swipePreviewDetailPageButton, new FrameLayout.LayoutParams(-2, -2));

        swipePreviewInputRow = new FrameLayout(this);
        swipePreviewDanmakuInputPill = pillText("发弹幕", 14, 0xFFE1E1E1, 0xAA55575B);
        swipePreviewDanmakuInputPill.setGravity(Gravity.CENTER_VERTICAL);
        swipePreviewDanmakuInputPill.setPadding(dp(16), 0, dp(16), 0);
        swipePreviewDanmakuInputPill.setClickable(false);
        swipePreviewInputRow.addView(swipePreviewDanmakuInputPill, new FrameLayout.LayoutParams(-1, -1));

        swipePreviewDanmakuInputDivider = new View(this);
        swipePreviewDanmakuInputDivider.setBackgroundColor(0x66D6D8DE);
        swipePreviewInputRow.addView(swipePreviewDanmakuInputDivider, new FrameLayout.LayoutParams(1, 1));

        swipePreviewDanmakuButton = iconImage(R.drawable.ic_bili_danmaku_on, "弹幕开关");
        swipePreviewDanmakuButton.setClickable(false);
        swipePreviewInputRow.addView(swipePreviewDanmakuButton, new FrameLayout.LayoutParams(-2, -2));

        bottom.addView(swipePreviewInputRow, new FrameLayout.LayoutParams(-2, -2));
        return bottom;
    }

    private boolean handleVideoTouch(MotionEvent event) {
        if (commentsPanel.getVisibility() == View.VISIBLE) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                hideComments();
            }
            return true;
        }
        if (switchAnimating) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                hideDanmakuActionBox();
                recycleSwipeVelocityTracker();
                swipeVelocityTracker = VelocityTracker.obtain();
                swipeVelocityTracker.addMovement(event);
                touchDownX = event.getRawX();
                touchDownY = event.getRawY();
                touchDownTimeMs = SystemClock.uptimeMillis();
                lastDragY = 0;
                swipeDragging = false;
                landscapeProgressDragging = false;
                landscapeVerticalAdjusting = false;
                landscapeVerticalAdjustMode = 0;
                landscapeProgressStartPositionMs = -1L;
                landscapeProgressLastX = Float.NaN;
                touchMovedBeyondTapSlop = false;
                cancelSwipeChromeAnimation();
                pageLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                swipePreviewPage.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                gestureDetector.onTouchEvent(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (swipeVelocityTracker != null) swipeVelocityTracker.addMovement(event);
                float dx = event.getRawX() - touchDownX;
                float dy = event.getRawY() - touchDownY;
                if (Math.hypot(dx, dy) > dp(10)) {
                    touchMovedBeyondTapSlop = true;
                }
                if (landscapeMode) {
                    if (landscapeProgressDragging) {
                        previewLandscapeProgressAt(event.getRawX());
                        return true;
                    }
                    if (landscapeVerticalAdjusting) {
                        updateLandscapeVerticalAdjust(dy);
                        return true;
                    }
                    if (Math.abs(dy) > dp(22)
                            && Math.abs(dy) > Math.abs(dx) * 1.15f) {
                        startLandscapeVerticalAdjust();
                        updateLandscapeVerticalAdjust(dy);
                        MotionEvent cancelEvent = MotionEvent.obtain(event.getDownTime(), event.getEventTime(), MotionEvent.ACTION_CANCEL, event.getX(), event.getY(), event.getMetaState());
                        gestureDetector.onTouchEvent(cancelEvent);
                        cancelEvent.recycle();
                        return true;
                    }
                    if (!landscapeProgressDragging
                            && Math.abs(dx) > dp(22)
                            && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                        landscapeProgressDragging = true;
                        landscapeProgressStartX = touchDownX;
                        landscapeProgressLastX = touchDownX;
                        landscapeProgressStartPositionMs = player == null ? -1L : player.getCurrentPosition();
                        stopFastForward();
                        hideDanmakuActionBox();
                        setLightControlsVisible(true);
                        uiHandler.removeCallbacks(hideLightControlsRunnable);
                        MotionEvent cancelEvent = MotionEvent.obtain(event.getDownTime(), event.getEventTime(), MotionEvent.ACTION_CANCEL, event.getX(), event.getY(), event.getMetaState());
                        gestureDetector.onTouchEvent(cancelEvent);
                        cancelEvent.recycle();
                        previewLandscapeProgressAt(event.getRawX());
                        return true;
                    }
                    gestureDetector.onTouchEvent(event);
                    return true;
                }
                if (!swipeDragging && Math.abs(dy) > dp(18)) {
                    swipeDragging = true;
                    stopFastForward();
                    hideDanmakuActionBox();
                    MotionEvent cancelEvent = MotionEvent.obtain(event.getDownTime(), event.getEventTime(), MotionEvent.ACTION_CANCEL, event.getX(), event.getY(), event.getMetaState());
                    gestureDetector.onTouchEvent(cancelEvent);
                    cancelEvent.recycle();
                }
                if (swipeDragging) {
                    lastDragY = dampSwipeDistance(dy);
                    updateSwipePreview(lastDragY);
                    setSwipeChromeTranslation(lastDragY);
                    return true;
                }
                gestureDetector.onTouchEvent(event);
                return true;
            case MotionEvent.ACTION_UP:
                if (swipeVelocityTracker != null) swipeVelocityTracker.addMovement(event);
                float velocityY = currentSwipeVelocityY();
                recycleSwipeVelocityTracker();
                stopFastForward();
                if (landscapeProgressDragging) {
                    commitLandscapeProgressAt(event.getRawX());
                    landscapeProgressDragging = false;
                    landscapeProgressLastX = Float.NaN;
                    updateSeekTvEyes(0f, true);
                    hideProgressBubbleLater(800);
                    scheduleLightControlsHide(1100);
                    resetSwipeChrome();
                    pageLayer.setLayerType(View.LAYER_TYPE_NONE, null);
                    swipePreviewPage.setLayerType(View.LAYER_TYPE_NONE, null);
                    return true;
                }
                if (landscapeVerticalAdjusting) {
                    landscapeVerticalAdjusting = false;
                    landscapeVerticalAdjustMode = 0;
                    resetSwipeChrome();
                    pageLayer.setLayerType(View.LAYER_TYPE_NONE, null);
                    swipePreviewPage.setLayerType(View.LAYER_TYPE_NONE, null);
                    return true;
                }
                if (swipeDragging) {
                    finishSwipe(lastDragY, velocityY);
                    swipeDragging = false;
                    return true;
                }
                gestureDetector.onTouchEvent(event);
                resetSwipeChrome();
                pageLayer.setLayerType(View.LAYER_TYPE_NONE, null);
                swipePreviewPage.setLayerType(View.LAYER_TYPE_NONE, null);
                return true;
            case MotionEvent.ACTION_CANCEL:
                recycleSwipeVelocityTracker();
                stopFastForward();
                if (landscapeProgressDragging) {
                    hideProgressBubbleLater(0);
                    landscapeProgressDragging = false;
                    landscapeProgressLastX = Float.NaN;
                    updateSeekTvEyes(0f, true);
                    scheduleLightControlsHide(0);
                    resetSwipeChrome();
                    pageLayer.setLayerType(View.LAYER_TYPE_NONE, null);
                    swipePreviewPage.setLayerType(View.LAYER_TYPE_NONE, null);
                    return true;
                }
                if (landscapeVerticalAdjusting) {
                    landscapeVerticalAdjusting = false;
                    landscapeVerticalAdjustMode = 0;
                    resetSwipeChrome();
                    pageLayer.setLayerType(View.LAYER_TYPE_NONE, null);
                    swipePreviewPage.setLayerType(View.LAYER_TYPE_NONE, null);
                    return true;
                }
                if (swipeDragging) {
                    animateSwipeBack(180, null);
                    swipeDragging = false;
                    return true;
                }
                resetSwipeChrome();
                pageLayer.setLayerType(View.LAYER_TYPE_NONE, null);
                swipePreviewPage.setLayerType(View.LAYER_TYPE_NONE, null);
                return true;
            default:
                return true;
        }
    }

    private float dampSwipeDistance(float dy) {
        float height = Math.max(1, root.getHeight());
        float clamped = Math.max(-height, Math.min(height, dy));
        if (clamped > 0 && previousStack.isEmpty()) {
            return clamped * 0.38f;
        }
        return clamped;
    }

    private float currentSwipeVelocityY() {
        if (swipeVelocityTracker == null) return 0f;
        swipeVelocityTracker.computeCurrentVelocity(1000);
        return swipeVelocityTracker.getYVelocity();
    }

    private void recycleSwipeVelocityTracker() {
        if (swipeVelocityTracker == null) return;
        swipeVelocityTracker.recycle();
        swipeVelocityTracker = null;
    }

    private void startLandscapeVerticalAdjust() {
        landscapeVerticalAdjusting = true;
        landscapeVerticalAdjustMode = touchDownX < Math.max(1, root.getWidth()) / 2f ? 1 : 2;
        stopFastForward();
        hideDanmakuActionBox();
        if (landscapeVerticalAdjustMode == 1) {
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            landscapeGestureStartBrightness = attrs.screenBrightness >= 0f ? attrs.screenBrightness : 0.5f;
        } else if (audioManager != null) {
            landscapeGestureStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
    }

    private void updateLandscapeVerticalAdjust(float dy) {
        int height = Math.max(1, root == null ? getResources().getDisplayMetrics().heightPixels : root.getHeight());
        float delta = -dy / height;
        if (landscapeVerticalAdjustMode == 1) {
            float brightness = Math.max(0.02f, Math.min(1f, landscapeGestureStartBrightness + delta * 1.15f));
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.screenBrightness = brightness;
            getWindow().setAttributes(attrs);
            showCenter("亮度 " + Math.round(brightness * 100f) + "%");
            return;
        }
        if (landscapeVerticalAdjustMode == 2 && audioManager != null) {
            int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int target = Math.max(0, Math.min(max, landscapeGestureStartVolume + Math.round(delta * max * 1.25f)));
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            showCenter("音量 " + Math.round(target * 100f / max) + "%");
        }
    }

    private void finishSwipe(float dy, float velocityY) {
        float distanceThreshold = Math.max(dp(118), root.getHeight() * 0.20f);
        boolean shouldSwitch = Math.abs(dy) >= distanceThreshold || Math.abs(velocityY) > SWIPE_SWITCH_VELOCITY_THRESHOLD;
        if (!shouldSwitch) {
            animateSwipeBack(220, null);
            return;
        }
        boolean forward = dy < 0;
        if (!forward && previousStack.isEmpty()) {
            animateSwipeBack(220, () -> showCenter("已经是第一条"));
            return;
        }
        switchAnimating = true;
        int target = forward ? -root.getHeight() : root.getHeight();
        animateSwipePreviewTo(0, 320);
        animateSwipeChromeTo(target, 320, () -> {
            setSwipeChromeAlpha(0f);
            resetSwipeChromeTranslationOnly();
            holdSwipePreviewUntilReady = true;
            if (forward) {
                playNext();
            } else {
                playPrevious();
            }
        });
    }

    private void releaseHeldSwipePreview() {
        if (!holdSwipePreviewUntilReady) return;
        holdSwipePreviewUntilReady = false;
        uiHandler.removeCallbacks(releaseHeldSwipePreviewRunnable);
        pageLayer.animate().cancel();
        pageLayer.animate()
                .alpha(1f)
                .setDuration(140)
                .withEndAction(() -> {
                    hideSwipePreview();
                    endSwipeLayerBoost();
                    switchAnimating = false;
                })
                .start();
    }

    private void cancelHeldSwipePreview() {
        holdSwipePreviewUntilReady = false;
        uiHandler.removeCallbacks(releaseHeldSwipePreviewRunnable);
        pageLayer.animate().cancel();
        hideSwipePreview();
        resetSwipeChrome();
        endSwipeLayerBoost();
        switchAnimating = false;
    }

    private void endSwipeLayerBoost() {
        pageLayer.setLayerType(View.LAYER_TYPE_NONE, null);
        swipePreviewPage.setLayerType(View.LAYER_TYPE_NONE, null);
    }

    private void cancelSwipeChromeAnimation() {
        pageLayer.animate().cancel();
        swipePreviewPage.animate().cancel();
    }

    private void updateSwipePreview(float dy) {
        boolean forward = dy < 0;
        PrefetchedVideo previewVideo = forward ? peekForwardPreview() : previousStack.peekLast();
        if (forward && previewVideo == null) prefetchNext();
        if (swipePreviewPage.getVisibility() != View.VISIBLE || forward != swipePreviewForward) {
            swipePreviewForward = forward;
            swipePreviewPage.setVisibility(View.VISIBLE);
            showSwipePreviewItem(previewVideo);
        } else {
            String key = previewVideo == null || previewVideo.item == null ? "" : videoIdentity(previewVideo.item);
            if (!key.equals(swipePreviewVideoKey)) showSwipePreviewItem(previewVideo);
        }
        float base = forward ? root.getHeight() : -root.getHeight();
        swipePreviewPage.setTranslationY(base + dy);
    }

    private void showSwipePreviewItem(PrefetchedVideo video) {
        swipePreviewView.setBackgroundColor(Color.BLACK);
        if (video == null || video.item == null) {
            swipePreviewItem = null;
            swipePreviewVideoKey = "";
            swipePreviewView.setImageDrawable(null);
            swipePreviewView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (swipePreviewAvatar != null) swipePreviewAvatar.setImageDrawable(null);
            swipePreviewWatching.setText("准备中");
            swipePreviewOwner.setText("正在准备下一条");
            swipePreviewFans.setText("");
            swipePreviewTitle.setText("松手后加载");
            swipePreviewMeta.setText("");
            if (swipePreviewNotice != null) swipePreviewNotice.setVisibility(View.GONE);
            if (swipePreviewSearch != null) swipePreviewSearch.setVisibility(View.GONE);
            if (swipePreviewProgressFill != null) updateProgressFill(swipePreviewProgressFill, 0L, 1L);
            updateDisplayModeButtonIcon(swipePreviewFullscreenButton, null, false);
            swipePreviewRail.removeAllViews();
            swipePreviewDanmaku.removeAllViews();
            return;
        }
        FeedItem item = video.item;
        swipePreviewItem = item;
        swipePreviewVideoKey = videoIdentity(item);
        applyVideoViewportLayout(swipePreviewView, item);
        loadFirstFrameInto(video, swipePreviewView);
        if (swipePreviewAvatar != null) loadImageInto(item.ownerFace, swipePreviewAvatar);
        swipePreviewWatching.setText(watchingText(item));
        swipePreviewOwner.setText(fallback(item.ownerName, "未知 UP"));
        swipePreviewFans.setText(item.ownerFollowerCount > 0 ? formatCount(item.ownerFollowerCount) + "粉丝" : "净流推荐");
        swipePreviewTitle.setText(fallback(item.title, "准备下一条"));
        swipePreviewMeta.setText(formatCount(item.viewCount) + "播放⌄");
        refreshOwnerFollowPosition(swipePreviewOwnerTexts, swipePreviewFollowButton);
        if (swipePreviewNotice != null) {
            swipePreviewNotice.setVisibility(View.GONE);
            swipePreviewNotice.setText("");
        }
        if (swipePreviewProgressFill != null) updateProgressFill(swipePreviewProgressFill, 0L, Math.max(1L, item.durationSeconds * 1000L));
        updateDisplayModeButtonIcon(swipePreviewFullscreenButton, item, false);
        updateSearchEntrance(swipePreviewSearch, item, false);
        renderSwipePreviewRail(item);
        swipePreviewDanmaku.removeAllViews();
    }

    private void renderSwipePreviewRail(FeedItem item) {
        swipePreviewRail.removeAllViews();
        if (swipePreviewLikeButton == null
                || swipePreviewCommentButton == null
                || swipePreviewCoinButton == null
                || swipePreviewFavoriteButton == null
                || swipePreviewShareButton == null) {
            swipePreviewRail.addView(previewRailAction("👍", formatCount(item.likeCount)));
            swipePreviewRail.addView(previewRailAction("■", formatCount(item.replyCount)));
            swipePreviewRail.addView(previewRailAction(R.drawable.ic_bili_rail_coin, formatCount(item.coinCount)));
            swipePreviewRail.addView(previewRailAction("★", formatCount(item.favoriteCount)));
            swipePreviewRail.addView(previewRailAction("↗", formatCount(item.shareCount)));
            return;
        }
        swipePreviewLikeButton.setCount(formatCount(item.likeCount));
        swipePreviewLikeButton.setIconColor(item.liked ? BILI_PINK : Color.WHITE);
        swipePreviewCommentButton.setCount(formatCount(item.replyCount));
        swipePreviewCommentButton.setIconColor(Color.WHITE);
        swipePreviewCoinButton.setCount(formatCount(item.coinCount));
        swipePreviewCoinButton.setIconColor(Color.WHITE);
        swipePreviewFavoriteButton.setCount(formatCount(item.favoriteCount));
        swipePreviewFavoriteButton.setIconColor(Color.WHITE);
        swipePreviewShareButton.setCount(formatCount(item.shareCount));
        swipePreviewShareButton.setIconColor(Color.WHITE);
        swipePreviewRail.addView(swipePreviewLikeButton);
        swipePreviewRail.addView(swipePreviewCommentButton);
        swipePreviewRail.addView(swipePreviewCoinButton);
        swipePreviewRail.addView(swipePreviewFavoriteButton);
        swipePreviewRail.addView(swipePreviewShareButton);
    }

    private TextView previewRailAction(String icon, String count) {
        TextView view = text(icon + "\n" + count, 24, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setShadowLayer(6, 0, 2, 0xAA000000);
        view.setIncludeFontPadding(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(64), dp(72));
        params.bottomMargin = dp(4);
        view.setLayoutParams(params);
        return view;
    }

    private View previewRailAction(int iconRes, String count) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        if (iconRes == R.drawable.ic_bili_rail_coin
                || iconRes == R.drawable.ic_bili_rail_comment
                || iconRes == R.drawable.ic_bili_rail_favorite) {
            icon.clearColorFilter();
        } else {
            icon.setColorFilter(Color.WHITE);
        }
        box.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView countView = text(count, 13, Color.WHITE, Typeface.BOLD);
        countView.setGravity(Gravity.CENTER);
        countView.setShadowLayer(6, 0, 2, 0xAA000000);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, dp(24));
        countParams.topMargin = dp(2);
        box.addView(countView, countParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(64), dp(72));
        params.bottomMargin = dp(4);
        box.setLayoutParams(params);
        return box;
    }

    private void renderSwipePreviewDanmaku(FeedItem item) {
        swipePreviewDanmaku.removeAllViews();
    }

    private PrefetchedVideo peekForwardPreview() {
        PrefetchedVideo next;
        synchronized (prefetchLock) {
            next = peekNextReadyLocked();
        }
        if (next == null) {
            FeedItem queued = repository.peek();
            if (queued != null) next = new PrefetchedVideo(queued, new PlayInfo());
        }
        return next;
    }

    private void animateSwipePreviewTo(float translationY, long duration) {
        if (swipePreviewPage.getVisibility() == View.VISIBLE) {
            swipePreviewPage.animate()
                    .translationY(translationY)
                    .setInterpolator(swipeInterpolator)
                    .setDuration(duration)
                    .start();
        }
    }

    private void animateSwipeBack(long duration, Runnable endAction) {
        float previewTarget = swipePreviewForward ? root.getHeight() : -root.getHeight();
        animateSwipePreviewTo(previewTarget, duration);
        animateSwipeChromeTo(0, duration, () -> {
            hideSwipePreview();
            endSwipeLayerBoost();
            if (endAction != null) endAction.run();
        });
    }

    private void hideSwipePreview() {
        swipePreviewPage.setVisibility(View.GONE);
        swipePreviewView.setImageDrawable(null);
        swipePreviewItem = null;
        swipePreviewVideoKey = "";
        swipePreviewPage.setTranslationY(0);
        swipePreviewPage.setAlpha(1f);
    }

    private void setSwipeChromeTranslation(float translationY) {
        pageLayer.setTranslationY(translationY);
    }

    private void animateSwipeChromeTo(float translationY, long duration, Runnable endAction) {
        pageLayer.animate()
                .translationY(translationY)
                .setInterpolator(swipeInterpolator)
                .setDuration(duration)
                .withEndAction(endAction)
                .start();
    }

    private void resetSwipeChrome() {
        resetSwipeChromeTranslationOnly();
        setSwipeChromeAlpha(1f);
    }

    private void resetSwipeChromeTranslationOnly() {
        setSwipeChromeTranslation(0);
    }

    private void setSwipeChromeAlpha(float alpha) {
        pageLayer.setAlpha(alpha);
    }

    private void applyResponsivePortraitLayout() {
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        if (landscapeMode) return;

        applyVideoResizeMode();
        applyVideoViewportLayout(swipePreviewView, swipePreviewItem);

        setFrame(topShade, docFrame(0f, DESIGN_STATUS_BOTTOM_PCT, 100f, 11.5f));
        setFrame(rightShade, docFrame(85.6f, 4.95f, 14.4f, 91.27f));
        setFrame(swipePreviewTopShade, docFrame(0f, DESIGN_STATUS_BOTTOM_PCT, 100f, 11.5f));
        setFrame(swipePreviewRightShade, docFrame(85.6f, 4.95f, 14.4f, 91.27f));

        applyTopBarLayout(topBar, topBackButton, topPeopleIcon, watchingView, topSearchButton, topMenuButton);
        applyTopBarLayout(swipePreviewTopBar, swipePreviewBackButton, swipePreviewPeopleIcon,
                swipePreviewWatching, swipePreviewSearchButton, swipePreviewMenuButton);

        applyRailLayout(actionRail, likeButton, commentButton, coinButton, favoriteButton, shareButton);
        applyRailLayout(swipePreviewRail, swipePreviewLikeButton, swipePreviewCommentButton,
                swipePreviewCoinButton, swipePreviewFavoriteButton, swipePreviewShareButton);

        applyBottomOverlayLayout(bottomInfo, bottomShade, ownerInfoGroup, ownerAvatarView, ownerTextStack,
                ownerFollowButton, titleView, metaView, noticeView, progressBar, progressFill,
                searchSuggestView, bottomInputRow, danmakuInputPill, danmakuButton, detailPageButton);
        applyBottomOverlayLayout(swipePreviewBottomInfo, swipePreviewBottomShade, swipePreviewOwnerGroup,
                swipePreviewAvatar, swipePreviewOwnerTexts, swipePreviewFollowButton, swipePreviewTitle,
                swipePreviewMeta, swipePreviewNotice, swipePreviewProgressBar, swipePreviewProgressFill,
                swipePreviewSearch, swipePreviewInputRow, swipePreviewDanmakuInputPill, swipePreviewDanmakuButton,
                swipePreviewDetailPageButton);

        setFrame(fullscreenButton, effectiveDocFrame(90.08f, 95.01f, 5.72f, 2.70f));
        setFrame(swipePreviewFullscreenButton, effectiveDocFrame(90.08f, 95.01f, 5.72f, 2.70f));

        if (swipePreviewDanmaku != null) {
            swipePreviewDanmaku.setPadding(docWidth(3.33f), 0, docWidth(12.8f), 0);
            setFrame(swipePreviewDanmaku, docFrame(0f, 10.6f, 100f, 31f));
        }
    }

    private void applyTopBarLayout(FrameLayout bar, TextView back, View people,
                                   TextView watching, View search, View menu) {
        if (bar == null) return;
        setFrame(bar, docFrame(0f, 4.95f, 100f, 4.82f));
        setFrame(back, relativeDocFrame(2.60f, 4.95f, 8.39f, 4.82f, 0f, 4.95f));
        setFrame(people, relativeDocFrame(14.18f, 6.35f, 4.15f, 2.33f, 0f, 4.95f));
        setFrame(watching, relativeDocFrame(18.34f, 5.83f, 27.2f, 3.12f, 0f, 4.95f));
        setFrame(search, relativeDocFrame(78.05f, 5.87f, 6.25f, 3.02f, 0f, 4.95f));
        setFrame(menu, relativeDocFrame(91.05f, 6.49f, 5.20f, 2.05f, 0f, 4.95f));
    }

    private void applyRailLayout(LinearLayout rail, RailActionButton like, RailActionButton comment,
                                 RailActionButton coin, RailActionButton favorite, RailActionButton share) {
        if (rail == null) return;
        rail.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        rail.setTranslationY(0f);
        rail.setClipChildren(false);
        rail.setClipToPadding(false);
        setFrame(rail, docFrame(87.70f, 45.38f, 8.97f, 40.76f));
        int buttonHeight = docHeight(6.18f);
        int buttonGap = docHeight(2.38f);
        int iconSize = Math.min(docWidth(8.39f), docHeight(3.84f));
        int countHeight = docHeight(1.65f);
        applyRailButtonLayout(like, buttonHeight, buttonGap, iconSize, countHeight);
        applyRailButtonLayout(comment, buttonHeight, buttonGap, iconSize, countHeight);
        applyRailButtonLayout(coin, buttonHeight, buttonGap, iconSize, countHeight);
        applyRailButtonLayout(favorite, buttonHeight, buttonGap, iconSize, countHeight);
        applyRailButtonLayout(share, buttonHeight, 0, iconSize, countHeight);
    }

    private void applyRailButtonLayout(RailActionButton button, int height, int bottomMargin,
                                       int iconSize, int countHeight) {
        if (button == null) return;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height);
        params.bottomMargin = bottomMargin;
        button.setLayoutParams(params);
        button.setResponsiveSizes(iconSize, countHeight, Math.max(0, docHeight(0.25f)));
    }

    private void applyBottomOverlayLayout(FrameLayout overlay, View shade, FrameLayout ownerGroup,
                                          ImageView avatar, LinearLayout ownerTexts, TextView follow,
                                          TextView title, TextView meta, TextView notice,
                                          FrameLayout progress, View fill, TextView search,
                                          FrameLayout inputRow, TextView inputPill, View danmakuToggle,
                                          FrameLayout detailButton) {
        if (overlay == null) return;
        setFrame(shade, docFrame(0f, 66.8f, 100f, 29.42f));
        setFrame(ownerGroup, docFrame(3.33f, 71.94f, 78.0f, 5.27f));
        if (ownerGroup != null) {
            int avatarSize = Math.min(docWidth(11.72f), docHeight(5.27f));
            FrameLayout.LayoutParams avatarParams = new FrameLayout.LayoutParams(avatarSize, avatarSize);
            avatarParams.leftMargin = 0;
            avatarParams.topMargin = 0;
            setFrame(avatar, avatarParams);
            if (avatar != null) avatar.setBackground(rounded(0x66FFFFFF, avatarSize / 2));

            float ownerGroupLeftPct = 3.33f;
            float ownerNameLeftPct = 18.35f;
            float followLeftPct = 31.98f;
            float ownerNameToFollowGapPct = 2.50f;
            FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(-2, -2);
            textParams.leftMargin = docWidth(ownerNameLeftPct - ownerGroupLeftPct);
            textParams.topMargin = docHeight(0.25f);
            setFrame(ownerTexts, textParams);

            FrameLayout.LayoutParams followParams = new FrameLayout.LayoutParams(docWidth(17.22f), docHeight(2.99f));
            followParams.leftMargin = docWidth(followLeftPct - ownerGroupLeftPct);
            followParams.topMargin = docHeight(72.01f - 71.94f);
            setFrame(follow, followParams);
            if (follow != null) follow.setBackground(rounded(BILI_PINK, Math.max(1, followParams.height / 2)));
            positionFollowAfterOwnerName(ownerTexts, follow, textParams.leftMargin,
                    followParams.leftMargin, docWidth(ownerNameToFollowGapPct));
        }

        if (title != null) title.setTextSize(15);
        setFrame(title, docFrameWrapHeight(3.33f, 79.56f, 70.77f));
        setFrame(meta, docFrameWrapHeight(3.47f, 81.75f, 42.0f));
        setFrame(notice, docFrameWrapHeight(3.47f, 85.34f, 70.0f));
        setFrame(search, effectiveDocFrame(3.37f, 87.22f, 81.00f, 3.75f));
        if (search != null) {
            search.setTextSize(15);
            search.setPadding(docWidth(1.85f), 0, docWidth(3.37f), 0);
            search.setBackground(rounded(0xDD141517, Math.max(1, docWidth(1.95f))));
            applySearchEntranceIcon(search);
        }
        setFrame(progress, effectiveDocFrame(3.33f, 92.02f, 93.20f, 1.05f));
        applyProgressTrackLayout(progress, fill);
        setFrame(inputRow, effectiveDocFrame(3.37f, 94.29f, 65.36f, 4.17f));
        if (inputPill != null) {
            inputPill.setTextSize(14);
            inputPill.setPadding(docWidth(3.15f), 0, docWidth(9.5f), 0);
            inputPill.setBackground(rounded(0xAA55575B, Math.max(1, docHeight(2.02f))));
        }
        if (danmakuToggle != null) {
            if (inputRow != null && inputRow.getChildCount() >= 3) {
                View divider = inputRow.getChildAt(1);
                FrameLayout.LayoutParams dividerParams = new FrameLayout.LayoutParams(Math.max(1, docWidth(0.16f)), effectiveDocHeight(2.72f));
                dividerParams.leftMargin = docWidth(57.28f - 3.37f);
                dividerParams.topMargin = effectiveDocHeight(0.74f);
                setFrame(divider, dividerParams);
            }
            FrameLayout.LayoutParams toggleParams = new FrameLayout.LayoutParams(docWidth(7.10f), effectiveDocHeight(3.28f));
            toggleParams.leftMargin = docWidth(59.45f - 3.37f);
            toggleParams.topMargin = effectiveDocHeight(0.44f);
            setFrame(danmakuToggle, toggleParams);
            danmakuToggle.setBackgroundColor(Color.TRANSPARENT);
            if (danmakuToggle instanceof ImageView) {
                ((ImageView) danmakuToggle).setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
        }
        if (detailButton != null) {
            setFrame(detailButton, effectiveDocFrame(75.95f, 93.18f, 7.35f, 4.78f));
            if (detailButton.getChildCount() >= 2) {
                View label = detailButton.getChildAt(0);
                View icon = detailButton.getChildAt(1);
                int labelHeight = effectiveDocHeight(1.62f);
                FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(docWidth(7.35f), labelHeight, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                setFrame(label, labelParams);
                if (label != null) label.setBackground(rounded(0xCC454545, Math.max(1, labelHeight / 2)));
                FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(docWidth(6.65f), effectiveDocHeight(3.22f), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                setFrame(icon, iconParams);
            }
        }
    }

    private void refreshOwnerFollowPosition(LinearLayout ownerTexts, TextView follow) {
        float ownerGroupLeftPct = 3.33f;
        float ownerNameLeftPct = 18.35f;
        float followLeftPct = 31.98f;
        float ownerNameToFollowGapPct = 2.50f;
        positionFollowAfterOwnerName(ownerTexts, follow,
                docWidth(ownerNameLeftPct - ownerGroupLeftPct),
                docWidth(followLeftPct - ownerGroupLeftPct),
                docWidth(ownerNameToFollowGapPct));
    }

    private void positionFollowAfterOwnerName(LinearLayout ownerTexts, TextView follow,
                                              int nameLeft, int fallbackLeft, int gap) {
        if (ownerTexts == null || follow == null || ownerTexts.getChildCount() == 0) return;
        ownerTexts.post(() -> {
            View ownerName = ownerTexts.getChildAt(0);
            int nameWidth = ownerName.getWidth();
            if (nameWidth <= 0) {
                ownerName.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                nameWidth = ownerName.getMeasuredWidth();
            }
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) follow.getLayoutParams();
            int desiredLeft = Math.max(fallbackLeft, nameLeft + nameWidth + gap);
            params.leftMargin = desiredLeft;
            follow.setLayoutParams(params);
        });
    }

    private void applyProgressTrackLayout(FrameLayout progress, View fill) {
        if (progress == null) return;
        int trackHeight = Math.max(1, docHeight(0.26f));
        for (int i = 0; i < progress.getChildCount(); i++) {
            View child = progress.getChildAt(i);
            if (child instanceof SeekTvThumbView) continue;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) child.getLayoutParams();
            params.height = trackHeight;
            params.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
            child.setLayoutParams(params);
        }
        if (fill != null) fill.bringToFront();
        View thumb = progress == progressBar ? progressThumb : null;
        if (thumb != null) thumb.bringToFront();
    }

    private FrameLayout.LayoutParams docFrame(float leftPct, float topPct, float widthPct, float heightPct) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(docWidth(widthPct), docHeight(heightPct));
        params.leftMargin = docX(leftPct);
        params.topMargin = docY(topPct);
        return params;
    }

    private FrameLayout.LayoutParams effectiveDocFrame(float leftPct, float topPct, float widthPct, float heightPct) {
        float absoluteTop = DESIGN_STATUS_BOTTOM_PCT + topPct * DESIGN_APP_HEIGHT_PCT / 100f;
        float absoluteHeight = heightPct * DESIGN_APP_HEIGHT_PCT / 100f;
        return docFrame(leftPct, absoluteTop, widthPct, absoluteHeight);
    }

    private int effectiveDocHeight(float pct) {
        return docHeight(pct * DESIGN_APP_HEIGHT_PCT / 100f);
    }

    private FrameLayout.LayoutParams docFrameWrapHeight(float leftPct, float topPct, float widthPct) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(docWidth(widthPct), -2);
        params.leftMargin = docX(leftPct);
        params.topMargin = docY(topPct);
        return params;
    }

    private FrameLayout.LayoutParams relativeDocFrame(float leftPct, float topPct, float widthPct, float heightPct,
                                                      float parentLeftPct, float parentTopPct) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(docWidth(widthPct), docHeight(heightPct));
        params.leftMargin = docX(leftPct) - docX(parentLeftPct);
        params.topMargin = docY(topPct) - docY(parentTopPct);
        return params;
    }

    private void setFrame(View view, FrameLayout.LayoutParams params) {
        if (view == null || params == null) return;
        view.setLayoutParams(params);
    }

    private int docX(float pct) {
        return Math.round(rootWidth() * pct / 100f);
    }

    private int docY(float pct) {
        return Math.round(rootHeight() * (pct - DESIGN_STATUS_BOTTOM_PCT) / DESIGN_APP_HEIGHT_PCT);
    }

    private int docWidth(float pct) {
        return Math.max(1, Math.round(rootWidth() * pct / 100f));
    }

    private int docHeight(float pct) {
        return Math.max(1, Math.round(rootHeight() * pct / DESIGN_APP_HEIGHT_PCT));
    }

    private int rootWidth() {
        return root == null || root.getWidth() <= 0 ? getResources().getDisplayMetrics().widthPixels : root.getWidth();
    }

    private int rootHeight() {
        return root == null || root.getHeight() <= 0 ? getResources().getDisplayMetrics().heightPixels : root.getHeight();
    }

    private FrameLayout buildTopBar() {
        FrameLayout top = new FrameLayout(this);
        top.setBackgroundColor(0x00000000);

        topBackButton = text("‹", 38, Color.WHITE, Typeface.NORMAL);
        topBackButton.setGravity(Gravity.CENTER);
        topBackButton.setContentDescription("返回");
        topBackButton.setOnClickListener(v -> finish());
        top.addView(topBackButton, new FrameLayout.LayoutParams(-2, -1));

        topPeopleIcon = iconImage(R.drawable.ic_bili_audience, "audience");
        top.addView(topPeopleIcon, new FrameLayout.LayoutParams(-2, -2));

        watchingView = text("100+人正在看", 15, 0xF2FFFFFF, Typeface.BOLD);
        watchingView.setGravity(Gravity.CENTER_VERTICAL);
        watchingView.setShadowLayer(4, 0, 1, 0xAA000000);
        top.addView(watchingView, new FrameLayout.LayoutParams(-2, -1));

        topSearchButton = iconImage(R.drawable.ic_bili_search_clean, "search");
        topSearchButton.setContentDescription("搜索");
        topSearchButton.setOnClickListener(v -> showSearchSuggestSheet());
        top.addView(topSearchButton, new FrameLayout.LayoutParams(-2, -1));

        topMenuButton = iconImage(R.drawable.ic_bili_more, "more");
        topMenuButton.setColorFilter(Color.WHITE);
        topMenuButton.setContentDescription("更多");
        topMenuButton.setOnClickListener(v -> showMoreSheet());
        top.addView(topMenuButton, new FrameLayout.LayoutParams(-2, -1));
        return top;
    }

    private LinearLayout buildActionRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER);

        likeButton = railButton(R.drawable.ic_rail_like, "", "0");
        likeButton.attachLottieAnimation(R.raw.bili_story_like_lottie_v2);
        commentButton = railButton(R.drawable.ic_bili_rail_comment, "", "0");
        commentButton.setStateIcons(R.drawable.ic_bili_rail_comment, 0);
        coinButton = railButton(R.drawable.ic_bili_rail_coin, "", "0");
        coinButton.setStateIcons(R.drawable.ic_bili_rail_coin, R.drawable.ic_bili_rail_coin_active);
        coinButton.attachLottieAnimation(R.raw.bili_story_coin_lottie_v1);
        favoriteButton = railButton(R.drawable.ic_bili_rail_favorite, "", "0");
        favoriteButton.setStateIcons(R.drawable.ic_bili_rail_favorite, R.drawable.ic_bili_rail_favorite_active);
        favoriteButton.attachLottieAnimation(R.raw.bili_story_favorite_lottie_v1);
        shareButton = railButton(R.drawable.ic_rail_share, "", "0");
        likeButton.setContentDescription("点赞");
        commentButton.setContentDescription("评论");
        coinButton.setContentDescription("投币");
        favoriteButton.setContentDescription("收藏");
        shareButton.setContentDescription("分享");
        likeButton.setOnTouchListener(this::handleLikeButtonTouch);
        commentButton.setOnClickListener(v -> showComments());
        coinButton.setOnClickListener(v -> showCoinSheet());
        favoriteButton.setOnClickListener(v -> handleFavoriteClick());
        shareButton.setOnClickListener(v -> showShareSheet());

        rail.addView(likeButton);
        rail.addView(commentButton);
        rail.addView(coinButton);
        rail.addView(favoriteButton);
        rail.addView(shareButton);
        return rail;
    }

    private FrameLayout buildBottomInfo() {
        FrameLayout bottom = new FrameLayout(this);

        bottomShade = new View(this);
        bottomShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xDD000000}
        ));
        bottom.addView(bottomShade, new FrameLayout.LayoutParams(-1, -1));

        ownerInfoGroup = new FrameLayout(this);
        ownerAvatarView = new ImageView(this);
        ownerAvatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ownerAvatarView.setBackground(rounded(0x66FFFFFF, dp(24)));
        makeCircular(ownerAvatarView);
        ownerAvatarView.setContentDescription("UP主头像");
        ownerAvatarView.setOnClickListener(v -> showOwnerProfileSheet());
        ownerInfoGroup.addView(ownerAvatarView, new FrameLayout.LayoutParams(-2, -2));

        ownerTextStack = new LinearLayout(this);
        ownerTextStack.setOrientation(LinearLayout.VERTICAL);
        ownerView = text("", 16, Color.WHITE, Typeface.BOLD);
        ownerView.setSingleLine(true);
        ownerView.setIncludeFontPadding(false);
        ownerView.setShadowLayer(5, 0, 1, 0xAA000000);
        fansView = text("净流推荐", 12, 0xD9FFFFFF, Typeface.NORMAL);
        fansView.setSingleLine(true);
        fansView.setEllipsize(TextUtils.TruncateAt.END);
        fansView.setIncludeFontPadding(false);
        fansView.setShadowLayer(4, 0, 1, 0x99000000);
        ownerTextStack.addView(ownerView, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams fansParams = new LinearLayout.LayoutParams(-2, -2);
        fansParams.topMargin = dp(1);
        ownerTextStack.addView(fansView, fansParams);
        ownerInfoGroup.addView(ownerTextStack, new FrameLayout.LayoutParams(-2, -2));

        ownerFollowButton = pillText("+ 关注", 15, Color.WHITE, BILI_PINK);
        ownerFollowButton.setGravity(Gravity.CENTER);
        ownerFollowButton.setContentDescription("关注UP主");
        ownerFollowButton.setOnClickListener(v -> followCurrentOwner());
        ownerInfoGroup.addView(ownerFollowButton, new FrameLayout.LayoutParams(-2, -2));
        bottom.addView(ownerInfoGroup, new FrameLayout.LayoutParams(-2, -2));

        titleView = text("", 16, Color.WHITE, Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setIncludeFontPadding(false);
        titleView.setShadowLayer(5, 0, 1, 0xAA000000);
        bottom.addView(titleView, new FrameLayout.LayoutParams(-2, -2));

        metaView = text("", 13, 0xE6FFFFFF, Typeface.NORMAL);
        metaView.setSingleLine(true);
        metaView.setEllipsize(TextUtils.TruncateAt.END);
        metaView.setIncludeFontPadding(false);
        metaView.setShadowLayer(4, 0, 1, 0x99000000);
        bottom.addView(metaView, new FrameLayout.LayoutParams(-2, -2));

        noticeView = text("ⓘ 个人观点，仅供参考", 12, 0xB8FFFFFF, Typeface.NORMAL);
        noticeView.setMaxLines(1);
        noticeView.setVisibility(View.GONE);
        noticeView.setShadowLayer(4, 0, 1, 0x99000000);
        bottom.addView(noticeView, new FrameLayout.LayoutParams(-2, -2));

        searchSuggestView = pillText("", 15, 0xFFECEDEE, 0xDD141517);
        searchSuggestView.setGravity(Gravity.CENTER_VERTICAL);
        searchSuggestView.setPadding(dp(16), 0, dp(14), 0);
        searchSuggestView.setOnClickListener(v -> showSearchSuggestSheet());
        searchSuggestView.setVisibility(View.GONE);
        bottom.addView(searchSuggestView, new FrameLayout.LayoutParams(-2, -2));

        progressBar = new FrameLayout(this);
        progressBar.setContentDescription("播放进度条");
        progressBar.setBackgroundColor(Color.TRANSPARENT);
        progressBar.setClipChildren(false);
        progressBar.setClipToPadding(false);
        View progressTrack = new View(this);
        progressTrack.setBackgroundColor(0x66FFFFFF);
        progressBar.addView(progressTrack, new FrameLayout.LayoutParams(-1, dp(3), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        progressFill = new View(this);
        progressFill.setBackgroundColor(BILI_PINK);
        progressBar.addView(progressFill, new FrameLayout.LayoutParams(1, dp(3), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        progressThumb = new SeekTvThumbView(this);
        progressBar.addView(progressThumb, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        progressBar.setOnTouchListener(this::handleProgressTouch);
        bottom.addView(progressBar, new FrameLayout.LayoutParams(-2, dp(24)));

        detailPageButton = buildDetailPageButton(true);
        bottom.addView(detailPageButton, new FrameLayout.LayoutParams(-2, -2));

        bottomInputRow = new FrameLayout(this);
        danmakuInputPill = pillText("发弹幕", 14, 0xFFE1E1E1, 0xAA55575B);
        danmakuInputPill.setGravity(Gravity.CENTER_VERTICAL);
        danmakuInputPill.setPadding(dp(16), 0, dp(16), 0);
        danmakuInputPill.setContentDescription("发送弹幕");
        danmakuInputPill.setOnClickListener(v -> showDanmakuInput());
        bottomInputRow.addView(danmakuInputPill, new FrameLayout.LayoutParams(-1, -1));

        danmakuInputDivider = new View(this);
        danmakuInputDivider.setBackgroundColor(0x66D6D8DE);
        bottomInputRow.addView(danmakuInputDivider, new FrameLayout.LayoutParams(1, 1));

        danmakuButton = iconImage(R.drawable.ic_bili_danmaku_on, "danmaku");
        danmakuButton.setOnClickListener(v -> toggleDanmaku());
        danmakuButton.setOnLongClickListener(v -> {
            showDanmakuSettings();
            return true;
        });
        bottomInputRow.addView(danmakuButton, new FrameLayout.LayoutParams(-2, -2));

        bottom.addView(bottomInputRow, new FrameLayout.LayoutParams(-2, -2));
        return bottom;
    }

    private FrameLayout buildLightControls() {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackgroundColor(Color.TRANSPARENT);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                applyLandscapeControlsLayout((FrameLayout) v);
            }
        });

        TextView back = text("‹", 48, Color.WHITE, Typeface.NORMAL);
        back.setGravity(Gravity.CENTER);
        back.setIncludeFontPadding(false);
        back.setShadowLayer(5, 0, 2, 0xAA000000);
        back.setContentDescription("返回竖屏");
        back.setOnClickListener(v -> {
            if (landscapeMode) exitLandscapeMode();
            else exitLightControlsMode();
        });
        panel.addView(back, new FrameLayout.LayoutParams(-2, -2));

        landscapeTitleView = text("", 21, Color.WHITE, Typeface.BOLD);
        landscapeTitleView.setSingleLine(true);
        landscapeTitleView.setEllipsize(TextUtils.TruncateAt.END);
        landscapeTitleView.setGravity(Gravity.CENTER_VERTICAL);
        landscapeTitleView.setShadowLayer(5, 0, 2, 0xAA000000);
        panel.addView(landscapeTitleView, new FrameLayout.LayoutParams(-2, -2));

        TextView titleDivider = text("|", 22, Color.WHITE, Typeface.BOLD);
        titleDivider.setGravity(Gravity.CENTER);
        titleDivider.setIncludeFontPadding(false);
        titleDivider.setShadowLayer(5, 0, 2, 0xAA000000);
        panel.addView(titleDivider, new FrameLayout.LayoutParams(-2, -2));

        landscapeOwnerNameView = text("", 20, Color.WHITE, Typeface.BOLD);
        landscapeOwnerNameView.setSingleLine(true);
        landscapeOwnerNameView.setEllipsize(TextUtils.TruncateAt.END);
        landscapeOwnerNameView.setGravity(Gravity.CENTER_VERTICAL);
        landscapeOwnerNameView.setShadowLayer(5, 0, 2, 0xAA000000);
        panel.addView(landscapeOwnerNameView, new FrameLayout.LayoutParams(-2, -2));

        landscapeAudienceIconView = iconImage(R.drawable.ic_bili_audience, "正在看");
        landscapeAudienceIconView.setColorFilter(0xAAFFFFFF);
        panel.addView(landscapeAudienceIconView, new FrameLayout.LayoutParams(-2, -2));

        landscapeWatchingTextView = text("100+ 正在看", 14, 0xAAFFFFFF, Typeface.BOLD);
        landscapeWatchingTextView.setGravity(Gravity.CENTER_VERTICAL);
        landscapeWatchingTextView.setShadowLayer(4, 0, 1, 0x99000000);
        panel.addView(landscapeWatchingTextView, new FrameLayout.LayoutParams(-2, -2));

        landscapeStatusTimeView = text(currentClockText(), 16, Color.WHITE, Typeface.BOLD);
        landscapeStatusTimeView.setGravity(Gravity.CENTER);
        landscapeStatusTimeView.setShadowLayer(4, 0, 1, 0x99000000);
        panel.addView(landscapeStatusTimeView, new FrameLayout.LayoutParams(-2, -2));

        TextView wifi = pillText("wifi", 13, Color.WHITE, 0x22000000);
        wifi.setGravity(Gravity.CENTER);
        wifi.setBackground(strokeRounded(0x88FFFFFF, 0x22000000, dp(13), dp(1)));
        panel.addView(wifi, new FrameLayout.LayoutParams(-2, -2));

        LandscapeIconView battery = new LandscapeIconView(this, LandscapeIconView.BATTERY);
        panel.addView(battery, new FrameLayout.LayoutParams(-2, -2));

        landscapeFavoriteButton = null;
        landscapeCoinButton = null;
        landscapeShareButton = null;
        landscapeViewerAvatarView = null;
        landscapeOwnerAvatarView = null;
        landscapeFollowButton = null;
        landscapeTreasureTextView = null;

        lightTimeView = text("00:00", 18, Color.WHITE, Typeface.NORMAL);
        lightTimeView.setGravity(Gravity.CENTER_VERTICAL);
        lightTimeView.setShadowLayer(4, 0, 1, 0x99000000);
        panel.addView(lightTimeView, new FrameLayout.LayoutParams(-2, -2));

        landscapeDurationView = text("00:00", 18, Color.WHITE, Typeface.NORMAL);
        landscapeDurationView.setGravity(Gravity.CENTER);
        landscapeDurationView.setShadowLayer(4, 0, 1, 0x99000000);
        panel.addView(landscapeDurationView, new FrameLayout.LayoutParams(-2, -2));

        lightProgressBar = new FrameLayout(this);
        lightProgressBar.setContentDescription("横屏播放进度条");
        lightProgressBar.setClipChildren(false);
        lightProgressBar.setClipToPadding(false);
        View track = new View(this);
        track.setBackgroundColor(0x66FFFFFF);
        lightProgressBar.addView(track, new FrameLayout.LayoutParams(-1, dp(3), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        lightProgressFill = new View(this);
        lightProgressFill.setBackgroundColor(BILI_PINK);
        lightProgressBar.addView(lightProgressFill, new FrameLayout.LayoutParams(1, dp(3), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        lightProgressThumb = new SeekTvThumbView(this);
        lightProgressBar.addView(lightProgressThumb, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        lightProgressBar.setOnTouchListener(this::handleProgressTouch);
        panel.addView(lightProgressBar, new FrameLayout.LayoutParams(-2, -2));

        lightPlayButton = text("▶", 42, Color.WHITE, Typeface.BOLD);
        lightPlayButton.setGravity(Gravity.CENTER);
        lightPlayButton.setIncludeFontPadding(false);
        lightPlayButton.setContentDescription("播放/暂停");
        lightPlayButton.setOnClickListener(v -> togglePlayPause());
        panel.addView(lightPlayButton, new FrameLayout.LayoutParams(-2, -2));

        lightDanmakuButton = iconImage(R.drawable.ic_bili_danmaku_on, "弹幕开关");
        lightDanmakuButton.setContentDescription("弹幕开关");
        lightDanmakuButton.setOnClickListener(v -> toggleDanmaku());
        lightDanmakuButton.setOnLongClickListener(v -> {
            showDanmakuSettings();
            return true;
        });
        panel.addView(lightDanmakuButton, new FrameLayout.LayoutParams(-2, -2));

        lightDanmakuInputPill = pillText("发弹幕", 16, 0xFF5D626B, 0xDDE6E6E6);
        lightDanmakuInputPill.setGravity(Gravity.CENTER_VERTICAL);
        lightDanmakuInputPill.setPadding(dp(24), 0, dp(24), 0);
        lightDanmakuInputPill.setContentDescription("发送弹幕");
        lightDanmakuInputPill.setOnClickListener(v -> showDanmakuInput());
        panel.addView(lightDanmakuInputPill, new FrameLayout.LayoutParams(-2, -2));

        landscapeSpeedView = text("倍速", 17, Color.WHITE, Typeface.BOLD);
        landscapeSpeedView.setGravity(Gravity.CENTER);
        landscapeSpeedView.setShadowLayer(4, 0, 1, 0x99000000);
        landscapeSpeedView.setOnClickListener(v -> showMoreSheet());
        panel.addView(landscapeSpeedView, new FrameLayout.LayoutParams(-2, -2));

        landscapeClarityView = text(currentClarity, 17, Color.WHITE, Typeface.BOLD);
        landscapeClarityView.setGravity(Gravity.CENTER);
        landscapeClarityView.setShadowLayer(4, 0, 1, 0x99000000);
        landscapeClarityView.setOnClickListener(v -> showClaritySheet());
        panel.addView(landscapeClarityView, new FrameLayout.LayoutParams(-2, -2));

        landscapeLikeButton = new LandscapeActionButton(this, R.drawable.ic_rail_like);
        landscapeLikeButton.setIconColor(Color.WHITE);
        landscapeLikeButton.setOnClickListener(v -> likeCurrentFromGesture());
        panel.addView(landscapeLikeButton, new FrameLayout.LayoutParams(-2, -2));

        landscapeCommentButton = new LandscapeActionButton(this, R.drawable.ic_rail_comment);
        landscapeCommentButton.setIconColor(Color.WHITE);
        landscapeCommentButton.setOnClickListener(v -> showComments());
        panel.addView(landscapeCommentButton, new FrameLayout.LayoutParams(-2, -2));

        return panel;
    }

    private void applyLandscapeControlsLayout(FrameLayout panel) {
        if (panel == null || panel.getWidth() <= 0 || panel.getHeight() <= 0) return;
        setPctFrame(panel.getChildAt(0), 5.1f, 6.4f, 4.5f, 9.6f);
        setPctFrame(landscapeTitleView, 10.9f, 7.0f, 41.4f, 7.8f);
        setPctFrame(panel.getChildAt(2), 52.6f, 7.2f, 1.2f, 7.0f);
        setPctFrame(landscapeOwnerNameView, 54.3f, 7.0f, 8.0f, 7.8f);
        setPctFrame(landscapeAudienceIconView, 62.6f, 9.5f, 1.5f, 3.0f);
        setPctFrame(landscapeWatchingTextView, 64.0f, 8.6f, 8.4f, 4.8f);
        setPctFrame(panel.getChildAt(6), 48.6f, 1.1f, 5.4f, 4.7f);
        setPctFrame(panel.getChildAt(7), 89.5f, 1.2f, 4.8f, 4.2f);
        setPctFrame(panel.getChildAt(8), 94.5f, 1.1f, 3.8f, 4.2f);

        setPctFrame(lightTimeView, 5.6f, 78.7f, 6.0f, 5.5f);
        setPctFrame(landscapeDurationView, 88.3f, 78.7f, 6.5f, 5.5f);
        setPctFrame(lightProgressBar, 12.0f, 79.5f, 74.4f, 4.3f);
        setPctFrame(lightPlayButton, 5.0f, 88.6f, 5.2f, 8.7f);
        setPctFrame(lightDanmakuButton, 12.8f, 89.2f, 4.0f, 7.6f);
        setPctFrame(lightDanmakuInputPill, 18.6f, 89.1f, 47.2f, 7.8f);
        setPctFrame(landscapeSpeedView, 68.2f, 89.0f, 6.0f, 7.6f);
        setPctFrame(landscapeClarityView, 75.6f, 89.0f, 5.4f, 7.6f);
        setPctFrame(landscapeLikeButton, 82.2f, 87.8f, 5.3f, 10.2f);
        setPctFrame(landscapeCommentButton, 90.0f, 87.8f, 5.3f, 10.2f);
    }

    private void setPctFrame(View child, float leftPct, float topPct, float widthPct, float heightPct) {
        if (child == null || child.getParent() == null) return;
        View parent = (View) child.getParent();
        int parentWidth = Math.max(1, parent.getWidth());
        int parentHeight = Math.max(1, parent.getHeight());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.max(1, Math.round(parentWidth * widthPct / 100f)),
                Math.max(1, Math.round(parentHeight * heightPct / 100f)));
        params.leftMargin = Math.round(parentWidth * leftPct / 100f);
        params.topMargin = Math.round(parentHeight * topPct / 100f);
        child.setLayoutParams(params);
    }

    private boolean handleProgressTouch(View view, MotionEvent event) {
        if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(true);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            if (action == MotionEvent.ACTION_DOWN) {
                progressDragging = true;
                progressLastTouchX = event.getX();
                updateSeekTvEyes(0f, false);
                uiHandler.removeCallbacks(hideLightControlsRunnable);
                view.animate().scaleY(1.35f).setDuration(80).start();
            }
            if (action == MotionEvent.ACTION_MOVE) {
                updateSeekTvEyesFromDelta(event.getX() - progressLastTouchX);
                progressLastTouchX = event.getX();
            }
            previewProgressAt(event.getX(), view.getWidth());
            if (action == MotionEvent.ACTION_UP) {
                commitProgressAt(event.getX(), view.getWidth());
                progressDragging = false;
                progressLastTouchX = Float.NaN;
                updateSeekTvEyes(0f, true);
                view.animate().scaleY(1f).setDuration(120).start();
                hideProgressBubbleLater(1500);
                if (landscapeMode) scheduleLightControlsHide(1100);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL && progressBubbleCard != null) {
            progressDragging = false;
            progressLastTouchX = Float.NaN;
            progressDragPositionMs = -1L;
            updateSeekTvEyes(0f, true);
            view.animate().scaleY(1f).setDuration(120).start();
            progressBubbleCard.setVisibility(View.GONE);
        }
        return true;
    }

    private FrameLayout buildDetailPageButton(boolean clickable) {
        FrameLayout button = new FrameLayout(this);
        TextView label = pillText("\u8BE6\u60C5\u9875", 8, 0xFFE8E8E8, 0xCC454545);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, 0, 0, 0);
        button.addView(label, new FrameLayout.LayoutParams(-1, dp(18), Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        ImageView icon = iconImage(R.drawable.ic_bili_detail_page, "detail");
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(-1, dp(36), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        button.addView(icon, iconParams);

        if (clickable) {
            button.setClickable(true);
            button.setOnClickListener(v -> showCenter("\u8BE6\u60C5\u9875"));
        } else {
            button.setClickable(false);
        }
        return button;
    }

    private LinearLayout buildCommentsPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        panel.setOnTouchListener((view, event) -> {
            view.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        panel.setPadding(dp(14), dp(8), dp(14), dp(8));
        panel.setBackground(topRounded(0xFFFFFFFF, dp(18)));

        View handle = new View(this);
        handle.setBackground(rounded(0xFFD2D2D2, dp(3)));
        handle.setOnTouchListener((view, event) -> handleCommentsDrag(event));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(36), dp(5));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = dp(2);
        panel.addView(handle, handleParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(10), 0, dp(4));
        header.setOnTouchListener((view, event) -> handleCommentsDrag(event));
        TextView intro = text("简介", 18, 0xFF9A9EA6, Typeface.BOLD);
        intro.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(intro, new LinearLayout.LayoutParams(dp(70), dp(40)));
        commentTitleView = text("评论（0）", 19, 0xFF171A1F, Typeface.BOLD);
        commentTitleView.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(commentTitleView, new LinearLayout.LayoutParams(0, dp(40), 1));
        commentExpandButton = iconImage(R.drawable.ic_bili_expand, "expand comments");
        commentExpandButton.setBackground(rounded(0xFFF1F2F4, dp(21)));
        commentExpandButton.setContentDescription("展开/收起评论");
        commentExpandButton.setOnClickListener(v -> {
            commentsExpanded = !commentsExpanded;
            updateCommentExpandIcon();
            animateCommentsPanelHeight(commentsExpanded ? commentsFullHeight() : commentsHalfHeight());
        });
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        closeParams.rightMargin = dp(2);
        header.addView(commentExpandButton, closeParams);
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        View activeTab = new View(this);
        activeTab.setBackground(rounded(BILI_PINK, dp(2)));
        LinearLayout.LayoutParams activeParams = new LinearLayout.LayoutParams(dp(34), dp(4));
        activeParams.leftMargin = dp(118);
        activeParams.bottomMargin = dp(13);
        panel.addView(activeTab, activeParams);

        commentSubHeader = new LinearLayout(this);
        commentSubHeader.setGravity(Gravity.CENTER_VERTICAL);
        commentSubHeader.setPadding(0, 0, 0, dp(8));
        commentSectionTitleView = text("热门评论", 15, 0xFF8E9299, Typeface.NORMAL);
        commentSectionTitleView.setGravity(Gravity.CENTER_VERTICAL);
        commentSubHeader.addView(commentSectionTitleView, new LinearLayout.LayoutParams(0, dp(34), 1));
        commentSortView = text("☰ 按热度", 15, 0xFF8E9299, Typeface.NORMAL);
        commentSortView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        commentSortView.setOnClickListener(v -> {
            if (commentDetailOpen) return;
            commentsByTime = !commentsByTime;
            updateCommentSortLabel();
            loadComments(true);
        });
        commentSubHeader.addView(commentSortView, new LinearLayout.LayoutParams(dp(120), dp(34)));
        panel.addView(commentSubHeader, new LinearLayout.LayoutParams(-1, -2));

        commentsContentFrame = new FrameLayout(this);
        commentsScrollView = new ScrollView(this);
        commentsList = new LinearLayout(this);
        commentsList.setOrientation(LinearLayout.VERTICAL);
        commentsScrollView.addView(commentsList, new ScrollView.LayoutParams(-1, -2));
        commentsContentFrame.addView(commentsScrollView, new FrameLayout.LayoutParams(-1, -1));

        commentDetailDrawer = new LinearLayout(this);
        commentDetailDrawer.setOrientation(LinearLayout.VERTICAL);
        commentDetailDrawer.setBackgroundColor(Color.WHITE);
        commentDetailDrawer.setVisibility(View.GONE);
        commentDetailScrollView = new ScrollView(this);
        commentDetailList = new LinearLayout(this);
        commentDetailList.setOrientation(LinearLayout.VERTICAL);
        commentDetailScrollView.addView(commentDetailList, new ScrollView.LayoutParams(-1, -2));
        commentDetailDrawer.addView(commentDetailScrollView, new LinearLayout.LayoutParams(-1, -1));
        commentsContentFrame.addView(commentDetailDrawer, new FrameLayout.LayoutParams(-1, -1));

        panel.addView(commentsContentFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, dp(8), 0, dp(2));
        commentInput = new EditText(this);
        commentInput.setSingleLine(false);
        commentInput.setMaxLines(3);
        commentInput.setHint(currentCommentControl.rootHint());
        commentInput.setHintTextColor(0xFF9CA1A8);
        commentInput.setTextColor(0xFF1D1F23);
        commentInput.setTextSize(16);
        commentInput.setPadding(dp(16), 0, dp(16), 0);
        commentInput.setMinHeight(0);
        commentInput.setIncludeFontPadding(false);
        commentInput.setBackground(rounded(0xFFF0F1F4, dp(20)));
        commentInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                commentInputEverFocused = true;
                return;
            }
            updateCommentInputHintAfterFocusLoss();
        });
        commentInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCommentSendButton();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        inputRow.addView(commentInput, new LinearLayout.LayoutParams(0, dp(40), 1));

        sendCommentButton = pillText("☻", 22, 0xFF8F949C, 0xFFF0F1F4);
        sendCommentButton.setGravity(Gravity.CENTER);
        sendCommentButton.setOnClickListener(v -> sendComment());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        sendParams.leftMargin = dp(10);
        inputRow.addView(sendCommentButton, sendParams);
        panel.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));
        updateCommentSendButton();
        return panel;
    }

    private void loadInitial() {
        status("补充 cleanQueue...");
        new Thread(() -> {
            try {
                repository.fillTo(12);
                runOnUiThread(this::playNext);
            } catch (Exception error) {
                runOnUiThread(() -> status("加载失败：" + error.getMessage()));
            }
        }).start();
    }

    private void playNext() {
        playNext(true);
    }

    private void playNext(boolean pushCurrentToHistory) {
        hideComments();
        setOverlayVisibility(true);
        new Thread(() -> {
            try {
                PrefetchedVideo video = forwardReturnStack.pollLast();
                if (video == null) {
                    synchronized (prefetchLock) {
                        if (forwardPrefetchCountLocked() == 0 && !prefetchRunning) {
                            prefetchNext();
                        }
                    }
                    video = takeNextReady(8000);
                }
                if (video == null) video = fetchNextPlayable();
                if (video == null) {
                    runOnUiThread(() -> {
                        cancelHeldSwipePreview();
                        status("没有可播放内容");
                    });
                    return;
                }
                PrefetchedVideo playable = video;
                runOnUiThread(() -> play(playable, pushCurrentToHistory));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    cancelHeldSwipePreview();
                    status("播放失败：" + error.getMessage());
                });
            }
        }).start();
    }

    private void playPrevious() {
        commentsPanel.setVisibility(View.GONE);
        PrefetchedVideo previous = previousStack.pollLast();
        if (previous == null) {
            cancelHeldSwipePreview();
            showCenter("已经是第一条");
            return;
        }
        if (currentVideo != null) forwardReturnStack.addLast(currentVideo);
        play(previous, false);
    }

    private PrefetchedVideo fetchNextPlayable() throws Exception {
        for (int attempt = 0; attempt < 12; attempt++) {
            if (repository.size() < 6) repository.fillTo(12);
            FeedItem item = repository.poll();
            if (item == null) return null;
            if (isNotInterested(item)) {
                continue;
            }
            repository.apiClient().enrichItem(item);
            repository.apiClient().enrichStoryEntrance(item);
            if (isNotInterested(item)) {
                continue;
            }
            PlayInfo playInfo = repository.apiClient().fetchPlayInfo(item, globalPreferredQn);
            if (playInfo.playable) {
                return new PrefetchedVideo(item, playInfo);
            }
        }
        return null;
    }

    private boolean isNotInterested(FeedItem item) {
        return item != null && notInterestedVideoIds.contains(videoIdentity(item));
    }

    private String videoIdentity(FeedItem item) {
        return item == null ? "" : item.identity();
    }

    private void rememberCurrentPlaybackPosition() {
        if (currentItem == null || player == null) return;
        long duration = player.getDuration();
        long position = player.getCurrentPosition();
        if (duration <= 0 || position < 0) return;
        if (position >= duration - 1200L) position = 0L;
        String id = videoIdentity(currentItem);
        if (!id.isEmpty()) playbackPositions.put(id, position);
    }

    private long savedPlaybackPosition(FeedItem item) {
        String id = videoIdentity(item);
        if (id.isEmpty()) return 0L;
        Long value = playbackPositions.get(id);
        if (value == null || value < 0L) return 0L;
        long max = item.durationSeconds > 0 ? item.durationSeconds * 1000L - 1200L : Long.MAX_VALUE;
        return Math.max(0L, Math.min(value, max));
    }

    private void markCurrentNotInterested() {
        if (currentItem == null) {
            Toast.makeText(this, "当前没有可标记的视频", Toast.LENGTH_SHORT).show();
            return;
        }
        String id = videoIdentity(currentItem);
        if (!id.isEmpty()) notInterestedVideoIds.add(id);
        removeVideoFromDeque(previousStack, id);
        removeVideoFromDeque(nextStack, id);
        removeVideoFromDeque(forwardReturnStack, id);
        clearPrefetch();
        dismissActiveBottomSheet();
        Toast.makeText(this, "已减少类似内容推荐", Toast.LENGTH_SHORT).show();
        showCenter("已不感兴趣");
        playNext(false);
    }

    private void addCurrentToWatchLater() {
        if (currentItem == null) {
            Toast.makeText(this, "当前没有可加入稍后再看的视频", Toast.LENGTH_SHORT).show();
            return;
        }
        String id = videoIdentity(currentItem);
        boolean added = !id.isEmpty() && watchLaterVideoIds.add(id);
        Toast.makeText(this, added ? "已加入稍后再看" : "已在稍后再看", Toast.LENGTH_SHORT).show();
    }

    private void removeVideoFromDeque(ArrayDeque<PrefetchedVideo> deque, String videoId) {
        if (videoId == null || videoId.isEmpty()) return;
        Iterator<PrefetchedVideo> iterator = deque.iterator();
        while (iterator.hasNext()) {
            PrefetchedVideo video = iterator.next();
            if (video != null && video.item != null && videoId.equals(videoIdentity(video.item))) {
                iterator.remove();
            }
        }
    }

    private void play(PrefetchedVideo video, boolean pushCurrentToHistory) {
        rememberCurrentPlaybackPosition();
        FeedItem previousItem = currentItem;
        if (pushCurrentToHistory && currentVideo != null) {
            previousStack.addLast(currentVideo);
        }
        currentVideo = video;
        currentItem = video.item;
        hideDanmakuActionBox();
        if (closeAfterCurrentVideo && previousItem != null && previousItem != currentItem) {
            closeAfterCurrentVideo = false;
            timerCloseOption = "关闭";
        }
        android.util.Log.d("BiliClean", "play bvid=" + currentItem.bvid
                + " aid=" + currentItem.aid
                + " cid=" + currentItem.cid
                + " title=" + currentItem.title
                + " reply=" + currentItem.replyCount
                + " dm=" + currentItem.danmakuCount);
        boolean keepSwipePreview = holdSwipePreviewUntilReady
                && swipePreviewPage.getVisibility() == View.VISIBLE;
        if (keepSwipePreview) {
            resetSwipeChromeTranslationOnly();
            setSwipeChromeAlpha(0f);
        } else {
            hideSwipePreview();
            resetSwipeChrome();
        }
        uiHidden = false;
        clearScreenMode = false;
        landscapeMode = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        applySystemBarsForMode();
        setOverlayVisibility(true);
        updateItemViews(video.item, video.playInfo);

        boolean useWarmPlayer = warmPlayer != null && warmVideo == video;
        if (useWarmPlayer) {
            ExoPlayer oldPlayer = player;
            player = warmPlayer;
            warmPlayer = null;
            warmVideo = null;
            playerView.setPlayer(player);
            if (oldPlayer != null) {
                try {
                    oldPlayer.release();
                } catch (Exception ignored) {
                }
            }
            android.util.Log.d("BiliClean", "use warm player bvid=" + currentItem.bvid);
        } else {
            releaseWarmPlayer();
            MediaSource source = buildMediaSource(video);
            player.setMediaSource(source);
            player.prepare();
        }
        player.setRepeatMode(autoSlideEnabled ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ONE);
        player.setPlaybackSpeed(playbackSpeed);
        long resumePosition = savedPlaybackPosition(video.item);
        if (resumePosition > 0L) {
            player.seekTo(resumePosition);
        }
        player.play();
        if (keepSwipePreview) {
            uiHandler.removeCallbacks(releaseHeldSwipePreviewRunnable);
            uiHandler.postDelayed(releaseHeldSwipePreviewRunnable, 1600);
        }
        if (commentsPanel != null && commentsPanel.getVisibility() == View.VISIBLE) {
            hideCommentDetailDrawer(false);
            loadComments(false);
        } else {
            invalidateCommentStateForNewVideo();
        }
        loadDanmaku();
        prefetchNext();
        ensureWarmNextReady(currentPrefetchGeneration());
    }

    private void updateItemViews(FeedItem item, PlayInfo playInfo) {
        currentFollowed = false;
        currentCoined = false;
        currentFavorited = false;
        commentsByTime = false;
        updateCommentSortLabel();
        watchingView.setText(watchingText(item));
        if (lightWatchingView != null) lightWatchingView.setText(watchingText(item));
        ownerView.setText(fallback(item.ownerName, "未知 UP"));
        if (landscapeTitleView != null) landscapeTitleView.setText(item.title);
        if (landscapeOwnerNameView != null) landscapeOwnerNameView.setText(fallback(item.ownerName, "未知 UP"));
        if (landscapeWatchingTextView != null) landscapeWatchingTextView.setText(watchingText(item));
        fansView.setText(item.ownerFollowerCount > 0 ? formatCount(item.ownerFollowerCount) + "粉丝" : "净流推荐");
        refreshOwnerFollowPosition(ownerTextStack, ownerFollowButton);
        if (ownerFollowButton != null) {
            ownerFollowButton.setText("+ 关注");
            ownerFollowButton.setTextColor(Color.WHITE);
            ownerFollowButton.setBackground(rounded(BILI_PINK, dp(20)));
        }
        loadImageInto(item.ownerFace, ownerAvatarView);
        if (landscapeOwnerAvatarView != null) loadImageInto(item.ownerFace, landscapeOwnerAvatarView);
        if (landscapeViewerAvatarView != null) loadImageInto(item.ownerFace, landscapeViewerAvatarView);
        progressFrameGeneration++;
        progressFrameLoading = false;
        progressVideoShotLoading = false;
        progressSpriteLoadingUrl = "";
        progressVideoShotInfo = null;
        progressVideoShotKey = videoIdentity(item);
        lastProgressFramePositionMs = -1L;
        pendingProgressFramePositionMs = -1L;
        clearProgressSpriteCache();
        if (progressBubbleCover != null) progressBubbleCover.clearSprite();
        loadImageInto(item.cover, progressBubbleCover);
        titleView.setText(item.title);
        currentClarity = clarityLabelForQuality(playInfo.quality, "自动");
        updateSearchEntrance(searchSuggestView, item, true);
        metaView.setText(formatCount(item.viewCount) + "播放⌄");
        noticeView.setVisibility(View.GONE);
        noticeView.setText("");
        likeButton.setCount(formatCount(item.likeCount));
        likeButton.setIconColor(item.liked ? BILI_PINK : Color.WHITE);
        if (landscapeLikeButton != null) {
            landscapeLikeButton.setCount(formatCount(item.likeCount));
            landscapeLikeButton.setIconColor(item.liked ? BILI_PINK : Color.WHITE);
        }
        commentButton.setCount(formatCount(item.replyCount));
        if (landscapeCommentButton != null) landscapeCommentButton.setCount(formatCount(item.replyCount));
        coinButton.setCount(formatCount(item.coinCount));
        coinButton.setIconColor(Color.WHITE);
        if (landscapeCoinButton != null) {
            landscapeCoinButton.setCount(formatCount(item.coinCount));
            landscapeCoinButton.setIconColor(Color.WHITE);
        }
        favoriteButton.setCount(formatCount(item.favoriteCount));
        favoriteButton.setIconColor(Color.WHITE);
        if (landscapeFavoriteButton != null) {
            landscapeFavoriteButton.setCount(formatCount(item.favoriteCount));
            landscapeFavoriteButton.setIconColor(Color.WHITE);
        }
        shareButton.setCount(formatCount(item.shareCount));
        if (landscapeShareButton != null) landscapeShareButton.setCount("");
        if (landscapeClarityView != null) landscapeClarityView.setText(currentClarity);
        if (landscapeSpeedView != null) landscapeSpeedView.setText("倍速");
        updateFullscreenButtonIcon();
        applyVideoResizeMode();
    }

    private MediaSource buildMediaSource(PrefetchedVideo video) {
        ProgressiveMediaSource.Factory mediaSourceFactory = new ProgressiveMediaSource.Factory(buildCacheDataSourceFactory(video.item));
        MediaSource videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(video.playInfo.videoUrl));
        if (!video.playInfo.audioUrl.isEmpty()) {
            MediaSource audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(video.playInfo.audioUrl));
            return new MergingMediaSource(videoSource, audioSource);
        }
        return videoSource;
    }

    private CacheDataSource.Factory buildCacheDataSourceFactory(FeedItem item) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", BiliApiClient.USER_AGENT);
        headers.put("Referer", item.webUrl());
        DefaultHttpDataSource.Factory upstreamFactory = new DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers);
        return new CacheDataSource.Factory()
                .setCache(mediaCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    private PrefetchedVideo takeNextReady(long waitMs) {
        synchronized (prefetchLock) {
            if (forwardPrefetchCountLocked() == 0 && prefetchRunning && waitMs > 0) {
                try {
                    prefetchLock.wait(waitMs);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }
            return takeNextReadyLocked();
        }
    }

    private PrefetchedVideo takeNextReadyLocked() {
        if (prefetchedVideo != null) {
            PrefetchedVideo video = prefetchedVideo;
            prefetchedVideo = null;
            return video;
        }
        return nextStack.pollLast();
    }

    private PrefetchedVideo peekNextReadyLocked() {
        return prefetchedVideo != null ? prefetchedVideo : nextStack.peekLast();
    }

    private void prefetchNext() {
        final int generation;
        final int existingCount;
        synchronized (prefetchLock) {
            existingCount = forwardPrefetchCountLocked();
            if (prefetchRunning
                    || existingCount > FORWARD_PREFETCH_REFILL_THRESHOLD
                    || existingCount >= FORWARD_PREFETCH_LIMIT) {
                return;
            }
            prefetchRunning = true;
            generation = prefetchGeneration;
        }
        new Thread(() -> {
            List<PrefetchedVideo> results = new ArrayList<>();
            while (true) {
                synchronized (prefetchLock) {
                    if (generation != prefetchGeneration
                            || existingCount + results.size() >= FORWARD_PREFETCH_LIMIT
                            || results.size() >= FORWARD_PREFETCH_BATCH_SIZE) {
                        break;
                    }
                }
                try {
                    PrefetchedVideo result = fetchNextPlayable();
                    if (result == null) break;
                    results.add(result);
                } catch (Exception ignored) {
                    break;
                }
            }
            PrefetchedVideo firstReady = null;
            synchronized (prefetchLock) {
                if (generation == prefetchGeneration) {
                    for (PrefetchedVideo result : results) {
                        if (prefetchedVideo == null && nextStack.isEmpty()) {
                            prefetchedVideo = result;
                            if (firstReady == null) firstReady = result;
                        } else {
                            nextStack.addFirst(result);
                        }
                    }
                }
                prefetchRunning = false;
                prefetchLock.notifyAll();
            }
            if (firstReady != null) {
                PrefetchedVideo ready = firstReady;
                runOnUiThread(() -> {
                    if (swipeDragging && swipePreviewForward && swipePreviewPage.getVisibility() == View.VISIBLE) {
                        showSwipePreviewItem(ready);
                    }
                });
            }
            ensureWarmNextReady(generation);
            if (!results.isEmpty()) {
                new Thread(() -> {
                    for (PrefetchedVideo video : results) {
                        prefetchUiImages(video);
                        cacheLeadBytes(video);
                    }
                }).start();
            }
        }).start();
    }

    private int forwardPrefetchCountLocked() {
        return (prefetchedVideo == null ? 0 : 1) + nextStack.size();
    }

    private int currentPrefetchGeneration() {
        synchronized (prefetchLock) {
            return prefetchGeneration;
        }
    }

    private void ensureWarmNextReady(int generation) {
        uiHandler.post(() -> {
            PrefetchedVideo next;
            synchronized (prefetchLock) {
                if (generation != prefetchGeneration) return;
                next = peekNextReadyLocked();
            }
            warmPrefetchedVideo(next, generation);
        });
    }

    private void warmPrefetchedVideo(PrefetchedVideo video, int generation) {
        if (video == null) {
            releaseWarmPlayer();
            return;
        }
        uiHandler.post(() -> {
            synchronized (prefetchLock) {
                if (generation != prefetchGeneration || peekNextReadyLocked() != video) return;
            }
            if (warmVideo == video && warmPlayer != null) {
                return;
            }
            releaseWarmPlayer();
            try {
                warmVideo = video;
                warmPlayer = newPlaybackPlayer();
                warmPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
                warmPlayer.setPlaybackSpeed(playbackSpeed);
                warmPlayer.setMediaSource(buildMediaSource(video));
                warmPlayer.prepare();
                android.util.Log.d("BiliClean", "warm next bvid=" + video.item.bvid);
            } catch (Exception error) {
                releaseWarmPlayer();
                android.util.Log.d("BiliClean", "warm next failed=" + error.getMessage());
            }
        });
    }

    private void cacheLeadBytes(PrefetchedVideo video) {
        if (video == null || video.playInfo == null || video.item == null || mediaCache == null) return;
        cacheLeadBytes(video.item, video.playInfo.videoUrl);
        cacheLeadBytes(video.item, video.playInfo.audioUrl);
    }

    private void cacheLeadBytes(FeedItem item, String url) {
        if (url == null || url.trim().isEmpty()) return;
        try {
            CacheDataSource dataSource = buildCacheDataSourceFactory(item).createDataSource();
            DataSpec dataSpec = new DataSpec.Builder()
                    .setUri(Uri.parse(url))
                    .setPosition(0L)
                    .setLength(PREFETCH_STREAM_BYTES)
                    .build();
            new CacheWriter(dataSource, dataSpec, new byte[64 * 1024], null).cache();
        } catch (Exception ignored) {
        }
    }

    private void prefetchUiImages(PrefetchedVideo video) {
        if (video == null || video.item == null || video.playInfo == null) return;
        String key = firstFrameCacheKey(video.item);
        if (!imageCache.containsKey(key)) {
            Bitmap frame = downloadBitmap(video.item.cover);
            if (frame != null) imageCache.put(key, frame);
        }
        downloadBitmap(video.item.cover);
        downloadBitmap(video.item.ownerFace);
    }

    private void loadFirstFrameInto(PrefetchedVideo video, ImageView target) {
        if (target == null) return;
        target.setImageDrawable(null);
        if (video == null || video.item == null || video.playInfo == null) return;
        String key = firstFrameCacheKey(video.item);
        target.setTag(key);
        Bitmap cached = imageCache.get(key);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        new Thread(() -> {
            Bitmap frame = downloadBitmap(video.item.cover);
            if (frame != null) imageCache.put(key, frame);
            runOnUiThread(() -> {
                if (frame != null && key.equals(target.getTag())) {
                    target.setImageBitmap(frame);
                }
            });
        }).start();
    }

    private String firstFrameCacheKey(FeedItem item) {
        return "first-frame:" + videoIdentity(item);
    }

    private void releaseWarmPlayer() {
        if (warmPlayer != null) {
            try {
                warmPlayer.release();
            } catch (Exception ignored) {
            }
        }
        warmPlayer = null;
        warmVideo = null;
    }

    private void clearPrefetch() {
        synchronized (prefetchLock) {
            prefetchedVideo = null;
            prefetchRunning = false;
            prefetchGeneration++;
            prefetchLock.notifyAll();
        }
        releaseWarmPlayer();
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
            showCenter("暂停\n" + currentPlaybackTimeText());
        } else {
            player.play();
            showCenter("播放");
        }
        updateLightControlsPlaybackState();
    }

    private void startFastForward() {
        if (player == null || fastForwarding) return;
        fastForwarding = true;
        player.setPlaybackSpeed(2.0f);
        showCenter("2.0x");
    }

    private void stopFastForward() {
        if (player == null || !fastForwarding) return;
        fastForwarding = false;
        player.setPlaybackSpeed(playbackSpeed);
    }

    private void showDoubleLike(float x, float y) {
        if (root == null) return;
        SVGAImageView burst = new SVGAImageView(this);
        burst.setLoops(1);
        burst.setClearsAfterStop(true);
        burst.setClickable(false);
        int size = Math.max(1, docWidth(44f));
        FrameLayout.LayoutParams params = centeredOverlayParams(x, y, size);
        burst.setCallback(new SVGACallback() {
            private boolean removed;

            @Override
            public void onPause() {
            }

            @Override
            public void onFinished() {
                remove();
            }

            @Override
            public void onRepeat() {
            }

            @Override
            public void onStep(int frame, double percentage) {
            }

            private void remove() {
                if (removed) return;
                removed = true;
                root.removeView(burst);
            }
        });
        root.addView(burst, params);
        playDoubleLikeSvga(burst, x, y);
    }

    private void playDoubleLikeSvga(SVGAImageView target, float x, float y) {
        if (doubleLikeSvgaVideo != null) {
            target.setVideoItem(doubleLikeSvgaVideo);
            target.startAnimation();
            return;
        }
        if (svgaParser == null) svgaParser = new SVGAParser(this);
        InputStream input = getResources().openRawResource(R.raw.bili_story_like_combo);
        svgaParser.decodeFromInputStream(input, "bili_story_like_combo", new SVGAParser.ParseCompletion() {
            @Override
            public void onComplete(SVGAVideoEntity videoItem) {
                doubleLikeSvgaVideo = videoItem;
                runOnUiThread(() -> {
                    if (target.getParent() == null) return;
                    target.setVideoItem(videoItem);
                    target.startAnimation();
                });
            }

            @Override
            public void onError() {
                runOnUiThread(() -> {
                    if (target.getParent() != null) root.removeView(target);
                    showDoubleLikeFallback(x, y);
                });
            }
        }, true, null, "");
    }

    private void showTripleLikeAnimation() {
        if (root == null) return;
        SVGAImageView burst = new SVGAImageView(this);
        burst.setLoops(1);
        burst.setClearsAfterStop(true);
        burst.setClickable(false);
        burst.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int width = Math.max(1, Math.round(rootWidth() * 0.68f));
        int height = Math.max(1, Math.round(width * 0.62f));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER;
        params.topMargin = -docHeight(8f);
        burst.setCallback(new SVGACallback() {
            private boolean removed;

            @Override
            public void onPause() {
            }

            @Override
            public void onFinished() {
                remove();
            }

            @Override
            public void onRepeat() {
            }

            @Override
            public void onStep(int frame, double percentage) {
            }

            private void remove() {
                if (removed) return;
                removed = true;
                root.removeView(burst);
            }
        });
        root.addView(burst, params);
        playTripleLikeSvga(burst);
    }

    private void playTripleLikeSvga(SVGAImageView target) {
        if (tripleLikeSvgaVideo != null) {
            target.setVideoItem(tripleLikeSvgaVideo);
            target.startAnimation();
            return;
        }
        if (svgaParser == null) svgaParser = new SVGAParser(this);
        InputStream input = getResources().openRawResource(R.raw.bili_player_triple_like_animation);
        svgaParser.decodeFromInputStream(input, "bili_player_triple_like_animation", new SVGAParser.ParseCompletion() {
            @Override
            public void onComplete(SVGAVideoEntity videoItem) {
                tripleLikeSvgaVideo = videoItem;
                runOnUiThread(() -> {
                    if (target.getParent() == null) return;
                    target.setVideoItem(videoItem);
                    target.startAnimation();
                });
            }

            @Override
            public void onError() {
                runOnUiThread(() -> {
                    if (target.getParent() != null) root.removeView(target);
                    showCenter("已三连");
                });
            }
        }, true, null, "");
    }

    private void showDoubleLikeFallback(float x, float y) {
        if (root == null) return;
        LottieAnimationView burst = new LottieAnimationView(this);
        burst.setAnimation(R.raw.bili_story_double_tap);
        burst.setRepeatCount(0);
        burst.setScaleType(ImageView.ScaleType.FIT_CENTER);
        burst.setClickable(false);
        int size = Math.max(1, docWidth(42f));
        FrameLayout.LayoutParams params = centeredOverlayParams(x, y, size);
        burst.addAnimatorListener(new AnimatorListenerAdapter() {
            private boolean removed;

            @Override
            public void onAnimationEnd(Animator animation) {
                remove();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                remove();
            }

            private void remove() {
                if (removed) return;
                removed = true;
                root.removeView(burst);
            }
        });
        root.addView(burst, params);
        burst.playAnimation();
    }

    private FrameLayout.LayoutParams centeredOverlayParams(float x, float y, int size) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.leftMargin = Math.max(0, Math.min(rootWidth() - size, Math.round(x) - size / 2));
        params.topMargin = Math.max(0, Math.min(rootHeight() - size, Math.round(y) - size / 2));
        return params;
    }

    private void toggleLikeFromButton() {
        if (currentItem == null) return;
        if (currentItem.liked) {
            currentItem.liked = false;
            currentItem.likeCount = Math.max(0, currentItem.likeCount - 1);
            likeButton.setCount(formatCount(currentItem.likeCount));
            likeButton.setIconColor(Color.WHITE);
            if (landscapeLikeButton != null) {
                landscapeLikeButton.setCount(formatCount(currentItem.likeCount));
                landscapeLikeButton.setIconColor(Color.WHITE);
            }
            Toast.makeText(this, "已取消点赞", Toast.LENGTH_SHORT).show();
            return;
        }
        likeCurrentFromGesture();
    }

    private boolean handleLikeButtonTouch(View view, MotionEvent event) {
        if (currentItem == null) return true;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            startTripleHold();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            long elapsed = SystemClock.uptimeMillis() - tripleHoldStartMs;
            if (tripleCommitted) {
                finishTripleHoldVisuals(false);
            } else if (elapsed < 360L) {
                cancelTripleHoldVisuals(false);
                toggleLikeFromButton();
            } else if (elapsed < 2000L) {
                cancelTripleHoldVisuals(true);
            } else {
                commitTripleHold();
                finishTripleHoldVisuals(false);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            cancelTripleHoldVisuals(true);
            return true;
        }
        return true;
    }

    private void startTripleHold() {
        tripleHoldActive = true;
        tripleCommitted = false;
        tripleHoldStartMs = SystemClock.uptimeMillis();
        tripleLikeWasLiked = currentItem != null && currentItem.liked;
        likeButton.setIconColor(BILI_PINK);
        coinButton.setHoldProgress(0f);
        favoriteButton.setHoldProgress(0f);
        uiHandler.post(tripleHoldTick);
    }

    private final Runnable tripleHoldTick = new Runnable() {
        @Override
        public void run() {
            if (!tripleHoldActive || tripleCommitted) return;
            float progress = Math.min(1f, (SystemClock.uptimeMillis() - tripleHoldStartMs) / 2000f);
            coinButton.setHoldProgress(progress);
            favoriteButton.setHoldProgress(progress);
            if (progress >= 1f) {
                commitTripleHold();
                finishTripleHoldVisuals(false);
                return;
            }
            uiHandler.postDelayed(this, 16);
        }
    };

    private void commitTripleHold() {
        if (tripleCommitted || currentItem == null) return;
        tripleCommitted = true;
        tripleHoldActive = false;
        likeCurrentFromGesture();
        showTripleLikeAnimation();
        Toast.makeText(this, "投币/收藏接口未接入，一键三连仅提交点赞", Toast.LENGTH_SHORT).show();
    }

    private void cancelTripleHoldVisuals(boolean animateBack) {
        tripleHoldActive = false;
        tripleCommitted = false;
        uiHandler.removeCallbacks(tripleHoldTick);
        if (!tripleLikeWasLiked && currentItem != null && !currentItem.liked) {
            likeButton.setIconColor(Color.WHITE);
        }
        if (tripleLikeWasLiked) likeButton.setIconColor(BILI_PINK);
        if (animateBack) {
            animateHoldProgressToZero(coinButton);
            animateHoldProgressToZero(favoriteButton);
        } else {
            coinButton.setHoldProgress(0f);
            favoriteButton.setHoldProgress(0f);
        }
    }

    private void finishTripleHoldVisuals(boolean resetLike) {
        tripleHoldActive = false;
        uiHandler.removeCallbacks(tripleHoldTick);
        coinButton.setHoldProgress(0f);
        favoriteButton.setHoldProgress(0f);
        if (resetLike && !tripleLikeWasLiked) likeButton.setIconColor(Color.WHITE);
    }

    private void animateHoldProgressToZero(RailActionButton button) {
        if (button == null) return;
        ValueAnimator animator = ValueAnimator.ofFloat(button.getHoldProgress(), 0f);
        animator.setDuration(180);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> button.setHoldProgress((float) animation.getAnimatedValue()));
        animator.start();
    }

    private void likeCurrentFromGesture() {
        likeCurrentFromGesture(true);
    }

    private void likeCurrentFromGesture(boolean animateButton) {
        if (currentItem == null) return;
        if (!currentItem.liked) {
            currentItem.liked = true;
            currentItem.likeCount++;
        }
        likeButton.setCount(formatCount(currentItem.likeCount));
        likeButton.setIconColor(BILI_PINK);
        if (landscapeLikeButton != null) {
            landscapeLikeButton.setCount(formatCount(currentItem.likeCount));
            landscapeLikeButton.setIconColor(BILI_PINK);
        }
        if (animateButton) likeButton.playLottieAnimation();
        showCenter("已点赞");
        if (!repository.apiClient().hasAuthCookie()) {
            Toast.makeText(this, "未登录：只做本地点亮", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                repository.apiClient().likeVideo(currentItem);
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, "点赞失败：" + error.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showReactionParticles(View anchor, String[] glyphs, int color) {
        if (anchor == null || root == null) return;
        int[] rootPos = new int[2];
        int[] anchorPos = new int[2];
        root.getLocationOnScreen(rootPos);
        anchor.getLocationOnScreen(anchorPos);
        float centerX = anchorPos[0] - rootPos[0] + anchor.getWidth() / 2f;
        float centerY = anchorPos[1] - rootPos[1] + anchor.getHeight() / 2f;
        showReactionParticlesAt(centerX, centerY, glyphs, color);
    }

    private void showReactionParticlesAt(float centerX, float centerY, String[] glyphs, int color) {
        if (root == null || glyphs == null || glyphs.length == 0) return;
        final int[][] offsets = new int[][]{
                {-38, -34}, {-18, -54}, {8, -48}, {34, -36},
                {-44, -8}, {42, -10}, {-26, 18}, {24, 20},
                {-6, -72}, {54, 8}
        };
        for (int i = 0; i < offsets.length; i++) {
            TextView particle = text(glyphs[i % glyphs.length], i % 3 == 0 ? 18 : 15, color, Typeface.BOLD);
            particle.setGravity(Gravity.CENTER);
            particle.setIncludeFontPadding(false);
            particle.setAlpha(0f);
            particle.setScaleX(0.35f);
            particle.setScaleY(0.35f);
            particle.setRotation(i % 2 == 0 ? -10f : 10f);
            particle.setShadowLayer(dp(4), 0, dp(1), 0x99000000);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(34), dp(34));
            params.leftMargin = Math.round(centerX) - dp(17);
            params.topMargin = Math.round(centerY) - dp(17);
            root.addView(particle, params);

            final float targetX = dp(offsets[i][0]);
            final float targetY = dp(offsets[i][1]);
            final float rotate = i % 2 == 0 ? -24f : 24f;
            particle.animate()
                    .alpha(1f)
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .translationX(targetX * 0.35f)
                    .translationY(targetY * 0.35f)
                    .rotation(0f)
                    .setStartDelay(i * 18L)
                    .setDuration(110)
                    .withEndAction(() -> particle.animate()
                            .alpha(0f)
                            .translationX(targetX)
                            .translationY(targetY - dp(26))
                            .scaleX(0.65f)
                            .scaleY(0.65f)
                            .rotation(rotate)
                            .setDuration(360)
                            .withEndAction(() -> {
                                ViewGroup parent = (ViewGroup) particle.getParent();
                                if (parent != null) parent.removeView(particle);
                            })
                            .start())
                    .start();
        }
    }

    private void toggleVideoDisplayMode() {
        if (uiHidden && !landscapeMode) {
            exitLightControlsMode();
            return;
        }
        if (currentItem != null && currentItem.isHorizontal() && !landscapeMode) {
            enterLandscapeMode();
            return;
        }
        if (landscapeMode) {
            exitLandscapeMode();
            return;
        }
        enterLightControlsMode();
    }

    private void enterLandscapeMode() {
        landscapeMode = true;
        uiHidden = true;
        clearScreenMode = false;
        applySystemBarsForMode();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        setOverlayVisibility(false);
        setLightControlsVisible(false);
        updateFullscreenButtonIcon();
        fullscreenButton.setVisibility(View.GONE);
    }

    private void exitLandscapeMode() {
        landscapeMode = false;
        uiHidden = false;
        clearScreenMode = false;
        landscapeProgressDragging = false;
        landscapeVerticalAdjusting = false;
        hideProgressBubbleLater(0);
        uiHandler.removeCallbacks(hideLightControlsRunnable);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        applySystemBarsForMode();
        setOverlayVisibility(true);
        updateFullscreenButtonIcon();
        if (root != null) root.post(this::applyResponsivePortraitLayout);
    }

    private void applySystemBarsForMode() {
        Window window = getWindow();
        if (landscapeMode) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void toggleLandscapeControls() {
        if (!landscapeMode) return;
        boolean visible = lightControls != null && lightControls.getVisibility() == View.VISIBLE;
        uiHandler.removeCallbacks(hideLightControlsRunnable);
        setLightControlsVisible(!visible);
    }

    private void scheduleLightControlsHide(long delayMs) {
        uiHandler.removeCallbacks(hideLightControlsRunnable);
        uiHandler.postDelayed(hideLightControlsRunnable, Math.max(0L, delayMs));
    }

    private void enterCleanScreenMode() {
        enterLightControlsMode();
    }

    private void enterLightControlsMode() {
        uiHidden = true;
        clearScreenMode = false;
        setOverlayVisibility(false);
        setLightControlsVisible(false);
        updateFullscreenButtonIcon();
        showPortraitHiddenToggle();
    }

    private void exitLightControlsMode() {
        clearScreenMode = false;
        uiHidden = false;
        setLightControlsVisible(false);
        setOverlayVisibility(true);
        updateFullscreenButtonIcon();
    }

    private void setLightControlsVisible(boolean visible) {
        visible = visible && landscapeMode;
        if (lightControls != null) {
            lightControls.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                lightControls.bringToFront();
                lightControls.post(() -> applyLandscapeControlsLayout(lightControls));
            }
        }
        if (lightWatchingView != null) {
            lightWatchingView.setVisibility(visible && !landscapeMode ? View.VISIBLE : View.GONE);
            if (visible) lightWatchingView.bringToFront();
        }
        if (fullscreenButton != null && visible) {
            fullscreenButton.setVisibility(landscapeMode ? View.GONE : View.VISIBLE);
            if (!landscapeMode) fullscreenButton.bringToFront();
        }
        if (visible) updateLightControlsPlaybackState();
    }

    private void showPortraitHiddenToggle() {
        if (fullscreenButton == null || landscapeMode) return;
        fullscreenButton.setVisibility(View.VISIBLE);
        fullscreenButton.bringToFront();
    }

    private void updateFullscreenButtonIcon() {
        if (fullscreenButton == null) return;
        updateDisplayModeButtonIcon(fullscreenButton, currentItem, uiHidden || clearScreenMode);
    }

    private void updateDisplayModeButtonIcon(DisplayModeIconButton button, FeedItem item, boolean hidden) {
        if (button == null) return;
        if (landscapeMode) {
            button.setFallbackFullscreenIcon();
        } else if (item != null && item.isHorizontal()) {
            button.setRasterIcon(R.drawable.ic_bili_switch_landscape);
        } else {
            button.setRasterIcon(hidden ? R.drawable.ic_bili_vertical_ui_off : R.drawable.ic_bili_vertical_ui_on);
        }
    }

    private void setOverlayVisibility(boolean visible) {
        if (visible) setLightControlsVisible(false);
        if (topShade != null) topShade.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (rightShade != null) rightShade.setVisibility(visible ? View.VISIBLE : View.GONE);
        topBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        actionRail.setVisibility(visible ? View.VISIBLE : View.GONE);
        bottomInfo.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (fullscreenButton != null) {
            fullscreenButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (!visible) hideComments(false);
    }

    private void showComments() {
        if (currentItem == null) return;
        String currentKey = videoIdentity(currentItem);
        boolean hasCachedState = currentKey.equals(commentsVideoKey)
                && commentsList != null
                && commentsList.getChildCount() > 0;
        hideDanmakuActionBox();
        setLightControlsVisible(false);
        if (topShade != null) topShade.setVisibility(View.GONE);
        if (rightShade != null) rightShade.setVisibility(View.GONE);
        topBar.setVisibility(View.GONE);
        actionRail.setVisibility(View.GONE);
        bottomInfo.setVisibility(View.GONE);
        if (fullscreenButton != null) fullscreenButton.setVisibility(View.GONE);
        if (!hasCachedState) {
            hideCommentDetailDrawer(false);
            commentsExpanded = false;
            commentsByTime = false;
            updateCommentExpandIcon();
            updateCommentSortLabel();
            setCommentsPanelHeight(commentsHalfHeight(), false);
        } else {
            int preservedHeight = commentsPanel.getLayoutParams() == null
                    ? commentsHalfHeight()
                    : Math.max(1, commentsPanel.getLayoutParams().height);
            setCommentsPanelHeight(preservedHeight, false);
            updateCommentChromeForDetailState();
        }
        commentsPanel.bringToFront();
        commentsPanel.animate().cancel();
        if (commentsPanelAnimator != null) commentsPanelAnimator.cancel();
        if (videoViewportAnimator != null) videoViewportAnimator.cancel();
        int height = commentsPanel.getLayoutParams() == null
                ? commentsHalfHeight()
                : Math.max(1, commentsPanel.getLayoutParams().height);
        int finalPanelTop = Math.max(1, rootHeight() - height);
        Rect currentFrame = currentPlayerFrame();
        int startPanelTop = Math.max(finalPanelTop, Math.min(rootHeight(), currentFrame.bottom));
        float startTranslation = Math.max(0f, startPanelTop - finalPanelTop);
        commentsPanel.setTranslationY(startTranslation);
        commentsPanel.setVisibility(View.VISIBLE);
        updateDanmakuChrome();
        animateCommentsPanelTo(startTranslation, 0f, height, 300, null);
        if (!hasCachedState) loadComments(true);
    }

    private void hideComments() {
        hideComments(true);
    }

    private void hideComments(boolean restoreChrome) {
        if (commentsPanel == null) return;
        commentsPanel.animate().cancel();
        clearCommentInputFocus();
        if (commentsPanel.getVisibility() == View.VISIBLE && commentsPanel.getHeight() > 0) {
            if (commentsPanelAnimator != null) commentsPanelAnimator.cancel();
            if (videoViewportAnimator != null) videoViewportAnimator.cancel();
            int height = commentsPanel.getLayoutParams() == null
                    ? commentsPanel.getHeight()
                    : Math.max(1, commentsPanel.getLayoutParams().height);
            int finalPanelTop = Math.max(1, rootHeight() - height);
            float startTranslation = commentsPanel.getTranslationY();
            float endTranslation = Math.max(0f, portraitVideoFrame().bottom - finalPanelTop);
            animateCommentsPanelTo(startTranslation, endTranslation, height, 240, () -> {
                        commentsPanel.setVisibility(View.GONE);
                        commentsPanel.setTranslationY(0);
                        updateDanmakuChrome();
                        if (restoreChrome) {
                            restoreChromeAfterComments();
                        } else {
                            exitCommentsVideoMode();
                            applyVideoResizeMode();
                        }
                    });
        } else {
            commentsPanel.setVisibility(View.GONE);
            commentsPanel.setTranslationY(0);
            updateDanmakuChrome();
            if (restoreChrome) {
                restoreChromeAfterComments();
            } else {
                exitCommentsVideoMode();
                applyVideoResizeMode();
            }
        }
    }

    private void clearCommentInputFocus() {
        if (commentInput != null) {
            commentInput.clearFocus();
            InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (input != null) {
                input.hideSoftInputFromWindow(commentInput.getWindowToken(), 0);
            }
        }
    }

    private void restoreChromeAfterComments() {
        exitCommentsVideoMode();
        applyVideoResizeMode();
        if (!uiHidden && !landscapeMode) {
            if (topShade != null) topShade.setVisibility(View.VISIBLE);
            if (rightShade != null) rightShade.setVisibility(View.VISIBLE);
            topBar.setVisibility(View.VISIBLE);
            actionRail.setVisibility(View.VISIBLE);
            bottomInfo.setVisibility(View.VISIBLE);
            if (fullscreenButton != null) fullscreenButton.setVisibility(View.VISIBLE);
        } else if (uiHidden && !landscapeMode) {
            setOverlayVisibility(false);
            setLightControlsVisible(false);
            showPortraitHiddenToggle();
        }
    }

    private void enterCommentsVideoMode() {
        if (playerView == null) return;
        int panelHeight = commentsPanel != null && commentsPanel.getLayoutParams() != null
                ? commentsPanel.getLayoutParams().height
                : commentsHalfHeight();
        applyPlayerFrame(commentsVideoFrame(panelHeight), commentsVideoResizeMode());
    }

    private void exitCommentsVideoMode() {
        if (playerView == null) return;
        applyVideoResizeMode();
    }

    private void applyVideoResizeMode() {
        if (playerView == null) return;
        if (landscapeMode) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) playerView.getLayoutParams();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.leftMargin = 0;
            params.topMargin = 0;
            params.gravity = Gravity.NO_GRAVITY;
            playerView.setLayoutParams(params);
            playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            return;
        }
        if (commentsPanel != null && commentsPanel.getVisibility() == View.VISIBLE) return;
        applyVideoViewportLayout(playerView, currentItem);
    }

    private void applyVideoViewportLayout(View target, FeedItem item) {
        if (target == null || root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        boolean horizontal = item != null && item.isHorizontal();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) target.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.leftMargin = 0;
        params.gravity = Gravity.NO_GRAVITY;
        if (horizontal) {
            params.topMargin = docY(24.57f);
            params.height = Math.max(1, Math.round(rootWidth() * 9f / 16f));
        } else {
            int top = docY(DESIGN_STATUS_BOTTOM_PCT);
            int bottom = docY(89.90f);
            params.topMargin = top;
            params.height = Math.max(1, bottom - top);
        }
        target.setLayoutParams(params);
        if (target == playerView) {
            playerView.setResizeMode(horizontal
                    ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    : androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        } else if (target instanceof ImageView) {
            ((ImageView) target).setScaleType(horizontal
                    ? ImageView.ScaleType.FIT_CENTER
                    : ImageView.ScaleType.CENTER_CROP);
        }
    }

    private Rect commentsVideoFrame(int panelHeight) {
        return commentsVideoFrameForPanelTop(Math.max(1, rootHeight() - panelHeight));
    }

    private Rect commentsVideoFrameForPanelTop(int panelTop) {
        int rw = rootWidth();
        panelTop = Math.max(1, Math.min(rootHeight(), panelTop));
        boolean horizontal = currentItem != null && currentItem.isHorizontal();
        float aspect = currentItem != null && currentItem.width > 0 && currentItem.height > 0
                ? currentItem.width / (float) currentItem.height
                : (horizontal ? 16f / 9f : 9f / 16f);
        if (horizontal) {
            int height = Math.min(panelTop, Math.max(1, Math.round(rw / Math.max(0.1f, aspect))));
            int top = Math.max(0, panelTop - height);
            return new Rect(0, top, rw, top + height);
        }
        int height = panelTop;
        int width = Math.min(rw, Math.max(1, Math.round(height * Math.max(0.1f, aspect))));
        int left = Math.max(0, (rw - width) / 2);
        return new Rect(left, 0, left + width, height);
    }

    private void animateCommentsPanelTo(float fromTranslation, float toTranslation, int panelHeight,
                                        long durationMs, Runnable endAction) {
        if (commentsPanel == null) return;
        if (commentsPanelAnimator != null) commentsPanelAnimator.cancel();
        int resizeMode = commentsVideoResizeMode();
        if (playerView != null) playerView.setResizeMode(resizeMode);
        if (playerView != null) playerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        commentsPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        int finalPanelTop = Math.max(1, rootHeight() - panelHeight);
        Rect compactFrame = commentsVideoFrameForPanelTop(finalPanelTop);
        Rect normalFrame = portraitVideoFrame();
        float normalTranslation = Math.max(0f, normalFrame.bottom - finalPanelTop);
        commentsPanelAnimator = ValueAnimator.ofFloat(fromTranslation, toTranslation);
        commentsPanelAnimator.setDuration(durationMs);
        commentsPanelAnimator.setInterpolator(swipeInterpolator);
        commentsPanelAnimator.addUpdateListener(animation -> {
            float translation = (Float) animation.getAnimatedValue();
            commentsPanel.setTranslationY(translation);
            applyPlayerFrame(commentsTransitionFrame(compactFrame, normalFrame, translation, normalTranslation), resizeMode);
            if (danmakuLayer != null) danmakuLayer.invalidate();
        });
        commentsPanelAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                boolean current = commentsPanelAnimator == animation;
                if (current) commentsPanelAnimator = null;
                if (current) {
                    if (playerView != null) playerView.setLayerType(View.LAYER_TYPE_NONE, null);
                    commentsPanel.setLayerType(View.LAYER_TYPE_NONE, null);
                }
                if (!canceled && current && endAction != null) endAction.run();
            }

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                canceled = true;
                if (commentsPanelAnimator == animation) commentsPanelAnimator = null;
                if (playerView != null) playerView.setLayerType(View.LAYER_TYPE_NONE, null);
                commentsPanel.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });
        commentsPanelAnimator.start();
    }

    private Rect commentsTransitionFrame(Rect compactFrame, Rect normalFrame,
                                         float translation, float normalTranslation) {
        if (normalTranslation <= 0f) {
            return translation <= 0f ? compactFrame : normalFrame;
        }
        float progress = Math.max(0f, Math.min(1f, translation / normalTranslation));
        return lerpRect(compactFrame, normalFrame, progress);
    }

    private Rect lerpRect(Rect from, Rect to, float progress) {
        return new Rect(
                Math.round(from.left + (to.left - from.left) * progress),
                Math.round(from.top + (to.top - from.top) * progress),
                Math.round(from.right + (to.right - from.right) * progress),
                Math.round(from.bottom + (to.bottom - from.bottom) * progress)
        );
    }

    private int commentsVideoResizeMode() {
        return currentItem != null && currentItem.isHorizontal()
                ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                : androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
    }

    private Rect currentPlayerFrame() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) playerView.getLayoutParams();
        int width = params.width == ViewGroup.LayoutParams.MATCH_PARENT ? rootWidth() : Math.max(1, params.width);
        int height = params.height == ViewGroup.LayoutParams.MATCH_PARENT ? rootHeight() : Math.max(1, params.height);
        return new Rect(Math.max(0, params.leftMargin), Math.max(0, params.topMargin),
                Math.max(0, params.leftMargin) + width, Math.max(0, params.topMargin) + height);
    }

    private Rect portraitVideoFrame() {
        boolean horizontal = currentItem != null && currentItem.isHorizontal();
        if (horizontal) {
            int height = Math.max(1, Math.round(rootWidth() * 9f / 16f));
            int top = docY(24.57f);
            return new Rect(0, top, rootWidth(), top + height);
        }
        int top = docY(DESIGN_STATUS_BOTTOM_PCT);
        int bottom = docY(89.90f);
        return new Rect(0, top, rootWidth(), Math.max(top + 1, bottom));
    }

    private void applyPlayerFrame(Rect frame, int resizeMode) {
        if (playerView == null || frame == null) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) playerView.getLayoutParams();
        params.width = Math.max(1, frame.width());
        params.height = Math.max(1, frame.height());
        params.leftMargin = frame.left;
        params.topMargin = frame.top;
        params.gravity = Gravity.NO_GRAVITY;
        playerView.setLayoutParams(params);
        playerView.setResizeMode(resizeMode);
    }

    private void animatePlayerFrame(Rect target, int resizeMode, long durationMs) {
        if (playerView == null || target == null) return;
        if (videoViewportAnimator != null) videoViewportAnimator.cancel();
        Rect start = currentPlayerFrame();
        playerView.setResizeMode(resizeMode);
        videoViewportAnimator = ValueAnimator.ofFloat(0f, 1f);
        videoViewportAnimator.setDuration(durationMs);
        videoViewportAnimator.setInterpolator(swipeInterpolator);
        videoViewportAnimator.addUpdateListener(animation -> {
            float t = (Float) animation.getAnimatedValue();
            Rect frame = new Rect(
                    Math.round(start.left + (target.left - start.left) * t),
                    Math.round(start.top + (target.top - start.top) * t),
                    Math.round(start.right + (target.right - start.right) * t),
                    Math.round(start.bottom + (target.bottom - start.bottom) * t));
            applyPlayerFrame(frame, resizeMode);
            if (danmakuLayer != null) danmakuLayer.invalidate();
        });
        videoViewportAnimator.start();
    }

    private boolean handleCommentsDrag(MotionEvent event) {
        if (commentsPanel == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (commentsPanelAnimator != null) commentsPanelAnimator.cancel();
                commentsDragStartY = event.getRawY();
                commentsDragStartHeight = commentsPanel.getLayoutParams() == null
                        ? commentsHalfHeight()
                        : commentsPanel.getLayoutParams().height;
                commentsPanel.animate().cancel();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dy = event.getRawY() - commentsDragStartY;
                int half = commentsHalfHeight();
                int full = commentsFullHeight();
                if (dy < 0) {
                    setCommentsPanelHeight(Math.min(full, Math.round(commentsDragStartHeight - dy)), true);
                    commentsPanel.setTranslationY(0);
                } else {
                    int targetHeight = Math.max(half, Math.round(commentsDragStartHeight - dy));
                    setCommentsPanelHeight(targetHeight, true);
                    commentsPanel.setTranslationY(commentsDragStartHeight > half
                            ? Math.max(0, dy - (commentsDragStartHeight - half))
                            : dy);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (commentsPanel.getTranslationY() > commentsPanel.getHeight() * 0.20f) {
                    hideComments();
                } else {
                    int currentHeight = commentsPanel.getLayoutParams() == null
                            ? commentsHalfHeight()
                            : commentsPanel.getLayoutParams().height;
                    int targetHeight = currentHeight > (commentsHalfHeight() + commentsFullHeight()) / 2
                            ? commentsFullHeight()
                            : commentsHalfHeight();
                    commentsExpanded = targetHeight == commentsFullHeight();
                    updateCommentExpandIcon();
                    animateCommentsPanelHeight(targetHeight);
                }
                return true;
            default:
                return true;
        }
    }

    private int commentsRootHeight() {
        return root != null && root.getHeight() > 0
                ? root.getHeight()
                : getResources().getDisplayMetrics().heightPixels;
    }

    private int commentsHalfHeight() {
        return Math.round(commentsRootHeight() * 0.637f);
    }

    private int commentsFullHeight() {
        return Math.round(commentsRootHeight() * 0.92f);
    }

    private void setCommentsPanelHeight(int height, boolean updateVideo) {
        if (commentsPanel == null) return;
        int safeHeight = Math.max(dp(360), Math.min(commentsFullHeight(), height));
        ViewGroup.LayoutParams params = commentsPanel.getLayoutParams();
        if (params == null) {
            params = new FrameLayout.LayoutParams(-1, safeHeight, Gravity.BOTTOM);
        }
        if (params.height != safeHeight) {
            params.height = safeHeight;
            commentsPanel.setLayoutParams(params);
        }
        if (updateVideo && commentsPanel.getVisibility() == View.VISIBLE) {
            enterCommentsVideoMode();
            danmakuLayer.invalidate();
        }
    }

    private void animateCommentsPanelHeight(int targetHeight) {
        if (commentsPanel == null) return;
        int startHeight = commentsPanel.getLayoutParams() == null
                ? commentsHalfHeight()
                : commentsPanel.getLayoutParams().height;
        ValueAnimator animator = ValueAnimator.ofInt(startHeight, targetHeight);
        animator.setDuration(220);
        animator.setInterpolator(swipeInterpolator);
        animator.addUpdateListener(animation -> {
            setCommentsPanelHeight((Integer) animation.getAnimatedValue(), true);
            commentsPanel.setTranslationY(0);
        });
        animator.start();
    }

    private void updateCommentExpandIcon() {
        if (commentExpandButton == null) return;
        commentExpandButton.setImageResource(commentsExpanded ? R.drawable.ic_bili_collapse : R.drawable.ic_bili_expand);
    }

    private void updateCommentSortLabel() {
        if (commentDetailOpen) {
            updateCommentChromeForDetailState();
            return;
        }
        if (commentSortView != null) {
            commentSortView.setText(commentsByTime ? "☰ 按时间" : "☰ 按热度");
            commentSortView.setTextColor(commentsByTime ? BILI_PINK : 0xFF8E9299);
        }
        if (commentSectionTitleView != null) {
            commentSectionTitleView.setText(commentsByTime ? "最新评论" : "热门评论");
        }
    }

    private void updateCommentChromeForDetailState() {
        if (commentDetailOpen) {
            if (commentTitleView != null) commentTitleView.setText("评论详情");
            if (commentSubHeader != null) commentSubHeader.setVisibility(View.GONE);
            return;
        }
        if (commentSubHeader != null) commentSubHeader.setVisibility(View.VISIBLE);
        if (currentItem != null && commentTitleView != null) {
            commentTitleView.setText("评论（" + formatCount(currentItem.replyCount) + "）");
        }
        if (commentSortView != null) {
            commentSortView.setText(commentsByTime ? "☰ 按时间" : "☰ 按热度");
            commentSortView.setTextColor(commentsByTime ? BILI_PINK : 0xFF8E9299);
        }
        if (commentSectionTitleView != null) {
            commentSectionTitleView.setText(commentsByTime ? "最新评论" : "热门评论");
        }
    }

    private void invalidateCommentStateForNewVideo() {
        commentsGeneration++;
        commentsLoading = false;
        commentsVideoKey = "";
        commentsNextOffset = "";
        commentsHasMore = false;
        loadedComments.clear();
        hideCommentDetailDrawer(false);
        if (commentsList != null) commentsList.removeAllViews();
    }

    private void loadComments(boolean showStatus) {
        if (currentItem == null) return;
        commentsGeneration++;
        commentsLoading = false;
        commentsVideoKey = videoIdentity(currentItem);
        commentsNextOffset = "";
        commentsHasMore = false;
        commentInputEverFocused = false;
        commentSentInCurrentSession = false;
        loadedComments.clear();
        if (showStatus) {
            commentsList.removeAllViews();
            commentsList.addView(commentStatus("加载评论..."));
        }
        loadCommentPage(false);
    }

    private void loadMoreComments() {
        if (!commentsHasMore || commentsLoading || currentItem == null) return;
        loadCommentPage(true);
    }

    private void loadCommentPage(boolean append) {
        if (currentItem == null || commentsLoading) return;
        commentsLoading = true;
        int generation = commentsGeneration;
        String videoKey = commentsVideoKey;
        String offset = append ? commentsNextOffset : "";
        boolean byTime = commentsByTime;
        new Thread(() -> {
            try {
                CommentPage page = repository.apiClient().fetchCommentPage(currentItem, 20, byTime, offset);
                runOnUiThread(() -> {
                    if (generation != commentsGeneration || !videoKey.equals(commentsVideoKey)) return;
                    commentsLoading = false;
                    renderCommentPage(page, append);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (generation != commentsGeneration || !videoKey.equals(commentsVideoKey)) return;
                    commentsLoading = false;
                    commentsList.removeAllViews();
                    commentsList.addView(commentStatus("评论加载失败：" + error.getMessage()));
                });
            }
        }).start();
    }

    private void renderCommentPage(CommentPage page, boolean append) {
        if (commentDetailOpen) {
            updateCommentChromeForDetailState();
        } else {
            commentTitleView.setText("评论（" + formatCount(currentItem.replyCount) + "）");
        }
        if (!append) loadedComments.clear();
        if (page != null) {
            loadedComments.addAll(page.comments);
            commentsNextOffset = page.nextOffset;
            commentsHasMore = page.hasMore;
            applyCommentControl(page.control);
        } else {
            commentsNextOffset = "";
            commentsHasMore = false;
        }
        renderComments();
        if (!append && commentsScrollView != null) commentsScrollView.scrollTo(0, 0);
    }

    private void applyCommentControl(CommentControl control) {
        if (control == null) return;
        currentCommentControl.rootInputText = control.rootInputText;
        currentCommentControl.childInputText = control.childInputText;
        currentCommentControl.giveupInputText = control.giveupInputText;
        currentCommentControl.inputDisabled = control.inputDisabled;
        if (commentInput != null) {
            commentInput.setEnabled(!currentCommentControl.inputDisabled);
            if (commentInput.getText() == null || commentInput.getText().toString().trim().isEmpty()) {
                commentInput.setHint(currentCommentControl.rootHint());
            }
        }
        updateCommentSendButton();
    }

    private void updateCommentInputHintAfterFocusLoss() {
        if (commentInput == null) return;
        String text = commentInput.getText() == null ? "" : commentInput.getText().toString().trim();
        if (commentInputEverFocused && !commentSentInCurrentSession && text.isEmpty()) {
            commentInput.setHint(currentCommentControl.giveupHint());
        }
    }

    private void renderComments() {
        commentVoteBindings.clear();
        previewReplyViews.clear();
        commentsList.removeAllViews();
        if (loadedComments.isEmpty()) {
            String debug = repository.apiClient().lastCommentDebug();
            String text = currentItem.replyCount > 0
                    ? "评论接口暂未返回列表\n" + debug
                    : "暂无评论\n" + debug;
            commentsList.addView(commentStatus(text));
            return;
        }
        for (CommentItem comment : loadedComments) {
            commentsList.addView(buildCommentRow(comment, false));
        }
        commentsList.addView(commentLoadMoreView());
    }

    private View commentLoadMoreView() {
        TextView footer = text(commentsHasMore ? "加载更多评论" : "没有更多评论", 15,
                commentsHasMore ? 0xFF3B83A6 : 0xFF9FA4AB,
                commentsHasMore ? Typeface.BOLD : Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(18), 0, dp(24));
        footer.setEnabled(commentsHasMore);
        footer.setOnClickListener(v -> {
            if (!commentsHasMore || commentsLoading) return;
            footer.setText("加载中...");
            loadMoreComments();
        });
        return footer;
    }

    private View buildCommentRow(CommentItem comment, boolean child) {
        return buildCommentRow(comment, child, !child);
    }

    private View buildCommentRow(CommentItem comment, boolean child, boolean allowReplies) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(child ? Math.round(rootWidth() * 0.116f) : 0,
                child ? Math.round(rootWidth() * 0.018f) : Math.round(rootWidth() * 0.033f),
                0,
                child ? Math.round(rootWidth() * 0.020f) : Math.round(rootWidth() * 0.031f));

        int avatarSize = Math.max(1, Math.round(rootWidth() * (child ? 0.058f : 0.078f)));
        FrameLayout avatarBox = new FrameLayout(this);
        avatarBox.setBackground(rounded(child ? 0xFFB8C0CA : 0xFFFFD166, avatarSize / 2));
        TextView avatarFallback = text(userInitial(comment.user), child ? 12 : 14, Color.WHITE, Typeface.BOLD);
        avatarFallback.setGravity(Gravity.CENTER);
        avatarBox.addView(avatarFallback, new FrameLayout.LayoutParams(-1, -1));
        ImageView avatarImage = new ImageView(this);
        avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        makeCircular(avatarImage);
        avatarImage.setOnClickListener(v -> showUserProfileSheet(comment.mid, comment.user, comment.face, comment.level));
        avatarBox.addView(avatarImage, new FrameLayout.LayoutParams(-1, -1));
        loadImageInto(comment.face, avatarImage);
        if (comment.vip) {
            ImageView vipBadge = iconImage(R.drawable.ic_bili_vip, "vip");
            int vipSize = Math.max(1, Math.round(avatarSize * 0.36f));
            FrameLayout.LayoutParams vipParams = new FrameLayout.LayoutParams(vipSize, vipSize);
            vipParams.leftMargin = Math.max(0, Math.round(avatarSize * 0.66f));
            vipParams.topMargin = Math.max(0, Math.round(avatarSize * 0.66f));
            avatarBox.addView(vipBadge, vipParams);
        }
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(avatarSize, avatarSize);
        avatarParams.rightMargin = Math.max(1, Math.round(rootWidth() * 0.030f));
        row.addView(avatarBox, avatarParams);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        boolean upComment = currentItem != null
                && comment.mid != null
                && !comment.mid.isEmpty()
                && comment.mid.equals(currentItem.ownerMid);
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView name = text(fallback(comment.user, "匿名用户"), 14, upComment ? BILI_PINK : 0xFF8B9098, Typeface.NORMAL);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMaxWidth(Math.max(1, Math.round(rootWidth() * (child ? 0.48f : 0.56f))));
        nameRow.addView(name, new LinearLayout.LayoutParams(-2, -2));

        int levelRes = levelIconRes(comment.level);
        if (levelRes != 0) {
            ImageView levelIcon = iconImage(levelRes, comment.level);
            int levelWidth = Math.max(1, Math.round(rootWidth() * (child ? 0.047f : 0.054f)));
            int levelHeight = Math.max(1, Math.round(levelWidth * 0.5f));
            LinearLayout.LayoutParams levelParams = new LinearLayout.LayoutParams(levelWidth, levelHeight);
            levelParams.leftMargin = Math.max(1, Math.round(rootWidth() * 0.014f));
            nameRow.addView(levelIcon, levelParams);
        }
        if (upComment) {
            TextView upBadge = text("UP", 9, Color.WHITE, Typeface.BOLD);
            upBadge.setGravity(Gravity.CENTER);
            upBadge.setIncludeFontPadding(false);
            upBadge.setPadding(Math.max(1, Math.round(rootWidth() * 0.010f)), 0,
                    Math.max(1, Math.round(rootWidth() * 0.010f)), 0);
            upBadge.setBackground(rounded(BILI_PINK, Math.max(1, Math.round(rootWidth() * 0.004f))));
            LinearLayout.LayoutParams upParams = new LinearLayout.LayoutParams(-2, Math.max(1, Math.round(rootWidth() * 0.025f)));
            upParams.leftMargin = Math.max(1, Math.round(rootWidth() * 0.014f));
            nameRow.addView(upBadge, upParams);
        }
        body.addView(nameRow, new LinearLayout.LayoutParams(-1, -2));

        TextView message = text("", child ? 14 : 17, 0xFF24262B, Typeface.NORMAL);
        renderCommentText(message, comment);
        message.setLineSpacing(dp(1), 1.08f);
        message.setPadding(0, dp(7), 0, 0);
        body.addView(message, new LinearLayout.LayoutParams(-1, -2));

        if (!comment.pictureUrls.isEmpty()) {
            LinearLayout.LayoutParams pictureParams = new LinearLayout.LayoutParams(-1, -2);
            pictureParams.topMargin = Math.max(1, Math.round(rootWidth() * 0.018f));
            body.addView(buildCommentPictures(comment), pictureParams);
        }

        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(0, dp(8), 0, 0);
        TextView left = text(comment.ctimeText + locationText(comment.location) + "  回复", 13, 0xFFA7ABB2, Typeface.NORMAL);
        meta.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        meta.addView(buildCommentActionStrip(comment), new LinearLayout.LayoutParams(commentActionStripWidth(), commentActionStripHeight()));
        body.addView(meta, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout repliesBox = new LinearLayout(this);
        repliesBox.setOrientation(LinearLayout.VERTICAL);
        if (allowReplies && !child && (!comment.previewReplies.isEmpty() || comment.replyCount > 0)) {
            repliesBox.setPadding(dp(12), dp(10), dp(12), dp(10));
            repliesBox.setBackground(rounded(0xFFF2F3F5, dp(10)));
            repliesBox.setClickable(true);
            repliesBox.setOnClickListener(v -> showCommentDetail(comment));
            for (CommentItem reply : comment.previewReplies) {
                View preview = buildPreviewReplyRow(comment, reply);
                repliesBox.addView(preview, new LinearLayout.LayoutParams(-1, -2));
            }
            TextView expand = text("共" + comment.replyCount + "条回复  ›", 14, 0xFF3B83A6, Typeface.BOLD);
            expand.setPadding(0, dp(8), 0, 0);
            expand.setOnClickListener(v -> showCommentDetail(comment));
            repliesBox.addView(expand, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(-1, -2);
            boxParams.topMargin = dp(10);
            body.addView(repliesBox, boxParams);
        }

        row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
        wrapper.addView(row, new LinearLayout.LayoutParams(-1, -2));
        if (!child) {
            View divider = new View(this);
            divider.setBackgroundColor(0xFFEDEEF1);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
            dividerParams.leftMargin = dp(50);
            wrapper.addView(divider, dividerParams);
        }
        return wrapper;
    }

    private View buildPreviewReplyRow(CommentItem parent, CommentItem reply) {
        TextView preview = text(fallback(reply.user, "匿名用户") + "：" + displayCommentMessage(reply.message),
                14, 0xFF666D76, Typeface.NORMAL);
        preview.setMaxLines(2);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        preview.setText(buildInlinePreviewReplyText(reply));
        registerPreviewReplyView(reply, preview);
        preview.setOnTouchListener((view, event) -> handlePreviewReplyTouch((TextView) view, parent, reply, event));
        return preview;
    }

    private CharSequence buildInlinePreviewReplyText(CommentItem reply) {
        String content = fallback(reply.user, "匿名用户") + "：" + displayCommentMessage(reply.message);
        SpannableStringBuilder builder = new SpannableStringBuilder(content);
        int separatorStart = builder.length();
        builder.append("\u00A0|\u00A0");
        int separatorEnd = builder.length();
        int iconStart = builder.length();
        builder.append("\uFFFC");
        int iconEnd = builder.length();
        builder.append("\u00A0");
        int countStart = builder.length();
        builder.append(formatCount(reply.like));

        Drawable drawable = getResources().getDrawable(reply.liked
                ? R.drawable.ic_bili_comment_like_active
                : R.drawable.ic_bili_comment_like);
        int iconSize = Math.max(1, Math.round(rootWidth() * 0.034f));
        drawable.setBounds(0, 0, iconSize, iconSize);
        drawable.setAlpha(reply.liked ? 255 : 150);
        builder.setSpan(new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM), iconStart, iconEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new android.text.style.ForegroundColorSpan(0xFF8F949C),
                separatorStart, separatorEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new android.text.style.ForegroundColorSpan(reply.liked ? BILI_PINK : 0xFF8F949C),
                countStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

    private boolean handlePreviewReplyTouch(TextView view, CommentItem parent, CommentItem reply, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) return true;
        if (action == MotionEvent.ACTION_CANCEL) return true;
        if (action != MotionEvent.ACTION_UP) return true;
        CharSequence text = view.getText();
        int actionStart = text == null ? -1 : text.toString().lastIndexOf('|');
        int offset = view.getOffsetForPosition(event.getX(), event.getY());
        if (actionStart >= 0 && offset >= actionStart) {
            reply.liked = !reply.liked;
            reply.like += reply.liked ? 1 : -1;
            if (reply.like < 0) reply.like = 0;
            syncCommentVoteState(reply);
            updateCommentVoteViews(reply);
        } else {
            showCommentDetail(parent);
        }
        return true;
    }

    private void syncCommentVoteState(CommentItem source) {
        if (source == null || source.rpid == null || source.rpid.isEmpty()) return;
        for (CommentItem comment : loadedComments) {
            copyVoteStateIfSame(comment, source);
            for (CommentItem reply : comment.previewReplies) {
                copyVoteStateIfSame(reply, source);
            }
        }
        updateCommentVoteViews(source);
    }

    private void copyVoteStateIfSame(CommentItem target, CommentItem source) {
        if (target == null || target == source || target.rpid == null) return;
        if (!target.rpid.equals(source.rpid)) return;
        target.like = source.like;
        target.liked = source.liked;
        target.disliked = source.disliked;
    }

    private void registerCommentVoteBinding(CommentItem comment, ImageView likeIcon,
                                            TextView likeCount, ImageView dislikeIcon) {
        if (comment == null || comment.rpid == null || comment.rpid.isEmpty()) return;
        List<CommentVoteBinding> bindings = commentVoteBindings.get(comment.rpid);
        if (bindings == null) {
            bindings = new ArrayList<>();
            commentVoteBindings.put(comment.rpid, bindings);
        }
        bindings.add(new CommentVoteBinding(likeIcon, likeCount, dislikeIcon));
    }

    private void registerPreviewReplyView(CommentItem reply, TextView view) {
        if (reply == null || reply.rpid == null || reply.rpid.isEmpty() || view == null) return;
        List<TextView> views = previewReplyViews.get(reply.rpid);
        if (views == null) {
            views = new ArrayList<>();
            previewReplyViews.put(reply.rpid, views);
        }
        views.add(view);
    }

    private void updateCommentVoteViews(CommentItem source) {
        if (source == null || source.rpid == null || source.rpid.isEmpty()) return;
        List<CommentVoteBinding> bindings = commentVoteBindings.get(source.rpid);
        if (bindings != null) {
            for (int i = bindings.size() - 1; i >= 0; i--) {
                CommentVoteBinding binding = bindings.get(i);
                if (!binding.isAttached()) {
                    bindings.remove(i);
                    continue;
                }
                updateCommentVoteState(source, binding.likeIcon, binding.likeCount, binding.dislikeIcon);
            }
        }
        List<TextView> previews = previewReplyViews.get(source.rpid);
        if (previews != null) {
            for (int i = previews.size() - 1; i >= 0; i--) {
                TextView view = previews.get(i);
                if (view == null || view.getParent() == null) {
                    previews.remove(i);
                    continue;
                }
                view.setText(buildInlinePreviewReplyText(source));
            }
        }
    }

    private View buildCommentPictures(CommentItem comment) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int count = Math.min(9, comment.pictureUrls.size());
        if (count == 1) {
            CommentPictureView picture = new CommentPictureView(this);
            picture.setMetadata(commentPictureWidth(comment, 0), commentPictureHeight(comment, 0));
            picture.setSingleLayout(true);
            picture.setOnClickListener(v -> showCommentImageViewer(comment, 0));
            loadImageInto(comment.pictureUrls.get(0), picture);
            grid.addView(picture, new LinearLayout.LayoutParams(1, 1));
            picture.post(() -> applySingleCommentPictureLayout(picture));
            return grid;
        }
        int columns = count == 2 ? 2 : 3;
        for (int i = 0; i < count; i += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.LEFT);
            for (int j = 0; j < columns && i + j < count; j++) {
                int index = i + j;
                CommentPictureView picture = new CommentPictureView(this);
                picture.setMetadata(commentPictureWidth(comment, index), commentPictureHeight(comment, index));
                picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
                picture.setOnClickListener(v -> showCommentImageViewer(comment, index));
                loadImageInto(comment.pictureUrls.get(index), picture);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(1, 1);
                row.addView(picture, params);
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-2, 1);
            grid.addView(row, rowParams);
        }
        grid.post(() -> applyMultiCommentPictureGridLayout(grid, columns));
        return grid;
    }

    private LinearLayout buildCommentActionStrip(CommentItem comment) {
        LinearLayout strip = new LinearLayout(this);
        strip.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        strip.setOrientation(LinearLayout.HORIZONTAL);

        ImageView likeIcon = iconImage(R.drawable.ic_bili_comment_like, "like");
        TextView likeCount = text(formatCount(comment.like), 13, 0xFF7E838B, Typeface.NORMAL);
        likeCount.setGravity(Gravity.CENTER_VERTICAL);
        ImageView dislikeIcon = iconImage(R.drawable.ic_bili_comment_dislike, "dislike");
        View.OnClickListener likeListener = v -> {
            if (comment.liked) {
                comment.liked = false;
                comment.like = Math.max(0, comment.like - 1);
            } else {
                comment.liked = true;
                comment.disliked = false;
                comment.like++;
            }
            syncCommentVoteState(comment);
            updateCommentVoteState(comment, likeIcon, likeCount, dislikeIcon);
        };
        likeIcon.setOnClickListener(likeListener);
        likeCount.setOnClickListener(likeListener);

        int iconSize = commentActionIconSize();
        strip.addView(likeIcon, new LinearLayout.LayoutParams(iconSize, iconSize));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-2, commentActionStripHeight());
        countParams.leftMargin = Math.max(1, Math.round(rootWidth() * 0.008f));
        countParams.rightMargin = Math.max(1, Math.round(rootWidth() * 0.031f));
        strip.addView(likeCount, countParams);

        View.OnClickListener dislikeListener = v -> {
            if (comment.disliked) {
                comment.disliked = false;
            } else {
                if (comment.liked) {
                    comment.liked = false;
                    comment.like = Math.max(0, comment.like - 1);
                }
                comment.disliked = true;
            }
            syncCommentVoteState(comment);
            updateCommentVoteState(comment, likeIcon, likeCount, dislikeIcon);
        };
        dislikeIcon.setOnClickListener(dislikeListener);
        LinearLayout.LayoutParams dislikeParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        dislikeParams.rightMargin = Math.max(1, Math.round(rootWidth() * 0.036f));
        strip.addView(dislikeIcon, dislikeParams);

        ImageView moreIcon = iconImage(R.drawable.ic_bili_more, "more");
        strip.addView(moreIcon, new LinearLayout.LayoutParams(iconSize, iconSize));
        registerCommentVoteBinding(comment, likeIcon, likeCount, dislikeIcon);
        updateCommentVoteState(comment, likeIcon, likeCount, dislikeIcon);
        return strip;
    }

    private void updateCommentVoteState(CommentItem comment, ImageView likeIcon,
                                        TextView likeCount, ImageView dislikeIcon) {
        likeCount.setText(formatCount(comment.like));
        likeCount.setTextColor(comment.liked ? BILI_PINK : 0xFF7E838B);
        likeIcon.setImageResource(comment.liked
                ? R.drawable.ic_bili_comment_like_active
                : R.drawable.ic_bili_comment_like);
        dislikeIcon.setImageResource(comment.disliked
                ? R.drawable.ic_bili_comment_dislike_active
                : R.drawable.ic_bili_comment_dislike);
        likeIcon.clearColorFilter();
        dislikeIcon.clearColorFilter();
        likeIcon.setAlpha(comment.liked ? 1f : 0.74f);
        dislikeIcon.setAlpha(comment.disliked ? 1f : 0.74f);
    }

    private int commentActionStripWidth() {
        return Math.max(1, Math.round(rootWidth() * 0.306f));
    }

    private int commentActionStripHeight() {
        return Math.max(1, Math.round(rootWidth() * 0.052f));
    }

    private int commentActionIconSize() {
        return Math.max(1, Math.round(rootWidth() * 0.042f));
    }

    private int commentPictureWidth(CommentItem comment, int index) {
        if (comment.pictureWidths == null || index < 0 || index >= comment.pictureWidths.size()) return 0;
        Integer value = comment.pictureWidths.get(index);
        return value == null ? 0 : value;
    }

    private int commentPictureHeight(CommentItem comment, int index) {
        if (comment.pictureHeights == null || index < 0 || index >= comment.pictureHeights.size()) return 0;
        Integer value = comment.pictureHeights.get(index);
        return value == null ? 0 : value;
    }

    private void applySingleCommentPictureLayout(CommentPictureView picture) {
        if (picture == null) return;
        int columnWidth = commentPictureColumnWidth(picture);
        float aspect = picture.aspectRatio();
        int targetWidth;
        int targetHeight;
        if (aspect >= 1.20f) {
            targetWidth = Math.round(columnWidth * 0.82f);
            targetHeight = Math.max(1, Math.round(targetWidth / aspect));
            int maxHeight = Math.round(columnWidth * 0.56f);
            if (targetHeight > maxHeight) {
                targetHeight = maxHeight;
                targetWidth = Math.max(1, Math.round(targetHeight * aspect));
            }
        } else if (aspect >= 0.75f) {
            targetWidth = Math.round(columnWidth * 0.56f);
            targetHeight = Math.max(1, Math.round(targetWidth / aspect));
            int maxHeight = Math.round(columnWidth * 0.64f);
            if (targetHeight > maxHeight) targetHeight = maxHeight;
        } else {
            targetWidth = Math.round(columnWidth * 0.48f);
            targetHeight = Math.max(1, Math.round(targetWidth / aspect));
            int maxHeight = Math.round(columnWidth * 0.72f);
            if (targetHeight > maxHeight) {
                targetHeight = maxHeight;
                targetWidth = Math.max(1, Math.round(targetHeight * aspect));
            }
        }
        ViewGroup.LayoutParams params = picture.getLayoutParams();
        if (params != null && (params.width != targetWidth || params.height != targetHeight)) {
            params.width = Math.max(1, targetWidth);
            params.height = Math.max(1, targetHeight);
            picture.setLayoutParams(params);
        }
    }

    private void applyMultiCommentPictureGridLayout(LinearLayout grid, int columns) {
        if (grid == null || columns <= 0) return;
        int columnWidth = commentPictureColumnWidth(grid);
        int gap = Math.max(1, Math.round(columnWidth * 0.014f));
        int side = Math.max(1, Math.round((columnWidth - gap * (columns - 1)) / (float) columns));
        for (int rowIndex = 0; rowIndex < grid.getChildCount(); rowIndex++) {
            View rowView = grid.getChildAt(rowIndex);
            if (!(rowView instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) rowView;
            int rowWidth = side * row.getChildCount() + gap * Math.max(0, row.getChildCount() - 1);
            ViewGroup.LayoutParams rowParams = row.getLayoutParams();
            if (rowParams != null) {
                rowParams.width = rowWidth;
                rowParams.height = side;
                row.setLayoutParams(rowParams);
            }
            for (int i = 0; i < row.getChildCount(); i++) {
                View child = row.getChildAt(i);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
                params.width = side;
                params.height = side;
                params.rightMargin = i == row.getChildCount() - 1 ? 0 : gap;
                child.setLayoutParams(params);
            }
            LinearLayout.LayoutParams rowLp = (LinearLayout.LayoutParams) row.getLayoutParams();
            if (rowLp != null) {
                rowLp.bottomMargin = rowIndex == grid.getChildCount() - 1 ? 0 : gap;
                row.setLayoutParams(rowLp);
            }
        }
    }

    private int commentPictureColumnWidth(View anchor) {
        View parent = anchor == null ? null : (View) anchor.getParent();
        int width = parent != null && parent.getWidth() > 0 ? parent.getWidth() : 0;
        if (width <= 0 && parent != null && parent.getParent() instanceof View) {
            View grandParent = (View) parent.getParent();
            width = grandParent.getWidth();
        }
        if (width <= 0) width = Math.round(rootWidth() * 0.82f);
        return Math.max(1, width);
    }

    private void showCommentImageViewer(CommentItem comment, int index) {
        if (comment.pictureUrls.isEmpty()) return;
        int safeIndex = Math.max(0, Math.min(index, comment.pictureUrls.size() - 1));
        final int[] currentPicture = new int[]{safeIndex};
        Dialog dialog = new Dialog(this);
        FrameLayout viewer = new FrameLayout(this);
        viewer.setBackgroundColor(Color.BLACK);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        viewer.addView(image, new FrameLayout.LayoutParams(-1, -1));
        loadImageInto(comment.pictureUrls.get(currentPicture[0]), image);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(30), dp(14), 0);
        top.setBackgroundColor(0x66000000);
        TextView close = text("×", 32, Color.WHITE, Typeface.NORMAL);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        top.addView(close, new LinearLayout.LayoutParams(dp(46), dp(58)));
        LinearLayout topCenter = new LinearLayout(this);
        topCenter.setOrientation(LinearLayout.VERTICAL);
        topCenter.setGravity(Gravity.CENTER);
        TextView origin = pillText("查看原图", 13, Color.WHITE, 0x88383B42);
        origin.setGravity(Gravity.CENTER);
        origin.setOnClickListener(v -> {
            origin.setText("加载中");
            loadImageInto(comment.pictureUrls.get(currentPicture[0]), image);
            uiHandler.postDelayed(() -> {
                origin.setText("已查看原图");
                Toast.makeText(this, "暂无独立原图地址，已显示当前最高可用图", Toast.LENGTH_SHORT).show();
            }, 280);
        });
        topCenter.addView(origin, new LinearLayout.LayoutParams(dp(132), dp(30)));
        TextView page = text((currentPicture[0] + 1) + "/" + comment.pictureUrls.size(), 12, 0xCCFFFFFF, Typeface.BOLD);
        page.setGravity(Gravity.CENTER);
        topCenter.addView(page, new LinearLayout.LayoutParams(-1, dp(22)));
        top.addView(topCenter, new LinearLayout.LayoutParams(0, dp(58), 1));
        TextView more = text("⋮", 30, Color.WHITE, Typeface.BOLD);
        more.setGravity(Gravity.CENTER);
        more.setOnClickListener(v -> showCommentImageMoreSheet(comment, currentPicture[0]));
        top.addView(more, new LinearLayout.LayoutParams(dp(46), dp(58)));
        viewer.addView(top, new FrameLayout.LayoutParams(-1, dp(88), Gravity.TOP));

        final float[] pictureDownX = new float[1];
        viewer.setOnTouchListener((view, event) -> {
            if (comment.pictureUrls.size() <= 1) return true;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                pictureDownX[0] = event.getRawX();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = event.getRawX() - pictureDownX[0];
                if (Math.abs(dx) > dp(60)) {
                    int next = currentPicture[0] + (dx < 0 ? 1 : -1);
                    if (next >= 0 && next < comment.pictureUrls.size()) {
                        currentPicture[0] = next;
                        image.setImageDrawable(null);
                        loadImageInto(comment.pictureUrls.get(currentPicture[0]), image);
                        page.setText((currentPicture[0] + 1) + "/" + comment.pictureUrls.size());
                        origin.setText("查看原图");
                    } else {
                        Toast.makeText(this, dx < 0 ? "已经是最后一张" : "已经是第一张", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }
            return true;
        });

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(18), dp(14), dp(18), dp(22));
        bottom.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xDD000000}
        ));
        TextView user = text(fallback(comment.user, "匿名用户") + "  " + fallback(comment.ctimeText, ""), 14, Color.WHITE, Typeface.BOLD);
        LinearLayout.LayoutParams userParams = new LinearLayout.LayoutParams(-1, -2);
        userParams.topMargin = dp(6);
        bottom.addView(user, userParams);
        TextView message = text(displayCommentMessage(comment.message), 14, 0xFFE8EAF0, Typeface.NORMAL);
        message.setMaxLines(3);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, -2);
        messageParams.topMargin = dp(6);
        bottom.addView(message, messageParams);
        LinearLayout opRow = new LinearLayout(this);
        opRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView input = pillText("回复 " + fallback(comment.user, "用户"), 14, 0xFFE0E2E8, 0x66383B42);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setOnClickListener(v -> showReplyDraftSheet(comment));
        opRow.addView(input, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView actions = text("👍 " + formatCount(comment.like) + "   👎   ↗", 15, Color.WHITE, Typeface.BOLD);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        opRow.addView(actions, new LinearLayout.LayoutParams(dp(150), dp(42)));
        LinearLayout.LayoutParams opParams = new LinearLayout.LayoutParams(-1, -2);
        opParams.topMargin = dp(12);
        bottom.addView(opRow, opParams);
        viewer.addView(bottom, new FrameLayout.LayoutParams(-1, dp(220), Gravity.BOTTOM));

        dialog.setContentView(viewer);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.black);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void showOwnerProfileSheet() {
        if (currentItem == null) return;
        showUserProfileSheet(currentItem.ownerMid, currentItem.ownerName, currentItem.ownerFace,
                currentItem.ownerFollowerCount > 0 ? formatCount(currentItem.ownerFollowerCount) + "粉丝" : "");
    }

    private void showUserProfileSheet(String mid, String name, String face, String subtitle) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(rounded(0xFFE7E9EE, dp(28)));
        makeCircular(avatar);
        loadImageInto(face, avatar);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        avatarParams.rightMargin = dp(12);
        header.addView(avatar, avatarParams);

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView user = text(fallback(name, "匿名用户"), 18, 0xFF171A1F, Typeface.BOLD);
        names.addView(user, new LinearLayout.LayoutParams(-1, -2));
        String detail = subtitle == null || subtitle.trim().isEmpty() ? (mid == null || mid.isEmpty() ? "B站用户" : "UID " + mid) : subtitle;
        TextView desc = text(detail, 13, 0xFF8B9098, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = dp(3);
        names.addView(desc, descParams);
        header.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView uid = sheetRow((mid == null || mid.isEmpty()) ? "UID    暂无" : "UID    " + mid);
        uid.setOnClickListener(v -> {
            if (mid == null || mid.isEmpty()) {
                Toast.makeText(this, "暂无 UID", Toast.LENGTH_SHORT).show();
            } else {
                copyToClipboard("BiliClean UID", mid);
                Toast.makeText(this, "已复制 UID", Toast.LENGTH_SHORT).show();
            }
        });
        content.addView(uid);

        TextView open = sheetRow("打开 B站空间");
        open.setOnClickListener(v -> openUserSpace(mid));
        content.addView(open);

        TextView copy = sheetRow("复制空间链接");
        copy.setOnClickListener(v -> {
            String link = userSpaceUrl(mid);
            if (link.isEmpty()) {
                Toast.makeText(this, "暂无空间链接", Toast.LENGTH_SHORT).show();
                return;
            }
            copyToClipboard("BiliClean 空间链接", link);
            Toast.makeText(this, "已复制空间链接", Toast.LENGTH_SHORT).show();
        });
        content.addView(copy);

        if (currentItem != null && mid != null && mid.equals(currentItem.ownerMid)) {
            TextView follow = sheetPill(currentFollowed ? "已关注" : "+ 关注");
            LinearLayout.LayoutParams followParams = new LinearLayout.LayoutParams(-1, dp(44));
            followParams.topMargin = dp(10);
            follow.setOnClickListener(v -> followCurrentOwner());
            content.addView(follow, followParams);
        }

        showBottomSheet("用户资料", content, dp(330));
    }

    private String userSpaceUrl(String mid) {
        if (mid == null || mid.trim().isEmpty()) return "";
        return "https://space.bilibili.com/" + mid.trim();
    }

    private void openUserSpace(String mid) {
        String link = userSpaceUrl(mid);
        if (link.isEmpty()) {
            Toast.makeText(this, "暂无空间链接", Toast.LENGTH_SHORT).show();
            return;
        }
        copyToClipboard("BiliClean 空间链接", link);
        Toast.makeText(this, "播放器内不跳空间，已复制空间链接", Toast.LENGTH_SHORT).show();
    }

    private void showCommentImageMoreSheet(CommentItem comment, int pictureIndex) {
        if (comment.pictureUrls.isEmpty()) return;
        int safeIndex = Math.max(0, Math.min(pictureIndex, comment.pictureUrls.size() - 1));
        String url = comment.pictureUrls.get(safeIndex);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView save = sheetRow("保存图片到相册");
        save.setOnClickListener(v -> saveRemoteImageToGallery(url, "BiliClean_comment_"));
        content.addView(save);
        TextView copy = sheetRow("复制图片地址");
        copy.setOnClickListener(v -> {
            copyToClipboard("BiliClean 评论图片", url);
            Toast.makeText(this, "已复制图片地址", Toast.LENGTH_SHORT).show();
        });
        content.addView(copy);
        TextView reply = sheetRow("回复这条评论");
        reply.setOnClickListener(v -> showReplyDraftSheet(comment));
        content.addView(reply);
        TextView info = sheetRow("图片 " + (safeIndex + 1) + "/" + comment.pictureUrls.size());
        info.setOnClickListener(v -> Toast.makeText(this, "当前图片来自评论区接口", Toast.LENGTH_SHORT).show());
        content.addView(info);
        showBottomSheet("图片操作", content, dp(300));
    }

    private void showImageCommentDraftSheet(CommentItem comment) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView hint = text("图片上传接口暂未接入；可以先对这张图发布文字回复。", 14, 0xFF6A707A, Typeface.NORMAL);
        hint.setPadding(0, dp(4), 0, dp(10));
        content.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        addReplyDraftControls(content, comment, "回复这张图片");
        showBottomSheet("图片评论", content, dp(330));
    }

    private void showReplyDraftSheet(CommentItem comment) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        addReplyDraftControls(content, comment, "回复 " + fallback(comment.user, "用户"));
        showBottomSheet("回复评论", content, dp(300));
    }

    private void addReplyDraftControls(LinearLayout content, CommentItem comment, String hintText) {
        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setHint(hintText);
        input.setHintTextColor(0xFF9CA1A8);
        input.setTextColor(0xFF1D1F23);
        input.setTextSize(15);
        input.setPadding(dp(14), dp(8), dp(14), dp(8));
        input.setBackground(rounded(0xFFF0F1F4, dp(18)));
        content.addView(input, new LinearLayout.LayoutParams(-1, dp(82)));

        TextView send = sheetPill("发送回复");
        send.setEnabled(false);
        send.setTextColor(0xFF9CA1A8);
        send.setBackground(rounded(0xFFE8EAF0, dp(22)));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(-1, dp(44));
        sendParams.topMargin = dp(12);
        content.addView(send, sendParams);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasText = s != null && !s.toString().trim().isEmpty();
                send.setEnabled(hasText);
                send.setTextColor(hasText ? Color.WHITE : 0xFF9CA1A8);
                send.setBackground(rounded(hasText ? BILI_PINK : 0xFFE8EAF0, dp(22)));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        send.setOnClickListener(v -> {
            String body = input.getText().toString().trim();
            if (body.isEmpty()) return;
            if (!repository.apiClient().hasAuthCookie()) {
                Toast.makeText(this, "请先登录后回复", Toast.LENGTH_SHORT).show();
                return;
            }
            String message = "回复 @" + fallback(comment.user, "用户") + "：" + body;
            send.setEnabled(false);
            new Thread(() -> {
                try {
                    repository.apiClient().sendComment(currentItem, message);
                    runOnUiThread(() -> {
                        input.setText("");
                        Toast.makeText(this, "回复已发送", Toast.LENGTH_SHORT).show();
                        loadComments(true);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        send.setEnabled(true);
                        Toast.makeText(this, "回复失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        });
    }

    private void saveRemoteImageToGallery(String imageUrl, String prefix) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            Toast.makeText(this, "图片地址为空", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在保存图片", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = downloadBitmap(imageUrl);
                if (bitmap == null) throw new IllegalStateException("图片下载失败");
                Uri uri = saveBitmapToGallery(bitmap, prefix + System.currentTimeMillis() + ".png");
                runOnUiThread(() -> Toast.makeText(this, uri == null ? "图片保存失败" : "已保存图片到相册/BiliClean", Toast.LENGTH_SHORT).show());
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, "图片保存失败：" + error.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (bitmap != null) bitmap.recycle();
            }
        }).start();
    }

    private void showCommentDetail(CommentItem comment) {
        if (currentItem == null || comment == null || commentDetailDrawer == null || commentDetailList == null) return;
        commentDetailGeneration++;
        int generation = commentDetailGeneration;
        commentDetailOpen = true;
        updateCommentChromeForDetailState();

        commentDetailList.removeAllViews();
        TextView back = text("‹ 返回评论列表", 15, 0xFF3B83A6, Typeface.BOLD);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(0, dp(8), 0, dp(10));
        back.setOnClickListener(v -> hideCommentDetailDrawer(true));
        commentDetailList.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));

        commentDetailList.addView(buildCommentRow(comment, false, false));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(12), 0, dp(8));
        TextView title = text("相关回复共" + formatCount(comment.replyCount) + "条", 15, 0xFF252A31, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1));
        commentDetailList.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout replies = new LinearLayout(this);
        replies.setOrientation(LinearLayout.VERTICAL);
        replies.addView(commentStatus("加载回复..."));
        commentDetailList.addView(replies, new LinearLayout.LayoutParams(-1, -2));
        if (commentDetailScrollView != null) commentDetailScrollView.scrollTo(0, 0);

        int travel = Math.max(1, commentsContentFrame == null || commentsContentFrame.getHeight() <= 0
                ? rootHeight()
                : commentsContentFrame.getHeight());
        commentDetailDrawer.animate().cancel();
        if (commentDetailDrawer.getVisibility() != View.VISIBLE) {
            commentDetailDrawer.setTranslationX(0f);
            commentDetailDrawer.setTranslationY(travel);
            commentDetailDrawer.setVisibility(View.VISIBLE);
        }
        commentDetailDrawer.bringToFront();
        commentDetailDrawer.animate()
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(swipeInterpolator)
                .start();

        new Thread(() -> {
            try {
                List<CommentItem> items = repository.apiClient().fetchCommentReplies(currentItem, comment.rpid, 50);
                runOnUiThread(() -> {
                    if (generation != commentDetailGeneration || !commentDetailOpen) return;
                    replies.removeAllViews();
                    if (items.isEmpty()) {
                        replies.addView(commentStatus("暂无相关回复"));
                        return;
                    }
                    for (CommentItem reply : items) {
                        replies.addView(buildCommentRow(reply, true, false));
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (generation != commentDetailGeneration || !commentDetailOpen) return;
                    replies.removeAllViews();
                    replies.addView(commentStatus("回复加载失败：" + error.getMessage()));
                });
            }
        }).start();
    }

    private void hideCommentDetailDrawer(boolean animate) {
        if (commentDetailDrawer == null) {
            commentDetailOpen = false;
            updateCommentChromeForDetailState();
            return;
        }
        commentDetailGeneration++;
        if (!commentDetailOpen && commentDetailDrawer.getVisibility() != View.VISIBLE) return;
        commentDetailOpen = false;
        updateCommentChromeForDetailState();
        int travel = Math.max(1, commentsContentFrame == null || commentsContentFrame.getHeight() <= 0
                ? rootHeight()
                : commentsContentFrame.getHeight());
        commentDetailDrawer.animate().cancel();
        if (!animate) {
            commentDetailDrawer.setVisibility(View.GONE);
            commentDetailDrawer.setTranslationX(0f);
            commentDetailDrawer.setTranslationY(travel);
            return;
        }
        commentDetailDrawer.animate()
                .translationY(travel)
                .setDuration(220)
                .setInterpolator(swipeInterpolator)
                .withEndAction(() -> {
                    if (commentDetailOpen) return;
                    commentDetailDrawer.setVisibility(View.GONE);
                    commentDetailDrawer.setTranslationX(0f);
                })
                .start();
    }

    private void expandReplies(CommentItem comment, LinearLayout repliesBox) {
        repliesBox.removeAllViews();
        repliesBox.addView(text("加载回复...", 15, 0xFF8B9098, Typeface.NORMAL));
        new Thread(() -> {
            try {
                List<CommentItem> replies = repository.apiClient().fetchCommentReplies(currentItem, comment.rpid, 30);
                runOnUiThread(() -> {
                    repliesBox.removeAllViews();
                    for (CommentItem reply : replies) {
                        repliesBox.addView(buildCommentRow(reply, true));
                    }
                    if (replies.isEmpty()) repliesBox.addView(text("没有更多回复", 15, 0xFF8B9098, Typeface.NORMAL));
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    repliesBox.removeAllViews();
                    repliesBox.addView(text("回复加载失败：" + error.getMessage(), 15, 0xFF8B9098, Typeface.NORMAL));
                });
            }
        }).start();
    }

    private void updateCommentSendButton() {
        if (commentInput == null || sendCommentButton == null) return;
        boolean hasText = !commentInput.getText().toString().trim().isEmpty();
        boolean enabled = hasText && !currentCommentControl.inputDisabled;
        ViewGroup.LayoutParams rawParams = sendCommentButton.getLayoutParams();
        if (rawParams instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rawParams;
            int targetWidth = hasText ? dp(68) : dp(40);
            if (params.width != targetWidth || params.height != dp(40)) {
                params.width = targetWidth;
                params.height = dp(40);
                sendCommentButton.setLayoutParams(params);
            }
        }
        sendCommentButton.setEnabled(enabled);
        sendCommentButton.setText(hasText ? "发送" : "☻");
        sendCommentButton.setTextSize(hasText ? 14 : 22);
        sendCommentButton.setTextColor(hasText ? (enabled ? Color.WHITE : 0xFF9CA1A8) : 0xFF8F949C);
        sendCommentButton.setBackground(rounded(hasText ? (enabled ? BILI_PINK : 0xFFE8EAF0) : 0xFFF0F1F4, dp(20)));
    }

    private void sendComment() {
        if (currentItem == null) return;
        String message = commentInput.getText().toString().trim();
        if (message.isEmpty()) {
            updateCommentSendButton();
            return;
        }
        if (!repository.apiClient().hasAuthCookie()) {
            Toast.makeText(this, "请先登录后评论", Toast.LENGTH_SHORT).show();
            return;
        }
        sendCommentButton.setEnabled(false);
        new Thread(() -> {
            try {
                repository.apiClient().sendComment(currentItem, message);
                runOnUiThread(() -> {
                    commentSentInCurrentSession = true;
                    commentInput.setText("");
                    commentInput.setHint(currentCommentControl.rootHint());
                    updateCommentSendButton();
                    Toast.makeText(this, "评论已发送", Toast.LENGTH_SHORT).show();
                    loadComments(true);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    updateCommentSendButton();
                    Toast.makeText(this, "评论失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showDanmakuInput() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(danmakuVisible ? "发个友善的弹幕见证当下" : "弹幕已隐藏，发送后会自动显示");
        input.setHintTextColor(0xFF9CA1A8);
        input.setTextColor(0xFF1D1F23);
        input.setTextSize(15);
        input.setPadding(dp(16), 0, dp(16), 0);
        input.setBackground(rounded(0xFFF0F1F4, dp(22)));
        content.addView(input, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(0, dp(12), 0, 0);
        String[] toolNames = new String[]{"表情", colorToolLabel(), sizeToolLabel(), modeToolLabel()};
        for (String name : toolNames) {
            TextView tool = text(name, 14, 0xFF6A707A, Typeface.BOLD);
            tool.setGravity(Gravity.CENTER);
            tool.setBackground(rounded(0xFFF3F4F7, dp(18)));
            tool.setOnClickListener(v -> handleDanmakuDraftTool(tool, input));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1);
            params.rightMargin = dp(8);
            tools.addView(tool, params);
        }
        content.addView(tools, new LinearLayout.LayoutParams(-1, -2));

        TextView send = sheetPill("发送弹幕");
        send.setEnabled(false);
        send.setTextColor(0xFF9CA1A8);
        send.setBackground(rounded(0xFFE8EAF0, dp(22)));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(-1, dp(44));
        sendParams.topMargin = dp(14);
        content.addView(send, sendParams);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasText = s != null && !s.toString().trim().isEmpty();
                send.setEnabled(hasText);
                send.setTextColor(hasText ? Color.WHITE : 0xFF9CA1A8);
                send.setBackground(rounded(hasText ? BILI_PINK : 0xFFE8EAF0, dp(22)));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        final Dialog[] holder = new Dialog[1];
        send.setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) return;
            if (!danmakuVisible) toggleDanmaku();
            addSelfDanmakuLine(value, danmakuGeneration);
            if (holder[0] != null) holder[0].dismiss();
            Toast.makeText(this, "弹幕已发送", Toast.LENGTH_SHORT).show();
        });
        holder[0] = showBottomSheet("发弹幕", content, dp(245));
    }

    private void handleDanmakuDraftTool(TextView tool, EditText input) {
        String label = tool.getText() == null ? "" : tool.getText().toString();
        if (label.startsWith("表情")) {
            int start = Math.max(0, input.getSelectionStart());
            input.getText().insert(start, "[doge]");
            return;
        }
        if (label.startsWith("颜色")) {
            int[] colors = new int[]{BILI_PINK, Color.WHITE, 0xFFFFD166, 0xFF7DE3FF, 0xFF82E68B};
            int index = 0;
            for (int i = 0; i < colors.length; i++) {
                if (colors[i] == draftDanmakuColor) {
                    index = i;
                    break;
                }
            }
            draftDanmakuColor = colors[(index + 1) % colors.length];
            tool.setText(colorToolLabel());
            tool.setTextColor(draftDanmakuColor == Color.WHITE ? 0xFF30343A : Color.WHITE);
            tool.setBackground(rounded(draftDanmakuColor == Color.WHITE ? 0xFFF3F4F7 : draftDanmakuColor, dp(18)));
            return;
        }
        if (label.startsWith("字号")) {
            int[] sizes = new int[]{14, 16, 20, 24};
            int index = 0;
            for (int i = 0; i < sizes.length; i++) {
                if (sizes[i] == draftDanmakuTextSp) {
                    index = i;
                    break;
                }
            }
            draftDanmakuTextSp = sizes[(index + 1) % sizes.length];
            tool.setText(sizeToolLabel());
            tool.setTextSize(Math.min(18, draftDanmakuTextSp));
            return;
        }
        if (label.startsWith("模式")) {
            draftDanmakuMode = draftDanmakuMode == 1 ? 5 : (draftDanmakuMode == 5 ? 4 : 1);
            tool.setText(modeToolLabel());
            tool.setTextColor(draftDanmakuMode == 1 ? 0xFF6A707A : BILI_PINK);
        }
    }

    private String colorToolLabel() {
        return "颜色 " + colorName(draftDanmakuColor);
    }

    private String sizeToolLabel() {
        return "字号 " + draftDanmakuTextSp;
    }

    private String modeToolLabel() {
        return "模式 " + danmakuModeName(draftDanmakuMode);
    }

    private String colorName(int color) {
        if (color == Color.WHITE) return "白";
        if (color == BILI_PINK) return "粉";
        if (color == 0xFFFFD166) return "黄";
        if (color == 0xFF7DE3FF) return "蓝";
        if (color == 0xFF82E68B) return "绿";
        return "彩";
    }

    private String danmakuModeName(int mode) {
        if (mode == 5) return "顶部";
        if (mode == 4) return "底部";
        return "滚动";
    }

    private void toggleDanmaku() {
        danmakuVisible = !danmakuVisible;
        updateDanmakuChrome();
        if (danmakuVisible && activeDanmakuEntries.isEmpty()) loadDanmaku();
    }

    private void updateDanmakuChrome() {
        danmakuButton.setImageResource(danmakuVisible ? R.drawable.ic_bili_danmaku_on : R.drawable.ic_bili_danmaku_off);
        danmakuButton.setBackgroundColor(Color.TRANSPARENT);
        if (swipePreviewDanmakuButton != null) {
            swipePreviewDanmakuButton.setImageResource(danmakuVisible ? R.drawable.ic_bili_danmaku_on : R.drawable.ic_bili_danmaku_off);
            swipePreviewDanmakuButton.setBackgroundColor(Color.TRANSPARENT);
        }
        if (lightDanmakuButton != null) {
            lightDanmakuButton.setImageResource(danmakuVisible ? R.drawable.ic_bili_danmaku_on : R.drawable.ic_bili_danmaku_off);
            lightDanmakuButton.setAlpha(danmakuVisible ? 1f : 0.72f);
            lightDanmakuButton.setBackgroundColor(Color.TRANSPARENT);
        }
        if (danmakuInputPill != null) {
            danmakuInputPill.setText(danmakuVisible ? "发个友善的弹幕见证当下" : "弹幕已隐藏");
            danmakuInputPill.setTextColor(danmakuVisible ? 0xFFE1E1E1 : 0xFFB0B4BC);
        }
        if (lightDanmakuInputPill != null) {
            lightDanmakuInputPill.setText(danmakuVisible ? "发弹幕" : "弹幕已隐藏");
            lightDanmakuInputPill.setTextColor(danmakuVisible ? 0xFFE1E1E1 : 0xFFB0B4BC);
        }
        boolean commentsShowing = commentsPanel != null && commentsPanel.getVisibility() == View.VISIBLE;
        boolean showLayer = danmakuVisible && !commentsShowing;
        danmakuLayer.setVisibility(showLayer ? View.VISIBLE : View.GONE);
        danmakuLayer.setAlpha(showLayer ? 1f : 0f);
    }

    private void showDanmakuActionBox(DanmakuSprite sprite, float centerX, float baselineY) {
        if (sprite == null || root == null) return;
        selectedDanmakuSprite = sprite;
        if (danmakuActionBox == null) {
            danmakuActionBox = new LinearLayout(this);
            danmakuActionBox.setGravity(Gravity.CENTER);
            danmakuActionBox.setPadding(dp(6), 0, dp(6), 0);
            danmakuActionBox.setBackground(rounded(0xE61B1D22, dp(22)));
            danmakuActionBox.setElevation(dp(8));
            danmakuActionBox.addView(danmakuActionButton("赞", v ->
                    Toast.makeText(this, "弹幕点赞接口暂未接入", Toast.LENGTH_SHORT).show()));
            danmakuActionBox.addView(danmakuActionButton("复制", v -> {
                if (selectedDanmakuSprite != null) {
                    copyToClipboard("BiliClean 弹幕", selectedDanmakuSprite.text);
                    Toast.makeText(this, "已复制弹幕", Toast.LENGTH_SHORT).show();
                }
                hideDanmakuActionBox();
            }));
            danmakuActionBox.addView(danmakuActionButton("举报", v -> {
                Toast.makeText(this, "弹幕举报接口暂未接入", Toast.LENGTH_SHORT).show();
                hideDanmakuActionBox();
            }));
            root.addView(danmakuActionBox, new FrameLayout.LayoutParams(dp(174), dp(46)));
        }
        int boxWidth = dp(174);
        int boxHeight = dp(46);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) danmakuActionBox.getLayoutParams();
        params.width = boxWidth;
        params.height = boxHeight;
        int maxLeft = Math.max(0, root.getWidth() - boxWidth - dp(8));
        int maxTop = Math.max(0, root.getHeight() - boxHeight - dp(16));
        params.leftMargin = Math.max(dp(8), Math.min(maxLeft, Math.round(centerX - boxWidth / 2f)));
        params.topMargin = Math.max(dp(88), Math.min(maxTop, Math.round(baselineY + dp(10))));
        danmakuActionBox.setLayoutParams(params);
        danmakuActionBox.setVisibility(View.VISIBLE);
        danmakuActionBox.bringToFront();
        uiHandler.postDelayed(() -> {
            if (selectedDanmakuSprite == sprite) hideDanmakuActionBox();
        }, 2100);
    }

    private TextView danmakuActionButton(String label, View.OnClickListener listener) {
        TextView button = text(label, 13, Color.WHITE, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(listener);
        button.setBackground(rounded(0x00000000, dp(18)));
        button.setIncludeFontPadding(false);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(54), dp(38)));
        return button;
    }

    private void hideDanmakuActionBox() {
        if (selectedDanmakuSprite != null) {
            selectedDanmakuSprite.selected = false;
            selectedDanmakuSprite.selectedUntilWallMs = 0L;
            selectedDanmakuSprite = null;
        }
        if (danmakuActionBox != null) {
            danmakuActionBox.setVisibility(View.GONE);
        }
        if (danmakuLayer != null) danmakuLayer.invalidate();
    }

    private void showDanmakuSettings() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        content.addView(danmakuSwitchRow("弹幕开关", danmakuVisible, "关闭后仅隐藏，弹幕仍按播放进度推进", checked -> toggleDanmaku()));
        content.addView(danmakuPercentSlider("不透明度", danmakuOpacityPercent, 0, 100, "%", value -> {
            danmakuOpacityPercent = value;
            danmakuLayer.invalidate();
        }));
        content.addView(danmakuPercentSlider("显示区域", danmakuAreaPercent, 25, 100, "%", value -> {
            danmakuAreaPercent = value;
            restartDanmakuWithCurrentSettings();
        }));
        content.addView(danmakuPercentSlider("字体大小", danmakuTextSp, 14, 24, "sp", value -> {
            danmakuTextSp = value;
            restartDanmakuWithCurrentSettings();
        }));
        content.addView(danmakuSegmentSlider("弹幕速度", danmakuSpeedIndex(), new String[]{"慢", "适中", "快"}, index -> {
            danmakuDelayMs = index == 0 ? 900 : index == 1 ? 680 : 450;
            restartDanmakuWithCurrentSettings();
        }));
        content.addView(danmakuSegmentSlider("密度", danmakuDensityIndex(), new String[]{"低", "中", "高"}, index -> {
            danmakuMaxRows = index == 0 ? 4 : index == 1 ? 7 : 10;
            restartDanmakuWithCurrentSettings();
        }));

        content.addView(sheetSection("按弹幕类型屏蔽"));
        content.addView(danmakuTypeGrid());

        content.addView(danmakuSwitchRow("彩色弹幕", colorfulDanmaku, "关闭后彩色弹幕以白色显示", checked -> {
            colorfulDanmaku = checked;
            danmakuLayer.invalidate();
        }));
        content.addView(danmakuSwitchRow("重复弹幕隐藏", hideRepeatedDanmaku, "大量内容相同的弹幕会被隐藏", checked -> {
            hideRepeatedDanmaku = checked;
            restartDanmakuWithCurrentSettings();
        }));
        content.addView(danmakuSwitchRow("人像防挡", portraitProtectDanmaku, "预留人物区域，减少底部遮挡", checked -> {
            portraitProtectDanmaku = checked;
            restartDanmakuWithCurrentSettings();
        }));
        content.addView(danmakuSwitchRow("弹幕观看屏蔽词", keywordShieldDanmaku, "仅对我不展示特定弹幕", checked -> {
            keywordShieldDanmaku = checked;
            restartDanmakuWithCurrentSettings();
        }));
        content.addView(danmakuKeywordEditor());

        TextView reset = sheetRow("重置弹幕设置");
        reset.setTextColor(BILI_PINK);
        reset.setOnClickListener(v -> {
            danmakuOpacityPercent = 90;
            danmakuAreaPercent = 100;
            danmakuTextSp = 16;
            danmakuDelayMs = 520;
            danmakuMaxRows = 10;
            colorfulDanmaku = true;
            blockFixedDanmaku = false;
            blockRollingDanmaku = false;
            blockColorDanmaku = false;
            blockAdvancedDanmaku = false;
            blockCountDanmaku = false;
            hideRepeatedDanmaku = false;
            portraitProtectDanmaku = true;
            keywordShieldDanmaku = true;
            danmakuShieldWords.clear();
            emittedDanmakuTexts.clear();
            updateDanmakuChrome();
            restartDanmakuWithCurrentSettings();
            Toast.makeText(this, "已重置弹幕设置", Toast.LENGTH_SHORT).show();
        });
        content.addView(reset);

        showBottomSheet("弹幕设置", content, dp(620));
    }

    private int danmakuSpeedIndex() {
        if (danmakuDelayMs > 680) return 0;
        if (danmakuDelayMs < 680) return 2;
        return 1;
    }

    private int danmakuDensityIndex() {
        if (danmakuMaxRows < 7) return 0;
        if (danmakuMaxRows > 7) return 2;
        return 1;
    }

    private View danmakuKeywordEditor() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(6), dp(6), dp(6), dp(10));
        box.setBackground(rounded(0xFFF7F8FA, dp(12)));

        TextView summary = text(danmakuShieldSummary(), 13, 0xFF777D86, Typeface.NORMAL);
        box.addView(summary, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("添加屏蔽词，如剧透");
        input.setTextSize(14);
        input.setHintTextColor(0xFF9CA1A8);
        input.setTextColor(0xFF252A31);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(rounded(0xFFEFF1F5, dp(18)));
        row.addView(input, new LinearLayout.LayoutParams(0, dp(38), 1));

        TextView add = pillText("添加", 14, Color.WHITE, BILI_PINK);
        add.setContentDescription("添加弹幕屏蔽词");
        add.setOnClickListener(v -> {
            String word = input.getText().toString().trim();
            if (word.isEmpty()) {
                Toast.makeText(this, "先输入一个屏蔽词", Toast.LENGTH_SHORT).show();
                return;
            }
            danmakuShieldWords.add(word);
            input.setText("");
            summary.setText(danmakuShieldSummary());
            restartDanmakuWithCurrentSettings();
            Toast.makeText(this, "已添加屏蔽词：" + word, Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(72), dp(38));
        addParams.leftMargin = dp(8);
        row.addView(add, addParams);
        box.addView(row, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView clear = text("清空屏蔽词", 13, BILI_PINK, Typeface.BOLD);
        clear.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        clear.setContentDescription("清空弹幕屏蔽词");
        clear.setOnClickListener(v -> {
            if (danmakuShieldWords.isEmpty()) {
                Toast.makeText(this, "当前没有屏蔽词", Toast.LENGTH_SHORT).show();
                return;
            }
            danmakuShieldWords.clear();
            summary.setText(danmakuShieldSummary());
            restartDanmakuWithCurrentSettings();
            Toast.makeText(this, "已清空屏蔽词", Toast.LENGTH_SHORT).show();
        });
        box.addView(clear, new LinearLayout.LayoutParams(-1, dp(34)));
        return box;
    }

    private String danmakuShieldSummary() {
        if (danmakuShieldWords.isEmpty()) return "屏蔽词：暂无";
        List<String> words = new ArrayList<>(danmakuShieldWords);
        words.sort(String::compareTo);
        return "屏蔽词：" + String.join("、", words);
    }

    private void restartDanmakuWithCurrentSettings() {
        if (danmakuLayer == null) return;
        List<DanmakuEntry> entries = new ArrayList<>(activeDanmakuEntries);
        int generation = ++danmakuGeneration;
        activeDanmakuEntries = entries;
        nextDanmakuEntryIndex = 0;
        lastDanmakuPositionMs = 0L;
        emittedDanmakuTexts.clear();
        java.util.Arrays.fill(danmakuRowReadyAtMs, 0L);
        danmakuLayer.clear();
        if (!danmakuEnabled) return;
        if (entries.isEmpty()) {
            if (currentItem != null) loadDanmaku();
            return;
        }
        danmakuLayer.post(() -> {
            if (generation != danmakuGeneration) return;
            long position = player == null ? 0L : Math.max(0L, player.getCurrentPosition());
            resetDanmakuCursorFor(position);
            primeDanmakuViewport(generation);
            pumpDanmaku(generation);
        });
    }

    private View danmakuPercentSlider(String title, int value, int min, int max, String unit, IntSettingChange onChange) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(10), dp(14), dp(10));
        box.setBackground(rounded(0xFFF5F6F8, dp(14)));
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(-1, -2);
        boxParams.bottomMargin = dp(8);
        box.setLayoutParams(boxParams);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(title, 15, 0xFF252A31, Typeface.BOLD);
        row.addView(name, new LinearLayout.LayoutParams(0, dp(28), 1));
        TextView valueView = text(value + unit, 14, BILI_PINK, Typeface.BOLD);
        valueView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(valueView, new LinearLayout.LayoutParams(dp(86), dp(28)));
        box.addView(row, new LinearLayout.LayoutParams(-1, dp(30)));

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(Math.max(1, max - min));
        seekBar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int next = min + progress;
                valueView.setText(next + unit);
                onChange.onChange(next);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        box.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(42)));
        return box;
    }

    private View danmakuSegmentSlider(String title, int index, String[] labels, IntSettingChange onChange) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(10), dp(14), dp(10));
        box.setBackground(rounded(0xFFF5F6F8, dp(14)));
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(-1, -2);
        boxParams.bottomMargin = dp(8);
        box.setLayoutParams(boxParams);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(title, 15, 0xFF252A31, Typeface.BOLD);
        row.addView(name, new LinearLayout.LayoutParams(0, dp(28), 1));
        TextView valueView = text(labels[Math.max(0, Math.min(labels.length - 1, index))], 14, BILI_PINK, Typeface.BOLD);
        valueView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(valueView, new LinearLayout.LayoutParams(dp(86), dp(28)));
        box.addView(row, new LinearLayout.LayoutParams(-1, dp(30)));

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(Math.max(1, labels.length - 1));
        seekBar.setProgress(Math.max(0, Math.min(labels.length - 1, index)));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int next = Math.max(0, Math.min(labels.length - 1, progress));
                valueView.setText(labels[next]);
                onChange.onChange(next);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        box.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(42)));
        return box;
    }

    private View danmakuSwitchRow(String title, boolean checked, String subtitle, BooleanSettingChange onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(12), dp(8));
        row.setBackground(rounded(0xFFF5F6F8, dp(14)));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(62));
        rowParams.bottomMargin = dp(8);
        row.setLayoutParams(rowParams);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(title, 15, 0xFF252A31, Typeface.BOLD);
        textBox.addView(name, new LinearLayout.LayoutParams(-1, dp(25)));
        TextView desc = text(subtitle, 12, 0xFF8A9099, Typeface.NORMAL);
        desc.setSingleLine(true);
        textBox.addView(desc, new LinearLayout.LayoutParams(-1, dp(21)));
        row.addView(textBox, new LinearLayout.LayoutParams(0, -1, 1));

        TextView pill = text("", 13, Color.WHITE, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        row.addView(pill, new LinearLayout.LayoutParams(dp(54), dp(32)));
        final boolean[] state = new boolean[]{checked};
        updateSwitchPill(pill, state[0]);
        row.setOnClickListener(v -> {
            state[0] = !state[0];
            updateSwitchPill(pill, state[0]);
            onChange.onChange(state[0]);
        });
        return row;
    }

    private void updateSwitchPill(TextView pill, boolean checked) {
        pill.setText(checked ? "开" : "关");
        pill.setBackground(rounded(checked ? BILI_PINK : 0xFFC9CDD3, dp(16)));
        pill.setTextColor(Color.WHITE);
    }

    private View danmakuTypeGrid() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(8));
        row.addView(danmakuTypeChip("固定", blockFixedDanmaku, view -> {
            blockFixedDanmaku = !blockFixedDanmaku;
            updateTypeChip((TextView) view, "固定", blockFixedDanmaku);
            restartDanmakuWithCurrentSettings();
        }));
        row.addView(danmakuTypeChip("滚动", blockRollingDanmaku, view -> {
            blockRollingDanmaku = !blockRollingDanmaku;
            updateTypeChip((TextView) view, "滚动", blockRollingDanmaku);
            restartDanmakuWithCurrentSettings();
        }));
        row.addView(danmakuTypeChip("彩色", blockColorDanmaku, view -> {
            blockColorDanmaku = !blockColorDanmaku;
            updateTypeChip((TextView) view, "彩色", blockColorDanmaku);
            restartDanmakuWithCurrentSettings();
        }));
        row.addView(danmakuTypeChip("高级", blockAdvancedDanmaku, view -> {
            blockAdvancedDanmaku = !blockAdvancedDanmaku;
            updateTypeChip((TextView) view, "高级", blockAdvancedDanmaku);
            restartDanmakuWithCurrentSettings();
        }));
        row.addView(danmakuTypeChip("计数", blockCountDanmaku, view -> {
            blockCountDanmaku = !blockCountDanmaku;
            updateTypeChip((TextView) view, "计数", blockCountDanmaku);
            restartDanmakuWithCurrentSettings();
        }));
        return row;
    }

    private TextView danmakuTypeChip(String title, boolean blocked, View.OnClickListener listener) {
        TextView chip = text("", 12, 0xFF252A31, Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setIncludeFontPadding(false);
        updateTypeChip(chip, title, blocked);
        chip.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(58), 1);
        params.rightMargin = dp(6);
        chip.setLayoutParams(params);
        return chip;
    }

    private void updateTypeChip(TextView chip, String title, boolean blocked) {
        chip.setText(title + "\n" + (blocked ? "已屏蔽" : "显示"));
        chip.setTextColor(blocked ? BILI_PINK : 0xFF30343A);
        chip.setBackground(rounded(blocked ? 0xFFFFE7F0 : 0xFFF2F3F6, dp(12)));
    }

    private interface IntSettingChange {
        void onChange(int value);
    }

    private interface BooleanSettingChange {
        void onChange(boolean checked);
    }

    private void loadDanmaku() {
        danmakuGeneration++;
        int generation = danmakuGeneration;
        activeDanmakuEntries = java.util.Collections.emptyList();
        nextDanmakuEntryIndex = 0;
        lastDanmakuPositionMs = 0L;
        emittedDanmakuTexts.clear();
        java.util.Arrays.fill(danmakuRowReadyAtMs, 0L);
        danmakuLayer.clear();
        if (!danmakuEnabled || currentItem == null) return;
        new Thread(() -> {
            try {
                List<DanmakuEntry> entries = repository.apiClient().fetchDanmakuEntries(currentItem, 360);
                runOnUiThread(() -> startDanmaku(entries, generation));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (generation == danmakuGeneration && danmakuEnabled) {
                        addDanmakuLine("弹幕加载失败", generation, 0);
                    }
                });
            }
        }).start();
    }

    private void startDanmaku(List<DanmakuEntry> entries, int generation) {
        if (generation != danmakuGeneration || !danmakuEnabled) return;
        if (entries.isEmpty()) {
            addDanmakuLine("暂无弹幕", generation, 0);
            return;
        }
        List<DanmakuEntry> sorted = new ArrayList<>(entries);
        sorted.sort((left, right) -> Long.compare(left.timeMs, right.timeMs));
        activeDanmakuEntries = sorted;
        nextDanmakuEntryIndex = 0;
        lastDanmakuPositionMs = 0L;
        emittedDanmakuTexts.clear();
        java.util.Arrays.fill(danmakuRowReadyAtMs, 0L);
        android.util.Log.d("BiliClean", "danmaku visible timed entries=" + sorted.size());
        danmakuLayer.post(() -> {
            primeDanmakuViewport(generation);
            pumpDanmaku(generation);
        });
    }

    private void primeDanmakuViewport(int generation) {
        if (generation != danmakuGeneration || !danmakuEnabled || activeDanmakuEntries.isEmpty()) return;
        long positionMs = player == null ? 0L : Math.max(0L, player.getCurrentPosition());
        int index = 0;
        while (index < activeDanmakuEntries.size() && activeDanmakuEntries.get(index).timeMs < positionMs - 1200L) {
            index++;
        }
        int emitted = 0;
        int limit = Math.max(4, Math.min(7, currentDanmakuRowCount()));
        while (index < activeDanmakuEntries.size() && emitted < limit) {
            DanmakuEntry entry = activeDanmakuEntries.get(index);
            if (entry.timeMs > positionMs + 7000L && emitted > 0) break;
            index++;
            long startOffsetMs = Math.min(currentDanmakuDurationMs(entry.durationMs) - 1200L, 900L + emitted * 520L);
            if (addDanmakuEntry(entry, generation, Math.max(80L, startOffsetMs))) emitted++;
        }
        nextDanmakuEntryIndex = Math.max(nextDanmakuEntryIndex, index);
        android.util.Log.d("BiliClean", "danmaku prime emitted=" + emitted + " pos=" + positionMs);
    }

    private void pumpDanmaku(int generation) {
        uiHandler.postDelayed(() -> {
            if (generation != danmakuGeneration || !danmakuEnabled || activeDanmakuEntries.isEmpty()) return;
            if (player == null) {
                pumpDanmaku(generation);
                return;
            }

            long positionMs = Math.max(0L, player.getCurrentPosition());
            if (lastDanmakuPositionMs > 0L
                    && (positionMs + 1200L < lastDanmakuPositionMs || positionMs - lastDanmakuPositionMs > 2500L)) {
                resetDanmakuCursorFor(positionMs);
                danmakuLayer.clear();
            }
            lastDanmakuPositionMs = positionMs;

            long leadMs = player.isPlaying() ? 1800L : 500L;
            int emitted = 0;
            int burstLimit = currentDanmakuBurstLimit();
            while (nextDanmakuEntryIndex < activeDanmakuEntries.size()) {
                DanmakuEntry entry = activeDanmakuEntries.get(nextDanmakuEntryIndex);
                if (entry.timeMs > positionMs + leadMs) break;
                nextDanmakuEntryIndex++;
                if (entry.timeMs < positionMs - 4200L) continue;
                if (addDanmakuEntry(entry, generation)) emitted++;
                if (emitted >= burstLimit) break;
            }
            pumpDanmaku(generation);
        }, 180);
    }

    private void addDanmakuLine(String value, int generation, int row) {
        if (generation != danmakuGeneration || !danmakuEnabled || value.isEmpty()) return;
        long videoTimeMs = player == null ? 0L : Math.max(0L, player.getCurrentPosition());
        danmakuLayer.addLineAtVideoTime(value, row, Color.WHITE, currentDanmakuDurationMs(7200L), 1, 80L, false, 0, videoTimeMs);
    }

    private void addSelfDanmakuLine(String value, int generation) {
        if (generation != danmakuGeneration || !danmakuEnabled || value.isEmpty()) return;
        DanmakuEntry entry = new DanmakuEntry();
        entry.text = "我：" + value;
        entry.durationMs = 7200L;
        entry.mode = draftDanmakuMode;
        int row = chooseDanmakuRow(entry);
        if (row < 0) row = 0;
        long videoTimeMs = player == null ? 0L : Math.max(0L, player.getCurrentPosition());
        danmakuLayer.addLineAtVideoTime(entry.text, row, draftDanmakuColor, currentDanmakuDurationMs(entry.durationMs), draftDanmakuMode, 80L, true, draftDanmakuTextSp, videoTimeMs);
    }

    private boolean addDanmakuEntry(DanmakuEntry entry, int generation) {
        return addDanmakuEntry(entry, generation, 80L);
    }

    private boolean addDanmakuEntry(DanmakuEntry entry, int generation, long startOffsetMs) {
        if (entry == null || entry.text == null || entry.text.isEmpty()) return false;
        if (generation != danmakuGeneration || !danmakuEnabled) return false;
        if (shouldBlockDanmaku(entry)) return false;
        if (hideRepeatedDanmaku) {
            String normalized = entry.text.trim().replaceAll("\\s+", "");
            if (!normalized.isEmpty() && !emittedDanmakuTexts.add(normalized)) return false;
        }
        int color = colorfulDanmaku ? entry.color : Color.WHITE;
        if ((color & 0x00FFFFFF) == 0) color = Color.WHITE;
        int row = chooseDanmakuRow(entry);
        if (row < 0) return false;
        danmakuLayer.addLineAtVideoTime(entry.text, row, color, currentDanmakuDurationMs(entry.durationMs), entry.mode, startOffsetMs, false, 0, entry.timeMs);
        return true;
    }

    private boolean shouldBlockDanmaku(DanmakuEntry entry) {
        if (blockFixedDanmaku && (entry.mode == 4 || entry.mode == 5)) return true;
        if (blockRollingDanmaku && (entry.mode == 1 || entry.mode == 2 || entry.mode == 3 || entry.mode == 6)) return true;
        if (blockAdvancedDanmaku && entry.mode >= 7) return true;
        if (blockColorDanmaku && (entry.color & 0x00FFFFFF) != 0x00FFFFFF) return true;
        if (blockCountDanmaku && looksLikeCountDanmaku(entry.text)) return true;
        if (keywordShieldDanmaku && shouldBlockByKeyword(entry.text)) return true;
        return false;
    }

    private boolean shouldBlockByKeyword(String value) {
        if (value == null || value.isEmpty() || danmakuShieldWords.isEmpty()) return false;
        String text = value.toLowerCase(Locale.CHINA);
        for (String word : danmakuShieldWords) {
            if (word == null) continue;
            String normalized = word.trim().toLowerCase(Locale.CHINA);
            if (!normalized.isEmpty() && text.contains(normalized)) return true;
        }
        return false;
    }

    private boolean looksLikeCountDanmaku(String value) {
        if (value == null) return false;
        String text = value.trim();
        if (text.length() > 12) return false;
        return text.matches("^[0-9５5]+$")
                || text.matches("^[xX×*][0-9０-９]+$")
                || text.matches("^[一二三四五六七八九十百千万]+$");
    }

    private int chooseDanmakuRow(DanmakuEntry entry) {
        int rowCount = currentDanmakuRowCount();
        boolean fixed = entry != null && (entry.mode == 4 || entry.mode == 5);
        if (fixed) rowCount = 1;
        long now = SystemClock.uptimeMillis();
        int bestRow = 0;
        long bestReadyAt = Long.MAX_VALUE;
        boolean hasReadyRow = false;
        for (int i = 0; i < rowCount; i++) {
            long readyAt = danmakuRowReadyAtMs[i];
            if (readyAt <= now) {
                bestRow = i;
                hasReadyRow = true;
                break;
            }
            if (readyAt < bestReadyAt) {
                bestReadyAt = readyAt;
                bestRow = i;
            }
        }
        if (!hasReadyRow) return -1;
        int textLength = entry.text == null ? 0 : entry.text.length();
        long spacingMs = fixed
                ? currentDanmakuDurationMs(entry.durationMs) + 500L
                : (danmakuMaxRows >= 10
                ? Math.max(420L, Math.min(1500L, 360L + textLength * 24L))
                : Math.max(680L, Math.min(2400L, 620L + textLength * 38L)));
        danmakuRowReadyAtMs[bestRow] = now + spacingMs;
        return bestRow;
    }

    private int currentDanmakuRowCount() {
        int available = Math.max(dp(96), danmakuDisplayBottomPx() - danmakuTopPx());
        return Math.max(1, Math.min(Math.min(danmakuMaxRows, danmakuRowReadyAtMs.length), available / danmakuRowHeightPx()));
    }

    private int currentDanmakuBurstLimit() {
        int rows = currentDanmakuRowCount();
        if (danmakuMaxRows <= 4) return Math.max(2, rows / 2);
        if (danmakuMaxRows >= 10) return Math.max(6, Math.min(10, rows));
        return Math.max(3, Math.min(5, rows));
    }

    private int danmakuTopPx() {
        int height = danmakuViewportHeight();
        return Math.round(height * (landscapeMode ? 0.00f : 0.092f));
    }

    private int danmakuDisplayBottomPx() {
        int top = danmakuTopPx();
        int maxBottom = danmakuMaxBottomPx();
        return Math.min(maxBottom, top + Math.max(1, (maxBottom - top) * danmakuAreaPercent / 100));
    }

    private int danmakuMaxBottomPx() {
        int height = danmakuViewportHeight();
        int bottom = Math.round(height * (landscapeMode ? 0.65f : 0.31f));
        return Math.max(danmakuTopPx() + 1, bottom);
    }

    private int danmakuViewportHeight() {
        if (danmakuLayer != null && danmakuLayer.getHeight() > 0) return danmakuLayer.getHeight();
        return rootHeight();
    }

    private int danmakuRowHeightPx() {
        float textSize = danmakuTextSp * getResources().getDisplayMetrics().scaledDensity;
        return Math.max(dp(28), Math.round(textSize * 1.55f));
    }

    private long currentDanmakuDurationMs(long baseDurationMs) {
        long base = baseDurationMs > 0L ? baseDurationMs : 7200L;
        if (danmakuDelayMs < 680) base = base * 85L / 100L;
        if (danmakuDelayMs > 680) base = base * 115L / 100L;
        return Math.max(3600L, Math.min(11000L, base));
    }

    private void resetDanmakuCursorFor(long positionMs) {
        long targetMs = Math.max(0L, positionMs - 900L);
        int index = 0;
        while (index < activeDanmakuEntries.size() && activeDanmakuEntries.get(index).timeMs < targetMs) {
            index++;
        }
        nextDanmakuEntryIndex = index;
        lastDanmakuPositionMs = positionMs;
        emittedDanmakuTexts.clear();
        java.util.Arrays.fill(danmakuRowReadyAtMs, 0L);
    }

    private void openCurrentInBili() {
        if (currentItem == null || currentItem.webUrl() == null || currentItem.webUrl().trim().isEmpty()) {
            Toast.makeText(this, "暂无可打开的视频链接", Toast.LENGTH_SHORT).show();
            return;
        }
        copyCurrentVideoLink();
        Toast.makeText(this, "播放器内不跳官方页，已复制视频链接", Toast.LENGTH_SHORT).show();
    }

    private void followCurrentOwner() {
        if (currentItem == null || currentFollowed) {
            Toast.makeText(this, "已关注", Toast.LENGTH_SHORT).show();
            return;
        }
        currentFollowed = true;
        if (ownerFollowButton != null) {
            ownerFollowButton.setText("已关注");
            ownerFollowButton.setTextColor(0xFFE8EAF0);
            ownerFollowButton.setBackground(rounded(0x66383B42, dp(20)));
            ownerFollowButton.animate().cancel();
            ownerFollowButton.setScaleX(1f);
            ownerFollowButton.setScaleY(1f);
            ownerFollowButton.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90)
                    .withEndAction(() -> ownerFollowButton.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                    .start();
        }
        Toast.makeText(this, "关注成功", Toast.LENGTH_SHORT).show();
    }

    private void showCoinSheet() {
        if (currentItem == null) return;
        Dialog dialog = new Dialog(this);
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xCC000000);

        TextView close = text("×", 30, Color.WHITE, Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setBackground(rounded(0x55383B42, dp(24)));
        close.setOnClickListener(v -> dialog.dismiss());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        closeParams.bottomMargin = dp(70);
        overlay.addView(close, closeParams);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(22), dp(20), dp(22), dp(18));

        TextView title = text("给 UP 主投币", 20, Color.WHITE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setShadowLayer(6, 0, 2, 0xAA000000);
        panel.addView(title, new LinearLayout.LayoutParams(-1, dp(38)));

        LinearLayout choices = new LinearLayout(this);
        choices.setGravity(Gravity.CENTER);
        TextView one = coinModeCard(1, true);
        TextView two = coinModeCard(2, false);
        choices.addView(one, new LinearLayout.LayoutParams(0, dp(116), 1));
        LinearLayout.LayoutParams twoParams = new LinearLayout.LayoutParams(0, dp(116), 1);
        twoParams.leftMargin = dp(14);
        choices.addView(two, twoParams);
        LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(-1, dp(124));
        choiceParams.topMargin = dp(14);
        panel.addView(choices, choiceParams);

        CoinMascotView mascot = new CoinMascotView(this);
        mascot.setBackground(rounded(0xFFE7F6FF, dp(18)));
        LinearLayout.LayoutParams mascotParams = new LinearLayout.LayoutParams(-1, dp(132));
        mascotParams.topMargin = dp(14);
        panel.addView(mascot, mascotParams);

        final int[] selected = new int[]{1};
        final boolean[] alsoLike = new boolean[]{true};
        TextView alsoLikeView = text("✓ 同时点赞内容", 15, 0xFFE7EAF0, Typeface.BOLD);
        alsoLikeView.setGravity(Gravity.CENTER_VERTICAL);
        alsoLikeView.setPadding(dp(14), 0, dp(14), 0);
        alsoLikeView.setBackground(rounded(0x55383B42, dp(19)));
        alsoLikeView.setOnClickListener(v -> {
            alsoLike[0] = !alsoLike[0];
            alsoLikeView.setText((alsoLike[0] ? "✓ " : "□ ") + "同时点赞内容");
            alsoLikeView.setTextColor(alsoLike[0] ? 0xFFE7EAF0 : 0xFFB9BEC7);
        });

        TextView balance = text("硬币余额：--    如何获取硬币？", 14, 0xFFD5DAE2, Typeface.NORMAL);
        balance.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        balance.setOnClickListener(v -> Toast.makeText(this, "硬币余额以账号接口为准", Toast.LENGTH_SHORT).show());
        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(alsoLikeView, new LinearLayout.LayoutParams(dp(156), dp(38)));
        meta.addView(balance, new LinearLayout.LayoutParams(0, dp(38), 1));
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, dp(42));
        metaParams.topMargin = dp(12);
        panel.addView(meta, metaParams);

        TextView hint = text("选择硬币数量后确认投币，也可以点击小人投出硬币", 13, 0xFFCAD1DA, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint, new LinearLayout.LayoutParams(-1, dp(30)));

        TextView confirmButton = sheetPill("确认投币");
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(-1, dp(44));
        confirmParams.topMargin = dp(8);
        panel.addView(confirmButton, confirmParams);

        final Runnable[] confirm = new Runnable[1];
        confirm[0] = () -> {
            int count = selected[0];
            showCoinFly(count);
            dialog.dismiss();
            Toast.makeText(this, "投币接口未接入，未改变真实投币状态", Toast.LENGTH_SHORT).show();
        };

        one.setOnClickListener(v -> {
            selected[0] = 1;
            updateCoinModeCards(one, two, selected[0]);
        });
        two.setOnClickListener(v -> {
            selected[0] = 2;
            updateCoinModeCards(one, two, selected[0]);
        });
        confirmButton.setOnClickListener(v -> confirm[0].run());
        mascot.setOnClickListener(v -> confirm[0].run());

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        panelParams.leftMargin = dp(18);
        panelParams.rightMargin = dp(18);
        overlay.addView(panel, panelParams);

        dialog.setContentView(overlay);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private TextView coinModeCard(int count, boolean selected) {
        TextView card = text((count == 1 ? "币" : "币币") + "\n" + count + " 硬币", 17, 0xFF5A3700, Typeface.BOLD);
        card.setGravity(Gravity.CENTER);
        card.setLines(2);
        card.setBackground(rounded(selected ? 0xFFFFC46B : 0xFFD99645, dp(16)));
        card.setElevation(selected ? dp(8) : dp(2));
        card.setScaleX(selected ? 1.04f : 0.96f);
        card.setScaleY(selected ? 1.04f : 0.96f);
        return card;
    }

    private void updateCoinModeCards(TextView one, TextView two, int selected) {
        one.setBackground(rounded(selected == 1 ? 0xFFFFC46B : 0xFFD99645, dp(16)));
        two.setBackground(rounded(selected == 2 ? 0xFFFFC46B : 0xFFD99645, dp(16)));
        one.animate().scaleX(selected == 1 ? 1.04f : 0.96f).scaleY(selected == 1 ? 1.04f : 0.96f).setDuration(140).start();
        two.animate().scaleX(selected == 2 ? 1.04f : 0.96f).scaleY(selected == 2 ? 1.04f : 0.96f).setDuration(140).start();
    }

    private void showCoinFly(int count) {
        for (int i = 0; i < count; i++) {
            TextView coin = text("币", 24, 0xFFFFD166, Typeface.BOLD);
            coin.setGravity(Gravity.CENTER);
            coin.setBackground(rounded(0xFFFFC83D, dp(18)));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(36), dp(36));
            params.leftMargin = root.getWidth() / 2 - dp(18);
            params.topMargin = root.getHeight() - dp(330) - i * dp(14);
            root.addView(coin, params);
            coin.setAlpha(0.95f);
            coin.animate()
                    .translationX(dp(360))
                    .translationY(-dp(360) - i * dp(24))
                    .rotation(540f)
                    .scaleX(0.6f)
                    .scaleY(0.6f)
                    .alpha(0f)
                    .setStartDelay(i * 90L)
                    .setDuration(760)
                    .withEndAction(() -> root.removeView(coin))
                    .start();
        }
        if (currentItem != null && !currentCoined) {
            currentCoined = true;
            currentItem.coinCount += Math.max(1, count);
            coinButton.setCount(formatCount(currentItem.coinCount));
            if (landscapeCoinButton != null) landscapeCoinButton.setCount(formatCount(currentItem.coinCount));
        }
        coinButton.setIconColor(BILI_PINK);
        if (landscapeCoinButton != null) landscapeCoinButton.setIconColor(BILI_PINK);
        coinButton.playLottieAnimation();
    }

    private void handleFavoriteClick() {
        if (currentItem == null) return;
        if (currentFavorited) {
            setCurrentFavorite(false, false);
            Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show();
            return;
        }
        showFavoriteSheet();
    }

    private void setCurrentFavorite(boolean favorited, boolean animate) {
        if (currentItem == null || favoriteButton == null || currentFavorited == favorited) return;
        currentFavorited = favorited;
        if (favorited) {
            currentItem.favoriteCount++;
            favoriteButton.setIconColor(BILI_PINK);
            if (landscapeFavoriteButton != null) landscapeFavoriteButton.setIconColor(BILI_PINK);
            if (animate) favoriteButton.playLottieAnimation();
        } else {
            currentItem.favoriteCount = Math.max(0, currentItem.favoriteCount - 1);
            favoriteButton.setIconColor(Color.WHITE);
            if (landscapeFavoriteButton != null) landscapeFavoriteButton.setIconColor(Color.WHITE);
        }
        favoriteButton.setCount(formatCount(currentItem.favoriteCount));
        if (landscapeFavoriteButton != null) landscapeFavoriteButton.setCount(formatCount(currentItem.favoriteCount));
    }

    private void showFavoriteSheet() {
        if (currentItem == null) return;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(sheetRow("✓ 默认收藏夹"));
        TextView watchLater = sheetRow(watchLaterVideoIds.contains(videoIdentity(currentItem)) ? "✓ 稍后再看" : "□ 稍后再看");
        watchLater.setOnClickListener(v -> {
            addCurrentToWatchLater();
            watchLater.setText("✓ 稍后再看");
            watchLater.setTextColor(BILI_PINK);
        });
        content.addView(watchLater);
        TextView done = sheetPill("完成");
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, dp(44));
        doneParams.topMargin = dp(14);
        content.addView(done, doneParams);

        final Dialog[] holder = new Dialog[1];
        done.setOnClickListener(v -> {
            setCurrentFavorite(true, true);
            if (holder[0] != null) holder[0].dismiss();
            Toast.makeText(this, "已收藏", Toast.LENGTH_SHORT).show();
        });
        holder[0] = showBottomSheet("添加到收藏", content, dp(270));
    }

    private void showShareSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        content.addView(sheetSection("分享给"));
        content.addView(sheetIconGrid(
                new String[]{"微信", "朋友圈", "QQ", "QQ空间", "动态"},
                new String[]{"微", "朋", "Q", "空", "动"}
        ), new LinearLayout.LayoutParams(-1, dp(84)));
        content.addView(sheetSection("工具"));
        content.addView(sheetToolGrid(
                new String[]{"复制链接", "生成截图", "保存封面", "转发动态"},
                new String[]{"链", "图", "封", "转"}
        ), new LinearLayout.LayoutParams(-1, dp(84)));

        String[] rows = new String[]{"不感兴趣", "稍后再看", "缓存", "小窗播放", "投屏"};
        for (String row : rows) {
            TextView item = sheetRow(row);
            item.setOnClickListener(v -> handleSheetAction(row, false));
            content.addView(item);
        }
        showBottomSheet("分享/更多", content, dp(430));
    }

    private void showPlaylistSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        int count = 0;
        if (!previousStack.isEmpty()) {
            content.addView(sheetSection("上一条"));
            Iterator<PrefetchedVideo> previous = previousStack.descendingIterator();
            int shown = 0;
            while (previous.hasNext() && shown < 3) {
                PrefetchedVideo video = previous.next();
                content.addView(playlistRow(video, "上滑前看过", false, true));
                shown++;
                count++;
            }
        }

        if (currentVideo != null) {
            content.addView(sheetSection("正在播放"));
            content.addView(playlistRow(currentVideo, "当前", true, false));
            count++;
        }

        List<PrefetchedVideo> nextItems = nextPlaylistItems();
        if (!nextItems.isEmpty()) {
            content.addView(sheetSection("接下来"));
            for (PrefetchedVideo video : nextItems) {
                content.addView(playlistRow(video, "已预加载", false, false));
                count++;
            }
        }

        if (count == 0) {
            content.addView(commentStatus("暂时没有可展示的视频队列"));
        } else {
            TextView hint = text("只在播放器内切换，不进入完整合集页。", 13, 0xFF8A9099, Typeface.NORMAL);
            hint.setPadding(dp(6), dp(8), dp(6), dp(4));
            content.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        }

        showBottomSheet("视频列表", content, dp(520));
    }

    private void showSearchSuggestSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        String entranceTitle = currentItem == null ? "" : currentItem.searchEntranceTitle;
        String jumpUri = currentItem == null ? "" : currentItem.searchEntranceJumpUri;
        String keyword = searchKeywordFromJumpUri(jumpUri);

        TextView hint = text("来自 B 站竖屏 story 流 creative_entrance。", 13, 0xFF8A9099, Typeface.NORMAL);
        hint.setPadding(dp(6), 0, dp(6), dp(10));
        content.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        EditText input = new EditText(this);
        String initial = firstNonEmpty(keyword, entranceTitle.replace("搜索·", "").replace("搜索 · ", ""), currentItem == null ? "" : searchSuggestText(currentItem));
        input.setSingleLine(true);
        input.setText(initial);
        input.setSelectAllOnFocus(true);
        input.setHint("搜索相关视频");
        input.setHintTextColor(0xFF9CA1A8);
        input.setTextColor(0xFF1D1F23);
        input.setTextSize(15);
        input.setPadding(dp(16), 0, dp(16), 0);
        input.setBackground(rounded(0xFFF0F1F4, dp(22)));
        content.addView(input, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView apply = pillText("更新底部推荐词", 15, Color.WHITE, BILI_PINK);
        apply.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(-1, dp(42));
        applyParams.topMargin = dp(10);
        content.addView(apply, applyParams);

        content.addView(sheetSection("相关提示"));
        List<String> seeds = searchSeeds(currentItem);
        for (String seed : seeds) {
            TextView row = sheetRow("搜索 · " + seed);
            row.setOnClickListener(v -> {
                input.setText(seed);
                input.setSelection(input.getText().length());
            });
            content.addView(row);
        }

        final Dialog[] holder = new Dialog[1];
        apply.setOnClickListener(v -> {
            String value = input.getText() == null ? "" : input.getText().toString().trim();
            if (value.isEmpty()) value = "相关视频";
            if (searchSuggestView != null) {
                searchSuggestView.setText("搜索 · " + compactSearchText(value));
                applySearchEntranceIcon(searchSuggestView);
            }
            if (holder[0] != null) holder[0].dismiss();
            Toast.makeText(this, "已更新播放器内搜索提示", Toast.LENGTH_SHORT).show();
        });
        holder[0] = showBottomSheet("搜索", content, dp(430));
    }

    private void updateSearchEntrance(TextView target, FeedItem item, boolean clickable) {
        if (target == null) return;
        String title = item == null ? "" : item.searchEntranceTitle;
        if (title == null || title.trim().isEmpty()) {
            title = "搜索·" + compactSearchText(searchSuggestText(item));
        }
        target.setVisibility(View.VISIBLE);
        target.setText(title + "    ›");
        applySearchEntranceIcon(target);
        target.setClickable(clickable);
        if (clickable) target.setOnClickListener(v -> showSearchSuggestSheet());
    }

    private String searchKeywordFromJumpUri(String jumpUri) {
        if (jumpUri == null || jumpUri.isEmpty()) return "";
        try {
            String keyword = Uri.parse(jumpUri).getQueryParameter("keyword");
            return keyword == null ? "" : keyword.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<String> searchSeeds(FeedItem item) {
        List<String> seeds = new ArrayList<>();
        if (item == null) {
            seeds.add("相关视频");
            return seeds;
        }
        addSearchSeed(seeds, searchSuggestText(item));
        addSearchSeed(seeds, item.ownerName);
        addSearchSeed(seeds, firstTitleSegment(item.title));
        if (item.desc != null && !item.desc.isEmpty()) {
            addSearchSeed(seeds, firstTitleSegment(item.desc));
        }
        String[] fallback = new String[]{"相关视频", "同类视频", "热门评论提到的内容", "UP主其他作品"};
        for (String value : fallback) {
            if (seeds.size() >= 4) break;
            addSearchSeed(seeds, value);
        }
        return seeds.subList(0, Math.min(4, seeds.size()));
    }

    private void addSearchSeed(List<String> seeds, String value) {
        String text = compactSearchText(value);
        if (text.isEmpty() || seeds.contains(text)) return;
        seeds.add(text);
    }

    private String compactSearchText(String value) {
        if (value == null) return "";
        String text = value
                .replace("《", "")
                .replace("》", "")
                .replace("#", " ")
                .replace("|", " ")
                .replace("，", " ")
                .replace(",", " ")
                .replace("：", " ")
                .replace(":", " ")
                .trim();
        text = text.replaceAll("\\s+", " ");
        return text.length() > 18 ? text.substring(0, 18) : text;
    }

    private String firstTitleSegment(String value) {
        String text = compactSearchText(value);
        int space = text.indexOf(' ');
        if (space > 4) text = text.substring(0, space);
        return text;
    }

    private List<PrefetchedVideo> nextPlaylistItems() {
        List<PrefetchedVideo> items = new ArrayList<>();
        PrefetchedVideo prefetched;
        synchronized (prefetchLock) {
            prefetched = prefetchedVideo;
        }
        if (prefetched != null) items.add(prefetched);

        Iterator<PrefetchedVideo> iterator = nextStack.descendingIterator();
        while (iterator.hasNext() && items.size() < 3) {
            PrefetchedVideo video = iterator.next();
            if (video != null && video != prefetched) items.add(video);
        }
        return items;
    }

    private View playlistRow(PrefetchedVideo video, String badge, boolean current, boolean previous) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setBackground(rounded(current ? 0xFFFFEEF4 : 0xFFF5F6F8, dp(12)));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (current) {
                dismissActiveBottomSheet();
                showCenter("正在播放");
            } else {
                playFromPlaylist(video, previous);
            }
        });

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(rounded(0xFFE1E4EA, dp(8)));
        loadImageInto(video.item.cover, cover);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(dp(74), dp(46));
        coverParams.rightMargin = dp(10);
        row.addView(cover, coverParams);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(fallback(video.item.title, "未命名视频"), 14, 0xFF252A31, Typeface.BOLD);
        title.setMaxLines(1);
        body.addView(title, new LinearLayout.LayoutParams(-1, dp(22)));
        String meta = badge + " · " + fallback(video.item.ownerName, "未知UP") + " · " + formatDuration(video.item.durationSeconds);
        TextView subtitle = text(meta, 12, current ? BILI_PINK : 0xFF8A9099, Typeface.NORMAL);
        subtitle.setMaxLines(1);
        body.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(20)));
        row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));

        TextView state = text(current ? "播放中" : (previous ? "回看" : "播放"), 12, current ? Color.WHITE : BILI_PINK, Typeface.BOLD);
        state.setGravity(Gravity.CENTER);
        state.setBackground(rounded(current ? BILI_PINK : 0xFFFFFFFF, dp(14)));
        row.addView(state, new LinearLayout.LayoutParams(dp(58), dp(30)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(66));
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);
        return row;
    }

    private void playFromPlaylist(PrefetchedVideo target, boolean previous) {
        if (target == null) return;
        dismissActiveBottomSheet();
        hideComments(false);
        if (previous) {
            if (previousStack.remove(target)) {
                if (currentVideo != null) nextStack.addLast(currentVideo);
                play(target, false);
                showCenter("已切到上一条");
            } else {
                showCenter("这条已经不在队列里");
            }
            return;
        }

        boolean consumed = nextStack.remove(target);
        synchronized (prefetchLock) {
            if (!consumed && prefetchedVideo == target) {
                prefetchedVideo = null;
                consumed = true;
            }
        }
        if (!consumed) {
            showCenter("这条已经不在队列里");
            return;
        }
        if (currentVideo != null) previousStack.addLast(currentVideo);
        play(target, false);
        showCenter("已切到下一条");
    }

    private void showMoreSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] holder = new Dialog[1];

        content.addView(sheetSection("分享给"));
        content.addView(sheetIconGrid(
                new String[]{"微信", "朋友圈", "QQ", "QQ空间", "动态"},
                new String[]{"微", "朋", "Q", "空", "动"}
        ), new LinearLayout.LayoutParams(-1, dp(84)));

        content.addView(sheetSection("常用工具"));
        content.addView(sheetToolGrid(
                new String[]{"不感兴趣", "稍后再看", "缓存", "小窗", "投屏"},
                new String[]{"×", "稍", "缓", "窗", "投"}
        ), new LinearLayout.LayoutParams(-1, dp(84)));

        content.addView(sheetSection("账号"));
        TextView account = sheetRow(repository.apiClient().hasAuthCookie() ? "账号管理" : "登录账号");
        account.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            openAccount();
        });
        content.addView(account);

        content.addView(sheetSection("播放设置"));
        TextView speed = sheetRow("倍速    " + speedLabel(playbackSpeed));
        speed.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            showSpeedSheet();
        });
        content.addView(speed);
        content.addView(speedOptionsRow(), new LinearLayout.LayoutParams(-1, dp(44)));

        TextView autoSlide = sheetRow("自动上滑    " + (autoSlideEnabled ? "开启" : "关闭"));
        autoSlide.setOnClickListener(v -> {
            autoSlideEnabled = !autoSlideEnabled;
            if (player != null) {
                player.setRepeatMode(autoSlideEnabled ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ONE);
            }
            autoSlide.setText("自动上滑    " + (autoSlideEnabled ? "开启" : "关闭"));
            autoSlide.setTextColor(autoSlideEnabled ? BILI_PINK : 0xFF252A31);
            Toast.makeText(this, autoSlideEnabled ? "已开启自动上滑" : "已关闭自动上滑", Toast.LENGTH_SHORT).show();
        });
        content.addView(autoSlide);

        TextView timer = sheetRow("定时关闭    " + timerCloseOption + "  ›");
        timer.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            showTimerSheet();
        });
        content.addView(timer);

        TextView gesture = sheetRow("单双击手势    单击暂停 / 双击点赞");
        gesture.setOnClickListener(v -> Toast.makeText(this, "手势策略已按播放器内固定", Toast.LENGTH_SHORT).show());
        content.addView(gesture);

        TextView backgroundAudio = sheetRow("后台听视频    " + (backgroundAudioEnabled ? "开启" : "关闭"));
        backgroundAudio.setOnClickListener(v -> {
            backgroundAudioEnabled = !backgroundAudioEnabled;
            if (backgroundAudioEnabled) pausedByBackgroundRule = false;
            backgroundAudio.setText("后台听视频    " + (backgroundAudioEnabled ? "开启" : "关闭"));
            backgroundAudio.setTextColor(backgroundAudioEnabled ? BILI_PINK : 0xFF252A31);
            Toast.makeText(this, backgroundAudioEnabled ? "已开启后台听视频" : "已关闭后台听视频", Toast.LENGTH_SHORT).show();
        });
        content.addView(backgroundAudio);

        TextView clarity = sheetRow("清晰度    " + currentClarity + "  ›");
        clarity.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            showClaritySheet();
        });
        content.addView(clarity);

        TextView danmakuSettings = sheetRow("弹幕设置");
        danmakuSettings.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            showDanmakuSettings();
        });
        content.addView(danmakuSettings);

        TextView subtitle = sheetRow("字幕    无字幕资源");
        subtitle.setOnClickListener(v -> Toast.makeText(this, "暂无字幕资源", Toast.LENGTH_SHORT).show());
        content.addView(subtitle);

        TextView mirror = sheetRow("镜像翻转    " + (mirrorEnabled ? "开启" : "关闭"));
        mirror.setOnClickListener(v -> {
            mirrorEnabled = !mirrorEnabled;
            applyMirrorMode();
            mirror.setText("镜像翻转    " + (mirrorEnabled ? "开启" : "关闭"));
            mirror.setTextColor(mirrorEnabled ? BILI_PINK : 0xFF252A31);
            Toast.makeText(this, mirrorEnabled ? "已开启镜像翻转" : "已关闭镜像翻转", Toast.LENGTH_SHORT).show();
        });
        content.addView(mirror);

        TextView cleanMode = sheetRow("轻量控制态");
        cleanMode.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            enterCleanScreenMode();
            Toast.makeText(this, "已进入轻量控制态，点右下角恢复完整 UI", Toast.LENGTH_SHORT).show();
        });
        content.addView(cleanMode);

        TextView dislike = sheetRow("不感兴趣");
        dislike.setOnClickListener(v -> markCurrentNotInterested());
        content.addView(dislike);

        TextView boost = sheetRow("助TA必火");
        boost.setOnClickListener(v -> showBoostSheet());
        content.addView(boost);

        TextView report = sheetRow("举报");
        report.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            showReportSheet();
        });
        content.addView(report);

        TextView feedback = sheetRow("播放反馈 / 反馈建议");
        feedback.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            showPlaybackFeedbackSheet();
        });
        content.addView(feedback);

        holder[0] = showBottomSheet("更多", content, dp(620));
    }

    private void showSpeedSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        float[] speeds = new float[]{0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        final Dialog[] holder = new Dialog[1];
        for (float speed : speeds) {
            TextView row = sheetRow(speedLabel(speed));
            if (Math.abs(playbackSpeed - speed) < 0.01f) row.setTextColor(BILI_PINK);
            row.setOnClickListener(v -> {
                playbackSpeed = speed;
                if (player != null && !fastForwarding) {
                    player.setPlaybackSpeed(playbackSpeed);
                }
                if (holder[0] != null) holder[0].dismiss();
                Toast.makeText(this, "已切换倍速：" + speedLabel(speed), Toast.LENGTH_SHORT).show();
            });
            content.addView(row);
        }
        holder[0] = showBottomSheet("倍速", content, dp(370));
    }

    private void showReportSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView hint = text("仅在播放器内记录，不跳转举报详情页。", 13, 0xFF8A9099, Typeface.NORMAL);
        hint.setPadding(dp(6), 0, dp(6), dp(10));
        content.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        String[] reasons = new String[]{"内容不适", "标题封面误导", "低质重复", "广告营销", "其他问题"};
        final Dialog[] holder = new Dialog[1];
        for (String reason : reasons) {
            TextView row = sheetRow(reason);
            row.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                Toast.makeText(this, "已记录举报原因：" + reason, Toast.LENGTH_SHORT).show();
            });
            content.addView(row);
        }
        holder[0] = showBottomSheet("举报", content, dp(420));
    }

    private void showPlaybackFeedbackSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView hint = text("用于本地记录播放体验问题，不离开播放器。", 13, 0xFF8A9099, Typeface.NORMAL);
        hint.setPadding(dp(6), 0, dp(6), dp(10));
        content.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        String[] reasons = new String[]{"播放卡顿", "音画不同步", "清晰度异常", "弹幕遮挡", "评论加载异常", "界面比例异常"};
        final Dialog[] holder = new Dialog[1];
        for (String reason : reasons) {
            TextView row = sheetRow(reason);
            row.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                Toast.makeText(this, "已记录反馈：" + reason, Toast.LENGTH_SHORT).show();
            });
            content.addView(row);
        }
        holder[0] = showBottomSheet("播放反馈", content, dp(500));
    }

    private void showTimerSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        String[] options = new String[]{"关闭", "15分钟", "30分钟", "60分钟", "播完当前视频"};
        final Dialog[] holder = new Dialog[1];
        for (String option : options) {
            TextView row = sheetRow(option);
            if (timerCloseOption.equals(option)) row.setTextColor(BILI_PINK);
            row.setOnClickListener(v -> {
                configureTimerClose(option);
                if (holder[0] != null) holder[0].dismiss();
            });
            content.addView(row);
        }
        holder[0] = showBottomSheet("定时关闭", content, dp(350));
    }

    private void configureTimerClose(String option) {
        uiHandler.removeCallbacks(timerCloseRunnable);
        closeAfterCurrentVideo = false;
        timerCloseOption = option;
        long delayMs = 0L;
        if ("15分钟".equals(option)) {
            delayMs = 15L * 60L * 1000L;
        } else if ("30分钟".equals(option)) {
            delayMs = 30L * 60L * 1000L;
        } else if ("60分钟".equals(option)) {
            delayMs = 60L * 60L * 1000L;
        } else if ("播完当前视频".equals(option)) {
            closeAfterCurrentVideo = true;
        }
        if (delayMs > 0L) {
            uiHandler.postDelayed(timerCloseRunnable, delayMs);
        }
        Toast.makeText(this, "定时关闭：" + option, Toast.LENGTH_SHORT).show();
    }

    private void applyMirrorMode() {
        if (playerView == null) return;
        playerView.setScaleX(mirrorEnabled ? -1f : 1f);
    }

    private void showClaritySheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView state = text("当前实际播放：" + currentClarity + "    全局偏好：" + clarityLabelForQuality(globalPreferredQn, "自动"), 13, 0xFF8A9099, Typeface.NORMAL);
        state.setPadding(dp(6), 0, dp(6), dp(8));
        content.addView(state, new LinearLayout.LayoutParams(-1, -2));

        final Dialog[] holder = new Dialog[1];
        List<QualityOption> options = currentVideo == null || currentVideo.playInfo == null
                ? java.util.Collections.emptyList()
                : currentVideo.playInfo.qualityOptions;
        if (options.isEmpty()) {
            int[] fallbackQn = new int[]{120, 116, 112, 80, 64, 32, 16};
            for (int qn : fallbackQn) {
                QualityOption option = new QualityOption();
                option.qn = qn;
                option.title = clarityLabelForQuality(qn, qn + "P");
                option.playable = true;
                option.selected = qn == (currentVideo == null || currentVideo.playInfo == null ? globalPreferredQn : currentVideo.playInfo.quality);
                options = new ArrayList<>(options);
                options.add(option);
            }
        }
        for (QualityOption option : options) {
            String label = qualityOptionLabel(option);
            TextView row = sheetRow(label);
            if (option.selected) row.setTextColor(BILI_PINK);
            if (!option.playable) row.setTextColor(option.selected ? BILI_PINK : 0xFF9CA1A8);
            row.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                switchClarity(label, option.qn);
            });
            content.addView(row);
        }
        holder[0] = showBottomSheet("清晰度", content, dp(470));
    }

    private String qualityOptionLabel(QualityOption option) {
        String title = firstNonEmpty(option.title, option.displayDesc, clarityLabelForQuality(option.qn, option.qn + "P"));
        StringBuilder builder = new StringBuilder(title);
        if (option.requiresVip) builder.append("    大会员");
        if (!option.playable) builder.append("    可能降级");
        if (option.selected) builder.append("    ✓");
        return builder.toString();
    }

    private void switchClarity(String label, int qualityNumber) {
        if (currentItem == null) return;
        globalPreferredQn = qualityNumber;
        prefs.edit().putInt(PREF_QUALITY_QN, globalPreferredQn).apply();
        if (player == null) {
            currentClarity = label;
            Toast.makeText(this, "已切换清晰度：" + label, Toast.LENGTH_SHORT).show();
            return;
        }
        long position = Math.max(0L, player.getCurrentPosition());
        boolean wasPlaying = player.isPlaying();
        float speed = playbackSpeed;
        Toast.makeText(this, "正在切换清晰度：" + label, Toast.LENGTH_SHORT).show();
        FeedItem item = currentItem;
        new Thread(() -> {
            try {
                PlayInfo playInfo = repository.apiClient().fetchPlayInfo(item, qualityNumber);
                if (!playInfo.playable) {
                    String error = playInfo.error == null || playInfo.error.isEmpty() ? "接口未返回可播放地址" : playInfo.error;
                    throw new IllegalStateException(error);
                }
                runOnUiThread(() -> applyClaritySource(item, playInfo, label, qualityNumber, position, wasPlaying, speed));
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, "清晰度切换失败：" + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void applyClaritySource(FeedItem item, PlayInfo playInfo, String requestedLabel, int requestedQuality, long position, boolean wasPlaying, float speed) {
        if (item != currentItem || player == null) return;
        currentVideo = new PrefetchedVideo(item, playInfo);
        currentClarity = clarityLabelForQuality(playInfo.quality, requestedLabel);
        player.setRepeatMode(autoSlideEnabled ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ONE);
        player.setPlaybackSpeed(speed);
        player.setMediaSource(buildMediaSource(currentVideo));
        player.prepare();
        player.seekTo(position);
        if (wasPlaying) {
            player.play();
        } else {
            player.pause();
        }
        String message = playInfo.quality > 0 && playInfo.quality != requestedQuality
                ? "已自动降级到：" + currentClarity
                : "已切换清晰度：" + currentClarity;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String clarityLabelForQuality(int quality, String fallback) {
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
                return fallback;
        }
    }

    private Dialog showBottomSheet(String title, View content, int height) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(10), dp(18), dp(20));
        panel.setBackground(topRounded(0xFFFFFFFF, dp(20)));

        View handle = new View(this);
        handle.setBackground(rounded(0xFFD6D8DD, dp(2)));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(44), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(handle, handleParams);
        View.OnTouchListener sheetDragListener = bottomSheetDragListener(dialog, panel, height);
        handle.setOnTouchListener(sheetDragListener);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setOnTouchListener(sheetDragListener);
        TextView titleView = text(title, 18, 0xFF171A1F, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(titleView, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView close = pillText("\u00d7", 20, 0xFF59606A, 0xFFF0F1F3);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        panel.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(52)));
        ScrollView contentScroll = new ScrollView(this);
        contentScroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        panel.addView(contentScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        dialog.setContentView(panel);
        dialog.setOnDismissListener(d -> {
            if (activeBottomSheet == dialog) activeBottomSheet = null;
        });
        dialog.show();
        activeBottomSheet = dialog;
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height);
        }
        return dialog;
    }

    private void dismissActiveBottomSheet() {
        Dialog dialog = activeBottomSheet;
        activeBottomSheet = null;
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    private View.OnTouchListener bottomSheetDragListener(Dialog dialog, View panel, int height) {
        final float[] dragStartY = new float[1];
        return (view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartY[0] = event.getRawY();
                    panel.animate().cancel();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    panel.setTranslationY(Math.max(0f, event.getRawY() - dragStartY[0]));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (panel.getTranslationY() > Math.max(dp(72), height * 0.18f)) {
                        dialog.dismiss();
                    } else {
                        panel.animate()
                                .translationY(0)
                                .setInterpolator(swipeInterpolator)
                                .setDuration(180)
                                .start();
                    }
                    return true;
                default:
                    return true;
            }
        };
    }

    private TextView sheetPill(String value) {
        TextView view = pillText(value, 15, Color.WHITE, BILI_PINK);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private TextView coinCard(String value) {
        TextView view = text(value, 17, 0xFF4B2C00, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(0xFFFFC46B, dp(14)));
        view.setPadding(dp(6), dp(6), dp(6), dp(6));
        return view;
    }

    private TextView sheetSection(String value) {
        TextView view = text(value, 13, 0xFF8A9099, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(2), dp(8), 0, dp(4));
        return view;
    }

    private LinearLayout sheetChipRow(String[] values) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (String value : values) {
            TextView chip = text(value, 13, 0xFF30343A, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(rounded(0xFFF2F3F6, dp(16)));
            chip.setOnClickListener(v -> Toast.makeText(this, "已选择：" + value, Toast.LENGTH_SHORT).show());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1);
            params.rightMargin = dp(6);
            row.addView(chip, params);
        }
        return row;
    }

    private LinearLayout speedOptionsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(8));
        float[] speeds = new float[]{0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        for (int i = 0; i < speeds.length; i++) {
            float speed = speeds[i];
            TextView chip = text(speedLabel(speed), 13, 0xFF30343A, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            styleSpeedChip(chip, speed);
            chip.setOnClickListener(v -> {
                playbackSpeed = speed;
                if (player != null && !fastForwarding) {
                    player.setPlaybackSpeed(playbackSpeed);
                }
                for (int child = 0; child < row.getChildCount(); child++) {
                    Object tag = row.getChildAt(child).getTag();
                    if (row.getChildAt(child) instanceof TextView && tag instanceof Float) {
                        styleSpeedChip((TextView) row.getChildAt(child), (Float) tag);
                    }
                }
                Toast.makeText(this, "已切换倍速：" + speedLabel(speed), Toast.LENGTH_SHORT).show();
            });
            chip.setTag(speed);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i < speeds.length - 1) params.rightMargin = dp(6);
            row.addView(chip, params);
        }
        return row;
    }

    private void styleSpeedChip(TextView chip, float speed) {
        boolean selected = Math.abs(playbackSpeed - speed) < 0.01f;
        chip.setTextColor(selected ? Color.WHITE : 0xFF30343A);
        chip.setBackground(rounded(selected ? BILI_PINK : 0xFFF2F3F6, dp(18)));
    }

    private LinearLayout sheetIconGrid(String[] labels, String[] icons) {
        return sheetActionGrid(labels, icons, true);
    }

    private LinearLayout sheetToolGrid(String[] labels, String[] icons) {
        return sheetActionGrid(labels, icons, false);
    }

    private LinearLayout sheetActionGrid(String[] labels, String[] icons, boolean shareStyle) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int count = Math.max(1, labels.length);
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setContentDescription(label);
            item.setOnClickListener(v -> handleSheetAction(label, shareStyle));

            View circle = new SheetActionIconView(this, label, shareStyle);
            item.addView(circle, new LinearLayout.LayoutParams(dp(46), dp(46)));

            TextView caption = text(label, 12, 0xFF4D535C, Typeface.NORMAL);
            caption.setGravity(Gravity.CENTER);
            caption.setSingleLine(true);
            LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-1, dp(24));
            captionParams.topMargin = dp(5);
            item.addView(caption, captionParams);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f / count);
            if (i < labels.length - 1) params.rightMargin = dp(4);
            row.addView(item, params);
        }
        return row;
    }

    private void handleSheetAction(String label, boolean shareStyle) {
        if (shareStyle) {
            shareCurrentViaSystem(label);
            return;
        }
        if ("复制链接".equals(label)) {
            copyCurrentVideoLink();
        } else if ("生成截图".equals(label)) {
            saveCurrentScreenshot();
        } else if ("保存封面".equals(label)) {
            saveCurrentCover();
        } else if ("转发动态".equals(label)) {
            showDynamicForwardSheet();
        } else if ("不感兴趣".equals(label)) {
            markCurrentNotInterested();
        } else if ("稍后再看".equals(label)) {
            addCurrentToWatchLater();
        } else if ("缓存".equals(label) || "缓".equals(label)) {
            showCacheInfoSheet();
        } else if ("小窗播放".equals(label) || "小窗".equals(label)) {
            showPictureInPictureSheet();
        } else if ("投屏".equals(label)) {
            showCastSheet();
        } else {
            showGenericActionSheet(label);
        }
    }

    private void shareCurrentViaSystem(String channel) {
        if (currentItem == null || currentItem.webUrl() == null || currentItem.webUrl().trim().isEmpty()) {
            Toast.makeText(this, "暂无可分享的视频链接", Toast.LENGTH_SHORT).show();
            return;
        }
        recordShareAction();
        copyToClipboard("BiliClean 分享文案", fallback(currentItem.title, "B站视频") + "\n" + currentItem.webUrl());
        Toast.makeText(this, "播放器内不拉起" + channel + "，已复制分享文案", Toast.LENGTH_SHORT).show();
    }

    private void showDynamicForwardSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(fallback(currentItem == null ? "" : currentItem.title, "当前视频"), 15, 0xFF252A31, Typeface.BOLD);
        title.setMaxLines(2);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        EditText input = new EditText(this);
        input.setHint("说点什么再转发");
        input.setHintTextColor(0xFF9CA1A8);
        input.setTextColor(0xFF1D1F23);
        input.setTextSize(15);
        input.setPadding(dp(14), dp(8), dp(14), dp(8));
        input.setBackground(rounded(0xFFF0F1F4, dp(16)));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(82));
        inputParams.topMargin = dp(12);
        content.addView(input, inputParams);
        TextView send = sheetPill("复制为动态文案");
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(-1, dp(44));
        sendParams.topMargin = dp(12);
        send.setOnClickListener(v -> {
            String body = input.getText().toString().trim();
            String text = (body.isEmpty() ? "转发视频" : body) + "\n" + (currentItem == null ? "" : currentItem.webUrl());
            copyToClipboard("BiliClean 动态文案", text);
            recordShareAction();
            Toast.makeText(this, "已复制动态文案", Toast.LENGTH_SHORT).show();
        });
        content.addView(send, sendParams);
        showBottomSheet("转发动态", content, dp(300));
    }

    private void showCacheInfoSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(text("播放器已启用 300MB 边播边缓存；当前视频会随播放写入缓存，下一个视频会提前预取播放地址。", 14, 0xFF4D535C, Typeface.NORMAL));
        TextView copy = sheetRow("复制当前视频链接");
        copy.setOnClickListener(v -> copyCurrentVideoLink());
        content.addView(copy);
        TextView cover = sheetRow("保存封面到相册");
        cover.setOnClickListener(v -> saveCurrentCover());
        content.addView(cover);
        showBottomSheet("缓存", content, dp(270));
    }

    private void showPictureInPictureSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(text("Android 小窗需要系统画中画权限。当前版本先保持播放器内播放，不跳出页面。", 14, 0xFF4D535C, Typeface.NORMAL));
        TextView clean = sheetRow("进入轻量控制播放");
        clean.setOnClickListener(v -> enterCleanScreenMode());
        content.addView(clean);
        TextView background = sheetRow("后台听视频    " + (backgroundAudioEnabled ? "开启" : "关闭"));
        background.setOnClickListener(v -> {
            backgroundAudioEnabled = !backgroundAudioEnabled;
            background.setText("后台听视频    " + (backgroundAudioEnabled ? "开启" : "关闭"));
        });
        content.addView(background);
        showBottomSheet("小窗播放", content, dp(270));
    }

    private void showCastSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(text("未发现可用投屏设备；可以复制视频链接到电视端或其它播放器尝试。", 14, 0xFF4D535C, Typeface.NORMAL));
        TextView copy = sheetRow("复制视频链接");
        copy.setOnClickListener(v -> copyCurrentVideoLink());
        content.addView(copy);
        showBottomSheet("投屏", content, dp(230));
    }

    private void showBoostSheet() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(text("助TA必火属于 B站站内互动能力，当前独立客户端只在播放器内提供复制链接，不跳到官方页。", 14, 0xFF4D535C, Typeface.NORMAL));
        TextView copy = sheetRow("复制视频链接");
        copy.setOnClickListener(v -> copyCurrentVideoLink());
        content.addView(copy);
        TextView open = sheetRow("复制官方视频链接");
        open.setOnClickListener(v -> openCurrentInBili());
        content.addView(open);
        showBottomSheet("助TA必火", content, dp(270));
    }

    private void showGenericActionSheet(String label) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(text(label + "暂未接入独立接口；当前仅在播放器内展示说明，不会跳出当前视频。", 14, 0xFF4D535C, Typeface.NORMAL));
        showBottomSheet(label, content, dp(210));
    }

    private void saveCurrentScreenshot() {
        dismissActiveBottomSheet();
        uiHandler.postDelayed(() -> {
            if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) {
                Toast.makeText(this, "当前画面还没准备好", Toast.LENGTH_SHORT).show();
                return;
            }
            Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    PixelCopy.request(getWindow(), bitmap, result -> {
                        if (result != PixelCopy.SUCCESS) {
                            drawRootIntoBitmap(bitmap);
                        }
                        finishSavingScreenshot(bitmap);
                    }, uiHandler);
                    return;
                } catch (Exception ignored) {
                    drawRootIntoBitmap(bitmap);
                }
            } else {
                drawRootIntoBitmap(bitmap);
            }
            finishSavingScreenshot(bitmap);
        }, 220);
    }

    private void drawRootIntoBitmap(Bitmap bitmap) {
        Canvas canvas = new Canvas(bitmap);
        root.draw(canvas);
    }

    private void finishSavingScreenshot(Bitmap bitmap) {
        new Thread(() -> {
            try {
                String name = "BiliClean_" + System.currentTimeMillis() + ".png";
                Uri uri = saveBitmapToGallery(bitmap, name);
                runOnUiThread(() -> {
                    recordShareAction();
                    Toast.makeText(this, uri == null ? "截图保存失败" : "已保存截图到相册/BiliClean", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, "截图保存失败：" + error.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                bitmap.recycle();
            }
        }).start();
    }

    private Uri saveBitmapToGallery(Bitmap bitmap, String fileName) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BiliClean");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("无法创建图片文件");
        try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
            if (stream == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw new IllegalStateException("写入图片失败");
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
        }
        return uri;
    }

    private void recordShareAction() {
        if (currentItem == null) return;
        currentItem.shareCount++;
        if (shareButton != null) shareButton.setCount(formatCount(currentItem.shareCount));
    }

    private void copyCurrentVideoLink() {
        if (currentItem == null) {
            Toast.makeText(this, "暂无可复制的视频链接", Toast.LENGTH_SHORT).show();
            return;
        }
        String link = currentItem.webUrl();
        if (link == null || link.trim().isEmpty()) {
            Toast.makeText(this, "暂无可复制的视频链接", Toast.LENGTH_SHORT).show();
            return;
        }
        copyToClipboard("BiliClean 视频链接", link);
        recordShareAction();
        Toast.makeText(this, "已复制视频链接", Toast.LENGTH_SHORT).show();
    }

    private void copyCurrentCoverLink() {
        if (currentItem == null || currentItem.cover == null || currentItem.cover.trim().isEmpty()) {
            Toast.makeText(this, "当前视频暂无封面地址", Toast.LENGTH_SHORT).show();
            return;
        }
        copyToClipboard("BiliClean 封面链接", currentItem.cover);
        Toast.makeText(this, "已复制封面地址", Toast.LENGTH_SHORT).show();
    }

    private void saveCurrentCover() {
        if (currentItem == null || currentItem.cover == null || currentItem.cover.trim().isEmpty()) {
            Toast.makeText(this, "当前视频暂无封面", Toast.LENGTH_SHORT).show();
            return;
        }
        String coverUrl = currentItem.cover;
        dismissActiveBottomSheet();
        Toast.makeText(this, "正在保存封面", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = downloadBitmap(coverUrl);
                if (bitmap == null) throw new IllegalStateException("封面下载失败");
                Uri uri = saveBitmapToGallery(bitmap, "BiliClean_cover_" + System.currentTimeMillis() + ".png");
                runOnUiThread(() -> Toast.makeText(this, uri == null ? "封面保存失败" : "已保存封面到相册/BiliClean", Toast.LENGTH_SHORT).show());
            } catch (Exception error) {
                runOnUiThread(() -> {
                    copyCurrentCoverLink();
                    Toast.makeText(this, "封面保存失败，已复制封面地址", Toast.LENGTH_LONG).show();
                });
            } finally {
                if (bitmap != null) bitmap.recycle();
            }
        }).start();
    }

    private void copyToClipboard(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        }
    }

    private TextView sheetRow(String value) {
        TextView view = text(value, 15, 0xFF252A31, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(6), 0, dp(6), 0);
        view.setBackground(rounded(0xFFF4F5F7, dp(10)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(44));
        params.bottomMargin = dp(8);
        view.setLayoutParams(params);
        return view;
    }

    private TextView settingRow(String name, String value, View.OnClickListener listener) {
        TextView row = sheetRow(name + "    " + value + "  ›");
        row.setOnClickListener(listener);
        return row;
    }

    private void openAccount() {
        if (repository.apiClient().hasAuthCookie()) {
            new AlertDialog.Builder(this)
                    .setTitle("账号")
                    .setMessage("当前已保存登录态。")
                    .setPositiveButton("重新登录", (dialog, which) -> showLoginChoice())
                    .setNegativeButton("退出登录", (dialog, which) -> logout())
                    .setNeutralButton("取消", null)
                    .show();
        } else {
            showLoginChoice();
        }
    }

    private void showLoginChoice() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView hint = text("登录后推荐流会更贴近你的账号。播放器不会跳出到站外页面。", 14, 0xFF6F7680, Typeface.NORMAL);
        hint.setPadding(dp(4), 0, dp(4), dp(12));
        content.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        final Dialog[] holder = new Dialog[1];
        TextView qr = sheetRow("扫码登录（使用官方 B 站 App）");
        qr.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            showQrLoginDialog();
        });
        content.addView(qr);

        TextView cookie = sheetRow("手动粘贴 Cookie");
        cookie.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            showCookieLoginDialog();
        });
        content.addView(cookie);

        holder[0] = showBottomSheet("登录 B 站", content, dp(260));
    }

    private void showCookieLoginDialog() {
        EditText input = new EditText(this);
        input.setHint("粘贴包含 SESSDATA 的 Cookie");
        input.setSingleLine(false);
        input.setMinLines(4);
        input.setTextColor(0xFF1D1F23);
        input.setHintTextColor(0xFF9CA1A8);
        new AlertDialog.Builder(this)
                .setTitle("手动登录")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String cookie = input.getText().toString().trim();
                    if (!cookie.contains("SESSDATA")) {
                        Toast.makeText(this, "没有检测到 SESSDATA", Toast.LENGTH_LONG).show();
                        return;
                    }
                    saveAuthCookieAndReload(cookie);
                    Toast.makeText(this, "已保存登录态", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showQrLoginDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(14), dp(22), dp(22));
        content.setBackground(topRounded(0xFFFFFFFF, dp(22)));

        View handle = new View(this);
        handle.setBackground(rounded(0xFFD6D8DD, dp(2)));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(44), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(handle, handleParams);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("扫码登录 B 站", 19, 0xFF171A1F, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView close = pillText("\u00d7", 20, 0xFF59606A, 0xFFF0F1F3);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        content.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView hint = text("用官方 B 站 App 扫码确认。成功后会保存 Cookie，只用于当前净流推荐、点赞和评论接口。", 14, 0xFF606873, Typeface.NORMAL);
        hint.setLineSpacing(dp(2), 1.0f);
        content.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        ImageView qrImage = new ImageView(this);
        qrImage.setBackground(rounded(0xFFF5F6F8, dp(14)));
        qrImage.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(248), dp(248));
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.topMargin = dp(18);
        content.addView(qrImage, qrParams);

        TextView status = text("正在生成二维码...", 14, 0xFF7A818C, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, dp(42));
        statusParams.topMargin = dp(8);
        content.addView(status, statusParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        TextView refresh = sheetPill("刷新二维码");
        TextView manual = sheetPill("手动 Cookie");
        buttons.addView(refresh, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams manualParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        manualParams.leftMargin = dp(10);
        buttons.addView(manual, manualParams);
        content.addView(buttons, new LinearLayout.LayoutParams(-1, dp(46)));

        final boolean[] alive = new boolean[]{true};
        final Runnable[] loader = new Runnable[1];
        loader[0] = () -> loadQrLoginCode(qrImage, status, dialog, alive);
        refresh.setOnClickListener(v -> {
            alive[0] = false;
            alive[0] = true;
            qrImage.setImageDrawable(null);
            status.setText("正在刷新二维码...");
            loader[0].run();
        });
        manual.setOnClickListener(v -> {
            dialog.dismiss();
            showCookieLoginDialog();
        });

        dialog.setOnDismissListener(d -> alive[0] = false);
        dialog.setContentView(content);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        loader[0].run();
    }

    private void loadQrLoginCode(ImageView qrImage, TextView status, Dialog dialog, boolean[] alive) {
        new Thread(() -> {
            try {
                QrLoginSession session = repository.apiClient().createQrLoginSession();
                Bitmap bitmap = createQrBitmap(session.url, dp(220));
                runOnUiThread(() -> {
                    if (!alive[0]) return;
                    qrImage.setImageBitmap(bitmap);
                    status.setText("等待扫码");
                    startQrLoginPolling(session.key, status, dialog, alive);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (alive[0]) status.setText("二维码生成失败：" + error.getMessage());
                });
            }
        }).start();
    }

    private void startQrLoginPolling(String key, TextView status, Dialog dialog, boolean[] alive) {
        final Runnable[] poller = new Runnable[1];
        poller[0] = () -> {
            if (!alive[0]) return;
            new Thread(() -> {
                try {
                    QrLoginResult result = repository.apiClient().pollQrLogin(key);
                    runOnUiThread(() -> {
                        if (!alive[0]) return;
                        if (result.loggedIn()) {
                            alive[0] = false;
                            saveAuthCookieAndReload(result.cookie);
                            status.setText("登录成功");
                            dialog.dismiss();
                            Toast.makeText(this, "已登录", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (result.code == 86038) {
                            status.setText("二维码已过期，请刷新");
                            return;
                        }
                        status.setText(qrLoginStatusText(result));
                        uiHandler.postDelayed(poller[0], 1800);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        if (!alive[0]) return;
                        status.setText("轮询失败，稍后重试");
                        uiHandler.postDelayed(poller[0], 2400);
                    });
                }
            }).start();
        };
        uiHandler.postDelayed(poller[0], 1400);
    }

    private String qrLoginStatusText(QrLoginResult result) {
        if (result.code == 86101) return "等待扫码";
        if (result.code == 86090) return "已扫码，请在手机上确认";
        return result.message == null || result.message.isEmpty() ? "等待确认" : result.message;
    }

    private Bitmap createQrBitmap(String value, int size) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }

    private void saveAuthCookieAndReload(String cookie) {
        prefs.edit().putString(PREF_COOKIE, cookie).apply();
        repository.apiClient().setAuthCookie(cookie);
        repository.clear();
        previousStack.clear();
        nextStack.clear();
        forwardReturnStack.clear();
        playbackPositions.clear();
        currentVideo = null;
        clearPrefetch();
        refreshAuthState();
        loadInitial();
    }

    private void logout() {
        prefs.edit().remove(PREF_COOKIE).apply();
        repository.apiClient().setAuthCookie("");
        repository.clear();
        previousStack.clear();
        nextStack.clear();
        forwardReturnStack.clear();
        playbackPositions.clear();
        currentVideo = null;
        clearPrefetch();
        refreshAuthState();
        loadInitial();
        Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show();
    }

    private void refreshAuthState() {
        new Thread(() -> {
            try {
                AuthState state = repository.apiClient().fetchAuthState();
                if (state.loggedIn) {
                    runOnUiThread(() -> Toast.makeText(this, "已登录：" + state.userName, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void updateProgress() {
        if (progressFill == null || player == null) return;
        long duration = player.getDuration();
        long position = player.getCurrentPosition();
        rememberCurrentPlaybackPosition();
        if (closeAfterCurrentVideo && duration > 0 && position >= duration - 900L) {
            closeAfterCurrentVideo = false;
            timerCloseOption = "关闭";
            player.pause();
            showCenter("已播完暂停");
            Toast.makeText(this, "已按定时关闭暂停", Toast.LENGTH_SHORT).show();
        }
        if (!progressDragging && !landscapeProgressDragging) {
            updateProgressFill(progressFill, position, duration);
            updateProgressFill(lightProgressFill, position, duration);
        }
        updateLightControlsPlaybackState();
    }

    private void updateProgressFill(View fillView, long position, long duration) {
        if (fillView == null || fillView.getParent() == null) return;
        int width = ((View) fillView.getParent()).getWidth();
        int fill = duration > 0 && width > 0 ? Math.max(1, (int) (width * position / duration)) : 1;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) fillView.getLayoutParams();
        params.width = fill;
        fillView.setLayoutParams(params);
        SeekTvThumbView thumb = fillView == progressFill ? progressThumb : (fillView == lightProgressFill ? lightProgressThumb : null);
        updateSeekTvThumbPosition(thumb, width, fill);
    }

    private void updateSeekTvThumbPosition(SeekTvThumbView thumb, int trackWidth, int fillWidth) {
        if (thumb == null || thumb.getParent() == null || trackWidth <= 0) return;
        FrameLayout.LayoutParams thumbParams = (FrameLayout.LayoutParams) thumb.getLayoutParams();
        int thumbWidth = thumbParams.width > 0 ? thumbParams.width : dp(24);
        thumbParams.leftMargin = Math.max(0, Math.min(Math.max(0, trackWidth - thumbWidth), fillWidth - thumbWidth / 2));
        thumb.setLayoutParams(thumbParams);
        thumb.bringToFront();
    }

    private void updateSeekTvEyesFromDelta(float deltaX) {
        if (Math.abs(deltaX) < 0.2f) return;
        float target = Math.max(-1f, Math.min(1f, deltaX / Math.max(1f, dp(18))));
        updateSeekTvEyes(target, false);
    }

    private void updateSeekTvEyes(float target, boolean animate) {
        if (progressThumb != null) progressThumb.setEyeDirection(target, animate);
        if (lightProgressThumb != null) lightProgressThumb.setEyeDirection(target, animate);
    }

    private void updateLightControlsPlaybackState() {
        if (player == null) return;
        if (lightPlayButton != null) {
            lightPlayButton.setText(player.isPlaying() ? "Ⅱ" : "▶");
        }
        long duration = player.getDuration();
        if (duration <= 0) duration = currentItem == null ? 0 : currentItem.durationSeconds * 1000L;
        if (lightTimeView != null) {
            lightTimeView.setText(formatMillis(player.getCurrentPosition()));
        }
        if (landscapeDurationView != null) {
            landscapeDurationView.setText(formatMillis(duration));
        }
        if (landscapeStatusTimeView != null) {
            landscapeStatusTimeView.setText(currentClockText());
        }
        if (landscapeSpeedView != null) {
            landscapeSpeedView.setText("倍速");
        }
        if (landscapeClarityView != null) {
            landscapeClarityView.setText(currentClarity);
        }
    }

    private long progressPositionFor(float x, int width) {
        if (player == null || width <= 0) return -1L;
        long duration = player.getDuration();
        if (duration <= 0) return -1L;
        float ratio = Math.max(0f, Math.min(1f, x / width));
        return (long) (duration * ratio);
    }

    private void previewProgressAt(float x, int width) {
        long position = progressPositionFor(x, width);
        if (position < 0) return;
        progressDragPositionMs = position;
        showProgressBubbleForPosition(position, x, width);
        long duration = player == null ? 0L : player.getDuration();
        updateProgressFill(progressFill, position, duration);
        updateProgressFill(lightProgressFill, position, duration);
        updateLightControlsPlaybackState();
    }

    private long landscapeProgressPositionFor(float rawX) {
        if (player == null || root == null) return -1L;
        long duration = player.getDuration();
        if (duration <= 0) return -1L;
        long start = landscapeProgressStartPositionMs >= 0L
                ? landscapeProgressStartPositionMs
                : Math.max(0L, player.getCurrentPosition());
        int width = Math.max(1, root.getWidth());
        long deltaMs = Math.round(duration * ((rawX - landscapeProgressStartX) / width));
        return Math.max(0L, Math.min(duration, start + deltaMs));
    }

    private void previewLandscapeProgressAt(float rawX) {
        long position = landscapeProgressPositionFor(rawX);
        if (position < 0 || player == null || root == null) return;
        if (!Float.isNaN(landscapeProgressLastX)) {
            updateSeekTvEyesFromDelta(rawX - landscapeProgressLastX);
        }
        landscapeProgressLastX = rawX;
        progressDragPositionMs = position;
        long duration = player.getDuration();
        int width = Math.max(1, root.getWidth());
        float bubbleX = duration > 0 ? width * (position / (float) duration) : rawX;
        showProgressBubbleForPosition(position, bubbleX, width);
        updateProgressFill(progressFill, position, duration);
        updateProgressFill(lightProgressFill, position, duration);
        updateLightControlsPlaybackState();
    }

    private void commitLandscapeProgressAt(float rawX) {
        long position = landscapeProgressPositionFor(rawX);
        if (position < 0 && progressDragPositionMs >= 0) position = progressDragPositionMs;
        if (position < 0) return;
        seekToPositionFromUser(position);
        progressDragPositionMs = -1L;
        landscapeProgressStartPositionMs = -1L;
    }

    private void commitProgressAt(float x, int width) {
        long position = progressPositionFor(x, width);
        if (position < 0 && progressDragPositionMs >= 0) position = progressDragPositionMs;
        if (position < 0) return;
        seekToPositionFromUser(position);
        progressDragPositionMs = -1L;
    }

    private void seekToPositionFromUser(long position) {
        if (player == null) return;
        long duration = player.getDuration();
        if (duration <= 0) return;
        position = Math.max(0L, Math.min(duration, position));
        player.seekTo(position);
        if (!activeDanmakuEntries.isEmpty()) {
            resetDanmakuCursorFor(position);
            danmakuLayer.clear();
        }
        hideDanmakuActionBox();
        updateProgress();
    }

    private void showProgressBubbleForPosition(long position, float x, int width) {
        if (progressBubbleCard == null || progressBubble == null || player == null || width <= 0 || root == null) return;
        long duration = player.getDuration();
        if (duration <= 0) return;
        float ratio = Math.max(0f, Math.min(1f, x / width));
        uiHandler.removeCallbacks(hideProgressBubbleRunnable);
        progressBubble.setText(formatMillis(position) + " / " + formatMillis(duration));
        requestProgressFrame(position);
        progressBubbleCard.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) progressBubbleCard.getLayoutParams();
        int bubbleWidth = dp(156);
        int left = dp(16) + Math.round(ratio * width) - bubbleWidth / 2;
        int maxLeft = Math.max(dp(16), root.getWidth() - bubbleWidth - dp(16));
        params.leftMargin = Math.max(dp(16), Math.min(maxLeft, left));
        params.topMargin = landscapeMode
                ? Math.max(dp(24), root.getHeight() - dp(190))
                : Math.max(dp(120), root.getHeight() - dp(312));
        progressBubbleCard.setLayoutParams(params);
        progressBubbleCard.bringToFront();
    }

    private void hideProgressBubbleLater(long delayMs) {
        uiHandler.removeCallbacks(hideProgressBubbleRunnable);
        uiHandler.postDelayed(hideProgressBubbleRunnable, Math.max(0L, delayMs));
    }

    private void requestProgressFrame(long positionMs) {
        if (progressBubbleCover == null || currentItem == null) return;
        String videoKey = videoIdentity(currentItem);
        if (!videoKey.equals(progressVideoShotKey)) {
            progressVideoShotKey = videoKey;
            progressVideoShotInfo = null;
            progressVideoShotLoading = false;
            progressSpriteLoadingUrl = "";
            clearProgressSpriteCache();
        }
        if (progressVideoShotInfo == null || !progressVideoShotInfo.isUsable()) {
            pendingProgressFramePositionMs = positionMs;
            loadProgressVideoShotInfo(videoKey);
            return;
        }
        int index = findVideoShotIndex(progressVideoShotInfo.pvTimes, Math.max(0, Math.round(positionMs / 1000f)));
        int cellsPerImage = Math.max(1, progressVideoShotInfo.imgXLen * progressVideoShotInfo.imgYLen);
        int imageIndex = Math.max(0, Math.min(progressVideoShotInfo.imageUrls.size() - 1, index / cellsPerImage));
        String imageUrl = progressVideoShotInfo.imageUrls.get(imageIndex);
        Bitmap sprite = progressFrameCache.get(imageUrl);
        if (sprite != null) {
            progressBubbleCover.showSprite(sprite, videoShotSrcRect(progressVideoShotInfo, index));
            return;
        }
        pendingProgressFramePositionMs = positionMs;
        loadProgressSpriteImage(videoKey, imageUrl);
    }

    private void loadProgressVideoShotInfo(String videoKey) {
        if (progressVideoShotLoading || currentItem == null) return;
        progressVideoShotLoading = true;
        int generation = ++progressFrameGeneration;
        FeedItem item = currentItem;
        new Thread(() -> {
            VideoShotInfo info = null;
            try {
                info = repository.apiClient().fetchVideoShot(item);
            } catch (Exception ignored) {
            }
            VideoShotInfo finalInfo = info;
            runOnUiThread(() -> {
                progressVideoShotLoading = false;
                if (generation != progressFrameGeneration || !videoKey.equals(progressVideoShotKey)) return;
                progressVideoShotInfo = finalInfo != null && finalInfo.isUsable() ? finalInfo : null;
                long pending = pendingProgressFramePositionMs;
                pendingProgressFramePositionMs = -1L;
                if (progressVideoShotInfo != null && pending >= 0L) requestProgressFrame(pending);
            });
        }).start();
    }

    private void loadProgressSpriteImage(String videoKey, String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;
        if (imageUrl.equals(progressSpriteLoadingUrl)) return;
        progressSpriteLoadingUrl = imageUrl;
        int generation = ++progressFrameGeneration;
        FeedItem item = currentItem;
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                byte[] bytes = repository.apiClient().downloadBytes(imageUrl, item == null ? "https://www.bilibili.com/" : item.webUrl());
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (Exception ignored) {
            }
            Bitmap finalBitmap = bitmap;
            runOnUiThread(() -> {
                if (imageUrl.equals(progressSpriteLoadingUrl)) progressSpriteLoadingUrl = "";
                if (generation != progressFrameGeneration || !videoKey.equals(progressVideoShotKey)) {
                    if (finalBitmap != null) finalBitmap.recycle();
                    return;
                }
                long pending = pendingProgressFramePositionMs;
                pendingProgressFramePositionMs = -1L;
                if (finalBitmap == null) return;
                progressFrameCache.put(imageUrl, finalBitmap);
                if (pending >= 0L) requestProgressFrame(pending);
            });
        }).start();
    }

    private int findVideoShotIndex(int[] times, int currentTimeSec) {
        if (times == null || times.length == 0) return 0;
        int left = 0;
        int right = times.length - 1;
        int answer = 0;
        while (left <= right) {
            int mid = (left + right) >>> 1;
            if (times[mid] <= currentTimeSec) {
                answer = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        // B 站 pvdata 是稀疏时间点。选择未来帧会让缩略图看起来比 seek 位置提前几秒。
        return Math.max(0, Math.min(times.length - 1, answer));
    }

    private Rect videoShotSrcRect(VideoShotInfo info, int index) {
        int cellsPerImage = Math.max(1, info.imgXLen * info.imgYLen);
        int innerIndex = Math.max(0, index % cellsPerImage);
        int col = innerIndex % Math.max(1, info.imgXLen);
        int row = innerIndex / Math.max(1, info.imgXLen);
        int left = col * info.imgXSize;
        int top = row * info.imgYSize;
        return new Rect(left, top, left + info.imgXSize, top + info.imgYSize);
    }

    private void clearProgressSpriteCache() {
        for (Bitmap bitmap : progressFrameCache.values()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        progressFrameCache.clear();
    }

    private String formatMillis(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.CHINA, "%02d:%02d", minutes, seconds);
    }

    private String currentPlaybackTimeText() {
        if (player == null) return "00:00 / 00:00";
        long duration = player.getDuration();
        if (duration <= 0) duration = currentItem == null ? 0 : currentItem.durationSeconds * 1000L;
        return formatMillis(player.getCurrentPosition()) + " / " + formatMillis(duration);
    }

    private String currentClockText() {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
    }

    private String speedLabel(float speed) {
        if (Math.abs(speed - Math.round(speed)) < 0.01f) {
            return String.format(Locale.CHINA, "%.0fx", speed);
        }
        return String.format(Locale.CHINA, "%.2fx", speed).replace("0x", "x");
    }

    private void loadImageInto(String url, ImageView target) {
        if (target == null) return;
        if (target instanceof VideoShotPreviewView) {
            ((VideoShotPreviewView) target).clearSprite();
        }
        target.setImageDrawable(null);
        target.setTag(null);
        if (url == null || url.isEmpty()) return;
        String safeUrl = url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
        target.setTag(safeUrl);
        Bitmap cached = imageCache.get(safeUrl);
        if (cached != null) {
            target.setImageBitmap(cached);
            notifyBitmapLoaded(target, cached);
            return;
        }
        new Thread(() -> {
            try {
                InputStream stream = new URL(safeUrl).openStream();
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                stream.close();
                if (bitmap != null) {
                    imageCache.put(safeUrl, bitmap);
                    runOnUiThread(() -> {
                        if (safeUrl.equals(target.getTag())) {
                            target.setImageBitmap(bitmap);
                            notifyBitmapLoaded(target, bitmap);
                        }
                    });
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void notifyBitmapLoaded(ImageView target, Bitmap bitmap) {
        if (target instanceof BitmapAwareImage) {
            ((BitmapAwareImage) target).onBitmapLoaded(bitmap);
        }
    }

    private static final class CommentVoteBinding {
        final ImageView likeIcon;
        final TextView likeCount;
        final ImageView dislikeIcon;

        CommentVoteBinding(ImageView likeIcon, TextView likeCount, ImageView dislikeIcon) {
            this.likeIcon = likeIcon;
            this.likeCount = likeCount;
            this.dislikeIcon = dislikeIcon;
        }

        boolean isAttached() {
            return likeIcon != null
                    && likeCount != null
                    && dislikeIcon != null
                    && likeIcon.getParent() != null
                    && likeCount.getParent() != null
                    && dislikeIcon.getParent() != null;
        }
    }

    private Bitmap downloadBitmap(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String safeUrl = url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
        try {
            Bitmap cached = imageCache.get(safeUrl);
            if (cached != null) return cached;
            InputStream stream = new URL(safeUrl).openStream();
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            stream.close();
            if (bitmap != null) imageCache.put(safeUrl, bitmap);
            return bitmap;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void renderCommentTextLegacy(TextView target, CommentItem comment) {
        String raw = comment.message == null ? "" : comment.message;
        target.setText(displayCommentMessage(raw));
        if (comment.emoteUrls.isEmpty()) return;
        new Thread(() -> {
            try {
                SpannableStringBuilder builder = new SpannableStringBuilder(displayCommentMessage(raw));
                List<String> keys = new ArrayList<>(comment.emoteUrls.keySet());
                keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
                boolean changed = false;
                for (String key : keys) {
                    Bitmap bitmap = downloadBitmap(comment.emoteUrls.get(key));
                    if (bitmap == null) continue;
                    Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                    int size = key.contains("热词") ? dp(42) : dp(22);
                    drawable.setBounds(0, 0, size, size);
                    String text = builder.toString();
                    int start = text.indexOf(key);
                    while (start >= 0) {
                        int end = start + key.length();
                        builder.setSpan(new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        changed = true;
                        start = builder.toString().indexOf(key, end);
                    }
                }
                if (changed) runOnUiThread(() -> target.setText(builder));
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void makeCircular(ImageView view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        view.setClipToOutline(true);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
    }

    private TextView commentStatus(String value) {
        TextView view = text(value, 16, 0xFF8B9098, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(32), 0, dp(32));
        return view;
    }

    private RailActionButton railButton(int iconRes, String iconText, String count) {
        RailActionButton view = new RailActionButton(this, iconRes, iconText);
        view.setCount(count);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(66), dp(68));
        params.setMargins(0, 0, 0, dp(4));
        view.setLayoutParams(params);
        return view;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(true);
        return view;
    }

    private TextView pillText(String value, int sp, int color, int background) {
        TextView view = text(value, sp, color, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), 0, dp(10), 0);
        view.setSingleLine(true);
        view.setBackground(rounded(background, dp(24)));
        return view;
    }

    private ImageView iconImage(int resId, String description) {
        ImageView view = new ImageView(this);
        view.setImageResource(resId);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setAdjustViewBounds(false);
        view.setPadding(0, 0, 0, 0);
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setContentDescription(description);
        return view;
    }

    private void applySearchEntranceIcon(TextView target) {
        if (target == null) return;
        Drawable icon = getResources().getDrawable(R.drawable.ic_bili_search_clean);
        int size = Math.max(1, Math.min(docWidth(4.7f), effectiveDocHeight(2.55f)));
        icon.setBounds(0, 0, size, size);
        target.setCompoundDrawables(icon, null, null, null);
        target.setCompoundDrawablePadding(Math.max(1, docWidth(1.45f)));
    }

    private int levelIconRes(String level) {
        if (level == null) return 0;
        int value = 0;
        for (int i = 0; i < level.length(); i++) {
            char c = level.charAt(i);
            if (c >= '0' && c <= '9') value = value * 10 + (c - '0');
        }
        switch (value) {
            case 1:
                return R.drawable.ic_bili_level_1;
            case 2:
                return R.drawable.ic_bili_level_2;
            case 3:
                return R.drawable.ic_bili_level_3;
            case 4:
                return R.drawable.ic_bili_level_4;
            case 5:
                return R.drawable.ic_bili_level_5;
            case 6:
                return R.drawable.ic_bili_level_6;
            default:
                return 0;
        }
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable strokeRounded(int strokeColor, int fillColor, int radius, int strokeWidth) {
        GradientDrawable drawable = rounded(fillColor, radius);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private GradientDrawable topRounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float r = radius;
        drawable.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return drawable;
    }

    private void showCenter(String value) {
        centerOverlay.setText(value);
        centerOverlay.setTextSize(value.contains("\n") ? 17 : 26);
        centerOverlay.setVisibility(View.VISIBLE);
        centerOverlay.setAlpha(1f);
        centerOverlay.animate().cancel();
        centerOverlay.animate().alpha(0f).setStartDelay(650).setDuration(250).withEndAction(() -> centerOverlay.setVisibility(View.GONE)).start();
    }

    private void status(String text) {
        if (metaView == null) return;
        if (currentItem == null) {
            metaView.setText(text);
        } else {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        }
    }

    private String watchingText(FeedItem item) {
        if (item.viewCount > 1000000) return "1000+人正在看";
        if (item.viewCount > 100000) return "100+人正在看";
        return "5人正在看";
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private String userInitial(String user) {
        if (user == null || user.isEmpty()) return "匿";
        return user.substring(0, 1);
    }

    private String compactLine(String value) {
        if (value == null) return "";
        String oneLine = value.replace('\n', ' ').trim();
        return oneLine.length() > 22 ? oneLine.substring(0, 22) + "..." : oneLine;
    }

    private String cleanedNoticeText(String value) {
        String text = compactLine(value);
        if (text.isEmpty()) return "";
        String normalized = text
                .replace("⌕", "")
                .replace("🔍", "")
                .trim();
        if (normalized.startsWith("搜索 ·")
                || normalized.startsWith("搜索·")
                || normalized.startsWith("搜索:")
                || normalized.startsWith("搜索：")) {
            return "";
        }
        return text;
    }

    private String searchSuggestText(FeedItem item) {
        String value = item == null ? "" : fallback(item.title, "相关视频");
        value = value
                .replace("【", "")
                .replace("】", " ")
                .replace("#", " ")
                .replace("|", " ")
                .replace("：", " ")
                .replace(":", " ")
                .trim();
        if (value.isEmpty()) value = "相关视频";
        return value.length() > 14 ? value.substring(0, 14) : value;
    }

    private String displayCommentMessage(String value) {
        if (value == null) return "";
        return value
                .replace("[doge]", "🐶")
                .replace("[笑哭]", "😂")
                .replace("[妙啊]", "👍")
                .replace("[OK]", "👌")
                .replace("[热词系列_知识增加]", "📚知识增加")
                .replace("[热词系列_泪目]", "🥲泪目")
                .replace("[热词系列_好耶]", "🎉好耶");
    }

    private void renderCommentText(TextView target, CommentItem comment) {
        String raw = comment.message == null ? "" : comment.message;
        target.setText(displayCommentMessage(raw));
        if (comment.emoteUrls.isEmpty()) return;

        new Thread(() -> {
            try {
                SpannableStringBuilder builder = new SpannableStringBuilder(raw);
                List<String> keys = new ArrayList<>(comment.emoteUrls.keySet());
                keys.sort((left, right) -> Integer.compare(right.length(), left.length()));
                boolean applied = false;
                for (String key : keys) {
                    Bitmap bitmap = downloadBitmap(comment.emoteUrls.get(key));
                    if (bitmap == null) continue;
                    int start = raw.indexOf(key);
                    while (start >= 0) {
                        int end = start + key.length();
                        int size = key.contains("热词") ? dp(42) : dp(22);
                        Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                        drawable.setBounds(0, 0, size, size);
                        builder.setSpan(new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        applied = true;
                        start = raw.indexOf(key, end);
                    }
                }
                boolean hasImages = applied;
                runOnUiThread(() -> target.setText(hasImages ? builder : displayCommentMessage(raw)));
            } catch (Exception ignored) {
                runOnUiThread(() -> target.setText(displayCommentMessage(raw)));
            }
        }).start();
    }

    private Bitmap downloadBitmapLegacy(String url) {
        if (url == null || url.isEmpty()) return null;
        String safeUrl = url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
        try {
            InputStream stream = new URL(safeUrl).openStream();
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            stream.close();
            return bitmap;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String locationText(String location) {
        return location == null || location.isEmpty() ? "" : " " + location;
    }

    private String formatDuration(int seconds) {
        if (seconds <= 0) return "";
        return String.format(Locale.CHINA, "%d:%02d", seconds / 60, seconds % 60);
    }

    private String formatCount(long value) {
        if (value <= 0) return "0";
        if (value >= 100000000) {
            return trimDecimal(value / 100000000.0) + "亿";
        }
        if (value >= 10000) {
            return trimDecimal(value / 10000.0) + "万";
        }
        return String.valueOf(value);
    }

    private String trimDecimal(double value) {
        if (value >= 100) return String.valueOf(Math.round(value));
        String text = String.format(Locale.CHINA, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    private final class VideoShotPreviewView extends ImageView {
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect sourceRect = new Rect();
        private final RectF targetRect = new RectF();
        private Bitmap spriteBitmap;

        VideoShotPreviewView(Context context) {
            super(context);
            setScaleType(ScaleType.CENTER_CROP);
        }

        void showSprite(Bitmap bitmap, Rect source) {
            spriteBitmap = bitmap;
            if (source != null) {
                sourceRect.set(source);
            } else {
                sourceRect.setEmpty();
            }
            invalidate();
        }

        void clearSprite() {
            spriteBitmap = null;
            sourceRect.setEmpty();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (spriteBitmap != null && !spriteBitmap.isRecycled() && !sourceRect.isEmpty()) {
                targetRect.set(0f, 0f, getWidth(), getHeight());
                canvas.drawBitmap(spriteBitmap, sourceRect, targetRect, bitmapPaint);
                return;
            }
            super.onDraw(canvas);
        }
    }

    private interface BitmapAwareImage {
        void onBitmapLoaded(Bitmap bitmap);
    }

    private final class CommentPictureView extends ImageView implements BitmapAwareImage {
        private int sourceWidth;
        private int sourceHeight;
        private boolean singleLayout;

        CommentPictureView(Context context) {
            super(context);
            setScaleType(ImageView.ScaleType.CENTER_CROP);
            setBackground(rounded(0xFFE7E9EE, Math.max(1, Math.round(rootWidth() * 0.008f))));
        }

        void setMetadata(int width, int height) {
            sourceWidth = Math.max(0, width);
            sourceHeight = Math.max(0, height);
        }

        void setSingleLayout(boolean value) {
            singleLayout = value;
        }

        float aspectRatio() {
            if (sourceWidth > 0 && sourceHeight > 0) return sourceWidth / (float) sourceHeight;
            return 1f;
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap) {
            if (bitmap != null) {
                if (sourceWidth <= 0) sourceWidth = bitmap.getWidth();
                if (sourceHeight <= 0) sourceHeight = bitmap.getHeight();
            }
            if (singleLayout) post(() -> applySingleCommentPictureLayout(this));
        }
    }

    private final class DisplayModeIconButton extends ImageView {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean fallbackFullscreenIcon;

        DisplayModeIconButton(Context context) {
            super(context);
            setScaleType(ImageView.ScaleType.FIT_CENTER);
            setAdjustViewBounds(false);
            setBackgroundColor(Color.TRANSPARENT);
            setClickable(true);
        }

        void setRasterIcon(int resId) {
            fallbackFullscreenIcon = false;
            setImageResource(resId);
            invalidate();
        }

        void setFallbackFullscreenIcon() {
            fallbackFullscreenIcon = true;
            setImageDrawable(null);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!fallbackFullscreenIcon) {
                super.onDraw(canvas);
                return;
            }
            float w = getWidth();
            float h = getHeight();
            float stroke = Math.max(3f, Math.min(w, h) * 0.075f);
            float padX = w * 0.18f;
            float padY = h * 0.16f;
            float arm = Math.min(w, h) * 0.28f;
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.SQUARE);

            canvas.drawLine(padX, padY, padX + arm, padY, paint);
            canvas.drawLine(padX, padY, padX, padY + arm, paint);
            canvas.drawLine(w - padX, padY, w - padX - arm, padY, paint);
            canvas.drawLine(w - padX, padY, w - padX, padY + arm, paint);
            canvas.drawLine(padX, h - padY, padX + arm, h - padY, paint);
            canvas.drawLine(padX, h - padY, padX, h - padY - arm, paint);
            canvas.drawLine(w - padX, h - padY, w - padX - arm, h - padY, paint);
            canvas.drawLine(w - padX, h - padY, w - padX, h - padY - arm, paint);
        }
    }

    private final class LandscapeActionButton extends FrameLayout {
        private final ImageView iconView;
        private final TextView countView;
        private int inactiveIconRes;
        private int activeIconRes;
        private boolean usesStateIconResources;

        LandscapeActionButton(Context context, int iconRes) {
            super(context);
            setClickable(true);
            setFocusable(true);
            setClipChildren(false);
            setClipToPadding(false);
            inactiveIconRes = iconRes;

            iconView = new ImageView(context);
            iconView.setImageResource(iconRes);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iconView.setColorFilter(Color.WHITE);
            addView(iconView, new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER));

            countView = text("", 12, Color.WHITE, Typeface.BOLD);
            countView.setGravity(Gravity.CENTER);
            countView.setIncludeFontPadding(false);
            countView.setShadowLayer(4, 0, 1, 0xAA000000);
            addView(countView, new FrameLayout.LayoutParams(dp(54), dp(18), Gravity.TOP | Gravity.RIGHT));
        }

        void setStateIcons(int inactiveRes, int activeRes) {
            usesStateIconResources = true;
            inactiveIconRes = inactiveRes;
            activeIconRes = activeRes;
            iconView.clearColorFilter();
            iconView.setImageResource(inactiveIconRes);
        }

        void setCount(String value) {
            countView.setText(value == null ? "" : value);
            countView.setVisibility(value == null || value.isEmpty() ? GONE : VISIBLE);
        }

        void setIconColor(int color) {
            if (usesStateIconResources) {
                iconView.clearColorFilter();
                int res = color == BILI_PINK && activeIconRes != 0 ? activeIconRes : inactiveIconRes;
                iconView.setImageResource(res);
            } else {
                iconView.setColorFilter(color);
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            int icon = Math.max(1, Math.round(Math.min(w, h) * 0.58f));
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(icon, icon, Gravity.CENTER);
            iconParams.topMargin = Math.round(h * 0.08f);
            iconView.setLayoutParams(iconParams);

            FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(
                    Math.max(1, Math.round(w * 0.70f)),
                    Math.max(1, Math.round(h * 0.26f)),
                    Gravity.TOP | Gravity.RIGHT);
            countParams.topMargin = 0;
            countView.setLayoutParams(countParams);
        }
    }

    private final class SeekTvThumbView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float eyeDirection;
        private ValueAnimator eyeAnimator;

        SeekTvThumbView(Context context) {
            super(context);
            setWillNotDraw(false);
            setClickable(false);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        void setEyeDirection(float target, boolean animate) {
            target = Math.max(-1f, Math.min(1f, target));
            if (eyeAnimator != null) eyeAnimator.cancel();
            if (animate) {
                eyeAnimator = ValueAnimator.ofFloat(eyeDirection, target);
                eyeAnimator.setDuration(160);
                eyeAnimator.setInterpolator(swipeInterpolator);
                eyeAnimator.addUpdateListener(animation -> {
                    eyeDirection = (float) animation.getAnimatedValue();
                    invalidate();
                });
                eyeAnimator.start();
            } else {
                eyeDirection += (target - eyeDirection) * 0.55f;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            if (size <= 0f) return;
            float scale = size / 18f;
            float left = (getWidth() - size) / 2f;
            float top = (getHeight() - size) / 2f;
            canvas.save();
            canvas.translate(left, top);
            canvas.scale(scale, scale);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x24FF6699);
            rect.set(0f, 1.9f, 18f, 16.9f);
            canvas.drawRoundRect(rect, 4f, 4f, paint);
            paint.setColor(0x47FF6699);
            rect.set(1f, 2.9f, 17f, 15.9f);
            canvas.drawRoundRect(rect, 3.5f, 3.5f, paint);

            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.45f);
            paint.setColor(0xFF333333);
            canvas.drawLine(6.2f, 4.7f, 4.9f, 1.3f, paint);
            canvas.drawLine(11.8f, 4.7f, 13.1f, 1.3f, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            rect.set(2.05f, 4.35f, 15.95f, 15.35f);
            canvas.drawRoundRect(rect, 2.5f, 2.5f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.45f);
            paint.setColor(0xFF333333);
            canvas.drawRoundRect(rect, 2.5f, 2.5f, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF333333);
            float offset = eyeDirection * 2.35f;
            drawSeekTvEye(canvas, 6.25f + offset, 9.75f, 1.75f, 4.0f);
            drawSeekTvEye(canvas, 11.75f + offset, 9.75f, 1.8f, 4.0f);
            canvas.restore();
        }

        private void drawSeekTvEye(Canvas canvas, float cx, float cy, float width, float height) {
            rect.set(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
            canvas.drawRoundRect(rect, width / 2f, width / 2f, paint);
        }
    }

    private final class LandscapeIconView extends View {
        static final int BATTERY = 1;
        static final int MORE = 2;
        static final int CAMERA = 3;
        static final int LOCK = 4;
        static final int SIDE_CHEVRON = 5;
        static final int TREASURE = 6;
        static final int PROGRESS_THUMB = 7;

        private final int kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        LandscapeIconView(Context context, int kind) {
            super(context);
            this.kind = kind;
            setWillNotDraw(false);
            setClickable(kind != BATTERY && kind != SIDE_CHEVRON && kind != PROGRESS_THUMB && kind != TREASURE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(Math.max(2f, Math.min(w, h) * 0.075f));
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);

            if (kind == BATTERY) {
                rect.set(w * 0.10f, h * 0.28f, w * 0.82f, h * 0.72f);
                canvas.drawRoundRect(rect, h * 0.10f, h * 0.10f, paint);
                rect.set(w * 0.84f, h * 0.40f, w * 0.94f, h * 0.60f);
                canvas.drawRoundRect(rect, h * 0.05f, h * 0.05f, paint);
                paint.setStyle(Paint.Style.FILL);
                rect.set(w * 0.18f, h * 0.37f, w * 0.35f, h * 0.63f);
                canvas.drawRoundRect(rect, h * 0.04f, h * 0.04f, paint);
                return;
            }

            if (kind == MORE) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, h * 0.27f, Math.min(w, h) * 0.055f, paint);
                canvas.drawCircle(cx, cy, Math.min(w, h) * 0.055f, paint);
                canvas.drawCircle(cx, h * 0.73f, Math.min(w, h) * 0.055f, paint);
                return;
            }

            if (kind == CAMERA) {
                rect.set(w * 0.20f, h * 0.28f, w * 0.82f, h * 0.75f);
                canvas.drawRoundRect(rect, h * 0.08f, h * 0.08f, paint);
                canvas.drawLine(w * 0.35f, h * 0.28f, w * 0.43f, h * 0.16f, paint);
                canvas.drawLine(w * 0.43f, h * 0.16f, w * 0.60f, h * 0.16f, paint);
                canvas.drawLine(w * 0.60f, h * 0.16f, w * 0.68f, h * 0.28f, paint);
                canvas.drawCircle(cx, h * 0.53f, Math.min(w, h) * 0.13f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(w * 0.72f, h * 0.38f, Math.min(w, h) * 0.035f, paint);
                return;
            }

            if (kind == LOCK) {
                rect.set(w * 0.22f, h * 0.45f, w * 0.78f, h * 0.78f);
                canvas.drawRoundRect(rect, h * 0.07f, h * 0.07f, paint);
                rect.set(w * 0.38f, h * 0.17f, w * 0.76f, h * 0.55f);
                canvas.drawArc(rect, 180, -210, false, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, h * 0.61f, Math.min(w, h) * 0.035f, paint);
                return;
            }

            if (kind == SIDE_CHEVRON) {
                paint.setColor(0x99FFFFFF);
                paint.setStrokeWidth(Math.max(4f, Math.min(w, h) * 0.12f));
                canvas.drawLine(w * 0.62f, h * 0.22f, w * 0.38f, cy, paint);
                canvas.drawLine(w * 0.38f, cy, w * 0.62f, h * 0.78f, paint);
                return;
            }

            if (kind == TREASURE) {
                paint.setColor(BILI_PINK);
                paint.setStrokeWidth(Math.max(2f, Math.min(w, h) * 0.07f));
                rect.set(w * 0.24f, h * 0.35f, w * 0.76f, h * 0.72f);
                canvas.drawRoundRect(rect, h * 0.09f, h * 0.09f, paint);
                canvas.drawCircle(w * 0.34f, h * 0.30f, Math.min(w, h) * 0.08f, paint);
                canvas.drawCircle(w * 0.66f, h * 0.30f, Math.min(w, h) * 0.08f, paint);
                canvas.drawLine(w * 0.38f, h * 0.48f, w * 0.62f, h * 0.48f, paint);
                canvas.drawLine(cx, h * 0.38f, cx, h * 0.70f, paint);
                return;
            }

            if (kind == PROGRESS_THUMB) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                rect.set(w * 0.10f, h * 0.14f, w * 0.90f, h * 0.86f);
                canvas.drawRoundRect(rect, h * 0.18f, h * 0.18f, paint);
                paint.setColor(Color.BLACK);
                rect.set(w * 0.33f, h * 0.32f, w * 0.43f, h * 0.68f);
                canvas.drawRoundRect(rect, h * 0.03f, h * 0.03f, paint);
                rect.set(w * 0.57f, h * 0.32f, w * 0.67f, h * 0.68f);
                canvas.drawRoundRect(rect, h * 0.03f, h * 0.03f, paint);
            }
        }
    }

    private final class SheetActionIconView extends View {
        private final String label;
        private final boolean shareStyle;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SheetActionIconView(Context context, String label, boolean shareStyle) {
            super(context);
            this.label = label;
            this.shareStyle = shareStyle;
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(shareStyle ? BILI_PINK : 0xFFF1F2F5);
            if (shareStyle) {
                canvas.drawCircle(cx, cy, Math.min(w, h) * 0.48f, paint);
            } else {
                canvas.drawRoundRect(0, 0, w, h, dp(12), dp(12), paint);
            }

            paint.setColor(shareStyle ? Color.WHITE : 0xFF252A31);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);

            if ("复制链接".equals(label)) {
                drawLink(canvas, cx, cy);
            } else if ("生成截图".equals(label)) {
                drawScreenshot(canvas, cx, cy);
            } else if ("保存封面".equals(label)) {
                drawImage(canvas, cx, cy);
            } else if ("转发动态".equals(label) || "动态".equals(label)) {
                drawForward(canvas, cx, cy);
            } else if ("不感兴趣".equals(label)) {
                drawCross(canvas, cx, cy);
            } else if ("稍后再看".equals(label)) {
                drawClock(canvas, cx, cy);
            } else if ("缓存".equals(label) || "缓".equals(label)) {
                drawDownload(canvas, cx, cy);
            } else if ("小窗播放".equals(label) || "小窗".equals(label)) {
                drawPip(canvas, cx, cy);
            } else if ("投屏".equals(label)) {
                drawCast(canvas, cx, cy);
            } else if ("朋友圈".equals(label) || "QQ空间".equals(label)) {
                drawOrbit(canvas, cx, cy);
            } else if ("微信".equals(label) || "QQ".equals(label)) {
                drawChat(canvas, cx, cy);
            } else {
                drawSmallText(canvas, label.substring(0, Math.min(1, label.length())), cx, cy);
            }
        }

        private void drawLink(Canvas canvas, float cx, float cy) {
            android.graphics.RectF left = new android.graphics.RectF(cx - dp(17), cy - dp(6), cx + dp(1), cy + dp(8));
            android.graphics.RectF right = new android.graphics.RectF(cx - dp(1), cy - dp(8), cx + dp(17), cy + dp(6));
            canvas.save();
            canvas.rotate(-28, cx, cy);
            canvas.drawRoundRect(left, dp(7), dp(7), paint);
            canvas.drawRoundRect(right, dp(7), dp(7), paint);
            canvas.restore();
        }

        private void drawScreenshot(Canvas canvas, float cx, float cy) {
            canvas.drawRoundRect(cx - dp(15), cy - dp(11), cx + dp(15), cy + dp(11), dp(4), dp(4), paint);
            canvas.drawLine(cx - dp(8), cy - dp(15), cx - dp(14), cy - dp(15), paint);
            canvas.drawLine(cx - dp(15), cy - dp(14), cx - dp(15), cy - dp(8), paint);
            canvas.drawLine(cx + dp(8), cy + dp(15), cx + dp(14), cy + dp(15), paint);
            canvas.drawLine(cx + dp(15), cy + dp(14), cx + dp(15), cy + dp(8), paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, dp(3), paint);
            paint.setStyle(Paint.Style.STROKE);
        }

        private void drawImage(Canvas canvas, float cx, float cy) {
            canvas.drawRoundRect(cx - dp(15), cy - dp(12), cx + dp(15), cy + dp(12), dp(4), dp(4), paint);
            canvas.drawCircle(cx + dp(8), cy - dp(5), dp(3), paint);
            canvas.drawLine(cx - dp(11), cy + dp(8), cx - dp(2), cy - dp(2), paint);
            canvas.drawLine(cx - dp(2), cy - dp(2), cx + dp(5), cy + dp(6), paint);
            canvas.drawLine(cx + dp(5), cy + dp(6), cx + dp(12), cy - dp(1), paint);
        }

        private void drawForward(Canvas canvas, float cx, float cy) {
            canvas.drawArc(new android.graphics.RectF(cx - dp(15), cy - dp(13), cx + dp(13), cy + dp(15)), 205, 230, false, paint);
            canvas.drawLine(cx + dp(10), cy - dp(13), cx + dp(17), cy - dp(13), paint);
            canvas.drawLine(cx + dp(17), cy - dp(13), cx + dp(17), cy - dp(6), paint);
        }

        private void drawCross(Canvas canvas, float cx, float cy) {
            canvas.drawLine(cx - dp(9), cy - dp(9), cx + dp(9), cy + dp(9), paint);
            canvas.drawLine(cx + dp(9), cy - dp(9), cx - dp(9), cy + dp(9), paint);
        }

        private void drawClock(Canvas canvas, float cx, float cy) {
            canvas.drawCircle(cx, cy, dp(14), paint);
            canvas.drawLine(cx, cy, cx, cy - dp(8), paint);
            canvas.drawLine(cx, cy, cx + dp(7), cy + dp(4), paint);
        }

        private void drawDownload(Canvas canvas, float cx, float cy) {
            canvas.drawLine(cx, cy - dp(13), cx, cy + dp(6), paint);
            canvas.drawLine(cx - dp(8), cy - dp(1), cx, cy + dp(7), paint);
            canvas.drawLine(cx + dp(8), cy - dp(1), cx, cy + dp(7), paint);
            canvas.drawLine(cx - dp(13), cy + dp(14), cx + dp(13), cy + dp(14), paint);
        }

        private void drawPip(Canvas canvas, float cx, float cy) {
            canvas.drawRoundRect(cx - dp(16), cy - dp(11), cx + dp(16), cy + dp(11), dp(3), dp(3), paint);
            canvas.drawRoundRect(cx + dp(2), cy, cx + dp(13), cy + dp(8), dp(2), dp(2), paint);
        }

        private void drawCast(Canvas canvas, float cx, float cy) {
            canvas.drawRoundRect(cx - dp(16), cy - dp(12), cx + dp(16), cy + dp(8), dp(3), dp(3), paint);
            canvas.drawArc(new android.graphics.RectF(cx - dp(18), cy + dp(6), cx + dp(2), cy + dp(26)), 270, 90, false, paint);
            canvas.drawArc(new android.graphics.RectF(cx - dp(12), cy + dp(12), cx - dp(2), cy + dp(22)), 270, 90, false, paint);
        }

        private void drawOrbit(Canvas canvas, float cx, float cy) {
            canvas.drawCircle(cx, cy, dp(4), paint);
            canvas.drawOval(new android.graphics.RectF(cx - dp(15), cy - dp(9), cx + dp(15), cy + dp(9)), paint);
            canvas.save();
            canvas.rotate(58, cx, cy);
            canvas.drawOval(new android.graphics.RectF(cx - dp(15), cy - dp(9), cx + dp(15), cy + dp(9)), paint);
            canvas.restore();
        }

        private void drawChat(Canvas canvas, float cx, float cy) {
            android.graphics.RectF left = new android.graphics.RectF(cx - dp(16), cy - dp(10), cx + dp(5), cy + dp(7));
            android.graphics.RectF right = new android.graphics.RectF(cx - dp(3), cy - dp(3), cx + dp(17), cy + dp(13));
            canvas.drawRoundRect(left, dp(8), dp(8), paint);
            canvas.drawRoundRect(right, dp(8), dp(8), paint);
            canvas.drawLine(cx - dp(7), cy + dp(7), cx - dp(11), cy + dp(13), paint);
        }

        private void drawSmallText(Canvas canvas, String value, float cx, float cy) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(19));
            canvas.drawText(value, cx, cy + dp(7), paint);
            paint.setStyle(Paint.Style.STROKE);
        }
    }

    private final class RailActionButton extends LinearLayout {
        private final FrameLayout iconFrame;
        private final ImageView iconView;
        private final TextView textIconView;
        private final TextView countView;
        private final Paint holdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean rasterIcon;
        private LottieAnimationView lottieAnimationView;
        private boolean usesStateIconResources;
        private int inactiveIconRes;
        private int activeIconRes;
        private float holdProgress;

        RailActionButton(Context context, int iconRes, String iconText) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setClickable(true);
            setFocusable(true);
            setWillNotDraw(false);
            setClipChildren(false);
            setClipToPadding(false);

            rasterIcon = iconRes != 0;
            iconFrame = new FrameLayout(context);
            iconFrame.setClipChildren(false);
            iconFrame.setClipToPadding(false);
            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iconView.setColorFilter(Color.WHITE);
            textIconView = text(iconText, 24, 0xFF4A3A20, Typeface.BOLD);
            textIconView.setGravity(Gravity.CENTER);
            textIconView.setIncludeFontPadding(false);
            textIconView.setShadowLayer(6, 0, 2, 0xAA000000);
            textIconView.setBackground(rounded(0xEEFFFFFF, dp(17)));

            if (iconRes != 0) {
                inactiveIconRes = iconRes;
                iconView.setImageResource(iconRes);
                iconFrame.addView(iconView, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
            } else {
                iconFrame.addView(textIconView, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
            }
            addView(iconFrame, new LinearLayout.LayoutParams(dp(38), dp(38)));

            countView = text("", 13, Color.WHITE, Typeface.BOLD);
            countView.setGravity(Gravity.CENTER);
            countView.setShadowLayer(6, 0, 2, 0xAA000000);
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, dp(24));
            countParams.topMargin = dp(1);
            addView(countView, countParams);
        }

        void setCount(String value) {
            countView.setText(value);
        }

        void setStateIcons(int inactiveRes, int activeRes) {
            if (!rasterIcon || inactiveRes == 0) return;
            usesStateIconResources = true;
            inactiveIconRes = inactiveRes;
            activeIconRes = activeRes;
            iconView.clearColorFilter();
            iconView.setImageResource(inactiveIconRes);
        }

        void setIconColor(int color) {
            if (usesStateIconResources) {
                iconView.clearColorFilter();
                int nextIcon = color == BILI_PINK && activeIconRes != 0 ? activeIconRes : inactiveIconRes;
                if (nextIcon != 0) iconView.setImageResource(nextIcon);
            } else {
                iconView.setColorFilter(color);
            }
            textIconView.setTextColor(color == BILI_PINK ? Color.WHITE : 0xFF4A3A20);
            textIconView.setBackground(rounded(color == BILI_PINK ? BILI_PINK : 0xEEFFFFFF, dp(18)));
            countView.setTextColor(color == BILI_PINK ? BILI_PINK : Color.WHITE);
            if (color != BILI_PINK) stopLottieAnimation();
        }

        void setResponsiveSizes(int iconSize, int countHeight, int countTopMargin) {
            ViewGroup.LayoutParams frameParams = iconFrame.getLayoutParams();
            frameParams.width = iconSize;
            frameParams.height = iconSize;
            iconFrame.setLayoutParams(frameParams);

            View icon = rasterIcon ? iconView : textIconView;
            icon.setLayoutParams(new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
            if (icon == textIconView) {
                textIconView.setBackground(rounded(0xEEFFFFFF, Math.max(1, iconSize / 2)));
            }
            if (lottieAnimationView != null) {
                lottieAnimationView.setLayoutParams(new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
            }
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, countHeight);
            countParams.topMargin = countTopMargin;
            countView.setLayoutParams(countParams);
        }

        void attachLottieAnimation(int rawResId) {
            if (lottieAnimationView != null) return;
            lottieAnimationView = new LottieAnimationView(getContext());
            lottieAnimationView.setAnimation(rawResId);
            lottieAnimationView.setRepeatCount(0);
            lottieAnimationView.setVisibility(INVISIBLE);
            lottieAnimationView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            lottieAnimationView.addAnimatorListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    stopLottieAnimation();
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    stopLottieAnimation();
                }
            });
            iconFrame.addView(lottieAnimationView, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        }

        void playLottieAnimation() {
            if (lottieAnimationView == null) {
                animate().cancel();
                setScaleX(1f);
                setScaleY(1f);
                animate()
                        .scaleX(1.18f)
                        .scaleY(1.18f)
                        .setDuration(110)
                        .withEndAction(() -> animate().scaleX(1f).scaleY(1f).setDuration(160).start())
                        .start();
                return;
            }
            stopLottieAnimation();
            View icon = rasterIcon ? iconView : textIconView;
            icon.setVisibility(INVISIBLE);
            lottieAnimationView.setVisibility(VISIBLE);
            lottieAnimationView.setProgress(0f);
            lottieAnimationView.playAnimation();
        }

        private void stopLottieAnimation() {
            if (lottieAnimationView == null) return;
            lottieAnimationView.removeAllAnimatorListeners();
            lottieAnimationView.cancelAnimation();
            lottieAnimationView.setVisibility(INVISIBLE);
            lottieAnimationView.addAnimatorListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    stopLottieAnimation();
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    stopLottieAnimation();
                }
            });
            View icon = rasterIcon ? iconView : textIconView;
            icon.setVisibility(VISIBLE);
        }

        void setHoldProgress(float progress) {
            holdProgress = Math.max(0f, Math.min(1f, progress));
            invalidate();
        }

        float getHoldProgress() {
            return holdProgress;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (holdProgress <= 0f) return;
            holdPaint.setStyle(Paint.Style.STROKE);
            holdPaint.setStrokeWidth(dp(3));
            holdPaint.setStrokeCap(Paint.Cap.ROUND);
            holdPaint.setColor(BILI_PINK);
            android.graphics.RectF arc = new android.graphics.RectF(
                    getWidth() / 2f - dp(24),
                    dp(2),
                    getWidth() / 2f + dp(24),
                    dp(50)
            );
            canvas.drawArc(arc, -90, 360f * holdProgress, false, holdPaint);
        }
    }

    private final class CoinMascotView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CoinMascotView(Context context) {
            super(context);
            setWillNotDraw(false);
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w * 0.24f;
            float cy = h * 0.52f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFD8F2FF);
            canvas.drawCircle(cx + dp(8), cy - dp(4), dp(44), paint);

            paint.setColor(0xFF2DBAFF);
            canvas.drawCircle(cx, cy - dp(18), dp(25), paint);
            canvas.drawCircle(cx - dp(22), cy - dp(4), dp(20), paint);
            canvas.drawCircle(cx + dp(23), cy - dp(2), dp(20), paint);
            canvas.drawCircle(cx - dp(5), cy + dp(10), dp(18), paint);

            paint.setColor(0xFFFFE4CA);
            canvas.drawCircle(cx, cy - dp(5), dp(20), paint);

            paint.setColor(0xFF213344);
            canvas.drawCircle(cx - dp(7), cy - dp(10), dp(2), paint);
            canvas.drawCircle(cx + dp(7), cy - dp(10), dp(2), paint);
            paint.setStrokeWidth(dp(2));
            canvas.drawLine(cx - dp(6), cy + dp(4), cx + dp(6), cy + dp(4), paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(BILI_PINK);
            canvas.drawRoundRect(cx - dp(17), cy + dp(17), cx + dp(17), cy + dp(45), dp(10), dp(10), paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(cx - dp(8), cy + dp(26), dp(3), paint);
            canvas.drawCircle(cx + dp(8), cy + dp(26), dp(3), paint);

            paint.setColor(0xFFFFCF45);
            canvas.drawCircle(cx + dp(46), cy - dp(28), dp(16), paint);
            paint.setColor(0xFF8A5A00);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(15));
            canvas.drawText("\u5e01", cx + dp(46), cy - dp(22), paint);

            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(0xFF25384B);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(17));
            canvas.drawText("\u539f\u521b\u84dd\u53d1\u6295\u5e01\u5c0f\u4eba", w * 0.44f, h * 0.40f, paint);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(dp(14));
            paint.setColor(0xFF68717D);
            canvas.drawText("\u70b9\u51fb\u5361\u7247\u6216\u5c0f\u4eba\u6295\u5e01", w * 0.44f, h * 0.66f, paint);
        }
    }

    private final class PeopleIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PeopleIconView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, dp(2)));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xE6FFFFFF);
            canvas.drawCircle(w * 0.40f, h * 0.34f, w * 0.13f, paint);
            canvas.drawCircle(w * 0.63f, h * 0.34f, w * 0.13f, paint);
            canvas.drawArc(new android.graphics.RectF(w * 0.18f, h * 0.50f, w * 0.60f, h * 1.02f), 205, 130, false, paint);
            canvas.drawArc(new android.graphics.RectF(w * 0.43f, h * 0.50f, w * 0.85f, h * 1.02f), 205, 130, false, paint);
        }
    }

    private final class DanmakuOverlayView extends View {
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selfBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<DanmakuSprite> sprites = new ArrayList<>();
        private DanmakuSprite pressedSprite;

        DanmakuOverlayView(Context context) {
            super(context);
            setWillNotDraw(false);
            setClickable(true);
            strokePaint.setColor(Color.BLACK);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(3));
            strokePaint.setTypeface(Typeface.DEFAULT_BOLD);
            fillPaint.setColor(Color.WHITE);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setTypeface(Typeface.DEFAULT_BOLD);
            selfBgPaint.setStyle(Paint.Style.FILL);
        }

        void clear() {
            sprites.clear();
            hideDanmakuActionBox();
            invalidate();
        }

        void addLine(String text, int row) {
            addLine(text, row, Color.WHITE, currentDanmakuDurationMs(7200L), 1);
        }

        void addLine(String text, int row, int color, long durationMs) {
            addLine(text, row, color, durationMs, 1);
        }

        void addLine(String text, int row, int color, long durationMs, int mode) {
            addLine(text, row, color, durationMs, mode, 80L);
        }

        void addLine(String text, int row, int color, long durationMs, int mode, long startOffsetMs) {
            addLine(text, row, color, durationMs, mode, startOffsetMs, false);
        }

        void addLine(String text, int row, int color, long durationMs, int mode, long startOffsetMs, boolean self) {
            addLine(text, row, color, durationMs, mode, startOffsetMs, self, 0);
        }

        void addLine(String text, int row, int color, long durationMs, int mode, long startOffsetMs, boolean self, int textSp) {
            long videoTimeMs = player == null ? -1L : Math.max(0L, player.getCurrentPosition() - Math.max(0L, startOffsetMs));
            addLineAtVideoTime(text, row, color, durationMs, mode, startOffsetMs, self, textSp, videoTimeMs);
        }

        void addLineAtVideoTime(String text, int row, int color, long durationMs, int mode, long startOffsetMs, boolean self, int textSp, long videoTimeMs) {
            if (text == null || text.isEmpty()) return;
            int finalColor = color == Color.TRANSPARENT ? Color.WHITE : color;
            long safeOffsetMs = Math.max(0L, Math.min(durationMs - 400L, startOffsetMs));
            sprites.add(new DanmakuSprite(text, row, SystemClock.uptimeMillis() - safeOffsetMs, finalColor, durationMs, mode, self, textSp, videoTimeMs));
            postInvalidateOnAnimation();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!danmakuVisible || sprites.isEmpty()) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                pressedSprite = hitTest(event.getX(), event.getY());
                return pressedSprite != null;
            }
            if (action == MotionEvent.ACTION_UP) {
                DanmakuSprite hit = hitTest(event.getX(), event.getY());
                if (pressedSprite != null && pressedSprite == hit) {
                    selectSprite(pressedSprite);
                    pressedSprite = null;
                    return true;
                }
                pressedSprite = null;
                return false;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                pressedSprite = null;
                return false;
            }
            return pressedSprite != null;
        }

        private DanmakuSprite hitTest(float x, float y) {
            for (int i = sprites.size() - 1; i >= 0; i--) {
                DanmakuSprite sprite = sprites.get(i);
                if (sprite.bounds.contains(x, y)) return sprite;
            }
            return null;
        }

        private void selectSprite(DanmakuSprite sprite) {
            if (sprite == null || sprite.bounds.isEmpty()) return;
            if (selectedDanmakuSprite != null && selectedDanmakuSprite != sprite) {
                selectedDanmakuSprite.selected = false;
            }
            long now = SystemClock.uptimeMillis();
            sprite.selected = true;
            sprite.selectedUntilWallMs = now + 2000L;
            sprite.frozenLeft = sprite.bounds.left;
            sprite.frozenBaseline = sprite.lastBaseline;
            showDanmakuActionBox(sprite, sprite.bounds.centerX(), sprite.bounds.bottom);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (sprites.isEmpty()) return;

            long now = SystemClock.uptimeMillis();
            long videoPositionMs = player == null ? -1L : Math.max(0L, player.getCurrentPosition());
            strokePaint.setAlpha(Math.round(165 * danmakuOpacityPercent / 100f));
            fillPaint.setAlpha(Math.round(245 * danmakuOpacityPercent / 100f));

            int save = canvas.save();
            int top = danmakuTopPx();
            int maxBottom = danmakuMaxBottomPx();
            int bottom = danmakuDisplayBottomPx();
            int rowHeight = danmakuRowHeightPx();
            int clipRight = Math.max(0, getWidth());
            canvas.clipRect(0, top, clipRight, Math.min(maxBottom, bottom));
            Iterator<DanmakuSprite> iterator = sprites.iterator();
            while (iterator.hasNext()) {
                DanmakuSprite sprite = iterator.next();
                if (sprite.selected && now >= sprite.selectedUntilWallMs) {
                    if (selectedDanmakuSprite == sprite) hideDanmakuActionBox();
                    iterator.remove();
                    continue;
                }
                float progress = sprite.videoTimeMs >= 0L && videoPositionMs >= 0L
                        ? (videoPositionMs - sprite.videoTimeMs) / (float) sprite.durationMs
                        : (now - sprite.startedAtMs) / (float) sprite.durationMs;
                if (progress < 0f) {
                    continue;
                }
                if (progress >= 1f) {
                    iterator.remove();
                    continue;
                }
                float textSize = (sprite.textSp > 0 ? sprite.textSp : danmakuTextSp) * getResources().getDisplayMetrics().scaledDensity;
                strokePaint.setTextSize(textSize);
                fillPaint.setTextSize(textSize);
                float textWidth = fillPaint.measureText(sprite.text);
                float x;
                float y;
                if (sprite.selected) {
                    x = sprite.frozenLeft;
                    y = sprite.frozenBaseline;
                } else if (sprite.mode == 4 || sprite.mode == 5) {
                    x = Math.max(dp(8), Math.min(clipRight - textWidth - dp(8), (clipRight - textWidth) / 2f));
                    if (sprite.mode == 5) {
                        y = danmakuBaselineForRow(top, sprite.row, rowHeight);
                    } else {
                        int bottomRowTop = Math.max(top, Math.min(maxBottom, bottom) - rowHeight * (sprite.row + 1));
                        y = danmakuBaselineForRow(bottomRowTop, 0, rowHeight);
                    }
                } else {
                    float startX = getWidth() + dp(8);
                    float endX = -textWidth - dp(8);
                    x = startX + (endX - startX) * progress;
                    y = danmakuBaselineForRow(top, sprite.row, rowHeight);
                }
                Paint.FontMetrics metrics = fillPaint.getFontMetrics();
                sprite.lastBaseline = y;
                sprite.bounds.set(
                        x - dp(8),
                        y + metrics.ascent - dp(5),
                        x + textWidth + dp(8),
                        y + metrics.descent + dp(5)
                );
                fillPaint.setColor(sprite.color);
                if (sprite.self) {
                    selfBgPaint.setColor(0x44FB7299);
                    canvas.drawRoundRect(
                            x - dp(8),
                            y + metrics.ascent - dp(4),
                            x + textWidth + dp(8),
                            y + metrics.descent + dp(4),
                            dp(10),
                            dp(10),
                            selfBgPaint
                    );
                }
                canvas.drawText(sprite.text, x, y, strokePaint);
                canvas.drawText(sprite.text, x, y, fillPaint);
            }
            canvas.restoreToCount(save);

            if (!sprites.isEmpty()) postInvalidateOnAnimation();
        }

        private float danmakuBaselineForRow(int top, int row, int rowHeight) {
            Paint.FontMetrics metrics = fillPaint.getFontMetrics();
            float rowTop = top + row * rowHeight;
            float textHeight = metrics.descent - metrics.ascent;
            return rowTop + (rowHeight - textHeight) / 2f - metrics.ascent;
        }
    }

    private static final class DanmakuSprite {
        final String text;
        final int row;
        final long startedAtMs;
        final int color;
        final long durationMs;
        final int mode;
        final boolean self;
        final int textSp;
        final long videoTimeMs;
        final android.graphics.RectF bounds = new android.graphics.RectF();
        boolean selected;
        long selectedUntilWallMs;
        float frozenLeft;
        float frozenBaseline;
        float lastBaseline;

        DanmakuSprite(String text, int row, long startedAtMs, int color, long durationMs, int mode, boolean self, int textSp, long videoTimeMs) {
            this.text = text;
            this.row = row;
            this.startedAtMs = startedAtMs;
            this.color = color;
            this.durationMs = Math.max(1200L, durationMs);
            this.mode = mode;
            this.self = self;
            this.textSp = textSp;
            this.videoTimeMs = videoTimeMs;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
