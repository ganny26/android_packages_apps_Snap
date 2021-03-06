/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.android.camera.PreviewGestures;

import java.util.ArrayList;
import java.util.List;

public class RenderOverlay extends FrameLayout {

    private static final String TAG = "CAM_Overlay";

    interface Renderer {

        public boolean handlesTouch();
        public boolean onTouchEvent(MotionEvent evt);
        public void setOverlay(RenderOverlay overlay);
        public void layout(int left, int top, int right, int bottom);
        public void draw(Canvas canvas);

    }

    private RenderView mRenderView;
    private List<Renderer> mClients;
    private PreviewGestures mGestures;
    // reverse list of touch clients
    private List<Renderer> mTouchClients;
    private int[] mPosition = new int[2];
    private final Rect mInsets = new Rect();

    public RenderOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRenderView = new RenderView(context);
        addView(mRenderView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        mClients = new ArrayList<Renderer>(10);
        mTouchClients = new ArrayList<Renderer>(10);
        setWillNotDraw(false);
    }

    public void setGestures(PreviewGestures gestures) {
        mGestures = gestures;
    }

    public void addRenderer(Renderer renderer) {
        mClients.add(renderer);
        renderer.setOverlay(this);
        if (renderer.handlesTouch()) {
            mTouchClients.add(0, renderer);
        }
        renderer.layout(mRenderView.getLeft(), mRenderView.getTop(),
                mRenderView.getRight(), mRenderView.getBottom());
    }

    public void addRenderer(int pos, Renderer renderer) {
        mClients.add(pos, renderer);
        renderer.setOverlay(this);
        renderer.layout(mRenderView.getLeft(), mRenderView.getTop(),
                mRenderView.getRight(), mRenderView.getBottom());
    }

    public void remove(Renderer renderer) {
        mClients.remove(renderer);
        renderer.setOverlay(null);
    }

    public int getClientSize() {
        return mClients.size();
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        if (!mInsets.equals(insets)) {
            mInsets.set(insets);
            // Make sure onMeasure will be called to adapt to the new insets.
            requestLayout();
        }
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // make sure all the children are resized - insets include status bar,
        // navigation bar, etc, but in this case, we are only concerned with the size of nav bar
        super.onMeasure(widthMeasureSpec - mInsets.right, heightMeasureSpec - mInsets.bottom);
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        if (mGestures != null) {
            if (!mGestures.isEnabled()) return false;
            mGestures.dispatchTouch(m);
            return true;
        }
        if (mTouchClients != null) {
            boolean res = false;
            for (Renderer client : mTouchClients) {
                res |= client.onTouchEvent(m);
            }
            return res;
        }
        return true;
    }

    public boolean directDispatchTouch(MotionEvent m, Renderer target) {
        mRenderView.setTouchTarget(target);
        boolean res = mRenderView.dispatchTouchEvent(m);
        mRenderView.setTouchTarget(null);
        return res;
    }

    private void adjustPosition() {
        getLocationInWindow(mPosition);
    }

    public int getWindowPositionX() {
        return mPosition[0];
    }

    public int getWindowPositionY() {
        return mPosition[1];
    }

    public void update() {
        mRenderView.invalidate();
    }

    private class RenderView extends View {

        private Renderer mTouchTarget;

        public RenderView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        public void setTouchTarget(Renderer target) {
            mTouchTarget = target;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent evt) {

            if (mTouchTarget != null) {
                return mTouchTarget.onTouchEvent(evt);
            }
            if (mTouchClients != null) {
                boolean res = false;
                for (Renderer client : mTouchClients) {
                    res |= client.onTouchEvent(evt);
                }
                return res;
            }
            return false;
        }

        @Override
        public void onLayout(boolean changed, int left, int top, int right, int bottom) {
            adjustPosition();
            super.onLayout(changed, left,  top, right, bottom);
            if (mClients == null) return;
            for (Renderer renderer : mClients) {
                renderer.layout(left, top, right, bottom);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            if (mClients == null) return;
            for (Renderer renderer : mClients) {
                renderer.draw(canvas);
            }
        }
    }

}
