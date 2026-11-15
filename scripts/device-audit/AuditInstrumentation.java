package pl.proszczok.piggiaudit;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import org.json.JSONArray;
import org.json.JSONObject;

/** External test tool: never disables TalkBack and never ships inside the player. */
public final class AuditInstrumentation extends Instrumentation {
    private Bundle args;
    private UiAutomation ui;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        args = arguments == null ? new Bundle() : arguments;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            ui = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = ui.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            ui.setServiceInfo(info);
            SystemClock.sleep(300);
            String op = args.getString("op", "dump");
            if (op.equals("dump")) {
                JSONArray nodes = new JSONArray();
                for (AccessibilityWindowInfo w : ui.getWindows()) dump(w.getRoot(), nodes, 0);
                result.putString("nodes", nodes.toString());
            } else if (op.equals("click") || op.equals("focus") || op.equals("scroll") || op.equals("set") || op.equals("action")) {
                AccessibilityNodeInfo node = find(args.getString("text", ""));
                if (node == null) throw new IllegalStateException("Node not found: " + args.getString("text"));
                int action = AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS;
                if (op.equals("click")) {
                    action = AccessibilityNodeInfo.ACTION_CLICK;
                    while (!node.isClickable() && node.getParent() != null) node = node.getParent();
                } else if (op.equals("scroll")) {
                    action = "back".equals(args.getString("direction"))
                        ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
                    while (!node.isScrollable() && node.getParent() != null) node = node.getParent();
                } else if (op.equals("set")) action = AccessibilityNodeInfo.ACTION_SET_TEXT;
                else if (op.equals("action")) {
                    action = 0;
                    while (node != null && action == 0) {
                        for (AccessibilityNodeInfo.AccessibilityAction a : node.getActionList())
                            if (args.getString("value", "").contentEquals(String.valueOf(a.getLabel()))) action = a.getId();
                        if (action == 0) node = node.getParent();
                    }
                    if (node == null) throw new IllegalStateException("Custom action not found");
                } else {
                    while (!node.isFocusable() && node.getParent() != null) node = node.getParent();
                }
                Bundle values = new Bundle();
                values.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, args.getString("value", ""));
                if (!node.performAction(action, values)) throw new IllegalStateException("Action rejected: " + op);
                result.putString("action", op + ": " + node.getText() + " / " + node.getContentDescription());
                SystemClock.sleep(1000);
            } else if (op.equals("key")) {
                int key = Integer.parseInt(args.getString("key", "24"));
                long down = SystemClock.uptimeMillis();
                inject(down, down, KeyEvent.ACTION_DOWN, key);
                SystemClock.sleep(Long.parseLong(args.getString("hold", "850")));
                inject(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, key);
                SystemClock.sleep(700);
                result.putString("key", String.valueOf(key));
            } else if (op.equals("media")) {
                ui.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL");
                MediaController controller = getContext().getSystemService(MediaSessionManager.class)
                    .getActiveSessions(null).stream().filter(c -> "pl.proszczok.player".equals(c.getPackageName()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Player session missing"));
                result.putString("before", String.valueOf(controller.getMetadata().getDescription()));
                result.putString("stateBefore", String.valueOf(controller.getPlaybackState()));
                String command = args.getString("command", "state");
                MediaController.TransportControls controls = controller.getTransportControls();
                if (command.equals("next")) controls.skipToNext();
                if (command.equals("previous")) controls.skipToPrevious();
                if (command.equals("play")) controls.play();
                if (command.equals("pause")) controls.pause();
                if (command.equals("seek")) controls.seekTo(Long.parseLong(args.getString("position", "0")));
                if (command.equals("queue")) controls.skipToQueueItem(Long.parseLong(args.getString("index", "0")));
                if (command.equals("button")) {
                    int key = Integer.parseInt(args.getString("key", "87"));
                    long now = SystemClock.uptimeMillis();
                    controller.dispatchMediaButtonEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0));
                    controller.dispatchMediaButtonEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0));
                }
                SystemClock.sleep(1000);
                result.putString("after", String.valueOf(controller.getMetadata().getDescription()));
                result.putString("stateAfter", String.valueOf(controller.getPlaybackState()));
                result.putInt("queueSize", controller.getQueue() == null ? 0 : controller.getQueue().size());
            } else throw new IllegalArgumentException("Unknown op: " + op);
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("error", android.util.Log.getStackTraceString(error));
            finish(0, result);
        }
    }

    private void inject(long down, long when, int action, int key) {
        if (!ui.injectInputEvent(new KeyEvent(down, when, action, key, 0, 0,
                android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD), true))
            throw new IllegalStateException("Key injection failed");
    }

    private AccessibilityNodeInfo find(String label) {
        for (AccessibilityWindowInfo w : ui.getWindows()) {
            AccessibilityNodeInfo found = find(w.getRoot(), label);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String label) {
        if (node == null) return null;
        if (String.valueOf(node.getText()).equals(label) || String.valueOf(node.getContentDescription()).equals(label)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), label);
            if (found != null) return found;
        }
        return null;
    }

    private void dump(AccessibilityNodeInfo node, JSONArray out, int depth) throws Exception {
        if (node == null) return;
        Rect rect = new Rect(); node.getBoundsInScreen(rect);
        JSONObject item = new JSONObject();
        item.put("depth", depth).put("text", String.valueOf(node.getText()))
            .put("description", String.valueOf(node.getContentDescription()))
            .put("state", String.valueOf(node.getStateDescription()))
            .put("class", String.valueOf(node.getClassName()))
            .put("package", String.valueOf(node.getPackageName()))
            .put("clickable", node.isClickable()).put("focusable", node.isFocusable())
            .put("focused", node.isAccessibilityFocused()).put("visible", node.isVisibleToUser())
            .put("selected", node.isSelected()).put("heading", node.isHeading())
            .put("bounds", rect.toShortString()).put("pane", String.valueOf(node.getPaneTitle()));
        JSONArray actions = new JSONArray();
        for (AccessibilityNodeInfo.AccessibilityAction a : node.getActionList())
            actions.put(a.getId() + ":" + a.getLabel());
        item.put("actions", actions); out.put(item);
        for (int i = 0; i < node.getChildCount(); i++) dump(node.getChild(i), out, depth + 1);
    }
}
