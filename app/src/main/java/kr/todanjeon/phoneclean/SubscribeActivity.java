package kr.todanjeon.phoneclean;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 구독 화면: 도와주는 사람이 월 구독한다. 결제 확인은 서버(Cloud Functions)가 Google Play에 직접 물어 처리한다. */
public class SubscribeActivity extends BaseActivity implements PurchasesUpdatedListener {
    private BillingClient bc;
    private ProductDetails details;
    private TextView state;
    private Button buy;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Auth.uid() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, getString(R.string.sub_title), getString(R.string.version_line, BuildConfig.VERSION_NAME)));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        root.addView(box);
        box.addView(Ui.text(this, getString(R.string.sub_desc), 15, Ui.GRAY, false));
        state = Ui.text(this, "…", 17, Ui.INK, true);
        state.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 8));
        box.addView(state);
        buy = Ui.button(this, getString(R.string.sub_unavailable), Ui.CORAL, Color.WHITE);
        buy.setEnabled(false);
        buy.setOnClickListener(v -> launch());
        box.addView(buy);
        Button manage = Ui.button(this, getString(R.string.sub_manage), Color.WHITE, Ui.NAVY);
        manage.setOnClickListener(v -> Ui.openUrl(this, "https://play.google.com/store/account/subscriptions?sku="
                + Access.PRODUCT_ID + "&package=" + getPackageName()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 8);
        box.addView(manage, lp);

        final String uid = Auth.uid();
        TextView id = Ui.text(this, getString(R.string.my_id_label, uid), 12, Ui.GRAY, false);
        id.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 4));
        box.addView(id);
        Button cp = Ui.button(this, getString(R.string.btn_copy), Color.WHITE, Ui.INK);
        cp.setOnClickListener(v -> {
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("uid", uid));
            Ui.toast(this, getString(R.string.btn_copy) + " ✓");
        });
        box.addView(cp);
        setContentView(root);

        refreshState();
        bc = BillingClient.newBuilder(this).setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build();
        bc.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    query();
                    restore();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
            }
        });
    }

    private void refreshState() {
        Access.check((ok, via) -> runOnUiThread(() -> {
            if (ok) state.setText("grant".equals(via) ? R.string.sub_free_grant : R.string.sub_active);
            else state.setText(R.string.sub_need);
        }));
    }

    private void query() {
        QueryProductDetailsParams.Product p = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(Access.PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build();
        bc.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(p)).build(),
                (br, list) -> {
                    if (list == null || list.isEmpty()) return;
                    details = list.get(0);
                    List<ProductDetails.SubscriptionOfferDetails> offers = details.getSubscriptionOfferDetails();
                    if (offers == null || offers.isEmpty()) return;
                    final String price = offers.get(0).getPricingPhases().getPricingPhaseList().get(0).getFormattedPrice();
                    runOnUiThread(() -> {
                        buy.setText(getString(R.string.sub_price_btn, price));
                        buy.setEnabled(true);
                    });
                });
    }

    /** 앱을 지우고 다시 깔았거나 새 폰일 때: 이 구글 계정의 기존 구독을 찾아 서버에서 다시 확인한다. */
    private void restore() {
        bc.queryPurchasesAsync(com.android.billingclient.api.QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS).build(), (br, list) -> {
            if (list == null) return;
            for (Purchase p : list) {
                if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
                Map<String, Object> m = new HashMap<>();
                m.put("purchaseToken", p.getPurchaseToken());
                m.put("productId", p.getProducts().get(0));
                FirebaseFunctions.getInstance(Auth.REGION).getHttpsCallable("verifyPurchase").call(m)
                        .addOnSuccessListener(x -> refreshState());
            }
        });
    }

    private void launch() {
        if (details == null || details.getSubscriptionOfferDetails() == null) return;
        BillingFlowParams.ProductDetailsParams pd = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(details.getSubscriptionOfferDetails().get(0).getOfferToken()).build();
        bc.launchBillingFlow(this, BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(pd))
                // 결제를 계정에 묶어 둔다(다른 계정이 같은 구매를 쓰지 못하게 서버가 확인)
                .setObfuscatedAccountId(Auth.uid()).build());
    }

    @Override
    public void onPurchasesUpdated(BillingResult r, List<Purchase> list) {
        if (r.getResponseCode() != BillingClient.BillingResponseCode.OK || list == null) return;
        for (final Purchase p : list) {
            if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
            runOnUiThread(() -> state.setText(R.string.sub_verifying));
            Map<String, Object> m = new HashMap<>();
            m.put("purchaseToken", p.getPurchaseToken());
            m.put("productId", p.getProducts().get(0));
            FirebaseFunctions.getInstance(Auth.REGION).getHttpsCallable("verifyPurchase").call(m)
                    .addOnSuccessListener(x -> refreshState())
                    .addOnFailureListener(e -> runOnUiThread(() -> Ui.toast(this, getString(R.string.err_generic, Auth.msg(e)))));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bc != null) bc.endConnection();
    }
}
