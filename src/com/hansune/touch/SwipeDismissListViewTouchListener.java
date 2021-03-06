/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hansune.touch;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link android.view.View.OnTouchListener} that makes the list items in a {@link android.widget.ListView}
 * dismissable. {@link android.widget.ListView} is given special treatment because by default it handles touches
 * for its list items... i.e. it's in charge of drawing the pressed state (the list selector),
 * handling list item clicks, etc.
 *
 * <p>After creating the listener, the caller should also call
 * {@link android.widget.ListView#setOnScrollListener(android.widget.AbsListView.OnScrollListener)}, passing
 * in the scroll listener returned by {@link #makeScrollListener()}. If a scroll listener is
 * already assigned, the caller should still pass scroll changes through to this listener. This will
 * ensure that this {@link SwipeDismissListViewTouchListener} is paused during list view
 * scrolling.</p>
 *
 * <p>Example usage:</p>
 *
 * <pre>
 * SwipeDismissListViewTouchListener touchListener =
 *         new SwipeDismissListViewTouchListener(
 *                 listView,
 *                 new SwipeDismissListViewTouchListener.OnDismissCallback() {
 *                     public void onDismiss(ListView listView, int[] reverseSortedPositions) {
 *                         for (int position : reverseSortedPositions) {
 *                             adapter.remove(adapter.getItem(position));
 *                         }
 *                         adapter.notifyDataSetChanged();
 *                     }
 *                 });
 * listView.setOnTouchListener(touchListener);
 * listView.setOnScrollListener(touchListener.makeScrollListener());
 * </pre>
 *
 * <p>This class Requires API level 12 or later due to use of {@link
 * android.view.ViewPropertyAnimator}.</p>
 *
 * <p>For a generalized {@link android.view.View.OnTouchListener} that makes any view dismissable,
 * see {@link SwipeDismissListViewTouchListener}.</p>
 *
 * <p>* this is fork code.
 * original code : https://github.com/romannurik/android-swipetodismiss
 * <br>
 * I add some functions below.<br>
 * {@link #setSwipeDistanceRatio(float)}, {@link #setSwipeMode(int)},
 * {@link #setEnableTouchListen(boolean)}, {@link #setDoDismiss(boolean)},
 * {@link com.hansune.touch.SwipeDismissListViewTouchListener.DismissCallbacks#onTryToDismiss(android.view.View, int)}
 * With these codes, I make a some different example.
 * Thanks to original author. <br><br>
 * Brownsoo.<br>
 *     Example : <br>
 *     Code : <br>
 * </p>
 *
 * @see SwipeDismissTouchListener
 */
public class SwipeDismissListViewTouchListener implements View.OnTouchListener {

    /** Right and Left swiping <br> 오른쪽과 왼쪽으로 밀기 모드*/
    public static final int SWIPE_MODE_BOTH = 0;
    /** Right only swiping <br> 오른쪽으로만 밀기 모드*/
    public static final int SWIPE_MODE_RIGHT = 1;
    /** Left only swiping <br> 왼쪽으로만 밀기 모드*/
    public static final int SWIPE_MODE_LEFT = 2;

    private static final String TAG = "ListViewSwipeTouchListener";

    // Cached ViewConfiguration and system-wide constant values
    private int mSlop;
    private int mMinFlingVelocity;
    private int mMaxFlingVelocity;
    private long mAnimationTime;

    // Fixed properties
    private ListView mListView;
    private DismissCallbacks mCallbacks;
    private int mViewWidth = 1; // 1 and not 0 to prevent dividing by zero

    // Transient properties
    private List<PendingDismissData> mPendingDismisses = new ArrayList<PendingDismissData>();
    private int mDismissAnimationRefCount = 0;
    private float mDownX;
    private float mDownY;
    private boolean mSwiping;
    private int mSwipingSlop;
    private VelocityTracker mVelocityTracker;
    private int mDownPosition;
    private View mDownView;
    private boolean mPaused;
    private boolean touchListen = true;
    private int swipeMode = SWIPE_MODE_BOTH;
    private boolean mDoDismiss = true;
    private float swipeDistanceRatio = 1;
    private float dismissDecisionDistanceRatio = 0.5f;

    /**
     * Determine swiping direction.<br>
     * 밀기 방향을 결정한다.
     * @param swipeMode {@link #SWIPE_MODE_BOTH}, {@link #SWIPE_MODE_RIGHT}, {@link #SWIPE_MODE_LEFT}
     */
    public void setSwipeMode(int swipeMode) {
        switch (swipeMode) {
            case SWIPE_MODE_LEFT :
            case SWIPE_MODE_RIGHT :
                this.swipeMode = swipeMode;
                break;
            default:
            case SWIPE_MODE_BOTH :
                this.swipeMode = swipeMode;
                break;
        }
    }

    /**
     * Get swiping mode.
     * @return {@link #SWIPE_MODE_BOTH}, {@link #SWIPE_MODE_RIGHT}, {@link #SWIPE_MODE_LEFT}
     */
    public int getSwipeMode() {
        return swipeMode;
    }

    /**
     * Determine whether the item view will be dismissed when swiped<br>
     * 밀기를 통해 리스트 아이템 뷰를 사라지게 할 것인지 여부
     */
    public boolean isDoDismiss() {
        return mDoDismiss;
    }

    /**
     * Determine whether the item view will be dismissed or not when swiped<br/>
     * 밀기를 통해 리스트 아이템 뷰를 사라지게 할지 결정한다.
     * @param doDismiss if false, the child view will be not dismissed
     *                   and {@link DismissCallbacks#onTryToDismiss} will be called
     *                   instead {@link DismissCallbacks#onDismiss}
     */
    public void setDoDismiss(boolean doDismiss) {
        this.mDoDismiss = doDismiss;
    }

    /**
     * Set the movable distance in ratio to the length of the item view.
     * If the distance is less than the value of {@link #getDismissDecisionDistanceRatio()},
     * dismissing event is not called.
     * <br>
     * <br>
     * 밀기 가능한 거리를 아이템 뷰 가로 길이의 비율로 지정한다.
     * 만약 그 거리가 {@link #getDismissDecisionDistanceRatio()}의 값보다 작을 경우,
     * 사라지게 하는 이벤트는 작동하지 않는다.
     * @param ratio Proportion to the length of the item view. (0 ~ 1)<br>
     *              If this is 1, user can swipe the item view as far as the item view's width.
     */
    public void setSwipeDistanceRatio(float ratio) {
        this.swipeDistanceRatio = ratio;
    }

    /**
     * To obtain the movable distance in proportion to the length of the item view.<br>
     *     <br>
     *     움직일 수 있는 거리를 아이템뷰의 길이에 대한 비율로 얻는다.
     * @return Proportion to the length of the item view. (0 ~ 1)
     */
    public float getSwipeDistanceRatio() {
        return swipeDistanceRatio;
    }


    /**
     * Set the distance to decide dismissal in proportion to the length of the item view.<br>
     * <br>
     * 언제 사라지게 할지 판단하기 위한 이동 거리를 비율로 지정한다. 비율은 아이템뷰 가로 길이에 대한 거리를 말한다.
     * @param ratio Proportion to the length of the item view. (0 ~ 1)
     */
    public void setDismissDecisionDistanceRatio(float ratio) {
        this.dismissDecisionDistanceRatio = ratio;
    }

    /**
     * To obtain the distance to decide dismissal in portion to the length of the item view<br>
     * <br>
     * 언제 사라지게 할지 판단하기 위한 이동 거리를 비율로 얻는다. 비율은 아이템뷰 가로 길이에 대한 거리를 말한다.
     * @return Proportion to the length of the item view. (0 ~ 1)
     */
    public float getDismissDecisionDistanceRatio() {
        return dismissDecisionDistanceRatio;
    }

    /**
     * The callback interface used by {@link SwipeDismissListViewTouchListener} to inform its client
     * about a successful dismissal of one or more list item positions.
     */
    public interface DismissCallbacks {
        /**
         * Called to determine whether the given position can be dismissed.
         */
        boolean canDismiss(int position);

        /**
         * Called when the user has indicated they would like to dismiss one or more list item
         * positions. It runs the disappearing animation.(사라지는 애니메이션까지 진행)
         *
         * @param listView               The originating {@link android.widget.ListView}.
         * @param reverseSortedPositions An array of positions to dismiss, sorted in descending
         *                               order for convenience.
         */
        void onDismiss(ListView listView, int[] reverseSortedPositions);

        /**
         * Called when the user has indicated they would list to dismiss one list item
         * position Only if {@link #isDoDismiss()} is false.
         * Thus {@link DismissCallbacks#onDismiss(android.widget.ListView, int[])} is not called.
         * It does not runs the disappearing animation. Swiped item view returns to original position.
         * <br><br>
         * {@link #isDoDismiss()} 값이 false 일 때, 사용자가 하나의 아이템을 밀어서 사라지게 하려고 시도할 때 호출된다.
         * 그래서 {@link DismissCallbacks#onDismiss(android.widget.ListView, int[])} 는 호출되지 않는다.
         * 아이템 뷰는 사라지지 않고 원래 자리로 돌아간다.
         *
         *
         * @param childView The item view of {@link android.widget.ListView}.
         * @param position position to try to dismiss
         */
        void onTryToDismiss(View childView, int position);
    }

    /**
     * Constructs a new swipe-to-dismiss touch listener for the given list view.
     *
     * @param listView  The list view whose items should be dismissable.
     * @param callbacks The callback to trigger when the user has indicated that she would like to
     *                  dismiss one or more list items.
     */
    public SwipeDismissListViewTouchListener(ListView listView, DismissCallbacks callbacks) {
        ViewConfiguration vc = ViewConfiguration.get(listView.getContext());
        mSlop = vc.getScaledTouchSlop();
        mMinFlingVelocity = vc.getScaledMinimumFlingVelocity() * 16;
        mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mAnimationTime = listView.getContext().getResources().getInteger(
                android.R.integer.config_shortAnimTime);
        mListView = listView;
        mCallbacks = callbacks;
    }

    /**
     * Enables or disables (pauses or resumes) watching for swipe-to-dismiss gestures.
     *
     * @param enabled Whether or not to watch for gestures.
     */
    public void setEnabled(boolean enabled) {
        mPaused = !enabled;
    }

    /**
     * Enables or disables listening for touch<br>
     * 터치 이벤트 리스너를 동작시킬지 말지 결정한다.
     * @param listen if false, {@link #onTouch(android.view.View, android.view.MotionEvent)} of {@link android.view.View} does not work.<br>
     *               default true.
     */
    public void setEnableTouchListen(boolean listen) {
        this.touchListen = listen;
    }

    /**
     * Returns whether it listens for touch event.
     * @return if false, touch events does not work.
     */
    public boolean isEnableTouchListen(){
        return this.touchListen;
    }


    /**
     * Returns an {@link android.widget.AbsListView.OnScrollListener} to be added to the {@link
     * android.widget.ListView} using {@link android.widget.ListView#setOnScrollListener(android.widget.AbsListView.OnScrollListener)}.
     * If a scroll listener is already assigned, the caller should still pass scroll changes through
     * to this listener. This will ensure that this {@link SwipeDismissListViewTouchListener} is
     * paused during list view scrolling.</p>
     *
     * @see SwipeDismissListViewTouchListener
     */
    public AbsListView.OnScrollListener makeScrollListener() {
        return new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                setEnabled(scrollState != AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {
            }
        };
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {

        if(!touchListen) return false;

        if (mViewWidth < 2) {
            mViewWidth = mListView.getWidth();
        }

        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (mPaused) {
                    return false;
                }

                // TODO: ensure this is a finger, and set a flag

                // Find the child view that was touched (perform a hit test)
                Rect rect = new Rect();
                int childCount = mListView.getChildCount();
                int[] listViewCoords = new int[2];
                mListView.getLocationOnScreen(listViewCoords);
                int x = (int) motionEvent.getRawX() - listViewCoords[0];
                int y = (int) motionEvent.getRawY() - listViewCoords[1];
                View child;
                for (int i = 0; i < childCount; i++) {
                    child = mListView.getChildAt(i);
                    child.getHitRect(rect);
                    if (rect.contains(x, y)) {
                        mDownView = child;
                        break;
                    }
                }

                if (mDownView != null) {
                    mDownX = motionEvent.getRawX();
                    mDownY = motionEvent.getRawY();
                    mDownPosition = mListView.getPositionForView(mDownView);
                    if (mCallbacks.canDismiss(mDownPosition)) {
                        mVelocityTracker = VelocityTracker.obtain();
                        mVelocityTracker.addMovement(motionEvent);
                    } else {
                        mDownView = null;
                    }
                }
                return false;
            }

            case MotionEvent.ACTION_CANCEL: {
                if (mVelocityTracker == null) {
                    break;
                }

                if (mDownView != null && mSwiping) {
                    // cancel
                    mDownView.animate()
                            .translationX(0)
                            .alpha(1)
                            .setDuration(mAnimationTime)
                            .setListener(null);
                }
                mVelocityTracker.recycle();
                mVelocityTracker = null;
                mDownX = 0;
                mDownY = 0;
                mDownView = null;
                mDownPosition = ListView.INVALID_POSITION;
                mSwiping = false;
                break;
            }

            case MotionEvent.ACTION_UP: {

                if (mVelocityTracker == null) {
                    break;
                }

                float deltaX = motionEvent.getRawX() - mDownX;
                mVelocityTracker.addMovement(motionEvent);
                mVelocityTracker.computeCurrentVelocity(1000);
                float velocityX = mVelocityTracker.getXVelocity();
                float absVelocityX = Math.abs(velocityX);
                float absVelocityY = Math.abs(mVelocityTracker.getYVelocity());
                boolean dismiss = false;
                boolean dismissRight = false;

                float viewXRatio = (mDownView.getX() / mViewWidth);
                //Check dismissal in view of moved distance.
                if (Math.abs(viewXRatio) > dismissDecisionDistanceRatio && mSwiping) {
                    dismiss = true;
                    dismissRight = deltaX > 0;
                }
                //Check dismissal in view of moving velocity.
                else if (mMinFlingVelocity <= absVelocityX && absVelocityX <= mMaxFlingVelocity
                        && absVelocityY < absVelocityX
                        && mSwiping) {
                    // dismiss only if flinging in the same direction as dragging
                    dismiss = (velocityX < 0) == (deltaX < 0);
                    dismissRight = mVelocityTracker.getXVelocity() > 0;
                }

                if (dismiss
                        && mDownPosition != ListView.INVALID_POSITION
                        && mDoDismiss) {
                    // dismiss
                    final View downView = mDownView; // mDownView gets null'd before animation ends
                    final int downPosition = mDownPosition;
                    ++mDismissAnimationRefCount;
                    mDownView.animate()
                            .translationX(dismissRight ? mViewWidth : -mViewWidth)
                            .alpha(0)
                            .setDuration(mAnimationTime)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    performDismiss(downView, downPosition);
                                }
                            });
                } else {

                    if(dismiss && !mDoDismiss) {
                        // try to dismiss
                        final View downView = mDownView; // mDownView gets null'd before animation ends
                        final int downPosition = mDownPosition;

                        mDownView.animate()
                                .translationX(0)
                                .alpha(1)
                                .setDuration(mAnimationTime)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        mCallbacks.onTryToDismiss(downView, downPosition);
                                    }
                                });
                    }
                    else {
                        // cancel
                        mDownView.animate()
                                .translationX(0)
                                .alpha(1)
                                .setDuration(mAnimationTime)
                                .setListener(null);
                    }

                }
                mVelocityTracker.recycle();
                mVelocityTracker = null;
                mDownX = 0;
                mDownY = 0;
                mDownView = null;
                mDownPosition = ListView.INVALID_POSITION;
                mSwiping = false;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mVelocityTracker == null || mPaused) {
                    break;
                }

                mVelocityTracker.addMovement(motionEvent);
                float deltaX = motionEvent.getRawX() - mDownX;
                float deltaY = motionEvent.getRawY() - mDownY;

                //limit direction
                if(swipeMode == SWIPE_MODE_LEFT) {
                    deltaX = Math.min(0, deltaX);
                }
                else {
                    deltaX = Math.max(0, deltaX);
                }
                //limit distance
                float swipeDistance = mViewWidth * swipeDistanceRatio;
                if(deltaX > 0) {
                    deltaX = Math.min(swipeDistance, deltaX);
                }
                else {
                    deltaX = Math.max(-swipeDistance, deltaX);
                }

                //check starting swipe
                if (Math.abs(deltaX) > mSlop && Math.abs(deltaY) < Math.abs(deltaX) / 2) {
                    mSwiping = true;
                    mSwipingSlop = (deltaX > 0 ? mSlop : -mSlop);
                    mListView.requestDisallowInterceptTouchEvent(true);

                    // Cancel ListView's touch (un-highlighting the item)
                    MotionEvent cancelEvent = MotionEvent.obtain(motionEvent);
                    cancelEvent.setAction(MotionEvent.ACTION_CANCEL |
                            (motionEvent.getActionIndex()
                                    << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
                    mListView.onTouchEvent(cancelEvent);
                    cancelEvent.recycle();
                }

                if (mSwiping) {
                    mDownView.setTranslationX(deltaX - mSwipingSlop);
                    mDownView.setAlpha(Math.max(0f, Math.min(1f,
                            1f - Math.abs(deltaX) / mViewWidth)));
                    return true;
                }
                break;
            }
        }
        return false;
    }

    class PendingDismissData implements Comparable<PendingDismissData> {
        public int position;
        public View view;

        public PendingDismissData(int position, View view) {
            this.position = position;
            this.view = view;
        }

        @Override
        public int compareTo(PendingDismissData other) {
            // Sort by descending position
            return other.position - position;
        }
    }


    /**
     * Dismiss view with animation.
     * After animation, {@link SwipeDismissListViewTouchListener.DismissCallbacks#onDismiss(android.widget.ListView, int[])}
     * is called.
     * <br><br>
     * 뷰를 사라지게 한다. 애니메이션이 끝나면,
     * {@link SwipeDismissListViewTouchListener.DismissCallbacks#onDismiss(android.widget.ListView, int[])}
     * 함수가 실행된다.
     * @param dismissView target view to dismiss
     * @param dismissPosition position to dismiss
     */
    public void dismiss(View dismissView, int dismissPosition) {
        ++mDismissAnimationRefCount;
        performDismiss(dismissView, dismissPosition);
    }

    private void performDismiss(final View dismissView, final int dismissPosition) {
        // Animate the dismissed list item to zero-height and fire the dismiss callback when
        // all dismissed list item animations have completed. This triggers layout on each animation
        // frame; in the future we may want to do something smarter and more performant.

        final ViewGroup.LayoutParams lp = dismissView.getLayoutParams();
        final int originalHeight = dismissView.getHeight();

        ValueAnimator animator = ValueAnimator.ofInt(originalHeight, 1).setDuration(mAnimationTime);

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                --mDismissAnimationRefCount;
                if (mDismissAnimationRefCount == 0) {
                    // No active animations, process all pending dismisses.
                    // Sort by descending position
                    Collections.sort(mPendingDismisses);

                    int[] dismissPositions = new int[mPendingDismisses.size()];
                    for (int i = mPendingDismisses.size() - 1; i >= 0; i--) {
                        dismissPositions[i] = mPendingDismisses.get(i).position;
                    }
                    mCallbacks.onDismiss(mListView, dismissPositions);
                    
                    // Reset mDownPosition to avoid MotionEvent.ACTION_UP trying to start a dismiss 
                    // animation with a stale position
                    mDownPosition = ListView.INVALID_POSITION;

                    ViewGroup.LayoutParams lp;
                    for (PendingDismissData pendingDismiss : mPendingDismisses) {
                        // Reset view presentation
                        pendingDismiss.view.setAlpha(1f);
                        pendingDismiss.view.setTranslationX(0);
                        lp = pendingDismiss.view.getLayoutParams();
                        lp.height = originalHeight;
                        pendingDismiss.view.setLayoutParams(lp);
                    }

                    // Send a cancel event
                    long time = SystemClock.uptimeMillis();
                    MotionEvent cancelEvent = MotionEvent.obtain(time, time,
                            MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    mListView.dispatchTouchEvent(cancelEvent);

                    mPendingDismisses.clear();
                }
            }
        });

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                lp.height = (Integer) valueAnimator.getAnimatedValue();
                dismissView.setLayoutParams(lp);
            }
        });

        mPendingDismisses.add(new PendingDismissData(dismissPosition, dismissView));
        animator.start();
    }
}
