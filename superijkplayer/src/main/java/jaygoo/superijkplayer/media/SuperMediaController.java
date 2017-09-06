package jaygoo.superijkplayer.media;

/**
 * ================================================
 * 作    者：JayGoo
 * 版    本：
 * 创建日期：2017/9/5
 * 描    述:
 * ================================================
 */
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;


import java.util.Formatter;
import java.util.Locale;

import jaygoo.superijkplayer.R;


public class SuperMediaController extends FrameLayout{

    private MediaPlayerControl mPlayer;
    private View mRoot;
    private SeekBar mProgress;
    private TextView mCurrentTime;
    private boolean mShowing;
    private boolean mDragging;
    private static final int sDefaultTimeout = 3000;
    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final float MIN_MOVE_DISTANCE = 40;//最小滑动距离
    StringBuilder mFormatBuilder;
    Formatter mFormatter;
    private View mPauseButton;
    private TextView mPlaySpeedTv;
    private CharSequence mPlayDescription;
    private CharSequence mPauseDescription;
    private GestureDetector mGestureDetector;
    private boolean isGestureDetectorEnable = true;
    private int mMediaControllerLayoutId = R.layout.default_media_controller;

    public SuperMediaController(Context context, AttributeSet attrs) {
        super(context, attrs);
        initAttrs(attrs);
        makeControllerView();
    }


    public SuperMediaController(Context context) {
        super(context);
        makeControllerView();
    }


    private void initAttrs(AttributeSet attrs) {
        TypedArray t = getContext().obtainStyledAttributes(attrs, R.styleable.SuperMediaController);
        if (t != null){
            mMediaControllerLayoutId = t.getResourceId(R.styleable.SuperMediaController_controllerLayoutId,
                    R.layout.default_media_controller);
            t.recycle();
        }
    }

    private View makeControllerView() {
        mRoot = LayoutInflater.from(getContext()).inflate(mMediaControllerLayoutId, null);
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        addView(mRoot, params);
        initControllerView(mRoot);
        return mRoot;
    }

    private void initControllerView(View v) {
        mPlayDescription = "播放";
        mPauseDescription = "暂停";
        mPauseButton =  v.findViewById(R.id.controller_iv_pause);
        if (mPauseButton != null) {
            mPauseButton.requestFocus();
            mPauseButton.setOnClickListener(mPauseListener);
        }

        mProgress = (SeekBar) v.findViewById(R.id.controller_seekBar_progress);
        if (mProgress != null) {
            mProgress.setOnSeekBarChangeListener(mSeekListener);
            mProgress.setMax(1000);
        }

        mCurrentTime = (TextView) v.findViewById(R.id.controller_tv_time);
        mFormatBuilder = new StringBuilder();
        mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
        mPlaySpeedTv = (TextView) v.findViewById(R.id.controller_tv_speed);
        mPlaySpeedTv.setOnClickListener(mSetSpeedListener);
        mGestureDetector = new GestureDetector(getContext(),onGestureListener);
    }


    private void disableUnsupportedButtons() {
        try {
            if (mPauseButton != null && mPlayer != null && !mPlayer.canPause()) {
                mPauseButton.setEnabled(false);
            }
            if (mProgress != null && mPlayer != null && !mPlayer.canSeekBackward() && !mPlayer.canSeekForward()) {
                mProgress.setEnabled(false);
            }
        } catch (IncompatibleClassChangeError ex) {
        }
    }



    protected void showControlLayout() {
        setVisibility(View.VISIBLE);
    }

    protected void hideControlLayout() {
        setVisibility(View.GONE);
    }



    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int pos;
            switch (msg.what) {
                case FADE_OUT:
                    hide();
                    break;
                case SHOW_PROGRESS:
                    pos = setProgress();
                    if (!mDragging && mShowing && mPlayer.isPlaying()) {
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
            }
        }
    };

    private String stringForTime(long timeMs) {
        long totalSeconds = timeMs / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours   = totalSeconds / 3600;

        mFormatBuilder.setLength(0);
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    private String stringForTime(long startTimeMs, long endTimeMs) {
        return stringForTime(startTimeMs) + " / " + stringForTime(endTimeMs);
    }

    private int setProgress() {
        if (mPlayer == null || mDragging) {
            return 0;
        }
        int position = mPlayer.getCurrentPosition();
        int duration = mPlayer.getDuration();
        if (mProgress != null) {
            if (duration > 0) {
                // use long to avoid overflow
                long pos = 1000L * position / duration;
                mProgress.setProgress( (int) pos);
            }
            int percent = mPlayer.getBufferPercentage();
            mProgress.setSecondaryProgress(percent * 10);
        }

        if (mCurrentTime != null)
            mCurrentTime.setText(stringForTime(position , duration));

        return position;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                show(0); // show until hide is called
                break;
            case MotionEvent.ACTION_UP:
                show(sDefaultTimeout); // start timeout
                break;
            case MotionEvent.ACTION_CANCEL:
                hide();
                break;
            default:
                break;
        }
        if (mPlayer != null && mPlayer.isPlaying() && isGestureDetectorEnable) {
            mGestureDetector.onTouchEvent(event);
        }
        return true;
    }

    private GestureDetector.OnGestureListener onGestureListener =
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                                       float velocityY) {
                    float beginX = e1.getX();
                    float endX = e2.getX();
                    float beginY = e1.getY();
                    float endY = e2.getY();
                    float moveDistanceX = endX - beginX;
                    float moveDistanceY = endY - beginY;

                    if(-moveDistanceX > MIN_MOVE_DISTANCE && checkIsHorizontalDirection(moveDistanceX, moveDistanceY)){
                        //左滑
                        Log.i("fuck", "onFling: 左");
                    }else if(moveDistanceX > MIN_MOVE_DISTANCE && checkIsHorizontalDirection(moveDistanceX, moveDistanceY)){
                        //右滑
                        Log.i("fuck", "onFling: 右");
                    }else if(-moveDistanceY > MIN_MOVE_DISTANCE && !checkIsHorizontalDirection(moveDistanceX, moveDistanceY)){
                        //上滑
                        Log.i("fuck", "onFling: 上");
                    }else if(moveDistanceY > MIN_MOVE_DISTANCE && !checkIsHorizontalDirection(moveDistanceX, moveDistanceY)){
                        //下滑
                        Log.i("fuck", "onFling: 下");
                    }
                    return true;
                }


                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    Log.i("fuck", "onDoubleTapEvent: 双击");
                    return true;
                }
            };

    /**
     *
     * @param distanceX
     * @param distanceY
     * @return
     * true is considered to be moving horizontally
     * false is considered to be moving vertically
     */
    private boolean checkIsHorizontalDirection(float distanceX, float distanceY){
        if (Math.abs(distanceX) >= Math.abs(distanceY)){
            return true;
        }
        return false;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        show(sDefaultTimeout);
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        final boolean uniqueDown = event.getRepeatCount() == 0
                && event.getAction() == KeyEvent.ACTION_DOWN;
        if (keyCode ==  KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_SPACE) {
            if (uniqueDown) {
                doPauseResume();
                show(sDefaultTimeout);
                if (mPauseButton != null) {
                    mPauseButton.requestFocus();
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            if (uniqueDown && !mPlayer.isPlaying()) {
                mPlayer.start();
                updatePausePlay();
                show(sDefaultTimeout);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (uniqueDown && mPlayer.isPlaying()) {
                mPlayer.pause();
                updatePausePlay();
                show(sDefaultTimeout);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                || keyCode == KeyEvent.KEYCODE_CAMERA) {
            // don't show the controls for volume adjustment
            return super.dispatchKeyEvent(event);
        } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (uniqueDown) {
                hide();
            }
            return true;
        }

        show(sDefaultTimeout);
        return super.dispatchKeyEvent(event);
    }

    private final OnClickListener mPauseListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            doPauseResume();
            show(sDefaultTimeout);
        }
    };

    private final OnClickListener mSetSpeedListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            onClickOptionChangeSpeed(mPlaySpeedTv, mPlayer);

        }
    };

    private void updatePausePlay() {
        if (mRoot == null)
            return;
        onClickOptionPause(mPlayer);
    }


    private void doPauseResume() {
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
        } else {
            mPlayer.start();
        }
        updatePausePlay();
    }

    private final SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onStartTrackingTouch(SeekBar bar) {
            //when you drag the seekBar

            show(3600000);
            mDragging = true;
            mHandler.removeMessages(SHOW_PROGRESS);
            onSeekBarStartDrag();
        }


        @Override
        public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            //when the seekBar progress Changed

            if (!fromUser) {
                return;
            }

            long duration = mPlayer.getDuration();
            long newPosition = (duration * progress) / 1000L;
            mPlayer.seekTo( (int) newPosition);
            onSeekBarProgressChanged(newPosition, duration);
        }


        @Override
        public void onStopTrackingTouch(SeekBar bar) {
            //when you stop drag the seekBar

            mDragging = false;
            setProgress();
            updatePausePlay();
            show(sDefaultTimeout);
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
            onSeekBarStopDrag();
        }
    };

    //========================= Override  Methods =====================//
    // you can extend your UI methods in your superIjkPlayer

    /**
     * called when you click change speed option
     * @param mPlaySpeedTv
     * @param mPlayer
     */
    protected void onClickOptionChangeSpeed(TextView mPlaySpeedTv, MediaPlayerControl mPlayer){
        if (mPlaySpeedTv.getText().toString().contains("2.0")){
            mPlayer.setSpeed(1.0f);
            mPlaySpeedTv.setText("X 1.0");
        } else if (mPlaySpeedTv.getText().toString().contains("1.0")) {
            mPlayer.setSpeed(1.25f);
            mPlaySpeedTv.setText("X 1.25");
        }else if (mPlaySpeedTv.getText().toString().contains("1.25")) {
            mPlayer.setSpeed(1.5f);
            mPlaySpeedTv.setText("X 1.5");
        } else {
            mPlayer.setSpeed(2f);
            mPlaySpeedTv.setText("X 2.0");
        }
    }


    /**
     * when the seekBar progress changed
     * @param current the current progress of media
     * @param duration the total progress of media
     */
    protected void onSeekBarProgressChanged(long current, long duration) {
        if (mCurrentTime != null)
            mCurrentTime.setText(stringForTime(current , duration));
        Log.i("fuck", "onSeekBarProgressChanged: ");
    }

    /**
     * when you start drag the seekBar
     */
    protected void onSeekBarStartDrag() {
        Log.i("fuck", "onSeekBarStartDrag: ");
    }

    /**
     * when you stop drag the seekBar
     * it will call after {@link #onSeekBarProgressChanged(long current, long duration)}
     */
    protected void onSeekBarStopDrag() {
        Log.i("fuck", "onSeekBarStopDrag: ");
    }

    /**
     * you can change your pause button UI state
     * @param mPlayer the MediaPlayerControl
     */
    protected void onClickOptionPause(MediaPlayerControl mPlayer) {
        if (mPauseButton == null)
            return;
        if (mPauseButton instanceof ImageView){
            ImageView pauseButton = (ImageView) mPauseButton;
            if (mPlayer.isPlaying()) {
                pauseButton.setImageResource(R.drawable.video_pause);
                mPauseButton.setContentDescription(mPauseDescription);
            } else {
                pauseButton.setImageResource(R.drawable.video_start);
                mPauseButton.setContentDescription(mPlayDescription);
            }
        }

    }

    //========================= External  Methods =====================//

    public void setMediaPlayer(MediaPlayerControl player) {
        mPlayer = player;
        updatePausePlay();
    }

    public void show() {
        show(sDefaultTimeout);
    }

    /**
     * show the media controller panel with a timer
     * @param timeout
     */
    public void show(int timeout) {
        if (!mShowing) {
            setProgress();
            if (mPauseButton != null) {
                mPauseButton.requestFocus();
            }
            showControlLayout();
            disableUnsupportedButtons();
            mShowing = true;
        }
        updatePausePlay();
        mHandler.sendEmptyMessage(SHOW_PROGRESS);

        if (timeout != 0) {
            mHandler.removeMessages(FADE_OUT);
            Message msg = mHandler.obtainMessage(FADE_OUT);
            mHandler.sendMessageDelayed(msg, timeout);
        }
    }

    public boolean isShowing() {
        return mShowing;
    }

    public void hide() {
        if (mShowing) {
            try {
                hideControlLayout();
                mHandler.removeMessages(SHOW_PROGRESS);
            } catch (IllegalArgumentException ex) {
                Log.w("MediaController", "already removed");
            }
            mShowing = false;
        }
    }

    /**
     * set the controller panel enable
     * @param enabled
     */
    @Override
    public void setEnabled(boolean enabled) {
        if (mPauseButton != null) {
            mPauseButton.setEnabled(enabled);
        }
        if (mProgress != null) {
            mProgress.setEnabled(enabled);
        }
        disableUnsupportedButtons();
        super.setEnabled(enabled);
    }

    /**
     * is support GestureDetector, default is support
     * @param gestureDetectorEnable
     */
    public void setGestureDetectorEnable(boolean gestureDetectorEnable) {
        isGestureDetectorEnable = gestureDetectorEnable;
    }

    public void setMediaControllerLayoutId(int layoutId){
        mMediaControllerLayoutId = layoutId;
    }

    public interface MediaPlayerControl {
        void    start();
        void    pause();
        int     getDuration();
        int     getCurrentPosition();
        void    seekTo(int pos);
        boolean isPlaying();
        int     getBufferPercentage();
        boolean canPause();
        boolean canSeekBackward();
        boolean canSeekForward();

        /**
         * speed value [0.75f ,2f]
         * the source file need have audio, otherwise this method will not work
         * https://github.com/Bilibili/ijkplayer/issues/3316
         * @param speed
         */
        void setSpeed(float speed);
    }
}


