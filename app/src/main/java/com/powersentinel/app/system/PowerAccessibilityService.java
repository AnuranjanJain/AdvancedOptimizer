package com.powersentinel.app.system;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class PowerAccessibilityService extends AccessibilityService {
    private static final String TAG = "PowerAccessibility";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
            if ("com.android.settings".equals(packageName)) {
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    performAutoClick(rootNode, "Force stop");
                    performAutoClick(rootNode, "Clear cache");
                }
            }
        }
    }

    private void performAutoClick(AccessibilityNodeInfo node, String textToFind) {
        if (node == null) return;
        List<AccessibilityNodeInfo> list = node.findAccessibilityNodeInfosByText(textToFind);
        for (AccessibilityNodeInfo targetNode : list) {
            if (targetNode.isClickable() && targetNode.isEnabled()) {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d(TAG, "Clicked: " + textToFind);
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.e(TAG, "Service Interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.DEFAULT | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        this.setServiceInfo(info);
        Log.d(TAG, "Accessibility Service Connected");
    }
}
