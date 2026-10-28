package br.com.universidadebolso;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(Color.rgb(12,12,22));
    getWindow().setNavigationBarColor(Color.rgb(12,12,22));
    WebView w = new WebView(this);
    w.setBackgroundColor(Color.rgb(12,12,22));
    w.getSettings().setJavaScriptEnabled(true);
    w.getSettings().setDomStorageEnabled(true);
    w.setWebViewClient(new WebViewClient());
    w.setWebChromeClient(new WebChromeClient());
    w.loadUrl("file:///android_asset/index.html");
    setContentView(w, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
  }
  @Override public void onBackPressed() { super.onBackPressed(); }
}
