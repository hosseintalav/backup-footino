package com.footino.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * NestedSwipeRefreshLayout - رفع مشکل تداخل SwipeRefreshLayout با WebView.
 * فقط هنگامی که WebView در بالای صفحه است اجازه رفرش می‌دهد.
 */
public class NestedSwipeRefreshLayout extends SwipeRefreshLayout {

    private WebView webView;

    public NestedSwipeRefreshLayout(@NonNull Context context) {
        super(context);
    }

    public NestedSwipeRefreshLayout(@NonNull Context context, @NonNull AttributeSet attrs) {
        super(context, attrs);
    }

    public void setWebView(WebView webView) {
        this.webView = webView;
    }

    @Override
    public boolean canChildScrollUp() {
        if (webView != null) {
            if (webView.getScrollY() > 0) {
                return true;
            }
            return webView.canScrollVertically(-1);
        }
        return super.canChildScrollUp();
    }

    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        if (canChildScrollUp()) {
            return false;
        }
        return super.onStartNestedScroll(child, target, axes, type);
    }
}
