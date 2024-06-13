/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 27/05/2017
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;

import me.vkryl.android.animatorx.AnimatorListener;
import me.vkryl.android.animatorx.FloatAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

public class ViewPagerHeaderViewCompact extends FrameLayoutFix implements PagerHeaderView, StretchyHeaderView, ViewPagerTopView.SelectionChangeListener {
  private static class VH extends RecyclerView.ViewHolder {
    public VH (View itemView) {
      super(itemView);
      setIsRecyclable(false);
    }
  }

  @Override
  public void checkRtl () {
    LinearLayoutManager manager = (LinearLayoutManager) getRecyclerView().getLayoutManager();
    if (manager.getReverseLayout() != Lang.rtl()) {
      View view = manager.findViewByPosition(0);
      boolean needScroll = false;
      int scrollOffset = 0;
      if (view != null) {
        int parentWidth = getRecyclerView().getMeasuredWidth();
        int viewWidth = view.getMeasuredWidth();
        int viewOffset = view.getLeft();
        if (viewWidth > parentWidth) {
          int availScrollX = viewWidth - parentWidth;
          int scrolledX;
          if (Lang.rtl()) { // LTR -> RTL
            scrolledX = -viewOffset;
          } else { // RTL -> LTR
            scrolledX = availScrollX + viewOffset;
          }
          scrollOffset = -scrolledX;
          needScroll = true;
        }
      }
      manager.setReverseLayout(Lang.rtl());
      if (needScroll) {
        manager.scrollToPositionWithOffset(0, scrollOffset);
        hasUserInteraction = false;
      }
    }
    getTopView().checkRtl();
  }

  @Override
  public boolean hasPendingUserInteraction () {
    return hasUserInteraction;
  }

  @Override
  public void resetUserInteraction () {
    hasUserInteraction = false;
    resetUserInteraction = false;
  }

  private static class A extends RecyclerView.Adapter<VH> {
    private final ViewPagerTopView topView;

    public A (ViewPagerTopView topView) {
      this.topView = topView;
    }

    @Override
    @NonNull
    public VH onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      if (topView.getParent() != null) {
        Log.w("ViewPagerHeaderViewCompact: topView is already attached to another cell");
        ((ViewGroup) topView.getParent()).removeView(topView);
      }
      return new VH(topView);
    }

    @Override
    public void onBindViewHolder (@NonNull VH holder, int position) { }

    @Override
    public int getItemCount () {
      return 1;
    }
  }

  private final A adapter;
  private final RecyclerView recyclerView;

  private @Dimension(unit = Dimension.DP) float fadingEdgeLength;

  public ViewPagerHeaderViewCompact (Context context) {
    super(context);

    ViewPagerTopView topView = new ViewPagerTopView(context);
    topView.setSelectionColorId(ColorId.headerTabActive);
    topView.setTextFromToColorId(ColorId.headerTabInactiveText, ColorId.headerTabActiveText);
    topView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Size.getHeaderPortraitSize()));
    topView.setSelectionChangeListener(this);

    adapter = new A(topView);

    recyclerView = new RecyclerView(context) {
      private final Paint paint = new Paint();
      private final Matrix matrix = new Matrix();

      {
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        paint.setShader(new LinearGradient(0, 0, 1, 0, Color.BLACK, 0, Shader.TileMode.CLAMP));
      }

      @Override
      public void draw (Canvas c) {
        int length = Screen.dp(fadingEdgeLength);
        if (getChildCount() == 0 || length <= 1 /* px */) {
          super.draw(c);
          return;
        }
        View child = getChildAt(0);
        int leftSpan = -child.getLeft();
        float leftFadeStrength = leftSpan < length ? Math.max(0f, leftSpan / (float) length) : 1f;
        float leftLength = leftFadeStrength * length;
        boolean drawLeft = leftLength > 1f /* px */;

        int rightSpan = child.getRight() - getWidth();
        float rightFadeStrength = rightSpan < length ? Math.max(0f, rightSpan / (float) length) : 1f;
        float rightLength = rightFadeStrength * length;
        boolean drawRight = rightLength > 1f /* px */;

        if (!drawLeft && !drawRight) {
          super.draw(c);
          return;
        }

        int selectionHeight = Screen.dp(ViewPagerTopView.SELECTION_HEIGHT);
        int top = topView.isDrawSelectionAtTop() ? selectionHeight : 0;
        int bottom = getHeight() - (topView.isDrawSelectionAtTop() ? 0 : selectionHeight);
        int saveCount = c.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
        super.draw(c);
        if (drawLeft) {
          matrix.setScale(leftLength, 1f);
          paint.getShader().setLocalMatrix(matrix);
          c.drawRect(0, top, length, bottom, paint);
        }
        if (drawRight) {
          matrix.setScale(rightLength, 1f);
          matrix.postRotate(180);
          matrix.postTranslate(getWidth(), 0);
          paint.getShader().setLocalMatrix(matrix);
          c.drawRect(getWidth() - length, top, getWidth(), bottom, paint);
        }
        c.restoreToCount(saveCount);
      }
    };
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      private boolean dragging;

      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        dragging = newState == RecyclerView.SCROLL_STATE_DRAGGING;
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
          if (resetUserInteraction) {
            resetUserInteraction();
          }
        }
      }

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (dx != 0 && dragging) {
          hasUserInteraction = true;
        }
      }
    });
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getHeaderPortraitSize(), Gravity.TOP));
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS :OVER_SCROLL_NEVER);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, Lang.rtl()));
    recyclerView.setAdapter(adapter);
    addView(recyclerView);

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getHeaderBigPortraitSize(true)));
  }

  private boolean hasUserInteraction, resetUserInteraction;

  public void setFadingEdgeLength (@Dimension(unit = Dimension.DP) float length) {
    if (fadingEdgeLength != length) {
      fadingEdgeLength = length;
      recyclerView.invalidate();
    }
  }

  @Override
  public void onSelectionChanged (int selectionLeft, int selectionWidth, int firstItemWidth, int lastItemWidth, float totalFactor, boolean animated) {
    View view = recyclerView.getLayoutManager().findViewByPosition(0);
    if (view == null) {
      return;
    }

    final int viewWidth;
    final float topViewTranslationX;
    final ViewPagerTopView topView = getTopView();
    if (topView.shouldWrapContent()) {
      final float selectionFactor = totalFactor * (topView.getItemCount() - 1);
      final int totalWidth = topView.getTotalWidth();
      final int itemsWidth = topView.getItemsWidth(selectionFactor);
      int widthDiff = Math.min(itemsWidth - totalWidth, 0);
      topViewTranslationX = animated ? (widthDiff - Views.getRightMargin(topView)) / 2f : 0f;
      Views.setRightMargin(topView, widthDiff);
      viewWidth = itemsWidth;
    } else {
      topView.setTranslationX(topViewTranslationX = 0f);
      Views.setRightMargin(topView, 0);
      viewWidth = topView.getMeasuredWidth();
    }

    final int parentWidth = recyclerView.getMeasuredWidth();
    final int parentPaddingLeft = recyclerView.getPaddingLeft();
    final int parentPaddingRight = recyclerView.getPaddingRight();
    if (parentWidth == 0 || viewWidth <= parentWidth - parentPaddingLeft - parentPaddingRight) {
      if (topViewTranslationX != 0f) {
        topView.setTranslationX(topViewTranslationX);
        topView.animate()
          .translationX(0f)
          .setInterpolator(QUINTIC_INTERPOLATOR)
          .setDuration(computeScrollDuration(topViewTranslationX, parentWidth))
          .start();
      }
      return;
    }
    if (recyclerView.isComputingLayout()) {
      return;
    }

    //noinspection UnnecessaryLocalVariable
    final int maxViewLeft = parentPaddingLeft;
    final int minViewLeft = parentWidth - viewWidth - parentPaddingRight;
    if (minViewLeft > maxViewLeft) {
      return;
    }
    final int viewLeft = MathUtils.clamp(view.getLeft(), minViewLeft, maxViewLeft); // TODO RTL

    final Interpolator interpolator = QUINTIC_INTERPOLATOR;
    int animationDuration = RecyclerView.UNDEFINED_DURATION;

    if ((getParent() != null && ((View) getParent()).getMeasuredWidth() > getMeasuredWidth()) || (topView.getMeasuredWidth() - parentWidth) < lastItemWidth / 2) {
      int desiredViewLeft = (int) (maxViewLeft * (1f - totalFactor) + minViewLeft * totalFactor);
      if (viewLeft != desiredViewLeft) {
        int diff = (viewLeft - desiredViewLeft)/* * (Lang.rtl() ? -1 : 1)*/;  // TODO RTL
        if (animated) {
          if (topViewTranslationX != 0f) {
            animationDuration = computeScrollDuration(diff, parentWidth);
          }
          recyclerView.stopScroll();
          recyclerView.smoothScrollBy(diff, 0, interpolator, animationDuration);
          if (hasUserInteraction) {
            resetUserInteraction = true;
          }
        } else {
          pseudoSmoothScrollBy(diff);
        }
      } else {
        resetUserInteraction();
      }
    } else {
      int visibleSelectionX = selectionLeft + viewLeft;
      int desiredSelectionX;
      if (parentPaddingLeft > 0) {
        desiredSelectionX = parentPaddingLeft;
      } else {
        desiredSelectionX = (int) ((float) Screen.dp(16f) * (selectionLeft >= selectionWidth ? 1f : (float) selectionLeft / (float) selectionWidth));
      }

      if (visibleSelectionX != desiredSelectionX) {
        int newViewLeft = viewLeft + (desiredSelectionX - visibleSelectionX);
        newViewLeft = MathUtils.clamp(newViewLeft, minViewLeft, maxViewLeft);
        if (newViewLeft != viewLeft) {
          int offset = (viewLeft - newViewLeft)/* * (Lang.rtl() ? -1 : 1)*/; // TODO RTL
          if (animated) {
            if (topViewTranslationX != 0f) {
              animationDuration = computeScrollDuration(offset, parentWidth);
            }
            recyclerView.stopScroll();
            recyclerView.smoothScrollBy(offset, 0, interpolator, animationDuration);
            if (hasUserInteraction) {
              resetUserInteraction = true;
            }
          } else {
            pseudoSmoothScrollBy(offset);
          }
        } else {
          resetUserInteraction();
        }
      }
    }

    /* else {
      int visibleSelectionLeft = selectionLeft + viewOffset;

      int desiredLeft = (int) ((float) Screen.dp(16f) * (selectionLeft >= selectionWidth ? 1f : (float) selectionLeft / (float) selectionWidth));

      if (visibleSelectionLeft != desiredLeft) {
        int newViewLeft = viewOffset + (desiredLeft - visibleSelectionLeft);

        int maxLeft = parentWidth - viewWidth;
        if (newViewLeft < maxLeft) {
          newViewLeft = maxLeft;
        }


        if (newViewLeft != viewOffset) {
          recyclerView.stopScroll();
          int offset = viewOffset - newViewLeft;
          if (animated) {
            recyclerView.smoothScrollBy(offset, 0);
          } else {
            recyclerView.scrollBy(offset, 0);
          }
        }
      }
    }*/

    if (topViewTranslationX != 0f && animationDuration > 0) {
      topView.setTranslationX(topViewTranslationX);
      topView.animate()
        .translationX(0f)
        .setDuration(animationDuration)
        .setInterpolator(interpolator)
        .start();
    }
  }

  private FloatAnimator scrollByAnimator;
  private int scrolledBy, finalScrollBy;

  private void pseudoSmoothScrollBy (int scrollX) {
    if (scrollByAnimator != null && scrollByAnimator.isAnimating()) {
      finalScrollBy = scrollX + scrolledBy;
      return;
    }

    recyclerView.stopScroll();
    int threshold = Screen.dp(2f);
    if (Math.abs(scrollX) >= threshold && hasUserInteraction) {
      long duration = computeScrollDuration(scrollX, recyclerView.getMeasuredWidth());

      scrollByAnimator = new FloatAnimator(duration, QUINTIC_INTERPOLATOR, 0, new AnimatorListener<>() {
        @Override
        public void onAnimationUpdate (Float newValue) {
          int desiredScrollBy = (int) (finalScrollBy * newValue);
          int scrollBy = (desiredScrollBy - scrolledBy);
          recyclerView.scrollBy(scrollBy, 0);
          scrolledBy += scrollBy;
        }

        @Override
        public void onAnimationFinish (Float finalValue, boolean byAnimationEnd) {
          resetUserInteraction();
          scrollByAnimator = null;
        }
      });

      scrolledBy = 0;
      finalScrollBy = scrollX;

      scrollByAnimator.setAnimatedValue(1f);
    } else {
      recyclerView.scrollBy(scrollX, 0);
      resetUserInteraction();
    }
  }

  public boolean canScrollLeft () {
    int i = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
    if (i != 0) {
      return true;
    }
    int maxLeft = recyclerView.getClipToPadding() ? 0 : recyclerView.getPaddingLeft();
    View view = recyclerView.getLayoutManager().findViewByPosition(0);
    return view == null || view.getLeft() < maxLeft;
  }

  public boolean canScrollInAnyDirection () {
    int i = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
    if (i != 0) {
      return i != RecyclerView.NO_POSITION;
    }
    int maxLeft = recyclerView.getClipToPadding() ? 0 : recyclerView.getPaddingLeft();
    int minRight = recyclerView.getMeasuredWidth() - (recyclerView.getClipToPadding() ? 0 : recyclerView.getPaddingRight());
    View view = recyclerView.getLayoutManager().findViewByPosition(0);
    return view == null || view.getLeft() < maxLeft || view.getRight() > minRight;
  }

  public RecyclerView getRecyclerView () {
    return recyclerView;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return !(e.getAction() == MotionEvent.ACTION_DOWN && !canTouchAt(e.getX(), e.getY())) && super.onTouchEvent(e);
  }

  protected boolean canTouchAt (float x, float y) {
    y -= recyclerView.getTop() + (int) recyclerView.getTranslationY();
    return y >= 0 && y < adapter.topView.getMeasuredHeight();
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    return (e.getAction() == MotionEvent.ACTION_DOWN && !canTouchAt(e.getX(), e.getY())) || super.onInterceptTouchEvent(e);
  }

  @Override
  public View getView () {
    return this;
  }

  @Override
  public ViewPagerTopView getTopView () {
    return adapter.topView;
  }

  private static final float TOP_SCALE_LIMIT = .25f;

  @Override
  public void setScaleFactor (float scaleFactor, float fromFactor, float toScaleFactor, boolean byScroll) {
    //final float totalScale = (float) Size.HEADER_DRAWER_SIZE / (float) Size.HEADER_SIZE_DIFFERENCE;
    //scaleFactor = scaleFactor / totalScale;
    scaleFactor = 1f - scaleFactor;

    //noinspection Range
    recyclerView.setAlpha(scaleFactor <= TOP_SCALE_LIMIT ? 0f : (scaleFactor - TOP_SCALE_LIMIT) / TOP_SCALE_LIMIT);
    recyclerView.setTranslationY(Size.getHeaderSizeDifference(true) * (1f - scaleFactor));
  }

  private static final Interpolator QUINTIC_INTERPOLATOR = t -> {
    t -= 1.0f;
    return t * t * t * t * t + 1.0f;
  };

  private static int computeScrollDuration(float dx, int containerSize) {
    float duration = ((Math.abs(dx) / containerSize) + 1) * 300;
    return Math.min((int) duration, 2000);
  }
}
