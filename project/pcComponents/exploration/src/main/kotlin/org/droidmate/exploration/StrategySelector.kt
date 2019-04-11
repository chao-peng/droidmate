// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.exploration

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.exploration.strategy.*
import org.droidmate.exploration.strategy.playback.Playback
import org.droidmate.exploration.strategy.widget.*
import org.droidmate.explorationModel.debugOut
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

typealias SelectorFunction = suspend (context: ExplorationContext, explorationPool:ExplorationStrategyPool, bundle: Array<out Any>?) -> ISelectableExplorationStrategy?
typealias OnSelected = (context: ExplorationContext) -> Unit

class StrategySelector constructor(val priority: Int,
                                   val description: String,
                                   val selector: SelectorFunction,
                                   val onSelected: OnSelected? = null,
                                   vararg val bundle: Any){
	constructor(priority: Int,
	            description: String,
	            selector: SelectorFunction,
	            bundle: Any): this(priority, description, selector, null, bundle)


	override fun toString(): String {
		return "($priority)-$description"
	}

	companion object {
		val logger: Logger by lazy { LoggerFactory.getLogger(StrategySelector::class.java) }

		/**
		 * Terminate the exploration after a predefined number of actions
		 */
		@JvmStatic
		val actionBasedTerminate : SelectorFunction = { context, pool, bundle ->
			val maxActions = bundle!![0] .toString().toInt()
			if (context.explorationTrace.size == maxActions) {
				logger.debug("Maximum number of actions reached. Returning 'Terminate'")
				pool.getFirstInstanceOf(Terminate::class.java)
			}
			else
				null
		}

		/**
		 * Terminate the exploration after a predefined elapsed time
		 */
		@JvmStatic
		val timeBasedTerminate : SelectorFunction = { context, _, bundle ->
			val timeLimit = bundle!![0].toString().toInt()
			if(timeLimit <= 0) null
			else {
				val diff = context.getExplorationTimeInMs()

				logger.debug("remaining exploration time: ${"%.1f".format((timeLimit-diff)/1000.0)}s")
				if (diff >= timeLimit) {
					logger.info("Exploration time exhausted. Returning 'Terminate'")
					Terminate
				} else
					null
			}
		}

		/**
		 * Restarts the exploration when the current state is an "app not responding" dialog
		 */
		@JvmStatic
		val appCrashedReset: SelectorFunction = { context, pool, _ ->
			val currentState = context.getCurrentState()

			if (currentState.isAppHasStoppedDialogBox) {
				logger.debug("Current screen is 'App has stopped'. Returning 'Reset'")
				pool.getFirstInstanceOf(Reset::class.java)
			}
			else
				null
		}

		/**
		 * Sets the device to a known state (wifi on, empty logcat) and starts the app
		 */
		@JvmStatic
		val startExplorationReset: SelectorFunction = { context, pool, _ ->
			if (context.isEmpty()) {
				logger.debug("Context is empty, must start exploration. Returning 'Reset'")
				pool.getFirstInstanceOf(Reset::class.java)
			}
			else
				null
		}

		/**
		 * Resets the exploration once a predetermined number of non-reset actions has been executed
		 */
		@JvmStatic
		val intervalReset: SelectorFunction = { context, pool, bundle ->
			val interval = bundle!![0].toString().toInt()

			val lastReset = context.explorationTrace.P_getActions()
					.indexOfLast { it -> it.actionType == LaunchApp.name }

			val currAction = context.explorationTrace.size
			val diff = currAction - lastReset

			if (diff > interval){
				logger.debug("Has not restarted for $diff actions. Returning 'Reset'")
				pool.getFirstInstanceOf(Reset::class.java)
			}
			else
				null
		}

		/**
		 * Selects a random widget and acts over it
		 */
		@JvmStatic
		val randomWidget: SelectorFunction = { _, pool, _ ->
			pool.getFirstInstanceOf(RandomWidget::class.java)
		}

		/**
		 * Randomly presses back.
		 *
		 * Expected bundle: Array: [Probability (Double), java.util.Random].
		 *
		 * Passing a different bundle will crash the execution.
		 */
		@JvmStatic
		val randomBack: SelectorFunction = { context, pool, bundle ->
			val bundleArray = bundle!!
			val probability = bundleArray[0] as Double
			val random = bundleArray[1] as Random
			val value = random.nextDouble()

			val lastLaunchDistance = with(context.explorationTrace.getActions()) {
				size-lastIndexOf(findLast{ !it.actionType.isQueueEnd() })
			}
			if ((lastLaunchDistance <=3 ) || (value > probability))
				null
			else {
				logger.debug("Has triggered back probability and previous action was not to press back. Returning 'Back'")
				pool.getFirstInstanceOf(Back::class.java)
			}
		}

		object WaitForLaunch:AbstractStrategy() {
			override val noContext: Boolean = true
			private var cnt = 0
			var terminate = false

			fun init(){
				cnt = 0
				terminate = false
			}

			override suspend fun internalDecide(): ExplorationAction {
				return when{
					cnt++ < 2 ->{
						runBlocking {  delay(5000) }
						GlobalAction(ActionType.FetchGUI)
					}
					terminate -> {
						StrategySelector.logger.debug("Cannot explore. Last action was reset. Previous action was to press back. Returning 'Terminate'")
						GlobalAction(ActionType.Terminate)
					}
					else -> GlobalAction(ActionType.PressBack)
				}
			}
		}

		@JvmStatic
		val cannotExplore: SelectorFunction = { context, pool, _ ->
			if (!context.explorationCanMoveOn()){
				val lastActionType = context.getLastActionType()
				val (lastLaunchDistance,secondLast) = with(
						context.explorationTrace.getActions().filterNot {
							it.actionType.isQueueStart()|| it.actionType.isQueueEnd() }
				){
					lastIndexOf(findLast{ it.actionType.isLaunchApp() }).let{ launchIdx ->
						val beforeLaunch = this.getOrNull(launchIdx - 1)
						Pair( size-launchIdx, beforeLaunch)
					}
				}
				@Suppress("UNUSED_VARIABLE")  // it is here for debugging purposes of an conditional breakpoint
				val apkPkg = context.apk.packageName
				val s = context.getCurrentState()
				when {
					lastActionType.isPressBack() -> { // if previous action was back, terminate
						logger.debug("Cannot explore. Last action was back. Returning 'Reset'")
						pool.getFirstInstanceOf(Reset::class.java)
					}
					lastLaunchDistance <=3 || context.getLastActionType().isFetch() -> { // since app reset is an ActionQueue of (Launch+EnableWifi), or we had a WaitForLaunch action
						when {  // last action was reset
							s.isAppHasStoppedDialogBox -> {
								logger.debug("Cannot explore. Last action was reset. Currently on an 'App has stopped' dialog. Returning 'Terminate'")
								pool.getFirstInstanceOf(Terminate::class.java)
							}
							secondLast?.actionType?.isPressBack() ?: false -> {
								//WaitForLaunch.terminate = true  // try to wait for launch but terminate if we still have nothing to explore afterwards
								WaitForLaunch
							}
							else -> { // the app may simply need more time to start (synchronization for app-launch not yet perfectly working) -> do delayed re-fetch for now
								logger.debug("Cannot explore. Returning 'Wait'")
								WaitForLaunch
							}
						}
					}
				// by default, if it cannot explore, presses back
					else -> {
						pool.getFirstInstanceOf(Back::class.java)
					}
				}
			}
			else{			// can move forwards
				Companion.WaitForLaunch.init()
				null
			}
		}

		/**
		 * Selects the allow runtime permission command
		 */
		@JvmStatic
		val allowPermission: SelectorFunction = { context, pool, _ ->
			if (context.getCurrentState().isRequestRuntimePermissionDialogBox) {
				logger.debug("Runtime permission dialog. Returning 'AllowRuntimePermission'")
				pool.getFirstInstanceOf(AllowRuntimePermission::class.java)
			}
			else
				null
		}

		@JvmStatic
		val denyPermission: SelectorFunction = { context, pool, _ ->
			val widgets = context.getCurrentState().widgets
			var hasDenyButton = widgets.any { it.resourceId == "com.android.packageinstaller:id/permission_deny_button" }

			if (!hasDenyButton)
				hasDenyButton = widgets.any { it.text.toUpperCase() == "DENY" }

			if (hasDenyButton) {
				logger.debug("Runtime permission dialog. Returning 'DenyRuntimePermission'")
				pool.getFirstInstanceOf(DenyRuntimePermission::class.java)
			}
			else
				null
		}

		/**
		 * Finishes the exploration once all widgets have been explored
		 */
		@JvmStatic
		val explorationExhausted: SelectorFunction = { context, pool, _ ->
			// wait for at least two actions to allow for second fetch after launch for slow/non-synchronized apps
			val exhausted = context.explorationTrace.size>2 && context.areAllWidgetsExplored()

			if (exhausted)
				pool.getFirstInstanceOf(Terminate::class.java)
			else
				null
		}

		@JvmStatic
		val playback: SelectorFunction = { _, pool, _ ->
			logger.debug("Playback. Returning 'Playback'")
			pool.getFirstInstanceOf(Playback::class.java)
		}

		/**
		 * Uses the Depth-First-Search strategy, if available
		 */
		@JvmStatic
		val dfs: SelectorFunction = { _, pool, _ ->
			pool.getFirstInstanceOf(DFS::class.java)
		}

		/**
		 * Selector to synchronizestatementt coverage
		 */
		val statementCoverage: SelectorFunction = { context, _, _ ->
			context.findWatcher { it is StatementCoverageMF }?.join()

			null
		}
	}
}