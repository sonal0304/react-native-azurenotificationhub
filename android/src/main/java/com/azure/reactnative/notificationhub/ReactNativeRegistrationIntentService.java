package com.azure.reactnative.notificationhub;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.JobIntentService;

import com.azure.reactnative.notificationhub.util.ReactNativeConstants;
import com.azure.reactnative.notificationhub.util.ReactNativeNotificationHubUtil;
import com.azure.reactnative.notificationhub.util.ReactNativeNotificationsHandler;
import com.microsoft.windowsazure.messaging.NotificationHub;
import com.microsoft.windowsazure.messaging.notificationhubs.FcmV1Registration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReactNativeRegistrationIntentService extends JobIntentService {

    public static final String TAG = "ReactNativeRegistration";

    private static final int JOB_ID = 1000;

    private final ExecutorService mPool = Executors.newFixedThreadPool(1);

    /**
     * Convenience method for enqueuing work into this service.
     */
    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, ReactNativeRegistrationIntentService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(Intent intent) {
        final ReactNativeNotificationHubUtil notificationHubUtil = ReactNativeNotificationHubUtil.getInstance();
        final String connectionString = notificationHubUtil.getConnectionString(this);
        final String hubName = notificationHubUtil.getHubName(this);
        final String storedToken = notificationHubUtil.getFCMToken(this);
        final String[] tags = notificationHubUtil.getTags(this);
        final boolean isTemplated = notificationHubUtil.isTemplated(this);
        final String templateName = notificationHubUtil.getTemplateName(this);
        final String template = notificationHubUtil.getTemplate(this);

        if (connectionString == null || hubName == null) {
            Log.e(TAG, "Azure Notification Hub connection settings are not configured.");
            return;
        }

        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(mPool, new OnSuccessListener<String>() {
            @Override
            public void onSuccess(String token) {
                try {
                    String regID = notificationHubUtil.getRegistrationID(ReactNativeRegistrationIntentService.this);
                    Log.d(TAG, "FCM Registration Token: " + token);

                    // Check if registration needs to be refreshed
                    if (regID == null || !storedToken.equals(token)) {
                        NotificationHub hub = ReactNativeUtil.createNotificationHub(hubName, connectionString,
                                ReactNativeRegistrationIntentService.this);
                        Log.d(TAG, "NH Registration refreshing with token : " + token);

                        FcmV1Registration fcmV1Registration = new FcmV1Registration(token, tags);
                        if (isTemplated) {
                            fcmV1Registration.setTemplateName(templateName);
                            fcmV1Registration.setTemplate(template);
                        }

                        regID = hub.register(fcmV1Registration).getRegistrationId();
                        Log.d(TAG, "New NH Registration Successfully - RegId : " + regID);

                        // Update stored registration ID and FCM token
                        notificationHubUtil.setRegistrationID(ReactNativeRegistrationIntentService.this, regID);
                        notificationHubUtil.setFCMToken(ReactNativeRegistrationIntentService.this, token);

                        // Broadcast registration success event
                        Intent event = ReactNativeNotificationHubUtil.IntentFactory.createIntent(TAG);
                        event.putExtra(ReactNativeConstants.KEY_INTENT_EVENT_NAME,
                                ReactNativeConstants.EVENT_AZURE_NOTIFICATION_HUB_REGISTERED);
                        event.putExtra(ReactNativeConstants.KEY_INTENT_EVENT_TYPE,
                                ReactNativeConstants.INTENT_EVENT_TYPE_STRING);
                        event.putExtra(ReactNativeConstants.KEY_INTENT_EVENT_STRING_DATA, regID);
                        ReactNativeNotificationsHandler.sendBroadcast(ReactNativeRegistrationIntentService.this,
                                event, 0);

                        // Create notification channel if not already created
                        ReactNativeFirebaseMessagingService.createNotificationChannel(
                                ReactNativeRegistrationIntentService.this);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to complete token refresh", e);

                    // Broadcast registration error event
                    Intent event = ReactNativeNotificationHubUtil.IntentFactory.createIntent(TAG);
                    event.putExtra(ReactNativeConstants.KEY_INTENT_EVENT_NAME,
                            ReactNativeConstants.EVENT_AZURE_NOTIFICATION_HUB_REGISTERED_ERROR);
                    event.putExtra(ReactNativeConstants.KEY_INTENT_EVENT_TYPE,
                            ReactNativeConstants.INTENT_EVENT_TYPE_STRING);
                    event.putExtra(ReactNativeConstants.KEY_INTENT_EVENT_STRING_DATA, e.getMessage());
                    ReactNativeNotificationsHandler.sendBroadcast(ReactNativeRegistrationIntentService.this,
                            event, 0);
                }
            }
        });
    }
}
