package com.example.player;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Rational;
import android.view.View;
import android.widget.ImageView;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.inappmessaging.display.FirebaseInAppMessagingDisplay;
import com.google.firebase.inappmessaging.display.internal.FiamImageLoader;
import com.google.firebase.inappmessaging.display.internal.GlideErrorListener;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;

public class IptvAudioHelper {
    private static final String TAG = "IptvAudioHelper";
    public static final String ACTION_PIP_PLAY_PAUSE = "com.hypnotix.iptv.PIP_PLAY_PAUSE";

    public static volatile ExoPlayer activePlayer = null;
    private static volatile boolean lifecycleRegistered = false;
    private static WeakReference<Activity> activeActivityRef = null;
    private static BroadcastReceiver pipReceiver = null;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static Bitmap cachedPlayBitmap = null;
    private static Bitmap cachedPauseBitmap = null;

    public static void initFiamSafeHooks(Context context) {
        ensureAppHooks();
        installSafeFiamImageLoader();
        try {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    installSafeFiamImageLoader();
                }
            }, 500);
        } catch (Throwable ignored) {}
    }

    public static void installSafeFiamImageLoader() {
        try {
            FirebaseInAppMessagingDisplay display = FirebaseInAppMessagingDisplay.getInstance();
            if (display == null) return;
            Field imageLoaderField = FirebaseInAppMessagingDisplay.class.getDeclaredField("imageLoader");
            imageLoaderField.setAccessible(true);
            Object currentLoader = imageLoaderField.get(display);
            if (currentLoader instanceof FiamImageLoader && !(currentLoader instanceof SafeFiamImageLoader)) {
                Field rmField = FiamImageLoader.class.getDeclaredField("requestManager");
                rmField.setAccessible(true);
                RequestManager rm = (RequestManager) rmField.get(currentLoader);
                if (rm != null) {
                    imageLoaderField.set(display, new SafeFiamImageLoader(rm));
                    Log.d(TAG, "Installed SafeFiamImageLoader on FirebaseInAppMessagingDisplay");
                }
            }
        } catch (Throwable t) {
            Log.d(TAG, "installSafeFiamImageLoader skipped: " + t.getMessage());
        }
    }

    public static class SafeFiamImageLoader extends FiamImageLoader {
        private final RequestManager glideRequestManager;

        public SafeFiamImageLoader(RequestManager requestManager) {
            super(requestManager);
            this.glideRequestManager = requestManager;
        }

        @Override
        public FiamImageRequestCreator load(String imageUrl) {
            String safeUrl = (imageUrl != null) ? imageUrl.trim() : "";
            GlideUrl glideUrl = new GlideUrl(
                safeUrl,
                new LazyHeaders.Builder().addHeader("Accept", "image/*").build()
            );
            RequestBuilder<Drawable> rb = glideRequestManager
                .load(glideUrl)
                .format(DecodeFormat.PREFER_ARGB_8888);
            return new SafeFiamImageRequestCreator(this, rb);
        }
    }

    public static class SafeFiamImageRequestCreator extends FiamImageLoader.FiamImageRequestCreator {
        private final RequestBuilder<Drawable> requestBuilder;

        public SafeFiamImageRequestCreator(FiamImageLoader loader, RequestBuilder<Drawable> rb) {
            loader.super(rb);
            this.requestBuilder = rb;
        }

        @Override
        public FiamImageLoader.FiamImageRequestCreator placeholder(int placeholderResId) {
            requestBuilder.placeholder(placeholderResId);
            return this;
        }

        @Override
        public FiamImageLoader.FiamImageRequestCreator addErrorListener(GlideErrorListener glideErrorListener) {
            return this;
        }

        @Override
        public FiamImageLoader.FiamImageRequestCreator tag(Class c) {
            super.tag(c);
            return this;
        }

        @Override
        public void into(final ImageView imageView, final FiamImageLoader.Callback callback) {
            if (callback == null) return;
            FiamImageLoader.Callback safeCallback = new FiamImageLoader.Callback() {
                @Override
                public void onResourceReady(Drawable resource, Transition<? super Drawable> transition) {
                    if (imageView != null) {
                        imageView.setVisibility(View.VISIBLE);
                        imageView.setImageDrawable(resource);
                    }
                    callback.onSuccess();
                }

                @Override
                public void onLoadCleared(Drawable placeholder) {
                    if (imageView != null && placeholder != null) {
                        imageView.setImageDrawable(placeholder);
                    }
                    callback.onSuccess();
                }

                @Override
                public void onLoadFailed(Drawable errorDrawable) {
                    if (imageView != null) {
                        if (errorDrawable != null) {
                            imageView.setImageDrawable(errorDrawable);
                        } else {
                            imageView.setVisibility(View.GONE);
                        }
                    }
                    callback.onSuccess();
                }

                @Override
                public void onSuccess() {
                    callback.onSuccess();
                }

                @Override
                public void onError(Exception e) {
                    callback.onSuccess();
                }
            };
            super.into(imageView, safeCallback);
        }
    }

    /**
     * Configures the track selector during createPlayer().
     * Sets exceedRendererCapabilitiesIfNecessary = false so unsupported audio
     * formats like audio/mpeg-L2 are never selected, preventing MediaCodecAudioRenderer [-49999] crash.
     */
    public static void initSelector(DefaultTrackSelector selector) {
        if (selector == null) return;
        try {
            DefaultTrackSelector.Parameters params = selector.buildUponParameters()
                .setMaxVideoSize(1280, 720)
                .setMaxVideoBitrate(1500000)
                .setExceedRendererCapabilitiesIfNecessary(false)
                .setExceedAudioConstraintsIfNecessary(false)
                .build();
            selector.setParameters(params);
            Log.d(TAG, "Initialized track selector with exceedRendererCapabilities=false");
        } catch (Throwable t) {
            Log.w(TAG, "Failed in initSelector: " + t.getMessage());
        }
        ensureAppHooks();
    }

    /**
     * Fallback recovery handler called when a codec error (e.g. 4001) occurs.
     * Steps down to 360p SD and disables failing audio track so video continues smoothly.
     */
    public static void onCodecFallback(Object manager, ExoPlayer player, boolean lockDataSaver) {
        if (player == null) return;
        try {
            TrackSelectionParameters params = player.getTrackSelectionParameters()
                .buildUpon()
                .clearOverridesOfType(2) // 2 = C.TRACK_TYPE_VIDEO
                .setMaxVideoSize(640, 360)
                .setMaxVideoBitrate(420000)
                .setForceLowestBitrate(true)
                .setTrackTypeDisabled(1, true) // 1 = C.TRACK_TYPE_AUDIO (disable failing audio decoder)
                .build();
            player.setTrackSelectionParameters(params);
            Log.w(TAG, "Applied fallback: SD 360p + audio track disabled to recover from decoder error");
        } catch (Throwable t) {
            Log.e(TAG, "Failed in onCodecFallback: " + t.getMessage());
        }
    }

    /**
     * Re-enables audio track when playing a new channel and sets up native PiP actions.
     */
    public static void onChannelChange(final ExoPlayer player) {
        if (player == null) return;
        activePlayer = player;
        try {
            TrackSelectionParameters params = player.getTrackSelectionParameters()
                .buildUpon()
                .setTrackTypeDisabled(1, false) // 1 = C.TRACK_TYPE_AUDIO
                .build();
            player.setTrackSelectionParameters(params);
        } catch (Throwable ignored) {}

        ensureAppHooks();

        try {
            player.addListener(new Player.Listener() {
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    updatePipUi(isPlaying);
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    boolean playing = player.isPlaying();
                    updatePipUi(playing);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "Failed to attach Player.Listener: " + t.getMessage());
        }

        schedulePipParamsRefresh();
    }

    private static synchronized void ensureAppHooks() {
        if (lifecycleRegistered) return;
        lifecycleRegistered = true;
        try {
            Application app = (Application) Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null);
            if (app != null) {
                registerPipReceiver(app);
                app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                        activeActivityRef = new WeakReference<Activity>(activity);
                        installSafeFiamImageLoader();
                        registerPipReceiver(activity.getApplicationContext());
                        attachPipLayoutListener(activity);
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {
                        activeActivityRef = new WeakReference<Activity>(activity);
                        installSafeFiamImageLoader();
                    }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        activeActivityRef = new WeakReference<Activity>(activity);
                        installSafeFiamImageLoader();
                        attachPipLayoutListener(activity);
                        schedulePipParamsRefresh();
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
                        activeActivityRef = new WeakReference<Activity>(activity);
                        schedulePipParamsRefresh();
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {}
                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                    @Override
                    public void onActivityDestroyed(Activity activity) {}
                });
                Log.d(TAG, "Registered ActivityLifecycleCallbacks for native PiP actions and FIAM");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not register ActivityLifecycleCallbacks: " + t.getMessage());
        }
    }

    private static void attachPipLayoutListener(final Activity activity) {
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    View decor = activity.getWindow().getDecorView();
                    if (decor == null) return;
                    Object tag = decor.getTag(0x7f0a0001);
                    if (tag != null) return;
                    decor.setTag(0x7f0a0001, Boolean.TRUE);

                    decor.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                        private boolean lastPipState = false;

                        @Override
                        public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                                   int oldLeft, int oldTop, int oldRight, int oldBottom) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                boolean inPip = activity.isInPictureInPictureMode();
                                if (inPip != lastPipState) {
                                    lastPipState = inPip;
                                    if (inPip) {
                                        onEnterPip(activity);
                                    }
                                }
                            }
                        }
                    });
                } catch (Throwable t) {
                    Log.w(TAG, "Failed attaching layout listener: " + t.getMessage());
                }
            }
        });
    }

    private static void onEnterPip(final Activity activity) {
        registerPipReceiver(activity.getApplicationContext());
        schedulePipParamsRefresh();
    }

    private static void schedulePipParamsRefresh() {
        final Runnable refreshTask = new Runnable() {
            @Override
            public void run() {
                Activity activity = activeActivityRef != null ? activeActivityRef.get() : null;
                if (activity != null && !activity.isFinishing()) {
                    boolean isPlaying = activePlayer != null && activePlayer.isPlaying();
                    updatePipActions(activity, isPlaying);
                }
            }
        };
        mainHandler.post(refreshTask);
        mainHandler.postDelayed(refreshTask, 60);
        mainHandler.postDelayed(refreshTask, 180);
        mainHandler.postDelayed(refreshTask, 450);
    }

    public static void togglePlayPause() {
        try {
            if (activePlayer != null) {
                boolean wasPlaying = activePlayer.isPlaying();
                if (wasPlaying) {
                    activePlayer.pause();
                } else {
                    activePlayer.play();
                }
                updatePipUi(!wasPlaying);
                return;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error toggling play/pause: " + t.getMessage());
        }
        boolean isPlaying = activePlayer != null && activePlayer.isPlaying();
        updatePipUi(isPlaying);
    }

    public static void updatePipUi(final boolean isPlaying) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Activity activity = activeActivityRef != null ? activeActivityRef.get() : null;
                if (activity != null && !activity.isFinishing()) {
                    updatePipActions(activity, isPlaying);
                }
            }
        });
    }

    private static synchronized void registerPipReceiver(final Context context) {
        if (context == null || pipReceiver != null) return;
        Context appCtx = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        pipReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent != null && ACTION_PIP_PLAY_PAUSE.equals(intent.getAction())) {
                    togglePlayPause();
                }
            }
        };
        try {
            IntentFilter filter = new IntentFilter(ACTION_PIP_PLAY_PAUSE);
            if (Build.VERSION.SDK_INT >= 33) {
                appCtx.registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                appCtx.registerReceiver(pipReceiver, filter);
            }
            Log.d(TAG, "Registered PiP Play/Pause broadcast receiver");
        } catch (Throwable t) {
            Log.w(TAG, "Failed registering PiP receiver: " + t.getMessage());
            pipReceiver = null;
        }
    }

    private static Bitmap getPlayIconBitmap() {
        if (cachedPlayBitmap != null) return cachedPlayBitmap;
        int size = 72; // 24dp @ 3x density
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setPathEffect(new CornerPathEffect(4.5f));
        Path path = new Path();
        path.moveTo(23f, 15f);
        path.lineTo(23f, 57f);
        path.lineTo(56f, 36f);
        path.close();
        canvas.drawPath(path, paint);
        cachedPlayBitmap = bmp;
        return bmp;
    }

    private static Bitmap getPauseIconBitmap() {
        if (cachedPauseBitmap != null) return cachedPauseBitmap;
        int size = 72; // 24dp @ 3x density
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        RectF leftBar = new RectF(18f, 15f, 30f, 57f);
        RectF rightBar = new RectF(42f, 15f, 54f, 57f);
        canvas.drawRoundRect(leftBar, 3.5f, 3.5f, paint);
        canvas.drawRoundRect(rightBar, 3.5f, 3.5f, paint);
        cachedPauseBitmap = bmp;
        return bmp;
    }

    private static void updatePipActions(Activity activity, boolean isPlaying) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
            builder.setAspectRatio(new Rational(16, 9));

            ArrayList<RemoteAction> actions = new ArrayList<RemoteAction>();
            Intent playPauseIntent = new Intent(ACTION_PIP_PLAY_PAUSE).setPackage(activity.getPackageName());
            int reqCode = isPlaying ? 4004 : 4005;
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getBroadcast(activity, reqCode, playPauseIntent, flags);
            Bitmap iconBmp = isPlaying ? getPauseIconBitmap() : getPlayIconBitmap();
            Icon icon = Icon.createWithBitmap(iconBmp);
            CharSequence title = isPlaying ? "Pause" : "Play";
            RemoteAction action = new RemoteAction(icon, title, title, pi);
            actions.add(action);
            builder.setActions(actions);

            if (Build.VERSION.SDK_INT >= 31) {
                boolean canEnter = activePlayer != null;
                builder.setAutoEnterEnabled(canEnter);
                builder.setSeamlessResizeEnabled(canEnter);
            }

            activity.setPictureInPictureParams(builder.build());
        } catch (Throwable t) {
            Log.w(TAG, "Failed updating PiP params: " + t.getMessage());
        }
    }
}
