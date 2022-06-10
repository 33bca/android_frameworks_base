package com.android.systemui.statusbar.notification.collection

import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.Pluggable
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifDismissInterceptor
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifLifetimeExtender
import com.android.systemui.util.asIndenting
import com.android.systemui.util.withIncreasedIndent
import java.io.PrintWriter

class PipelineDumper(pw: PrintWriter) {
    private val ipw = pw.asIndenting()

    fun println(a: Any?) = ipw.println(a)

    fun dump(label: String, value: Any?) {
        ipw.print("$label: ")
        dump(value)
    }

    private fun dump(value: Any?) = when (value) {
        null, is String, is Int -> ipw.println(value)
        is Collection<*> -> dumpCollection(value)
        else -> {
            ipw.println(value.fullPipelineName)
            (value as? PipelineDumpable)?.let {
                ipw.withIncreasedIndent { it.dumpPipeline(this) }
            }
        }
    }

    private fun dumpCollection(values: Collection<Any?>) {
        ipw.println(values.size)
        ipw.withIncreasedIndent { values.forEach { dump(it) } }
    }
}

private val Any.bareClassName: String get() {
    val className = javaClass.name
    val packagePrefixLength = javaClass.`package`?.name?.length?.plus(1) ?: 0
    return className.substring(packagePrefixLength)
}

private val Any.barePipelineName: String? get() = when (this) {
    is NotifLifetimeExtender -> name
    is NotifDismissInterceptor -> name
    is Pluggable<*> -> name
    else -> null
}

private val Any.fullPipelineName: String get() =
    barePipelineName?.let { "\"$it\" ($bareClassName)" } ?: bareClassName
