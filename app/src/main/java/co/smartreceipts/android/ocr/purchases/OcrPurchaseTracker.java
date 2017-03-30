package co.smartreceipts.android.ocr.purchases;

import android.content.Context;
import android.support.annotation.NonNull;

import com.google.common.base.Preconditions;

import co.smartreceipts.android.apis.hosts.ServiceManager;
import co.smartreceipts.android.purchases.PurchaseEventsListener;
import co.smartreceipts.android.purchases.PurchaseManager;
import co.smartreceipts.android.purchases.apis.MobileAppPurchasesService;
import co.smartreceipts.android.purchases.apis.PurchaseRequest;
import co.smartreceipts.android.purchases.apis.PurchaseResponse;
import co.smartreceipts.android.purchases.model.ConsumablePurchase;
import co.smartreceipts.android.purchases.model.InAppPurchase;
import co.smartreceipts.android.purchases.model.ManagedProduct;
import co.smartreceipts.android.purchases.source.PurchaseSource;
import co.smartreceipts.android.purchases.wallet.PurchaseWallet;
import co.smartreceipts.android.utils.log.Logger;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

public class OcrPurchaseTracker implements PurchaseEventsListener {

    private static final String GOAL = "Recognition";

    private final ServiceManager serviceManager;
    private final PurchaseManager purchaseManager;
    private final PurchaseWallet purchaseWallet;
    private final OcrScansTracker ocrScansTracker;

    public OcrPurchaseTracker(@NonNull Context context, @NonNull ServiceManager serviceManager,
                              @NonNull PurchaseManager purchaseManager, @NonNull PurchaseWallet purchaseWallet) {
        this(serviceManager, purchaseManager, purchaseWallet, new OcrScansTracker(context));
    }

    public OcrPurchaseTracker(@NonNull ServiceManager serviceManager, @NonNull PurchaseManager purchaseManager,
                              @NonNull PurchaseWallet purchaseWallet, @NonNull OcrScansTracker ocrScansTracker) {
        this.serviceManager = Preconditions.checkNotNull(serviceManager);
        this.purchaseManager = Preconditions.checkNotNull(purchaseManager);
        this.purchaseWallet = Preconditions.checkNotNull(purchaseWallet);
        this.ocrScansTracker = Preconditions.checkNotNull(ocrScansTracker);
    }

    public void initialize() {
        this.purchaseManager.addEventListener(this);
        final ManagedProduct managedProduct = purchaseWallet.getManagedProduct(InAppPurchase.OcrScans50);
        Preconditions.checkArgument(managedProduct instanceof ConsumablePurchase, "OcrScans50 must be a ConsumablePurchase");
        uploadOcrPurchase((ConsumablePurchase) managedProduct);

        // TODO: Get the most accurate amount of scans (currently available) and persist
    }

    @Override
    public void onPurchaseSuccess(@NonNull InAppPurchase inAppPurchase, @NonNull PurchaseSource purchaseSource, @NonNull PurchaseWallet updatedPurchaseWallet) {
        if (inAppPurchase == InAppPurchase.OcrScans50) {
            final ManagedProduct managedProduct = purchaseWallet.getManagedProduct(InAppPurchase.OcrScans50);
            Preconditions.checkArgument(managedProduct instanceof ConsumablePurchase, "OcrScans50 must be a ConsumablePurchase");
            uploadOcrPurchase((ConsumablePurchase) managedProduct);
        }
    }

    @Override
    public void onPurchaseFailed(@NonNull PurchaseSource purchaseSource) {

    }

    /**
     * @return {@code true} if we have OCR scans remaining. {@code false} otherwise. Please note that is
     * this not the authority for this (ie it's not the server), this may not be fully accurate, so we
     * may still get a remote error after a scan
     */
    public boolean hasAvailableScans() {
        return ocrScansTracker.getRemainingScans() > 0;
    }

    /**
     * Decrements the remaining scan count by 1, to indicate that we've successfully used one of our scans
     */
    public void decrementRemainingScans() {
        ocrScansTracker.decrementRemainingScans();
    }

    private void uploadOcrPurchase(@NonNull final ConsumablePurchase consumablePurchase) {
        if (consumablePurchase.getInAppPurchase() != InAppPurchase.OcrScans50) {
            throw new IllegalArgumentException("Unsupported purchase type: " + consumablePurchase.getInAppPurchase());
        }

        serviceManager.getService(MobileAppPurchasesService.class).addPurchase(new PurchaseRequest(consumablePurchase, GOAL))
                .flatMap(new Func1<PurchaseResponse, Observable<?>>() {
                    @Override
                    public Observable<?> call(PurchaseResponse purchaseResponse) {
                        Logger.debug(OcrPurchaseTracker.this, "Received purchase response of {}", purchaseResponse);
                        return purchaseManager.consumePurchase(consumablePurchase);
                    }
                })
                .subscribe(new Subscriber<Object>() {
                    @Override
                    public void onCompleted() {
                        Logger.info(OcrPurchaseTracker.this, "Successfully uploaded and consumed purchase of {}.", consumablePurchase.getInAppPurchase());
                    }

                    @Override
                    public void onError(Throwable e) {
                        Logger.error(OcrPurchaseTracker.this, "Failed to upload purchase of " + consumablePurchase.getInAppPurchase(), e);
                    }

                    @Override
                    public void onNext(Object o) {

                    }
                });
    }
}
