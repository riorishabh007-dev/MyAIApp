package com.ai.personalassistant.services
import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistantAccessibilityService : AccessibilityService() {
    companion object { var instance: AssistantAccessibilityService? = null }
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { instance = null }

    fun clickByText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val clean = query.lowercase().trim()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (text.contains(clean) || desc.contains(clean)) {
                var cur: AccessibilityNodeInfo? = node
                while (cur != null) {
                    if (cur.isClickable) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    cur = cur.parent
                }
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            for (i in 0 until node.childCount) { node.getChild(i)?.let { queue.add(it) } }
        }
        return false
    }

    fun inputText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun executeGlobal(action: Int): Boolean = performGlobalAction(action)
}
