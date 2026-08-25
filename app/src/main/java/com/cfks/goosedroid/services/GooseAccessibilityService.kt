package com.cfks.goosedroid.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GooseAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: GooseAccessibilityService? = null

        fun isServiceEnabled(): Boolean {
            return instance != null
        }

        fun captureScreenText(): String {
            val service = instance ?: return "Screen content unavailable or service not enabled."
            
            val root = service.rootInActiveWindow
            if (root == null) {
                return "Screen content unavailable."
            }

            val sb = java.lang.StringBuilder()
            traverseNode(root, sb)
            root.recycle()
            return sb.toString()
        }

        private fun traverseNode(node: AccessibilityNodeInfo?, sb: java.lang.StringBuilder) {
            if (node == null) return
            
            if (!node.text.isNullOrBlank()) {
                sb.append(node.text).append("\n")
            } else if (!node.contentDescription.isNullOrBlank()) {
                sb.append(node.contentDescription).append("\n")
            }

            for (i in 0 until node.childCount) {
                traverseNode(node.getChild(i), sb)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only fetch on-demand via captureScreenText, so we don't need to process events here.
    }

    override fun onInterrupt() {
        // Required method, not used.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
}
