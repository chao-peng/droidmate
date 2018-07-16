@file:Suppress("MemberVisibilityCanBePrivate")

package org.droidmate.uiautomator2daemon.uiautomatorExtensions

import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.apply
import android.support.test.uiautomator.getRootNodes
import android.util.Log
import android.util.Xml
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.experimental.NonCancellable.isActive
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.selects.select
import org.droidmate.uiautomator2daemon.debugT
import org.droidmate.uiautomator_daemon.guimodel.WidgetData
import java.io.StringWriter
import java.util.*
import kotlin.math.max
import kotlin.system.measureTimeMillis


@Suppress("unused")
object UiHierarchy : UiParser() {
	private const val LOGTAG = "droidmate/UiHierarchy"

	private var nActions = 1
	private var time = 0L
	fun fetch(device: UiDevice): List<WidgetData> = debugT(" compute UiNodes avg= ${time/(nActions*1000000)}", {LinkedList<WidgetData>().apply {
		device.waitForIdle()
		device.apply(widgetCreator(this,device.displayWidth, device.displayHeight))
	}.apply { Log.d(LOGTAG,"#elems = ${this.size}")} }, inMillis = true, timer = {time += it; nActions+=1})

	fun getXml(device: UiDevice):String = 	debugT(" fetching gui Dump ", {StringWriter().use { out ->
		device.waitForIdle()

		val serializer = Xml.newSerializer()
		serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
		serializer.setOutput(out)//, "UTF-8")

		serializer.startDocument("UTF-8", true)
		serializer.startTag("", "hierarchy")
		serializer.attribute("", "rotation", Integer.toString(device.displayRotation))

		device.apply(nodeDumper(serializer, device.displayWidth, device.displayHeight))

		serializer.endTag("", "hierarchy")
		serializer.endDocument()
		serializer.flush()
		out.toString()
	}}, inMillis = true)

	suspend fun any(device: UiDevice, cond: SelectorCondition):Boolean = device.getRootNodes().let{ roots ->
		var found = false
		var i = 0

		while (isActive && !found && i<roots.size){
			found = roots[i].checkC(cond)
			i += 1
		}
		roots.forEach{ it.recycle() }
		found
	}
	/** @paramt timeout amount of mili seconds, maximal spend to wait for condition [cond] to become true (default 10s)
	 * @param pollTime time intervall (in ms) to recheck the condition [cond]
	 * @return if the condition was fulfilled within timeout
	 * */
	@JvmOverloads
	fun waitFor(device: UiDevice, timeout: Long = 10000, cond: SelectorCondition): Boolean{
		return waitFor(device,timeout,10,cond)
	}
	/** @param pollTime time intervall (in ms) to recheck the condition [cond] */
	fun waitFor(device: UiDevice, timeout: Long, pollTime: Long, cond: SelectorCondition) = runBlocking{
		// lookup should only take less than 100ms (avg 50-80ms) if the UiAutomator did not screw up
		val scanTimeout = 100 // this is the maximal number of mili seconds, which is spend for each lookup in the hierarchy
		var time = 0.0
		var found = false

		while(!found && time<timeout){
			measureTimeMillis {
				with(async { any(device, cond) }) {
					var i = 0
					while(!isCompleted && i<scanTimeout){
						delay(10)
						i+=10
					}
					if (isCompleted)
						found = await()
					else cancel()
				}

				// in theory this should be more efficient, but the calls where find = true are much slower for whatever reason
//				val process = async { any(device, cond) }
//				val timer = async { delay(50)  }
//				select<Unit>{
//					process.onAwait{ value -> found = value}
//						timer.onAwait{ process.cancel() }
//				}
//				if(!found) delay(pollTime)

			}.run{ time += this
				device.runWatchers() // to update the ui view?
				if(!found && this<pollTime) delay(pollTime-this)
				Log.d(LOGTAG,"$found single wait iteration $this")
			}
		}
		found
	}

	/** check if this node fulfills the given condition and recursively check descendents if not **/
	private suspend fun AccessibilityNodeInfo.checkC(c: SelectorCondition): Boolean {
		if(!isActive || !isVisibleToUser || !refresh()) return false
		var found = c(this)
		var i = 0
		while (isActive && !found && i < childCount) {
			val child = getChild(i)
			if (child != null) {
				found = child.checkC(c)
				child.recycle()
			}
			i += 1
		}
		return found
	}

	/*
		/** check if this node fulfills the given condition and recursively check descendents if not **/
	private fun<T> AccessibilityNodeInfo.computeC(p: T, c: (AccessibilityNodeInfo, T)-> Pair<Boolean,T> ): Boolean {
		val res = c(this,p)
		var found = res.first
		var i = 0
		while (!found && i < childCount) {
			val child = getChild(i)
			if (child != null) {
				found = child.computeC(res.second,c)
				child.recycle()
			}
			i += 1
		}
		return found
	}
	 */

}




