package com.muzammil.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.http.SslError;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Muzammil's browser - a minimal WebView-based browser.
 *
 * WARNING: This activity installs a permissive TrustManager and a
 * WebViewClient that proceeds through SSL errors (expired / self-signed /
 * hostname-mismatched certificates). This intentionally disables HTTPS
 * certificate validation for the app's network traffic, which removes
 * protection against man-in-the-middle attacks. Only use this in trusted,
 * non-production environments (e.g. internal testing against a server with
 * an expired/self-signed cert). Do not ship this behavior in a
 * production/public app.
 */
public class MainActivity extends Activity {

    private static final String TAG = "MuzammilBrowser";

    // A modern Chrome-on-Android user agent string.
    private static final String CUSTOM_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36";

    private WebView webView;
    private EditText etUrl;
    private Button btnGo;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = (WebView) findViewById(R.id.webView);
        etUrl = (EditText) findViewById(R.id.etUrl);
        btnGo = (Button) findViewById(R.id.btnGo);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        // Install a permissive trust manager so that expired / invalid
        // certificates do not block HttpsURLConnection-based requests made
        // by the app (in addition to the WebViewClient override below,
        // which handles the WebView's own connections).
        installPermissiveTrustManager();

        setupWebView();

        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadUrlFromInput();
            }
        });

        etUrl.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    loadUrlFromInput();
                    return true;
                }
                return false;
            }
        });

        // Load a default start page.
        String startUrl = "https://www.google.com";
        etUrl.setText(startUrl);
        webView.loadUrl(startUrl);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // Allow zoom controls.
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);

        // Layout / rendering.
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        // Mixed content (http resources on https pages), needed on API 21+.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Hardcode a modern Chrome Android user agent.
        settings.setUserAgentString(CUSTOM_USER_AGENT);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                etUrl.setText(url);
            }

            // Bypass SSL certificate errors (expired, self-signed, hostname
            // mismatch, untrusted CA, etc.) so pages keep loading instead of
            // showing an interstitial warning. See class-level warning above.
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                Log.w(TAG, "Ignoring SSL error and proceeding: " + error.toString());
                handler.proceed();
            }
        });
    }

    private void loadUrlFromInput() {
        String input = etUrl.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            return;
        }

        String url = normalizeUrl(input);
        webView.loadUrl(url);
        hideKeyboard();
    }

    /**
     * Turns raw user input into a loadable URL. If the input already looks
     * like a URL (has a scheme) it is used as-is; otherwise it's either
     * treated as a bare domain (has a dot, no spaces) or sent to a search
     * engine.
     */
    private String normalizeUrl(String input) {
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return input;
        }

        boolean looksLikeDomain = !input.contains(" ") && input.contains(".");
        if (looksLikeDomain) {
            return "https://" + input;
        }

        // Fall back to a web search.
        return "https://www.google.com/search?q=" + android.net.Uri.encode(input);
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    /**
     * Installs a TrustManager that accepts all certificates, and a
     * HostnameVerifier that accepts all hostnames, for the default
     * SSLContext used by HttpsURLConnection. This affects any plain Java
     * networking the app performs outside of the WebView.
     *
     * WARNING: This completely disables certificate validation. See the
     * class-level Javadoc warning for details.
     */
    private void installPermissiveTrustManager() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType)
                                throws CertificateException {
                            // Accept all client certificates.
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType)
                                throws CertificateException {
                            // Accept all server certificates (including expired ones).
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new javax.net.ssl.HostnameVerifier() {
                @Override
                public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
                    return true;
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to install permissive trust manager", e);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
